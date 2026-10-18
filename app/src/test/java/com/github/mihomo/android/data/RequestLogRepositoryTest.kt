package com.github.mihomo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestLogRepositoryTest {
    @Before
    fun setUp() {
        RequestLogRepository.setMonitoringEnabled(true)
        RequestLogRepository.clear()
        LogRepository.clear()
    }

    @Test
    fun `pause ignores new requests and resume continues incrementally`() {
        LogRepository.addLog("info", "[TCP] 10.0.0.1:1234 --> example.com:443 match Domain using Proxy")
        RequestLogRepository.setMonitoringEnabled(false)
        LogRepository.addLog("info", "[TCP] 10.0.0.1:1234 --> paused.example:443 match Domain using Proxy")
        assertFalse(RequestLogRepository.monitoringEnabled.value)
        assertEquals(1, RequestLogRepository.requestsFlow.value.size)

        RequestLogRepository.setMonitoringEnabled(true)
        LogRepository.addLog("info", "[UDP] 10.0.0.1:1234 --> resumed.example:53 match Domain using Direct")
        assertTrue(RequestLogRepository.monitoringEnabled.value)
        assertEquals(listOf("example.com:443", "resumed.example:53"),
            RequestLogRepository.requestsFlow.value.map { it.message.substringAfter("--> ").substringBefore(" match") })
    }

    @Test
    fun `only actual protocol connection records are captured`() {
        LogRepository.addLog("info", "service started")
        LogRepository.addLog("warning", "TCP handshake failed")
        LogRepository.addLog("info", "[TCP] 127.0.0.1:1234 --> example.com:443 match Domain using Proxy")
        LogRepository.addLog("info", "[DNS] resolver --> example.com match Domain using DNS")
        assertEquals(2, RequestLogRepository.requestsFlow.value.size)
    }

    @Test
    fun `clearing requests leaves core logs intact`() {
        LogRepository.addLog("info", "[TCP] 127.0.0.1:1234 --> example.com:443 match Domain using Proxy")
        RequestLogRepository.clear()
        assertTrue(RequestLogRepository.requestsFlow.value.isEmpty())
        assertEquals(1, LogRepository.logsFlow.value.size)
    }

    @Test
    fun `request snapshot is capped at one thousand entries`() {
        repeat(1005) { index ->
            LogRepository.addLog("info", "[TCP] src:$index --> host$index.example:443 match Domain using Proxy")
        }
        val requests = RequestLogRepository.requestsFlow.value
        assertEquals(1000, requests.size)
        assertTrue(requests.first().message.contains("host5.example"))
        assertTrue(requests.last().message.contains("host1004.example"))
    }
}
