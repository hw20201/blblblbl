package com.github.arca.android.data

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Benchmark-only reproduction of the two original, per-record synchronized repositories. */
private class OriginalRepositories {
    private val nextId = AtomicLong(1)
    private val logs = mutableListOf<LogEntry>()
    private val requests = mutableListOf<LogEntry>()
    private val mutableLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    private val mutableRequests = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = mutableLogs
    val requestsFlow: StateFlow<List<LogEntry>> = mutableRequests
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun addRawLogcat(payload: String) {
        try {
            val obj = json.parseToJsonElement(payload).jsonObject
            fun text(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull
            addLog(text("level") ?: text("type") ?: "info", text("message") ?: text("payload") ?: payload)
        } catch (_: Exception) {
            addLog("info", payload)
        }
    }

    @Synchronized
    private fun addLog(level: String, message: String) {
        val entry = LogEntry(nextId.getAndIncrement(), level = level.lowercase(), message = message)
        logs.add(entry)
        onLog(entry)
        if (logs.size > 1000) logs.removeAt(0)
        mutableLogs.value = logs.toList()
    }

    @Synchronized
    private fun onLog(entry: LogEntry) {
        // Same request test and default enabled monitoring as the original implementation.
        val text = entry.message.trim()
        val protocol = text.startsWith("[TCP]", true) || text.startsWith("[UDP]", true) || text.startsWith("[DNS]", true)
        if (!protocol || !text.contains("-->")) return
        requests.add(entry)
        if (requests.size > 1000) requests.removeAt(0)
        mutableRequests.value = requests.toList()
    }
}

private val allocationBean = (ManagementFactory.getThreadMXBean() as? ThreadMXBean)?.takeIf {
    it.isThreadAllocatedMemorySupported
}?.apply { if (!isThreadAllocatedMemoryEnabled) isThreadAllocatedMemoryEnabled = true }

@Suppress("DEPRECATION") // Keep the harness compatible with JVM 11+ as well as this Java 21 host.
private fun allocatedBytes(): Long? = allocationBean?.getThreadAllocatedBytes(Thread.currentThread().id)?.takeIf { it >= 0 }

private fun inputs(size: Int, prefix: String): List<String> = List(size) { index ->
    val message = if (index % 4 != 0) {
        "[TCP] 192.168.1.2:${20000 + index % 30000} --> $prefix-host$index.example:443 match DomainSuffix using Proxy"
    } else {
        "$prefix diagnostic-$index: updating proxy state and checking network connectivity"
    }
    val level = if (index % 10 == 0) "warning" else "info"
    """{"level":"$level","message":"$message","time":1700000000000}"""
}

/** These synchronous collectors observe every serial update; the timed variants use identical observers. */
private class SnapshotObserver(core: StateFlow<List<LogEntry>>, requests: StateFlow<List<LogEntry>>) {
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    var coreSnapshots = 0L
    var requestSnapshots = 0L
    var copiedReferenceSlots = 0L
    init {
        scope.launch { core.collect { if (it.isNotEmpty()) { coreSnapshots++; copiedReferenceSlots += it.size } } }
        scope.launch { requests.collect { if (it.isNotEmpty()) { requestSnapshots++; copiedReferenceSlots += it.size } } }
    }
    fun close() = scope.cancel()
}

private fun resetNew() {
    LogRepository.clear()
    RequestLogRepository.clear()
    RequestLogRepository.setMonitoringEnabled(true)
}

private fun serial(mode: String, payloads: List<String>): JsonObject {
    resetNew()
    val old = if (mode == "original_per_record") OriginalRepositories() else null
    val core = old?.logsFlow ?: LogRepository.logsFlow
    val requests = old?.requestsFlow ?: RequestLogRepository.requestsFlow
    val observer = SnapshotObserver(core, requests)
    val queue = RawLogQueue()
    val capture = LogStore.capture()
    val beforeAllocation = allocatedBytes()
    val begin = System.nanoTime()
    when (mode) {
        "original_per_record" -> payloads.forEach { old!!.addRawLogcat(it) }
        "current_per_record" -> payloads.forEach { LogRepository.addRawLogcat(it) }
        "current_batch_64" -> {
            payloads.forEach { payload ->
                check(queue.offer(queue.prepare(payload, capture)))
                if (queue.stats().queuedEntries >= 64) LogRepository.addRawLogcatBatch(queue.drain(64))
            }
            val tail = queue.drain(64)
            if (tail.isNotEmpty()) LogRepository.addRawLogcatBatch(tail)
        }
        else -> error("Unknown serial mode: $mode")
    }
    val elapsed = System.nanoTime() - begin
    val afterAllocation = allocatedBytes()
    val expectedCore = if (mode == "current_batch_64") (payloads.size + 63) / 64 else payloads.size
    val expectedRequests = if (mode == "current_batch_64") expectedCore else payloads.indices.count { it % 4 != 0 }
    check(observer.coreSnapshots == expectedCore.toLong()) { "Unexpected core observation count" }
    check(observer.requestSnapshots == expectedRequests.toLong()) { "Unexpected request observation count" }
    check(core.value.size == minOf(1000, payloads.size))
    check(requests.value.size == minOf(1000, payloads.indices.count { it % 4 != 0 }))
    check(core.value.last().message.contains("host${payloads.lastIndex}.example"))
    val result = buildJsonObject {
        put("mode", mode)
        put("input_records", payloads.size)
        put("wall_ms", elapsed / 1_000_000.0)
        put("core_snapshot_count", observer.coreSnapshots)
        put("request_snapshot_count", observer.requestSnapshots)
        put("snapshot_reference_slots_proxy", observer.copiedReferenceSlots)
        if (beforeAllocation != null && afterAllocation != null) put("current_thread_allocated_bytes", afterAllocation - beforeAllocation)
        put("core_history_size", core.value.size)
        put("request_history_size", requests.value.size)
        put("dropped_records", queue.stats().droppedEntries)
    }
    observer.close()
    return result
}

private fun lastId(): Long = LogRepository.logsFlow.value.lastOrNull()?.id ?: 0L

private fun waitForDrain(beforeId: Long, baselineDrops: Long, attempted: Int): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
    while (System.nanoTime() < deadline) {
        val stats = LogRepository.rawLogcatQueueStats
        if (stats.queuedEntries == 0 && lastId() - beforeId + stats.droppedEntries - baselineDrops == attempted.toLong()) return true
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
    }
    return false // Report incompletion; never turn wall time into a performance pass/fail threshold.
}

private fun percentile(sorted: LongArray, percent: Double): Double {
    val index = ((sorted.size - 1) * percent).toInt().coerceIn(0, sorted.lastIndex)
    return sorted[index] / 1000.0
}

private fun async(mode: String, payloads: List<String>, producerCount: Int, paced: Boolean): JsonObject {
    resetNew()
    LogRepository.addLog("info", "benchmark-before-$mode")
    val beforeId = lastId()
    LogRepository.clear()
    val baseline = LogRepository.rawLogcatQueueStats
    val durations = LongArray(payloads.size)
    val accepted = AtomicLong()
    val maxEntries = AtomicLong()
    val maxBytes = AtomicLong()
    val start = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(producerCount)
    val begin = System.nanoTime()
    val jobs = (0 until producerCount).map { producer ->
        executor.submit {
            check(start.await(30, TimeUnit.SECONDS))
            var ownCount = 0
            var index = producer
            while (index < payloads.size) {
                val callBegin = System.nanoTime()
                if (LogRepository.enqueueRawLogcat(payloads[index])) accepted.incrementAndGet()
                durations[index] = System.nanoTime() - callBegin
                val stats = LogRepository.rawLogcatQueueStats
                maxEntries.accumulateAndGet(stats.queuedEntries.toLong(), ::maxOf)
                maxBytes.accumulateAndGet(stats.queuedBytes.toLong(), ::maxOf)
                ownCount++
                if (paced && ownCount % 8 == 0) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10))
                index += producerCount
            }
        }
    }
    start.countDown()
    var producersCompleted = true
    try {
        jobs.forEach { it.get(30, TimeUnit.SECONDS) }
    } catch (_: java.util.concurrent.TimeoutException) {
        producersCompleted = false
    } finally {
        executor.shutdownNow()
    }
    val enqueueEnd = System.nanoTime()
    val drained = producersCompleted && waitForDrain(beforeId, baseline.droppedEntries, payloads.size)
    val end = System.nanoTime()
    val stats = LogRepository.rawLogcatQueueStats
    val processed = lastId() - beforeId
    val drops = stats.droppedEntries - baseline.droppedEntries
    durations.sort()
    check(maxEntries.get() <= 512 && maxBytes.get() <= 512 * 1024)
    return buildJsonObject {
        put("mode", mode)
        put("input_records", payloads.size)
        put("producer_threads", producerCount)
        put("pacing", if (paced) "8 records then 10ms park" else "unpaced burst")
        put("producer_wall_ms", (enqueueEnd - begin) / 1_000_000.0)
        put("end_to_end_wall_ms", (end - begin) / 1_000_000.0)
        put("drain_after_producers_ms", (end - enqueueEnd) / 1_000_000.0)
        put("enqueue_p50_us", percentile(durations, .50))
        put("enqueue_p95_us", percentile(durations, .95))
        put("enqueue_p99_us", percentile(durations, .99))
        put("accepted_arrivals", accepted.get())
        put("rejected_arrivals", payloads.size - accepted.get())
        put("processed_records", processed)
        put("dropped_records", drops)
        put("drop_fraction", drops.toDouble() / payloads.size)
        put("truncated_records", stats.truncatedEntries - baseline.truncatedEntries)
        put("observed_max_queued_entries", maxEntries.get())
        put("observed_max_queued_bytes", maxBytes.get())
        put("final_queued_entries", stats.queuedEntries)
        put("final_queued_bytes", stats.queuedBytes)
        put("core_history_size", LogRepository.logsFlow.value.size)
        put("request_history_size", RequestLogRepository.requestsFlow.value.size)
        put("completed", drained)
        put("accounting_balanced", processed + drops == payloads.size.toLong())
    }
}

fun main(arguments: Array<String>) {
    val round = arguments.firstOrNull()?.toInt() ?: 1
    val serialInputs = inputs(12_800, "serial")
    val lowInputs = inputs(256, "low")
    val highInputs = inputs(50_000, "high")
    val modes = listOf("original_per_record", "current_per_record", "current_batch_64")
    // Two unreported warmups per mode; input construction, JVM startup, and compilation are excluded.
    repeat(2) { modes.forEach { serial(it, serialInputs.take(3200)) } }
    // Start the process-lifetime async worker before any reported async sample.
    async("async_warmup", inputs(128, "warmup"), 1, false)
    val orderedModes = modes.drop((round - 1) % 3) + modes.take((round - 1) % 3)
    val serialResults = orderedModes.map { serial(it, serialInputs) }
    val asyncResults = listOf(async("async_low_paced", lowInputs, 1, true), async("async_high_burst", highInputs, 4, false))
    println(buildJsonObject {
        put("round", round)
        put("jvm", System.getProperty("java.runtime.version"))
        put("vm", System.getProperty("java.vm.name"))
        put("os", System.getProperty("os.name"))
        put("arch", System.getProperty("os.arch"))
        put("available_processors", Runtime.getRuntime().availableProcessors())
        put("max_heap_bytes", Runtime.getRuntime().maxMemory())
        put("thread_allocation_supported", allocationBean != null)
        put("serial", buildJsonArray { serialResults.forEach { add(it) } })
        put("async", buildJsonArray { asyncResults.forEach { add(it) } })
    })
}
