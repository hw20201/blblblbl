package com.github.mihomo.android.data

object DashboardShortcuts {
    val added = listOf("vpn", "ip_test")
    val defaults = added + listOf("nodes", "profiles", "request_logs", "dns", "rules", "routing", "plugins", "network_share")
    fun order(raw: String?): List<String> = if (raw.isNullOrBlank()) defaults else (raw.split(",").filter { it.isNotBlank() } + added).distinct()
}
