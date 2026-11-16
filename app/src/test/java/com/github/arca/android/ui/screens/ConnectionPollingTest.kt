package com.github.arca.android.ui.screens

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.github.arca.android.core.model.ConnectionInfo
import com.github.arca.android.core.model.ConnectionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionPollingTest {
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun stoppedLifecycleDoesNotPollAndRestartSamplesImmediately() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val owner = Owner().apply { registry.currentState = Lifecycle.State.CREATED }
            var queries = 0
            val job = launch {
                pollConnectionsWhileStarted(owner.lifecycle, MutableStateFlow(false),
                    querySnapshot = { queries++; ConnectionSnapshot() },
                    onConnections = {}, onUnsupported = { error("supported") },
                    workerDispatcher = dispatcher)
            }
            runCurrent()
            advanceTimeBy(5000)
            runCurrent()
            assertEquals(0, queries)

            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(1, queries)
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(2, queries)

            owner.registry.currentState = Lifecycle.State.CREATED // ON_STOP
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals("no background samples", 2, queries)

            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals("resume does not wait one second", 3, queries)
            job.cancelAndJoin() // LaunchedEffect leaving composition
            advanceTimeBy(5000)
            runCurrent()
            assertEquals("screen disposal cancels future sampling", 3, queries)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun pauseCancelsPendingSampleAndResumePreservesDisplayedListUntilNewSample() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val owner = Owner().apply { registry.currentState = Lifecycle.State.STARTED }
            val paused = MutableStateFlow(false)
            var queries = 0
            var cancelledQueries = 0
            var displayed = listOf(ConnectionInfo("existing"))
            val job = launch {
                pollConnectionsWhileStarted(owner.lifecycle, paused,
                    querySnapshot = {
                        queries++
                        if (queries == 1) {
                            try { awaitCancellation() } finally { cancelledQueries++ }
                        }
                        ConnectionSnapshot(connections = listOf(ConnectionInfo("fresh")))
                    },
                    onConnections = { displayed = it }, onUnsupported = { error("supported") },
                    workerDispatcher = dispatcher)
            }
            runCurrent()
            assertEquals(1, queries)
            paused.value = true
            runCurrent()
            assertEquals("pause cancels the in-flight coroutine", 1, cancelledQueries)
            advanceTimeBy(5000)
            runCurrent()
            assertEquals(1, queries)
            assertEquals(listOf("existing"), displayed.map { it.id })

            paused.value = false
            runCurrent()
            assertEquals(2, queries)
            assertEquals(listOf("fresh"), displayed.map { it.id })
            job.cancelAndJoin()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun sortingKeepsDescendingStartAndStableTiesAndNullListMeansEmpty() = runTest {
        val main = StandardTestDispatcher(testScheduler, "main")
        val worker = StandardTestDispatcher(testScheduler, "snapshot-worker")
        Dispatchers.setMain(main)
        try {
            val owner = Owner().apply { registry.currentState = Lifecycle.State.STARTED }
            val input = listOf(
                ConnectionInfo("older", start = "2026-01-01T00:00:00Z"),
                ConnectionInfo("newer-a", start = "2026-01-02T00:00:00Z"),
                ConnectionInfo("newer-b", start = "2026-01-02T00:00:00Z"),
                ConnectionInfo("unknown")
            )
            var queries = 0
            val results = mutableListOf<List<ConnectionInfo>>()
            val job = launch(main) {
                pollConnectionsWhileStarted(owner.lifecycle, MutableStateFlow(false),
                    querySnapshot = {
                        assertSame(worker, currentCoroutineContext()[ContinuationInterceptor])
                        queries++
                        ConnectionSnapshot(connections = if (queries == 1) input else null)
                    },
                    onConnections = { results.add(it) }, onUnsupported = { error("supported") },
                    workerDispatcher = worker)
            }
            runCurrent()
            assertEquals(listOf("newer-a", "newer-b", "older", "unknown"), results.single().map { it.id })
            assertEquals(listOf("older", "newer-a", "newer-b", "unknown"), input.map { it.id })
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(emptyList<ConnectionInfo>(), results.last())
            job.cancelAndJoin()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun stoppingDuringPendingQueryCancelsItWithoutPublishingStaleResult() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val owner = Owner().apply { registry.currentState = Lifecycle.State.STARTED }
            var cancellations = 0
            var results = 0
            val job = launch {
                pollConnectionsWhileStarted(owner.lifecycle, MutableStateFlow(false),
                    querySnapshot = {
                        try { awaitCancellation() } finally { cancellations++ }
                    },
                    onConnections = { results++ }, onUnsupported = { error("not unsupported") },
                    workerDispatcher = dispatcher)
            }
            runCurrent()
            owner.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            assertEquals(1, cancellations)
            assertEquals(0, results)
            job.cancelAndJoin()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
