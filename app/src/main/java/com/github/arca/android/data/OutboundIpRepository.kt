package com.github.arca.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import org.json.JSONObject
import okhttp3.Request
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Facts supplied by lookup sources; level is a local FlClash-compatible classification. */
data class OutboundIpInfo(
    val ipAddress: String,
    val level: String? = null,
    val type: String? = null,
    val flags: List<String>? = null,
    val organization: String? = null,
    val asn: String? = null,
    val source: String
)

data class ScamalyticsIndicator(
    val source: String,
    val label: String,
    val detected: Boolean?
)

data class ScamalyticsInfo(
    val ipAddress: String,
    val hostingProxySignals: List<ScamalyticsIndicator>,
    val blacklistMarkers: List<ScamalyticsIndicator>,
    val blacklistCount: Int?,
    val ip2proxyBlacklistType: String?,
    val proxyType: String?,
    val proxyTypeSource: String?,
    val proxyTypeRequiresPremium: Boolean,
    val usageType: String?
)

class OutboundIpRepository(
    private val client: OkHttpClient? = null
) {
    private val directClient by lazy { newClient(null) }
    private val proxyClient by lazy { newClient(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890))) }

    suspend fun lookup(isVpnRunning: Boolean = false): OutboundIpInfo = withContext(Dispatchers.IO) {
        val selectedClient = lookupClient(isVpnRunning)
        try {
            query(selectedClient, IDENT_URL, "ident.me") { json ->
                val ip = json.optString("ip").validIp() ?: error("ident.me returned an invalid IP")
                val type = declaredType(json.optString("type"))
                OutboundIpInfo(ip, type?.let(::levelOf), type,
                    organization = json.optString("aso").clean(),
                    asn = json.opt("asn")?.toString()?.asn(), source = "ident.me")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (first: Exception) {
            try {
                query(selectedClient, IPQUERY_URL, "ipquery.io") { json ->
                    val ip = json.optString("ip").validIp() ?: error("ipquery.io returned an invalid IP")
                    val risk = json.optJSONObject("risk")
                    val isp = json.optJSONObject("isp")
                    val type = when {
                        risk?.optBoolean("is_datacenter", false) == true -> "Hosting"
                        risk?.optBoolean("is_mobile", false) == true -> "Mobile"
                        else -> null
                    }
                    val keys = listOf("is_proxy", "is_vpn", "is_tor", "is_abuser")
                    val positive = buildList<String> {
                        listOf("is_proxy" to "Proxy", "is_vpn" to "VPN", "is_tor" to "Tor", "is_abuser" to "Abuser")
                            .forEach { (key, label) -> if (risk?.opt(key) == true) add(label) }
                    }
                    val flags: List<String>? = when {
                        positive.isNotEmpty() -> positive
                        keys.all { risk?.opt(it) is Boolean } -> emptyList()
                        else -> null
                    }
                    val level = if (positive.any { it == "Tor" || it == "Abuser" }) "风险" else type?.let(::levelOf)
                    OutboundIpInfo(ip, level, type, flags,
                        isp?.optString("org")?.clean() ?: isp?.optString("isp")?.clean(),
                        isp?.optString("asn")?.asn(), "ipquery.io")
                }
            } catch (second: kotlinx.coroutines.CancellationException) {
                throw second
            } catch (second: Exception) {
                second.addSuppressed(first)
                throw second
            }
        }
    }

    private suspend fun <T> query(http: OkHttpClient, url: String, source: String, parse: (org.json.JSONObject) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(Request.Builder().url(url).get().build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        response.use {
                            check(it.isSuccessful) { "$source lookup failed (HTTP ${it.code})" }
                            val json = org.json.JSONObject(it.body?.string() ?: error("Empty response"))
                            val result = parse(json)
                            if (continuation.isActive) continuation.resume(result)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }

    internal fun lookupClient(isVpnRunning: Boolean): OkHttpClient =
        client ?: if (isVpnRunning) proxyClient else directClient

    private fun String.asn(): String? = Regex("\\d+").find(this)?.value?.takeUnless { it == "0" }?.let { "AS$it" }

    private fun newClient(proxy: Proxy?) = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .apply { if (proxy != null) proxy(proxy) }
        .build()

    private fun String?.clean(): String? = this?.takeIf { it.isNotBlank() && it != "null" }

    private fun String?.validIp(): String? {
        val value = this?.trim() ?: return null
        if (value.isEmpty() || value.any { it !in "0123456789abcdefABCDEF:." }) return null
        if (value.contains(':')) {
            return try { value.takeIf { InetAddress.getByName(it) is Inet6Address } } catch (_: Exception) { null }
        }
        val parts = value.split('.')
        return value.takeIf { parts.size == 4 && parts.all { part -> part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) && part.toIntOrNull() in 0..255 } }
    }

    private fun declaredType(value: String): String? = when (value.lowercase()) {
        "isp" -> "Residential"
        "hosting" -> "Hosting"
        "business", "education", "government", "banking" -> "Business"
        else -> null
    }

    private fun levelOf(type: String): String = when (type) {
        "Hosting" -> "普通"
        "Residential", "Mobile", "Business" -> "优良"
        else -> "普通"
    }

    companion object {
        private const val IDENT_URL = "https://ident.me/json"
        private const val IPQUERY_URL = "https://api.ipquery.io/?format=json"
    }
}

private const val SCAMALYTICS_PREMIUM_PLACEHOLDER = "PREMIUM FIELD - upgrade to view"

internal fun parseScamalyticsResponse(json: JSONObject, requestedIp: String): ScamalyticsInfo {
    val scamalytics = json.optJSONObject("scamalytics") ?: throw IOException("Invalid Scamalytics response")
    if (scamalytics.optString("status") != "ok") throw IOException("Scamalytics response not successful")
    val returnedIp = scamalytics.safeText("ip") ?: throw IOException("Scamalytics response has no IP")
    if (returnedIp != requestedIp) throw IOException("Scamalytics response IP mismatch")

    val proxy = scamalytics.optJSONObject("scamalytics_proxy")
    val external = json.optJSONObject("external_datasources")
    val ip2proxy = external?.optJSONObject("ip2proxy")
    val ip2proxyLite = external?.optJSONObject("ip2proxy_lite")
    val firehol = external?.optJSONObject("firehol")
    val ipsum = external?.optJSONObject("ipsum")
    val spamhaus = external?.optJSONObject("spamhaus_drop")
    val x4bnet = external?.optJSONObject("x4bnet")

    val hostingProxy = listOf(
        ScamalyticsIndicator("Scamalytics", "数据中心 / Hosting", proxy.boolValue("is_datacenter")),
        ScamalyticsIndicator("Scamalytics", "VPN", proxy.boolValue("is_vpn")),
        ScamalyticsIndicator("Scamalytics", "住宅代理", proxy.boolValue("is_resproxy")),
        ScamalyticsIndicator("FireHOL", "代理", firehol.boolValue("is_proxy")),
        ScamalyticsIndicator("X4BNet", "数据中心 / Hosting", x4bnet.boolValue("is_datacenter")),
        ScamalyticsIndicator("X4BNet", "Tor", x4bnet.boolValue("is_tor"))
    )
    val blacklistMarkers = listOf(
        ScamalyticsIndicator("Scamalytics", "外部黑名单", scamalytics.boolValue("is_blacklisted_external")),
        ScamalyticsIndicator("IP2Proxy Lite", "黑名单", ip2proxyLite.boolValue("ip_blacklisted")),
        ScamalyticsIndicator("FireHOL", "近 30 天", firehol.boolValue("ip_blacklisted_30")),
        ScamalyticsIndicator("FireHOL", "近 1 天", firehol.boolValue("ip_blacklisted_1day")),
        ScamalyticsIndicator("IPsum", "黑名单", ipsum.boolValue("ip_blacklisted")),
        ScamalyticsIndicator("Spamhaus DROP", "黑名单", spamhaus.boolValue("ip_blacklisted")),
        ScamalyticsIndicator("X4BNet", "垃圾邮件标记", x4bnet.boolValue("is_blacklisted_spambot"))
    )

    val premiumProxyType = ip2proxy.safeText("proxy_type")
    val liteProxyType = ip2proxyLite.safeText("proxy_type")
    val premiumPlaceholder = premiumProxyType == SCAMALYTICS_PREMIUM_PLACEHOLDER
    val actualPremiumType = premiumProxyType?.takeUnless { it == SCAMALYTICS_PREMIUM_PLACEHOLDER }
    val actualLiteType = liteProxyType?.takeUnless { it == SCAMALYTICS_PREMIUM_PLACEHOLDER }
    val selectedProxyType = actualPremiumType ?: actualLiteType

    return ScamalyticsInfo(
        ipAddress = returnedIp,
        hostingProxySignals = hostingProxy,
        blacklistMarkers = blacklistMarkers,
        blacklistCount = (ipsum?.opt("num_blacklists") as? Number)?.toInt()?.takeIf { it >= 0 },
        ip2proxyBlacklistType = ip2proxyLite.safeText("ip_blacklist_type"),
        proxyType = selectedProxyType,
        proxyTypeSource = when {
            actualPremiumType != null -> "IP2Proxy"
            actualLiteType != null -> "IP2Proxy Lite"
            else -> null
        },
        proxyTypeRequiresPremium = premiumPlaceholder && actualLiteType == null,
        usageType = ip2proxyLite.safeText("usage_type")
    )
}

private fun JSONObject?.boolValue(key: String): Boolean? = when (val value = this?.opt(key)) {
    is Boolean -> value
    else -> null
}

private fun JSONObject?.safeText(key: String): String? {
    val value = this?.opt(key) ?: return null
    if (value == JSONObject.NULL) return null
    return value.toString().trim().takeIf { it.isNotEmpty() && it != "null" }
}

