package com.github.arca.android.service.network

import com.github.arca.android.core.DnsUpdate
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalNetworkObservationTest {
    @Test
    fun startAndCallbacksOnlyEnqueueAndDuplicateStartRegistersOnce() {
        val fixture = Fixture()
        fixture.observer.start()
        fixture.observer.start()
        assertEquals(0, fixture.adapter.registrations)
        assertTrue(fixture.updates.isEmpty())
        fixture.worker.drain()
        assertEquals(1, fixture.adapter.registrations)
        assertEquals(1, fixture.updates.size)
        val queries = fixture.adapter.snapshotQueries
        repeat(10_000) { fixture.adapter.changed() }
        assertEquals(queries, fixture.adapter.snapshotQueries)
        assertEquals(1, fixture.worker.pendingCount)
        fixture.worker.drain()
        assertEquals(queries + 1, fixture.adapter.snapshotQueries)
        assertEquals(1, fixture.updates.size)
    }

    @Test
    fun invalidationDuringSnapshotStillReadsTheFinalSnapshotWithoutAnotherQueuedTask() {
        val fixture = Fixture()
        fixture.observer.start()
        fixture.worker.drain()
        fixture.adapter.duringSnapshot = {
            fixture.adapter.duringSnapshot = null
            fixture.adapter.current = PhysicalNetworkSnapshot(
                listOf(PhysicalNetworkCandidate("net-2", listOf("1.1.1.1")))
            )
            repeat(100) { fixture.adapter.changed() }
            assertEquals(0, fixture.worker.pendingCount)
        }
        val queries = fixture.adapter.snapshotQueries
        fixture.adapter.changed()
        fixture.worker.drain()
        assertEquals(queries + 2, fixture.adapter.snapshotQueries)
        assertEquals(listOf("net-1", "net-2"), fixture.updates.map { it.networkIdentity })
        assertEquals("1.1.1.1", fixture.updates.last().orderedDnsKey)
    }

    @Test
    fun defaultNetworkSwitchWithEqualDnsNotifiesOnce() {
        val fixture = Fixture()
        val first = PhysicalNetworkCandidate("net-1", listOf("8.8.8.8"), validated = true)
        val second = first.copy(identity = "net-2")
        fixture.adapter.current = PhysicalNetworkSnapshot(listOf(first, second), "net-1")
        fixture.observer.start()
        fixture.worker.drain()
        fixture.adapter.current = PhysicalNetworkSnapshot(listOf(first, second), "net-2")
        repeat(4) { fixture.adapter.changed() }
        fixture.worker.drain()
        assertEquals(listOf("net-1", "net-2"), fixture.updates.map { it.networkIdentity })
        assertEquals(listOf("8.8.8.8", "8.8.8.8"), fixture.updates.map { it.orderedDnsKey })
    }

    @Test
    fun lostAndEmptyDnsDoNotPublishVpnDnsOrFallbackOverKnownDns() {
        val fixture = Fixture()
        fixture.observer.start()
        fixture.worker.drain()
        fixture.adapter.current = PhysicalNetworkSnapshot(emptyList())
        fixture.adapter.changed()
        fixture.worker.drain()
        fixture.adapter.current = PhysicalNetworkSnapshot(listOf(PhysicalNetworkCandidate("net-2")))
        fixture.adapter.changed()
        fixture.worker.drain()
        fixture.adapter.current = PhysicalNetworkSnapshot(
            listOf(PhysicalNetworkCandidate("vpn", listOf("172.19.0.2"), isVpn = true)), "vpn"
        )
        fixture.adapter.changed()
        fixture.worker.drain()
        assertEquals(listOf(DnsUpdate("net-1", listOf("8.8.8.8"))), fixture.updates)
    }

    @Test
    fun temporaryEmptySnapshotDoesNotDiscardThePreviousCandidateTiePreference() {
        val fixture = Fixture()
        val first = PhysicalNetworkCandidate("net-1", listOf("8.8.8.8"))
        val selected = first.copy(identity = "net-2")
        fixture.adapter.current = PhysicalNetworkSnapshot(listOf(first, selected), "net-2")
        fixture.observer.start()
        fixture.worker.drain()
        fixture.adapter.current = PhysicalNetworkSnapshot(emptyList())
        fixture.adapter.changed()
        fixture.worker.drain()
        fixture.adapter.current = PhysicalNetworkSnapshot(listOf(first, selected))
        fixture.adapter.changed()
        fixture.worker.drain()
        assertEquals(listOf("net-2"), fixture.updates.map { it.networkIdentity })
    }

    @Test
    fun closeDropsQueuedAndLateCallbacksAndUnregistersExactlyOnce() {
        val fixture = Fixture()
        fixture.observer.start()
        fixture.worker.drain()
        fixture.adapter.current = PhysicalNetworkSnapshot(
            listOf(PhysicalNetworkCandidate("net-2", listOf("1.1.1.1")))
        )
        fixture.adapter.changed()
        fixture.observer.close()
        fixture.observer.close()
        fixture.adapter.changed()
        assertEquals(0, fixture.adapter.unregistrations)
        fixture.worker.drain()
        assertEquals(1, fixture.adapter.unregistrations)
        assertEquals(1, fixture.worker.closeCalls)
        assertEquals(1, fixture.updates.size)
        val queued = fixture.worker.pendingCount
        fixture.adapter.changed()
        fixture.observer.start()
        assertEquals(queued, fixture.worker.pendingCount)
        assertEquals(1, fixture.adapter.registrations)
    }

    @Test
    fun closeBeforeQueuedStartupNeverRegistersOrPublishes() {
        val fixture = Fixture()
        fixture.observer.start()
        fixture.observer.close()
        fixture.worker.drain()
        assertEquals(0, fixture.adapter.registrations)
        assertEquals(0, fixture.adapter.unregistrations)
        assertEquals(1, fixture.worker.closeCalls)
        assertTrue(fixture.updates.isEmpty())
    }

    @Test
    fun registrationFailureClosesWorkerAndAllowsFreshStartWithoutOldCallback() {
        val firstWorker = QueuedWorker()
        val secondWorker = QueuedWorker()
        val failed = FakeAdapter().apply { registrationFailure = IllegalStateException("denied") }
        val healthy = FakeAdapter()
        val workers = ArrayDeque(listOf(firstWorker, secondWorker))
        val adapters = ArrayDeque(listOf(failed, healthy))
        val updates = mutableListOf<DnsUpdate>()
        val errors = mutableListOf<Throwable>()
        val observer = PhysicalNetworkObservation({ workers.removeFirst() }, { adapters.removeFirst() },
            { updates.add(it) }, { errors.add(it) })
        observer.start()
        firstWorker.drain()
        assertEquals(1, firstWorker.closeCalls)
        assertEquals(1, errors.size)
        assertTrue(updates.isEmpty())
        observer.start()
        secondWorker.drain()
        assertEquals(1, healthy.registrations)
        assertEquals(1, updates.size)
        failed.changed()
        assertEquals(0, firstWorker.pendingCount)
        observer.close()
        secondWorker.drain()
        assertEquals(1, healthy.unregistrations)
    }

    @Test
    fun closeDuringRegistrationStillReleasesTheReturnedRegistration() {
        val fixture = Fixture()
        fixture.adapter.duringRegistration = { fixture.observer.close() }
        fixture.observer.start()
        fixture.worker.drain()
        assertEquals(1, fixture.adapter.registrations)
        assertEquals(1, fixture.adapter.unregistrations)
        assertEquals(1, fixture.worker.closeCalls)
        assertTrue(fixture.updates.isEmpty())
    }

    @Test
    fun closeRacingWithRegistrationFailureClosesTheWorkerOnlyOnce() {
        val fixture = Fixture()
        fixture.adapter.duringRegistration = {
            fixture.observer.close()
            throw SecurityException("registration denied while closing")
        }
        fixture.observer.start()
        fixture.worker.drain()
        assertEquals(1, fixture.worker.closeCalls)
        assertEquals(1, fixture.errors.size)
        assertTrue(fixture.updates.isEmpty())
    }

    @Test
    fun unregisterFailureCannotSkipWorkerShutdownOrCauseRepeatUnregister() {
        val fixture = Fixture()
        fixture.adapter.unregisterFailure = IllegalArgumentException("already removed")
        fixture.observer.start()
        fixture.worker.drain()
        fixture.observer.close()
        fixture.worker.drain()
        fixture.observer.close()
        assertEquals(1, fixture.adapter.unregistrations)
        assertEquals(1, fixture.worker.closeCalls)
        assertEquals(1, fixture.errors.size)
    }

    @Test
    fun transientQueryFailureKeepsRegistrationForLaterEvents() {
        val fixture = Fixture()
        fixture.adapter.snapshotFailure = IllegalStateException("temporarily unavailable")
        fixture.observer.start()
        fixture.worker.drain()
        assertEquals(1, fixture.errors.size)
        assertFalse(fixture.worker.closed)
        assertTrue(fixture.updates.isEmpty())
        fixture.adapter.snapshotFailure = null
        fixture.adapter.changed()
        fixture.worker.drain()
        assertEquals(1, fixture.updates.size)
        assertEquals(1, fixture.adapter.registrations)
    }

    private class Fixture {
        val worker = QueuedWorker()
        val adapter = FakeAdapter()
        val updates = mutableListOf<DnsUpdate>()
        val errors = mutableListOf<Throwable>()
        val observer = PhysicalNetworkObservation({ worker }, { adapter }, { updates.add(it) }, { errors.add(it) })
    }

    private class QueuedWorker : NetworkObservationWorker {
        private val tasks = ArrayDeque<() -> Unit>()
        var closeCalls = 0
        val closed get() = closeCalls != 0
        val pendingCount get() = tasks.size

        override fun execute(task: () -> Unit) {
            check(!closed) { "Work enqueued after shutdown" }
            tasks.addLast(task)
        }

        fun drain() {
            while (tasks.isNotEmpty()) tasks.removeFirst().invoke()
        }

        override fun close() {
            closeCalls++
        }
    }

    private class FakeAdapter : PhysicalNetworkAdapter {
        var current = PhysicalNetworkSnapshot(listOf(PhysicalNetworkCandidate("net-1", listOf("8.8.8.8"))))
        var registrations = 0
        var unregistrations = 0
        var snapshotQueries = 0
        var registrationFailure: Exception? = null
        var unregisterFailure: Exception? = null
        var snapshotFailure: Exception? = null
        var duringRegistration: (() -> Unit)? = null
        var duringSnapshot: (() -> Unit)? = null
        private var onChanged: (() -> Unit)? = null

        override fun register(onChanged: () -> Unit): AutoCloseable {
            registrations++
            this.onChanged = onChanged
            registrationFailure?.let { throw it }
            duringRegistration?.invoke()
            return AutoCloseable {
                unregistrations++
                unregisterFailure?.let { throw it }
            }
        }

        override fun snapshot(): PhysicalNetworkSnapshot {
            snapshotQueries++
            snapshotFailure?.let { throw it }
            val snapshot = current
            duringSnapshot?.invoke()
            return snapshot
        }

        fun changed() {
            onChanged?.invoke()
        }
    }
}
