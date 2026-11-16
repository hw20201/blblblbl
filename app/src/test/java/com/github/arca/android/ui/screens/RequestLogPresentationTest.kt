package com.github.arca.android.ui.screens

import com.github.arca.android.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RequestLogPresentationTest {
    private fun entry(id: Long, protocol: String = "TCP", host: String = "host$id.example") =
        LogEntry(id = id, timestamp = id, level = "info",
            message = "[$protocol] src:$id --> $host:443 match Domain using Proxy")

    @Test fun appendingToOneThousandRowsParsesOnlyTheNewEntry() {
        var parses = 0
        val cache = RequestLogParseCache { parses++; parseRequestLog(it) }
        val initial = (1L..1000L).map { entry(it) }
        val first = cache.parse(initial)
        assertEquals(1000, parses)
        val next = cache.parse(initial.drop(1) + entry(1001))
        assertEquals(1001, parses)
        assertEquals(1000, cache.size)
        assertEquals(1001L, next.first().id)
        assertEquals(2L, next.last().id)
        assertSame(first.first(), next[1])
    }

    @Test fun cacheIsBoundedAndClearDropsEveryCachedRow() {
        var parses = 0
        val cache = RequestLogParseCache(maxEntries = 3) { parses++; parseRequestLog(it) }
        assertEquals(5, cache.parse((1L..5L).map { entry(it) }).size)
        assertEquals(3, cache.size)
        assertEquals(emptyList<StructuredRequest>(), cache.parse(emptyList()))
        assertEquals(0, cache.size)
        cache.parse(listOf(entry(1)))
        assertEquals(6, parses)
    }

    @Test fun changedEntryWithSameIdIsReparsed() {
        var parses = 0
        val cache = RequestLogParseCache { parses++; parseRequestLog(it) }
        cache.parse(listOf(entry(1)))
        val changed = cache.parse(listOf(entry(1, host = "replacement.example")))
        assertEquals(2, parses)
        assertEquals("replacement.example:443", changed.single().destination)
    }

    @Test fun filteringPreservesNewestFirstAndClearCannotRetainRows() {
        val cache = RequestLogParseCache()
        val rows = cache.parse(listOf(entry(1), entry(2, "UDP"), entry(3)))
        assertEquals(listOf(3L, 1L), filterRequestLogs(rows, "", "TCP").map { it.id })
        assertEquals(listOf(2L), filterRequestLogs(rows, "HOST2", "ALL").map { it.id })
        assertEquals(emptyList<StructuredRequest>(), filterRequestLogs(rows, "absent", "ALL"))
        assertEquals(emptyList<StructuredRequest>(), filterRequestLogs(cache.parse(emptyList()), "", "ALL"))
        assertEquals(listOf(4L), filterRequestLogs(cache.parse(listOf(entry(4))), "", "ALL").map { it.id })
    }
}
