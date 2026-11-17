package com.github.arca.android.data

import java.util.ArrayDeque
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal data class CapturedLog(
    val level: String,
    val message: String,
    val capture: LogCapture,
    val isRequest: Boolean = isRequestLog(message)
)

private data class LogSnapshot(
    val revision: Long = 0,
    val logs: List<LogEntry> = emptyList(),
    val requests: List<LogEntry> = emptyList(),
    val monitoringEnabled: Boolean = true
)

/** Projects one coherent snapshot, including synchronous .value reads. */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class LogSnapshotFlow<T>(
    private val source: StateFlow<LogSnapshot>,
    private val select: (LogSnapshot) -> T
) : StateFlow<T> {
    override val value: T get() = select(source.value)
    override val replayCache: List<T> get() = listOf(value)

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        source.map(select).distinctUntilChanged().collect(collector)
        error("StateFlow collection completed")
    }
}

/**
 * Both histories share one short mutation lock. Immutable snapshots are published after
 * releasing it; revision checks stop a slower publisher from restoring stale state.
 * No parser, flow collector, or external callback runs under [lock].
 */
internal object LogStore {
    private const val MAX_HISTORY = 1000
    val lock = Any()
    private val logs = ArrayDeque<LogEntry>()
    private val requests = ArrayDeque<LogEntry>()
    private var nextId = 1L
    private var coreGeneration = 0L
    private var requestGeneration = 0L
    private var monitoring = true
    private var latest = LogSnapshot()
    private val published = MutableStateFlow(latest)

    val logsFlow: StateFlow<List<LogEntry>> = LogSnapshotFlow(published) { it.logs }
    val requestsFlow: StateFlow<List<LogEntry>> = LogSnapshotFlow(published) { it.requests }
    val monitoringEnabled: StateFlow<Boolean> = LogSnapshotFlow(published) { it.monitoringEnabled }

    fun capture(): LogCapture = synchronized(lock) {
        LogCapture(coreGeneration, requestGeneration, monitoring)
    }

    /** Caller holds [lock], in the same order as native queue insertion and clear. */
    fun isCurrentLocked(capture: LogCapture): Boolean = capture.coreGeneration == coreGeneration

    fun append(batch: List<CapturedLog>) {
        val snapshot = synchronized(lock) {
            var logsChanged = false
            var requestsChanged = false
            for (log in batch) {
                if (!isCurrentLocked(log.capture)) continue
                val entry = LogEntry(nextId++, log.capture.timestamp, log.level, log.message)
                logs.addLast(entry)
                if (logs.size > MAX_HISTORY) logs.removeFirst()
                logsChanged = true
                if (log.isRequest && log.capture.captureRequest &&
                    log.capture.requestGeneration == requestGeneration) {
                    requests.addLast(entry)
                    if (requests.size > MAX_HISTORY) requests.removeFirst()
                    requestsChanged = true
                }
            }
            if (!logsChanged) return
            snapshotLocked(logsChanged, requestsChanged)
        }
        publish(snapshot)
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        val snapshot = synchronized(lock) {
            if (monitoring == enabled) {
                latest
            } else {
                monitoring = enabled
                snapshotLocked()
            }
        }
        publish(snapshot)
    }

    fun clearRequests() {
        val snapshot = synchronized(lock) {
            requestGeneration++
            requests.clear()
            snapshotLocked(requestsChanged = true)
        }
        publish(snapshot)
    }

    /** Queue clearing is a concrete operation, never an arbitrary callback under the lock. */
    fun clearLogs(queue: RawLogQueue) {
        val snapshot = synchronized(lock) {
            coreGeneration++
            queue.clear()
            logs.clear()
            snapshotLocked(logsChanged = true)
        }
        publish(snapshot)
    }

    private fun snapshotLocked(
        logsChanged: Boolean = false,
        requestsChanged: Boolean = false
    ): LogSnapshot {
        latest = LogSnapshot(
            revision = latest.revision + 1,
            logs = if (logsChanged) logs.toList() else latest.logs,
            requests = if (requestsChanged) requests.toList() else latest.requests,
            monitoringEnabled = monitoring
        )
        return latest
    }

    private fun publish(snapshot: LogSnapshot) {
        while (true) {
            val previous = published.value
            if (previous.revision >= snapshot.revision) return
            if (published.compareAndSet(previous, snapshot)) return
        }
    }
}

internal fun isRequestLog(message: String): Boolean {
    val text = message.trim()
    // Mihomo connection records have a protocol marker and a source-to-destination arrow.
    val protocolRecord = text.startsWith("[TCP]", ignoreCase = true) ||
        text.startsWith("[UDP]", ignoreCase = true) ||
        text.startsWith("[DNS]", ignoreCase = true)
    return protocolRecord && text.contains("-->")
}
