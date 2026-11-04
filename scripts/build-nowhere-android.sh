#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UPSTREAM="$ROOT/native/nowhere"
BRIDGE="$ROOT/native/nowhere-android"
TARGET="aarch64-linux-android"
PIN="000007b2482b8acf3a609888921fd847cad56c33"

if [[ "$(uname -m)" != "x86_64" ]]; then
  echo "Android NDK host tools are expected on an x86_64 build runner; found $(uname -m)." >&2
  exit 2
fi
if [[ ! -d "$UPSTREAM/.git" && ! -f "$UPSTREAM/.git" ]]; then
  echo "Nowhere source submodule is missing; initialize recursive submodules first." >&2
  exit 2
fi
if [[ "$(git -C "$UPSTREAM" rev-parse HEAD)" != "$PIN" ]]; then
  echo "Nowhere source is not the pinned v3.3.0 commit $PIN." >&2
  exit 2
fi
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$NDK" || ! -x "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang" ]]; then
  echo "Set ANDROID_NDK_HOME to an Android NDK with the linux-x86_64 toolchain." >&2
  exit 2
fi

NDK_BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
ANDROID_CLANG="$NDK_BIN/aarch64-linux-android26-clang"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_CLANG"
export CC_aarch64_linux_android="$ANDROID_CLANG"
export AR_aarch64_linux_android="$NDK_BIN/llvm-ar"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384"
export CARGO_TARGET_DIR="$ROOT/target"

if [[ ! -f "$BRIDGE/Cargo.lock" ]]; then
  cargo generate-lockfile --manifest-path "$BRIDGE/Cargo.toml"
fi
cargo build --manifest-path "$BRIDGE/Cargo.toml" --locked --release --target "$TARGET"

LIB="$ROOT/target/$TARGET/release/libnowhere_android.so"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a/libnowhere_android.so"
mkdir -p "$(dirname "$OUT")"
cp "$LIB" "$OUT"

readelf -h "$OUT" | grep -q 'Class:.*ELF64'
readelf -h "$OUT" | grep -q 'Machine:.*AArch64'
readelf -h "$OUT" | grep -q 'Type:.*DYN'
readelf --dyn-syms "$OUT" | grep -Fq 'Java_com_github_arca_android_service_NowhereNative_run'
readelf -lW "$OUT" | python3 -c '
import sys
alignments = [int(line.split()[-1], 0) for line in sys.stdin if line.split()[:1] == ["LOAD"]]
if not alignments:
    raise SystemExit("no PT_LOAD segments found")
if any(value < 16384 for value in alignments):
    raise SystemExit(f"PT_LOAD alignment below 16 KiB: {alignments}")
'

MANIFEST="$ROOT/native/NOWHERE-SHA256SUMS"
ACTUAL="$(sha256sum "$OUT" | sed "s#  $ROOT/##")"
if [[ -f "$MANIFEST" ]]; then
  EXPECTED="$(cat "$MANIFEST")"
  if [[ "$EXPECTED" != "$ACTUAL" ]]; then
    echo "Rebuilt Nowhere JNI library does not match the committed SHA-256 manifest." >&2
    echo "Expected: $EXPECTED" >&2
    echo "Actual:   $ACTUAL" >&2
    exit 1
  fi
else
  printf '%s\n' "$ACTUAL" > "$MANIFEST"
fi
printf 'Built and verified %s\n' "$OUT"
readelf -h "$OUT" | grep -E 'Class:|Machine:|Type:'
readelf -lW "$OUT" | awk '$1 == "LOAD" { print "PT_LOAD alignment:", $NF }'
cat "$ROOT/native/NOWHERE-SHA256SUMS"
