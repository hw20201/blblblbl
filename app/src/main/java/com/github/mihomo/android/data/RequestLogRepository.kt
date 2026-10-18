package com.github.mihomo.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Request-only snapshot; core diagnostic logs remain owned by [LogRepository]. */
object RequestLogRepository {
    private const val MAX_REQUESTS = 1000
    private val requests = mutableListOf<LogEntry>()
    private val _monitoringEnabled = MutableStateFlow(true)
    val monitoringEnabled: StateFlow<Boolean> = _monitoringEnabled.asStateFlow()

    private val _requestsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val requestsFlow: StateFlow<List<LogEntry>> = _requestsFlow.asStateFlow()

    @Synchronized
    fun setMonitoringEnabled(enabled: Boolean) {
        _monitoringEnabled.value = enabled
    }

    /** Called for each core log, retaining only actual proxy request records. */
    @Synchronized
    internal fun onLog(entry: LogEntry) {
        if (!_monitoringEnabled.value || !isRequest(entry.message)) return
        requests.add(entry)
        if (requests.size > MAX_REQUESTS) requests.removeAt(0)
        _requestsFlow.value = requests.toList()
    }

    @Synchronized
    fun clear() {
        requests.clear()
        _requestsFlow.value = emptyList()
    }

    private fun isRequest(message: String): Boolean {
        val text = message.trim()
        // Mihomo connection records have a protocol marker and a source-to-destination arrow.
        val protocolRecord = text.startsWith("[TCP]", ignoreCase = true) ||
            text.startsWith("[UDP]", ignoreCase = true) ||
            text.startsWith("[DNS]", ignoreCase = true)
        return protocolRecord && text.contains("-->")
    }
}
