# Arca v99 — nw3 / Nowhere integration

## Source and build identity

- App base: `hw20201/blblblbl@92d976df4d000b5fe08472435f4c273995756b22`; isolated branch `v99-nowhere-core`.
- Core base: `hw20201/mihomo-arca@5d965f69acfaddd6f81bb26feb1cccde102f98db` on `nw3`, based on Mihomo v1.19.33 and Nowhere 3.3.0.
- Android identity: `com.github.arca.android`, versionName `1.1.0`, versionCode `99`; primary delivery is ARM64.
- Go 1.27.4, NDK 28.2.13676358, API 26 native target; SDK 37.0 and build-tools 36.0.0.
- `scripts/build-native-v99.sh` is an explicit v99 build entry. It verifies the source archive SHA-256, applies the existing borrowed-FD/traffic patches plus `native/patches/mihomo-nowhere-v99.patch`, and prepares a private module replacement. It builds the matching core and JNI bridge pair with 16 KiB LOAD alignment.
- `scripts/build-native.sh`, the default Go module dependency and the default upstream source remain unchanged. Neither core PR #1 nor either repository's main branch is merged or updated for this release.

## Core changes

- Reject UDP ports outside 1..65535 before narrowing them or looking up cached flow destinations.
- Preserve the cancellation reason when a new target setup is canceled. An elapsed write deadline remains a deadline error even if the caller clears or extends the deadline before the dialer returns. Closing the association cancels in-flight setup.
- Keep cached target writes on a path without per-packet setup contexts or cancellation goroutines.
- Reuse bounded serialized Morph TCP/packet write scratch; use a per-call pooled 65535-byte Morph read buffer and copy plaintext before returning it to the pool. Keep concurrent packet reads supported.
- Retain UoT payload capacity while preserving framing and resume-after-timeout behavior.
- Authentication, wire format and protocol options are unchanged.

## Measured validation

Tests run on `ai-x86-01` using the exact pinned source and the checksum-verified Rust Nowhere 3.3.0 reference. Linux protocol, Morph and adapter race tests and the ordinary `with_gvisor` core build pass. The deadline-reset cancellation regression is repeated 20 times. Native TUN regression and Android cross-build results are recorded in the delivery evidence and GitHub Actions.

Five repetitions of the same microbenchmarks were run on the same machine, sequentially on baseline and optimized sources. The tests/benchmarks are identical across both trees. Values are medians; `ns/op` is per operation and these are local in-memory microbenchmarks, not internet download measurements.

| Benchmark | Baseline ns/op | v99 ns/op | Baseline B/op | v99 B/op | Baseline allocs/op | v99 allocs/op |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Cached UDP target write | 1222 | 440.8 | 168 | 24 | 4 | 1 |
| UoT read, 1200 bytes | 313.2 | 63.91 | 1280 | 0 | 1 | 0 |
| Morph TCP write, 1200 bytes | 2971 | 2546 | 1280 | 0 | 1 | 0 |
| Morph TCP write, 32768 bytes | 70624 | 67689 | 32768 | 1 | 1 | 0 |
| Morph packet read | 7978 | 2613 | 65536 | 0 | 1 | 0 |
| Morph packet write | 2848 | 2635 | 1280 | 0 | 1 | 0 |

The earlier user-supplied Cachefly results of TCP 105.5 MB/s and UDP 51.95 MB/s are historical validation of nw3. They were not reproduced or used as v99 performance claims in this task.

## GitHub packaging and verification

`.github/workflows/v99-nowhere.yml` runs core races with the real reference server, TUN tests, app static analysis and all app unit tests, then builds the native pair and signs the R8 release APK using the repository's existing release signing secrets. The workflow only has read access to repository contents and never creates tags or Releases.

The APK must pass signature, package/version, non-debuggable ARM64, ZIP alignment, embedded library hash, and ELF LOAD alignment checks before upload. The fixed signing certificate SHA-256 is `e73785b2afe10e9e3181c6cc337a1da5900edf9751896e21790d4d3ddc7a1ef2`. CI retains the APK, source/workflow identities, core provenance, checksums, native pair, test reports and R8 mapping for review.

The release evidence is authoritative for final job conclusions and APK hashes. The R8 build is not merged into main by this task; physical-device and remote real-node throughput validation are separate from the host interoperability tests above.
