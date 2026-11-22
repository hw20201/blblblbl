package com.github.arca.android.data

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
    fun `extractProfileName decodes percent-encoded fragment exactly once`() {
        val url = "https://example.invalid/sub?name=Ignored#HK%2B%20100%2520"
        assertEquals("HK+ 100%20", ConfigManager.extractProfileName(null, null, "", url))
    }

    @Test
    fun `extractProfileName splits raw query without interpreting encoded separators as parameters`() {
        val url = "https://example.invalid/sub?token=ignored%26name%3Dnot-the-title&TITLE=Tokyo%26Osaka=50%25%2B"
        assertEquals("Tokyo&Osaka=50%+", ConfigManager.extractProfileName(null, null, "", url))
    }

    @Test
    fun `extractProfileName prefers extended filename regardless of case and ignores trailing parameters`() {
        assertEquals("东+京 VIP", contentDispositionName(
            "attachment; filename=fallback.yaml; FILENAME* = uTf-8'zh'%E4%B8%9C+%E4%BA%AC%20VIP.YAML; size=147"))
    }

    @Test
    fun `extractProfileName accepts quoted semicolons and falls back from malformed extended filename`() {
        assertEquals("Tokyo; VIP", contentDispositionName(
            "attachment; FILENAME=\"Tokyo; VIP.YmL\"; filename*=UTF-8''broken%ZZ; size=147"))
    }

    private fun contentDispositionName(header: String): String {
        val url = "https://example.invalid/sub"
        val request = okhttp3.Request.Builder().url(url).build()
        return fakeResponse(request, 200, "", "Content-Disposition" to header).use { response ->
            ConfigManager.extractProfileName(null, response, "", url)
        }
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
