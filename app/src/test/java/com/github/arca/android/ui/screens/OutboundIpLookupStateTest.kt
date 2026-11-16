package com.github.arca.android.ui.screens

import com.github.arca.android.data.OutboundIpInfo
import com.github.arca.android.data.OutboundIpResult
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class OutboundIpLookupStateTest {
    @Test fun stateIsManualAndRefreshIsSingleFlight() = runBlocking {
        val state = OutboundIpLookupState()
        var calls = 0
        val release = CompletableDeferred<Unit>()
        val lookup: suspend ((OutboundIpInfo) -> Unit) -> OutboundIpResult = {
            calls++
            release.await()
            OutboundIpResult(ip("203.0.113.1"))
        }
        assertEquals(0, calls)
        assertNull(state.result)
        state.refresh(this, lookup)
        state.refresh(this, lookup)
        yield()
        assertEquals(1, calls)
        assertTrue(state.loading)
        release.complete(Unit)
        yield()
        assertFalse(state.loading)
        assertEquals("203.0.113.1", state.result?.outbound?.ipAddress)
    }

    @Test fun failedRefreshClearsPreviousIpAndRisk() = runBlocking {
        val state = OutboundIpLookupState()
        state.refresh(this) { OutboundIpResult(ip("203.0.113.1")) }
        yield()
        assertNotNull(state.result)
        state.refresh(this) { throw IOException("https://example.com/?key=fake-secret") }
        assertNull(state.result)
        yield()
        assertNull(state.result)
        assertFalse(state.error!!.contains("fake-secret"))
        assertFalse(state.error!!.contains("https://"))
    }

    @Test fun freshIpSurvivesRiskFailureAndCloseCancelsPendingWork() = runBlocking {
        val state = OutboundIpLookupState()
        val pending = CompletableDeferred<Unit>()
        var canceled = false
        state.refresh(this) { resolved ->
            resolved(ip("203.0.113.2"))
            try { pending.await() } finally { canceled = true }
            OutboundIpResult(ip("203.0.113.2"), riskError = "fixture unavailable")
        }
        yield()
        assertEquals("203.0.113.2", state.result?.outbound?.ipAddress)
        assertNull(state.result?.scamalytics)
        state.cancel()
        yield()
        assertTrue(canceled)
        assertFalse(state.loading)
        state.refresh(this) { OutboundIpResult(ip("203.0.113.3"), riskError = "fixture unavailable") }
        yield()
        assertEquals("203.0.113.3", state.result?.outbound?.ipAddress)
        assertEquals("fixture unavailable", state.result?.riskError)
    }

    @Test fun canceledGenerationCannotOverwriteNewResult() = runBlocking {
        val state = OutboundIpLookupState()
        val stale = CompletableDeferred<Unit>()
        state.refresh(this) { resolved ->
            withContext(NonCancellable) { stale.await() }
            resolved(ip("203.0.113.99"))
            OutboundIpResult(ip("203.0.113.99"))
        }
        yield()
        state.reset()
        state.refresh(this) { OutboundIpResult(ip("203.0.113.4")) }
        yield()
        stale.complete(Unit)
        yield()
        assertEquals("203.0.113.4", state.result?.outbound?.ipAddress)
        assertFalse(state.loading)
    }

    private fun ip(value: String) = OutboundIpInfo(value, source = "fixture")
}
