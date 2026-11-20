package com.github.arca.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.data.TunStack
import com.github.arca.android.ui.components.BOTTOM_BAR_BOTTOM_MARGIN
import com.github.arca.android.ui.components.BOTTOM_BAR_HEIGHT
import com.github.arca.android.ui.components.MAIN_DOCK_CONTENT_CLEAR_GAP
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class MainPagesDockReachabilityTest {
    @get:Rule val compose = createComposeRule()
    private val originalGlassEnabled = GlassDefaults.enabled

    @After fun restoreGlass() { GlassDefaults.enabled = originalGlassEnabled }

    @Test fun settingsInfoRowAndFinalMaintenanceActionClearFloatingDockAtScrollEnd() {
        val scrollState = ScrollState(0)
        setContent {
            SettingsScreen(
                scrollState = scrollState,
                tunStack = TunStack.DEFAULT,
                bootOnStartup = false,
                allowLan = false,
                fakeIpEnabled = false,
                onTunStackChanged = {},
                onBootOnStartupChanged = {},
                onAllowLanChanged = {},
                onNavigateToConfigurationCenter = {},
                onNavigateToLogs = {}
            )
        }
        compose.runOnIdle {
            assertTrue("Settings must overflow the page", scrollState.maxValue > 0)
            scrollState.dispatchRawDelta(scrollState.maxValue.toFloat())
        }
        compose.waitForIdle()
        assertEquals(scrollState.maxValue, scrollState.value)
        compose.onNodeWithText("INFO").assertIsDisplayed()
        assertClearsDock(compose.onNodeWithTag("settings-log-level-entry"))
        assertClearsDock(compose.onNodeWithText("重置核心状态"))
    }

    @Test fun tabsFinalProxyClearsFloatingDock() = assertFinalProxyClearsDock("TABS")

    @Test fun expandedListFinalProxyClearsFloatingDock() = assertFinalProxyClearsDock("LIST")

    private fun assertFinalProxyClearsDock(mode: String) {
        val nodes = List(30) { Proxy("Reachable node $it", delay = 10 + it) }
        val group = ProxyGroup("select", nodes, nodes.first().name)
        val expanded = mutableStateMapOf("Proxy" to true)
        setContent {
            ProxiesScreen(
                groups = listOf("Proxy"),
                groupMap = mapOf("Proxy" to group),
                currentGroup = group,
                selectedGroupName = "Proxy",
                currentViewMode = mode,
                expandedGroups = expanded,
                onViewModeChanged = {},
                onShowGroupIconsChanged = {},
                onSelectGroup = {},
                onSelectProxy = { _, _ -> },
                onHealthCheck = {}
            )
        }
        val list = compose.onNodeWithTag(if (mode == "LIST") "policy-group-list" else "policy-node-list")
        list.performScrollToIndex(if (mode == "LIST") nodes.size else nodes.lastIndex)
        list.performTouchInput { swipeUp() }
        compose.waitForIdle()
        val lastCard = compose.onNodeWithTag("proxy-card-Reachable node 29")
        lastCard.assertIsDisplayed()
        assertClearsDock(lastCard)
    }

    private fun setContent(content: @Composable () -> Unit) {
        GlassDefaults.enabled = false
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    Box(Modifier.fillMaxSize()) {
                        content()
                        Box(
                            Modifier.align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = BOTTOM_BAR_BOTTOM_MARGIN)
                        ) {
                            Box(Modifier.size(304.dp, BOTTOM_BAR_HEIGHT).testTag("main-pages-test-dock"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertClearsDock(item: SemanticsNodeInteraction) {
        val itemBottom = item.fetchSemanticsNode().boundsInRoot.bottom
        val dockTop = compose.onNodeWithTag("main-pages-test-dock").fetchSemanticsNode().boundsInRoot.top
        val gapPx = with(compose.density) { MAIN_DOCK_CONTENT_CLEAR_GAP.toPx() }
        assertTrue(
            "Final content must clear the dock by the shared safety margin: bottom=$itemBottom, dockTop=$dockTop, gap=$gapPx",
            itemBottom <= dockTop - gapPx + 1f
        )
    }
}
