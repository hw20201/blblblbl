package com.github.arca.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogRepositoryTest {

    @Before
    fun setUp() {
        LogRepository.clear()
    }

    @Test
    fun `addLog appends and formats log entries correctly`() {
        LogRepository.addLog("info", "Service started successfully")
        LogRepository.addLog("warning", "High memory usage detected")
        LogRepository.addLog("error", "Failed to connect to proxy")

        val logs = LogRepository.logsFlow.value
        assertEquals(3, logs.size)
        assertEquals("info", logs[0].level)
        assertEquals("Service started successfully", logs[0].message)
        assertEquals("warning", logs[1].level)
        assertEquals("error", logs[2].level)
    }

    @Test
    fun `addRawLogcat parses json format payload`() {
        val payload = """{"type":"warning","payload":"TCP connection timed out to 1.1.1.1:443"}"""
        LogRepository.addRawLogcat(payload)

        val logs = LogRepository.logsFlow.value
        assertEquals(1, logs.size)
        assertEquals("warning", logs[0].level)
        assertEquals("TCP connection timed out to 1.1.1.1:443", logs[0].message)
    }

    @Test
    fun `addRawLogcat falls back to raw string on invalid json`() {
        val rawMessage = "Plain text log from native bridge"
        LogRepository.addRawLogcat(rawMessage)

        val logs = LogRepository.logsFlow.value
        assertEquals(1, logs.size)
        assertEquals("info", logs[0].level)
        assertEquals(rawMessage, logs[0].message)
    }

    @Test
    fun `logs list is capped at max logs limit`() {
        for (i in 1..1050) {
            LogRepository.addLog("debug", "Log entry #$i")
        }
        val logs = LogRepository.logsFlow.value
        assertEquals(1000, logs.size)
        // First 50 items should have been evicted; earliest log should be #51
        assertEquals("Log entry #51", logs.first().message)
        assertEquals("Log entry #1050", logs.last().message)
    }

    @Test
    fun `bundled JNI envelope exposes request text to request monitoring`() {
        RequestLogRepository.clear()
        RequestLogRepository.setMonitoringEnabled(true)
        val message = "[TCP] 127.0.0.1:54321 --> example.com:443 match Domain(example.com) using DIRECT"
        LogRepository.addRawLogcat("""{"level":"DEBUG","message":"$message","time":1700000000000}""")
        assertEquals("debug", LogRepository.logsFlow.value.single().level)
        assertEquals(message, LogRepository.logsFlow.value.single().message)
        assertEquals(message, RequestLogRepository.requestsFlow.value.single().message)
        RequestLogRepository.clear()
    }

    @Test
    fun `null native fields preserve controller payload`() {
        LogRepository.addRawLogcat("""{"level":null,"message":null,"type":"warning","payload":"controller message"}""")
        assertEquals("warning", LogRepository.logsFlow.value.single().level)
        assertEquals("controller message", LogRepository.logsFlow.value.single().message)
    }

    @Test
    fun `clear removes all logs`() {
        LogRepository.addLog("info", "Test log")
        assertEquals(1, LogRepository.logsFlow.value.size)
        LogRepository.clear()
        assertTrue(LogRepository.logsFlow.value.isEmpty())
    }
}
