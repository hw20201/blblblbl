package com.github.mihomo.android.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutboundIpRepositoryTest {
    private fun repository(responses: (String) -> Pair<Int, String>) = OutboundIpRepository(
        client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val (code, body) = responses(request.url.toString())
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code)
                .message(if (code in 200..299) "OK" else "Failure").body(body.toResponseBody()).build()
        }.build()
    )

    @Test fun identMeDeclaredResidentialAndBusinessAreLocallyRated() = runBlocking {
        val residential = repository { url ->
            assertEquals("https://ident.me/json", url)
            200 to """{"ip":"203.0.113.5","type":"isp","aso":"Example ISP","asn":64500}"""
        }.lookup()
        assertEquals("Residential", residential.type)
        assertEquals("优良", residential.level)
        assertEquals("Example ISP", residential.organization)
        assertEquals("AS64500", residential.asn)
        assertNull(residential.flags)
        val business = repository { 200 to """{"ip":"2001:db8::1","type":"government"}""" }.lookup()
        assertEquals("Business", business.type)
        assertEquals("优良", business.level)
        assertEquals("2001:db8::1", business.ipAddress)
    }

    @Test fun unknownIdentTypeAndMalformedIpFallBackOnceToIpquery() = runBlocking {
        val seen = mutableListOf<String>()
        val info = repository { url ->
            seen += url
            if (url == "https://ident.me/json") 200 to """{"ip":"not-an-ip","type":"residential"}"""
            else 200 to """{"ip":"198.51.100.2","isp":{"org":"Sample"},"risk":{"is_proxy":false,"is_vpn":false,"is_tor":false,"is_abuser":false}}"""
        }.lookup()
        assertEquals(listOf("https://ident.me/json", "https://api.ipquery.io/?format=json"), seen)
        assertEquals("198.51.100.2", info.ipAddress)
        assertNull(info.type)
        assertNull(info.level)
        assertEquals(emptyList<String>(), info.flags)
        assertEquals("ipquery.io", info.source)
    }

    @Test fun ipqueryOnlyRatesExplicitTypeAndLeavesUnspecifiedRiskUnknown() = runBlocking {
        val info = repository { url ->
            if (url.contains("ident.me")) 503 to ""
            else 200 to """{"ip":"2001:db8::2","isp":{"isp":"ISP"},"risk":{"is_datacenter":true,"is_mobile":false,"is_tor":true}}"""
        }.lookup()
        assertEquals("Hosting", info.type)
        assertEquals("风险", info.level)
        assertEquals(listOf("Tor"), info.flags)
    }

    @Test fun bothSourcesFailAndMalformedFallbackFails() = runBlocking {
        val calls = mutableListOf<String>()
        try {
            repository { url -> calls += url; if (url.contains("ident.me")) 500 to "" else 200 to """{"ip":"example.com"}""" }.lookup()
            fail("Expected lookup to fail")
        } catch (_: Exception) { }
        assertEquals(2, calls.size)
        try {
            repository { 502 to "" }.lookup()
            fail("Expected both source failures")
        } catch (_: Exception) { }
    }

    @Test fun cancellationCancelsUnderlyingCall() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val canceled = AtomicBoolean(false)
        val http = OkHttpClient.Builder().eventListener(object : okhttp3.EventListener() {
            override fun canceled(call: okhttp3.Call) { canceled.set(true); release.countDown() }
        }).addInterceptor { _ ->
            entered.countDown()
            release.await(3, TimeUnit.SECONDS)
            throw IOException("canceled")
        }.build()
        val pending = async(kotlinx.coroutines.Dispatchers.Default) { OutboundIpRepository(client = http).lookup() }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        pending.cancel()
        try { pending.await() } catch (_: CancellationException) { }
        assertTrue(canceled.get())
    }

    @Test fun identHostingIsNormalAndUnknownFlagsStayUnknown() = runBlocking {
        val info = repository { 200 to """{"ip":"203.0.113.10","type":"hosting","aso":"Example DC","asn":"AS64501"}""" }.lookup()
        assertEquals("普通", info.level)
        assertEquals("Hosting", info.type)
        assertEquals("ident.me", info.source)
        assertEquals("AS64501", info.asn)
        assertNull(info.flags)
    }

    @Test fun partialFalseRiskDoesNotClaimNoHits() = runBlocking {
        val info = repository { url ->
            if (url.contains("ident.me")) 503 to ""
            else 200 to """{"ip":"203.0.113.11","risk":{"is_proxy":false}}"""
        }.lookup()
        assertNull(info.flags)
        assertNull(info.level)
    }

    @Test fun defaultClientsUseExplicitVpnProxyAndBoundedRequests() {
        val repository = OutboundIpRepository()
        val direct = repository.lookupClient(false)
        val vpn = repository.lookupClient(true)
        assertNull(direct.proxy)
        assertEquals(java.net.Proxy.Type.HTTP, vpn.proxy?.type())
        assertEquals(java.net.InetSocketAddress("127.0.0.1", 7890), vpn.proxy?.address())
        assertFalse(vpn.retryOnConnectionFailure)
        assertEquals(8000, vpn.callTimeoutMillis)
        assertEquals(8000, direct.callTimeoutMillis)
    }
}
