package com.github.arca.android.data

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogBufferTest {
    private val capture = LogCapture(0, 0, true, timestamp = 1234L)

    private fun offer(queue: RawLogQueue, payload: String): Boolean =
        queue.offer(queue.prepare(payload, capture))

    @Test
    fun `overflow retains warning and error before ordinary logs and counts every eviction`() {
        val queue = RawLogQueue(maxEntries = 3, maxBytes = 1024)
        val warning = """{"level":"WARNING","message":"important warning"}"""
        val error = """{"type":"error","payload":"important error"}"""
        offer(queue, "debug-old")
        offer(queue, warning)
        offer(queue, "debug-new")
        assertTrue(offer(queue, error))
        assertTrue(offer(queue, "latest"))
        assertEquals(listOf(warning, error, "latest"), queue.drain(64).map { it.payload })
        assertEquals(2L, queue.stats().droppedEntries)
        assertEquals(0, queue.stats().queuedBytes)
    }

    @Test
    fun `all important overflow is still bounded and rejects ordinary arrival`() {
        val queue = RawLogQueue(maxEntries = 2, maxBytes = 1024)
        fun error(index: Int) = """{"level":"error","message":"$index"}"""
        offer(queue, error(1))
        offer(queue, error(2))
        assertFalse(offer(queue, "ordinary"))
        assertTrue(offer(queue, error(3)))
        assertEquals(listOf(error(2), error(3)), queue.drain(64).map { it.payload })
        assertEquals(2L, queue.stats().droppedEntries)
    }

    @Test
    fun `queue byte limit evicts before count limit and oversized arrivals are truncated`() {
        val queue = RawLogQueue(maxEntries = 100, maxBytes = 40, maxEntryBytes = 30)
        offer(queue, "a".repeat(25))
        offer(queue, "b".repeat(25))
        assertEquals(1, queue.stats().queuedEntries)
        assertEquals(25, queue.stats().queuedBytes)
        assertEquals(1L, queue.stats().droppedEntries)
        offer(queue, "c".repeat(10_000))
        val stats = queue.stats()
        assertEquals(1, stats.queuedEntries)
        assertTrue(stats.queuedBytes <= 30)
        assertEquals(2L, stats.droppedEntries)
        assertEquals(1L, stats.truncatedEntries)
        assertTrue(queue.drain(1).single().payload.endsWith("[truncated]"))
    }

    @Test
    fun `UTF8 truncation respects byte limit without splitting emoji`() {
        val original = "鲸🐳".repeat(50)
        val bounded = truncateLogText(original, 36)
        assertTrue(utf8Size(bounded) <= 36)
        assertEquals(bounded.toByteArray(Charsets.UTF_8).size, utf8Size(bounded))
        assertFalse(bounded.contains('\uFFFD'))
        assertTrue(bounded.endsWith("[truncated]"))
        assertEquals("abc", truncateLogText("abc", 3))
        assertEquals("", truncateLogText("鲸", 1))
        assertEquals("🐳", truncateLogText("🐳", 4))
        assertEquals(1, utf8Size("\uD800"))
    }

    @Test
    fun `clear empties bytes and records without resetting cumulative overflow statistics`() {
        val queue = RawLogQueue(maxEntries = 1, maxBytes = 100)
        offer(queue, "first")
        offer(queue, "second")
        queue.clear()
        assertEquals(0, queue.stats().queuedEntries)
        assertEquals(0, queue.stats().queuedBytes)
        assertEquals(1L, queue.stats().droppedEntries)
        assertTrue(queue.drain(64).isEmpty())
        offer(queue, "after-clear")
        assertEquals(listOf("after-clear"), queue.drain(64).map { it.payload })
    }

    @Test
    fun `sparse batch flushes at fifty milliseconds without resetting timer on arrivals`() = runTest {
        val queue = RawLogQueue()
        val batches = mutableListOf<List<String>>()
        val batcher = RawLogBatcher(backgroundScope, queue, { batch ->
            batches.add(batch.map { it.payload })
        })
        offer(queue, "first")
        batcher.signal()
        runCurrent()
        advanceTimeBy(30)
        offer(queue, "second")
        batcher.signal()
        runCurrent()
        advanceTimeBy(19)
        runCurrent()
        assertTrue(batches.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(listOf("first", "second")), batches)
    }

    @Test
    fun `sixty four arrivals flush immediately and remainder waits for next batch`() = runTest {
        val queue = RawLogQueue()
        val batches = mutableListOf<List<String>>()
        val batcher = RawLogBatcher(backgroundScope, queue, { batch ->
            batches.add(batch.map { it.payload })
        })
        repeat(130) { offer(queue, "row-$it") }
        batcher.signal()
        runCurrent()
        assertEquals(listOf(64, 64), batches.map { it.size })
        assertEquals((0 until 128).map { "row-$it" }, batches.flatten())
        advanceTimeBy(50)
        runCurrent()
        assertEquals(listOf(64, 64, 2), batches.map { it.size })
        assertEquals((0 until 130).map { "row-$it" }, batches.flatten())
    }

    @Test
    fun `sixty fourth arrival wakes a waiting consumer before its timeout`() = runTest {
        val queue = RawLogQueue()
        val sizes = mutableListOf<Int>()
        val batcher = RawLogBatcher(backgroundScope, queue, { sizes.add(it.size) })
        offer(queue, "first")
        batcher.signal()
        runCurrent()
        advanceTimeBy(10)
        repeat(63) { offer(queue, "row-$it") }
        batcher.signal()
        runCurrent()
        assertEquals(listOf(64), sizes)
    }

    @Test
    fun `consumer callbacks run outside queue monitor and can reenter through another producer`() = runTest {
        val queue = RawLogQueue()
        val executor = Executors.newSingleThreadExecutor()
        val callbackCompleted = AtomicBoolean(false)
        val batcher = RawLogBatcher(backgroundScope, queue, {
            assertFalse(Thread.holdsLock(queue))
            assertTrue(executor.submit<Boolean> { offer(queue, "from-callback") }.get(2, TimeUnit.SECONDS))
            callbackCompleted.set(true)
        })
        try {
            offer(queue, "first")
            batcher.signal()
            runCurrent()
            advanceTimeBy(50)
            runCurrent()
            assertTrue(callbackCompleted.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `concurrent producers and a drainer preserve per producer order and account for overflow`() {
        val queue = RawLogQueue(maxEntries = 128, maxBytes = 4096, maxEntryBytes = 64)
        val executor = Executors.newFixedThreadPool(5)
        val start = CountDownLatch(1)
        val producersDone = CountDownLatch(4)
        val consumed = Collections.synchronizedList(mutableListOf<PendingRawLog>())
        try {
            val producers = (0 until 4).map { producer ->
                executor.submit {
                    check(start.await(5, TimeUnit.SECONDS))
                    try {
                        repeat(500) { index ->
                            offer(queue, "$producer:$index")
                            val stats = queue.stats()
                            check(stats.queuedEntries <= 128)
                            check(stats.queuedBytes <= 4096)
                        }
                    } finally {
                        producersDone.countDown()
                    }
                }
            }
            val consumer = executor.submit {
                check(start.await(5, TimeUnit.SECONDS))
                while (producersDone.count > 0 || queue.stats().queuedEntries > 0) {
                    consumed.addAll(queue.drain(64))
                    Thread.yield()
                }
            }
            start.countDown()
            producers.forEach { it.get(10, TimeUnit.SECONDS) }
            consumer.get(10, TimeUnit.SECONDS)
            val messages = consumed.map { it.payload }
            assertEquals(messages.size, messages.toSet().size)
            repeat(4) { producer ->
                val indices = messages.filter { it.startsWith("$producer:") }.map { it.substringAfter(':').toInt() }
                assertEquals(indices.sorted(), indices)
            }
            assertEquals(2000L, messages.size.toLong() + queue.stats().droppedEntries)
            assertEquals(0, queue.stats().queuedBytes)
        } finally {
            executor.shutdownNow()
        }
    }
}
