# Nowhere Android bridge

This integration runs the official Nowhere `Vector` Rust library (not an emulated Mihomo proxy type) inside ArCa's isolated `:nowhere` Android service process. The service must bind that process to a verified non-VPN Android `Network` before entering the JNI runtime; the native Vector sockets are created in the same process so they use that binding.

The upstream source is the unmodified `NodePassProject/Nowhere` Git submodule pinned to v3.3.0, commit `000007b2482b8acf3a609888921fd847cad56c33`. It is licensed `GPL-3.0-only`; retain its notices and provide the pinned source together with any distributed native binary. The JNI wrapper in `native/nowhere-android` is also GPL-3.0-only.

The Android JNI library is AArch64-only and must be built with the Android NDK linker. It uses a 16 KiB maximum ELF page size; the build script rejects any `PT_LOAD` segment below 16 KiB alignment. The standard `scripts/build-nowhere-android.sh` script builds `aarch64-linux-android` and writes the library to `app/src/main/jniLibs/arm64-v8a/libnowhere_android.so`, along with a separate checksum manifest.

The upstream project does not advertise Android as a supported platform. A successful cross-compile is not device compatibility validation. The test build must still be exercised on Android for process binding, loopback SOCKS, VPN routing, TCP, UDP, Wi-Fi/cellular handoff, cancellation and R8 behavior before broad release.
