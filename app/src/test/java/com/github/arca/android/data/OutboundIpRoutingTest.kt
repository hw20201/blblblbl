package com.github.arca.android.data

import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/** Real sockets on loopback: VPN on = through the local proxy only, VPN off = direct, never both. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutboundIpRoutingTest {
    private fun repository(proxy: InetSocketAddress, dns: Dns) =
        OutboundIpRepository(null, proxy, dns, 3_000, 3_000)

    private val ipApiBody = IP_API_OK.toByteArray()
    private fun proxyServer() = TinyHttpServer { line, _ ->
        if (line.startsWith("GET http://ip-api.com/")) RawReply(200, listOf("Content-Type" to "application/json"), ipApiBody)
        else RawReply(502)
    }

    @Test fun portConstantAndDefaultClients() {
        assertEquals(7890, VPN_LOCAL_PROXY_PORT)
        val repo = OutboundIpRepository()
        assertNull(repo.lookupClient(false).proxy)
        assertEquals(InetSocketAddress("127.0.0.1", 7890), repo.lookupClient(true).proxy?.address())
        assertEquals(java.net.Proxy.Type.HTTP, repo.lookupClient(true).proxy?.type())
    }

    @Test fun vpnOnSendsExitRequestsThroughTheLocalProxy() = runBlocking {
        proxyServer().use { proxy ->
            val dns = RecordingDns(fail = false)
            val info = repository(proxy.address, dns).lookup(isVpnRunning = true)
            assertEquals("ip-api.com", info.source)
            assertEquals("203.0.113.5", info.ipAddress)
            val first = proxy.requestLines.first { it.startsWith("GET ") }
            assertTrue(first, first.startsWith("GET http://ip-api.com/json/?"))
            assertTrue(first.contains("fields=") && first.contains("lang=zh-CN"))
            assertTrue("targets must not be resolved locally: ${dns.lookups}", dns.lookups.all { it == "127.0.0.1" })
        }
    }

    @Test fun vpnOnSendsRiskRequestThroughTheLocalProxy() = runBlocking {
        proxyServer().use { proxy ->
            val dns = RecordingDns(fail = false)
            val failure = runCatching { repository(proxy.address, dns).lookupScamalyticsWeb("203.0.113.5", true) }.exceptionOrNull()
            assertTrue(failure is IpLookupException)  // fake proxy refuses CONNECT
            assertTrue(proxy.requestLines.toString(), proxy.requestLines.any { it.startsWith("CONNECT scamalytics.com:443") })
            assertTrue(dns.lookups.all { it == "127.0.0.1" })
        }
    }

    @Test fun vpnOffGoesDirectAndNeverTouchesTheProxy() {
        proxyServer().use { proxy ->
            val dns = RecordingDns(fail = true)
            runCatching { runBlocking { repository(proxy.address, dns).lookup(isVpnRunning = false) } }
            assertTrue(dns.lookups.toString(), dns.lookups.containsAll(listOf("ip-api.com", "get.geojs.io")))
            runCatching { runBlocking { repository(proxy.address, dns).lookupScamalyticsWeb("203.0.113.5", false) } }
            assertTrue(dns.lookups.contains("scamalytics.com"))
            TimeUnit.MILLISECONDS.sleep(100)
            assertTrue("proxy must see nothing: ${proxy.requestLines}", proxy.requestLines.isEmpty())
        }
    }

    @Test fun deadProxyFailsAndNeverFallsBackToDirect() {
        val dead = InetSocketAddress("127.0.0.1", closedPort())
        val dns = RecordingDns(fail = false)
        val repository = repository(dead, dns)
        val lookup = runCatching { runBlocking { repository.lookup(isVpnRunning = true) } }.exceptionOrNull()
        assertTrue(lookup is IpLookupException)
        val risk = runCatching { runBlocking { repository.lookupScamalyticsWeb("203.0.113.5", true) } }.exceptionOrNull()
        assertTrue(risk is IpLookupException)
        val api = runCatching { runBlocking { repository.lookupScamalytics("203.0.113.5", ScamalyticsCredentials("u", "k"), true) } }.exceptionOrNull()
        assertTrue(api is IpLookupException)
        val full = runCatching { runBlocking { repository.lookupWithRisk(true, CredentialLoadState.Absent) } }.exceptionOrNull()
        assertTrue(full is IpLookupException)
        assertTrue("only the proxy host may ever be resolved: ${dns.lookups}", dns.lookups.all { it == "127.0.0.1" })
    }

    @Test fun routeSnapshotIsTheArgumentNotGlobalState() = runBlocking {
        // Same repository instance, two clicks with different snapshots pick different clients.
        val repo = OutboundIpRepository()
        assertNotSame(repo.lookupClient(true), repo.lookupClient(false))
        assertSame(repo.lookupClient(true), repo.lookupClient(true))
    }
}
