package com.github.arca.android.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
class ScriptManagerProxyPortTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun localProxyClientTracksConfiguredMixedPort() {
        assertEquals(
            InetSocketAddress("127.0.0.1", DEFAULT_LOCAL_MIXED_PROXY_PORT),
            ScriptManager.localProxyClient(context).proxy?.address()
        )
        assertEquals(true, SettingsManager(context).setLocalProxyPorts(LocalProxyPorts(2080, 5353)))
        assertEquals(
            InetSocketAddress("127.0.0.1", 2080),
            ScriptManager.localProxyClient(context).proxy?.address()
        )
    }
}
