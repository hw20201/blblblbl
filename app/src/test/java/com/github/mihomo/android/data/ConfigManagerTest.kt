package com.github.mihomo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigManagerTest {

    @Test
    fun `parseSubscriptionUserInfo extracts valid fields`() {
        val header = "upload=1048576; download=20971520; total=107374182400; expire=1735689600"
        val info = ConfigManager.parseSubscriptionUserInfo(header)

        assertNotNull(info)
        assertEquals(1048576L, info!!.upload)
        assertEquals(20971520L, info.download)
        assertEquals(107374182400L, info.total)
        assertEquals(1735689600L, info.expire)
    }

    @Test
    fun `parseSubscriptionUserInfo handles empty or invalid values`() {
        assertNull(ConfigManager.parseSubscriptionUserInfo(null))
        assertNull(ConfigManager.parseSubscriptionUserInfo(""))
        assertNull(ConfigManager.parseSubscriptionUserInfo("   "))
        assertNull(ConfigManager.parseSubscriptionUserInfo("invalid-header-without-equal-sign"))
        assertNull(ConfigManager.parseSubscriptionUserInfo("foo=bar; baz=qux"))
    }

    @Test
    fun `extractProfileName respects explicit name argument`() {
        val name = ConfigManager.extractProfileName(
            name = "My Custom Profile",
            response = null,
            body = "# name: IgnoredComment",
            url = "https://example.com/sub#IgnoredFragment"
        )
        assertEquals("My Custom Profile", name)
    }

    @Test
    fun `extractProfileName extracts from URL fragment`() {
        val name = ConfigManager.extractProfileName(
            name = null,
            response = null,
            body = "",
            url = "https://example.com/api/v1/client/subscribe?token=abc#HongKong%20VIP"
        )
        assertEquals("HongKong VIP", name)
    }

    @Test
    fun `extractProfileName extracts from URL query param`() {
        val name = ConfigManager.extractProfileName(
            name = null,
            response = null,
            body = "",
            url = "https://example.com/api/v1/client/subscribe?token=abc&name=Tokyo%20Node"
        )
        assertEquals("Tokyo Node", name)
    }

    @Test
    fun `extractProfileName extracts from yaml top comment`() {
        val body = """
            # title: Premium Service 2026
            # author: admin
            proxies: []
        """.trimIndent()
        val name = ConfigManager.extractProfileName(
            name = null,
            response = null,
            body = body,
            url = "https://example.com/sub"
        )
        assertEquals("Premium Service 2026", name)
    }

    @Test
    fun `extractProfileName falls back to hostname when no metadata exists`() {
        val name = ConfigManager.extractProfileName(
            name = null,
            response = null,
            body = "proxies: []",
            url = "https://sub.fastproxy.io:8443/download"
        )
        assertEquals("sub.fastproxy.io", name)
    }
}
