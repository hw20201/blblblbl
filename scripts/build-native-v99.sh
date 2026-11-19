#!/usr/bin/env bash
# Explicit Arca v99 candidate build. The default build-native.sh is intentionally independent.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
CORE_SHA=5d965f69acfaddd6f81bb26feb1cccde102f98db
CORE_ARCHIVE_SHA256=50f518b9656a9f0154b8c16c5798350b9cdcc7278eaae15ad6cb13202d6a5643
CORE_VERSION=v1.19.33-nw3-v99
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}/ndk/28.2.13676358}}"
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
BUILD="$ROOT/native/build-v99"
mkdir -p "$BUILD"
ARCHIVE="$BUILD/mihomo-$CORE_SHA.tar.gz"
if [[ ! -f "$ARCHIVE" ]]; then
  curl --fail --location --retry 3 --output "$ARCHIVE" "https://codeload.github.com/hw20201/mihomo-arca/tar.gz/$CORE_SHA"
fi
printf '%s  %s\n' "$CORE_ARCHIVE_SHA256" "$ARCHIVE" | sha256sum --check --status
PATCHES=(
  "$ROOT/native/patches/mihomo-borrowed-fd.patch"
  "$ROOT/native/patches/mihomo-proxy-only-traffic.patch"
  "$ROOT/native/patches/mihomo-nowhere-v99.patch"
)
PATCH_KEY=$(for core_patch in "${PATCHES[@]}"; do sha256sum "$core_patch" | cut -d ' ' -f 1; done | sha256sum | cut -d ' ' -f 1)
SOURCE="$BUILD/mihomo-$PATCH_KEY"
if [[ ! -f "$SOURCE/.arca-prepared" ]]; then
  # A partial/foreign tree must never be reused or removed implicitly.
  [[ ! -e "$SOURCE" ]] || { echo "Incomplete prepared tree: $SOURCE" >&2; exit 1; }
  mkdir "$SOURCE"
  tar -xzf "$ARCHIVE" --strip-components=1 -C "$SOURCE"
  for core_patch in "${PATCHES[@]}"; do patch --batch --fuzz=0 -d "$SOURCE" -p1 < "$core_patch"; done
  printf '%s\n' "$CORE_SHA" > "$SOURCE/.arca-prepared"
fi
MODFILE="$BUILD/native.go.mod"
cp "$ROOT/native/golang/go.mod" "$MODFILE"
cp "$ROOT/native/golang/go.sum" "$BUILD/native.go.sum"
(cd "$ROOT/native/golang" && go mod edit -modfile="$MODFILE" -go=1.24.0 -replace="github.com/metacubex/mihomo=$SOURCE")
printf '%s\n' "$CORE_SHA" > "$BUILD/core-source-commit.txt"
printf '%s\n' "$CORE_ARCHIVE_SHA256" > "$BUILD/core-source-archive.sha256"
sha256sum "${PATCHES[@]}" > "$BUILD/core-patches.sha256"
if [[ "$PREPARE_ONLY" == true ]]; then
  printf '[v99] Prepared pinned nw3 core: %s\n' "$SOURCE"
  exit 0
fi
TOOL="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
ABI_OUT="$BUILD/out/$ABI"
OUT="$ROOT/app/src/main/jniLibs/$ABI"
mkdir -p "$ABI_OUT" "$OUT"
export CGO_ENABLED=1 GOOS=android GOARCH="$NATIVE_GOARCH"
export CC="$TOOL/${TRIPLE}26-clang"
export CGO_CFLAGS='-D__BIONIC_NO_PAGE_SIZE_MACRO'
BUILD_TIME="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
cd "$ROOT/native/golang"
echo "[v99] Checking Android native integration for $ABI"
go vet -mod=readonly -modfile="$MODFILE" -tags 'foss with_gvisor cmfa' ./...
echo "[v99] Building $ABI core from nw3 $CORE_SHA"
go build -mod=readonly -modfile="$MODFILE" -trimpath -buildmode=c-shared \
  -tags 'foss with_gvisor cmfa' \
  -ldflags="-s -w -buildid= -X github.com/metacubex/mihomo/constant.Version=$CORE_VERSION -X github.com/metacubex/mihomo/constant.BuildTime=$BUILD_TIME -extldflags=-Wl,-z,max-page-size=16384" \
  -o "$ABI_OUT/libclash.so" .
printf '#define GIT_VERSION v1.19.33_nw3_5d965f6_native_v99\n#define make_Str(x) #x\n#define make_String(x) make_Str(x)\n' > "$ABI_OUT/version.h"
"$CC" -std=c11 -fPIC -shared -O2 -I"$ROOT/native/cpp" -I"$ROOT/native/golang" -I"$ABI_OUT" \
  -Wl,-z,max-page-size=16384 -Wl,-soname,libbridge.so -Wl,--no-undefined \
  -o "$ABI_OUT/libbridge.so" "$ROOT/native/cpp/main.c" "$ROOT/native/cpp/jni_helper.c" \
  "$ROOT/native/cpp/bridge_helper.c" "$ROOT/native/golang/trace.c" -L"$ABI_OUT" -lclash -llog
for so in "$ABI_OUT/libclash.so" "$ABI_OUT/libbridge.so"; do
  "$TOOL/llvm-readelf" -lW "$so" | awk '/ LOAD / { found=1; if ($NF != "0x4000") bad=1 } END { exit (!found || bad) }'
done
cp "$ABI_OUT/libclash.so" "$ABI_OUT/libbridge.so" "$OUT/"
if [[ "$ABI" == arm64-v8a ]]; then MANIFEST="$ROOT/native/SHA256SUMS"; else MANIFEST="$ROOT/native/SHA256SUMS.$ABI"; fi
(cd "$ROOT" && sha256sum "app/src/main/jniLibs/$ABI/libclash.so" "app/src/main/jniLibs/$ABI/libbridge.so" > "$MANIFEST")
go version > "$BUILD/go-version.txt"
go version -m "$ABI_OUT/libclash.so" > "$BUILD/go-build-info-$ABI.txt"
echo "[v99] Complete: $ABI matching core and JNI bridge; ELF LOAD alignment 16 KiB"
