package com.github.arca.android.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScamalyticsWebParserTest {
    private val ip = "203.0.113.5"

    private fun row(label: String, value: String) = "<tr><th>$label</th><td>$value</td></tr>"

    private fun page(
        ip: String = this.ip,
        banner: String? = "Fraud Score: 12",
        json: String? = """{ "ip":"$ip", "score":"12", "risk":"low" }""",
        riskLabel: String? = "Low Risk",
        rows: String = defaultRows,
        title: String = "$ip IP Fraud Risk",
        extra: String = ""
    ) = """<!DOCTYPE html><html><head><title>$title</title><style>.x{}</style></head><body>
        <h1>$ip IP Fraud Risk</h1>
        ${riskLabel?.let { "<div class=\"panel_title\">$it</div>" } ?: ""}
        ${banner?.let { "<div class=\"score\">$it</div>" } ?: ""}
        ${json?.let { "<pre>$it</pre>" } ?: ""}
        <table>$rows</table>
        $extra
        </body></html>"""

    private val defaultRows = listOf(
        "ISP Name" to "Example ISP", "Organization Name" to "Example Org", "Connection type" to "n/a",
        "Datacenter" to "No", "Firehol" to "No", "IP2ProxyLite" to "No", "IPsum" to "No", "Spamhaus" to "No",
        "X4Bnet Spambot" to "No", "Anonymizing VPN" to "No", "Tor Exit Node" to "No", "Server" to "No",
        "Public Proxy" to "No", "Web Proxy" to "No", "Search Engine Robot" to "No", "Residential Proxy" to "No"
    ).joinToString("\n") { row(it.first, it.second) }

    private fun check(info: ScamalyticsInfo, source: String, label: String): Boolean? =
        (info.hostingProxySignals + info.blacklistMarkers).first { it.source == source && it.label == label }.detected

    private fun assertRejected(html: String, requested: String = ip, messagePart: String? = null) {
        try {
            parseScamalyticsWebPage(html, requested)
            fail("Expected failure")
        } catch (e: IOException) {
            assertTrue(e is IpLookupException)
            if (messagePart != null) assertTrue(e.message, e.message!!.contains(messagePart))
        }
    }

    @Test fun normalPageReadsScoreRiskAndEveryCheck() {
        val info = parseScamalyticsWebPage(page(), ip)
        assertEquals(12, info.score); assertEquals("low", info.risk); assertEquals("web", info.mode); assertEquals(ip, info.ipAddress)
        assertEquals("Example ISP", info.isp); assertEquals("Example Org", info.organization)
        assertEquals(8, info.hostingProxySignals.size); assertEquals(5, info.blacklistMarkers.size)
        (info.hostingProxySignals + info.blacklistMarkers).forEach { assertEquals(it.label, false, it.detected) }
    }

    @Test fun yesAndNoMapToHitAndMissPerCheck() {
        val rows = listOf("Datacenter" to "Yes", "Anonymizing VPN" to "Yes", "Tor Exit Node" to "No", "Server" to "Yes",
            "Public Proxy" to "No", "Web Proxy" to "Yes", "Search Engine Robot" to "No", "Residential Proxy" to "Yes",
            "Firehol" to "Yes", "IP2ProxyLite" to "No", "IPsum" to "Yes", "Spamhaus" to "No", "X4Bnet Spambot" to "Yes")
            .joinToString("") { row(it.first, it.second) }
        val info = parseScamalyticsWebPage(page(rows = rows), ip)
        assertEquals(true, check(info, "Scamalytics", "数据中心 / Hosting"))
        assertEquals(true, check(info, "Scamalytics", "VPN"))
        assertEquals(false, check(info, "Scamalytics", "Tor"))
        assertEquals(true, check(info, "Scamalytics", "服务器"))
        assertEquals(false, check(info, "Scamalytics", "公开代理"))
        assertEquals(true, check(info, "Scamalytics", "网页代理"))
        assertEquals(false, check(info, "Scamalytics", "搜索引擎机器人"))
        assertEquals(true, check(info, "Scamalytics", "住宅代理"))
        assertEquals(true, check(info, "FireHOL", "黑名单"))
        assertEquals(false, check(info, "IP2Proxy Lite", "黑名单"))
        assertEquals(true, check(info, "IPsum", "黑名单"))
        assertEquals(false, check(info, "Spamhaus", "黑名单"))
        assertEquals(true, check(info, "X4Bnet Spambot", "黑名单"))
    }

    @Test fun whitespaceNewlinesEntitiesAndUpperCaseTagsAreHandled() {
        val rows = """
            <TR>
               <TH scope="row">
                   Anonymizing&nbsp;VPN
               </TH>
               <TD>
                   <span class="bad">&nbsp;Yes&nbsp;</span>
               </TD>
            </TR>
            <tr><td>Tor Exit Node&nbsp;</td>
                <td>

                  No
                </td></tr>
            <tr><td>ISP Name</td><td>AT&amp;T &#39;Corp&#39; &quot;X&quot; &#x41;</td></tr>
            <tr><td>Organization Name</td><td>n/a</td></tr>
        """
        val info = parseScamalyticsWebPage(page(rows = rows), ip)
        assertEquals(true, check(info, "Scamalytics", "VPN"))
        assertEquals(false, check(info, "Scamalytics", "Tor"))
        assertEquals("AT&T 'Corp' \"X\" A", info.isp)
        assertNull("n/a is unknown, not text", info.organization)
    }

    @Test fun entityDecodingIsSinglePassAndRejectsBadReferences() {
        assertEquals("&nbsp;", decodeHtmlEntities("&amp;nbsp;"))
        assertEquals("<b>", decodeHtmlEntities("&lt;b&gt;"))
        assertEquals("&#xD800;", decodeHtmlEntities("&#xD800;"))
        assertEquals("&unknown;", decodeHtmlEntities("&unknown;"))
        assertEquals("'", decodeHtmlEntities("&#39;"))
    }

    @Test fun scoreBoundaryValues() {
        assertEquals(0, parseScamalyticsWebPage(page(banner = "Fraud Score: 0", json = null), ip).score)
        assertEquals(100, parseScamalyticsWebPage(page(banner = "Fraud Score: 100", json = null), ip).score)
        listOf("101", "999", "abc", "-1", "1.5", "1e2", "ten").forEach { bad ->
            val info = parseScamalyticsWebPage(page(banner = "Fraud Score: $bad", json = null), ip)
            assertNull("banner '$bad'", info.score)
            val viaJson = parseScamalyticsWebPage(page(banner = null, json = """{"ip":"$ip","score":"$bad"}"""), ip)
            assertNull("json '$bad'", viaJson.score)
        }
    }

    @Test fun jsonScoreAcceptedAsStringOrNumberAndMustBeAnInteger() {
        assertEquals(37, parseScamalyticsWebPage(page(banner = null, json = """{"ip":"$ip","score":"37"}"""), ip).score)
        assertEquals(37, parseScamalyticsWebPage(page(banner = null, json = """{"ip":"$ip","score":37}"""), ip).score)
        assertNull(parseScamalyticsWebPage(page(banner = null, json = """{"ip":"$ip","score":"nan"}"""), ip).score)
    }

    @Test fun conflictingOrInvalidScoreSourcesGiveNoScore() {
        assertNull(parseScamalyticsWebPage(page(banner = "Fraud Score: 12", json = """{"ip":"$ip","score":"55"}"""), ip).score)
        assertNull(parseScamalyticsWebPage(page(banner = "Fraud Score: 101", json = """{"ip":"$ip","score":"55"}"""), ip).score)
        assertEquals(12, parseScamalyticsWebPage(page(banner = "Fraud Score: <span>12</span>", json = null), ip).score)
    }

    @Test fun scoresInScriptCommentsPromosAndUnrelatedNumbersAreNeverUsed() {
        val extra = """
            <script>var cfg = {"score":"55"}; var t = "Fraud Score: 66";</script>
            <!-- Fraud Score: 77 -->
            <div class="promo">Get our Fraud Score API today for 99 USD, 88 requests free!</div>
            <p>Page views: 42. Score card 91.</p>"""
        val info = parseScamalyticsWebPage(page(banner = null, json = null, riskLabel = null, extra = extra), ip)
        assertNull(info.score)
    }

    @Test fun onlyScoreIsEnoughForPartialData() {
        val info = parseScamalyticsWebPage(page(rows = "", riskLabel = null), ip)
        assertEquals(12, info.score)
        assertTrue((info.hostingProxySignals + info.blacklistMarkers).all { it.detected == null })
        assertNull(info.isp)
    }

    @Test fun onlyChecksAreEnoughForPartialData() {
        val info = parseScamalyticsWebPage(page(banner = null, json = null, riskLabel = null,
            rows = row("Datacenter", "Yes") + row("Firehol", "No")), ip)
        assertNull(info.score); assertNull(info.risk)
        assertEquals(true, check(info, "Scamalytics", "数据中心 / Hosting"))
        assertEquals(false, check(info, "FireHOL", "黑名单"))
        assertNull(check(info, "Scamalytics", "VPN"))
    }

    @Test fun neitherScoreNorChecksIsAFailure() {
        assertRejected(page(banner = null, json = null, riskLabel = "Low Risk", rows = ""))
        assertRejected(page(banner = "Fraud Score: abc", json = null, rows = ""))
        assertRejected(page(banner = "Fraud Score: 101", json = null, rows = row("Datacenter", "Maybe")))
    }

    @Test fun missingOrNonExplicitCheckIsUnknownNeverNotHit() {
        val rows = row("Datacenter", "") + row("Firehol", "n/a") + row("IPsum", "Maybe") + row("Spamhaus", "PREMIUM FIELD") +
            row("Server", "Yes") // at least one explicit value so the page is valid
        val info = parseScamalyticsWebPage(page(banner = null, json = null, rows = rows), ip)
        assertNull(check(info, "Scamalytics", "数据中心 / Hosting"))
        assertNull(check(info, "FireHOL", "黑名单"))
        assertNull(check(info, "IPsum", "黑名单"))
        assertNull(check(info, "Spamhaus", "黑名单"))
        assertNull(check(info, "X4Bnet Spambot", "黑名单"))
        assertEquals(true, check(info, "Scamalytics", "服务器"))
    }

    @Test fun conflictingDuplicateRowsAreUnknownAgreeingOnesAreKept() {
        val rows = row("Datacenter", "Yes") + row("Datacenter", "No") + row("Server", "No") + row("Server", "No")
        val info = parseScamalyticsWebPage(page(rows = rows), ip)
        assertNull(check(info, "Scamalytics", "数据中心 / Hosting"))
        assertEquals(false, check(info, "Scamalytics", "服务器"))
    }

    @Test fun rowsHiddenInScriptsCommentsAndStyleAreIgnored() {
        val extra = """<!-- <tr><td>Tor Exit Node</td><td>Yes</td></tr> -->
            <script>document.write("<tr><td>Public Proxy</td><td>Yes</td></tr>")</script>
            <style>/* <tr><td>Web Proxy</td><td>Yes</td></style>
            <p>Server Yes Datacenter Yes</p>"""
        val info = parseScamalyticsWebPage(page(rows = row("Anonymizing VPN", "No"), extra = extra), ip)
        assertNull(check(info, "Scamalytics", "Tor"))
        assertNull(check(info, "Scamalytics", "公开代理"))
        assertNull(check(info, "Scamalytics", "网页代理"))
        assertNull("prose is not a table row", check(info, "Scamalytics", "服务器"))
        assertEquals(false, check(info, "Scamalytics", "VPN"))
    }

    @Test fun riskBandsComeFromThePageAndNeverFromTheScore() {
        mapOf("low" to "Low Risk", "medium" to "Medium Risk", "high" to "High Risk", "very high" to "Very High Risk").forEach { (band, label) ->
            assertEquals(band, parseScamalyticsWebPage(page(json = null, riskLabel = label), ip).risk)
            assertEquals(band, parseScamalyticsWebPage(page(riskLabel = null, json = """{"ip":"$ip","score":"12","risk":"$band"}"""), ip).risk)
        }
        assertNull("score 95 must not invent a band", parseScamalyticsWebPage(page(banner = "Fraud Score: 95", json = null, riskLabel = null), ip).risk)
        assertNull(parseScamalyticsWebPage(page(riskLabel = null, json = """{"ip":"$ip","score":"12","risk":"extreme"}"""), ip).risk)
        assertNull("ambiguous legend labels give no band", parseScamalyticsWebPage(
            page(json = null, riskLabel = "Low Risk</div><div>High Risk"), ip).risk)
        // prose containing the words is not a band label
        assertNull(parseScamalyticsWebPage(page(json = null, riskLabel = null,
            extra = "<p>this is a potentially high risk address</p>"), ip).risk)
    }

    @Test fun captchaChallengeAndDeniedPagesAreFailures() {
        val withValidLookingData = defaultRows
        listOf(
            "<html><head><title>Just a moment...</title></head><body>$ip</body></html>",
            "<html><head><title>Attention Required! | Cloudflare</title></head><body>$ip</body></html>",
            page(extra = """<form id="challenge-form" action="/cdn-cgi/"></form>"""),
            page(extra = """<div class="g-recaptcha" data-sitekey="k"></div>"""),
            page(extra = """<div class="cf-turnstile"></div>"""),
            page(extra = "<p>Please complete the CAPTCHA to continue.</p>"),
            page(extra = "<p>Please verify you are human</p>"),
            page(title = "Access denied", rows = withValidLookingData)
        ).forEach { assertRejected(it, messagePart = "验证") }
    }

    @Test fun captchaWordOnlyInsideScriptDoesNotBlockARealPage() {
        val info = parseScamalyticsWebPage(page(extra = """<script src="https://x/recaptcha.js"></script><script>var captcha=1</script>"""), ip)
        assertEquals(12, info.score)
    }

    @Test fun emptyAndBlankPagesAreFailures() {
        listOf("", "   \n ", "<html></html>", "<html><body>  </body></html>", "<script>1</script>").forEach { assertRejected(it) }
    }

    @Test fun oversizedPageIsAFailure() {
        assertRejected(page(extra = "<p>" + "x".repeat(SCAMALYTICS_WEB_MAX_BYTES) + "</p>"))
    }

    @Test fun pageForAnotherIpIsRejectedOnTokenBoundaries() {
        assertRejected(page(ip = "1.1.1.1"), requested = "8.8.8.8", messagePart = "不匹配")
        assertRejected(page(ip = "18.8.8.80"), requested = "8.8.8.8")
        assertRejected(page(ip = "8.8.8.80"), requested = "8.8.8.8")
        assertRejected(page(ip = "8.8.8.88"), requested = "8.8.8.8")
        assertRejected(page(ip = "98.8.8.8"), requested = "8.8.8.8")
        assertRejected(page(ip = "8.8.8.8.8"), requested = "8.8.8.8")
        assertRejected(page(ip = "2001:db8::2"), requested = "2001:db8::1")
        val ok = parseScamalyticsWebPage(page(ip = "8.8.8.8"), "8.8.8.8")
        assertEquals("8.8.8.8", ok.ipAddress)
    }

    @Test fun requestedIpOnlyMentionedInBodyDoesNotSaveAMismatchedHeading() {
        val html = page(ip = "1.1.1.1", extra = "<p>Compare with 8.8.8.8</p>")
        assertRejected(html, requested = "8.8.8.8")
        val jsonOther = page(banner = "Fraud Score: 5", json = """{"ip":"1.1.1.1","score":"5"}""", title = "Fraud Risk", extra = "").replace("<h1>203.0.113.5 IP Fraud Risk</h1>", "")
        assertRejected(jsonOther, requested = "8.8.8.8")
    }

    @Test fun ipMatchAcceptsPunctuationAndEquivalentIpv6Spelling() {
        assertEquals(12, parseScamalyticsWebPage(page(title = "IP: 203.0.113.5.", ip = "203.0.113.5"), ip).score)
        val html = page(ip = "2001:db8::1", banner = "Fraud Score: 3", json = null)
        assertEquals(3, parseScamalyticsWebPage(html, "2001:0db8:0:0:0:0:0:1").score)
    }

    @Test fun displayScoreMapsZeroToOneButRawStaysZero() {
        val info = parseScamalyticsWebPage(page(banner = "Fraud Score: 0", json = null), ip)
        assertEquals(0, info.score)
        assertEquals(1, displayFraudScore(info.score))
        assertEquals(100, displayFraudScore(100))
        assertNull(displayFraudScore(null))
    }
}
