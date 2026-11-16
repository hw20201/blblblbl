package com.github.arca.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.components.LocalHapticFeedbackEnabled
import com.github.arca.android.ui.components.LocalHapticFeedbackPerformer
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.GlassPalettes
import com.github.arca.android.ui.glass.GlassPreset
import com.github.arca.android.ui.glass.GlassSettings
import com.github.arca.android.ui.glass.LocalGlassSettings
import com.github.arca.android.ui.glass.NexusGlassBackground
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.After
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

    @After
    fun resetGlass() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    @Test fun vpnShortcutTogglesDirectlyAndGuardsStopping() {
        val status = mutableStateOf(VpnState.Status.STOPPED)
        var starts = 0
        var stops = 0
        val actions = listOf("vpn")
        setUnifiedContent {
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
        compose.onNodeWithTag("shortcut-vpn").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(VpnState.Status.RUNNING, status.value)
            assertEquals(1, starts)
            assertEquals(0, stops)
        }

        compose.onNodeWithTag("shortcut-vpn").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(VpnState.Status.STOPPED, status.value)
            assertEquals(1, starts)
            assertEquals(1, stops)
        }

        compose.runOnIdle { status.value = VpnState.Status.STOPPING }
        compose.waitForIdle()
        compose.onNodeWithTag("shortcut-vpn").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(VpnState.Status.STOPPING, status.value)
            assertEquals(1, starts)
            assertEquals(1, stops)
        }
    }

    @Test fun profileShortcutAddsConfigInsidePanelWithoutNavigation() {
        var navigations = 0
        setUnifiedContent {
            DashboardScreen(
                scrollState = ScrollState(0), vpnState = VpnState(status = VpnState.Status.STOPPED),
                currentMode = TunnelMode.RULE, activeProfileName = "测试配置", mainGroupName = "Proxy",
                mainGroupProxies = emptyList(), activeNodeName = "", totalNodes = 0, totalRules = 0,
                requestMonitoringEnabled = false, visibleShortcuts = setOf("profiles"), shortcutOrder = listOf("profiles"),
                onToggleVpn = {}, onModeSelected = {}, onSelectProxy = { _, _ -> }, onHealthCheck = {},
                onNavigateToProfiles = { navigations++ }, onNavigateToAppRouting = {}, onNavigateToLogs = {},
                onNavigateToRules = {}, onNavigateToDns = {}, onNavigateToRequests = {},
                onNavigateToNetworkShare = {}, onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = {}
            )
        }

        compose.onNodeWithTag("shortcut-profiles").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("shortcut-panel-profiles-sheet").assertExists()
        compose.runOnIdle { assertEquals(0, navigations) }
        compose.onNodeWithTag("quick-profile-add").assertExists()
        compose.onNodeWithText("添加配置：本地文件 / 订阅").assertExists()

        compose.onNodeWithTag("quick-profile-add").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("添加配置").assertExists()
        compose.onNodeWithTag("profile-add-local-file").assertExists()
        compose.onNodeWithTag("profile-add-clipboard").assertExists()
        compose.onNodeWithTag("profile-add-name").assertExists()
        compose.onNodeWithTag("profile-add-url").assertExists()
        compose.onNodeWithTag("profile-add-submit").assertExists()
    }

    @Test fun pluginsShortcutShowsItsExistingCoreInfoInsideTheMorphPanel() {
        setUnifiedContent {
            DashboardScreen(
                scrollState = ScrollState(0), vpnState = VpnState(status = VpnState.Status.STOPPED, coreVersion = "v1.19.0"),
                currentMode = TunnelMode.RULE, activeProfileName = "测试配置", mainGroupName = "Proxy",
                mainGroupProxies = emptyList(), activeNodeName = "", totalNodes = 0, totalRules = 0,
                requestMonitoringEnabled = false, visibleShortcuts = setOf("plugins"), shortcutOrder = listOf("plugins"),
                onToggleVpn = {}, onModeSelected = {}, onSelectProxy = { _, _ -> }, onHealthCheck = {},
                onNavigateToProfiles = {}, onNavigateToAppRouting = {}, onNavigateToLogs = {},
                onNavigateToRules = {}, onNavigateToDns = {}, onNavigateToRequests = {},
                onNavigateToNetworkShare = {}, onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = {}
            )
        }

        compose.onNodeWithTag("shortcut-plugins").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("shortcut-panel-plugins-sheet").assertExists()
        compose.onNodeWithText("内核版本").assertExists()
        compose.onNodeWithText("v1.19.0").assertExists()
        compose.onNodeWithText("设备架构").assertExists()
        compose.onNodeWithTag("shortcut-panel-action").assertDoesNotExist()
    }

    @Test fun ruleModeMorphUsesDiscreteHapticsAndHonorsPreference() {
        val hapticsEnabled = mutableStateOf(true)
        var hapticTicks = 0
        val selectedModes = mutableListOf<TunnelMode>()
        setUnifiedContent {
            CompositionLocalProvider(
                LocalHapticFeedbackEnabled provides { hapticsEnabled.value },
                LocalHapticFeedbackPerformer provides { hapticTicks++ }
            ) {
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
        val collapsedBounds = compose.onNodeWithTag("mode-picker-trigger").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("mode-picker-trigger").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("mode-picker-panel").assertExists()
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

    private fun setUnifiedContent(content: @Composable () -> Unit) {
        GlassDefaults.enabled = true
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "zh",
                    LocalGlassSettings provides GlassSettings(glassPreset = GlassPreset.UNIFIED)
                ) {
                    ProvideGlassTheme {
                        NexusGlassBackground {
                            content()
                        }
                    }
                }
            }
        }
    }
}
