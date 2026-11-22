package com.github.arca.android.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The group health check waits for the core to finish. The core tests at
 * most 10 proxies at a time with a 5s per-proxy timeout, so the wait must
 * scale with the group size; the old flat 5.5s wait cut large groups off
 * before most nodes reported anything.
 */
class HealthCheckTimingTest {

    @Test
    fun emptyGroupWaitsMinimum() {
        assertEquals(15_000L, ClashCore.healthCheckWaitMs(0))
        assertEquals(15_000L, ClashCore.healthCheckWaitMs(-3))
    }

    @Test
    fun smallGroupWaitsMinimum() {
        assertEquals(15_000L, ClashCore.healthCheckWaitMs(1))
        assertEquals(15_000L, ClashCore.healthCheckWaitMs(10))
    }

    @Test
    fun waitScalesWithBatchesOfTen() {
        // 2 batches: 2 * 6000 + 5000 = 17000
        assertEquals(17_000L, ClashCore.healthCheckWaitMs(11))
        assertEquals(17_000L, ClashCore.healthCheckWaitMs(20))
        // 3 batches: 3 * 6000 + 5000 = 23000
        assertEquals(23_000L, ClashCore.healthCheckWaitMs(21))
        // 10 batches: 10 * 6000 + 5000 = 65000
        assertEquals(65_000L, ClashCore.healthCheckWaitMs(100))
    }

    @Test
    fun validDelayPassesThrough() {
        assertEquals(120, ClashCore.normalizeHealthDelay(120, false))
        assertEquals(120, ClashCore.normalizeHealthDelay(120, true))
        assertEquals(65534, ClashCore.normalizeHealthDelay(65534, true))
    }

    @Test
    fun explicitTimeoutStaysTimeout() {
        assertEquals(-2, ClashCore.normalizeHealthDelay(-2, false))
        assertEquals(-2, ClashCore.normalizeHealthDelay(-2, true))
        assertEquals(-2, ClashCore.normalizeHealthDelay(65535, false))
        assertEquals(-2, ClashCore.normalizeHealthDelay(65535, true))
    }

    @Test
    fun unreportedNodeIsTimeoutOnlyAfterCoreFinishes() {
        // Still running: keep -1 (not tested yet), UI shows "--".
        assertEquals(-1, ClashCore.normalizeHealthDelay(-1, false))
        assertEquals(-1, ClashCore.normalizeHealthDelay(0, false))
        // Core finished and the node never reported: timeout, UI shows 超时.
        assertEquals(-2, ClashCore.normalizeHealthDelay(-1, true))
        assertEquals(-2, ClashCore.normalizeHealthDelay(0, true))
    }
}
