package com.github.mihomo.android.data

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

    @Test fun `profile DNS resolvers and policies survive with filter union`() {
        val dns = ConfigManager.buildDnsSection(true, mapOf("nameserver" to listOf("1.1.1.1"), "nameserver-policy" to mapOf("+.corp" to "10.0.0.53"), "fake-ip-filter" to listOf("+.corp", "*.lan")))
        assertEquals(listOf("1.1.1.1"), dns["nameserver"]); assertEquals(mapOf("+.corp" to "10.0.0.53"), dns["nameserver-policy"])
        val filter = dns["fake-ip-filter"] as List<*>; assertTrue(filter.contains("connectivitycheck.gstatic.com")); assertEquals(filter.size, filter.toSet().size)
    }
}
