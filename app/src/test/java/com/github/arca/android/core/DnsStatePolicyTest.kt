package com.github.arca.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsStatePolicyTest {
    @Test
    fun normalizesLiteralIpv4AndDeduplicatesWithoutChangingResolverOrder() {
        val update = DnsStatePolicy().update(
            "wifi-1", listOf(" 008.008.008.008 ", "1.1.1.1", "8.8.8.8", "1.1.1.1")
        )!!
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), update.dnsServers)
        assertEquals("8.8.8.8,1.1.1.1", update.orderedDnsKey)
    }

    @Test
    fun ipv6NamesAndMalformedIpv4NeverBecomeResolverInput() {
        assertEquals(
            listOf("9.9.9.9"),
            DnsStatePolicy.normalizeIpv4Dns(
                listOf("::1", "2001:4860:4860::8888", "dns.example", "256.1.2.3", "+1.2.3.4",
                    "1.2.3", "1..2.3", "", "9.9.9.9")
            )
        )
    }

    @Test
    fun unchangedOrderedDnsOnSameNetworkDoesNotRefresh() {
        val policy = DnsStatePolicy()
        policy.update("net-1", listOf("8.8.8.8", "1.1.1.1"))
        assertNull(policy.update("net-1", listOf("8.8.8.8", "8.8.8.8", "1.1.1.1")))
        assertEquals(
            "1.1.1.1,8.8.8.8",
            policy.update("net-1", listOf("1.1.1.1", "8.8.8.8"))!!.orderedDnsKey
        )
    }

    @Test
    fun networkSwitchWithSameDnsRefreshesOnce() {
        val policy = DnsStatePolicy()
        val servers = listOf("8.8.8.8")
        policy.update("net-1", servers)
        assertEquals("net-2", policy.update("net-2", servers)!!.networkIdentity)
        assertNull(policy.update("net-2", servers))
    }

    @Test
    fun emptyDnsNewNetworkAndLossRetainTheLastAcceptedState() {
        val policy = DnsStatePolicy()
        val accepted = policy.update("net-1", listOf("8.8.8.8"))
        assertNull(policy.update("net-1", emptyList()))
        assertNull(policy.update("net-2", listOf("2001:4860:4860::8888")))
        assertNull(policy.update(null, emptyList()))
        assertNull(policy.update(null, listOf("172.19.0.2")))
        assertEquals(accepted, policy.lastAccepted)
        // Accept the replacement only once usable physical DNS arrives.
        assertEquals("net-2", policy.update("net-2", listOf("8.8.8.8"))!!.networkIdentity)
    }

    @Test
    fun noNetworkNeverTrustsAnUnattributedDnsList() {
        val update = DnsStatePolicy().update(null, listOf("172.19.0.2"))!!
        assertEquals(DnsStatePolicy.FALLBACK_IPV4_DNS, update.dnsServers)
        assertNull(update.networkIdentity)
    }

    @Test
    fun initialEmptyOrIpv6OnlyDnsUsesTheExistingIpv4FallbackOnce() {
        for (servers in listOf(emptyList(), listOf("2001:4860:4860::8888"))) {
            val policy = DnsStatePolicy()
            assertEquals(DnsStatePolicy.FALLBACK_IPV4_DNS, policy.update("net-1", servers)!!.dnsServers)
            assertNull(policy.update("net-1", servers))
            assertNull(policy.update(null, emptyList()))
            assertEquals("9.9.9.9", policy.update("net-1", listOf("9.9.9.9"))!!.orderedDnsKey)
        }
    }

    @Test
    fun resetAllowsANewNativeCoreToReceiveTheSameState() {
        val policy = DnsStatePolicy()
        val first = policy.update("net-1", listOf("1.1.1.1"))
        policy.reset()
        assertEquals(first, policy.update("net-1", listOf("1.1.1.1")))
    }
}
