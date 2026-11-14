package com.github.arca.android.data

object DashboardShortcuts {
    val added = listOf("vpn", "ip_test")
    val defaults = listOf("vpn", "ip_test", "nodes", "profiles", "request_logs", "dns", "rules", "routing")
    /** The default set shipped by v76, used only for the one-time default migration. */
    val v76Defaults = listOf("vpn", "ip_test", "traffic", "nodes", "profiles", "request_logs", "dns", "rules", "routing", "plugins", "network_share")
    fun order(raw: String?): List<String> = if (raw.isNullOrBlank()) defaults else (raw.split(",").filter { it.isNotBlank() } + added).distinct()
}
