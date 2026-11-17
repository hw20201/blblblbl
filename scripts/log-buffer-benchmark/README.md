# Log buffer JVM benchmark

Run from any directory with the existing local Kotlin/Gradle dependency cache:

```sh
python3 scripts/log-buffer-benchmark/run.py
```

The runner does not invoke Gradle, download dependencies, or modify production code. It compiles the four production logging Kotlin files together with the benchmark using cached Kotlin 2.3.0, coroutines 1.9.0, and serialization 1.7.3. All generated classes, logs and reports go to the sibling `arca-v98-delivery/validation/` directory. Missing cached dependencies fail explicitly.

Five isolated JVM processes each run three lossless serial variants over 12,800 prebuilt JSON envelopes: benchmark-only reproduction of the original per-record implementation, the current synchronous compatibility entry point, and the actual current queue plus batch API manually drained every 64 records. The same collectors record snapshot counts and sum snapshot list lengths as a logical allocation proxy. ThreadMXBean also measures calling-thread allocated bytes if the JVM supports it. Measurements include parsing, producer-side queue preparation for the batch variant, and observer overhead; they exclude input creation and compilation. Two warmups per variant precede each fork, and measurement order rotates.

Each process additionally exercises the actual public asynchronous `enqueueRawLogcat` with its real consumer: 256 paced records from one producer and 50,000 unpaced arrivals from four producers. It reports call latency percentiles, producer and end-to-end wall time, accepted/rejected arrivals, processed/dropped totals, truncation and sampled queue maxima. Completion is an accounting check, not a fixed speed target. Because overflow discards work, the asynchronous wall time is not a lossless speed comparison.

The JSON includes every sample, medians, dependency paths and source hashes. The Markdown includes the tables and limitations. This is a synthetic JVM experiment: it does not measure Android rendering, JNI overhead, retained device memory, network behavior or battery consumption.
