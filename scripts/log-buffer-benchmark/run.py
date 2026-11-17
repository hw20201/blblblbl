#!/usr/bin/env python3
"""Compile cached Kotlin dependencies and run five isolated JVM benchmark rounds; no Gradle."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import statistics
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT.parent / "arca-v98-delivery" / "validation"
CACHE = Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1"
ROUNDS = 5


def cached(group: str, artifact: str, version: str) -> Path:
    candidates = sorted((CACHE / group / artifact / version).glob(f"*/{artifact}-{version}.jar"))
    if len(candidates) != 1:
        raise RuntimeError(f"Expected one cached {group}:{artifact}:{version}; found {len(candidates)}. No dependencies will be installed.")
    return candidates[0]


def save(path: Path, text: str) -> None:
    # Inspect an existing report before replacing it. Run-specific raw files are never reused.
    if path.exists():
        path.read_text(encoding="utf-8")
    path.write_text(text, encoding="utf-8")


def median_rows(samples: list[dict], section: str) -> dict:
    modes = sorted({item["mode"] for sample in samples for item in sample[section]})
    result = {}
    for mode in modes:
        rows = [next(row for row in sample[section] if row["mode"] == mode) for sample in samples]
        numeric = {key for row in rows for key, value in row.items() if isinstance(value, (int, float)) and not isinstance(value, bool)}
        result[mode] = {key: statistics.median(row[key] for row in rows if key in row) for key in sorted(numeric)}
        result[mode]["all_completed"] = all(row.get("completed", True) for row in rows)
        result[mode]["all_accounting_balanced"] = all(row.get("accounting_balanced", True) for row in rows)
    return result


def markdown(report: dict) -> str:
    medians = report["medians"]
    lines = [
        "# JVM synthetic log buffer benchmark",
        "",
        f"Generated: {report['generated_at_utc']}. Five separate JVM processes; medians below, all samples in this report and the adjacent JSON.",
        "",
        "Host measurements describe this JVM workload. They do not establish Android UI, battery, device memory, or production latency improvements.",
        "",
        "## Method",
        "",
        "- Kotlin 2.3.0, coroutines 1.9.0, serialization 1.7.3 from the existing Gradle cache; no Gradle or dependency installation.",
        "- 12,800 prebuilt JSON envelopes per serial mode, 75% proxy requests, 10% warnings, histories capped at 1,000. The original algorithm is reproduced only in the benchmark, including mutable lists, removeAt(0), per-record parsing and both StateFlow snapshots.",
        "- Two 3,200-record warmups per serial mode. Measurement order rotates each process. All serial modes attach identical synchronous snapshot counters; observer work is included in wall time and thread allocation.",
        "- current_batch_64 includes bounded queue preparation, offer/drain, actual production JSON parsing, and production batch publication. It manually drains each 64 records so the serial comparison processes identical input without overflow or scheduling delay.",
        "- snapshot_reference_slots_proxy sums list lengths observed on every serial core/request snapshot. It measures logical snapshot reference slots, not actual JVM bytes or all allocations. The collector overhead is included equally; current_thread_allocated_bytes uses ThreadMXBean when supported and covers the calling thread only.",
        "- Async uses the real public enqueueRawLogcat and real background consumer without observers: low load is 256 records, one producer, 8 arrivals then 10 ms park; high load is 50,000 unpaced records from four producers. Enqueue latency excludes the separately sampled queue statistics, while producer wall time includes setup/stat sampling/pacing.",
        "- Completion requires published ID delta + overflow-drop delta = attempted input and an empty queue. A 30-second deadline reports incomplete rather than imposing a speed gate. Queue maxima are sampled lower bounds; accepted arrivals may later be evicted. drops include evictions plus immediate rejection.",
        "",
        "## Serial medians",
        "",
        "| Mode | Wall ms | Core snapshots | Request snapshots | Snapshot reference slots proxy | Calling thread allocated bytes |",
        "|---|---:|---:|---:|---:|---:|",
    ]
    for mode, row in medians["serial"].items():
        allocated = row.get("current_thread_allocated_bytes", "unavailable")
        lines.append(f"| {mode} | {row['wall_ms']:.3f} | {row['core_snapshot_count']:g} | {row['request_snapshot_count']:g} | {row['snapshot_reference_slots_proxy']:g} | {allocated} |")
    lines += [
        "", "## Real async medians", "",
        "| Mode | Producer ms | End-to-end ms | Enqueue p50 / p95 / p99 µs | Processed | Dropped | Drop % | All completed / balanced |",
        "|---|---:|---:|---:|---:|---:|---:|---|",
    ]
    for mode, row in medians["async"].items():
        lines.append(f"| {mode} | {row['producer_wall_ms']:.3f} | {row['end_to_end_wall_ms']:.3f} | {row['enqueue_p50_us']:.3f} / {row['enqueue_p95_us']:.3f} / {row['enqueue_p99_us']:.3f} | {row['processed_records']:g} | {row['dropped_records']:g} | {row['drop_fraction'] * 100:.3f} | {row['all_completed']} / {row['all_accounting_balanced']} |")
    lines += ["", "## All serial samples", "", "| Round | Mode | Wall ms | Core / request snapshots | Snapshot slots proxy | Allocated bytes |", "|---:|---|---:|---:|---:|---:|"]
    for sample in report["samples"]:
        for row in sample["serial"]:
            lines.append(f"| {sample['round']} | {row['mode']} | {row['wall_ms']:.3f} | {row['core_snapshot_count']} / {row['request_snapshot_count']} | {row['snapshot_reference_slots_proxy']} | {row.get('current_thread_allocated_bytes', 'unavailable')} |")
    lines += ["", "## All async samples", "", "| Round | Mode | Producer / total ms | p50 / p95 / p99 µs | Processed / dropped / rejected | Max queue entries / bytes observed | Completed / balanced |", "|---:|---|---:|---:|---:|---:|---|"]
    for sample in report["samples"]:
        for row in sample["async"]:
            lines.append(f"| {sample['round']} | {row['mode']} | {row['producer_wall_ms']:.3f} / {row['end_to_end_wall_ms']:.3f} | {row['enqueue_p50_us']:.3f} / {row['enqueue_p95_us']:.3f} / {row['enqueue_p99_us']:.3f} | {row['processed_records']} / {row['dropped_records']} / {row['rejected_arrivals']} | {row['observed_max_queued_entries']} / {row['observed_max_queued_bytes']} | {row['completed']} / {row['accounting_balanced']} |")
    env = report["samples"][0]
    lines += ["", "## Environment and limits", "", f"- {env['vm']} {env['jvm']}, {env['os']} {env['arch']}, {env['available_processors']} available processors, max heap {env['max_heap_bytes']} bytes.", "- Five forks are small samples; JIT/GC, host contention, counter overhead, and the synthetic message mix affect timings. Per-mode medians are independent; do not combine them as if from one run.", "- Async processing omits dropped work by design. Its timing cannot be compared directly to the lossless serial wall time as a throughput speedup.", "- Thread allocated bytes omit async worker allocations and do not measure retained heap. Snapshot proxy omits parser, queue, LogEntry and flow wrapper allocations.", "- Native JNI callback, VPN traffic, Compose rendering, network behavior, device power and battery were not exercised.", "- Full JSON contains all counters, raw timings, source SHA-256 hashes and dependency paths. Raw fork logs and compiled classes remain in the run directory for review.", ""]
    return "\n".join(lines)


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    run_dir = OUTPUT / ("log-buffer-benchmark-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ"))
    run_dir.mkdir()
    classes = run_dir / "classes"
    classes.mkdir()
    stdlib = cached("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.0")
    annotations = cached("org.jetbrains", "annotations", "13.0")
    coroutines = cached("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.9.0")
    runtime = [stdlib, annotations, coroutines, cached("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm", "1.7.3"), cached("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm", "1.7.3")]
    compiler = [cached("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.3.0"), stdlib, cached("org.jetbrains.kotlin", "kotlin-script-runtime", "2.3.0"), cached("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"), cached("org.jetbrains.intellij.deps", "trove4j", "1.0.20200330"), annotations, coroutines]
    sources = [ROOT / "app/src/main/java/com/github/arca/android/data" / (name + ".kt") for name in ["LogRepository", "RequestLogRepository", "LogBuffer", "LogStore"]]
    sources.append(Path(__file__).with_name("Benchmark.kt"))
    runtime_cp = os.pathsep.join(map(str, runtime))
    java_base = ["java", "-XX:-UsePerfData", f"-Djava.io.tmpdir={run_dir}"]
    compile_command = java_base + ["-cp", os.pathsep.join(map(str, compiler)), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-Werror", "-jvm-target", "11", "-classpath", runtime_cp, "-d", str(classes)] + list(map(str, sources))
    compiled = subprocess.run(compile_command, cwd=ROOT, capture_output=True, text=True)
    save(run_dir / "compile.log", compiled.stdout + compiled.stderr)
    if compiled.returncode:
        print(compiled.stdout + compiled.stderr, file=sys.stderr)
        raise SystemExit(compiled.returncode)
    samples = []
    for round_number in range(1, ROUNDS + 1):
        print(f"Running isolated JVM benchmark round {round_number}/{ROUNDS}", flush=True)
        result = subprocess.run(java_base + ["-Xms256m", "-Xmx512m", "-cp", str(classes) + os.pathsep + runtime_cp, "com.github.arca.android.data.BenchmarkKt", str(round_number)], cwd=ROOT, capture_output=True, text=True, timeout=180)
        save(run_dir / f"round-{round_number}.stdout.json", result.stdout)
        save(run_dir / f"round-{round_number}.stderr.log", result.stderr)
        if result.returncode:
            print(result.stdout + result.stderr, file=sys.stderr)
            raise SystemExit(result.returncode)
        samples.append(json.loads(result.stdout))
    report = {
        "schema_version": 1,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "run_directory": str(run_dir),
        "method": "Five isolated JVM forks, rotating serial mode order, two per-mode warmups; lossless manual 64-record batch comparison plus real low/high load async entry point.",
        "source_sha256": {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources + [Path(__file__)]},
        "runtime_dependencies": list(map(str, runtime)),
        "compiler_dependencies": list(map(str, compiler)),
        "samples": samples,
        "medians": {section: median_rows(samples, section) for section in ["serial", "async"]},
        "limitations": ["Synthetic host JVM workload; no device, UI or power measurement", "Snapshot reference slots are a logical allocation proxy", "ThreadMXBean measures serial caller allocations only", "Async losses make direct serial/async speedup comparisons invalid", "Sampled queue maxima are lower bounds; completion deadlines are not performance gates"],
    }
    json_path = OUTPUT / "log-buffer-benchmark.json"
    markdown_path = OUTPUT / "log-buffer-benchmark.md"
    save(json_path, json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    save(markdown_path, markdown(report))
    print(f"JSON: {json_path}\nMarkdown: {markdown_path}\nRaw evidence: {run_dir}")


if __name__ == "__main__":
    main()
