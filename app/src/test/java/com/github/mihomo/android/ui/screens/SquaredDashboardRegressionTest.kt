package com.github.mihomo.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.data.UiStyle
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.theme.MihomoTheme
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class SquaredDashboardRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun approvedShortcutsStayVisibleAndActionsWorkInBothStylesAtCompactWidth() {
        val style = mutableStateOf(UiStyle.CUPERTINO)
        val vpn = mutableStateOf(VpnState.Status.STOPPED)
        val viewportWidthPx = mutableStateOf(0f)
        var vpnToggles = 0
        var styleToggles = 0
        val shortcuts = listOf("vpn", "ip_test", "ui_style", "nodes", "profiles", "core_logs")

        compose.setContent {
            MihomoTheme(uiStyle = style.value) {
                // Explicit 360dp content width guards the narrow-phone dashboard layout.
                viewportWidthPx.value = with(LocalDensity.current) { 360.dp.toPx() }
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    DashboardScreen(
                        scrollState = ScrollState(0),
                        vpnState = VpnState(status = vpn.value),
                        currentMode = TunnelMode.RULE,
                        activeProfileName = "测试配置名称",
                        mainGroupName = "",
                        mainGroupProxies = emptyList(),
                        activeNodeName = "",
                        totalNodes = 12,
                        totalRules = 0,
                        requestMonitoringEnabled = false,
                        visibleShortcuts = shortcuts.toSet(),
                        shortcutOrder = shortcuts,
                        onToggleVpn = {
                            vpnToggles++
                            vpn.value = if (vpn.value == VpnState.Status.STOPPED) VpnState.Status.RUNNING else VpnState.Status.STOPPED
                        },
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
                        onUpdateShortcutOrder = {},
                        uiStyle = style.value,
                        onToggleUiStyle = {
                            styleToggles++
                            style.value = style.value.toggled()
                        }
                    )
                }
                }
            }
        }

        // All six configured entries remain rendered, with the first two occupying separate columns.
        compose.onNodeWithText("启动 VPN").assertExists()
        compose.onNodeWithText("IP 测试").assertExists()
        compose.onNodeWithText("切换 UI").assertExists()
        compose.onNodeWithText("节点").assertExists()
        compose.onNodeWithText("配置文件").assertExists()
        compose.onNodeWithText("核心日志").assertExists()
        val vpnX = compose.onNodeWithText("启动 VPN").fetchSemanticsNode().boundsInRoot.center.x
        val ipX = compose.onNodeWithText("IP 测试").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("shortcuts should remain in two columns at 360dp", ipX > vpnX)
        // Ensure the long profile label is laid out within the constrained viewport (not clipped off-screen).
        val profileBounds = compose.onNodeWithText("测试配置名称").fetchSemanticsNode().boundsInRoot
        assertTrue(profileBounds.left >= 0f && profileBounds.right <= viewportWidthPx.value)

        compose.onNodeWithText("启动 VPN").performClick()
        compose.onNodeWithText("停止 VPN").assertExists()
        compose.onNodeWithText("切换 UI").performClick()
        compose.onNodeWithText("Miuix → Cupertino").assertExists()
        compose.runOnIdle {
            assertEquals(1, vpnToggles)
            assertEquals(1, styleToggles)
        }

        // Validate the other style without replacing the dashboard, then exercise the IP sheet entry point.
        compose.onNodeWithText("切换 UI").performClick()
        compose.onNodeWithText("Cupertino → Miuix").assertExists()
        compose.onNodeWithText("IP 测试").performClick()
        compose.onNodeWithText("IP 地址").assertExists()
    }
}
