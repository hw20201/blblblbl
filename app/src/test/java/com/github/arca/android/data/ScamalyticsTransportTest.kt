package com.github.arca.android.data

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScamalyticsTransportTest {
    private val credentials = ScamalyticsCredentials("fixture ?#&+", "fake &key=+/=?#")
    private val ip = "203.0.113.7"
    private val good = """{"scamalytics":{"status":"ok","mode":"live","ip":"203.0.113.7","scamalytics_score":7,"scamalytics_risk":"low","credits":{"remaining":42}}}"""

    @Test fun urlsEscapeCredentialsAndUseOnlyAllowlistedHosts() {
        val repo = OutboundIpRepository()
        val url = repo.scamalyticsUrl(credentials, "2001:db8::1", test = true)
        assertEquals("https", url.scheme)
        assertEquals("api11.scamalytics.com", url.host)
        assertEquals(listOf("v3", credentials.username, ""), url.pathSegments)
        assertEquals(credentials.apiKey, url.queryParameter("key"))
        assertEquals("2001:db8::1", url.queryParameter("ip"))
        assertEquals("1", url.queryParameter("test"))
        assertEquals(setOf("key", "ip", "test"), url.queryParameterNames)
        assertNull(repo.scamalyticsUrl(credentials, ip).queryParameter("test"))
        assertEquals("api12.scamalytics.com", repo.scamalyticsUrl(credentials.copy(endpoint = ScamalyticsEndpoint.EU), ip).host)
    }

    @Test fun httpAndBodyErrorsNeverExposeRequestOrProviderText() = runBlocking {
        listOf(401 to "认证", 403 to "认证", 402 to "额度", 429 to "额度", 503 to "暂时").forEach { (code, expected) ->
            val repo = repository { response(it, code, credentials.apiKey) }
            val failure = runCatching { repo.lookupScamalytics(ip, credentials) }.exceptionOrNull()!!
            assertTrue(failure.message!!.contains(expected))
            assertRedacted(failure)
        }
        listOf("Unauthorized: invalid API key", "credits exhausted").forEach { message ->
            val repo = repository { response(it, 200, """{"scamalytics":{"status":"error","error":"$message fake-key https://api11.scamalytics.com"}}""") }
            assertRedacted(runCatching { repo.lookupScamalytics(ip, credentials) }.exceptionOrNull()!!)
        }
        val repo = repository {
            throw IOException("request=${it.url} secret=${credentials.apiKey}",
                IllegalStateException("raw cause: ${it.url}")).apply {
                addSuppressed(IOException("raw suppressed: ${credentials.apiKey}"))
            }
        }
        assertRedacted(runCatching { repo.lookupScamalytics(ip, credentials) }.exceptionOrNull()!!)
    }

    @Test fun redirectsAreDisabledEvenForInjectedClients() = runBlocking {
        val calls = AtomicInteger()
        val repo = repository { request ->
            calls.incrementAndGet()
            response(request, 302, "").newBuilder().header("Location", "https://evil.example/?key=fake").build()
        }
        val failure = runCatching { repo.lookupScamalytics(ip, credentials) }.exceptionOrNull()!!
        assertEquals(1, calls.get())
        assertTrue(failure.message!!.contains("重定向"))
        assertRedacted(failure)
    }

    @Test fun riskFailureRetainsFreshIpWithoutAnOldRiskResult() = runBlocking {
        val repo = repository { request ->
            if (request.url.host == "ip-api.com") response(request, 200, """{"status":"success","query":"$ip"}""")
            else response(request, 401, "secret response")
        }
        var resolved: String? = null
        val result = repo.lookupWithRisk(true, credentials, onIpResolved = { resolved = it.ipAddress })
        assertEquals(ip, resolved)
        assertEquals(ip, result.outbound.ipAddress)
        assertNull(result.scamalytics)
        assertTrue(result.riskError!!.contains("认证"))
    }

    @Test fun bothSourcesUseSameProxyAndNeverRetryDirect() = runBlocking {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890))
        val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
        val http = OkHttpClient.Builder().proxy(proxy).addInterceptor { chain ->
            seen += chain.request().url.host
            if (chain.request().url.host == "ip-api.com") response(chain.request(), 200, """{"status":"success","query":"$ip"}""")
            else response(chain.request(), 200, good)
        }.build()
        val repo = OutboundIpRepository(http)
        val result = repo.lookupWithRisk(true, credentials)
        // The two IP sources race in parallel: the loser may be cancelled before its
        // request even starts, so only the hosts that must appear are asserted.
        assertTrue(seen.contains("ip-api.com"))
        assertTrue(seen.contains("api11.scamalytics.com"))
        assertTrue(seen.size in 2..3)
        assertEquals(proxy, repo.lookupClient(true).proxy)
        assertEquals(7, result.scamalytics?.score)
        val failedHosts = java.util.Collections.synchronizedList(mutableListOf<String>())
        val failed = OutboundIpRepository(http.newBuilder().apply {
            interceptors().clear()
            addInterceptor { chain -> failedHosts += chain.request().url.host; throw IOException("proxy offline") }
        }.build())
        assertTrue(runCatching { failed.lookupWithRisk(true, credentials) }.isFailure)
        assertEquals(setOf("ip-api.com", "get.geojs.io"), failedHosts.toSet())
        assertEquals(2, failedHosts.size)
    }

    @Test fun malformedResponseIsRedactedAndCannotMasqueradeAsSuccessfulRisk() = runBlocking {
        listOf("{broken fake-key", "{}", """{"scamalytics":{"status":"ok","ip":"evil.example"}}""",
            """{"scamalytics":{"status":"ok","ip":"203.0.113.99"}}""").forEach { body ->
            val failure = runCatching { repository { response(it, 200, body) }.lookupScamalytics(ip, credentials) }.exceptionOrNull()
            assertNotNull(failure)
            assertRedacted(failure!!)
        }
    }

    @Test fun cancellationStopsScamalyticsCall() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val canceled = AtomicBoolean()
        val http = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun canceled(call: Call) { canceled.set(true); released.countDown() }
        }).addInterceptor {
            entered.countDown()
            released.await(3, TimeUnit.SECONDS)
            throw IOException("cancelled")
        }.build()
        val pending = async(Dispatchers.Default) { OutboundIpRepository(http).lookupScamalytics(ip, credentials) }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        pending.cancelAndJoin()
        assertTrue(canceled.get())
    }

    private fun repository(block: (Request) -> Response) = OutboundIpRepository(
        OkHttpClient.Builder().addInterceptor { block(it.request()) }.build()
    )
    private fun response(request: Request, code: Int, body: String) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(body.toResponseBody()).build()
    private fun assertRedacted(error: Throwable) {
        // Coroutine stacktrace recovery may copy the sanitized exception and attach the
        // sanitized original as its cause. Inspect the entire graph instead of banning causes.
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        val pending = java.util.ArrayDeque<Throwable>().apply { add(error) }
        val encodedKey = OutboundIpRepository().scamalyticsUrl(credentials, ip)
            .encodedQuery!!.substringAfter("key=").substringBefore('&')
        val forbidden = listOf(credentials.apiKey, credentials.username, encodedKey,
            "fake-key", "https://", "http://", "raw cause:", "raw suppressed:")
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (!visited.add(node)) continue
            assertTrue("Only sanitized lookup exceptions may remain in the failure graph", node is IpLookupException)
            assertEquals("Recovered causes must retain the same sanitized message", error.message, node.message)
            forbidden.forEach { assertFalse("Failure graph must not contain request or secret data", node.toString().contains(it)) }
            node.cause?.let(pending::add)
            node.suppressed.forEach(pending::add)
        }
        val printed = java.io.StringWriter().also { error.printStackTrace(java.io.PrintWriter(it)) }.toString()
        forbidden.forEach { assertFalse("Printed failure must not contain request or secret data", printed.contains(it)) }
    }
}
