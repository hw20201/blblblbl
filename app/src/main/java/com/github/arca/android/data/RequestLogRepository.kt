package com.github.arca.android.data

import kotlinx.coroutines.flow.StateFlow

/** Request-only snapshot; core diagnostic logs remain owned by [LogRepository]. */
object RequestLogRepository {
    val monitoringEnabled: StateFlow<Boolean> = LogStore.monitoringEnabled
    val requestsFlow: StateFlow<List<LogEntry>> = LogStore.requestsFlow

    /**
     * Applies to logs accepted after this call. Already-queued enabled records remain
     * eligible even when consumed during a pause; records accepted while paused never
     * become requests after a resume. History remains retained in the background.
     */
    fun setMonitoringEnabled(enabled: Boolean) {
        LogStore.setMonitoringEnabled(enabled)
    }

    /** Clears only requests, also excluding pending records accepted before this clear. */
    fun clear() {
        LogStore.clearRequests()
    }
}
