package com.github.arca.android.service.network

/** A non-routing snapshot. No transport preference or socket binding is implied. */
internal data class PhysicalNetworkCandidate(
    val identity: String,
    val dnsServers: List<String> = emptyList(),
    val isVpn: Boolean = false,
    val hasInternet: Boolean = true,
    val validated: Boolean = false,
    val notSuspended: Boolean = true
)

internal data class PhysicalNetworkSnapshot(
    val candidates: List<PhysicalNetworkCandidate>,
    /** The actual app default, only when it is known to be a non-VPN candidate. */
    val activeDefault: String? = null
)

/**
 * Respect an explicit physical default first. When Android exposes a VPN as the app default, its
 * physical default cannot be inferred exactly on API 26+ using public APIs. In that case compare
 * validation/suspension state, retaining the current candidate on a tie. Identity only breaks the
 * initial tie deterministically; neither Wi-Fi nor any other transport receives an invented rank.
 */
internal object PhysicalNetworkSelection {
    fun select(
        candidates: Collection<PhysicalNetworkCandidate>,
        activeDefault: String?,
        currentIdentity: String?
    ): PhysicalNetworkCandidate? {
        val eligible = candidates.filter { !it.isVpn && it.hasInternet }
        eligible.firstOrNull { it.identity == activeDefault }?.let { return it }
        val bestRank = eligible.maxOfOrNull { rank(it) } ?: return null
        val best = eligible.filter { rank(it) == bestRank }
        return best.firstOrNull { it.identity == currentIdentity } ?: best.minByOrNull { it.identity }
    }

    private fun rank(candidate: PhysicalNetworkCandidate): Int =
        (if (candidate.notSuspended) 2 else 0) + (if (candidate.validated) 1 else 0)
}
