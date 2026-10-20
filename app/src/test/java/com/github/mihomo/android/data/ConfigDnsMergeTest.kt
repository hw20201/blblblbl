package com.github.mihomo.android.data

import org.junit.Assert.*
import org.junit.Test

class ConfigDnsMergeTest {
    @Test fun `runtime owns DNS transport values`() {
        val dns = ConfigManager.buildDnsSection(false, mapOf("enable" to false, "listen" to "0.0.0.0:53", "enhanced-mode" to "normal", "fake-ip-range" to "10.0.0.0/8"))
        assertEquals(true, dns["enable"]); assertEquals("127.0.0.1:1053", dns["listen"])
        assertEquals("redir-host", dns["enhanced-mode"]); assertEquals("198.18.0.1/16", dns["fake-ip-range"])
    }
    @Test fun `profile DNS resolvers and policies survive with filter union`() {
        val dns = ConfigManager.buildDnsSection(true, mapOf("nameserver" to listOf("1.1.1.1"), "nameserver-policy" to mapOf("+.corp" to "10.0.0.53"), "fake-ip-filter" to listOf("+.corp", "*.lan")))
        assertEquals(listOf("1.1.1.1"), dns["nameserver"]); assertEquals(mapOf("+.corp" to "10.0.0.53"), dns["nameserver-policy"])
        val filter = dns["fake-ip-filter"] as List<*>; assertTrue(filter.contains("connectivitycheck.gstatic.com")); assertEquals(filter.size, filter.toSet().size)
    }
}
