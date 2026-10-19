package com.github.mihomo.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.data.UiStyle
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DashboardActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun vpnShortcutCallsLiveActionAndUiSwitchKeepsDashboardState() {
        val status = mutableStateOf(VpnState.Status.STOPPED)
        val style = mutableStateOf(UiStyle.CUPERTINO)
        var starts = 0
        var stops = 0
        val actions = listOf("vpn", "ui_style")
        compose.setContent {
            MihomoTheme(uiStyle = style.value) {
                DashboardScreen(
                    scrollState = ScrollState(0), vpnState = VpnState(status = status.value),
                    currentMode = TunnelMode.RULE, activeProfileName = "测试配置", mainGroupName = "",
                    mainGroupProxies = emptyList(), activeNodeName = "", totalNodes = 0, totalRules = 0,
                    requestMonitoringEnabled = false, visibleShortcuts = actions.toSet(), shortcutOrder = actions,
                    onToggleVpn = {
                        if (status.value == VpnState.Status.STOPPED) { starts++; status.value = VpnState.Status.RUNNING }
                        else { stops++; status.value = VpnState.Status.STOPPED }
                    },
                    onModeSelected = {}, onSelectProxy = { _, _ -> }, onHealthCheck = {},
                    onNavigateToProfiles = {}, onNavigateToAppRouting = {}, onNavigateToLogs = {},
                    onNavigateToRules = {}, onNavigateToDns = {}, onNavigateToRequests = {},
                    onNavigateToNetworkShare = {}, onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = {},
                    uiStyle = style.value, onToggleUiStyle = { style.value = style.value.toggled() }
                )
            }
        }
        compose.onNodeWithText("启动 VPN").performClick()
        compose.onNodeWithText("停止 VPN").assertExists()
        compose.onNodeWithText("切换 UI").performClick()
        compose.onNodeWithText("Miuix → Cupertino").assertExists()
        compose.onNodeWithText("停止 VPN").performClick()
        compose.onNodeWithText("启动 VPN").assertExists()
        compose.onNodeWithText("切换 UI").performClick()
        compose.onNodeWithText("Cupertino → Miuix").assertExists()
        compose.runOnIdle { status.value = VpnState.Status.STOPPING }
        compose.onNodeWithText("正在停止").performClick()
        compose.runOnIdle { assertEquals(1, starts); assertEquals(1, stops) }
    }
}
