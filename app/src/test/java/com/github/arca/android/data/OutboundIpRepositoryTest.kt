package com.github.arca.android.data

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

    private val ipApiJson =
        """{"status":"success","country":"美国","countryCode":"US","regionName":"伊利诺伊州","city":"芝加哥","isp":"Cloudflare, Inc.","org":"Cloudflare WARP","as":"AS13335 Cloudflare, Inc.","query":"203.0.113.5"}"""
    private val geojsJson =
        """{"ip":"198.51.100.2","country":"United States","country_code":"US","city":"Morgan Hill","organization":"AS13335 Cloudflare, Inc.","organization_name":"Cloudflare, Inc.","asn":13335,"region":"California"}"""

    @Test fun ipApiPrimaryReturnsCountryCityOrgAndAsn() = runBlocking {
        val info = repository { 200 to ipApiJson }.lookup()
        assertEquals("203.0.113.5", info.ipAddress)
        assertEquals("美国", info.country)
        assertEquals("芝加哥", info.city)
        assertEquals("Cloudflare WARP", info.organization)
        assertEquals("AS13335", info.asn)
        assertEquals("ip-api.com", info.source)
        assertNull(info.type)
    }

    @Test fun geojsBackupStripsAsPrefixFromOrganization() = runBlocking {
        val info = repository { url ->
            if (url.contains("ip-api.com")) 500 to "" else 200 to geojsJson
        }.lookup()
        assertEquals("198.51.100.2", info.ipAddress)
        assertEquals("United States", info.country)
        assertEquals("Morgan Hill", info.city)
        assertEquals("Cloudflare, Inc.", info.organization)
        assertEquals("AS13335", info.asn)
        assertEquals("geojs.io", info.source)
    }

    @Test fun slowPrimaryLosesRaceToFastBackup() = runBlocking {
        val info = repository { url ->
            if (url.contains("ip-api.com")) {
                Thread.sleep(600)
                200 to ipApiJson
            } else 200 to geojsJson
        }.lookup()
        assertEquals("geojs.io", info.source)
        assertEquals("198.51.100.2", info.ipAddress)
    }

    @Test fun failingPrimaryFallsBackToBackup() = runBlocking {
        val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
        val info = repository { url ->
            seen += url
            if (url.contains("ip-api.com")) 503 to "" else 200 to geojsJson
        }.lookup()
        assertEquals("198.51.100.2", info.ipAddress)
        assertEquals("geojs.io", info.source)
        assertEquals(2, seen.size)
    }

    @Test fun bothSourcesFail() = runBlocking {
        val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
        try {
            repository { url -> calls += url; 502 to "" }.lookup()
            fail("Expected lookup to fail")
        } catch (_: Exception) { }
        assertEquals(2, calls.size)
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
