package com.github.arca.android.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogRepositoryBatchTest {
    @Before
    fun setUp() {
        LogRepository.clear()
        RequestLogRepository.clear()
        RequestLogRepository.setMonitoringEnabled(true)
    }

    private fun request(host: String) = "[TCP] src:1234 --> $host:443 match Domain using Proxy"

    private fun pending(message: String, level: String = "info"): PendingRawLog =
        RawLogQueue().prepare(
            """{"level":"$level","message":"$message"}""",
            LogStore.capture()
        )

    @Test
    fun `batch publishes one coherent snapshot and preserves record order and arrival timestamps`() = runTest {
        val coreSnapshots = mutableListOf<List<LogEntry>>()
        val requestSnapshots = mutableListOf<List<LogEntry>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            LogRepository.logsFlow.collect { coreSnapshots.add(it) }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            RequestLogRepository.requestsFlow.collect { requestSnapshots.add(it) }
        }
        val batch = listOf(pending("start"), pending(request("first")), pending(request("second"), "WARNING"))
        LogRepository.addRawLogcatBatch(batch)
        val logs = LogRepository.logsFlow.value
        assertEquals(listOf("start", request("first"), request("second")), logs.map { it.message })
        assertEquals(batch.map { it.capture.timestamp }, logs.map { it.timestamp })
        assertEquals(listOf("info", "info", "warning"), logs.map { it.level })
        assertEquals(2, coreSnapshots.size)
        assertEquals(2, requestSnapshots.size)
        assertEquals(logs.drop(1), RequestLogRepository.requestsFlow.value)
        assertTrue(logs.zipWithNext().all { (left, right) -> left.id < right.id })
    }

    @Test
    fun `multiple batches keep latest thousand core and request entries in order`() {
        repeat(17) { batch ->
            LogRepository.addRawLogcatBatch((0 until 64).map { pending(request("host${batch * 64 + it}")) })
        }
        val logs = LogRepository.logsFlow.value
        val requests = RequestLogRepository.requestsFlow.value
        assertEquals(1000, logs.size)
        assertEquals(logs, requests)
        assertEquals((88 until 1088).map { request("host$it") }, logs.map { it.message })
        assertEquals(1000, logs.map { it.id }.toSet().size)
    }

    @Test
    fun `pause and resume use arrival state rather than batch consumption state`() {
        val beforePause = pending(request("before-pause"))
        RequestLogRepository.setMonitoringEnabled(false)
        val duringPause = pending(request("during-pause"))
        // Enabled records accepted before pause remain eligible when consumed during pause.
        LogRepository.addRawLogcatBatch(listOf(beforePause, duringPause))
        assertEquals(listOf(request("before-pause")), RequestLogRepository.requestsFlow.value.map { it.message })
        assertFalse(RequestLogRepository.monitoringEnabled.value)
        val stillPaused = pending(request("still-paused"))
        RequestLogRepository.setMonitoringEnabled(true)
        val afterResume = pending(request("after-resume"))
        LogRepository.addRawLogcatBatch(listOf(stillPaused, afterResume))
        assertEquals(listOf(request("before-pause"), request("after-resume")),
            RequestLogRepository.requestsFlow.value.map { it.message })
        assertEquals(4, LogRepository.logsFlow.value.size)
    }

    @Test
    fun `request clear rejects queued and drained old requests but keeps diagnostic history`() {
        LogRepository.addLog("info", request("existing"))
        val queue = RawLogQueue()
        queue.offer(pending(request("old-queued")))
        val drained = listOf(pending(request("old-drained")))
        RequestLogRepository.clear()
        LogRepository.addRawLogcatBatch(drained + queue.drain(64) + pending(request("new")))
        assertEquals(listOf(request("new")), RequestLogRepository.requestsFlow.value.map { it.message })
        assertEquals(4, LogRepository.logsFlow.value.size)
    }

    @Test
    fun `core clear rejects old queued drained and parsed records without clearing request history`() {
        LogRepository.addLog("info", request("retained-request"))
        val queue = RawLogQueue()
        queue.offer(pending(request("old-queued")))
        val drained = listOf(pending(request("old-drained")))
        val parsed = CapturedLog("info", request("old-parsed"), LogStore.capture())
        LogRepository.clear()
        assertTrue(LogRepository.logsFlow.value.isEmpty())
        LogRepository.addRawLogcatBatch(drained + queue.drain(64))
        LogStore.append(listOf(parsed))
        assertTrue(LogRepository.logsFlow.value.isEmpty())
        assertEquals(listOf(request("retained-request")), RequestLogRepository.requestsFlow.value.map { it.message })
        LogRepository.addRawLogcatBatch(listOf(pending(request("new"))))
        assertEquals(listOf(request("new")), LogRepository.logsFlow.value.map { it.message })
        assertEquals(listOf(request("retained-request"), request("new")),
            RequestLogRepository.requestsFlow.value.map { it.message })
    }

    @Test
    fun `core clear releases actual native queue immediately`() {
        // Holding the store monitor prevents the consumer from committing during setup.
        synchronized(LogStore.lock) {
            assertTrue(LogRepository.enqueueRawLogcat("old native arrival"))
            LogRepository.clear()
            assertEquals(0, LogRepository.rawLogcatQueueStats.queuedEntries)
            assertEquals(0, LogRepository.rawLogcatQueueStats.queuedBytes)
            assertTrue(LogRepository.logsFlow.value.isEmpty())
        }
    }

    @Test
    fun `oversized native warning remains bounded with its level hint when envelope is truncated`() {
        val payload = """{"level":"warning","message":"${"鲸🐳".repeat(10_000)}"}"""
        val queue = RawLogQueue()
        val record = queue.prepare(payload, LogStore.capture())
        assertTrue(record.truncated)
        assertTrue(record.important)
        LogRepository.addRawLogcatBatch(listOf(record))
        val log = LogRepository.logsFlow.value.single()
        assertEquals("warning", log.level)
        assertTrue(utf8Size(log.message) <= MAX_LOG_MESSAGE_BYTES)
        assertTrue(log.message.endsWith("[truncated]"))
    }

    @Test
    fun `flow callbacks run without repository lock and may write from another thread`() = runTest {
        val executor = Executors.newSingleThreadExecutor()
        var observed = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            LogRepository.logsFlow.collect { logs ->
                if (!observed && logs.any { it.message == "trigger" }) {
                    observed = true
                    assertFalse(Thread.holdsLock(LogStore.lock))
                    executor.submit { LogRepository.addLog("info", "from collector") }.get(2, TimeUnit.SECONDS)
                }
            }
        }
        try {
            LogRepository.addLog("info", "trigger")
            assertTrue(observed)
            assertEquals(listOf("trigger", "from collector"), LogRepository.logsFlow.value.map { it.message })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `clear from a collector cannot let an older publisher restore a stale snapshot`() = runTest {
        val seen = mutableListOf<List<String>>()
        var cleared = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            LogRepository.logsFlow.collect { logs ->
                seen.add(logs.map { it.message })
                if (!cleared && logs.any { it.message == "clear-trigger" }) {
                    cleared = true
                    LogRepository.clear()
                    LogRepository.addLog("info", "after-clear")
                }
            }
        }
        LogRepository.addLog("info", "clear-trigger")
        assertTrue(cleared)
        assertEquals(listOf("after-clear"), LogRepository.logsFlow.value.map { it.message })
        assertEquals(listOf("after-clear"), seen.last())
    }

    @Test
    fun `concurrent synchronous writers retain unique ordered ids in both bounded histories`() {
        val executor = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        try {
            val jobs = (0 until 4).map { producer ->
                executor.submit {
                    check(start.await(5, TimeUnit.SECONDS))
                    repeat(300) { index -> LogRepository.addLog("info", request("p$producer-$index")) }
                }
            }
            start.countDown()
            jobs.forEach { it.get(10, TimeUnit.SECONDS) }
            val logs = LogRepository.logsFlow.value
            assertEquals(1000, logs.size)
            assertEquals(logs, RequestLogRepository.requestsFlow.value)
            assertEquals(1000, logs.map { it.id }.toSet().size)
            assertTrue(logs.zipWithNext().all { (left, right) -> left.id + 1 == right.id })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `concurrent clear and pause cannot deadlock native enqueue and batch writes`() {
        val executor = Executors.newFixedThreadPool(3)
        val start = CountDownLatch(1)
        try {
            val native = executor.submit {
                check(start.await(5, TimeUnit.SECONDS))
                repeat(500) { LogRepository.enqueueRawLogcat("""{"level":"info","message":"${request("native-$it")}"}""") }
            }
            val synchronous = executor.submit {
                check(start.await(5, TimeUnit.SECONDS))
                repeat(500) { LogRepository.addLog("info", request("sync-$it")) }
            }
            val controls = executor.submit {
                check(start.await(5, TimeUnit.SECONDS))
                repeat(100) {
                    RequestLogRepository.setMonitoringEnabled(it % 2 == 0)
                    RequestLogRepository.clear()
                    LogRepository.clear()
                }
            }
            start.countDown()
            listOf(native, synchronous, controls).forEach { it.get(10, TimeUnit.SECONDS) }
            // Once all producers return, this final generation excludes every in-flight batch.
            RequestLogRepository.clear()
            LogRepository.clear()
            RequestLogRepository.setMonitoringEnabled(true)
            LogRepository.addLog("info", request("final"))
            assertEquals(listOf(request("final")), LogRepository.logsFlow.value.map { it.message })
            assertEquals(LogRepository.logsFlow.value, RequestLogRepository.requestsFlow.value)
        } finally {
            executor.shutdownNow()
        }
    }
}
