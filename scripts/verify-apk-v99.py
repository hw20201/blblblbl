#!/usr/bin/env python3
"""Verify every native library and the pinned payloads in the v99 APK."""
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile


def verify(apk: Path, output: Path) -> None:
    expected = {}
    for line in Path('native/SHA256SUMS').read_text().splitlines():
        digest, name = line.split(maxsplit=1)
        expected[Path(name).name] = digest
    assert set(expected) == {'libclash.so', 'libbridge.so'}, 'Incomplete native pair manifest'

    quickjs_aar = Path('native/build-v99/quickjs-android-1.4.6-16k.aar')
    quickjs = json.loads(quickjs_aar.with_suffix('.provenance.json').read_text())
    assert quickjs['abi'] == 'arm64-v8a', 'QuickJS rebuild has wrong ABI'
    assert quickjs['dependency'] == 'io.github.taoweiji.quickjs:quickjs-android:1.4.6', 'QuickJS dependency changed'
    quickjs_aar_sha256 = hashlib.sha256(quickjs_aar.read_bytes()).hexdigest()
    assert quickjs_aar_sha256 == quickjs['output_aar_sha256'], 'QuickJS AAR provenance mismatch'
    rebuilt = quickjs['rebuilt_jni']
    assert set(rebuilt) == {'libquickjs.so', 'libquickjs-android.so'}, 'Incomplete QuickJS rebuild manifest'
    expected.update({name: detail['sha256'] for name, detail in rebuilt.items()})

    libraries = {}
    with zipfile.ZipFile(apk) as archive:
        entries = [item.filename for item in archive.infolist() if item.filename.startswith('lib/') and item.filename.endswith('.so')]
        assert entries and len(entries) == len(set(entries)), 'Missing or duplicate APK native entries'
        assert all(name.startswith('lib/arm64-v8a/') and name.count('/') == 2 for name in entries), entries
        for name in entries:
            data = archive.read(name)
            assert data[:6] == b'\x7fELF\x02\x01', f'{name}: expected ELF64 little endian'
            machine = struct.unpack_from('<H', data, 18)[0]
            assert machine == 183, f'{name}: unexpected e_machine {machine}'
            phoff = struct.unpack_from('<Q', data, 32)[0]
            phentsize, phnum = struct.unpack_from('<HH', data, 54)
            alignments = []
            for index in range(phnum):
                header = struct.unpack_from('<IIQQQQQQ', data, phoff + index * phentsize)
                if header[0] == 1:
                    offset, address, alignment = header[2], header[3], header[7]
                    assert alignment >= 16384 and (alignment & (alignment - 1)) == 0, (
                        f'{name}: bad LOAD alignment {alignment}'
                    )
                    assert offset % alignment == address % alignment, f'{name}: incongruent LOAD segment'
                    alignments.append(alignment)
            assert alignments, f'{name}: no LOAD segments'
            digest = hashlib.sha256(data).hexdigest()
            basename = Path(name).name
            if basename in expected:
                assert digest == expected[basename], f'{name}: packaged library does not match build manifest'
            libraries[basename] = {'sha256': digest, 'bytes': len(data), 'load_alignments': alignments}
        assert set(expected).issubset(libraries), f'Missing from APK: {set(expected) - set(libraries)}'
    output.mkdir(parents=True, exist_ok=True)
    evidence = {
        'versionCode': 99,
        'versionName': '1.1.0',
        'package': 'com.github.arca.android',
        'abi': 'arm64-v8a',
        'core_base': 'hw20201/mihomo-arca@5d965f69acfaddd6f81bb26feb1cccde102f98db',
        'quickjs_aar_sha256': quickjs_aar_sha256,
        'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
        'apk_bytes': apk.stat().st_size,
        'libraries': libraries,
    }
    (output / 'verification.json').write_text(json.dumps(evidence, indent=2) + '\n')
    print(json.dumps(evidence, indent=2))


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('Usage: verify-apk-v99.py APK OUTPUT_DIRECTORY')
    verify(Path(sys.argv[1]), Path(sys.argv[2]))
