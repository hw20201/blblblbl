package com.github.mihomo.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.core.model.Proxy
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.service.TrafficMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class DashboardScreenTest {
    @get:Rule val compose = createComposeRule()

    private val shortcutOrder = listOf("nodes", "profiles", "rules", "request_logs")
    private val noOp: () -> Unit = {}

    @Test fun liveTrafficSamplesUpdateTheLeafCardWithoutChangingDashboardInputs() {
        val metrics = MutableStateFlow(TrafficMetrics())
        compose.setContent {
            DashboardScreen(
                scrollState = ScrollState(0),
                vpnState = VpnState(status = VpnState.Status.RUNNING),
                trafficMetrics = metrics,
                currentMode = TunnelMode.RULE,
                activeProfileName = "测试配置",
                mainGroupName = "",
                mainGroupProxies = emptyList<Proxy>(),
                activeNodeName = "",
                totalNodes = 0,
                totalRules = 0,
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
                onUpdateShortcutOrder = {}
            )
        }
        compose.runOnIdle {
            metrics.value = TrafficMetrics("5 KB/s", "12 KB/s", "30 MB", "80 MB")
        }
        compose.waitForIdle()
        compose.onNodeWithTag("traffic-upload-rate").assertTextEquals("5 KB/s")
        compose.onNodeWithTag("traffic-download-rate").assertTextEquals("12 KB/s")
        compose.onNodeWithTag("traffic-upload-total").assertTextEquals("Total 30 MB")
        compose.onNodeWithTag("traffic-download-total").assertTextEquals("Total 80 MB")
    }

    @Test fun oldTestWallpaperChoicesAreNotShownOnTheUserDashboard() {
        compose.setContent {
            DashboardScreen(
                scrollState = ScrollState(0),
                vpnState = VpnState(status = VpnState.Status.STOPPED),
                currentMode = TunnelMode.RULE,
                activeProfileName = "测试配置",
                mainGroupName = "",
                mainGroupProxies = emptyList<Proxy>(),
                activeNodeName = "",
                totalNodes = 0,
                totalRules = 0,
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
                onUpdateShortcutOrder = {}
            )
        }
        compose.onNodeWithTag("glass-test-background-picker").assertDoesNotExist()
        compose.onNodeWithTag("glass-test-background-original").assertDoesNotExist()
        compose.onNodeWithTag("glass-test-background-inverted").assertDoesNotExist()
    }

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
    @Test fun modePickerExpandsFromItsControlAndSelectsInPlace() {
        var selected: TunnelMode? = null
        compose.setContent {
            DashboardScreen(
                scrollState = ScrollState(0),
                vpnState = VpnState(status = VpnState.Status.STOPPED),
                currentMode = TunnelMode.RULE,
                activeProfileName = "未选择",
                mainGroupName = "",
                mainGroupProxies = emptyList<Proxy>(),
                activeNodeName = "",
                totalNodes = 0,
                totalRules = 0,
                visibleShortcuts = shortcutOrder.toSet(),
                shortcutOrder = shortcutOrder,
                onToggleVpn = noOp,
                onModeSelected = { selected = it },
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

        compose.onNodeWithTag("mode-picker-panel").assertDoesNotExist()
        compose.onNodeWithTag("mode-picker-trigger").performClick()
        // The only transition is the smooth 500ms downward expansion.
        compose.mainClock.advanceTimeBy(550)
        compose.waitForIdle()
        compose.onNodeWithTag("mode-picker-panel").assertExists().assertIsSelected()
        compose.onNodeWithTag("mode-option-rule", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("mode-option-direct", useUnmergedTree = true).assertExists()
        val titleBounds = compose.onNodeWithTag("dashboard-nexus-title").fetchSemanticsNode().boundsInRoot
        val panelBounds = compose.onNodeWithTag("mode-picker-panel").fetchSemanticsNode().boundsInRoot
        assertTrue("picker must expand below the ARCA header", panelBounds.top >= titleBounds.bottom)
        assertTrue("picker should remain a compact window", panelBounds.width < 260f)
        compose.onNodeWithTag("mode-option-global").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(TunnelMode.GLOBAL, selected) }
        compose.onNodeWithTag("mode-picker-panel").assertDoesNotExist()
    }

    @Test fun modePickerShapeMorphHasAVisibleIntermediateSize() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DashboardScreen(
                scrollState = ScrollState(0),
                vpnState = VpnState(status = VpnState.Status.STOPPED),
                currentMode = TunnelMode.RULE,
                activeProfileName = "未选择",
                mainGroupName = "",
                mainGroupProxies = emptyList<Proxy>(),
                activeNodeName = "",
                totalNodes = 0,
                totalRules = 0,
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

        compose.waitForIdle()
        val shellBefore = compose.onNodeWithTag("mode-picker-trigger", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("mode-picker-trigger").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val panel = compose.onNodeWithTag("mode-picker-panel", useUnmergedTree = true)
        panel.assertExists()
        val first = panel.fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(60)
        val middle = panel.fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(500) // Finish the 500ms expansion before asserting settled bounds.
        val settled = panel.fetchSemanticsNode().boundsInRoot
        assertTrue("the picker should extend downward during the morph", middle.height > first.height)
        assertEquals("the menu retains the capsule shell width", shellBefore.width, settled.width, 1f)
        assertEquals("the menu remains anchored at the capsule", shellBefore.top, settled.top, .5f)
        assertEquals("the menu extends downward far enough for both choices", 144f, settled.height, 2f)
    }

    @Test fun modePickerKeepsCapsuleFixedAndOpensDownward() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DashboardScreen(
                scrollState = ScrollState(0),
                vpnState = VpnState(status = VpnState.Status.STOPPED),
                currentMode = TunnelMode.RULE,
                activeProfileName = "未选择",
                mainGroupName = "",
                mainGroupProxies = emptyList<Proxy>(),
                activeNodeName = "",
                totalNodes = 0,
                totalRules = 0,
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

        val capsuleBefore = compose.onNodeWithTag("mode-picker-capsule", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val shellBefore = compose.onNodeWithTag("mode-picker-trigger", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("mode-picker-trigger").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val panel = compose.onNodeWithTag("mode-picker-panel", useUnmergedTree = true)
        panel.assertExists().assertIsSelected()
        val initialPanel = panel.fetchSemanticsNode().boundsInRoot
        assertEquals("the capsule keeps its width as the shell opens", shellBefore.width, initialPanel.width, 1f)
        assertEquals("the shell stays anchored to the capsule", shellBefore.top, initialPanel.top, .5f)
        val capsuleDuring = compose.onNodeWithTag("mode-picker-capsule", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals("the capsule itself keeps its width", capsuleBefore.width, capsuleDuring.width, .5f)
        assertEquals("the capsule itself keeps its height", capsuleBefore.height, capsuleDuring.height, .5f)
        assertTrue("the open shell cannot be shorter than the capsule", initialPanel.height >= capsuleBefore.height)
        compose.mainClock.advanceTimeBy(420)
        val expanded = panel.fetchSemanticsNode().boundsInRoot
        assertEquals("the expanded menu retains its width", initialPanel.width, expanded.width, .5f)
        assertEquals("the expanded menu remains anchored", initialPanel.top, expanded.top, .5f)
        assertTrue("the menu extends downward to expose both other choices", expanded.height > capsuleBefore.height)
        compose.onNodeWithTag("mode-option-global", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("mode-option-direct", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("mode-option-rule", useUnmergedTree = true).assertDoesNotExist()
    }


    @Test fun homepageTrafficPanelKeepsLiveDirectionsAndShowsOnlyProvidedTotals() {
        val metrics = MutableStateFlow(TrafficMetrics("12 KB/s", "4 MB/s", "18.2 MB", "2.4 GB"))
        compose.setContent {
            DashboardScreen(
                scrollState = ScrollState(0),
                vpnState = VpnState(status = VpnState.Status.RUNNING),
                trafficMetrics = metrics,
                currentMode = TunnelMode.RULE,
                activeProfileName = "测试配置",
                mainGroupName = "",
                mainGroupProxies = emptyList<Proxy>(),
                activeNodeName = "",
                totalNodes = 0,
                totalRules = 0,
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
                onUpdateShortcutOrder = {}
            )
        }
        compose.onNodeWithTag("dashboard-traffic-card").assertExists()
        compose.onNodeWithTag("traffic-upload-rate").assertTextEquals("12 KB/s")
        compose.onNodeWithTag("traffic-download-rate").assertTextEquals("4 MB/s")
        compose.onNodeWithTag("traffic-upload-total").assertTextEquals("Total 18.2 MB")
        compose.onNodeWithTag("traffic-download-total").assertTextEquals("Total 2.4 GB")
    }

}
