package com.github.arca.android.data

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.util.Base64
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

/** Mirrors Mihomo's pkg/nowhere/share.go and common/convert NW3 mapping. */
internal class NowhereLink private constructor(
    val name: String,
    private val server: String,
    private val port: Int,
    private val password: String,
    private val network: String,
    private val morph: Boolean,
    private val sni: String,
    private val pin: String
) {
    val endpoint: String get() = if (':' in server) "[$server]:$port" else "$server:$port"

    private fun toProxy(proxyName: String) = linkedMapOf<String, Any>(
        "name" to proxyName,
        "type" to "nowhere",
        "server" to server,
        "port" to port,
        "password" to password,
        "network" to network,
        "morph" to morph,
        "sni" to sni,
        "pin" to pin,
        "udp" to true
    )

    fun toProfileYaml(): String = toProfileYaml(listOf(this))

    companion object {
        const val SCHEME = "nowhere"
        const val INVALID_LINK_MESSAGE = "Nowhere 节点链接无效，请检查密钥、地址和参数"
        private const val PREFIX = "$SCHEME://"
        private const val GROUP = "PROXY"
        private val reservedNames = setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE", "GLOBAL", GROUP)
        private val queryKeys = setOf("net", "morph", "sni", "pin")
        private val keyPattern = Regex("[0-9a-f]{32}")
        private val pinPattern = Regex("[0-9a-f]{64}")
        private val hostLabel = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")
        private val ipv6Chars = Regex("[0-9A-Fa-f:.]+")
        private val base64Alphabet = Regex("[A-Za-z0-9+/=\\r\\n]+")

        fun hasScheme(raw: String): Boolean = raw.startsWith(PREFIX, ignoreCase = true)

        fun parse(raw: String): NowhereLink? {
            if (!hasScheme(raw)) return null
            val beforeFragment = raw.substringBefore('#')
            if (beforeFragment.any { it < ' ' || it == '\u007f' }) return null
            val authority = beforeFragment.substring(PREFIX.length).substringBefore('?')
            val password = unescape(authority.substringBefore('@')) ?: return null
            if (!keyPattern.matches(password)) return null
            val hostPort = authority.substringAfter('@', "")
            val separator = hostPort.lastIndexOf(':')
            if (separator <= 0) return null
            val rawServer = hostPort.substring(0, separator)
            val server = if (rawServer.startsWith('[') && rawServer.endsWith(']')) {
                rawServer.substring(1, rawServer.lastIndex)
            } else {
                rawServer.takeUnless { ':' in it } ?: return null
            }
            if (!validHost(server)) return null
            val rawPort = hostPort.substring(separator + 1)
            if (rawPort.any { it !in '0'..'9' }) return null
            val port = rawPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null

            val values = mutableMapOf<String, String>()
            for (pair in beforeFragment.substringAfter('?', "").split('&')) {
                val key = unescape(pair.substringBefore('=')) ?: continue
                if (key !in queryKeys || key in values) continue
                values[key] = unescape(pair.substringAfter('=', "")) ?: return null
            }
            val network = values["net"] ?: "tcp"
            if (network != "tcp" && network != "udp") return null
            val morph = when (values["morph"]) {
                null, "1" -> true
                "0" -> false
                else -> return null
            }
            val sni = values["sni"].orEmpty().let { if (it == "none") "" else it }
            if (sni.isNotEmpty() && !validHost(sni)) return null
            val pin = values["pin"].orEmpty().let { if (it == "none") "" else it }
            if (pin.isNotEmpty() && !pinPattern.matches(pin)) return null
            val name = unescape(raw.substringAfter('#', "")) ?: return null
            return NowhereLink(name.ifEmpty { server }, server, port, password, network, morph, sni, pin)
        }

        /** Convert only NW3 link lists so that YAML and other protocols remain untouched. */
        fun normalizeProfile(raw: String): String {
            val text = raw.removePrefix("\uFEFF")
            require(text.isNotBlank()) { "配置内容为空" }
            val links = linkList(text) ?: decodeBase64(text)?.let(::linkList) ?: return raw
            val valid = links.mapNotNull(::parse)
            require(valid.isNotEmpty()) { "未找到有效的 Nowhere 节点链接" }
            return toProfileYaml(valid)
        }

        private fun linkList(text: String): List<String>? {
            val links = mutableListOf<String>()
            for (line in text.lineSequence().map { it.trimEnd(' ', '\r') }) {
                if (line.isBlank() || line.startsWith('#')) continue
                if (!hasScheme(line)) return null
                links.add(line)
            }
            return links.takeIf { it.isNotEmpty() }
        }

        private fun decodeBase64(text: String): String? {
            val raw = text.trim()
            if (!base64Alphabet.matches(raw)) return null
            return try {
                val decoded = Base64.getDecoder().decode(raw.replace("\r", "").replace("\n", ""))
                Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(decoded)).toString()
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: CharacterCodingException) {
                null
            }
        }

        private fun toProfileYaml(links: List<NowhereLink>): String {
            val usedNames = reservedNames.toMutableSet()
            val nextSuffix = mutableMapOf<String, Int>()
            val proxies = links.map { link ->
                var name = link.name
                var suffix = nextSuffix[name] ?: 1
                while (!usedNames.add(name)) {
                    name = "${link.name}-${(suffix++).toString().padStart(2, '0')}"
                }
                nextSuffix[link.name] = suffix
                link.toProxy(name)
            }
            val body = linkedMapOf(
                "proxies" to proxies,
                "proxy-groups" to listOf(
                    linkedMapOf("name" to GROUP, "type" to "select", "proxies" to proxies.map { it.getValue("name") })
                ),
                "rules" to listOf("MATCH,$GROUP")
            )
            val options = DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                nonPrintableStyle = DumperOptions.NonPrintableStyle.ESCAPE
            }
            return Yaml(options).dump(body)
        }

        private fun validHost(host: String): Boolean {
            if (':' !in host) return host.length in 1..253 && host.split('.').all(hostLabel::matches)
            if (!ipv6Chars.matches(host)) return false
            if ('.' in host && host.substringAfterLast(':').split('.').any { it.length > 1 && it.startsWith('0') }) {
                return false
            }
            // URI validates IPv6 literals without DNS resolution.
            return runCatching { URI("http://[$host]/").host != null }.getOrDefault(false)
        }

        /** Decode percent escapes once, without form decoding '+' or replacing invalid UTF-8. */
        private fun unescape(raw: String): String? {
            val bytes = raw.toByteArray(Charsets.UTF_8)
            var read = 0
            var write = 0
            while (read < bytes.size) {
                val byte = bytes[read++]
                bytes[write++] = if (byte == '%'.code.toByte()) {
                    if (read + 1 >= bytes.size) return null
                    val first = bytes[read++].toInt().toChar().digitToIntOrNull(16) ?: return null
                    val second = bytes[read++].toInt().toChar().digitToIntOrNull(16) ?: return null
                    (first * 16 + second).toByte()
                } else byte
            }
            return try {
                Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes, 0, write)).toString()
            } catch (_: CharacterCodingException) {
                null
            }
        }
    }
}
