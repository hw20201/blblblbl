package com.github.arca.android.data

import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal const val MAX_LOG_MESSAGE_BYTES = 16 * 1024

/** The generations and monitoring setting at the instant a log is accepted. */
internal data class LogCapture(
    val coreGeneration: Long,
    val requestGeneration: Long,
    val captureRequest: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

internal data class PendingRawLog(
    val payload: String,
    val capture: LogCapture,
    val byteCount: Int,
    val important: Boolean,
    val truncated: Boolean
)

data class LogQueueStats(
    val queuedEntries: Int,
    val queuedBytes: Int,
    val droppedEntries: Long,
    val truncatedEntries: Long
)

/** UTF-8 size without allocating a byte array for an arbitrarily large native message. */
internal fun utf8Size(text: String, stopAfter: Int = Int.MAX_VALUE): Int {
    var bytes = 0L
    var index = 0
    while (index < text.length) {
        val character = text[index]
        bytes += when {
            character.code <= 0x7f -> 1
            character.code <= 0x7ff -> 2
            character.isHighSurrogate() && index + 1 < text.length &&
                text[index + 1].isLowSurrogate() -> {
                index++
                4
            }
            character.isSurrogate() -> 1 // JVM's UTF-8 replacement for an unpaired surrogate.
            else -> 3
        }
        if (bytes > stopAfter) return minOf(bytes, Int.MAX_VALUE.toLong()).toInt()
        index++
    }
    return bytes.toInt()
}

/** Keeps whole code points and accounts for the visible truncation marker in the limit. */
internal fun truncateLogText(text: String, maxBytes: Int): String {
    require(maxBytes > 0)
    if (utf8Size(text, stopAfter = maxBytes) <= maxBytes) return text
    val marker = "…[truncated]"
    val suffix = if (maxBytes >= utf8Size(marker)) marker else ""
    val budget = maxBytes - utf8Size(suffix)
    var index = 0
    var bytes = 0
    while (index < text.length) {
        val character = text[index]
        val pair = character.isHighSurrogate() && index + 1 < text.length &&
            text[index + 1].isLowSurrogate()
        val width = when {
            character.code <= 0x7f -> 1
            character.code <= 0x7ff -> 2
            pair -> 4
            character.isSurrogate() -> 1
            else -> 3
        }
        if (bytes + width > budget) break
        bytes += width
        index += if (pair) 2 else 1
    }
    return text.substring(0, index) + suffix
}

/**
 * Bounded producer queue. Parsing and callbacks never run under this queue's lock.
 * On overflow, oldest ordinary logs give way first. If only warnings/errors remain,
 * ordinary arrivals are rejected and important arrivals replace the oldest important log.
 */
internal class RawLogQueue(
    private val maxEntries: Int = 512,
    private val maxBytes: Int = 512 * 1024,
    private val maxEntryBytes: Int = MAX_LOG_MESSAGE_BYTES
) {
    init {
        require(maxEntries > 0)
        require(maxBytes > 0)
        require(maxEntryBytes > 0)
    }

    private val queue = ArrayDeque<PendingRawLog>()
    private var queuedBytes = 0
    private var droppedEntries = 0L
    private var truncatedEntries = 0L

    fun prepare(payload: String, capture: LogCapture): PendingRawLog {
        val bounded = truncateLogText(payload, minOf(maxEntryBytes, maxBytes))
        // Inspect only the bounded envelope header; full JSON parsing belongs to the consumer.
        val important = rawLogLevelHint(bounded) != null
        return PendingRawLog(bounded, capture, utf8Size(bounded), important, bounded != payload)
    }

    @Synchronized
    fun offer(pending: PendingRawLog): Boolean {
        if (pending.truncated) truncatedEntries++
        while (queue.size >= maxEntries || queuedBytes.toLong() + pending.byteCount > maxBytes) {
            val iterator = queue.iterator()
            var removed = false
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (!candidate.important) {
                    iterator.remove()
                    queuedBytes -= candidate.byteCount
                    droppedEntries++
                    removed = true
                    break
                }
            }
            if (!removed) {
                if (!pending.important || queue.isEmpty()) {
                    droppedEntries++
                    return false
                }
                queuedBytes -= queue.removeFirst().byteCount
                droppedEntries++
            }
        }
        queue.addLast(pending)
        queuedBytes += pending.byteCount
        return true
    }

    @Synchronized
    fun drain(maxCount: Int): List<PendingRawLog> {
        require(maxCount > 0)
        val batch = ArrayList<PendingRawLog>(minOf(maxCount, queue.size))
        repeat(minOf(maxCount, queue.size)) {
            val pending = queue.removeFirst()
            queuedBytes -= pending.byteCount
            batch.add(pending)
        }
        return batch
    }

    @Synchronized
    fun clear() {
        queue.clear()
        queuedBytes = 0
    }

    @Synchronized
    fun stats() = LogQueueStats(queue.size, queuedBytes, droppedEntries, truncatedEntries)
}

private val importantLogLevel = Regex(
    "\"(?:level|type)\"\\s*:\\s*\"(warn(?:ing)?|error|fatal)\"",
    RegexOption.IGNORE_CASE
)

/** Best effort only: the producer never parses JSON and inspects at most 512 characters. */
internal fun rawLogLevelHint(payload: String): String? =
    importantLogLevel.find(payload.take(512))?.groupValues?.get(1)?.lowercase()

/** A single consumer publishes at most 64 records per batch, or after 50 ms. */
internal class RawLogBatcher(
    scope: CoroutineScope,
    private val queue: RawLogQueue,
    private val consume: (List<PendingRawLog>) -> Unit,
    private val batchSize: Int = 64,
    private val flushMillis: Long = 50
) {
    init {
        require(batchSize > 0)
        require(flushMillis > 0)
    }

    private val wake = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (ignored in wake) {
                // A sparse batch waits from its first wake, not from every subsequent arrival.
                withTimeoutOrNull(flushMillis) {
                    while (queue.stats().queuedEntries < batchSize) wake.receive()
                }
                do {
                    val batch = queue.drain(batchSize)
                    if (batch.isNotEmpty()) consume(batch)
                } while (queue.stats().queuedEntries >= batchSize)
                if (queue.stats().queuedEntries > 0) wake.trySend(Unit)
            }
        }
    }

    fun signal() {
        wake.trySend(Unit)
    }
}
