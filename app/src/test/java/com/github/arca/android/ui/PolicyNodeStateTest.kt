package com.github.arca.android.ui

import com.github.arca.android.ui.screens.ProxyNodeFocusRequest
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PolicyNodeStateTest {
    @Test(timeout = 5_000)
    fun queuedAddedDefaultWinsOverInflightSuccessfulSwitch() = assertQueuedSavedNodeSelection(true, true)

    @Test(timeout = 5_000)
    fun queuedAddedDefaultStillWinsAfterEarlierSwitchFailure() = assertQueuedSavedNodeSelection(false, true)

    @Test(timeout = 5_000)
    fun savedAddedDefaultRemainsVisibleWhenItsCoreReloadFails() = assertQueuedSavedNodeSelection(true, false)

    @Test(timeout = 5_000)
    fun noHotSwitchChoiceWaitsForInflightSwitchAndKeepsItsOwnSelection() = assertQueuedSelectionWithoutLoad(false)

    @Test(timeout = 5_000)
    fun nonRunningChoiceWaitsForInflightSwitchWithoutReloadingCore() = assertQueuedSelectionWithoutLoad(true)

    private fun assertQueuedSavedNodeSelection(switchSucceeds: Boolean, nodeLoadSucceeds: Boolean) = runBlocking {
        val queue = PolicyProfileLoadQueue()
        var selected = "A"
        var persisted = "A"
        var loaded: String? = "A"
        var display: PolicyProxyDisplay? = null
        var focus: ProxyNodeFocusRequest? = null
        var reportedCoreError: Throwable? = null
        val defaultId = "builtin-default"
        // The file edit has already saved this node, independently of subsequent core loads.
        val savedGroups = mapOf("Main" to ProxyGroup("select", listOf(Proxy("new-saved-node", type = "ss"))))
        val request = ProxyNodeFocusRequest(defaultId, "Main", listOf("new-saved-node"))
        val events = mutableListOf<String>()
        val loadEntered = CompletableDeferred<Unit>()
        val finishLoad = CompletableDeferred<Unit>()
        val switching = async(start = CoroutineStart.UNDISPATCHED) {
            queue.run(
                resolveProfile = { "B" },
                load = { profile ->
                    loaded = null
                    events += "load:$profile"
                    loadEntered.complete(Unit)
                    finishLoad.await()
                    if (switchSucceeds) {
                        loaded = profile
                        Result.success(Unit)
                    } else Result.failure(IllegalStateException("B failed"))
                },
                commit = { profile, _ ->
                    selected = profile
                    persisted = profile
                    events += "select:$profile"
                }
            )
        }
        loadEntered.await()
        val savedNodeSelection = async(start = CoroutineStart.UNDISPATCHED) {
            queue.run(
                resolveProfile = { defaultId },
                load = { profile ->
                    loaded = null
                    events += "load:$profile"
                    if (nodeLoadSucceeds) {
                        loaded = profile
                        Result.success(Unit)
                    } else Result.failure(IllegalStateException("saved default core load failed"))
                },
                commitOnFailure = true,
                commit = { profile, result ->
                    selected = profile
                    persisted = profile
                    display = PolicyProxyDisplay(profile, savedGroups, savedGroups, loaded)
                    focus = request
                    reportedCoreError = result.exceptionOrNull()
                    events += "show:$profile"
                }
            )
        }
        assertEquals("A", selected)
        assertEquals("A", persisted)
        assertNull(display)
        assertNull(focus)
        assertEquals(listOf("load:B"), events)
        finishLoad.complete(Unit)
        assertEquals(switchSucceeds, switching.await().isSuccess)
        assertEquals(nodeLoadSucceeds, savedNodeSelection.await().isSuccess)
        assertEquals(defaultId, selected)
        assertEquals(defaultId, persisted)
        assertEquals(if (nodeLoadSucceeds) defaultId else null, loaded)
        assertEquals(!nodeLoadSucceeds, reportedCoreError != null)
        assertTrue(display!!.groups.getValue("Main").proxies.any { it.name == "new-saved-node" })
        assertEquals(request, display!!.readyFocus(focus, selected, savedGroups, loaded))
        val expected = mutableListOf("load:B")
        if (switchSucceeds) expected += "select:B"
        expected += listOf("load:$defaultId", "show:$defaultId")
        assertEquals(expected, events)
    }

    private fun assertQueuedSelectionWithoutLoad(stopBeforeChoice: Boolean) = runBlocking {
        val queue = PolicyProfileLoadQueue()
        var selected = "A"
        var persisted = "A"
        var loaded: String? = "A"
        var running = true
        val coreLoads = mutableListOf<String>()
        val events = mutableListOf<String>()
        val loadEntered = CompletableDeferred<Unit>()
        val finishLoad = CompletableDeferred<Unit>()
        val switching = async(start = CoroutineStart.UNDISPATCHED) {
            queue.run(
                resolveProfile = { "B" },
                load = { profile ->
                    loaded = null
                    coreLoads += profile
                    events += "load:$profile"
                    loadEntered.complete(Unit)
                    finishLoad.await()
                    if (running) loaded = profile
                    Result.success(Unit)
                },
                commit = { profile, _ ->
                    selected = profile
                    persisted = profile
                    events += "select:$profile"
                }
            )
        }
        loadEntered.await()
        if (stopBeforeChoice) running = false
        val noLoadChoice = async(start = CoroutineStart.UNDISPATCHED) {
            queue.run(
                resolveProfile = { "C" },
                load = { profile ->
                    // Mirrors loadInCore=false: skip the core, but remain in the selection queue.
                    events += "skip:$profile"
                    Result.success(Unit)
                },
                commit = { profile, _ ->
                    selected = profile
                    persisted = profile
                    events += "select:$profile"
                }
            )
        }
        assertEquals("A", selected)
        assertEquals("A", persisted)
        assertEquals(listOf("load:B"), events)
        finishLoad.complete(Unit)
        assertTrue(switching.await().isSuccess)
        assertTrue(noLoadChoice.await().isSuccess)
        assertEquals("C", selected)
        assertEquals("C", persisted)
        assertEquals(if (stopBeforeChoice) null else "B", loaded)
        assertEquals(listOf("B"), coreLoads)
        assertFalse(canUsePolicyRuntime(selected, loaded))
        assertEquals(listOf("load:B", "select:B", "skip:C", "select:C"), events)
    }

    @Test(timeout = 5_000)
    fun queuedSettingsReloadWaitsForSuccessfulSwitchSelectionCommit() = runBlocking {
        val queue = PolicyProfileLoadQueue()
        var selected = "A"
        var persisted = "A"
        var loaded: String? = "A"
        val events = mutableListOf<String>()
        val loadEntered = CompletableDeferred<Unit>()
        val finishLoad = CompletableDeferred<Unit>()
        val commitEntered = CompletableDeferred<Unit>()
        val finishCommit = CompletableDeferred<Unit>()
        val switching = async(start = CoroutineStart.UNDISPATCHED) {
            queue.run(
                resolveProfile = { "B" },
                load = { profile ->
                    loaded = null
                    events += "load:$profile"
                    loadEntered.complete(Unit)
                    finishLoad.await()
                    loaded = profile
                    Result.success(Unit)
                },
                commit = { profile, _ ->
                    commitEntered.complete(Unit)
                    finishCommit.await()
                    selected = profile
                    persisted = profile
                    events += "commit:$profile"
                }
            )
        }
        loadEntered.await()
        val settingsReload = async(start = CoroutineStart.UNDISPATCHED) {
            queue.run(
                resolveProfile = { selected.also { events += "resolve:$it" } },
                load = { profile ->
                    loaded = profile
                    events += "reload:$profile"
                    Result.success(Unit)
                }
            )
        }
        assertEquals(listOf("load:B"), events)
        assertNull(loaded)
        finishLoad.complete(Unit)
        commitEntered.await()
        assertEquals("A", selected)
        assertEquals("A", persisted)
        assertEquals(listOf("load:B"), events)
        finishCommit.complete(Unit)
        assertTrue(switching.await().isSuccess)
        assertTrue(settingsReload.await().isSuccess)
        assertEquals("B", selected)
        assertEquals("B", persisted)
        assertEquals("B", loaded)
        assertEquals(listOf("load:B", "commit:B", "resolve:B", "reload:B"), events)
    }

    @Test(timeout = 5_000)
    fun failedSwitchLeavesSelectionForTheQueuedSettingsReload() = runBlocking {
        val queue = PolicyProfileLoadQueue()
        var selected = "A"
        var persisted = "A"
        var loaded: String? = "A"
        val events = mutableListOf<String>()
        val loadEntered = CompletableDeferred<Unit>()
        val finishLoad = CompletableDeferred<Unit>()
        val switching = async(start = CoroutineStart.UNDISPATCHED) {
            queue.run(
                resolveProfile = { "B" },
                load = { profile ->
                    loaded = null
                    events += "load:$profile"
                    loadEntered.complete(Unit)
                    finishLoad.await()
                    Result.failure(IllegalStateException("rejected profile B"))
                },
                commit = { profile, _ ->
                    selected = profile
                    persisted = profile
                    events += "commit:$profile"
                }
            )
        }
        loadEntered.await()
        val settingsReload = async(start = CoroutineStart.UNDISPATCHED) {
            queue.run(
                resolveProfile = { selected.also { events += "resolve:$it" } },
                load = { profile ->
                    loaded = profile
                    events += "reload:$profile"
                    Result.success(Unit)
                }
            )
        }
        assertNull(loaded)
        assertEquals(listOf("load:B"), events)
        finishLoad.complete(Unit)
        assertTrue(switching.await().isFailure)
        assertTrue(settingsReload.await().isSuccess)
        assertEquals("A", selected)
        assertEquals("A", persisted)
        assertEquals("A", loaded)
        assertEquals(listOf("load:B", "resolve:A", "reload:A"), events)
    }

    @Test fun runtimeIdentityMustBeKnownAndMatchTheSelectedProfile() {
        assertFalse(canUsePolicyRuntime(null, null))
        assertFalse(canUsePolicyRuntime("builtin-default", null))
        assertFalse(canUsePolicyRuntime("builtin-default", "remote"))
        assertFalse(canUsePolicyRuntime("remote", "builtin-default"))
        assertTrue(canUsePolicyRuntime("builtin-default", "builtin-default"))
    }

    @Test fun focusWaitsForTheNewSourceSnapshotEvenWhenProfileIdAndNodeNamesMatch() {
        val old = mapOf("Main" to ProxyGroup("select", listOf(Proxy("HK", type = "trojan"))))
        val current = mapOf("Main" to ProxyGroup("select", listOf(Proxy("HK", type = "ss"))))
        val request = ProxyNodeFocusRequest("builtin-default", "Main", listOf("HK"))
        val oldDisplay = PolicyProxyDisplay("builtin-default", old, old)
        assertNull(oldDisplay.readyFocus(request, "builtin-default", current))
        val ready = PolicyProxyDisplay("builtin-default", current, current)
        assertEquals(request, ready.readyFocus(request, "builtin-default", current))
        assertNull(ready.readyFocus(request, "remote", current))
        assertNull(ready.readyFocus(request, "builtin-default", current, "builtin-default"))
        assertFalse(PolicyProxyDisplay("builtin-default", current, current, "remote")
            .matches("builtin-default", current, null))
    }
}
