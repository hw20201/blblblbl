package com.github.arca.android.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.github.arca.android.data.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

internal data class RequestLogPresentation(
    val requests: List<StructuredRequest> = emptyList(),
    val filteredRequests: List<StructuredRequest> = emptyList()
)

/** Bounded id+entry cache for request rows. */
internal class RequestLogParseCache(
    private val maxEntries: Int = MAX_CACHE_ENTRIES,
    private val parser: (LogEntry) -> StructuredRequest = ::parseRequestLog
) {
    private data class CachedEntry(
        val entry: LogEntry,
        val request: StructuredRequest
    )

    private val parsedById = LinkedHashMap<Long, CachedEntry>(maxEntries.coerceAtLeast(1), 0.75f, true)

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    /** Returns newest first, matching the request screen's stream order. */
    @Synchronized
    fun parse(entries: List<LogEntry>): List<StructuredRequest> {
        if (entries.isEmpty()) {
            parsedById.clear()
            return emptyList()
        }

        val currentIds = entries.asSequence().mapTo(HashSet(entries.size)) { it.id }
        parsedById.keys.retainAll(currentIds)
        val requests = entries.asReversed().map { entry ->
            val cached = parsedById[entry.id]
            if (cached != null && cached.entry == entry) cached.request
            else parser(entry).also { parsedById[entry.id] = CachedEntry(entry, it) }
        }

        while (parsedById.size > maxEntries) {
            val oldest = parsedById.entries.iterator()
            if (!oldest.hasNext()) break
            oldest.next()
            oldest.remove()
        }
        return requests
    }

    @Synchronized
    fun clear() {
        parsedById.clear()
    }

    @get:Synchronized
    val size: Int get() = parsedById.size

    companion object {
        const val MAX_CACHE_ENTRIES = 1000
    }
}

internal fun filterRequestLogs(
    requests: List<StructuredRequest>,
    searchQuery: String,
    selectedProtocol: String
): List<StructuredRequest> {
    val query = searchQuery.trim()
    return requests.filter { request ->
        val protocolMatches = selectedProtocol == "ALL" ||
            request.protocol.equals(selectedProtocol, ignoreCase = true)
        val queryMatches = query.isBlank() ||
            request.destination.contains(query, ignoreCase = true) ||
            request.rule.contains(query, ignoreCase = true) ||
            request.outbound.contains(query, ignoreCase = true) ||
            request.rawMessage.contains(query, ignoreCase = true)
        protocolMatches && queryMatches
    }
}

/**
 * Collects only while the owner is STARTED. Parsing and filtering happen on Default; keyed
 * collectLatest cancels old snapshots and query/filter changes cannot publish an older result.
 */
@Composable
internal fun rememberRequestLogPresentation(
    lifecycle: Lifecycle,
    rawLogs: StateFlow<List<LogEntry>>,
    searchQuery: String,
    selectedProtocol: String
): RequestLogPresentation {
    val cache = remember(rawLogs) { RequestLogParseCache() }

    return produceState(
        initialValue = RequestLogPresentation(),
        lifecycle,
        rawLogs,
        cache,
        searchQuery,
        selectedProtocol
    ) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            rawLogs.collectLatest { entries ->
                val presentation = withContext(Dispatchers.Default) {
                    val parsed = cache.parse(entries)
                    RequestLogPresentation(
                        requests = parsed,
                        filteredRequests = filterRequestLogs(parsed, searchQuery, selectedProtocol)
                    )
                }
                value = presentation
            }
        }
    }.value
}
