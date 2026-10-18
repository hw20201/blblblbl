#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}/ndk/28.2.13676358}}"
API=26
TOOL="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
BUILD="$ROOT/native/build"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$BUILD/out" "$OUT"
cd "$ROOT/native/golang"
echo '[native] Preparing pinned Mihomo v1.19.32 source'
SOURCE=$(go mod download -json github.com/metacubex/mihomo@v1.19.32 | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["Dir"])')
rm -rf "$BUILD/mihomo"
cp -R "$SOURCE" "$BUILD/mihomo"
chmod -R u+w "$BUILD/mihomo"
patch -d "$BUILD/mihomo" -p1 < "$ROOT/native/patches/mihomo-borrowed-fd.patch"
cp go.mod "$BUILD/native.go.mod"
cp go.sum "$BUILD/native.go.sum"
go mod edit -modfile="$BUILD/native.go.mod" -replace="github.com/metacubex/mihomo=$BUILD/mihomo"
if [[ "${1:-}" == '--prepare-only' ]]; then exit 0; fi
export CGO_ENABLED=1 GOOS=android GOARCH=arm64
export CC="$TOOL/aarch64-linux-android${API}-clang"
export CGO_CFLAGS='-D__BIONIC_NO_PAGE_SIZE_MACRO'
echo '[native] Building ARM64 Go core (may take several minutes)'
go build -mod=readonly -modfile="$BUILD/native.go.mod" -trimpath -buildmode=c-shared \
  -tags 'foss with_gvisor cmfa' -ldflags='-s -w -buildid= -extldflags=-Wl,-z,max-page-size=16384' \
  -o "$BUILD/out/libclash.so" .
printf '#define GIT_VERSION v1.19.32_88dcbf7_native-mips2\n#define make_Str(x) #x\n#define make_String(x) make_Str(x)\n' > "$BUILD/out/version.h"
echo '[native] Building checked JNI bridge'
"$CC" -fPIC -shared -O2 -I"$ROOT/native/cpp" -I"$ROOT/native/golang" -I"$BUILD/out" \
  -Wl,-z,max-page-size=16384 -Wl,-soname,libbridge.so -Wl,--no-undefined \
  -o "$BUILD/out/libbridge.so" "$ROOT/native/cpp/main.c" "$ROOT/native/cpp/jni_helper.c" \
  "$ROOT/native/cpp/bridge_helper.c" "$ROOT/native/golang/trace.c" -L"$BUILD/out" -lclash -llog
for so in "$BUILD/out/libclash.so" "$BUILD/out/libbridge.so"; do
  "$TOOL/llvm-readelf" -lW "$so" | awk '/ LOAD / { found=1; if ($NF != "0x4000") bad=1 } END { exit (!found || bad) }'
done
cp "$BUILD/out/libclash.so" "$BUILD/out/libbridge.so" "$OUT/"
(cd "$ROOT" && sha256sum app/src/main/jniLibs/arm64-v8a/lib{clash,bridge}.so > native/SHA256SUMS)
echo '[native] Complete: matching core + bridge; all LOAD segments aligned to 16 KiB'
