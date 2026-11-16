package com.github.arca.android.ui.screens

import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RequestLogPresentationIntegrationTest {
    @get:Rule val compose = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun entry(id: Long) = LogEntry(id, timestamp = id, level = "info",
        message = "[TCP] src:$id --> host$id.example:443 match Domain using Proxy")

    private fun awaitText(text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().size == 1
        }
    }

    @Test fun stoppedOwnerUnsubscribesAndResumeUsesLatestSnapshotAndFilter() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.CREATED }
        val logs = MutableStateFlow(listOf(entry(1)))
        val query = mutableStateOf("")
        compose.setContent {
            val presentation = rememberRequestLogPresentation(owner.lifecycle, logs, query.value, "ALL")
            Text("ids=" + presentation.filteredRequests.joinToString { it.id.toString() })
        }
        compose.runOnIdle { assertEquals(0, logs.subscriptionCount.value) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        awaitText("ids=1")
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitUntil(5_000) { logs.subscriptionCount.value == 0 }
        compose.runOnIdle {
            logs.value = listOf(entry(2), entry(3))
            query.value = "host2"
        }
        compose.runOnIdle { assertEquals(0, logs.subscriptionCount.value) }
        awaitText("ids=1")
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        awaitText("ids=2")
        compose.runOnIdle { query.value = "host3" }
        awaitText("ids=3")
        compose.runOnIdle { logs.value = emptyList() }
        awaitText("ids=")
        compose.runOnIdle { logs.value = listOf(entry(4)); query.value = "" }
        awaitText("ids=4")
    }

    @Test fun clearDuringWorkerParseNeverPublishesTheCancelledSnapshot() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.STARTED }
        val logs = MutableStateFlow<List<LogEntry>>(listOf(entry(1)))
        val seen = CopyOnWriteArrayList<List<Long>>()
        val enteredWorker = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        compose.setContent {
            val presentation = rememberRequestLogPresentation(owner.lifecycle, logs, "", "ALL")
            val ids = presentation.filteredRequests.map { it.id }
            SideEffect { seen.add(ids) }
            Text("ids=" + ids.joinToString())
        }
        awaitText("ids=1")
        // Block only the worker's list traversal, never StateFlow equality on the UI thread.
        val pending = object : AbstractList<LogEntry>() {
            override val size: Int get() = 1
            override fun get(index: Int): LogEntry {
                check(index == 0)
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    enteredWorker.countDown()
                    check(releaseWorker.await(5, TimeUnit.SECONDS)) { "test worker was not released" }
                }
                return entry(2)
            }
        }
        try {
            compose.runOnIdle { logs.value = pending }
            compose.waitUntil(5_000) { enteredWorker.count == 0L }
            compose.runOnIdle { logs.value = emptyList() }
        } finally {
            releaseWorker.countDown()
        }
        awaitText("ids=")
        compose.runOnIdle { logs.value = listOf(entry(3)) }
        awaitText("ids=3")
        assertFalse("cancelled worker must not flash row 2 after clear", seen.any { 2L in it })
    }
}
