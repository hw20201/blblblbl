package com.github.arca.android.data

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Collections

/** Which risk source is asked (never both), failure authenticity, and the 8 s risk stage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScamalyticsSourceSelectionTest {
    private val ip = "203.0.113.5"
    private val credentials = ScamalyticsCredentials("fixture-user", "fixture-key")
    private val hits = Collections.synchronizedList(mutableListOf<String>())
    private val apiOk = """{"scamalytics":{"status":"ok","mode":"live","ip":"$ip","scamalytics_score":2,"scamalytics_risk":"low"}}"""
    private val webOk = """<html><head><title>$ip IP Fraud Risk</title></head><body><h1>$ip</h1>
        <div>Fraud Score: 4</div><table><tr><td>Datacenter</td><td>Yes</td></tr><tr><td>Firehol</td><td>No</td></tr></table></body></html>"""

    /** ip-api succeeds, geojs fails; [risk] answers scamalytics.com / apiNN.scamalytics.com. */
    private fun client(risk: (Interceptor.Chain) -> Response) = fakeClient { chain ->
        val host = chain.request().url.host
        hits += host
        when (host) {
            "ip-api.com" -> fakeResponse(chain.request(), 200, IP_API_OK)
            "get.geojs.io" -> fakeResponse(chain.request(), 500, "")
            else -> risk(chain)
        }
    }

    private val webHits get() = hits.count { it == "scamalytics.com" }
    private val apiHits get() = hits.count { it.startsWith("api") && it.endsWith(".scamalytics.com") }

    @Test fun unconfiguredRequestsOnlyTheWebPage() = runBlocking {
        val result = repo(client { fakeResponse(it.request(), 200, webOk) }).lookupWithRisk()
        assertEquals(1, webHits); assertEquals(0, apiHits)
        assertEquals("web", result.scamalytics!!.mode)
        assertEquals(4, result.scamalytics!!.score)
        assertNull(result.riskError)
        assertEquals("Hosting", result.outbound.type)
    }

    @Test fun configuredRequestsOnlyTheOfficialApi() = runBlocking {
        val result = repo(client { fakeResponse(it.request(), 200, apiOk) }).lookupWithRisk(credentials = credentials)
        assertEquals(1, apiHits); assertEquals(0, webHits)
        assertEquals("live", result.scamalytics!!.mode)
        assertEquals(2, result.scamalytics!!.score)
    }

    @Test fun apiAuthQuotaAndRateLimitFailuresNeverFallBackToTheWebPage() = runBlocking {
        listOf(401 to "认证", 403 to "认证", 402 to "额度", 429 to "频繁", 500 to "暂时").forEach { (code, text) ->
            hits.clear()
            val result = repo(client { fakeResponse(it.request(), code, "secret-body $code") }).lookupWithRisk(credentials = credentials)
            assertEquals("code $code", 0, webHits)
            assertEquals(1, apiHits)
            assertNull(result.scamalytics)
            assertTrue(result.riskError!!, result.riskError!!.contains(text))
            assertFalse(result.riskError!!.contains("secret"))
            assertEquals(ip, result.outbound.ipAddress)
        }
    }

    @Test fun apiTransportErrorAndTimeoutNeverFallBackToTheWebPage() = runBlocking {
        val failed = repo(client { throw IOException("boom https://api11.scamalytics.com/?key=fixture-key") })
            .lookupWithRisk(credentials = credentials)
        assertEquals(0, webHits)
        assertNull(failed.scamalytics)
        assertFalse(failed.riskError!!.contains("fixture-key"))

        hits.clear()
        val slow = repo(client { Thread.sleep(700); fakeResponse(it.request(), 200, apiOk) }, riskTimeoutMillis = 150)
            .lookupWithRisk(credentials = credentials)
        assertEquals(0, webHits)
        assertNull("a timed-out API call must not show data", slow.scamalytics)
        assertNotNull(slow.riskError)
        assertEquals(ip, slow.outbound.ipAddress)
    }

    @Test fun webTimeoutKeepsIpAndReportsRiskErrorOnly() = runBlocking {
        val result = repo(client { Thread.sleep(700); fakeResponse(it.request(), 200, webOk) }, riskTimeoutMillis = 150).lookupWithRisk()
        assertNull(result.scamalytics)
        assertNotNull(result.riskError)
        assertEquals(ip, result.outbound.ipAddress)
        assertEquals(0, apiHits)
    }

    @Test fun unreadableCredentialsAreNotTreatedAsUnconfigured() = runBlocking {
        var resolved: String? = null
        val result = repo(client { fakeResponse(it.request(), 200, webOk) })
            .lookupWithRisk(false, CredentialLoadState.Unreadable, onIpResolved = { resolved = it.ipAddress })
        assertEquals(0, webHits); assertEquals(0, apiHits)
        assertNull(result.scamalytics)
        assertEquals(CREDENTIALS_UNREADABLE_MESSAGE, result.riskError)
        assertEquals(ip, resolved); assertEquals(ip, result.outbound.ipAddress)
    }

    @Test fun absentAndPresentStatesRouteLikeNullAndCredentials() = runBlocking {
        repo(client { fakeResponse(it.request(), 200, webOk) }).lookupWithRisk(false, CredentialLoadState.Absent)
        assertEquals(1, webHits); assertEquals(0, apiHits)
        hits.clear()
        repo(client { fakeResponse(it.request(), 200, apiOk) }).lookupWithRisk(false, CredentialLoadState.Present(credentials))
        assertEquals(0, webHits); assertEquals(1, apiHits)
    }

    @Test fun riskFailureKeepsThisIpAndCarriesNoPreviousData() = runBlocking {
        val repository = repo(client { fakeResponse(it.request(), 200, webOk) })
        val first = repository.lookupWithRisk()
        assertNotNull(first.scamalytics)
        // Same repository, now the page is a captcha: nothing from the first result may reappear.
        val failing = repo(client { fakeResponse(it.request(), 200, "<html><title>Just a moment...</title></html>") })
        val second = failing.lookupWithRisk()
        assertEquals(ip, second.outbound.ipAddress)
        assertNull(second.scamalytics)
        assertNull("type is only inferred from a successful page", second.outbound.type)
        assertTrue(second.riskError!!.contains("验证"))
    }

    @Test fun webFailuresAreFailuresNotSuccess() = runBlocking {
        val bodies = mapOf<String, Pair<Int, String>>(
            "403" to (403 to "<html>Forbidden</html>"),
            "429" to (429 to "slow down"),
            "captcha" to (200 to "<html><title>Attention Required! | Cloudflare</title><body>$ip</body></html>"),
            "empty" to (200 to "   "),
            "other ip" to (200 to webOk.replace(ip, "198.51.100.77")),
            "no data" to (200 to "<html><title>$ip</title><body>$ip nothing</body></html>"),
            "redirect" to (302 to "")
        )
        bodies.forEach { (name, reply) ->
            val result = repo(client { fakeResponse(it.request(), reply.first, reply.second) }).lookupWithRisk()
            assertNull(name, result.scamalytics)
            assertNotNull(name, result.riskError)
            assertEquals(name, ip, result.outbound.ipAddress)
            assertFalse(name, result.riskError!!.contains("slow down"))
        }
    }

    @Test fun lookupFailureThrowsAndRequestsNoRisk() {
        val failing = fakeClient { chain -> hits += chain.request().url.host; fakeResponse(chain.request(), 500, "") }
        try {
            runBlocking { repo(failing).lookupWithRisk() }
            fail("lookup failure must propagate")
        } catch (_: IpLookupException) { }
        assertEquals(setOf("ip-api.com", "get.geojs.io"), hits.toSet())
    }

    @Test fun riskStageUsesItsOwnEightSecondLimitForWebAndApi() = runBlocking {
        val seen = Collections.synchronizedMap(mutableMapOf<String, Int>())
        val http = fakeClient { chain ->
            val host = chain.request().url.host
            seen[host] = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(chain.call().timeout().timeoutNanos()).toInt()
            when (host) {
                "ip-api.com" -> fakeResponse(chain.request(), 200, IP_API_OK)
                "get.geojs.io" -> fakeResponse(chain.request(), 500, "")
                "scamalytics.com" -> fakeResponse(chain.request(), 200, webOk)
                else -> fakeResponse(chain.request(), 200, apiOk)
            }
        }
        repo(http).lookupWithRisk()
        repo(http).lookupWithRisk(credentials = credentials)
        assertEquals(8000, seen["scamalytics.com"])
        assertEquals(8000, seen["api11.scamalytics.com"])
    }

    @Test fun riskRequestsDoNotFollowRedirects() = runBlocking {
        val web = repo(client { fakeResponse(it.request(), 302, "", "Location" to "https://evil.example/") }).lookupWithRisk()
        assertTrue(web.riskError!!.contains("重定向"))
        assertEquals(1, webHits)
    }
}
