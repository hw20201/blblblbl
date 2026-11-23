package com.github.arca.android.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-proxy delay test (FlClash/BettBox style): one URL test per node, each
 * with its own timeout, each reporting a result. The old group HealthCheck
 * path is gone, so these tests cover the new contract: virtual nodes
 * short-circuit, every name in a batch gets exactly one result, and a
 * group's delay is its best member's delay.
 */
class ProxyDelayTest {

    @Test
    fun directNodeIsZero() = runBlocking {
        assertEquals(0, ClashCore.testProxyDelay("DIRECT"))
        assertEquals(0, ClashCore.testProxyDelay("direct"))
    }

    @Test
    fun rejectAndCompatibleAreTimeout() = runBlocking {
        assertEquals(-2, ClashCore.testProxyDelay("REJECT"))
        assertEquals(-2, ClashCore.testProxyDelay("COMPATIBLE"))
    }

    @Test
    fun batchReportsEveryName() = runBlocking {
        val seen = mutableListOf<String>()
        val result = ClashCore.testProxyDelays(
            listOf("a", "b", "c"),
            testOne = { name -> if (name == "b") -2 else 120 },
            onProgress = { name, _ -> seen.add(name) }
        )
        assertEquals(mapOf("a" to 120, "b" to -2, "c" to 120), result)
        assertEquals(listOf("a", "b", "c").sorted(), seen.sorted())
    }

    @Test
    fun batchEmptyIsEmpty() = runBlocking {
        assertTrue(ClashCore.testProxyDelays(emptyList()).isEmpty())
    }

    @Test
    fun batchTimeoutStillReports() = runBlocking {
        val result = ClashCore.testProxyDelays(
            listOf("slow", "dead"),
            testOne = { -2 }
        )
        assertEquals(mapOf("slow" to -2, "dead" to -2), result)
    }

    @Test
    fun groupDelayIsLowestPositive() {
        assertEquals(80, ClashCore.groupDelayFromMembers(listOf(120, 80, -2, 200)))
        assertEquals(120, ClashCore.groupDelayFromMembers(listOf(120)))
        assertEquals(-2, ClashCore.groupDelayFromMembers(listOf(-2, -2)))
        assertEquals(-2, ClashCore.groupDelayFromMembers(emptyList()))
        assertEquals(-2, ClashCore.groupDelayFromMembers(listOf(0, 65535)))
    }
}
