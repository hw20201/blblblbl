package com.github.mihomo.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.core.model.Proxy
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.service.VpnState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DashboardScreenTest {
    @get:Rule val compose = createComposeRule()

    private val shortcutOrder = listOf("nodes", "profiles", "rules", "request_logs")
    private val noOp: () -> Unit = {}

    @Test fun visibleShortcutSubtitlesFollowVpnProfileCountsAndMonitoringState() {
        val vpnState = mutableStateOf(VpnState(status = VpnState.Status.STOPPED))
        val profile = mutableStateOf("未选择")
        val nodes = mutableStateOf(0)
        val rules = mutableStateOf(0)
        val monitoringEnabled = mutableStateOf(true)

        compose.setContent {
            DashboardScreen(
                scrollState = ScrollState(0),
                vpnState = vpnState.value,
                currentMode = TunnelMode.RULE,
                activeProfileName = profile.value,
                mainGroupName = "",
                mainGroupProxies = emptyList<Proxy>(),
                activeNodeName = "",
                totalNodes = nodes.value,
                totalRules = rules.value,
                requestMonitoringEnabled = monitoringEnabled.value,
                visibleShortcuts = shortcutOrder.toSet(),
                shortcutOrder = shortcutOrder,
                onToggleVpn = noOp,
                onModeSelected = {},
                onSelectProxy = { _, _ -> },
                onHealthCheck = {},
                onNavigateToProfiles = noOp,
                onNavigateToAppRouting = noOp,
                onNavigateToLogs = noOp,
                onNavigateToRules = noOp,
                onNavigateToDns = noOp,
                onNavigateToRequests = noOp,
                onNavigateToNetworkShare = noOp,
                onUpdateVisibleShortcuts = {},
                onUpdateShortcutOrder = {},
            )
        }

        // The four targets fit in the first two shortcut rows; assert actual composed card subtitles.
        compose.onNodeWithText("未选择", useUnmergedTree = true).assertTextEquals("未选择")
        compose.onAllNodesWithText("0").assertCountEquals(2)
        compose.onNodeWithText("未开启").assertExists()

        compose.runOnIdle {
            vpnState.value = VpnState(status = VpnState.Status.RUNNING)
            profile.value = "自选配置"
            nodes.value = 42
            rules.value = 123
        }
        compose.waitForIdle()
        compose.onNodeWithText("自选配置").assertExists()
        compose.onNodeWithText("42").assertExists()
        compose.onNodeWithText("123").assertExists()
        compose.onNodeWithText("实时监控").assertExists()

        compose.runOnIdle { vpnState.value = VpnState(status = VpnState.Status.STOPPED) }
        compose.waitForIdle()
        compose.onAllNodesWithText("实时监控").assertCountEquals(0)
        compose.onNodeWithText("未开启").assertExists()

        compose.runOnIdle { monitoringEnabled.value = false }
        compose.waitForIdle()
        compose.onNodeWithText("已暂停").assertExists()
    }
}
