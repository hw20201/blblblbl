package com.github.mihomo.android.core

import java.net.Inet6Address
import java.net.InetSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketAddressParsingTest {
    @Test
    fun parsesIpv4HostAndPort() {
        val parsed = ClashCore.parseInetSocketAddress("192.0.2.17:443")

        assertNotNull(parsed)
        assertEquals(InetSocketAddress("192.0.2.17", 443), parsed)
    }

    @Test
    fun parsesBracketedIpv6HostAndPort() {
        val parsed = ClashCore.parseInetSocketAddress("[2001:db8::17]:443")

        assertNotNull(parsed)
        assertTrue(parsed!!.address is Inet6Address)
        assertEquals("2001:db8:0:0:0:0:0:17", parsed.address.hostAddress)
        assertEquals(443, parsed.port)
    }

    @Test
    fun parsesIpv6ScopeId() {
        val parsed = ClashCore.parseInetSocketAddress("[fe80::1%1]:5353")

        assertNotNull(parsed)
        assertTrue(parsed!!.address is Inet6Address)
        assertEquals(1, (parsed.address as Inet6Address).scopeId)
        assertEquals(5353, parsed.port)
    }

    @Test
    fun malformedEndpointsReturnNullForUidFallback() {
        val malformed = listOf(
            "",
            "192.0.2.17",
            "2001:db8::17:443",
            "[2001:db8::17]443",
            "[192.0.2.17]:443",
            "[2001:db8::17]:not-a-port",
            "[2001:db8::17]:65536",
            "[2001:db8::17]:443:extra",
        )

        malformed.forEach { address ->
            assertFalse("Expected malformed address to be rejected: $address", ClashCore.parseInetSocketAddress(address) != null)
        }
        assertNull(ClashCore.parseInetSocketAddress("[2001:db8::17]:not-a-port"))
    }
}
