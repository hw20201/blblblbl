package com.github.arca.android.service

import android.content.Intent
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise real service lifecycle branches, holding replacement startup before core/JNI work. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VpnRestartCommandTest {
    @Test
    fun settingsRestartDoesNotStartAnIdleService() = runBlocking {
        val service = Robolectric.buildService(MihomoVpnService::class.java).create().get()
        try {
            assertEquals(VpnState.Status.STOPPED, MihomoVpnService.vpnState.value.status)
            service.onStartCommand(Intent().setAction(MihomoVpnService.ACTION_RESTART), 0, 1)
            assertEquals(VpnState.Status.STOPPED, MihomoVpnService.vpnState.value.status)
        } finally {
            destroyAndJoin(service)
        }
    }

    @Test
    fun delayedSettingsRestartCannotUndoAnExplicitStop() = runBlocking {
        val service = Robolectric.buildService(MihomoVpnService::class.java).create().get()
        val originalScope = instanceField("serviceScope").get(service)
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            // An unobserved IO-to-paused-Main continuation would retain the process-wide mutex.
            instanceField("serviceScope").set(service, workerScope)
            service.onStartCommand(Intent().setAction(MihomoVpnService.ACTION_STOP), 0, 1)
            service.onStartCommand(Intent().setAction(MihomoVpnService.ACTION_RESTART), 0, 2)
            withTimeout(10_000) { (instanceField("stopJob").get(service) as Job).join() }
            assertEquals(VpnState.Status.STOPPED, MihomoVpnService.vpnState.value.status)
        } finally {
            workerScope.cancel()
            withTimeout(10_000) { workerScope.coroutineContext[Job]!!.join() }
            instanceField("serviceScope").set(service, originalScope)
            instanceField("stopJob").set(service, null)
            destroyAndJoin(service)
        }
    }

    @Test
    fun delayedOldServiceDestroyReleasesItsDescriptorWithoutStoppingTheNewOwner() = runBlocking {
        val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cleanupScope = staticField("cleanupScope").get(null) as CoroutineScope
        val started = CompletableDeferred<Unit>()
        val cancellationEntered = CompletableDeferred<Unit>()
        val releaseStartup = CompletableDeferred<Unit>()
        val starting = startupScope.launch {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    cancellationEntered.complete(Unit)
                    releaseStartup.await()
                }
            }
        }
        val oldService = Robolectric.buildService(MihomoVpnService::class.java).create().get()
        val newService = Robolectric.buildService(MihomoVpnService::class.java).create().get()
        val descriptors = ParcelFileDescriptor.createPipe()
        val oldDescriptor = descriptors[0]
        val replacementDescriptor = descriptors[1]
        val oldFileDescriptor = oldDescriptor.fileDescriptor
        val replacementFileDescriptor = replacementDescriptor.fileDescriptor
        val originalState = state().value
        val originalOwner = staticField("coreOwner").get(null)
        val oldOwner = instanceField("ownerToken").get(oldService)
        val newOwner = instanceField("ownerToken").get(newService)
        var pendingCleanup = emptyList<Job>()
        try {
            withTimeout(10_000) {
                started.await()
                instanceField("startJob").set(oldService, starting)
                staticField("coreOwner").set(null, oldOwner)
                instanceField("vpnInterface").set(oldService, oldDescriptor)
                instanceField("vpnInterface").set(newService, replacementDescriptor)
                state().value = VpnState(status = VpnState.Status.RUNNING, coreVersion = "old-core")

                // Real onDestroy schedules IO cleanup, which must join the cancelled startup.
                oldService.onDestroy()
                cancellationEntered.await()
                assertTrue(oldFileDescriptor.valid())
                assertSame(oldOwner, staticField("coreOwner").get(null))
                pendingCleanup = cleanupScope.coroutineContext[Job]!!.children.toList()
                assertTrue("destroy cleanup must wait for the old start", pendingCleanup.isNotEmpty())

                // A newer service wins ownership while old cleanup is still waiting.
                val newState = VpnState(status = VpnState.Status.RUNNING, coreVersion = "new-core")
                staticField("coreOwner").set(null, newOwner)
                state().value = newState
                releaseStartup.complete(Unit)
                pendingCleanup.forEach { it.join() }

                assertNull(instanceField("vpnInterface").get(oldService))
                assertFalse("old borrowed descriptor must still be released", oldFileDescriptor.valid())
                assertTrue("replacement descriptor must remain open", replacementFileDescriptor.valid())
                assertSame("old destroy must not clear the new native owner", newOwner, staticField("coreOwner").get(null))
                assertEquals("old destroy must not overwrite replacement state", newState, state().value)
            }
        } finally {
            releaseStartup.complete(Unit)
            startupScope.cancel()
            withTimeout(10_000) {
                starting.join()
                pendingCleanup.forEach { it.join() }
            }
            staticField("coreOwner").set(null, originalOwner)
            state().value = originalState
            instanceField("vpnInterface").set(newService, null)
            runCatching { oldDescriptor.close() }
            runCatching { replacementDescriptor.close() }
        }
    }

    @Test
    fun restartDuringStartupWaitsForCancellationThenSchedulesReplacementWithNoOwner() = runBlocking {
        val controlledServiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val service = Robolectric.buildService(MihomoVpnService::class.java).create().get()
        val originalScope = instanceField("serviceScope").get(service)
        val originalState = state().value
        val originalOwner = staticField("coreOwner").get(null)
        val started = CompletableDeferred<Unit>()
        val cancellationEntered = CompletableDeferred<Unit>()
        val releaseCancelledStart = CompletableDeferred<Unit>()
        val previousStop = Job()
        val owner = instanceField("ownerToken").get(service)
        val cancelledStart = controlledServiceScope.launch {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                // Model the in-flight start's asynchronous, non-cancellable resource release.
                withContext(NonCancellable) {
                    cancellationEntered.complete(Unit)
                    releaseCancelledStart.await()
                    staticField("coreOwner").set(null, null)
                }
            }
        }
        try {
            withTimeout(10_000) {
                started.await()
                instanceField("serviceScope").set(service, controlledServiceScope)
                instanceField("startJob").set(service, cancelledStart)
                // The real replacement start waits here before ownership or native initialization.
                instanceField("stopJob").set(service, previousStop)
                staticField("coreOwner").set(null, owner)
                state().value = VpnState(status = VpnState.Status.STARTING)

                service.onStartCommand(Intent().setAction(MihomoVpnService.ACTION_RESTART), 0, 1)
                cancellationEntered.await()
                assertTrue(cancelledStart.isCancelled)
                assertFalse("restart must join non-cancellable startup cleanup", cancelledStart.isCompleted)
                assertSame(cancelledStart, instanceField("startJob").get(service))
                val restartControl = controlledServiceScope.coroutineContext[Job]!!.children
                    .single { it !== cancelledStart }

                releaseCancelledStart.complete(Unit)
                restartControl.join()
                val replacement = instanceField("startJob").get(service) as Job
                assertNotSame("null owner after cancelled startup must allow a new start", cancelledStart, replacement)
                assertTrue("replacement is queued behind the previous stop", replacement.isActive)
                assertNull(staticField("coreOwner").get(null))
                assertFalse("the stop gate deliberately keeps this test outside JNI", previousStop.isCompleted)
            }
        } finally {
            releaseCancelledStart.complete(Unit)
            controlledServiceScope.cancel()
            previousStop.cancel()
            withTimeout(10_000) { controlledServiceScope.coroutineContext[Job]!!.join() }
            instanceField("serviceScope").set(service, originalScope)
            instanceField("startJob").set(service, null)
            instanceField("stopJob").set(service, null)
            staticField("coreOwner").set(null, originalOwner)
            state().value = originalState
        }
    }

    private suspend fun destroyAndJoin(service: MihomoVpnService) {
        service.onDestroy()
        val cleanupScope = staticField("cleanupScope").get(null) as CoroutineScope
        val pending = cleanupScope.coroutineContext[Job]!!.children.toList()
        try {
            withTimeout(10_000) { pending.forEach { it.join() } }
        } catch (failure: Exception) {
            throw AssertionError("Pending destroy cleanup=$pending start=${instanceField("startJob").get(service)} stop=${instanceField("stopJob").get(service)}", failure)
        }
    }

    private fun instanceField(name: String) = MihomoVpnService::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }

    private fun staticField(name: String) = instanceField(name)

    @Suppress("UNCHECKED_CAST")
    private fun state() = staticField("_vpnState").get(null) as MutableStateFlow<VpnState>
}
