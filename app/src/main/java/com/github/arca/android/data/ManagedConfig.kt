package com.github.arca.android.data

import kotlinx.serialization.Serializable

/**
 * One service group of the managed split rules, after BettBox's managed.js.
 * [options] are the proxies the group can point at, in order; the first is
 * the default target. [geoip] is set only when the bundled geoip.metadb has
 * that service code — the other services have no IP list bundled, their
 * domain rules cover them.
 */
data class ManagedServiceDef(
    val name: String,
    val geosite: String,
    val geoip: String?,
    val options: List<String>,
    val subtitle: String
)

/** Per-service choice in the managed config: on/off plus which target it routes to. */
@Serializable
data class ManagedServiceOption(
    val enabled: Boolean = true,
    val target: String = "GLOBAL"
)

/** The default per-service choices, one entry per service group. */
internal fun defaultManagedServices(): Map<String, ManagedServiceOption> =
    ManagedConfig.SERVICE_DEFS.associate { it.name to ManagedServiceOption(target = it.options.first()) }

/**
 * Managed (BettBox-style) split configuration for the built-in default
 * profile: per-service on/off + target, QUIC blocking and ad blocking.
 * [default] reproduces exactly the built-in 17 groups and 36 rules that
 * shipped with the 2026-11-22 optimization.
 */
@Serializable
data class ManagedConfig(
    val services: Map<String, ManagedServiceOption> = defaultManagedServices(),
    val blockQuic: Boolean = true,
    val blockAds: Boolean = true
) {
    companion object {
        /** Service groups in BettBox's managed order; AdBlock is last. */
        val SERVICE_DEFS = listOf(
            ManagedServiceDef("AI", "CATEGORY-AI-!CN", null, listOf("GLOBAL"), "AI 对话、推理与搜索"),
            ManagedServiceDef("YouTube", "YOUTUBE", null, listOf("GLOBAL"), "YouTube 视频"),
            ManagedServiceDef("FCM", "GOOGLEFCM", null, listOf("DIRECT", "GLOBAL"), "Google 推送通知"),
            ManagedServiceDef("Google", "GOOGLE", "google", listOf("GLOBAL"), "Google 搜索、地图与服务"),
            ManagedServiceDef("Telegram", "TELEGRAM", "telegram", listOf("GLOBAL"), "Telegram 消息"),
            ManagedServiceDef("GitHub", "GITHUB", null, listOf("GLOBAL"), "GitHub 代码托管"),
            ManagedServiceDef("Microsoft", "MICROSOFT", null, listOf("GLOBAL", "DIRECT"), "微软服务与更新"),
            ManagedServiceDef("Apple", "APPLE", null, listOf("GLOBAL", "DIRECT"), "苹果服务与更新"),
            ManagedServiceDef("Steam", "STEAM", null, listOf("GLOBAL", "DIRECT"), "Steam 游戏与下载"),
            ManagedServiceDef("TikTok", "TIKTOK", null, listOf("GLOBAL"), "TikTok 短视频"),
            ManagedServiceDef("Twitter", "TWITTER", "twitter", listOf("GLOBAL"), "X（Twitter）社交"),
            ManagedServiceDef("Meta", "FACEBOOK", "facebook", listOf("GLOBAL"), "Facebook、Instagram"),
            ManagedServiceDef("Netflix", "NETFLIX", "netflix", listOf("GLOBAL"), "Netflix 流媒体"),
            ManagedServiceDef("PikPak", "PIKPAK", null, listOf("GLOBAL", "DIRECT"), "PikPak 网盘"),
            ManagedServiceDef("Spotify", "SPOTIFY", null, listOf("GLOBAL"), "Spotify 音乐"),
            ManagedServiceDef("Crypto", "CATEGORY-CRYPTOCURRENCY", null, listOf("GLOBAL"), "加密货币行情与交易"),
            ManagedServiceDef("AdBlock", "CATEGORY-ADS", null, listOf("REJECT", "DIRECT"), "广告与追踪域名")
        )

        /** All managed service group names. */
        val SERVICE_NAMES: Set<String> = SERVICE_DEFS.map { it.name }.toSet()

        /** GEOIP service rules keep the bundled order, not the service-group order. */
        private val GEOIP_ORDER = listOf("Google", "Telegram", "Meta", "Netflix", "Twitter")

        fun default(): ManagedConfig = ManagedConfig()
    }

    /** The stored choice for [name], with unknown names and invalid targets folded back to the default. */
    fun serviceOption(name: String): ManagedServiceOption {
        val def = SERVICE_DEFS.firstOrNull { it.name == name }
            ?: return ManagedServiceOption(target = "GLOBAL")
        val stored = services[name] ?: return ManagedServiceOption(target = def.options.first())
        val target = stored.target.takeIf { it in def.options } ?: def.options.first()
        return ManagedServiceOption(enabled = stored.enabled, target = target)
    }

    /** A copy with one service's choice replaced. */
    fun withService(name: String, option: ManagedServiceOption): ManagedConfig =
        copy(services = services + (name to option))

    /** Enabled service groups as (name, proxies); the selected target comes first. */
    internal fun serviceGroups(): List<Pair<String, List<String>>> =
        SERVICE_DEFS.mapNotNull { def ->
            val option = serviceOption(def.name)
            if (!option.enabled) return@mapNotNull null
            val proxies = listOf(option.target) + def.options.filter { it != option.target }
            def.name to proxies
        }

    /** The enabled service groups rendered as a YAML block for the default profile template. */
    internal fun serviceGroupsYaml(): String = serviceGroups().joinToString("\n") { (name, proxies) ->
        "  - name: $name\n    type: select\n    proxies:\n" +
            proxies.joinToString("\n") { "      - $it" }
    }

    /**
     * Full domestic-direct + service split, in BettBox's rule order: private,
     * QUIC block, ads, domestic direct, per-service domains, non-CN
     * catch-all, per-service IPs, match.
     */
    internal fun splitRules(): List<String> = buildList {
        add("GEOSITE,PRIVATE,DIRECT")
        if (blockQuic) add("AND,((NETWORK,UDP),(DST-PORT,443)),REJECT")
        if (blockAds) add("GEOSITE,CATEGORY-ADS,${serviceOption("AdBlock").target}")
        add("GEOSITE,CN,DIRECT")
        add("GEOSITE,APPLE-CN,DIRECT")
        add("GEOSITE,CATEGORY-GAMES-CN,DIRECT")
        add("GEOIP,CN,DIRECT")
        add("IP-CIDR,192.168.0.0/16,DIRECT,no-resolve")
        add("IP-CIDR,10.0.0.0/8,DIRECT,no-resolve")
        add("IP-CIDR,172.16.0.0/12,DIRECT,no-resolve")
        add("IP-CIDR,127.0.0.0/8,DIRECT,no-resolve")
        add("IP-CIDR,224.0.0.0/4,DIRECT,no-resolve")
        add("IP-CIDR,240.0.0.0/4,DIRECT,no-resolve")
        for (def in SERVICE_DEFS) {
            if (def.name == "AdBlock") continue
            val option = serviceOption(def.name)
            // The rule points at the service group itself; the group's target
            // (GLOBAL / DIRECT / ...) lives in the group's proxy list, so
            // switching it in the UI actually moves the traffic.
            if (option.enabled) add("GEOSITE,${def.geosite},${def.name}")
        }
        add("GEOSITE,GEOLOCATION-!CN,GLOBAL")
        for (def in SERVICE_DEFS.filter { it.geoip != null }.sortedBy { GEOIP_ORDER.indexOf(it.name) }) {
            val option = serviceOption(def.name)
            if (option.enabled) add("GEOIP,${def.geoip},${def.name}")
        }
        add("MATCH,GLOBAL")
    }
}
