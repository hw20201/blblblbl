package com.github.arca.android.ui.screens

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.SettingsManager
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.service.TrafficMetrics
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.components.LocalShortcutTouchVariant
import com.github.arca.android.ui.components.ShortcutTouchVariant
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.NexusGlassBackground
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class DashboardShortcutReorderTest {
    @get:Rule val compose = createComposeRule()
    private val oldGlass = GlassDefaults.enabled
    private val scroll = ScrollState(0)
    private val initialOrder = listOf("nodes", "ip_test", "profiles", "future_shortcut", "rules", "dns", "vpn")
    private val order = mutableStateOf(initialOrder)
    private val visible = mutableStateOf(setOf("nodes", "profiles", "rules", "dns"))
    private val commits = mutableListOf<List<String>>()
    private var visibilityChanges = 0
    private var vpnToggles = 0
    private var profileNavigations = 0
    private lateinit var settings: SettingsManager

    @After fun restoreGlobals() {
        GlassDefaults.enabled = oldGlass
    }

    @Test fun directDownwardDragAcrossRowsCommitsFullOrder() {
        showDashboard()
        dragTo("nodes", "rules")
        assertOrder(listOf("profiles", "rules", "nodes", "dns"))
    }

    @Test fun directUpwardDragAcrossRowsCommitsFullOrder() {
        showDashboard()
        dragTo("dns", "profiles")
        assertOrder(listOf("nodes", "dns", "profiles", "rules"))
    }

    @Test fun consecutiveDragsUseTheUpdatedOrderAndCallback() {
        showDashboard()
        dragTo("nodes", "rules")
        dragTo("nodes", "profiles")
        assertOrder(listOf("nodes", "profiles", "rules", "dns"), commitCount = 2)
    }

    @Test fun draggedCardTracksTheFingerAcrossSeparateFrames() {
        showDashboard()
        val before = bounds("nodes")
        compose.onRoot().performTouchInput {
            down(before.center)
            moveBy(Offset(0f, 40f), delayMillis = 32)
        }
        compose.waitForIdle()
        val afterSlop = bounds("nodes")
        compose.onRoot().performTouchInput {
            moveBy(Offset(0f, 40f), delayMillis = 32)
        }
        compose.waitForIdle()
        assertEquals("unscaled drag coordinates follow the finger", 40f, bounds("nodes").top - afterSlop.top, 1f)
        compose.onRoot().performTouchInput {
            moveBy(Offset(0f, 70f), delayMillis = 32)
            up()
        }
        assertOrder(listOf("profiles", "rules", "nodes", "dns"))
        assertTrue(bounds("nodes").top > before.top)
    }

    @Test fun cancelledDragDoesNotPersistOrChangeVisibility() {
        showDashboard()
        val before = bounds("nodes")
        compose.onRoot().performTouchInput {
            down(before.center)
            moveBy(Offset(0f, 110f), delayMillis = 32)
        }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(commits.isEmpty())
            assertEquals(initialOrder, order.value)
            assertEquals(0, visibilityChanges)
        }
        assertEquals(before, bounds("nodes"))
    }

    @Test fun longPressDragRemainsSupportedInEditMode() {
        showDashboard()
        dragTo("nodes", "rules", holdMillis = 650)
        assertOrder(listOf("profiles", "rules", "nodes", "dns"))
    }

    @Test fun downwardDragCrossesTheRowMidpoint() {
        showDashboard()
        dragByInGrid("nodes", 66f)
        assertOrder(listOf("profiles", "rules", "nodes", "dns"))
    }

    @Test fun upwardDragStaysPutUntilItCrossesTheSameRowMidpoint() {
        showDashboard()
        dragByInGrid("rules", -64f)
        compose.runOnIdle {
            assertTrue(commits.isEmpty())
            assertEquals(initialOrder, order.value)
            assertEquals(initialOrder, settings.shortcutOrder)
        }
    }

    @Test fun upwardDragCrossesTheRowMidpoint() {
        showDashboard()
        dragByInGrid("rules", -66f)
        assertOrder(listOf("rules", "nodes", "profiles", "dns"))
    }

    @Test fun cancelRestoresScrollingFromTheGapBetweenCards() {
        showDashboard(scrollable = true)
        compose.runOnIdle { assertTrue("fixture can scroll", scroll.maxValue > 100) }
        val first = bounds("nodes")
        val gapX = (first.right + bounds("ip_test").left) / 2f
        compose.onRoot().performTouchInput {
            down(first.center)
            moveBy(Offset(0f, -100f), delayMillis = 32)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, scroll.value) }
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        compose.onRoot().performTouchInput {
            swipe(Offset(gapX, first.center.y), Offset(gapX, first.center.y - 100f), durationMillis = 300)
        }
        compose.runOnIdle {
            assertTrue("edit mode still scrolls from gaps after cancellation", scroll.value > 0)
            assertTrue(commits.isEmpty())
            assertEquals(0, visibilityChanges)
        }
    }

    @Test fun hidingAndReAddingProfilesKeepsItsReorderedPosition() {
        showDashboard()
        dragTo("profiles", "rules")
        val reordered = compose.runOnIdle { order.value }
        compose.onNode(hasText("−") and hasAnyAncestor(hasTestTag("shortcut-profiles"))).performClick()
        compose.onNodeWithTag("shortcut-profiles").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(reordered, order.value)
            assertEquals(reordered, settings.shortcutOrder)
            assertEquals(setOf("nodes", "rules", "dns"), visible.value)
            assertEquals(visible.value, SettingsManager(ApplicationProvider.getApplicationContext<Context>()).visibleShortcuts)
        }
        compose.onNodeWithText("Add Shortcut (3)").performClick()
        compose.onNodeWithText("Profiles").performClick()
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.waitForIdle()
        assertOrder(listOf("nodes", "rules", "profiles", "dns"), expectedVisibilityChanges = 2)
    }

    @Test fun reorderedProfilesStillOpensItsPanelOnANormalTap() {
        showDashboard(touchVariant = ShortcutTouchVariant.FingerTilt)
        dragTo("profiles", "rules")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("shortcut-profiles").performTouchInput { click(center) }
        compose.onNodeWithTag("shortcut-panel-profiles-sheet").assertExists()
        compose.runOnIdle {
            assertEquals(listOf("nodes", "rules", "profiles", "dns"), order.value.filter { it in visible.value })
            assertEquals(1, commits.size)
            assertEquals(0, profileNavigations)
            assertEquals(0, vpnToggles)
        }
    }

    @Test fun nonEditingProfileSlotRetainsItsSemanticClickAction() {
        showDashboard(editing = false, touchVariant = ShortcutTouchVariant.FingerTilt)
        compose.onNodeWithTag("shortcut-profiles").performClick()
        compose.onNodeWithTag("shortcut-panel-profiles-sheet").assertExists()
        compose.runOnIdle {
            assertTrue(commits.isEmpty())
            assertEquals(initialOrder, order.value)
            assertEquals(0, visibilityChanges)
            assertEquals(0, profileNavigations)
            assertEquals(0, vpnToggles)
        }
    }

    @Test fun nonEditingVpnStillTogglesDirectlyInsteadOfReorderingOrOpeningAPanel() {
        visible.value = setOf("vpn")
        showDashboard(editing = false, touchVariant = ShortcutTouchVariant.FingerTilt)
        compose.onNodeWithTag("shortcut-vpn").performClick()
        compose.runOnIdle {
            assertEquals(1, vpnToggles)
            assertTrue(commits.isEmpty())
            assertEquals(initialOrder, order.value)
            assertEquals(0, visibilityChanges)
        }
        compose.onNodeWithTag("shortcut-panel-vpn-sheet").assertDoesNotExist()
    }

    @Test fun nonEditingVerticalSwipeFromACardStillScrollsWithoutReordering() {
        showDashboard(scrollable = true, editing = false, touchVariant = ShortcutTouchVariant.FingerTilt)
        val first = bounds("nodes")
        compose.onRoot().performTouchInput {
            down(first.center)
            moveBy(Offset(0f, -40f), delayMillis = 32)
            moveBy(Offset(0f, -70f), delayMillis = 32)
            up()
        }
        compose.runOnIdle {
            assertTrue("normal card swipe scrolls the page", scroll.value > 0)
            assertTrue(commits.isEmpty())
            assertEquals(0, visibilityChanges)
            assertEquals(0, profileNavigations)
            assertEquals(0, vpnToggles)
        }
        compose.onNodeWithTag("shortcut-panel-nodes-sheet").assertDoesNotExist()
    }

    @Test fun leavingEditModeDuringADragReleasesScrollWithoutCommitting() {
        showDashboard(scrollable = true)
        val first = bounds("nodes")
        val gapX = (first.right + bounds("ip_test").left) / 2f
        compose.onRoot().performTouchInput {
            down(first.center)
            moveBy(Offset(0f, -100f), delayMillis = 32)
        }
        compose.waitForIdle()
        // A semantic action also models Done being activated by keyboard/accessibility
        // while a pointer is still down; pointerInput disposal must clear the drag lock.
        compose.onNodeWithText("Done").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        compose.onRoot().performTouchInput {
            swipe(Offset(gapX, first.center.y), Offset(gapX, first.center.y - 100f), durationMillis = 300)
        }
        compose.runOnIdle {
            assertTrue("disposing the drag input releases page scrolling", scroll.value > 0)
            assertTrue(commits.isEmpty())
            assertEquals(0, visibilityChanges)
        }
    }

    @Test fun externalOrderChangeDuringDragDoesNotCommitAStalePreview() {
        showDashboard()
        val first = bounds("nodes")
        compose.onRoot().performTouchInput {
            down(first.center)
            moveBy(Offset(0f, 110f), delayMillis = 32)
        }
        compose.waitForIdle()
        compose.runOnIdle { order.value = initialOrder.reversed() }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("changing the saved order cancels rather than overwrites it", commits.isEmpty())
            assertEquals(initialOrder.reversed(), order.value)
            assertEquals(0, visibilityChanges)
        }
        assertGrid(listOf("dns", "rules", "profiles", "nodes"))
    }

    private fun showDashboard(
        scrollable: Boolean = false,
        editing: Boolean = true,
        touchVariant: ShortcutTouchVariant? = null
    ) {
        if (scrollable) {
            order.value = initialOrder + listOf("request_logs", "routing", "traffic", "plugins", "network_share", "core_logs")
            visible.value = order.value.filterNot { it == "future_shortcut" }.toSet()
        }
        settings = SettingsManager(ApplicationProvider.getApplicationContext<Context>()).apply {
            visibleShortcuts = visible.value
            shortcutOrder = order.value
        }
        GlassDefaults.enabled = false
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "en",
                    LocalShortcutTouchVariant provides touchVariant
                ) {
                    NexusGlassBackground {
                        DashboardScreen(
                            scrollState = scroll,
                            vpnState = VpnState(status = VpnState.Status.STOPPED),
                            trafficMetrics = MutableStateFlow(TrafficMetrics()),
                            currentMode = TunnelMode.RULE,
                            activeProfileName = "Fixture", mainGroupName = "", mainGroupProxies = emptyList(),
                            activeNodeName = "", totalNodes = 0, totalRules = 0,
                            visibleShortcuts = visible.value, shortcutOrder = order.value,
                            onToggleVpn = { vpnToggles++ }, onModeSelected = {}, onSelectProxy = { _, _ -> }, onHealthCheck = {},
                            onNavigateToProfiles = { profileNavigations++ }, onNavigateToAppRouting = {}, onNavigateToLogs = {},
                            onUpdateVisibleShortcuts = { visibilityChanges++; visible.value = it; settings.visibleShortcuts = it },
                            onUpdateShortcutOrder = { commits += it; order.value = it; settings.shortcutOrder = it }
                        )
                    }
                }
            }
        }
        if (editing) compose.onNodeWithText("Edit").performClick()
        compose.waitForIdle()
    }

    private fun bounds(id: String): Rect = compose.onNodeWithTag("shortcut-$id").fetchSemanticsNode().boundsInRoot

    private fun dragTo(from: String, to: String, holdMillis: Long = 0) {
        val start = bounds(from).center
        val delta = bounds(to).center - start
        compose.onRoot().performTouchInput {
            down(start)
            if (holdMillis > 0) advanceEventTime(holdMillis)
            moveBy(delta / 2f, delayMillis = 32)
        }
        compose.waitForIdle()
        compose.onRoot().performTouchInput {
            moveBy(delta / 2f, delayMillis = 32)
            up()
        }
        compose.waitForIdle()
    }

    /** Start the gesture, then measure from the original slot so touch slop is not part of the threshold. */
    private fun dragByInGrid(id: String, dy: Float) {
        val before = bounds(id)
        compose.onRoot().performTouchInput {
            down(before.center)
            moveBy(Offset(0f, if (dy > 0) 40f else -40f), delayMillis = 32)
        }
        compose.waitForIdle()
        val moved = bounds(id).top - before.top
        compose.onRoot().performTouchInput {
            moveBy(Offset(0f, dy - moved), delayMillis = 32)
            up()
        }
        compose.waitForIdle()
    }

    private fun assertOrder(
        expectedVisible: List<String>,
        commitCount: Int = 1,
        expectedVisibilityChanges: Int = 0
    ) {
        compose.runOnIdle {
            assertEquals(commitCount, commits.size)
            assertEquals(expectedVisible, order.value.filter { it in visible.value })
            assertEquals(initialOrder.toSet(), order.value.toSet())
            assertEquals(initialOrder.size, order.value.size)
            assertEquals(setOf("nodes", "profiles", "rules", "dns"), visible.value)
            assertEquals(expectedVisibilityChanges, visibilityChanges)
            assertEquals(0, vpnToggles)
            assertEquals(0, profileNavigations)
            val reloaded = SettingsManager(ApplicationProvider.getApplicationContext<Context>())
            assertEquals("callback persists the complete order", order.value, reloaded.shortcutOrder)
            assertEquals("ordering leaves visibility untouched", visible.value, reloaded.visibleShortcuts)
            assertEquals("dragging a card does not scroll the page", 0, scroll.value)
        }
        assertGrid(expectedVisible)
        compose.onNodeWithTag("shortcut-panel-nodes-sheet").assertDoesNotExist()
    }

    private fun assertGrid(expectedVisible: List<String>) {
        expectedVisible.forEachIndexed { index, id ->
            val first = bounds(expectedVisible.first())
            val actual = bounds(id)
            assertEquals("row for $id", first.top + (index / 2) * (first.height + 12f), actual.top, 1f)
            assertEquals("column for $id", first.left + (index % 2) * (first.width + 12f), actual.left, 1f)
        }
    }
}
