package com.github.mihomo.android.data

import org.junit.Assert.fail
import org.junit.Test

class UrlValidationTest {

    @Test
    fun `https URLs are allowed`() {
        requireSecureRemoteUrl("https://example.com/api/v1/client/subscribe?token=xyz")
        requireSecureRemoteUrl("HTTPS://EXAMPLE.COM/config.yaml")
    }

    @Test
    fun `loopback http URLs are allowed for local tooling`() {
        requireSecureRemoteUrl("http://127.0.0.1:8080/sub.yaml")
        requireSecureRemoteUrl("http://localhost:3000/profiles")
        requireSecureRemoteUrl("http://localhost/config")
    }

    @Test
    fun `http URLs are allowed`() {
        requireSecureRemoteUrl("http://example.com/sub.yaml")
        requireSecureRemoteUrl("HTTP://EXAMPLE.COM/api/sub")
    }

    @Test
    fun `unsupported protocols are rejected`() {
        val dangerousUrls = listOf(
            "file:///etc/passwd",
            "ftp://ftp.example.com/sub.yaml",
            "content://com.android.providers.media/external/file",
            "javascript:alert(1)"
        )
        for (url in dangerousUrls) {
            try {
                requireSecureRemoteUrl(url)
                fail("Expected exception for unsupported protocol: $url")
            } catch (e: IllegalStateException) {
                assert(e.message?.contains("不支持的地址协议") == true)
            }
        }
    }
}
