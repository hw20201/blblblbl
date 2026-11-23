package com.github.arca.android.data

import org.junit.Assert.*
import org.junit.Test

class ConfigDnsMergeTest {
    @Test fun `runtime owns DNS transport values`() {
        val dns = ConfigManager.buildDnsSection(false, mapOf("enable" to false, "listen" to "0.0.0.0:53", "enhanced-mode" to "normal", "fake-ip-range" to "10.0.0.0/8"))
        assertEquals(true, dns["enable"]); assertEquals("127.0.0.1:1053", dns["listen"])
        assertEquals("redir-host", dns["enhanced-mode"]); assertEquals("198.18.0.1/16", dns["fake-ip-range"])
    }
    @Test fun `IPv6 routing switch does not disable proxy server endpoint resolution`() {
        val disabled = ConfigManager.runtimeIpv6Flags(ipv6RoutingEnabled = false)
        assertTrue(disabled.proxyServerResolution)
        assertFalse(disabled.clientDns)

        val enabled = ConfigManager.runtimeIpv6Flags(ipv6RoutingEnabled = true)
        assertTrue(enabled.proxyServerResolution)
        assertTrue(enabled.clientDns)

        val disabledDns = ConfigManager.buildDnsSection(false, mapOf("ipv6" to true), ipv6Enabled = disabled.clientDns)
        assertFalse(disabledDns["ipv6"] as Boolean)
    }

    @Test fun `TCP concurrent defaults on and preserves an explicit profile choice`() {
        assertNull(ConfigManager.extractUserTcpConcurrent("proxies: []\nrules: []"))
        assertEquals(true, ConfigManager.extractUserTcpConcurrent("tcp-concurrent: true\nproxies: []"))
        assertEquals(false, ConfigManager.extractUserTcpConcurrent("tcp-concurrent: false\nproxies: []"))
        assertEquals(false, ConfigManager.extractUserTcpConcurrent("TCP-CONCURRENT: false\nproxies: []"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `TCP concurrent rejects a non-boolean profile value`() {
        ConfigManager.extractUserTcpConcurrent("tcp-concurrent: fast\nproxies: []")
    }

    @Test fun `TCP concurrent is runtime-owned and cannot be overridden after injection`() {
        val sanitized = ConfigManager.sanitizeUserConfig("tcp-concurrent: false\nproxies: []")
        assertFalse(sanitized.contains("tcp-concurrent"))
    }

    @Test fun `DNS defaults separate bootstrap and DoH transports`() {
        val dns = ConfigManager.buildDnsSection(false, null)
        assertEquals(listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query"), dns["default-nameserver"])
        assertEquals(listOf("https://1.1.1.1/dns-query#DIRECT", "https://8.8.8.8/dns-query#DIRECT"), dns["proxy-server-nameserver"])
        assertEquals(listOf("https://dns.alidns.com/dns-query", "https://doh.pub/dns-query"), dns["nameserver"])
        assertEquals(listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query"), dns["fallback"])
        assertEquals(mapOf("geoip" to true, "geoip-code" to "CN"), dns["fallback-filter"])
    }

    @Test fun `profile cannot override encrypted bootstrap resolvers`() {
        val dns = ConfigManager.buildDnsSection(false, mapOf(
            "default-nameserver" to listOf("192.0.2.1"),
            "proxy-server-nameserver" to listOf("192.0.2.2")
        ))
        val encryptedBootstrap = listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query")
        assertEquals(encryptedBootstrap, dns["default-nameserver"])
        assertEquals(encryptedBootstrap.map { "$it#DIRECT" }, dns["proxy-server-nameserver"])
    }

    @Test fun `profile DNS retains encrypted resolvers and filters plaintext ones`() {
        val dns = ConfigManager.buildDnsSection(true, mapOf(
            "nameserver" to listOf("1.1.1.1", "https://dns.example/dns-query"),
            "fallback" to listOf("udp://8.8.8.8:53", "tls://dns.example:853"),
            "nameserver-policy" to mapOf(
                "+.corp" to "192.168.1.1",
                "+.secure" to listOf("https://dns.example/dns-query", "114.114.114.114")
            ),
            "fake-ip-filter" to listOf("+.corp", "*.lan")
        ))
        assertEquals(listOf("https://dns.example/dns-query"), dns["nameserver"])
        assertEquals(listOf("tls://dns.example:853"), dns["fallback"])
        assertEquals(mapOf("+.secure" to listOf("https://dns.example/dns-query")), dns["nameserver-policy"])
        val filter = dns["fake-ip-filter"] as List<*>
        assertTrue(filter.contains("connectivitycheck.gstatic.com"))
        assertTrue(filter.contains("+.corp"))
        assertEquals(filter.size, filter.toSet().size)
    }

    @Test fun `profiles cannot re-enable native system DNS append`() {
        val sanitized = ConfigManager.sanitizeUserConfig("clash-for-android:\n  append-system-dns: true\nproxies: []")
        assertFalse(sanitized.contains("append-system-dns"))
    }
}
