package com.github.arca.android.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ScamalyticsResponseTest {
    @Test
    fun parsesOnlySelectedGroupsAndPreservesThreeStateIndicators() {
        val response = JSONObject(
            """{
              "scamalytics": {
                "status": "ok",
                "ip": "203.0.113.8",
                "scamalytics_score": 99,
                "scamalytics_proxy": {
                  "is_datacenter": true,
                  "is_vpn": false,
                  "is_resproxy": null
                },
                "is_blacklisted_external": false
              },
              "external_datasources": {
                "ip2proxy": {"proxy_type": "PREMIUM FIELD - upgrade to view"},
                "ip2proxy_lite": {
                  "proxy_type": "PUB",
                  "usage_type": "DCH",
                  "ip_blacklisted": true,
                  "ip_blacklist_type": "proxy"
                },
                "firehol": {
                  "is_proxy": true,
                  "ip_blacklisted_30": false,
                  "ip_blacklisted_1day": null
                },
                "ipsum": {"ip_blacklisted": false, "num_blacklists": 0},
                "spamhaus_drop": {"ip_blacklisted": false},
                "x4bnet": {
                  "is_datacenter": true,
                  "is_tor": false,
                  "is_blacklisted_spambot": false
                }
              }
            }"""
        )

        val result = parseScamalyticsResponse(response, "203.0.113.8")

        assertEquals("203.0.113.8", result.ipAddress)
        assertEquals(true, result.hostingProxySignals.single { it.source == "Scamalytics" && it.label == "数据中心 / Hosting" }.detected)
        assertEquals(false, result.hostingProxySignals.single { it.source == "Scamalytics" && it.label == "VPN" }.detected)
        assertNull(result.hostingProxySignals.single { it.source == "Scamalytics" && it.label == "住宅代理" }.detected)
        assertEquals(true, result.blacklistMarkers.single { it.source == "IP2Proxy Lite" }.detected)
        assertEquals(0, result.blacklistCount)
        assertEquals("PUB", result.proxyType)
        assertEquals("IP2Proxy Lite", result.proxyTypeSource)
        assertFalse(result.proxyTypeRequiresPremium)
        assertEquals("DCH", result.usageType)
    }

    @Test
    fun doesNotShowPremiumPlaceholderOrInventMissingValues() {
        val response = JSONObject(
            """{
              "scamalytics": {"status":"ok", "ip":"203.0.113.9"},
              "external_datasources": {
                "ip2proxy": {"proxy_type":"PREMIUM FIELD - upgrade to view"},
                "ip2proxy_lite": {"proxy_type":"PREMIUM FIELD - upgrade to view"}
              }
            }"""
        )
        val result = parseScamalyticsResponse(response, "203.0.113.9")
        assertNull(result.proxyType)
        assertTrue(result.proxyTypeRequiresPremium)
        assertTrue(result.hostingProxySignals.all { it.detected == null })
        assertTrue(result.blacklistMarkers.all { it.detected == null })
    }

    @Test
    fun credentialStringRepresentationRedactsTheKey() {
        val credentials = ScamalyticsCredentials("account-name", "not-for-logs")
        assertFalse(credentials.toString().contains("not-for-logs"))
        assertTrue(credentials.toString().contains("REDACTED"))
    }
}
