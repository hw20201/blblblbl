package com.github.mihomo.android.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TunSettingsIntegrationTest {
    private lateinit var context: Context
    private lateinit var settings: SettingsManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        settings = SettingsManager(context)
    }

    @Test
    fun ipv6DefaultsToFalseAndPersistsTrue() {
        assertFalse(settings.ipv6Enabled)
        assertFalse(context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
            .contains("ipv6_enabled"))

        settings.ipv6Enabled = true
        assertTrue(settings.ipv6Enabled)
        assertTrue(context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
            .getBoolean("ipv6_enabled", false))
    }

    @Test
    fun generatedConfigUsesTheSameIpv6SettingForCoreAndDns() {
        val configFile = ConfigManager.prepareConfig(context, null)
        val generated = configFile.readText()

        assertTrue(generated.contains("ipv6: false"))
        assertTrue(generated.contains("dns:\n  enable: true\n  listen: 127.0.0.1:1053\n  ipv6: false"))

        settings.ipv6Enabled = true
        val ipv6Config = ConfigManager.prepareConfig(context, null).readText()
        assertTrue(ipv6Config.contains("ipv6: true"))
        assertTrue(ipv6Config.contains("dns:\n  enable: true\n  listen: 127.0.0.1:1053\n  ipv6: true"))
    }

    @Test
    fun startupSnapshotWinsOverASettingChangedDuringStartup() {
        settings.ipv6Enabled = true
        val generated = ConfigManager.prepareConfig(context, null, ipv6Enabled = false).readText()
        val parsed = org.yaml.snakeyaml.Yaml().load<Map<String, Any>>(generated)
        assertEquals(false, parsed["ipv6"])
        assertEquals(false, (parsed["dns"] as Map<*, *>)["ipv6"])
    }

    @Test
    fun mtuDefaultsClampsBoundariesAndPersistsNormalizedValue() {
        assertEquals(TunTuning.DEFAULT_MTU, settings.tunMtu)
        assertFalse(context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
            .contains("tun_mtu"))

        settings.tunMtu = TunTuning.MIN_MTU - 1
        assertEquals(TunTuning.MIN_MTU, settings.tunMtu)
        assertEquals(TunTuning.MIN_MTU, context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
            .getInt("tun_mtu", -1))

        settings.tunMtu = TunTuning.MAX_MTU + 1
        assertEquals(TunTuning.MAX_MTU, settings.tunMtu)
        assertEquals(TunTuning.MAX_MTU, context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
            .getInt("tun_mtu", -1))

        settings.tunMtu = TunTuning.MIN_MTU
        assertEquals(TunTuning.MIN_MTU, settings.tunMtu)
        settings.tunMtu = TunTuning.MAX_MTU
        assertEquals(TunTuning.MAX_MTU, settings.tunMtu)
    }

    @Test
    fun congestionControllerDefaultsNormalizesAndPersists() {
        assertEquals(TunTuning.DEFAULT_CONGESTION_CONTROLLER, settings.mipsCongestionController)
        assertFalse(context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
            .contains("mips_congestion_controller"))

        settings.mipsCongestionController = "  BBR3  "
        assertEquals("bbr3", settings.mipsCongestionController)
        assertEquals("bbr3", context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
            .getString("mips_congestion_controller", null))

        settings.mipsCongestionController = "unsupported"
        assertEquals(TunTuning.DEFAULT_CONGESTION_CONTROLLER, settings.mipsCongestionController)
        assertEquals(TunTuning.DEFAULT_CONGESTION_CONTROLLER,
            context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
                .getString("mips_congestion_controller", null))
    }
}
