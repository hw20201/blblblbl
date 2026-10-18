package com.github.mihomo.android.service

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** These stopped-service paths must not reach core/JNI startup at all. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VpnRestartCommandTest {
    @Test
    fun settingsRestartDoesNotStartAnIdleService() {
        val controller = Robolectric.buildService(MihomoVpnService::class.java).create()
        val service = controller.get()
        assertEquals(VpnState.Status.STOPPED, MihomoVpnService.vpnState.value.status)
        service.onStartCommand(Intent().setAction(MihomoVpnService.ACTION_RESTART), 0, 1)
        assertEquals(VpnState.Status.STOPPED, MihomoVpnService.vpnState.value.status)
    }

    @Test
    fun delayedSettingsRestartCannotUndoAnExplicitStop() {
        val controller = Robolectric.buildService(MihomoVpnService::class.java).create()
        val service = controller.get()
        service.onStartCommand(Intent().setAction(MihomoVpnService.ACTION_STOP), 0, 1)
        service.onStartCommand(Intent().setAction(MihomoVpnService.ACTION_RESTART), 0, 2)
        assertEquals(VpnState.Status.STOPPED, MihomoVpnService.vpnState.value.status)
    }
}
