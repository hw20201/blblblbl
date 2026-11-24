package com.github.arca.android.data

import android.content.Context
import android.content.SharedPreferences
import com.github.arca.android.ui.components.ShortcutTouchVariant
import com.github.arca.android.ui.glass.GlassOptics
import com.github.arca.android.ui.glass.GlassSettings
import com.github.arca.android.ui.glass.GlassSurfaceCategory
import com.github.arca.android.ui.glass.ShortcutGlassLevel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

enum class PerAppProxyMode {
    DISABLED, WHITELIST, BLACKLIST
}

data class LanCredentials(val username: String, val password: String) { val encoded: String get() = "$username:$password" }
private const val KEY_LAN_USER = "lan_auth_user"
private const val KEY_LAN_PASSWORD = "lan_auth_password"
private const val LAN_PASSWORD_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
private val lanCredentialLock = Any()
private val lanSecureRandom = java.security.SecureRandom()
enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

/** Preferences retired with the old built-in theme/background palette picker. */
private const val LEGACY_KEY_GLASS_PALETTE = "glass_palette"
private const val LEGACY_KEY_SAVED_GLASS_PALETTES = "glass_saved_palettes_v1"

private val glassSettingsJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Managed split config lives under one typed JSON preference. */
private val managedConfigJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class SettingsManager(context: Context) {
    private companion object {
        const val KEY_GLASS_SETTINGS = "glass_settings_v1"
        const val KEY_MANAGED_CONFIG = "managed_config_v1"
        const val KEY_MIXED_PROXY_PORT = "local_mixed_proxy_port"
        const val KEY_DNS_PROXY_PORT = "local_dns_proxy_port"
        const val KEY_SHORTCUT_GLASS_LEVEL = "shortcut_glass_level"
        const val KEY_SHORTCUT_GLASS_REFRACTION = "shortcut_glass_refraction_dp"
        const val KEY_SHORTCUT_GLASS_DISPERSION = "shortcut_glass_dispersion_dp"
        const val KEY_SHORTCUT_GLASS_EDGE_WIDTH = "shortcut_glass_edge_width_dp"
        const val KEY_SHORTCUT_GLASS_EDGE_LIGHT = "shortcut_glass_edge_light"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)

    init {
        // Remove retired palette and wallpaper preferences without touching active settings.
        val editor = prefs.edit()
            .remove(LEGACY_KEY_GLASS_PALETTE)
            .remove(LEGACY_KEY_SAVED_GLASS_PALETTES)

        prefs.all.keys.filter { it.startsWith("glass_wallpaper_") }.forEach(editor::remove)
        editor.apply()
    }

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

    /** Invalid legacy values fall back together so the two listeners can never collide. */
    val localProxyPorts: LocalProxyPorts
        get() {
            val ports = LocalProxyPorts(
                mixedPort = prefs.getInt(KEY_MIXED_PROXY_PORT, DEFAULT_LOCAL_MIXED_PROXY_PORT),
                dnsPort = prefs.getInt(KEY_DNS_PROXY_PORT, DEFAULT_LOCAL_DNS_PROXY_PORT)
            )
            return ports.takeIf(LocalProxyPorts::isValid) ?: LocalProxyPorts()
        }

    /** Persist both listener ports in one SharedPreferences transaction; reject invalid pairs. */
    fun setLocalProxyPorts(ports: LocalProxyPorts): Boolean {
        if (!ports.isValid()) return false
        return prefs.edit()
            .putInt(KEY_MIXED_PROXY_PORT, ports.mixedPort)
            .putInt(KEY_DNS_PROXY_PORT, ports.dnsPort)
            .commit()
    }

    fun lanCredentials(): LanCredentials = synchronized(lanCredentialLock) {
        val user = prefs.getString(KEY_LAN_USER, null)
        val password = prefs.getString(KEY_LAN_PASSWORD, null)
        if (!user.isNullOrBlank() && !password.isNullOrBlank()) return@synchronized LanCredentials(user, password)
        regenerateLanCredentialsLocked()
    }
    fun regenerateLanCredentials(): LanCredentials = synchronized(lanCredentialLock) { regenerateLanCredentialsLocked() }
    private fun regenerateLanCredentialsLocked(): LanCredentials {
        val password = buildString { repeat(16) { append(LAN_PASSWORD_ALPHABET[lanSecureRandom.nextInt(LAN_PASSWORD_ALPHABET.length)]) } }
        // commit() makes generated credentials durable before callers can start the shared listener.
        check(prefs.edit().putString(KEY_LAN_USER, "nexus").putString(KEY_LAN_PASSWORD, password).commit()) { "Unable to persist LAN credentials" }
        return LanCredentials("nexus", password)
    }
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

    fun renameSelectedNodes(groups: Collection<String>, oldName: String, newName: String) {
        if (oldName == newName) return
        groups.forEach { group ->
            if (getSelectedNode(group) == oldName) {
                setSelectedNode(group, newName)
            }
        }
    }

    // View mode persistence (TABS or LIST)
    var proxiesViewMode: String
        get() = prefs.getString("proxies_view_mode", "TABS") ?: "TABS"
        set(value) = prefs.edit().putString("proxies_view_mode", value).apply()

    // Migrate once: keep custom visibility/order, but move untouched v76 defaults to the requested v77 set.
    private fun migrateShortcuts() {
        val editor = prefs.edit()
        var changed = false
        val hasStoredVisible = prefs.contains("visible_shortcuts")
        var visible = prefs.getStringSet("visible_shortcuts", DashboardShortcuts.defaults.toSet()).orEmpty().toSet()
        if (!prefs.getBoolean("shortcuts_actions_v1", false)) {
            visible = visible + DashboardShortcuts.added
            editor.putBoolean("shortcuts_actions_v1", true)
            changed = true
        }
        if (!prefs.getBoolean("traffic_shortcut_v1", false)) {
            if (hasStoredVisible) visible = visible + "traffic"
            editor.putBoolean("traffic_shortcut_v1", true)
            changed = true
        }
        if (!prefs.getBoolean("shortcut_defaults_v77", false)) {
            val oldDefaults = DashboardShortcuts.v76Defaults.toSet()
            if (hasStoredVisible && visible == oldDefaults) {
                visible = DashboardShortcuts.defaults.toSet()
            }
            val savedOrder = prefs.getString("shortcut_order", null)
            if (savedOrder != null) {
                val order = savedOrder.split(",").filter { it.isNotBlank() }.distinct()
                if (order.toSet() == oldDefaults) {
                    editor.putString("shortcut_order", DashboardShortcuts.defaults.joinToString(","))
                }
            }
            editor.putBoolean("shortcut_defaults_v77", true)
            changed = true
        }
        if (changed) editor.putStringSet("visible_shortcuts", visible).apply()
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
            val raw = prefs.getString("theme_mode", ThemeMode.DARK.name)
            return runCatching { ThemeMode.valueOf(raw!!) }.getOrDefault(ThemeMode.SYSTEM)
        }
        set(value) = prefs.edit().putString("theme_mode", value.name).apply()

    var appLanguage: String
        get() = prefs.getString("app_language", "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString("app_language", value).apply()

    /** Glass effects live under one typed JSON preference; legacy split keys migrate on first write. */
    var glassSettings: GlassSettings
        get() {
            val raw = prefs.getString(KEY_GLASS_SETTINGS, null)
            if (raw.isNullOrBlank()) return legacyGlassSettings()
            return runCatching { glassSettingsJson.decodeFromString<GlassSettings>(raw).clamped() }
                .getOrElse { legacyGlassSettings() }
        }
        set(value) {
            val safe = value.clamped()
            prefs.edit()
                .putString(KEY_GLASS_SETTINGS, glassSettingsJson.encodeToString(safe))
                .remove(KEY_SHORTCUT_GLASS_LEVEL)
                .remove(KEY_SHORTCUT_GLASS_REFRACTION)
                .remove(KEY_SHORTCUT_GLASS_DISPERSION)
                .remove(KEY_SHORTCUT_GLASS_EDGE_WIDTH)
                .remove(KEY_SHORTCUT_GLASS_EDGE_LIGHT)
                .apply()
        }

    fun resetGlassSettings() { glassSettings = GlassSettings.DEFAULT }

    /** Managed split config for the built-in default profile; corrupt or absent stored JSON falls back to the default. */
    var managedConfig: ManagedConfig
        get() {
            val raw = prefs.getString(KEY_MANAGED_CONFIG, null)
            if (raw.isNullOrBlank()) return ManagedConfig.default()
            return runCatching { managedConfigJson.decodeFromString<ManagedConfig>(raw) }
                .getOrElse { ManagedConfig.default() }
        }
        set(value) = prefs.edit()
            .putString(KEY_MANAGED_CONFIG, managedConfigJson.encodeToString(value))
            .apply()

    private fun legacyGlassSettings(): GlassSettings {
        val hasOldShortcutOptics = listOf(
            KEY_SHORTCUT_GLASS_REFRACTION, KEY_SHORTCUT_GLASS_DISPERSION,
            KEY_SHORTCUT_GLASS_EDGE_WIDTH, KEY_SHORTCUT_GLASS_EDGE_LIGHT
        ).any(prefs::contains)
        val oldOptics = if (hasOldShortcutOptics) GlassOptics(
            refractionDp = prefs.getFloat(KEY_SHORTCUT_GLASS_REFRACTION, GlassOptics.DEFAULT_REFRACTION_DP),
            dispersionDp = prefs.getFloat(KEY_SHORTCUT_GLASS_DISPERSION, GlassOptics.DEFAULT_DISPERSION_DP),
            edgeWidthDp = prefs.getFloat(KEY_SHORTCUT_GLASS_EDGE_WIDTH, GlassOptics.DEFAULT_EDGE_WIDTH_DP),
            edgeLightMultiplier = prefs.getFloat(KEY_SHORTCUT_GLASS_EDGE_LIGHT, GlassOptics.DEFAULT_EDGE_LIGHT_MULTIPLIER)
        ).clamped() else null
        val level = ShortcutGlassLevel.fromPreference(prefs.getString(KEY_SHORTCUT_GLASS_LEVEL, null))
        return GlassSettings.DEFAULT.copy(
            surfaceOverrides = oldOptics?.let { mapOf(GlassSurfaceCategory.SHORTCUT_CARDS to it) } ?: emptyMap(),
            shortcutGlassLevel = level
        )
    }

    /** Selected dashboard shortcut touch motion style. Invalid stored values fall back safely. */
    var shortcutTouchVariant: ShortcutTouchVariant
        get() = runCatching {
            ShortcutTouchVariant.valueOf(prefs.getString("shortcut_touch_variant", ShortcutTouchVariant.GentleFloat.name)!!)
        }.getOrDefault(ShortcutTouchVariant.GentleFloat)
        set(value) = prefs.edit().putString("shortcut_touch_variant", value.name).apply()

    /** Shared panel timing; absent or retired preference values use the middle speed. */
    var panelAnimationSpeed: PanelAnimationSpeed
        get() = PanelAnimationSpeed.fromPreference(prefs.getString("panel_animation_speed", null))
        set(value) = prefs.edit().putString("panel_animation_speed", value.name).apply()

    /** Master switch for haptic feedback on key interactions. */
    var hapticEnabled: Boolean
        get() = prefs.getBoolean("haptic_enabled", true)
        set(value) = prefs.edit().putBoolean("haptic_enabled", value).apply()

    /** Captures IPv6 in the TUN and lets the core carry it; off leaves IPv6 outside the VPN. */
    var ipv6Enabled: Boolean
        get() = prefs.getBoolean("ipv6_enabled", false)
        set(value) = prefs.edit().putBoolean("ipv6_enabled", value).apply()

    /** Adds Mihomo's unified-delay option to the generated runtime config. */
    var unifiedDelayEnabled: Boolean
        get() = prefs.getBoolean("unified_delay_enabled", false)
        set(value) = prefs.edit().putBoolean("unified_delay_enabled", value).apply()

    /** Apply a newly selected profile to the running Mihomo core without rebuilding the VPN tunnel. */
    var profileHotSwitchEnabled: Boolean
        get() = prefs.getBoolean("profile_hot_switch_enabled", true)
        set(value) = prefs.edit().putBoolean("profile_hot_switch_enabled", value).apply()

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
