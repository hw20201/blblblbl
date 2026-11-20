#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}/ndk/28.2.13676358}}"
API=26
ABI=arm64-v8a
PREPARE_ONLY=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --abi) ABI="${2:?--abi requires arm64-v8a or x86_64}"; shift 2 ;;
    --prepare-only) PREPARE_ONLY=true; shift ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done
case "$ABI" in
  arm64-v8a) NATIVE_GOARCH=arm64; TRIPLE=aarch64-linux-android ;;
  x86_64) NATIVE_GOARCH=amd64; TRIPLE=x86_64-linux-android ;;
  *) echo "Unsupported ABI: $ABI" >&2; exit 2 ;;
esac
TOOL="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
BUILD="$ROOT/native/build"
ABI_OUT="$BUILD/out/$ABI"
OUT="$ROOT/app/src/main/jniLibs/$ABI"
mkdir -p "$ABI_OUT" "$OUT"
cd "$ROOT/native/golang"
echo '[native] Preparing pinned Mihomo v1.19.33 source'
SOURCE=$(go mod download -json github.com/metacubex/mihomo@v1.19.33 | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["Dir"])')
# Re-prepare only when the exact local patches change. Never alter the shared module cache.
PATCH_KEY=$(sha256sum "$ROOT/native/patches/mihomo-borrowed-fd.patch" "$ROOT/native/patches/mihomo-proxy-only-traffic.patch" "$ROOT/native/patches/mihomo-inspection-assets.patch" | sha256sum | cut -d ' ' -f 1)
if [[ ! -f "$BUILD/mihomo/.arca-patch-$PATCH_KEY" ]]; then
  # Check the resolved path before removing an earlier, private prepared tree.
  TARGET=$(realpath -m "$BUILD/mihomo")
  [[ "$TARGET" == "$ROOT/native/build/mihomo" && ! -L "$BUILD/mihomo" ]] || { echo 'Unsafe prepared source path' >&2; exit 1; }
  if [[ -e "$TARGET" ]]; then rm -rf -- "$TARGET"; fi
  cp -R "$SOURCE" "$TARGET"
  chmod -R u+w "$TARGET"
  patch -d "$TARGET" -p1 < "$ROOT/native/patches/mihomo-borrowed-fd.patch"
  patch -d "$TARGET" -p1 < "$ROOT/native/patches/mihomo-proxy-only-traffic.patch"
  patch -d "$TARGET" -p1 < "$ROOT/native/patches/mihomo-inspection-assets.patch"
  touch "$TARGET/.arca-patch-$PATCH_KEY"
fi
cp go.mod "$BUILD/native.go.mod"
cp go.sum "$BUILD/native.go.sum"
go mod edit -modfile="$BUILD/native.go.mod" -replace="github.com/metacubex/mihomo=$BUILD/mihomo"
if [[ "$PREPARE_ONLY" == true ]]; then exit 0; fi
export CGO_ENABLED=1 GOOS=android GOARCH="$NATIVE_GOARCH"
export CC="$TOOL/${TRIPLE}${API}-clang"
export CGO_CFLAGS='-D__BIONIC_NO_PAGE_SIZE_MACRO'
echo "[native] Building $ABI Go core"
go build -mod=readonly -modfile="$BUILD/native.go.mod" -trimpath -buildmode=c-shared \
  -tags 'foss with_gvisor cmfa' -ldflags='-s -w -buildid= -extldflags=-Wl,-z,max-page-size=16384' \
  -o "$ABI_OUT/libclash.so" .
printf '#define GIT_VERSION v1.19.33_3f27ef3_native-v98\n#define make_Str(x) #x\n#define make_String(x) make_Str(x)\n' > "$ABI_OUT/version.h"
echo '[native] Building checked JNI bridge'
"$CC" -std=c11 -fPIC -shared -O2 -I"$ROOT/native/cpp" -I"$ROOT/native/golang" -I"$ABI_OUT" \
  -Wl,-z,max-page-size=16384 -Wl,-soname,libbridge.so -Wl,--no-undefined \
  -o "$ABI_OUT/libbridge.so" "$ROOT/native/cpp/main.c" "$ROOT/native/cpp/jni_helper.c" \
  "$ROOT/native/cpp/bridge_helper.c" "$ROOT/native/golang/trace.c" -L"$ABI_OUT" -lclash -llog -landroid
for so in "$ABI_OUT/libclash.so" "$ABI_OUT/libbridge.so"; do
  "$TOOL/llvm-readelf" -lW "$so" | awk '/ LOAD / { found=1; if ($NF != "0x4000") bad=1 } END { exit (!found || bad) }'
done
cp "$ABI_OUT/libclash.so" "$ABI_OUT/libbridge.so" "$OUT/"
if [[ "$ABI" == arm64-v8a ]]; then MANIFEST="$ROOT/native/SHA256SUMS"; else MANIFEST="$ROOT/native/SHA256SUMS.$ABI"; fi
(cd "$ROOT" && sha256sum "app/src/main/jniLibs/$ABI/libclash.so" "app/src/main/jniLibs/$ABI/libbridge.so" > "$MANIFEST")
echo "[native] Complete: $ABI matching core + bridge; all LOAD segments aligned to 16 KiB"
