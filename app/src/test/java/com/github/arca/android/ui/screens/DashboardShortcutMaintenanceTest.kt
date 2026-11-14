package com.github.arca.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class DashboardShortcutMaintenanceTest {
    @get:Rule val compose = createComposeRule()

    private fun render(shortcut: String) {
        compose.setContent {
            MihomoTheme {
                DashboardScreen(
                    scrollState = ScrollState(0),
                    vpnState = VpnState(status = VpnState.Status.STOPPED),
                    currentMode = TunnelMode.RULE,
                    activeProfileName = "测试配置",
                    mainGroupName = "Proxy",
                    mainGroupProxies = emptyList(),
                    activeNodeName = "",
                    totalNodes = 0,
                    totalRules = 0,
                    requestMonitoringEnabled = false,
                    visibleShortcuts = setOf(shortcut),
                    shortcutOrder = listOf(shortcut),
                    onToggleVpn = {},
                    onModeSelected = {},
                    onSelectProxy = { _, _ -> },
                    onHealthCheck = {},
                    onNavigateToProfiles = {},
                    onNavigateToAppRouting = {},
                    onNavigateToLogs = {},
                    onNavigateToRules = {},
                    onNavigateToDns = {},
                    onNavigateToRequests = {},
                    onNavigateToNetworkShare = {},
                    onUpdateVisibleShortcuts = {},
                    onUpdateShortcutOrder = {}
                )
            }
        }
    }

    @Test
    fun ipShortcutIsMaintenanceOnlyAndCannotStartAScan() {
        render("ip_test")
        compose.onNodeWithTag("shortcut-ip_test").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("shortcut-panel-ip_test-sheet").assertExists()
        compose.onNodeWithTag("quick-ip-current-window").assertExists()
        compose.onNodeWithTag("quick-ip-details-window").assertExists()
        compose.onNodeWithText("IP 测试 · 维护中").assertExists()
        compose.onNodeWithTag("quick-ip-refresh").assertIsNotEnabled()
        compose.onNodeWithText("检测当前 IP").assertDoesNotExist()
        compose.onNodeWithText("重新检测 IP").assertDoesNotExist()
    }

    @Test
    fun profilePanelOffersInlineAddConfiguration() {
        render("profiles")
        compose.onNodeWithTag("shortcut-profiles").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("shortcut-panel-profiles-sheet").assertExists()
        compose.onNodeWithTag("quick-profile-add").assertIsEnabled()
        compose.onNodeWithText("添加配置：本地文件 / 订阅").assertExists()
    }
}
