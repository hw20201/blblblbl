package com.github.mihomo.android.data

import android.content.Context
import android.content.SharedPreferences

enum class PerAppProxyMode {
    DISABLED, WHITELIST, BLACKLIST
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)

    var uiStyle: UiStyle
        get() = UiStyle.fromKey(prefs.getString("ui_style", null))
        set(value) = prefs.edit().putString("ui_style", value.name).apply()
    var selectedProfileId: String?
        get() = prefs.getString("selected_profile_id", null)
        set(value) = prefs.edit().putString("selected_profile_id", value).apply()

    var tunnelMode: String
        get() = prefs.getString("tunnel_mode", "rule") ?: "rule"
        set(value) = prefs.edit().putString("tunnel_mode", value).apply()

    var tunStack: TunStack
        get() = if (!prefs.contains("tun_stack")) {
            TunStack.DEFAULT
        } else {
            TunStack.fromKey(prefs.getString("tun_stack", null)) ?: TunStack.MIXED
        }
        set(value) = prefs.edit().putString("tun_stack", value.key).apply()

    /** Writes a raw value only when it is one of the supported stack identifiers. */
    fun setTunStack(value: String): Boolean {
        val stack = TunStack.fromKey(value) ?: return false
        tunStack = stack
        return true
    }

    var perAppProxyMode: PerAppProxyMode
        get() {
            val raw = prefs.getString("per_app_proxy_mode", PerAppProxyMode.DISABLED.name)
            return runCatching { PerAppProxyMode.valueOf(raw!!) }.getOrDefault(PerAppProxyMode.DISABLED)
        }
        set(value) = prefs.edit().putString("per_app_proxy_mode", value.name).apply()

    var selectedPackages: Set<String>
        get() = prefs.getStringSet("selected_packages", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("selected_packages", value).apply()

    var bootOnStartup: Boolean
        get() = prefs.getBoolean("boot_on_startup", false)
        set(value) = prefs.edit().putBoolean("boot_on_startup", value).apply()

    /** Exposes the mixed port to the local network (LAN sharing). */
    var allowLan: Boolean
        get() = prefs.getBoolean("allow_lan", false)
        set(value) = prefs.edit().putBoolean("allow_lan", value).apply()

    /** Switches the DNS enhanced mode between fake-ip and redir-host. */
    var fakeIpEnabled: Boolean
        get() = prefs.getBoolean("fake_ip_enabled", true)
        set(value) = prefs.edit().putBoolean("fake_ip_enabled", value).apply()

    fun getSelectedNode(group: String): String? {
        return prefs.getString("sel_node_$group", null)
    }

    fun setSelectedNode(group: String, node: String) {
        prefs.edit().putString("sel_node_$group", node).apply()
    }

    fun getAllSelectedNodes(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((key, value) in prefs.all) {
            if (key.startsWith("sel_node_") && value is String && value.isNotBlank()) {
                result[key.removePrefix("sel_node_")] = value
            }
        }
        return result
    }

    // View mode persistence (TABS or LIST)
    var proxiesViewMode: String
        get() = prefs.getString("proxies_view_mode", "TABS") ?: "TABS"
        set(value) = prefs.edit().putString("proxies_view_mode", value).apply()

    // Migrate once: show newly introduced actions while preserving custom order/visibility.
    private fun migrateShortcuts() {
        if (!prefs.getBoolean("shortcuts_actions_v1", false)) {
            val existing = prefs.getStringSet("visible_shortcuts", DashboardShortcuts.defaults.toSet()).orEmpty()
            prefs.edit().putStringSet("visible_shortcuts", existing + DashboardShortcuts.added)
                .putBoolean("shortcuts_actions_v1", true).apply()
        }
    }
    var visibleShortcuts: Set<String>
        get() {
            migrateShortcuts()
            return prefs.getStringSet("visible_shortcuts", DashboardShortcuts.defaults.toSet()).orEmpty().toSet()
        }
        set(value) {
            migrateShortcuts()
            prefs.edit().putStringSet("visible_shortcuts", value).apply()
        }
    var shortcutOrder: List<String>
        get() = DashboardShortcuts.order(prefs.getString("shortcut_order", null))
        set(value) = prefs.edit().putString("shortcut_order", value.joinToString(",")).apply()
    // JavaScript Scripting & Rewrite Settings
    var scriptingEnabled: Boolean
        get() = prefs.getBoolean("scripting_enabled", true)
        set(value) = prefs.edit().putBoolean("scripting_enabled", value).apply()

    var rewriteEnabled: Boolean
        get() = prefs.getBoolean("rewrite_enabled", false)
        set(value) = prefs.edit().putBoolean("rewrite_enabled", value).apply()

    var customScripts: String
        get() = prefs.getString("custom_scripts", "") ?: ""
        set(value) = prefs.edit().putString("custom_scripts", value).apply()

    var customRewrites: String
        get() = prefs.getString("custom_rewrites", "") ?: ""
        set(value) = prefs.edit().putString("custom_rewrites", value).apply()

    var showGroupIcons: Boolean
        get() = prefs.getBoolean("show_group_icons", true)
        set(value) = prefs.edit().putBoolean("show_group_icons", value).apply()

    var nodeCardSize: String
        get() = prefs.getString("node_card_size", "STANDARD") ?: "STANDARD"
        set(value) = prefs.edit().putString("node_card_size", value).apply()

    var nodeColumns: Int
        get() = prefs.getInt("node_columns", 1)
        set(value) = prefs.edit().putInt("node_columns", value.coerceIn(1, 3)).apply()

    var logLevel: String
        get() = prefs.getString("core_log_level", "info") ?: "info"
        set(value) = prefs.edit().putString("core_log_level", value).apply()

    var themeMode: ThemeMode
        get() {
            val raw = prefs.getString("theme_mode", ThemeMode.SYSTEM.name)
            return runCatching { ThemeMode.valueOf(raw!!) }.getOrDefault(ThemeMode.SYSTEM)
        }
        set(value) = prefs.edit().putString("theme_mode", value.name).apply()

    var appLanguage: String
        get() = prefs.getString("app_language", "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString("app_language", value).apply()

    /** Master switch for haptic feedback on key interactions. */
    var hapticEnabled: Boolean
        get() = prefs.getBoolean("haptic_enabled", true)
        set(value) = prefs.edit().putBoolean("haptic_enabled", value).apply()

    /** Captures IPv6 in the TUN and lets the core carry it; off leaves IPv6 outside the VPN. */
    var ipv6Enabled: Boolean
        get() = prefs.getBoolean("ipv6_enabled", false)
        set(value) = prefs.edit().putBoolean("ipv6_enabled", value).apply()

    var tunMtu: Int
        get() = TunTuning.normalizeMtu(prefs.getInt("tun_mtu", TunTuning.DEFAULT_MTU))
        set(value) = prefs.edit().putInt("tun_mtu", TunTuning.normalizeMtu(value)).apply()

    /** Only the MIPS stack reads this; the value is still persisted for other stacks. */
    var mipsCongestionController: String
        get() = TunTuning.normalizeCongestionController(
            prefs.getString("mips_congestion_controller", TunTuning.DEFAULT_CONGESTION_CONTROLLER)
        )
        set(value) = prefs.edit()
            .putString("mips_congestion_controller", TunTuning.normalizeCongestionController(value))
            .apply()
}
