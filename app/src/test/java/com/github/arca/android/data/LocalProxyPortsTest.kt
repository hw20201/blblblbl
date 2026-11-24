package com.github.arca.android.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocalProxyPortsTest {
    private lateinit var context: Context
    private lateinit var settings: SettingsManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsManager(context)
    }

    @Test
    fun defaultsRemainUnwrittenAndDistinct() {
        assertEquals(LocalProxyPorts(7890, 1053), settings.localProxyPorts)
        assertFalse(context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE).contains("local_mixed_proxy_port"))
        assertFalse(context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE).contains("local_dns_proxy_port"))
    }

    @Test
    fun validatesRangeDigitsAndPortConflict() {
        assertEquals(LocalProxyPorts(1, 65535), LocalProxyPorts.parse("1", "65535"))
        assertNull(LocalProxyPorts.parse("0", "1053"))
        assertNull(LocalProxyPorts.parse("65536", "1053"))
        assertNull(LocalProxyPorts.parse("12x", "1053"))
        assertNull(LocalProxyPorts.parse("7890", "7890"))
        assertNull(LocalProxyPorts.parse("", "1053"))
    }

    @Test
    fun savesBothPortsTogetherAndRejectsInvalidPairWithoutOverwriting() {
        val prefs = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
        assertTrue(settings.setLocalProxyPorts(LocalProxyPorts(2080, 5353)))
        assertEquals(2080, prefs.getInt("local_mixed_proxy_port", 0))
        assertEquals(5353, prefs.getInt("local_dns_proxy_port", 0))
        assertEquals(LocalProxyPorts(2080, 5353), settings.localProxyPorts)

        assertFalse(settings.setLocalProxyPorts(LocalProxyPorts(0, 5354)))
        assertFalse(settings.setLocalProxyPorts(LocalProxyPorts(5353, 5353)))
        assertEquals(LocalProxyPorts(2080, 5353), settings.localProxyPorts)
        assertEquals(2080, prefs.getInt("local_mixed_proxy_port", 0))
        assertEquals(5353, prefs.getInt("local_dns_proxy_port", 0))
    }

    @Test
    fun runtimeHeaderUsesBothConfiguredListeners() {
        assertTrue(settings.setLocalProxyPorts(LocalProxyPorts(2080, 5353)))
        val header = ConfigManager.buildRuntimeHeader(settings, userDns = null)
        val root = org.yaml.snakeyaml.Yaml().load<Map<String, Any?>>(header)
        assertEquals(2080, (root["mixed-port"] as Number).toInt())
        val dns = root["dns"] as Map<*, *>
        assertEquals("127.0.0.1:5353", dns["listen"])
    }
}
