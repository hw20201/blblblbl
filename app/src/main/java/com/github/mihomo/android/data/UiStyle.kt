package com.github.mihomo.android.data
/** Cupertino is the sole supported interface style. */
enum class UiStyle { CUPERTINO;
    fun toggled(): UiStyle = CUPERTINO
    companion object { @Suppress("UnusedParameter")
        fun fromKey(value: String?): UiStyle = CUPERTINO }
}
/** Append new actions without resetting the user's existing order or hidden actions. */
object DashboardShortcuts {
    val added = listOf("vpn", "ip_test")
    val defaults = added + listOf("nodes", "profiles", "request_logs", "dns", "rules", "routing", "plugins", "network_share")
    fun order(raw: String?): List<String> = if (raw.isNullOrBlank()) defaults else (raw.split(",").filter { it.isNotBlank() } + added).distinct()
}
