package com.github.mihomo.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UrlValidationTest {
    @Test
    fun `https URLs are allowed`() {
        requireSecureRemoteUrl("https://example.com/api/v1/client/subscribe?token=xyz")
        requireSecureRemoteUrl("HTTPS://EXAMPLE.COM/config.yaml")
    }

    @Test
    fun `exact loopback http URLs are allowed for local tooling`() {
        listOf("http://127.0.0.1:8080/sub.yaml", "http://localhost:3000/profiles",
            "http://LOCALHOST/config", "http://user:pass@localhost:8080/config.yaml")
            .forEach(::requireSecureRemoteUrl)
    }

    @Test
    fun `remote http and disguised authorities are rejected`() {
        listOf("http://example.com/sub.yaml", "HTTP://EXAMPLE.COM/api/sub",
            "http://127.0.0.1.evil.example/sub.yaml", "http://localhost.evil.example/sub.yaml",
            "http://192.168.1.10:8080/sub.yaml", "http://localhost@evil.example/sub",
            "http://127.0.0.1@evil.example/sub", "http://localhost./sub",
            "http://[::1]:8080/sub").forEach { url ->
            assertFalse(isLoopbackUrl(url))
            rejected(url, "必须使用 HTTPS")
        }
    }

    @Test
    fun `unsupported protocols and malformed URLs are rejected`() {
        listOf("file:///etc/passwd", "ftp://ftp.example.com/sub.yaml",
            "content://com.android.providers.media/external/file", "javascript:alert(1)")
            .forEach { rejected(it, "不支持的地址协议") }
        listOf("https://", "http://", "https://example.com:99999/sub")
            .forEach { rejected(it, "地址格式无效") }
    }

    @Test
    fun `validation errors do not disclose subscription tokens or credentials`() {
        val error = runCatching { requireSecureRemoteUrl("http://user:secret@example.com/sub?token=private") }
            .exceptionOrNull()!!
        assertFalse(error.message.orEmpty().contains("secret"))
        assertFalse(error.message.orEmpty().contains("private"))
    }

    private fun rejected(url: String, message: String) {
        try {
            requireSecureRemoteUrl(url)
            fail("Expected URL to be rejected")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains(message))
        }
    }
}
