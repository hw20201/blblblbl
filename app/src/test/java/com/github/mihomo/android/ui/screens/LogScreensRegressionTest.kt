package com.github.mihomo.android.ui.screens

import com.github.mihomo.android.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logic-level regression coverage for the data rendered by the log screens. */
class LogScreensRegressionTest {
    @Test
    fun requestLogParserKeepsProtocolDestinationRuleAndOutbound() {
        val entry = LogEntry(id = 7L, timestamp = 1234L, level = "info", message = "[TCP] 192.168.1.2:50000 --> example.com:443 match DomainSuffix using Proxy")
        val parsed = parseRequestLog(entry)
        assertEquals("TCP", parsed.protocol)
        assertEquals("example.com:443", parsed.destination)
        assertEquals("DomainSuffix", parsed.rule)
        assertEquals("Proxy", parsed.outbound)
        assertEquals(entry.id, parsed.id)
    }

    @Test
    fun requestLogParserFallsBackToRawMessageForUnstructuredEntry() {
        val parsed = parseRequestLog(LogEntry(id = 8L, timestamp = 0L, level = "info", message = "DNS cache hit"))
        assertEquals("DNS", parsed.protocol)
        assertEquals("DNS cache hit", parsed.destination)
        assertTrue(parsed.rule.isEmpty())
        assertTrue(parsed.outbound.isEmpty())
    }
}
