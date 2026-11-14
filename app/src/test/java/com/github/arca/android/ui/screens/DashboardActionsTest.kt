package com.github.arca.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.components.LocalHapticFeedbackEnabled
import com.github.arca.android.ui.components.LocalHapticFeedbackPerformer
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class DashboardActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun vpnShortcutCallsLiveActionAndCupertinoDashboardKeepsState() {
        val status = mutableStateOf(VpnState.Status.STOPPED)
        var starts = 0
        var stops = 0
        val actions = listOf("vpn")
        compose.setContent {
            MihomoTheme() {
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
                )
            }
        }
        compose.onNodeWithText("启动 VPN").performClick()
        compose.onNodeWithText("停止 VPN").assertExists()
        compose.onNodeWithText("停止 VPN").performClick()
        compose.onNodeWithText("启动 VPN").assertExists()
        compose.runOnIdle { status.value = VpnState.Status.STOPPING }
        compose.onNodeWithText("正在停止").performClick()
        compose.runOnIdle { assertEquals(1, starts); assertEquals(1, stops) }
    }

    @Test fun ruleModeMorphUsesDiscreteHapticsAndHonorsPreference() {
        val hapticsEnabled = mutableStateOf(true)
        var hapticTicks = 0
        val selectedModes = mutableListOf<TunnelMode>()
        compose.setContent {
            CompositionLocalProvider(
                LocalAppLanguage provides "zh",
                LocalHapticFeedbackEnabled provides { hapticsEnabled.value },
                LocalHapticFeedbackPerformer provides { hapticTicks++ }
            ) {
                MihomoTheme {
                    DashboardScreen(
                        scrollState = ScrollState(0), vpnState = VpnState(status = VpnState.Status.STOPPED),
                        currentMode = TunnelMode.RULE, activeProfileName = "测试配置", mainGroupName = "",
                        mainGroupProxies = emptyList(), activeNodeName = "", totalNodes = 0, totalRules = 0,
                        visibleShortcuts = emptySet(), shortcutOrder = emptyList(),
                        onToggleVpn = {}, onModeSelected = { selectedModes += it },
                        onSelectProxy = { _, _ -> }, onHealthCheck = {}, onNavigateToProfiles = {},
                        onNavigateToAppRouting = {}, onNavigateToLogs = {}, onNavigateToRules = {},
                        onNavigateToDns = {}, onNavigateToRequests = {}, onNavigateToNetworkShare = {},
                        onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = {}
                    )
                }
            }
        }
        val collapsedBounds = compose.onNodeWithTag("mode-picker-trigger").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("mode-picker-trigger").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("mode-picker-selected-glass", useUnmergedTree = true).assertExists()
        val expandedBounds = compose.onNodeWithTag("mode-picker-panel").fetchSemanticsNode().boundsInRoot
        assertEquals("selector width is unchanged", collapsedBounds.width, expandedBounds.width, 1f)
        assertEquals("selector remains anchored at the same top", collapsedBounds.top, expandedBounds.top, 1f)
        assertTrue("selector morph grows downward", expandedBounds.height > collapsedBounds.height + 48f)
        compose.runOnIdle { assertEquals("opening the morph ticks once", 1, hapticTicks) }
        compose.runOnIdle { hapticsEnabled.value = false }
        compose.onNodeWithTag("mode-option-global", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf(TunnelMode.GLOBAL), selectedModes)
            assertEquals("disabled haptics stay silent", 1, hapticTicks)
        }
    }
}
