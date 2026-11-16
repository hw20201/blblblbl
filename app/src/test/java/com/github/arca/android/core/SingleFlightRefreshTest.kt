package com.github.arca.android.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SingleFlightRefreshTest {
    @Test fun overlappingTriggersDoNotStartAnotherLoad() = runBlocking {
        val refresh = SingleFlightRefresh()
        val gate = CompletableDeferred<Int>()
        var loads = 0
        var published = 0
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            refresh.run({ "A" }, { loads++; gate.await() }, { true }, { published = it })
        }
        assertFalse(refresh.run({ "A" }, { loads++; 2 }, { true }, { published = it }))
        gate.complete(1)
        assertTrue(first.await())
        assertEquals(1, loads)
        assertEquals(1, published)
    }

    @Test fun cancellationReleasesSlotWithoutPublishing() = runBlocking {
        val refresh = SingleFlightRefresh()
        val gate = CompletableDeferred<Int>()
        var published = 0
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            refresh.run({ "A" }, { gate.await() }, { true }, { published = it })
        }
        first.cancelAndJoin()
        assertEquals(0, published)
        assertTrue(refresh.run({ "A" }, { 2 }, { true }, { published = it }))
        assertEquals(2, published)
    }

    @Test fun configurationChangedDuringLoadCannotPublishOldResult() = runBlocking {
        val refresh = SingleFlightRefresh()
        var key = "A"
        var published = false
        assertFalse(refresh.run({ key }, { key = "B"; 1 }, { true }, { published = true }))
        assertFalse(published)
    }

    @Test fun stoppedVpnOrBackgroundedActivityCannotPublish() = runBlocking {
        val refresh = SingleFlightRefresh()
        var active = true
        var published = false
        assertFalse(refresh.run({ "A" }, { active = false; 1 }, { active }, { published = true }))
        assertFalse(published)
    }
}
