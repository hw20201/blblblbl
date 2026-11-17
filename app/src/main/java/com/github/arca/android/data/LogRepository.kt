package com.github.arca.android.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Immutable diagnostic row; native queue timestamps reflect arrival time. */
data class LogEntry(
    val id: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // info, warning, error, debug
    val message: String
)

object LogRepository {
    val logsFlow: StateFlow<List<LogEntry>> = LogStore.logsFlow
    private val json = Json { ignoreUnknownKeys = true }
    private val rawQueue = RawLogQueue()
    private val nativeBatcher by lazy {
        RawLogBatcher(
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
            rawQueue,
            ::addRawLogcatBatch
        )
    }

    /**
     * Native callback entry point. It bounds and queues the envelope without JSON parsing
     * or history publication. One consumer parses/publishes after 50 ms or 64 arrivals.
     * Monitoring is captured on arrival, including while no UI is collecting the flows.
     * Returns false if this arrival is dropped by overflow or superseded by clear().
     */
    fun enqueueRawLogcat(payload: String): Boolean {
        val pending = rawQueue.prepare(payload, LogStore.capture())
        val accepted = synchronized(LogStore.lock) {
            LogStore.isCurrentLocked(pending.capture) && rawQueue.offer(pending)
        }
        if (accepted) nativeBatcher.signal()
        return accepted
    }

    /** Cumulative overflow/truncation counters; an explicit clear is not an overflow. */
    val rawLogcatQueueStats: LogQueueStats get() = rawQueue.stats()

    /** Synchronous compatibility entry point: both histories are visible on return. */
    fun addRawLogcat(payload: String) {
        val capture = LogStore.capture()
        LogStore.append(listOf(parseRawLogcat(payload, capture)))
    }

    /** Synchronous compatibility entry point: both histories are visible on return. */
    fun addLog(level: String, message: String) {
        val capture = LogStore.capture()
        LogStore.append(listOf(CapturedLog(
            level = truncateLogText(level, 64).lowercase(),
            message = truncateLogText(message, MAX_LOG_MESSAGE_BYTES),
            capture = capture
        )))
    }

    /** One immutable history snapshot per batch, preserving accepted queue order. */
    internal fun addRawLogcatBatch(batch: List<PendingRawLog>) {
        val parsed = batch.map { parseRawLogcat(it.payload, it.capture) }
        LogStore.append(parsed)
    }

    /**
     * Clears diagnostic history and invalidates queued AND already-drained native logs.
     * Request history is independent; its own clear invalidates pending request capture.
     */
    fun clear() {
        LogStore.clearLogs(rawQueue)
    }

    private fun parseRawLogcat(payload: String, capture: LogCapture): CapturedLog {
        val bounded = truncateLogText(payload, MAX_LOG_MESSAGE_BYTES)
        return try {
            val obj = json.parseToJsonElement(bounded).jsonObject
            // Bundled JNI emits level/message/time; controller streams use type/payload.
            fun text(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull
            CapturedLog(
                level = truncateLogText(text("level") ?: text("type") ?: "info", 64).lowercase(),
                message = truncateLogText(text("message") ?: text("payload") ?: bounded, MAX_LOG_MESSAGE_BYTES),
                capture = capture
            )
        } catch (_: Exception) {
            // Truncating an oversized envelope may remove JSON syntax; retain bounded raw text.
            CapturedLog(rawLogLevelHint(bounded) ?: "info", bounded, capture)
        }
    }
}
