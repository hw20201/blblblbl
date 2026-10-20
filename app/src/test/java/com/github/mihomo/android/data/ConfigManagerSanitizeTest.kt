package com.github.mihomo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the protected top-level key filter. A subscription must not be able to
 * reintroduce runtime-owned keys, including through quoted keys that defeat text matching.
 */
class ConfigManagerSanitizeTest {

    private fun sanitized(raw: String) = ConfigManager.sanitizeUserConfig(raw)

    @Test
    fun `removes plain protected keys`() {
        val out = sanitized(
            """
            external-controller: 0.0.0.0:9090
            secret: "hunter2"
            allow-lan: true
            dns:
              enable: true
            proxies: []
            """.trimIndent()
        )
        assertFalse(out.contains("external-controller"))
        assertFalse(out.contains("secret"))
        assertFalse(out.contains("allow-lan"))
        assertTrue(out.contains("proxies"))
    }

    @Test
    fun `removes quoted protected keys`() {
        val out = sanitized(
            """
            "external-controller": 0.0.0.0:9090
            'allow-lan': true
            proxies: []
            """.trimIndent()
        )
        assertFalse(out.contains("external-controller"))
        assertFalse(out.contains("allow-lan"))
        assertTrue(out.contains("proxies"))
    }

    @Test
    fun `keeps user owned keys intact`() {
        val out = sanitized(
            """
            proxies:
              - name: node-a
                type: ss
            proxy-groups:
              - name: PROXY
                type: select
            rules:
              - MATCH,DIRECT
            """.trimIndent()
        )
        assertTrue(out.contains("node-a"))
        assertTrue(out.contains("proxy-groups"))
        assertTrue(out.contains("MATCH,DIRECT"))
    }

    @Test
    fun `protected keys do not leak through duplicate declarations`() {
        val out = sanitized(
            """
            proxies: []
            external-controller: 127.0.0.1:9090
            mode: global
            """.trimIndent()
        )
        assertFalse(out.contains("external-controller"))
        assertFalse(out.contains("mode"))
        assertEquals(false, out.contains("global"))
    }

    @Test
    fun `invalid yaml is rejected instead of passed through a textual fallback`() {
        assertThrows(IllegalArgumentException::class.java) {
            sanitized("external-controller: x\n  : : :\nproxies: []")
        }
    }
    @Test
    fun `sequence root is rejected even when its items include protected fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            sanitized("- authentication:\n  - injected:pw\n- skip-auth-prefixes:\n  - 0.0.0.0/0\n- proxies: []")
        }
    }

    @Test
    fun `structural filtering removes protected LAN authentication fields`() {
        val out = sanitized("authentication: [attacker:secret]\nskip-auth-prefixes:\n  - 0.0.0.0/0\nlan-allowed-ips: [0.0.0.0/0]\nproxies: []")
        assertFalse(out.contains("attacker"))
        assertFalse(out.contains("skip-auth-prefixes"))
        assertFalse(out.contains("lan-allowed-ips"))
    }

    @Test
    fun `scalar and malformed sequence inputs cannot become configuration tails`() {
        listOf("allow-lan: true\ninvalid: [", "[{authentication: [attacker:pw]}]", "plain text", "").forEach { input ->
            assertThrows(IllegalArgumentException::class.java) { sanitized(input) }
        }
    }

    @Test
    fun `protected only mapping leaves no stray empty flow map after the header`() {
        assertEquals("", sanitized("allow-lan: true\nauthentication: [attacker:pw]"))
    }

}
