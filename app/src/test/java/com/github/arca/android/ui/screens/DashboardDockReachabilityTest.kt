package com.github.arca.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.data.DashboardShortcuts
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.components.BOTTOM_BAR_BOTTOM_MARGIN
import com.github.arca.android.ui.components.BOTTOM_BAR_HEIGHT
import com.github.arca.android.ui.components.DASHBOARD_DOCK_CLEAR_GAP
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.compose.ui.unit.dp

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class DashboardDockReachabilityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun lastDefaultShortcutCanScrollFullyAboveFixedDock() {
        val scrollState = ScrollState(0)
        var rootHeightPx = 0
        var dockTopPx = Float.NaN
        var dockBottomPx = Float.NaN
        var dockTopBeforeScrollPx = Float.NaN
        var dockBottomBeforeScrollPx = Float.NaN

        compose.setContent {
            MihomoTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { rootHeightPx = it.height }
                ) {
                    DashboardScreen(
                        scrollState = scrollState,
                        vpnState = VpnState(status = VpnState.Status.STOPPED),
                        currentMode = TunnelMode.RULE,
                        activeProfileName = "测试配置",
                        mainGroupName = "Proxy",
                        mainGroupProxies = emptyList(),
                        activeNodeName = "",
                        totalNodes = 0,
                        totalRules = 0,
                        requestMonitoringEnabled = false,
                        visibleShortcuts = DashboardShortcuts.defaults.toSet(),
                        shortcutOrder = DashboardShortcuts.defaults,
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

                    // Mirrors MainActivity's fixed bottom-dock placement, but avoids rendering glass in Robolectric.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .wrapContentSize(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = BOTTOM_BAR_BOTTOM_MARGIN)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 304.dp, height = BOTTOM_BAR_HEIGHT)
                                .onGloballyPositioned { coordinates ->
                                    val top = coordinates.localToRoot(Offset.Zero).y
                                    dockTopPx = top
                                    dockBottomPx = top + coordinates.size.height
                                }
                        )
                    }
                }
            }
        }

        compose.waitForIdle()
        assertEquals("The shipped Home default shortcut set must stay at eight items", 8, DashboardShortcuts.defaults.size)
        assertEquals("routing", DashboardShortcuts.defaults.last())

        compose.runOnIdle {
            assertTrue("Fixture must have a measured height", rootHeightPx > 0)
            assertTrue("Scrollable content must exceed the viewport", scrollState.maxValue > 0)
            assertTrue(
                "Dashboard scroll viewport must remain full-height behind the dock",
                kotlin.math.abs(scrollState.viewportSize - rootHeightPx) <= 1
            )
            dockTopBeforeScrollPx = dockTopPx
            dockBottomBeforeScrollPx = dockBottomPx
            scrollState.dispatchRawDelta((scrollState.maxValue - scrollState.value).toFloat())
        }
        compose.waitForIdle()

        val lastCard = compose.onNodeWithTag("shortcut-routing").fetchSemanticsNode().boundsInRoot
        val expectedCardHeightPx = with(compose.density) { 118.dp.toPx() }
        val expectedGapPx = with(compose.density) { DASHBOARD_DOCK_CLEAR_GAP.toPx() }

        compose.runOnIdle {
            assertEquals("Scroll should reach the true content end", scrollState.maxValue, scrollState.value)
            assertEquals("Dock top must stay anchored while Home scrolls", dockTopBeforeScrollPx, dockTopPx, 1f)
            assertEquals("Dock bottom must stay anchored while Home scrolls", dockBottomBeforeScrollPx, dockBottomPx, 1f)
            assertTrue(
                "The final shortcut must remain fully laid out, height=${lastCard.height}, expected=$expectedCardHeightPx",
                lastCard.height >= expectedCardHeightPx - 1f
            )
            assertTrue(
                "The final shortcut must end above the fixed dock with the configured gap; cardBottom=${lastCard.bottom}, dockTop=$dockTopPx, gap=$expectedGapPx",
                lastCard.bottom <= dockTopPx - expectedGapPx + 1f
            )
        }
    }
}
