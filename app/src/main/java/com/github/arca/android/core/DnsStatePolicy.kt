package com.github.arca.android.core

/** An accepted physical-network DNS state. DNS order is significant to the native resolver. */
data class DnsUpdate(
    val networkIdentity: String?,
    val dnsServers: List<String>
) {
    val orderedDnsKey: String get() = dnsServers.joinToString(",")
}

/**
 * Pure, caller-serialized IPv4 DNS policy; it performs no address lookup or Android/native calls.
 *
 * A valid network switch is observable even when both networks advertise the same DNS servers.
 * Missing networks and transiently empty/IPv6-only DNS retain the last accepted state. Only the
 * first observation without usable IPv4 DNS uses the existing compatibility fallback. Call [reset]
 * when the consumer loses its DNS state (for example, after recreating the native core).
 */
class DnsStatePolicy {
    var lastAccepted: DnsUpdate? = null
        private set

    fun update(networkIdentity: String?, dnsServers: Iterable<String>): DnsUpdate? {
        val previous = lastAccepted
        if (networkIdentity == null && previous != null) return null

        val normalized = normalizeIpv4Dns(dnsServers)
        val effective = when {
            networkIdentity != null && normalized.isNotEmpty() -> normalized
            previous != null -> return null
            else -> FALLBACK_IPV4_DNS
        }
        val next = DnsUpdate(networkIdentity, effective)
        if (next == previous) return null
        lastAccepted = next
        return next
    }

    fun reset() {
        lastAccepted = null
    }

    companion object {
        val FALLBACK_IPV4_DNS: List<String> = listOf("223.5.5.5", "119.29.29.29")

        /** Canonical decimal IPv4 literals, de-duplicated in their advertised order. */
        fun normalizeIpv4Dns(dnsServers: Iterable<String>): List<String> = dnsServers
            .mapNotNull { raw ->
                val parts = raw.trim().split('.')
                if (parts.size != 4) return@mapNotNull null
                val octets = parts.map { part ->
                    if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' }) {
                        return@mapNotNull null
                    }
                    part.toIntOrNull()?.takeIf { it in 0..255 } ?: return@mapNotNull null
                }
                octets.joinToString(".")
            }
            .distinct()
    }
}
