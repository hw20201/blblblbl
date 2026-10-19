package com.github.mihomo.android.data

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.yaml.snakeyaml.Yaml
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LanAuthenticationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Before fun reset() {
        context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `credentials are persistent random and rotate without changing username`() {
        val first = SettingsManager(context).lanCredentials()
        assertEquals("nexus", first.username)
        assertEquals(16, first.password.length)
        assertTrue(first.password.all { it.isLetterOrDigit() })
        assertEquals(first, SettingsManager(context).lanCredentials())
        val rotated = SettingsManager(context).regenerateLanCredentials()
        assertEquals("nexus", rotated.username)
        assertNotEquals(first.password, rotated.password)
        assertEquals(rotated, SettingsManager(context).lanCredentials())
    }

    @Test fun `concurrent initializers all observe the same persisted credentials`() {
        val pool = Executors.newFixedThreadPool(6)
        try {
            val values = (1..24).map { pool.submit<LanCredentials> { SettingsManager(context).lanCredentials() } }
                .map { it.get(5, TimeUnit.SECONDS) }.toSet()
            assertEquals(1, values.size)
            assertEquals(values.first(), SettingsManager(context).lanCredentials())
        } finally { pool.shutdownNow() }
    }

    @Test fun `LAN header authenticates remote peers while only loopbacks bypass auth`() {
        val settings = SettingsManager(context).apply { allowLan = true }
        val header = Yaml().load<Map<String, Any?>>(ConfigManager.buildRuntimeHeader(settings, null, true))
        assertEquals(true, header["allow-lan"])
        assertEquals(listOf(settings.lanCredentials().encoded), header["authentication"])
        assertEquals(listOf("127.0.0.1/32", "::1/128"), header["skip-auth-prefixes"])
        assertEquals(true, header["ipv6"])
        val dns = header["dns"] as Map<*, *>
        assertEquals(true, dns["ipv6"])
    }

    @Test fun `local only mode does not introduce authentication or skip prefixes`() {
        val settings = SettingsManager(context).apply { allowLan = false }
        val header = Yaml().load<Map<String, Any?>>(ConfigManager.buildRuntimeHeader(settings, null, false))
        assertEquals(false, header["allow-lan"])
        assertFalse(header.containsKey("authentication"))
        assertFalse(header.containsKey("skip-auth-prefixes"))
    }

    @Test fun `profile cannot replace runtime auth or IPv6 ownership`() {
        val out = ConfigManager.sanitizeUserConfig("""
            authentication: [attacker:pw]
            skip-auth-prefixes: [0.0.0.0/0]
            ipv6: true
            dns: {ipv6: true}
            proxies: []
        """.trimIndent())
        val parsed = Yaml().load<Map<String, Any?>>(out)
        assertEquals(setOf("proxies"), parsed.keys)
        val dns = ConfigManager.buildDnsSection(true, mapOf("ipv6" to true), false)
        assertEquals(false, dns["ipv6"])
    }
}
