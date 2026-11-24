package com.github.arca.android.data

const val DEFAULT_LOCAL_MIXED_PROXY_PORT = 7890
const val DEFAULT_LOCAL_DNS_PROXY_PORT = 1053
val LOCAL_PROXY_PORT_RANGE = 1..65535

/** The local mixed HTTP/SOCKS endpoint and loopback DNS listener used by the core. */
data class LocalProxyPorts(
    val mixedPort: Int = DEFAULT_LOCAL_MIXED_PROXY_PORT,
    val dnsPort: Int = DEFAULT_LOCAL_DNS_PROXY_PORT
) {
    fun isValid(): Boolean = mixedPort in LOCAL_PROXY_PORT_RANGE &&
        dnsPort in LOCAL_PROXY_PORT_RANGE && mixedPort != dnsPort

    companion object {
        fun parse(mixed: String, dns: String): LocalProxyPorts? {
            if (mixed.isEmpty() || dns.isEmpty()) return null
            if (mixed.any { it !in '0'..'9' } || dns.any { it !in '0'..'9' }) return null
            val ports = LocalProxyPorts(mixed.toIntOrNull() ?: return null, dns.toIntOrNull() ?: return null)
            return ports.takeIf(LocalProxyPorts::isValid)
        }
    }
}
