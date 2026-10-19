package com.github.mihomo.android.data

/** Independent of light/dark mode; persisted without recreating the VPN or screen. */
enum class UiStyle {
    CUPERTINO, MIUIX;

    fun toggled(): UiStyle = if (this == CUPERTINO) MIUIX else CUPERTINO

    companion object {
        fun fromKey(value: String?): UiStyle = entries.firstOrNull { it.name == value } ?: CUPERTINO
    }
}

/** Append new actions without resetting the user's existing order or hidden actions. */
object DashboardShortcuts {
    val added = listOf("vpn", "ip_test", "ui_style")
    val defaults = added + listOf("nodes", "profiles", "request_logs", "dns", "rules", "routing", "plugins", "network_share")

    fun order(raw: String?): List<String> = if (raw.isNullOrBlank()) defaults else
        (raw.split(",").filter { it.isNotBlank() } + added).distinct()
}
