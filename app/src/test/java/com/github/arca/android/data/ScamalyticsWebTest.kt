package com.github.arca.android.data

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

/** Fixtures mirror the public scamalytics.com/ip/<ip> page observed 2026-11-16. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScamalyticsWebTest {
    private fun webPage(
        ip: String,
        score: Int,
        risk: String,
        datacenter: String = "Yes",
        firehol: String = "No",
        vpn: String = "No",
        tor: String = "No",
        server: String = "Yes",
        isp: String = "Google LLC",
        org: String = "Level 3"
    ): String = """
        <html><head><title>$ip ( $isp ) Fraud Risk</title></head><body>
        <h1>$ip ( $isp ) Fraud Risk</h1>
        <div>${risk.replaceFirstChar { it.uppercase() }} Risk</div>
        <div>Fraud Score: $score</div>
        <p>IP address <b>$ip</b> is operated by <b>$org</b> whose <b>web traffic</b> we consider to present a potentially <b>$risk</b> fraud risk.</p>
        <pre>{
        "ip":"$ip",
        "score":"$score",
        "risk":"$risk",
        "is_blacklisted_external": false
        }</pre>
        <table>
        <tr><td>Hostname</td><td>dns.google</td></tr>
        <tr><td>ASN</td><td>15169</td></tr>
        <tr><td>ISP Name</td><td>$isp</td></tr>
        <tr><td>Organization Name</td><td>$org</td></tr>
        <tr><td>Connection type</td><td>n/a</td></tr>
        <tr><td>Country Name</td><td>United States</td></tr>
        <tr><td>Country Code</td><td>US</td></tr>
        <tr><td>City</td><td>Mountain View</td></tr>
        <tr><td>Postal Code</td><td>94043</td></tr>
        </table>
        <table>
        <tr><td>Datacenter</td><td>$datacenter</td></tr>
        <tr><td>Firehol</td><td>$firehol</td></tr>
        <tr><td>IP2ProxyLite</td><td>No</td></tr>
        <tr><td>IPsum</td><td>No</td></tr>
        <tr><td>Spamhaus</td><td>No</td></tr>
        <tr><td>X4Bnet Spambot</td><td>No</td></tr>
        <tr><td>Anonymizing VPN</td><td>$vpn</td></tr>
        <tr><td>Tor Exit Node</td><td>$tor</td></tr>
        <tr><td>Server</td><td>$server</td></tr>
        <tr><td>Public Proxy</td><td>No</td></tr>
        <tr><td>Web Proxy</td><td>No</td></tr>
        <tr><td>Search Engine Robot</td><td>No</td></tr>
        <tr><td>Residential Proxy</td><td>No</td></tr>
        </table>
        </body></html>
    """.trimIndent()

    private fun indicator(info: ScamalyticsInfo, label: String): Boolean? =
        (info.hostingProxySignals + info.blacklistMarkers).firstOrNull { it.label == label }?.detected

    @Test fun lowRiskPageParsesScoreChecksAndOperator() {
        val info = parseScamalyticsWebPage(webPage("8.8.8.8", 0, "low"), "8.8.8.8")
        assertEquals("8.8.8.8", info.ipAddress)
        assertEquals(0, info.score)
        assertEquals("low", info.risk)
        assertEquals("web", info.mode)
        assertEquals(true, indicator(info, "数据中心 / Hosting"))
        assertEquals(true, indicator(info, "服务器"))
        assertEquals(false, indicator(info, "VPN"))
        assertEquals(false, indicator(info, "Tor"))
        assertEquals(false, indicator(info, "黑名单"))
        assertEquals("Google LLC", info.isp)
        assertEquals("Level 3", info.organization)
    }

    @Test fun mediumRiskPageParsesScoreAndBlacklistHit() {
        val info = parseScamalyticsWebPage(
            webPage("161.77.55.236", 27, "medium", datacenter = "No", firehol = "Yes",
                isp = "RingSquared CC", org = "RingSquared CC"),
            "161.77.55.236"
        )
        assertEquals(27, info.score)
        assertEquals("medium", info.risk)
        assertEquals(false, indicator(info, "数据中心 / Hosting"))
        assertEquals(true, indicator(info, "黑名单"))
    }

    @Test fun pageWithoutScoreOrChecksIsRejected() {
        try {
            parseScamalyticsWebPage("<html><body>8.8.8.8 Fraud Risk, nothing else here.</body></html>", "8.8.8.8")
            fail("Expected web parse to fail")
        } catch (_: IOException) {
        }
    }

    @Test fun pageForDifferentIpIsRejected() {
        try {
            parseScamalyticsWebPage(webPage("8.8.8.8", 0, "low"), "1.1.1.1")
            fail("Expected IP mismatch to fail")
        } catch (_: IOException) {
        }
    }

    @Test fun displayFraudScoreMapsZeroToOne() {
        assertEquals(1, displayFraudScore(0))
        assertEquals(27, displayFraudScore(27))
        assertEquals(100, displayFraudScore(100))
        assertNull(displayFraudScore(null))
    }

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

    @Test fun lookupWithRiskWithoutCredentialsUsesWebPage() = runBlocking {
        val result = repository { url ->
            when {
                url.contains("ip-api.com") -> 200 to ipApiJson
                url.contains("geojs.io") -> 500 to ""
                url.contains("scamalytics.com/ip/") -> 200 to webPage("203.0.113.5", 0, "low")
                else -> 404 to ""
            }
        }.lookupWithRisk()
        assertEquals("203.0.113.5", result.outbound.ipAddress)
        assertEquals("Hosting", result.outbound.type)
        assertEquals("普通", result.outbound.level)
        val risk = result.scamalytics
        assertNotNull(risk)
        assertEquals("web", risk!!.mode)
        assertEquals(0, risk.score)
        assertEquals("low", risk.risk)
        assertNull(result.riskError)
    }

    @Test fun lookupWithRiskWebFailureKeepsIp() = runBlocking {
        val result = repository { url ->
            when {
                url.contains("ip-api.com") -> 200 to ipApiJson
                url.contains("geojs.io") -> 500 to ""
                url.contains("scamalytics.com/ip/") -> 500 to ""
                else -> 404 to ""
            }
        }.lookupWithRisk()
        assertEquals("203.0.113.5", result.outbound.ipAddress)
        assertNull(result.scamalytics)
        assertNotNull(result.riskError)
    }

    @Test fun lookupWithRiskWithCredentialsUsesApiNotWebPage() = runBlocking {
        val apiJson =
            """{"scamalytics":{"status":"ok","ip":"203.0.113.5","scamalytics_score":2,"scamalytics_risk":"low","mode":"live"}}"""
        val result = repository { url ->
            when {
                url.contains("ip-api.com") -> 200 to ipApiJson
                url.contains("geojs.io") -> 500 to ""
                url.contains("api11.scamalytics.com") -> 200 to apiJson
                url.contains("scamalytics.com/ip/") -> throw AssertionError("web page must not be fetched when credentials exist")
                else -> 404 to ""
            }
        }.lookupWithRisk(credentials = ScamalyticsCredentials("fixture-user", "fixture-key"))
        assertEquals("live", result.scamalytics!!.mode)
        assertEquals(2, result.scamalytics!!.score)
        assertNull(result.riskError)
    }
}
