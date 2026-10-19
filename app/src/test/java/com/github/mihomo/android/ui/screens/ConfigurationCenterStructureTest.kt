package com.github.mihomo.android.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConfigurationCenterStructureTest {
    private fun source(relativePath: String): String {
        val candidates = listOf(File("src/main/java/$relativePath"), File("app/src/main/java/$relativePath"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Could not locate source: $relativePath")
    }

    @Test
    fun settingsHasSingleConfigurationManagementEntry() {
        val settings = source("com/github/mihomo/android/ui/screens/SettingsScreen.kt")
        assertTrue(settings.contains("title = \"配置中心\""))
        assertTrue(settings.contains("onClick = onNavigateToConfigurationCenter"))
        assertTrue(!settings.contains("onNavigateToScripts"))
        assertTrue(!settings.contains("onNavigateToProfiles"))
        assertTrue(!settings.contains("onNavigateToAppRouting"))
    }

    @Test
    fun centerGroupsExistingConfigurationScriptAndRoutingDestinations() {
        val center = source("com/github/mihomo/android/ui/screens/ConfigurationCenterScreen.kt")
        assertTrue(center.contains("配置 / 订阅"))
        assertTrue(center.contains("脚本"))
        assertTrue(center.contains("规则 / 路由资源"))
        assertTrue(center.contains("onNavigateToProfiles"))
        assertTrue(center.contains("onNavigateToScripts"))
        assertTrue(center.contains("onNavigateToRules"))
        assertTrue(center.contains("onNavigateToAppRouting"))
    }
}
