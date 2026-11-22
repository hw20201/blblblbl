package com.github.arca.android.data

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManagedConfigTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** The 17 built-in service groups, exactly as shipped with the 2026-11-22 optimization. */
    private val expectedGroups = listOf(
        "AI" to listOf("GLOBAL"),
        "YouTube" to listOf("GLOBAL"),
        "FCM" to listOf("DIRECT", "GLOBAL"),
        "Google" to listOf("GLOBAL"),
        "Telegram" to listOf("GLOBAL"),
        "GitHub" to listOf("GLOBAL"),
        "Microsoft" to listOf("GLOBAL", "DIRECT"),
        "Apple" to listOf("GLOBAL", "DIRECT"),
        "Steam" to listOf("GLOBAL", "DIRECT"),
        "TikTok" to listOf("GLOBAL"),
        "Twitter" to listOf("GLOBAL"),
        "Meta" to listOf("GLOBAL"),
        "Netflix" to listOf("GLOBAL"),
        "PikPak" to listOf("GLOBAL", "DIRECT"),
        "Spotify" to listOf("GLOBAL"),
        "Crypto" to listOf("GLOBAL"),
        "AdBlock" to listOf("REJECT", "DIRECT")
    )

    /** The 36 built-in split rules, exactly as shipped with the 2026-11-22 optimization. */
    private val expectedRules = listOf(
        "GEOSITE,PRIVATE,DIRECT",
        "AND,((NETWORK,UDP),(DST-PORT,443)),REJECT",
        "GEOSITE,CATEGORY-ADS,REJECT",
        "GEOSITE,CN,DIRECT",
        "GEOSITE,APPLE-CN,DIRECT",
        "GEOSITE,CATEGORY-GAMES-CN,DIRECT",
        "GEOIP,CN,DIRECT",
        "IP-CIDR,192.168.0.0/16,DIRECT,no-resolve",
        "IP-CIDR,10.0.0.0/8,DIRECT,no-resolve",
        "IP-CIDR,172.16.0.0/12,DIRECT,no-resolve",
        "IP-CIDR,127.0.0.0/8,DIRECT,no-resolve",
        "IP-CIDR,224.0.0.0/4,DIRECT,no-resolve",
        "IP-CIDR,240.0.0.0/4,DIRECT,no-resolve",
        "GEOSITE,CATEGORY-AI-!CN,AI",
        "GEOSITE,YOUTUBE,YouTube",
        "GEOSITE,GOOGLEFCM,FCM",
        "GEOSITE,GOOGLE,Google",
        "GEOSITE,TELEGRAM,Telegram",
        "GEOSITE,GITHUB,GitHub",
        "GEOSITE,MICROSOFT,Microsoft",
        "GEOSITE,APPLE,Apple",
        "GEOSITE,STEAM,Steam",
        "GEOSITE,TIKTOK,TikTok",
        "GEOSITE,TWITTER,Twitter",
        "GEOSITE,FACEBOOK,Meta",
        "GEOSITE,NETFLIX,Netflix",
        "GEOSITE,PIKPAK,PikPak",
        "GEOSITE,SPOTIFY,Spotify",
        "GEOSITE,CATEGORY-CRYPTOCURRENCY,Crypto",
        "GEOSITE,GEOLOCATION-!CN,GLOBAL",
        "GEOIP,google,Google",
        "GEOIP,telegram,Telegram",
        "GEOIP,facebook,Meta",
        "GEOIP,netflix,Netflix",
        "GEOIP,twitter,Twitter",
        "MATCH,GLOBAL"
    )

    @Before fun reset() {
        ConfigManager.getProfilesDir(context).listFiles()?.filter { it.name.startsWith(ConfigManager.DEFAULT_PROFILE_ID) }?.forEach { it.delete() }
        context.getSharedPreferences("profiles_meta", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE).edit().remove("managed_config_v1").commit()
        ConfigManager.invalidateProfilesCache()
    }

    @Test fun defaultMatchesBuiltinGroupsAndRules() {
        val config = ManagedConfig.default()
        assertEquals(expectedGroups, config.serviceGroups())
        assertEquals(expectedRules, config.splitRules())
        assertEquals(expectedGroups, ProfileNodeEditor.defaultServiceGroups)
        assertEquals(expectedRules, ProfileNodeEditor.defaultSplitRules)
        val template = ConfigManager.defaultProfileTemplate()
        assertTrue(template.contains("  - name: AdBlock\n    type: select\n    proxies:\n      - REJECT\n      - DIRECT"))
        assertTrue(template.contains("          - GEOSITE,CATEGORY-AI-!CN,AI"))
        assertTrue(template.contains("          - MATCH,GLOBAL"))
    }

    @Test fun disablingGroupRemovesItsRulesAndGroup() {
        val config = ManagedConfig.default()
            .withService("TikTok", ManagedServiceOption(enabled = false, target = "GLOBAL"))
            .withService("Google", ManagedServiceOption(enabled = false, target = "GLOBAL"))
        assertEquals(15, config.serviceGroups().size)
        assertFalse(config.serviceGroups().any { it.first == "TikTok" })
        assertFalse(config.serviceGroups().any { it.first == "Google" })
        val rules = config.splitRules()
        assertEquals(33, rules.size)
        assertFalse(rules.contains("GEOSITE,TIKTOK,TikTok"))
        assertFalse(rules.contains("GEOSITE,GOOGLE,Google"))
        assertFalse(rules.contains("GEOIP,google,Google"))
        assertTrue(rules.contains("GEOSITE,CATEGORY-AI-!CN,AI"))
        assertTrue(rules.contains("MATCH,GLOBAL"))
    }

    @Test fun targetChangeRetargetsRulesAndGroup() {
        val config = ManagedConfig.default()
            .withService("FCM", ManagedServiceOption(target = "GLOBAL"))
            .withService("AdBlock", ManagedServiceOption(target = "DIRECT"))
        // The rule still routes to the FCM group; only the group's exit changes.
        assertTrue(config.splitRules().contains("GEOSITE,GOOGLEFCM,FCM"))
        assertFalse(config.splitRules().contains("GEOSITE,GOOGLEFCM,GLOBAL"))
        assertTrue(config.splitRules().contains("GEOSITE,CATEGORY-ADS,DIRECT"))
        assertEquals(listOf("GLOBAL", "DIRECT"), config.serviceGroups().first { it.first == "FCM" }.second)
        assertEquals(listOf("DIRECT", "REJECT"), config.serviceGroups().first { it.first == "AdBlock" }.second)
    }

    @Test fun togglesGateQuicAndAdsRules() {
        val noQuic = ManagedConfig.default().copy(blockQuic = false)
        assertEquals(35, noQuic.splitRules().size)
        assertFalse(noQuic.splitRules().any { it.startsWith("AND,((NETWORK,UDP)") })
        val noAds = ManagedConfig.default().copy(blockAds = false)
        assertEquals(35, noAds.splitRules().size)
        assertFalse(noAds.splitRules().any { it.contains("CATEGORY-ADS") })
        val neither = ManagedConfig.default().copy(blockQuic = false, blockAds = false)
        assertEquals(34, neither.splitRules().size)
    }

    @Test fun applyRewritesDefaultAndAcceptsPreviousConfig() {
        val default = ConfigManager.getOrCreateDefaultProfile(context)
        val fresh = default.file.readText()
        val first = ManagedConfig.default()
            .withService("TikTok", ManagedServiceOption(enabled = false, target = "GLOBAL"))
        val rewritten = ProfileNodeEditor.applyManagedConfig(first, fresh)
        assertNotNull(rewritten)
        assertFalse(rewritten!!.contains("TIKTOK"))
        assertFalse(rewritten.contains("name: TikTok"))
        assertTrue(rewritten.contains("GEOSITE,CATEGORY-AI-!CN,AI"))
        val second = first.withService("FCM", ManagedServiceOption(target = "GLOBAL"))
        val rewrittenAgain = ProfileNodeEditor.applyManagedConfig(second, rewritten, first)
        assertNotNull(rewrittenAgain)
        assertTrue(rewrittenAgain!!.contains("GEOSITE,GOOGLEFCM,FCM"))
        assertTrue(rewrittenAgain.contains("  - name: FCM\n    type: select\n    proxies:\n      - GLOBAL\n      - DIRECT"))
        assertFalse(rewrittenAgain.contains("TIKTOK"))
    }

    @Test fun applyKeepsNodesWhenUpgradingRound1() {
        val round1 = """
            proxies:
              - {name: Old, type: socks5, server: example.com, port: 1080}
            proxy-groups:
              - {name: GLOBAL, type: select, proxies: [Old, DIRECT]}
            rules:
              - GEOSITE,CN,DIRECT
              - GEOIP,CN,DIRECT
              - IP-CIDR,192.168.0.0/16,DIRECT,no-resolve
              - IP-CIDR,10.0.0.0/8,DIRECT,no-resolve
              - IP-CIDR,172.16.0.0/12,DIRECT,no-resolve
              - IP-CIDR,127.0.0.0/8,DIRECT,no-resolve
              - IP-CIDR,224.0.0.0/4,DIRECT,no-resolve
              - IP-CIDR,240.0.0.0/4,DIRECT,no-resolve
              - MATCH,GLOBAL
        """.trimIndent()
        val rewritten = ProfileNodeEditor.applyManagedConfig(ManagedConfig.default(), round1)
        assertNotNull(rewritten)
        assertTrue(rewritten!!.contains("Old"))
        assertTrue(rewritten.contains("GEOSITE,CATEGORY-AI-!CN,AI"))
        assertTrue(rewritten.contains("name: AdBlock"))
    }

    @Test fun applyRefusesHandEditedRulesAndMissingGlobal() {
        val default = ConfigManager.getOrCreateDefaultProfile(context)
        val edited = default.file.readText().replace(
            "          - MATCH,GLOBAL",
            "          - DOMAIN,example.com,DIRECT\n          - MATCH,GLOBAL"
        )
        assertNull(ProfileNodeEditor.applyManagedConfig(ManagedConfig.default(), edited))
        assertNull(
            ProfileNodeEditor.applyManagedConfig(
                ManagedConfig.default(), "proxies: []\nrules:\n  - MATCH,DIRECT\n"
            )
        )
    }

    @Test fun managedConfigPersistsRoundTrip() {
        val manager = SettingsManager(context)
        assertEquals(ManagedConfig.default(), manager.managedConfig)
        val modified = ManagedConfig.default()
            .withService("TikTok", ManagedServiceOption(enabled = false, target = "GLOBAL"))
            .copy(blockQuic = false)
        manager.managedConfig = modified
        assertEquals(modified, SettingsManager(context).managedConfig)
        context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE).edit()
            .putString("managed_config_v1", "{not json").commit()
        assertEquals(ManagedConfig.default(), SettingsManager(context).managedConfig)
    }

    @Test fun storedConfigIsUsedWhenUpgradingLegacyDefault() {
        SettingsManager(context).managedConfig = ManagedConfig.default()
            .withService("TikTok", ManagedServiceOption(enabled = false, target = "GLOBAL"))
        val dir = ConfigManager.getProfilesDir(context)
        dir.resolve("${ConfigManager.DEFAULT_PROFILE_ID}.yaml").writeText(
            "proxies: []\nproxy-groups:\n  - name: GLOBAL\n    type: select\n    proxies:\n      - DIRECT\nrules:\n  - MATCH,DIRECT\n"
        )
        ConfigManager.invalidateProfilesCache()
        val loaded = ConfigManager.getOrCreateDefaultProfile(context)
        val content = loaded.file.readText()
        assertFalse(content.contains("TIKTOK"))
        assertFalse(content.contains("name: TikTok"))
        assertTrue(content.contains("GEOSITE,CATEGORY-AI-!CN,AI"))
        assertTrue(content.contains("MATCH,GLOBAL"))
    }

    @Test fun upgradeLegacyDefaultRulesUsesGivenConfig() {
        val legacy = "proxies: []\nproxy-groups:\n  - name: GLOBAL\n    type: select\n    proxies:\n      - DIRECT\nrules:\n  - MATCH,DIRECT\n"
        val config = ManagedConfig.default()
            .withService("TikTok", ManagedServiceOption(enabled = false, target = "GLOBAL"))
        val upgraded = ProfileNodeEditor.upgradeLegacyDefaultRules(legacy, config)
        assertNotNull(upgraded)
        assertFalse(upgraded!!.contains("TIKTOK"))
        assertTrue(upgraded.contains("GEOSITE,CATEGORY-AI-!CN,AI"))
        assertTrue(upgraded.contains("MATCH,GLOBAL"))
    }
}
