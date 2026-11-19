package com.github.arca.android.data

import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.yaml.snakeyaml.Yaml

class NowhereLinkTest {
    private val key = "0123456789abcdef0123456789abcdef"
    private val pin = "89abcdef01234567".repeat(4)
    private val url = "nowhere://$key@edge.example:443"

    @Test
    fun `minimal link maps to native fields and a usable rule profile`() {
        val link = requireNotNull(NowhereLink.parse(url))
        assertEquals("edge.example:443", link.endpoint)
        val config = load(link.toProfileYaml())
        assertEquals(listOf(mapOf(
            "name" to "edge.example", "type" to "nowhere", "server" to "edge.example",
            "port" to 443, "password" to key, "network" to "tcp", "morph" to true,
            "sni" to "", "pin" to "", "udp" to true
        )), config["proxies"])
        assertEquals(listOf(mapOf("name" to "PROXY", "type" to "select", "proxies" to listOf("edge.example"))),
            config["proxy-groups"])
        assertEquals(listOf("MATCH,PROXY"), config["rules"])
    }

    @Test
    fun `mirrors first query values and decodes UTF8 escapes only once`() {
        val link = requireNotNull(NowhereLink.parse(
            "NOWHERE://%30${key.drop(1)}@[::1]:65535?%6eet=%75dp&net=%ZZ&morph=0&" +
                "sni=cert.example&pin=$pin&ignored=%ZZ#%E8%8A%82%E7%82%B9+%253A"
        ))
        assertEquals("[::1]:65535", link.endpoint)
        assertEquals("\u8282\u70b9+%3A", link.name)
        val config = load(link.toProfileYaml())
        assertEquals(listOf(mapOf(
            "name" to link.name, "type" to "nowhere", "server" to "::1", "port" to 65535,
            "password" to key, "network" to "udp", "morph" to false, "sni" to "cert.example",
            "pin" to pin, "udp" to true
        )), config["proxies"])
        assertEquals(load(requireNotNull(NowhereLink.parse(url)).toProfileYaml()),
            load(requireNotNull(NowhereLink.parse("$url?sni=none&pin=none")).toProfileYaml()))
    }

    @Test
    fun `malformed links do not produce partial native settings`() {
        listOf(
            "nowhere://edge.example:443",
            "nowhere://${key.uppercase()}@edge.example:443",
            "nowhere://%2530${key.drop(1)}@edge.example:443",
            "nowhere://$key:pw@edge.example:443",
            "nowhere://$key@edge.example",
            "nowhere://$key@edge.example:0",
            "nowhere://$key@edge.example:65536",
            "nowhere://$key@edge.example:+443",
            "nowhere://$key@::1:443",
            "nowhere://$key@[fe80::1%25eth0]:443",
            "$url/",
            "$url?net=quic",
            "$url?net=udp+",
            "$url?net=%ZZ",
            "$url?net=%FF",
            "$url?morph=true&morph=1",
            "$url?sni=invalid_name",
            "$url?pin=${pin.uppercase()}",
            "$url?pin=bad",
            "$url#%ZZ"
        ).forEach { assertNull(it, NowhereLink.parse(it)) }
    }

    @Test
    fun `converts plain and standard base64 subscriptions and skips invalid nodes`() {
        val body = "# title: NW3\r\n$url#Edge\r\n$url?net=bad\r\n$url#Edge\r\n"
        val normalized = NowhereLink.normalizeProfile(body)
        val padded = Base64.getMimeEncoder().encodeToString(body.toByteArray())
        val unpadded = Base64.getEncoder().withoutPadding().encodeToString(body.toByteArray())
        assertEquals(normalized, NowhereLink.normalizeProfile(padded))
        assertEquals(normalized, NowhereLink.normalizeProfile(unpadded))
        val file = File.createTempFile("nowhere", ".yaml")
        try {
            file.writeText(normalized)
            val parsed = ProfileParser.parse(file)
            assertEquals(2, parsed.totalNodes)
            val group = parsed.groups.getValue("PROXY")
            assertEquals(listOf("Edge", "Edge-01"), group.proxies.map { it.name })
            assertEquals(listOf("nowhere", "nowhere"), group.proxies.map { it.type })
        } finally {
            file.delete()
            ProfileParser.clearCache()
        }
    }

    @Test
    fun `invalid link lists fail while native YAML passes through verbatim`() {
        assertThrows(IllegalArgumentException::class.java) { NowhereLink.normalizeProfile("\r\n") }
        assertThrows(IllegalArgumentException::class.java) { NowhereLink.normalizeProfile("$url?net=nope") }
        val nativeYaml = """
            proxies:
              - name: native
                type: nowhere
                server: edge.example
                port: 443
                password: '$key'
                tcp-prelude: true
                handshake-timeout: 3000
              - name: existing
                type: socks5
                server: socks.example
                port: 1080
        """.trimIndent()
        assertEquals(nativeYaml, NowhereLink.normalizeProfile(nativeYaml))
        assertEquals(load(nativeYaml), load(ConfigManager.sanitizeUserConfig(nativeYaml)))
        assertEquals("ss://other", NowhereLink.normalizeProfile("ss://other"))
        assertFalse(NowhereLink.hasScheme("https://sub.example/nowhere://"))
    }

    private fun load(text: String): Map<*, *> = Yaml().load(text)
}
