package com.github.mihomo.android.data

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
    fun `clear removes all logs`() {
        LogRepository.addLog("info", "Test log")
        assertEquals(1, LogRepository.logsFlow.value.size)
        LogRepository.clear()
        assertTrue(LogRepository.logsFlow.value.isEmpty())
    }
}
