package com.github.arca.android.data

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** A validated NW3 endpoint plus its official Vector runtime URL. */
data class NowhereNodeProfile(
    val displayName: String,
    val endpointHost: String,
    val carrier: String,
    val vectorUrl: String,
    val localSocksPort: Int
) {
    /** A standalone, single-proxy configuration with no direct/fallback route. */
    fun profileYaml(): String {
        val proxyName = "NW3-LOCAL"
        val groupName = "NW3-OUT"
        val options = DumperOptions().apply { defaultFlowStyle = DumperOptions.FlowStyle.BLOCK; isPrettyFlow = true }
        val yaml = Yaml(options)
        val doc = linkedMapOf<String, Any>(
            "proxies" to listOf(linkedMapOf("name" to proxyName, "type" to "socks5", "server" to "127.0.0.1", "port" to localSocksPort, "udp" to true)),
            "proxy-groups" to listOf(linkedMapOf("name" to groupName, "type" to "select", "proxies" to listOf(proxyName))),
            "dns" to linkedMapOf("respect-rules" to true, "proxy-server-nameserver" to listOf("223.5.5.5", "119.29.29.29")),
            "rules" to listOf("MATCH,$groupName")
        )
        return yaml.dump(doc)
    }
}

/** Parser for official nowhere share links and vector runtime URLs. */
object NowhereNodeParser {
    private val allowedQuery = setOf("net", "pin", "sni", "morph", "rate", "etar", "in")
    private val forbiddenSecurityOptions = setOf("allowinsecure", "insecure", "skip-cert-verify", "skip_cert_verify", "tlsinsecure")
    private val keyPattern = Regex("[0-9a-f]{32}")
    private val dnsLabel = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")

    @JvmStatic
    @JvmOverloads
    fun parse(link: String, localSocksPort: Int, displayName: String? = null): NowhereNodeProfile {
        require(localSocksPort in 1..65535) { "Invalid local SOCKS port" }
        require(link.length <= 4096) { "URI is too long" }
        require(link.isNotBlank() && link.none { it.isWhitespace() || it.isISOControl() }) { "Malformed URI" }
        validatePercentEscapes(link)
        val uri = try { URI(link) } catch (e: Exception) { throw IllegalArgumentException("Malformed URI", e) }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "nowhere" || scheme == "vector") { "Unsupported scheme" }
        require(uri.rawUserInfo != null && !uri.rawUserInfo.contains(':')) { "A 32-character key is required; passwords are forbidden" }
        val key = decode(uri.rawUserInfo)
        require(keyPattern.matches(key)) { "Key must be 32 lowercase hexadecimal characters" }
        val host = (uri.host ?: throw IllegalArgumentException("Invalid host")).removeSurrounding("[", "]")
        validateHost(host)
        val port: Int
        val carrier: String
        if (scheme == "nowhere") {
            require(uri.rawPath.isNullOrEmpty()) { "Unexpected path" }
            port = uri.port
            carrier = queryParams(uri.rawQuery).let { params ->
                val net = params["net"]?.singleOrNull()
                require(params["net"] == null || net == "tcp" || net == "udp") { "Invalid carrier" }
                net ?: "tcp"
            }
            require(port in 1..65535) { "Invalid port" }
        } else {
            val path = uri.rawPath.orEmpty()
            val pathMatch = Regex("^/(tcp|udp):([0-9]{1,5})$").matchEntire(path)
            if (pathMatch != null) {
                carrier = pathMatch.groupValues[1]
                port = pathMatch.groupValues[2].toIntOrNull() ?: -1
                require(uri.port == -1) { "Port specified twice" }
            } else {
                require(path.isEmpty()) { "Invalid vector path" }
                carrier = "tcp"
                port = uri.port
            }
            require(port in 1..65535) { "Invalid port" }
        }
        val params = queryParams(uri.rawQuery)
        require(params.keys.none { it.lowercase() in forbiddenSecurityOptions }) { "Unsafe TLS option" }
        require(params.keys.all { it in allowedQuery }) { "Unsupported query option" }
        params.forEach { (name, values) -> require(values.size <= 1) { "Duplicate $name" } }
        val requestedNet = params["net"]?.singleOrNull()
        if (scheme == "vector") require(requestedNet == null) { "The net option is only valid on nowhere share links" }
        params["morph"]?.singleOrNull()?.let { require(it == "0" || it == "1") { "morph must be 0 or 1" } }
        params["pin"]?.singleOrNull()?.let {
            require(it == "none" || it.matches(Regex("[0-9a-f]{64}"))) { "pin must be 64 lowercase hexadecimal characters or none" }
        }
        params["sni"]?.singleOrNull()?.let { value ->
            require(value.isNotEmpty()) { "Empty sni" }
            if (value != "none") {
                require(value.length <= 253 && value.all { it.code < 128 } && !value.contains(':') &&
                    !value.contains('[') && !value.contains(']') && !value.matches(Regex("[0-9.]+"))) {
                    "sni must be an ASCII DNS name"
                }
                validateHost(value)
            }
        }
        for (name in listOf("rate", "etar")) {
            params[name]?.singleOrNull()?.let { value ->
                require(value.toIntOrNull()?.let { it >= 0 } == true) { "$name must be a non-negative integer" }
            }
        }

        // Preserve original encoding and parameter values. Only force the local listener override.
        val forwarded = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() && decode(it.substringBefore('=')) != "in" && decode(it.substringBefore('=')) != "net" }.toMutableList()
        if (params["morph"] == null) forwarded.add("morph=1")
        forwarded.add("in=127.0.0.1%3A$localSocksPort")
        val hostForUrl = if (host.contains(':')) "[$host]" else host
        val runtime = "vector://$key@$hostForUrl/${carrier}:$port" + if (forwarded.isEmpty()) "" else "?${forwarded.joinToString("&")}"
        val fragmentName = uri.rawFragment?.let(::decode)?.filterNot { it.isISOControl() }?.take(64)?.trim()?.takeIf { it.isNotBlank() }
        val requestedName = displayName?.filterNot { it.isISOControl() }?.take(64)?.trim()?.takeIf { it.isNotBlank() }
        val name = requestedName ?: fragmentName ?: "$host:$port"
        return NowhereNodeProfile(name, host, carrier, runtime, localSocksPort)
    }

    private fun queryParams(raw: String?): Map<String, List<String>> {
        if (raw == null) return emptyMap()
        require(raw.isNotEmpty()) { "Empty query" }
        val out = linkedMapOf<String, MutableList<String>>()
        for (part in raw.split('&')) {
            require(part.isNotEmpty()) { "Malformed query" }
            val key = decode(part.substringBefore('='))
            require(key.isNotEmpty()) { "Empty query key" }
            val value = if (part.contains('=')) decode(part.substringAfter('=')) else ""
            out.getOrPut(key) { mutableListOf() }.add(value)
        }
        return out
    }

    private fun decode(s: String): String = try { URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8.name()) } catch (e: Exception) { throw IllegalArgumentException("Bad percent encoding", e) }
    private fun validatePercentEscapes(s: String) {
        var i = 0
        while (i < s.length) { if (s[i] == '%') { require(i + 2 < s.length && s.substring(i + 1, i + 3).all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) { "Bad percent escape" }; i += 3 } else i++ }
    }
    private fun validateHost(host: String) {
        if (host.contains(':')) {
            require(host.matches(Regex("[0-9A-Fa-f:.]+")) && host.contains(':')) { "Invalid IPv6 host" }
            require(runCatching { java.net.InetAddress.getByName(host) }.isSuccess) { "Invalid IPv6 host" }
        } else if (host.matches(Regex("[0-9.]+"))) {
            val pieces = host.split('.')
            require(pieces.size == 4 && pieces.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }) { "Invalid IPv4 host" }
        } else {
            require(host.length <= 253 && host.split('.').all { dnsLabel.matches(it) }) { "Invalid hostname" }
        }
    }
}
