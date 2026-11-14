package com.github.arca.android.data

import android.net.Uri

/** A subscription offered by a panel's "one-click import" button. */
data class SubscriptionLink(val url: String, val name: String)

/**
 * Parses `clash://install-config?url=…&name=…` and the `clashmeta://` / `mihomo://` variants
 * used by airport panels. The embedded URL must pass the same HTTPS policy as manual adds;
 * anything else returns null so a malicious page cannot slip in a plain-HTTP or file URL.
 */
object SubscriptionLinkParser {
    val SCHEMES = setOf("clash", "clashmeta", "mihomo")
    private const val HOST = "install-config"

    fun parse(uri: Uri?): SubscriptionLink? {
        uri ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in SCHEMES) return null
        if (!uri.host.equals(HOST, ignoreCase = true)) return null
        val rawUrl = runCatching { uri.getQueryParameter("url") }.getOrNull()?.trim().orEmpty()
        return parseParts(rawUrl, runCatching { uri.getQueryParameter("name") }.getOrNull())
    }

    /** Pure part, unit-testable without Android's Uri. */
    internal fun parseParts(rawUrl: String, rawName: String?): SubscriptionLink? {
        if (rawUrl.isBlank()) return null
        val ok = runCatching { requireSecureRemoteUrl(rawUrl) }.isSuccess
        if (!ok) return null
        return SubscriptionLink(url = rawUrl, name = rawName?.trim().orEmpty().take(64))
    }
}
