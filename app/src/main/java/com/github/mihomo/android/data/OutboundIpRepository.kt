package com.github.mihomo.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
