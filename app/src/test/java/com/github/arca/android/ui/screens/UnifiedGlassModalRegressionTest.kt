package com.github.arca.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.data.OutboundIpInfo
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.GlassModalBottomSheet
import com.github.arca.android.ui.glass.GlassPalettes
import com.github.arca.android.ui.glass.GlassPreset
import com.github.arca.android.ui.glass.GlassSettings
import com.github.arca.android.ui.glass.LocalGlassSettings
import com.github.arca.android.ui.glass.NexusGlassBackground
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import com.github.kr328.clash.core.model.Proxy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterial3Api::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnifiedGlassModalRegressionTest {
    @get:Rule val compose = createComposeRule()

    @After
    fun resetGlass() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    @Test
    fun ipDetectionSheetRendersUnderUnifiedGlasmioGlass() {
        setUnifiedContent {
            OutboundIpSheet(onRefresh = { OutboundIpInfo("203.0.113.9", source = "fixture") })
        }

        compose.waitForIdle()
        compose.onNodeWithText("IP 地址").assertExists()
        compose.onNodeWithText("203.0.113.9").assertExists()
    }

    @Test
    fun nodeShortcutOpensAndSelectsUnderUnifiedGlasmioGlass() {
        var selected: Pair<String, Proxy>? = null
        val node = Proxy("Tokyo", type = "Shadowsocks", delay = 45)
        setUnifiedContent {
            DashboardScreen(
                scrollState = ScrollState(0),
                vpnState = VpnState(status = VpnState.Status.STOPPED),
                currentMode = TunnelMode.RULE,
                activeProfileName = "Profile",
                mainGroupName = "Proxy",
                mainGroupProxies = listOf(node),
                activeNodeName = "",
                totalNodes = 1,
                totalRules = 0,
                visibleShortcuts = setOf("nodes"),
                shortcutOrder = listOf("nodes"),
                onToggleVpn = {},
                onModeSelected = {},
                onSelectProxy = { group, proxy -> selected = group to proxy },
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

        compose.onNodeWithTag("shortcut-nodes").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("quick-node-selector-sheet").assertExists()
        compose.onNodeWithText("Tokyo", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("Proxy" to node, selected) }
        compose.onNodeWithTag("quick-node-selector-sheet").assertDoesNotExist()
    }

    @Test
    fun squareModalSurfaceRendersUnderUnifiedGlass() {
        setUnifiedContent {
            GlassModalBottomSheet(onDismissRequest = {}, dragHandle = null) {
                Box(Modifier.fillMaxWidth().height(48.dp).testTag("square-modal-content"))
            }
        }

        compose.waitForIdle()
        compose.onNodeWithTag("square-modal-content").assertExists()
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
