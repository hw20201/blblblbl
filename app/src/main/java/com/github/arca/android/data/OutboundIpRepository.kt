package com.github.arca.android.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
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
    val country: String? = null,
    val city: String? = null,
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
    val usageType: String?,
    val score: Int? = null,
    val risk: String? = null,
    /** "web" = public web page; "live"/"test" = official API. */
    val mode: String? = null,
    val creditsRemaining: Long? = null,
    val isp: String? = null,
    val organization: String? = null,
    val ispRequiresPremium: Boolean = false,
    val organizationRequiresPremium: Boolean = false
)

data class OutboundIpResult(
    val outbound: OutboundIpInfo,
    val scamalytics: ScamalyticsInfo? = null,
    val riskError: String? = null
)

/** Messages are fixed locally; never retain request URLs, response bodies, or network causes. */
internal class IpLookupException(message: String, val rateLimited: Boolean = false) : IOException(message)

internal fun safeIpLookupError(error: Throwable): String =
    if (error is IpLookupException) error.message ?: "IP 查询失败，请稍后重试。"
    else "IP 查询失败，请检查网络或代理连接后重试。"

/** Local HTTP proxy of the running VPN core; used for every lookup request while the VPN is up. */
internal const val VPN_LOCAL_PROXY_HOST = "127.0.0.1"
internal const val VPN_LOCAL_PROXY_PORT = 7890

/** The whole exit-IP stage (both racing sources) is bounded by this; the risk stage has its own. */
internal const val EXIT_STAGE_TIMEOUT_MILLIS = 8_000L
internal const val RISK_STAGE_TIMEOUT_MILLIS = 8_000L

private const val EXIT_RESPONSE_MAX_BYTES = 64L * 1024
private const val API_RESPONSE_MAX_BYTES = 256L * 1024
private const val WEB_SOURCE = "Scamalytics 网页"
private const val EXIT_TIMEOUT_MESSAGE = "出口 IP 查询超时，请检查网络或代理连接后重试。"
private const val EXIT_BOTH_FAILED_MESSAGE = "出口 IP 查询失败：主、备用来源均不可用，请检查网络或代理连接后重试。"
internal const val CREDENTIALS_UNREADABLE_MESSAGE = "Scamalytics 凭据无法读取，请重新配置后重试。"

private enum class SourceKind { EXIT, API, WEB }

class OutboundIpRepository internal constructor(
    private val client: OkHttpClient?,
    private val proxyAddress: InetSocketAddress,
    private val dns: Dns,
    private val exitTimeoutMillis: Long,
    private val riskTimeoutMillis: Long
) {
    constructor(client: OkHttpClient? = null) : this(
        client,
        InetSocketAddress(VPN_LOCAL_PROXY_HOST, VPN_LOCAL_PROXY_PORT),
        Dns.SYSTEM,
        EXIT_STAGE_TIMEOUT_MILLIS,
        RISK_STAGE_TIMEOUT_MILLIS
    )

    private val directClient by lazy { newClient(null) }
    private val proxyClient by lazy { newClient(Proxy(Proxy.Type.HTTP, proxyAddress)) }

    /**
     * Exit IP from two free sources raced in parallel: the first successful response with a valid
     * IP wins and the slower request (and its OkHttp call) is cancelled. One source failing never
     * ends the other; both failing, or the 8 s stage limit, fails this lookup with a fixed message.
     * [isVpnRunning] is the route snapshot taken at click time.
     */
    suspend fun lookup(isVpnRunning: Boolean = false): OutboundIpInfo = withContext(Dispatchers.IO) {
        val http = lookupClient(isVpnRunning)
        try {
            withTimeout(exitTimeoutMillis) { raceExitSources(http) }
        } catch (_: TimeoutCancellationException) {
            throw IpLookupException(EXIT_TIMEOUT_MESSAGE)
        }
    }

    private suspend fun raceExitSources(http: OkHttpClient): OutboundIpInfo = coroutineScope {
        val winner = CompletableDeferred<OutboundIpInfo>()
        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val sources: List<suspend () -> OutboundIpInfo> = listOf({ queryIpApi(http) }, { queryGeojs(http) })
        val jobs = sources.map { source ->
            launch {
                try {
                    winner.complete(source())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failures += error
                    if (failures.size == sources.size) {
                        val limited = failures.firstOrNull { it is IpLookupException && it.rateLimited }
                        winner.completeExceptionally(
                            limited as? IpLookupException ?: IpLookupException(EXIT_BOTH_FAILED_MESSAGE)
                        )
                    }
                }
            }
        }
        try {
            winner.await()
        } finally {
            // Cancelling the coroutine runs invokeOnCancellation, which cancels the OkHttp call.
            jobs.forEach { it.cancel() }
        }
    }

    private suspend fun queryIpApi(http: OkHttpClient): OutboundIpInfo =
        fetch(http, Request.Builder().url(IP_API_URL).get().build(), "ip-api.com", SourceKind.EXIT, EXIT_RESPONSE_MAX_BYTES) { text ->
            val json = JSONObject(text)
            if (json.optString("status") != "success") throw IpLookupException("ip-api.com 查询未成功，请重试。")
            val ip = json.optString("query").validIp() ?: throw IpLookupException("ip-api.com 返回了无效 IP。")
            OutboundIpInfo(
                ipAddress = ip,
                organization = json.optString("org").clean() ?: json.optString("isp").clean(),
                asn = json.optString("as").asn(),
                country = json.optString("country").clean(),
                city = json.optString("city").clean(),
                source = "ip-api.com"
            )
        }

    private suspend fun queryGeojs(http: OkHttpClient): OutboundIpInfo =
        fetch(http, Request.Builder().url(GEOJS_URL).get().build(), "geojs.io", SourceKind.EXIT, EXIT_RESPONSE_MAX_BYTES) { text ->
            val json = JSONObject(text)
            val ip = json.optString("ip").validIp() ?: throw IpLookupException("geojs.io 返回了无效 IP。")
            val rawOrg = json.optString("organization").clean()
            // geojs returns the organization as "AS<n> <name>"; keep only the name.
            val asnDigits = json.opt("asn")?.toString()?.let { Regex("\\d+").find(it)?.value }?.takeUnless { it.trimStart('0').isEmpty() }
            val organization = rawOrg?.replace(Regex("^AS\\d+\\s+"), "").clean()
            OutboundIpInfo(
                ipAddress = ip,
                organization = organization ?: json.optString("organization_name").clean(),
                asn = asnDigits?.let { "AS$it" },
                country = json.optString("country").clean(),
                city = json.optString("city").clean(),
                source = "geojs.io"
            )
        }

    /**
     * One user action, with partial IP success retained when the risk provider is unavailable.
     * Credentials are optional: with them only the official Scamalytics API is used; without
     * them only the public Scamalytics web page is read. Never both.
     */
    suspend fun lookupWithRisk(
        isVpnRunning: Boolean = false,
        credentials: ScamalyticsCredentials? = null,
        test: Boolean = false,
        onIpResolved: (OutboundIpInfo) -> Unit = {}
    ): OutboundIpResult = lookupWithRisk(
        isVpnRunning,
        if (credentials != null) CredentialLoadState.Present(credentials) else CredentialLoadState.Absent,
        test,
        onIpResolved
    )

    /**
     * Same as the credentials overload but driven by the stored state: [CredentialLoadState.Unreadable]
     * is never treated as "not configured" — neither the API nor the public page is requested.
     */
    suspend fun lookupWithRisk(
        isVpnRunning: Boolean,
        credentialState: CredentialLoadState,
        test: Boolean = false,
        onIpResolved: (OutboundIpInfo) -> Unit = {}
    ): OutboundIpResult {
        val outbound = lookup(isVpnRunning)
        onIpResolved(outbound)
        return when (credentialState) {
            is CredentialLoadState.Present -> try {
                OutboundIpResult(outbound, lookupScamalytics(outbound.ipAddress, credentialState.credentials, isVpnRunning, test))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                OutboundIpResult(outbound, riskError = safeIpLookupError(error))
            }
            CredentialLoadState.Unreadable -> OutboundIpResult(outbound, riskError = CREDENTIALS_UNREADABLE_MESSAGE)
            CredentialLoadState.Absent -> try {
                val webRisk = lookupScamalyticsWeb(outbound.ipAddress, isVpnRunning)
                // The public page reports whether the IP sits in a datacenter; use that to
                // classify the exit when the IP sources themselves carry no type.
                val classified = if (outbound.type == null &&
                    webRisk.hostingProxySignals.any { it.label == "数据中心 / Hosting" && it.detected == true }
                ) outbound.copy(type = "Hosting", level = levelOf("Hosting")) else outbound
                OutboundIpResult(classified, webRisk)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                OutboundIpResult(outbound, riskError = safeIpLookupError(error))
            }
        }
    }

    /**
     * Built-in risk check: a plain GET of the public Scamalytics web page for the IP, parsed from
     * its real HTML table rows. No credentials, no paid API. The body is capped at 1 MiB (after
     * OkHttp's transparent gzip decoding, which must not be repeated by hand).
     */
    suspend fun lookupScamalyticsWeb(ip: String, isVpnRunning: Boolean = false): ScamalyticsInfo =
        withContext(Dispatchers.IO) {
            require(ip.validIp() != null) { "Invalid IP address" }
            val http = riskClient(isVpnRunning)
            val request = Request.Builder().url("$SCAMALYTICS_WEB_URL$ip").get()
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                .build()
            fetch(http, request, WEB_SOURCE, SourceKind.WEB, SCAMALYTICS_WEB_MAX_BYTES.toLong()) { html ->
                parseScamalyticsWebPage(html, ip)
            }
        }

    suspend fun lookupScamalytics(
        ip: String,
        credentials: ScamalyticsCredentials,
        isVpnRunning: Boolean = false,
        test: Boolean = false
    ): ScamalyticsInfo = withContext(Dispatchers.IO) {
        val http = riskClient(isVpnRunning)
        val request = Request.Builder().url(scamalyticsUrl(credentials, ip, test)).get().build()
        fetch(http, request, "Scamalytics", SourceKind.API, API_RESPONSE_MAX_BYTES) {
            parseScamalyticsResponse(JSONObject(it), ip)
        }
    }

    internal fun scamalyticsUrl(credentials: ScamalyticsCredentials, ip: String, test: Boolean = false): HttpUrl {
        val valid = credentials.validated()
        require(ip.validIp() != null) { "Invalid IP address" }
        return HttpUrl.Builder().scheme("https").host(valid.endpoint.host)
            .addPathSegment("v3").addPathSegment(valid.username).addPathSegment("")
            .addQueryParameter("key", valid.apiKey).addQueryParameter("ip", ip)
            .apply { if (test) addQueryParameter("test", "1") }.build()
    }

    /** Risk stage: own 8 s ceiling; clone even injected clients so a redirect can never forward a key. */
    private fun riskClient(isVpnRunning: Boolean): OkHttpClient = lookupClient(isVpnRunning).newBuilder()
        .callTimeout(riskTimeoutMillis, TimeUnit.MILLISECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    /**
     * One cancellable request. Cancelling the coroutine cancels the underlying OkHttp call.
     * Errors are mapped to fixed Chinese messages; bodies, URLs and causes never escape.
     */
    private suspend fun <T> fetch(
        http: OkHttpClient,
        request: Request,
        source: String,
        kind: SourceKind,
        maxBytes: Long,
        parse: (String) -> T
    ): T = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(
                    if (e is InterruptedIOException && !call.isCanceled()) IpLookupException("$source 请求超时，请稍后重试。")
                    else IpLookupException("$source 连接失败，请检查网络或代理连接。")
                )
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) throw httpError(source, kind, it.code)
                        val result = parse(readBounded(it.body, maxBytes, source))
                        if (continuation.isActive) continuation.resume(result)
                    }
                } catch (e: IpLookupException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(
                        IpLookupException("$source 返回了无效数据，请重试。")
                    )
                }
            }
        })
    }

    private fun httpError(source: String, kind: SourceKind, code: Int): IpLookupException = when {
        code == 429 -> IpLookupException(
            if (kind == SourceKind.API) "$source 额度不足或请求过于频繁，请稍后重试。"
            else "$source 请求过于频繁，请稍后重试。",
            rateLimited = true
        )
        code in 300..399 -> IpLookupException("$source 返回了重定向，已停止请求。")
        kind == SourceKind.WEB && (code == 401 || code == 403) ->
            IpLookupException("$source 拒绝了访问（可能需要人机验证），请稍后重试。")
        kind == SourceKind.API && (code == 401 || code == 403) ->
            IpLookupException("$source 认证失败，请检查账号、密钥与 API 节点。")
        kind == SourceKind.API && code == 402 -> IpLookupException("$source 额度不足或请求过于频繁，请稍后重试。")
        else -> IpLookupException("$source 服务暂时不可用（HTTP $code）。")
    }

    private fun emptyResponse(source: String): Nothing = throw IpLookupException("$source 返回了空响应。")

    private fun oversizeResponse(source: String): Nothing = throw IpLookupException("$source 返回的内容过大，已丢弃。")

    /** Bounded read of the (already gzip-decoded) body; oversize is a failure, never truncated data. */
    private fun readBounded(body: ResponseBody?, maxBytes: Long, source: String): String {
        body ?: emptyResponse(source)
        if (body.contentLength() > maxBytes) oversizeResponse(source)
        val buffered = body.source()
        buffered.request(maxBytes + 1)
        if (buffered.buffer.size > maxBytes) oversizeResponse(source)
        val text = buffered.readString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
        if (text.isBlank()) emptyResponse(source)
        return text
    }

    internal fun lookupClient(isVpnRunning: Boolean): OkHttpClient =
        client ?: if (isVpnRunning) proxyClient else directClient

    private fun String.asn(): String? = Regex("\\d+").find(this)?.value?.takeUnless { it.trimStart('0').isEmpty() }?.let { "AS$it" }

    private fun newClient(proxy: Proxy?) = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .dns(dns)
        .apply { if (proxy != null) proxy(proxy) }
        .build()

    /** Blank and provider placeholders ("null", "nil", "Unknown", ...) are unknown, never text. */
    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() !in UNKNOWN_PLACEHOLDERS }

    private fun levelOf(type: String): String = when (type) {
        "Hosting" -> "普通"
        "Residential", "Mobile", "Business" -> "优良"
        else -> "普通"
    }

    companion object {
        // Only the fields that are read; lang keeps country/city localised.
        private const val IP_API_URL = "http://ip-api.com/json/?fields=status,query,country,city,isp,org,as&lang=zh-CN"
        private const val GEOJS_URL = "https://get.geojs.io/v1/ip/geo.json"
        private const val SCAMALYTICS_WEB_URL = "https://scamalytics.com/ip/"
        private val UNKNOWN_PLACEHOLDERS = setOf("null", "nil", "unknown", "n/a", "none", "undefined", "-")
    }
}

private const val SCAMALYTICS_PREMIUM_PLACEHOLDER = "PREMIUM FIELD - upgrade to view"

private fun requireScamalyticsObject(json: JSONObject): JSONObject {
    val scamalytics = json.optJSONObject("scamalytics") ?: throw IpLookupException("Scamalytics 返回了无效数据。")
    if (scamalytics.optString("status") != "ok") {
        // Inspect only to classify; provider text may echo credentials and must never escape.
        val message = scamalytics.optString("error").lowercase()
        throw IpLookupException(when {
            listOf("unauthorized", "invalid key", "api key", "authentication").any { it in message } ->
                "Scamalytics 认证失败，请检查账号、密钥与 API 节点。"
            listOf("credit", "quota", "limit").any { it in message } ->
                "Scamalytics 额度不足或请求过于频繁，请稍后重试。"
            else -> "Scamalytics 查询未成功，请检查账号配置或稍后重试。"
        })
    }
    return scamalytics
}

internal fun parseScamalyticsResponse(json: JSONObject, requestedIp: String): ScamalyticsInfo {
    val scamalytics = requireScamalyticsObject(json)
    val returnedIp = scamalytics.safeText("ip") ?: throw IpLookupException("Scamalytics 返回了无效 IP。")
    if (!sameIpAddress(returnedIp, requestedIp)) throw IpLookupException("Scamalytics 返回的 IP 与本次检测不匹配。")

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
        blacklistCount = ipsum.nonNegativeInteger("num_blacklists")?.takeIf { it <= Int.MAX_VALUE }?.toInt(),
        ip2proxyBlacklistType = ip2proxyLite.actualText("ip_blacklist_type"),
        proxyType = selectedProxyType,
        proxyTypeSource = when {
            actualPremiumType != null -> "IP2Proxy"
            actualLiteType != null -> "IP2Proxy Lite"
            else -> null
        },
        proxyTypeRequiresPremium = (premiumPlaceholder || liteProxyType == SCAMALYTICS_PREMIUM_PLACEHOLDER) && selectedProxyType == null,
        usageType = ip2proxyLite.actualText("usage_type"),
        score = scamalytics.nonNegativeInteger("scamalytics_score")?.takeIf { it <= 100 }?.toInt(),
        risk = scamalytics.actualText("scamalytics_risk")?.takeIf { it in setOf("low", "medium", "high", "very high") },
        mode = scamalytics.actualText("mode")?.takeIf { it == "live" || it == "test" },
        creditsRemaining = scamalytics.optJSONObject("credits").nonNegativeInteger("remaining"),
        isp = scamalytics.actualText("scamalytics_isp"),
        organization = scamalytics.actualText("scamalytics_org"),
        ispRequiresPremium = scamalytics.safeText("scamalytics_isp") == SCAMALYTICS_PREMIUM_PLACEHOLDER,
        organizationRequiresPremium = scamalytics.safeText("scamalytics_org") == SCAMALYTICS_PREMIUM_PLACEHOLDER
    )
}

internal fun String?.validIp(): String? {
    val value = this?.trim() ?: return null
    // Only IP literals reach InetAddress; untrusted response text must never trigger DNS.
    if (value.isEmpty() || value.any { it !in "0123456789abcdefABCDEF:." }) return null
    if (value.contains(':')) {
        return try { value.takeIf { InetAddress.getByName(it) is Inet6Address } } catch (_: Exception) { null }
    }
    val parts = value.split('.')
    return value.takeIf { parts.size == 4 && parts.all { part ->
        part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) && part.toIntOrNull() in 0..255
    } }
}

internal fun sameIpAddress(left: String, right: String): Boolean {
    val validLeft = left.validIp() ?: return false
    val validRight = right.validIp() ?: return false
    return try {
        InetAddress.getByName(validLeft).address.contentEquals(InetAddress.getByName(validRight).address)
    } catch (_: Exception) { false }
}

private fun JSONObject?.nonNegativeInteger(key: String): Long? = when (val value = this?.opt(key)) {
    is Int -> value.toLong().takeIf { it >= 0 }
    is Long -> value.takeIf { it >= 0 }
    else -> null
}

private fun JSONObject?.actualText(key: String): String? = safeText(key)?.takeUnless { it == SCAMALYTICS_PREMIUM_PLACEHOLDER }

private fun JSONObject?.boolValue(key: String): Boolean? = when (val value = this?.opt(key)) {
    is Boolean -> value
    else -> null
}

private fun JSONObject?.safeText(key: String): String? {
    val value = this?.opt(key) ?: return null
    if (value == JSONObject.NULL) return null
    return value.toString().trim().takeIf { it.isNotEmpty() && it != "null" }
}

/**
 * A fraud score of 0 means "no observed risk", but a bare 0 reads as "no data" in the
 * panel; BettBox-era displays show it as 1 instead. The stored score stays 0.
 */
internal fun displayFraudScore(score: Int?): Int? = score?.let { if (it == 0) 1 else it }
