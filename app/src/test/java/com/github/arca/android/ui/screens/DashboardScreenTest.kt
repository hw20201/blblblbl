package com.github.arca.android.ui.screens

import com.github.arca.android.ui.glass.NexusGlassBackground
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.github.arca.android.ui.theme.LocalAppLanguage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.core.model.Proxy
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.service.VpnState
import com.github.arca.android.service.TrafficMetrics
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

    /** The dashboard draws Glasmio glass, which must be hosted by a backdrop provider. */
    private fun setGlassContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { NexusGlassBackground { content() } }
    }

    @Test fun liveTrafficSamplesUpdateTheLeafCardWithoutChangingDashboardInputs() {
        val metrics = MutableStateFlow(TrafficMetrics())
        setGlassContent {
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
            metrics.value = TrafficMetrics("5 KB/s", "12 KB/s")
        }
        compose.waitForIdle()
        compose.onNodeWithTag("traffic-upload-rate").assertTextEquals("5 KB/s")
        compose.onNodeWithTag("traffic-download-rate").assertTextEquals("12 KB/s")
        compose.onNodeWithTag("traffic-upload-total").assertDoesNotExist()
        compose.onNodeWithTag("traffic-download-total").assertDoesNotExist()
    }

    @Test fun oldTestBackgroundChoicesAreNotShownOnTheUserDashboard() {
        setGlassContent {
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

        setGlassContent {
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
    @Test fun modeSegmentsAlwaysExposeThreeRadioChoicesAndFollowExternalSelection() {
        val mode = mutableStateOf(TunnelMode.RULE)
        val language = mutableStateOf("zh")
        val selections = mutableListOf<TunnelMode>()
        setModeContent(mode, language) { selections += it; mode.value = it }

        compose.onNodeWithTag("mode-segment-control").assertIsDisplayed()
            .assertWidthIsEqualTo(264.dp).assertHeightIsEqualTo(48.dp)
        assertModeOptions(TunnelMode.RULE, listOf("规则", "全局", "直连"))
        compose.onNodeWithTag("mode-option-rule").performClick()
        compose.runOnIdle { assertTrue("selected option does not repeat the action", selections.isEmpty()) }
        compose.onNodeWithTag("mode-option-global").performClick()
        compose.waitForIdle()
        assertModeOptions(TunnelMode.GLOBAL, listOf("规则", "全局", "直连"))
        compose.onNodeWithTag("mode-option-global").performClick()
        compose.runOnIdle { assertEquals(listOf(TunnelMode.GLOBAL), selections) }

        // The control reflects the owner's currentMode, including an external update.
        compose.runOnIdle { mode.value = TunnelMode.DIRECT; language.value = "en" }
        compose.waitForIdle()
        assertModeOptions(TunnelMode.DIRECT, listOf("Rule", "Global", "Direct"))
        compose.runOnIdle { assertEquals("external changes are not new click actions", listOf(TunnelMode.GLOBAL), selections) }
    }

    @Test fun modeSelectionLensHasIntermediateHorizontalPositionsWithoutResizingControl() {
        val mode = mutableStateOf(TunnelMode.RULE)
        setModeContent(mode) { mode.value = it }
        compose.waitForIdle()
        val control = modeBounds("mode-segment-control")
        val initial = modeBounds("mode-selection-lens")
        val destination = modeBounds("mode-option-direct")
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("mode-option-direct").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            val frames = mutableListOf<Rect>()
            repeat(38) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                frames += modeBounds("mode-selection-lens")
                assertSameRect("fixed mode control", control, modeBounds("mode-segment-control"))
                assertEquals("lens expands around a fixed vertical center", initial.center.y, frames.last().center.y, .5f)
            }
            // The shared dock spring contracts only after horizontal arrival; keep the
            // measured travel frames, then allow that independent settle to finish.
            compose.mainClock.advanceTimeBy(900)
            compose.waitForIdle()
            val final = modeBounds("mode-selection-lens")
            assertTrue("lens travels from Rule to Direct", final.center.x > initial.center.x + control.width / 2f)
            assertEquals("lens centers on the Direct segment", destination.center.x, final.center.x, 1f)
            assertEquals("lens returns to the same resting width", initial.width, final.width, 1f)
            assertEquals("lens keeps its resting height", initial.height, final.height, 1f)
            assertTrue("real animation has intermediate positions, not an endpoint jump", frames.any {
                it.center.x > initial.center.x + 2f && it.center.x < final.center.x - 2f
            })
            assertTrue("multiple distinct positions are rendered", frames.map { it.left.toInt() }.distinct().size >= 4)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun modeSwitchingKeepsHeaderTrafficAndShortcutLayoutStationary() {
        val mode = mutableStateOf(TunnelMode.RULE)
        setModeContent(mode) { mode.value = it }
        compose.waitForIdle()
        val tags = listOf("mode-segment-control", "dashboard-nexus-title", "dashboard-traffic-card", "shortcut-nodes", "shortcut-profiles")
        val baseline = tags.associateWith(::modeBounds)
        compose.mainClock.autoAdvance = false
        try {
            for (choice in listOf("global", "direct", "rule")) {
                compose.onNodeWithTag("mode-option-$choice").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
                repeat(38) {
                    compose.mainClock.advanceTimeByFrame()
                    compose.waitForIdle()
                    tags.forEach { tag -> assertSameRect("$tag during switch to $choice", baseline.getValue(tag), modeBounds(tag)) }
                    listOf("rule", "global", "direct").forEach { option ->
                        compose.onNodeWithTag("mode-option-$option").assertIsDisplayed()
                    }
                }
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun setModeContent(
        mode: State<TunnelMode>,
        language: State<String> = mutableStateOf("zh"),
        onModeSelected: (TunnelMode) -> Unit
    ) {
        val scroll = ScrollState(0)
        setGlassContent {
            CompositionLocalProvider(LocalAppLanguage provides language.value) {
                DashboardScreen(
                    scrollState = scroll, vpnState = VpnState(status = VpnState.Status.STOPPED),
                    currentMode = mode.value, activeProfileName = "未选择", mainGroupName = "",
                    mainGroupProxies = emptyList(), activeNodeName = "", totalNodes = 0, totalRules = 0,
                    visibleShortcuts = shortcutOrder.toSet(), shortcutOrder = shortcutOrder,
                    onToggleVpn = noOp, onModeSelected = onModeSelected, onSelectProxy = { _, _ -> },
                    onHealthCheck = {}, onNavigateToProfiles = noOp, onNavigateToAppRouting = noOp,
                    onNavigateToLogs = noOp, onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = {}
                )
            }
        }
    }

    private fun assertModeOptions(selected: TunnelMode, labels: List<String>) {
        listOf("rule" to TunnelMode.RULE, "global" to TunnelMode.GLOBAL, "direct" to TunnelMode.DIRECT)
            .zip(labels).forEach { (choice, label) ->
                val node = compose.onNodeWithTag("mode-option-${choice.first}")
                node.assertIsDisplayed().assertHasClickAction().assertTextEquals(label)
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                if (choice.second == selected) node.assertIsSelected() else node.assertIsNotSelected()
            }
    }

    private fun modeBounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun assertSameRect(message: String, expected: Rect, actual: Rect) {
        assertEquals("$message left", expected.left, actual.left, .5f)
        assertEquals("$message top", expected.top, actual.top, .5f)
        assertEquals("$message right", expected.right, actual.right, .5f)
        assertEquals("$message bottom", expected.bottom, actual.bottom, .5f)
    }


    @Test fun homepageTrafficPanelShowsLiveDirectionsWithoutRecordedTotals() {
        val metrics = MutableStateFlow(TrafficMetrics("12 KB/s", "4 MB/s"))
        setGlassContent {
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
        compose.onNodeWithTag("traffic-upload-total").assertDoesNotExist()
        compose.onNodeWithTag("traffic-download-total").assertDoesNotExist()
    }

}
