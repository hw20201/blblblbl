#!/usr/bin/env bash
# Rebuild only the native payload of the existing QuickJS Android 1.4.6 AAR.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
COMMIT=fadb5b52f2ea988420b4d8dfae589ab55d66b911
SOURCE_SHA256=8f0d3d3d801b0c1e3438379bba1a7c5e3c099d2b0e86f74c74296bfab8bb4f33
AAR_SHA256=2595996dc2b0e5ce6b55a8913ab03d95d2574bc036a104a8ceb7330867a987a7
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-$SDK/ndk/28.2.13676358}}"
CMAKE="${CMAKE:-$SDK/cmake/3.22.1/bin/cmake}"
ABI=arm64-v8a
while [[ $# -gt 0 ]]; do
  case "$1" in
    --abi) ABI="${2:?--abi requires arm64-v8a or x86_64}"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done
case "$ABI" in arm64-v8a|x86_64) ;; *) echo "Unsupported ABI: $ABI" >&2; exit 2 ;; esac
BUILD="$ROOT/native/build-v99"
mkdir -p "$BUILD"
ARCHIVE="$BUILD/quickjs-$COMMIT.tar.gz"
AAR="${QUICKJS_ORIGINAL_AAR:-$BUILD/quickjs-android-1.4.6-original.aar}"
SOURCE_URL="https://codeload.github.com/taoweiji/quickjs-android/tar.gz/$COMMIT"
AAR_URL=https://repo.maven.apache.org/maven2/io/github/taoweiji/quickjs/quickjs-android/1.4.6/quickjs-android-1.4.6.aar
if [[ ! -f "$ARCHIVE" ]]; then
  curl --fail --location --retry 3 --output "$ARCHIVE" "$SOURCE_URL"
fi
printf '%s  %s\n' "$SOURCE_SHA256" "$ARCHIVE" | sha256sum --check --status
if [[ ! -f "$AAR" ]]; then
  [[ -z "${QUICKJS_ORIGINAL_AAR:-}" ]] || { echo "Missing original AAR: $AAR" >&2; exit 1; }
  curl --fail --location --retry 3 --output "$AAR" "$AAR_URL"
fi
printf '%s  %s\n' "$AAR_SHA256" "$AAR" | sha256sum --check --status
python3 - "$NDK/source.properties" <<'PY'
import pathlib, sys
assert 'Pkg.Revision = 28.2.13676358' in pathlib.Path(sys.argv[1]).read_text(), 'NDK 28.2.13676358 required'
PY
[[ "$("$CMAKE" --version | head -n 1)" == 'cmake version 3.22.1'* ]] || { echo 'CMake 3.22.1 required' >&2; exit 1; }
SOURCE="$BUILD/quickjs-source-$COMMIT"
# Reused source files must still match the checksum-pinned archive, byte for byte.
python3 - "$ARCHIVE" "$SOURCE" <<'PY'
import pathlib, sys, tarfile
archive, source = sys.argv[1], pathlib.Path(sys.argv[2])
with tarfile.open(archive, 'r:gz') as tar:
    members = tar.getmembers()
    prefix = members[0].name.split('/')[0] + '/'
    files = {}
    for member in members:
        if member.isdir():
            continue
        assert member.isfile() and member.name.startswith(prefix), 'Unexpected archive entry'
        relative = pathlib.PurePosixPath(member.name[len(prefix):])
        assert not relative.is_absolute() and '..' not in relative.parts, 'Unsafe archive entry'
        files[relative] = tar.extractfile(member).read()
    if source.exists():
        actual = {p.relative_to(source).as_posix() for p in source.rglob('*') if p.is_file() or p.is_symlink()}
        assert actual == {p.as_posix() for p in files}, 'Prepared source tree contains missing/extra files'
        for relative, data in files.items():
            path = source / relative
            assert not path.is_symlink() and path.read_bytes() == data, f'Modified source: {relative}'
    else:
        source.mkdir()
        for relative, data in files.items():
            path = source / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
assert 'VERSION_NAME=1.4.6' in (source / 'gradle.properties').read_text()
assert (source / 'quickjs-android/src/main/cpp/quickjs/quickjs/VERSION').read_text().strip() == '2021-03-27'
PY
OBJECTS="$BUILD/quickjs-objects/$ABI"
# Both upstream SHARED targets inherit these linker flags; no source patch is needed.
LINK_FLAGS='-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384'
"$CMAKE" -S "$SOURCE/quickjs-android/src/main/cpp" -B "$OBJECTS" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static \
  -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_FLAGS="-g0 -ffile-prefix-map=$SOURCE=quickjs-$COMMIT -ffile-prefix-map=$OBJECTS=quickjs-objects/$ABI" \
  -DCMAKE_CXX_FLAGS="-g0 -ffile-prefix-map=$SOURCE=quickjs-$COMMIT -ffile-prefix-map=$OBJECTS=quickjs-objects/$ABI" \
  -DCMAKE_SHARED_LINKER_FLAGS="$LINK_FLAGS"
"$CMAKE" --build "$OBJECTS" --parallel "${QUICKJS_BUILD_JOBS:-2}"
TOOL="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
# Package only runtime symbols and sections.
for library in "$OBJECTS/quickjs/libquickjs.so" "$OBJECTS/libquickjs-android.so"; do
  "$TOOL/llvm-strip" --strip-unneeded "$library"
done
OUT="$BUILD/quickjs-android-1.4.6-16k.aar"
python3 - "$AAR" "$OUT" "$OBJECTS" "$ABI" "$TOOL" "$COMMIT" "$SOURCE_SHA256" "$AAR_SHA256" "$SOURCE_URL" "$AAR_URL" "$LINK_FLAGS" "$0" <<'PY'
import hashlib, io, json, pathlib, re, struct, subprocess, sys, tempfile, zipfile
original, output, objects, abi, tools, commit, source_sha, aar_sha, source_url, aar_url, link_flags, script = sys.argv[1:]
output, objects, tools = pathlib.Path(output), pathlib.Path(objects), pathlib.Path(tools)
sha = lambda data: hashlib.sha256(data).hexdigest()
def exports(path):
    text = subprocess.check_output([str(tools / 'llvm-nm'), '-D', '--defined-only', '--format=posix', str(path)], text=True)
    return {line.split()[0] for line in text.splitlines() if line.strip()}
def elf(data):
    assert data[:6] == b'\x7fELF\x02\x01', 'Expected little-endian ELF64'
    machine = struct.unpack_from('<H', data, 18)[0]
    assert machine == {'arm64-v8a': 183, 'x86_64': 62}[abi], 'Wrong ELF ABI'
    phoff = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    loads = []
    for i in range(count):
        header = struct.unpack_from('<IIQQQQQQ', data, phoff + i * size)
        if header[0] == 1:
            offset, address, align = header[2], header[3], header[7]
            assert align == 16384 and offset % align == address % align, 'ELF LOAD must align to 16 KiB'
            loads.append(align)
    assert loads, 'Missing ELF LOAD segments'
    return loads
with zipfile.ZipFile(original) as old:
    assert len(old.namelist()) == len(set(old.namelist())), 'Duplicate original AAR entries'
    entries = {i.filename: old.read(i) for i in old.infolist() if not i.is_dir()}
original_jni = {name: sha(data) for name, data in entries.items() if name.startswith('jni/')}
assert f'jni/{abi}/libquickjs.so' in entries, 'Selected ABI absent from original AAR'
rebuilt = {}
with tempfile.TemporaryDirectory(dir=output.parent, prefix='quickjs-check-') as tmp:
    for name, path in [('libquickjs.so', objects / 'quickjs/libquickjs.so'), ('libquickjs-android.so', objects / 'libquickjs-android.so')]:
        data = path.read_bytes()
        alignments = elf(data)
        old_data = entries[f'jni/{abi}/{name}']
        baseline = pathlib.Path(tmp) / name
        baseline.write_bytes(old_data)
        before, after = exports(baseline), exports(path)
        if name == 'libquickjs-android.so':
            before = {s for s in before if s.startswith('Java_') or s == 'JNI_OnLoad'}
            after = {s for s in after if s.startswith('Java_') or s == 'JNI_OnLoad'}
            assert before and before == after, 'JNI entry points changed'
        else:
            before = {s for s in before if s.startswith(('JS_', 'js_', 'lre_', 'lre', 'bf_', 'dbuf_', 'unicode_'))}
            assert before and before <= after, 'QuickJS public symbols missing'
            assert b'BigNum 2021-03-27 version' in old_data and b'BigNum 2021-03-27 version' in data, 'QuickJS engine version changed'
        dynamic = subprocess.check_output([str(tools / 'llvm-readelf'), '-dW', str(path)], text=True)
        needed = re.findall(r'\(NEEDED\).*?\[(.*?)\]', dynamic)
        assert set(needed) <= {'libquickjs.so', 'liblog.so', 'libm.so', 'libdl.so', 'libc.so'}, f'Unexpected dependency: {needed}'
        assert f'Library soname: [{name}]' in dynamic, 'Unexpected SONAME'
        if name == 'libquickjs-android.so':
            assert 'libquickjs.so' in needed, 'JNI engine dependency missing'
        rebuilt[name] = {'sha256': sha(data), 'load_alignments': alignments, 'needed': needed, 'checked_symbols': len(before)}
        rebuilt[name]['data'] = data
# Exclude unselected ABIs rather than silently retaining their original 4 KiB libraries.
payload = {name: data for name, data in entries.items() if not name.startswith('jni/')}
for name, item in rebuilt.items():
    payload[f'jni/{abi}/{name}'] = item.pop('data')
# Fixed ZIP metadata and entry ordering make packaging deterministic.
buffer = io.BytesIO()
with zipfile.ZipFile(buffer, 'w', compression=zipfile.ZIP_STORED) as aar:
    for name, data in sorted(payload.items()):
        info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
        info.create_system = 3
        info.external_attr = 0o100644 << 16
        aar.writestr(info, data)
output.write_bytes(buffer.getvalue())
with zipfile.ZipFile(output) as aar:
    for name, data in entries.items():
        if not name.startswith('jni/'):
            assert aar.read(name) == data, f'Non-native payload changed: {name}'
manifest = {
    'dependency': 'io.github.taoweiji.quickjs:quickjs-android:1.4.6',
    'source_commit': commit, 'source_archive_url': source_url, 'source_archive_sha256': source_sha,
    'original_aar_url': aar_url, 'original_aar_sha256': aar_sha, 'original_jni_sha256': original_jni,
    'engine_version': '2021-03-27', 'source_patches': [],
    'ndk_revision': '28.2.13676358', 'cmake_version': '3.22.1', 'android_api': 26, 'abi': abi,
    'build_type': 'Release', 'android_stl': 'c++_static', 'shared_linker_flags': link_flags,
    'strip_flags': '--strip-unneeded', 'compiler_debug_info': False,
    'script_sha256': sha(pathlib.Path(script).read_bytes()),
    'preserved_non_jni_sha256': {n: sha(d) for n, d in entries.items() if not n.startswith('jni/')},
    'rebuilt_jni': rebuilt, 'output_aar_sha256': sha(output.read_bytes()),
    'device_runtime_tested': False,
}
provenance = output.with_suffix('.provenance.json')
provenance.write_text(json.dumps(manifest, indent=2, sort_keys=True) + '\n')
output.with_suffix('.sha256').write_text(f'{manifest["output_aar_sha256"]}  {output.name}\n')
print(json.dumps({'aar': str(output), 'sha256': manifest['output_aar_sha256'], 'classes_jar_sha256': manifest['preserved_non_jni_sha256']['classes.jar'], 'abi': abi, 'rebuilt_jni': rebuilt}, indent=2))
PY
printf '[v99] QuickJS 1.4.6 native rebuild complete: %s\n' "$OUT"
