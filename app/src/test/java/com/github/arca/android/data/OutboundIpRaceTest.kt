package com.github.arca.android.data

import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Interceptor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Dual-source exit IP race: first valid success wins, the loser (and its OkHttp call) is cancelled. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutboundIpRaceTest {
    private val calls = Collections.synchronizedMap(mutableMapOf<String, Call>())
    private val hits = Collections.synchronizedList(mutableListOf<String>())
    private val gate = CountDownLatch(1)   // released at the end of a test so parked threads exit

    private fun Interceptor.Chain.isIpApi() = request().url.host == "ip-api.com"
    private fun Interceptor.Chain.park(timeoutMs: Long = 5000) { gate.await(timeoutMs, TimeUnit.MILLISECONDS) }
    private fun Interceptor.Chain.note() { hits += request().url.host; calls[request().url.host] = call() }

    private fun exec(block: suspend () -> OutboundIpInfo): Result<OutboundIpInfo> =
        try { Result.success(runBlocking { block() }) } catch (e: Exception) { Result.failure(e) } finally { gate.countDown() }

    @Test fun primaryWinsAndSlowBackupCallIsCancelled() {
        val backupEntered = CountDownLatch(1)
        val client = fakeClient { chain ->
            chain.note()
            if (chain.isIpApi()) {
                backupEntered.await(2, TimeUnit.SECONDS)
                fakeResponse(chain.request(), 200, IP_API_OK)
            } else { backupEntered.countDown(); chain.park(); throw IOException("cancelled") }
        }
        val info = exec { repo(client).lookup() }.getOrThrow()
        assertEquals("ip-api.com", info.source)
        assertEquals("203.0.113.5", info.ipAddress)
        assertTrue("slow backup must be cancelled", calls.getValue("get.geojs.io").isCanceled())
        assertFalse(calls.getValue("ip-api.com").isCanceled())
    }

    @Test fun backupWinsAndSlowPrimaryCallIsCancelled() {
        val primaryEntered = CountDownLatch(1)
        val client = fakeClient { chain ->
            chain.note()
            if (chain.isIpApi()) { primaryEntered.countDown(); chain.park(); throw IOException("cancelled") }
            primaryEntered.await(2, TimeUnit.SECONDS)
            fakeResponse(chain.request(), 200, GEOJS_OK)
        }
        val info = exec { repo(client).lookup() }.getOrThrow()
        assertEquals("geojs.io", info.source)
        assertEquals("198.51.100.2", info.ipAddress)
        assertTrue("slow primary must be cancelled", calls.getValue("ip-api.com").isCanceled())
    }

    @Test fun primaryFailingFirstDoesNotStopBackup() {
        val primaryFailed = CountDownLatch(1)
        val client = fakeClient { chain ->
            chain.note()
            if (chain.isIpApi()) { primaryFailed.countDown(); fakeResponse(chain.request(), 503, "") }
            else { primaryFailed.await(2, TimeUnit.SECONDS); Thread.sleep(150); fakeResponse(chain.request(), 200, GEOJS_OK) }
        }
        val info = exec { repo(client).lookup() }.getOrThrow()
        assertEquals("geojs.io", info.source)
        assertEquals(setOf("ip-api.com", "get.geojs.io"), hits.toSet())
    }

    @Test fun backupFailingFirstDoesNotStopPrimary() {
        val backupFailed = CountDownLatch(1)
        val client = fakeClient { chain ->
            chain.note()
            if (chain.isIpApi()) { backupFailed.await(2, TimeUnit.SECONDS); Thread.sleep(150); fakeResponse(chain.request(), 200, IP_API_OK) }
            else { backupFailed.countDown(); throw IOException("boom") }
        }
        assertEquals("ip-api.com", exec { repo(client).lookup() }.getOrThrow().source)
    }

    @Test fun invalidIpFromOneSourceCountsAsFailureAndOtherStillWins() {
        val client = fakeClient { chain ->
            if (chain.isIpApi()) fakeResponse(chain.request(), 200, """{"status":"success","query":"evil.example"}""")
            else fakeResponse(chain.request(), 200, GEOJS_OK)
        }
        val info = exec { repo(client).lookup() }.getOrThrow()
        assertEquals("geojs.io", info.source)
    }

    @Test fun bothSourcesFailGivesFixedErrorAndNeverReusesOldIp() {
        val client = fakeClient { chain ->
            chain.note()
            if (chain.isIpApi()) fakeResponse(chain.request(), 500, "secret-body-ip-api")
            else throw IOException("secret cause https://get.geojs.io/x")
        }
        val failure = exec { repo(client).lookup() }.exceptionOrNull()
        assertTrue(failure is IpLookupException)
        val message = safeIpLookupError(failure!!)
        assertFalse(message.contains("secret"))
        assertFalse(message.contains("http"))
        assertEquals(2, hits.size)
        // A later successful lookup is independent of the failed one.
        assertEquals("203.0.113.5", exec { repo(fakeClient { fakeResponse(it.request(), if (it.isIpApi()) 200 else 500, IP_API_OK) }).lookup() }.getOrThrow().ipAddress)
    }

    @Test fun rateLimitGivesFrequentRequestMessageAndNoRetry() {
        val client = fakeClient { chain ->
            chain.note()
            if (chain.isIpApi()) fakeResponse(chain.request(), 429, "", "X-Rl" to "0", "X-Ttl" to "30")
            else fakeResponse(chain.request(), 500, "")
        }
        val failure = exec { repo(client).lookup() }.exceptionOrNull()
        assertTrue(failure!!.message!!.contains("请求过于频繁"))
        assertEquals("one request per source, no automatic retry", 1, hits.count { it == "ip-api.com" })
        assertEquals(1, hits.count { it == "get.geojs.io" })
    }

    @Test fun rateLimitHeaderOnSuccessfulResponseIsIgnored() {
        val client = fakeClient { chain ->
            if (chain.isIpApi()) fakeResponse(chain.request(), 200, IP_API_OK, "X-Rl" to "0", "X-Ttl" to "55")
            else fakeResponse(chain.request(), 500, "")
        }
        assertEquals("ip-api.com", exec { repo(client).lookup() }.getOrThrow().source)
    }

    @Test fun overallTimeoutCancelsBothCallsWithFixedMessage() {
        val entered = CountDownLatch(2)
        val client = fakeClient { chain ->
            chain.note(); entered.countDown(); chain.park(); throw IOException("cancelled")
        }
        val started = System.nanoTime()
        val failure = exec { repo(client, exitTimeoutMillis = 300).lookup() }.exceptionOrNull()
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        assertTrue(failure is IpLookupException)
        assertTrue(failure!!.message!!.contains("超时"))
        assertTrue("took $elapsedMs ms", elapsedMs < 3000)
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        assertEquals(setOf("ip-api.com", "get.geojs.io"), calls.keys)
        calls.values.forEach { assertTrue(it.isCanceled()) }
    }

    @Test fun defaultStageTimeoutIsEightSeconds() {
        assertEquals(8_000L, EXIT_STAGE_TIMEOUT_MILLIS)
        assertEquals(8_000L, RISK_STAGE_TIMEOUT_MILLIS)
    }

    @Test fun ipApiRequestAsksOnlyForNeededFieldsAndKeepsChineseLanguage() {
        var url: okhttp3.HttpUrl? = null
        val client = fakeClient { chain ->
            if (chain.isIpApi()) { url = chain.request().url; fakeResponse(chain.request(), 200, IP_API_OK) }
            else { Thread.sleep(100); fakeResponse(chain.request(), 500, "") }
        }
        exec { repo(client).lookup() }.getOrThrow()
        assertEquals("http", url!!.scheme)
        assertEquals("zh-CN", url!!.queryParameter("lang"))
        assertEquals(setOf("status", "query", "country", "city", "isp", "org", "as"), url!!.queryParameter("fields")!!.split(',').toSet())
    }

    @Test fun geojsUnknownPlaceholdersBecomeNullNotText() {
        val body = """{"ip":"198.51.100.2","country":"Unknown","city":"nil","organization":"nil","organization_name":"Unknown","asn":"Unknown"}"""
        val client = fakeClient { chain ->
            if (chain.isIpApi()) fakeResponse(chain.request(), 500, "") else fakeResponse(chain.request(), 200, body)
        }
        val info = exec { repo(client).lookup() }.getOrThrow()
        assertEquals("198.51.100.2", info.ipAddress)
        assertNull(info.country); assertNull(info.city); assertNull(info.organization); assertNull(info.asn)
    }

    @Test fun geojsZeroAsnWithUnknownOrganizationIsUnknown() {
        val body = """{"ip":"198.51.100.2","organization":"AS0 Unknown","asn":0}"""
        val client = fakeClient { chain ->
            if (chain.isIpApi()) fakeResponse(chain.request(), 500, "") else fakeResponse(chain.request(), 200, body)
        }
        val info = exec { repo(client).lookup() }.getOrThrow()
        assertNull(info.asn); assertNull(info.organization)
    }

    @Test fun infoComesOnlyFromTheWinningResponse() {
        val primaryDone = CountDownLatch(1)
        val primaryBody = """{"status":"success","query":"203.0.113.5","country":"美国","city":"","org":"","isp":"","as":""}"""
        val client = fakeClient { chain ->
            if (chain.isIpApi()) { primaryDone.countDown(); fakeResponse(chain.request(), 200, primaryBody) }
            else { primaryDone.await(2, TimeUnit.SECONDS); chain.park(2000); throw IOException("cancelled") }
        }
        val info = exec { repo(client).lookup() }.getOrThrow()
        assertEquals("ip-api.com", info.source)
        assertEquals("美国", info.country)
        assertNull("no field borrowed from the other source", info.city)
        assertNull(info.organization); assertNull(info.asn)
    }

    @Test fun ipLiteralValidationNeverResolvesNames() {
        listOf("evil.example", "1.2.3.4.example", "999.1.1.1", "1.2.3", "", "  ", "localhost", "1.2.3.4/8", "fe80::zz")
            .forEach { assertNull("'$it'", it.validIp()) }
        listOf("203.0.113.5", "2001:db8::1", " 8.8.8.8 ").forEach { assertNotNull("'$it'", it.validIp()) }
    }
}
