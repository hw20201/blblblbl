package com.github.arca.android.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.data.PanelAnimationSpeed
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.components.LocalPanelAnimationSpeed
import com.github.arca.android.ui.components.LocalShortcutMotionEnabled
import com.github.arca.android.ui.components.LocalShortcutTouchVariant
import com.github.arca.android.ui.components.ShortcutTouchVariant
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.NexusGlassBackground
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compare the real rendered source face, not the unchanged slot/hit-test rectangle.
 * The old dashboard returned to the slot, six dp below B's resting glass plane.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardShortcutMorphHandoffTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val oldGlass = GlassDefaults.enabled
    private val panelTag = "shortcut-panel-profiles-sheet"

    @After fun restoreGlobals() {
        compose.mainClock.autoAdvance = true
        GlassDefaults.enabled = oldGlass
    }

    @Test fun suspendedCardReturnsDirectlyAtFastSpeed() = verifyHandoff(PanelAnimationSpeed.FAST)
    @Test fun suspendedCardReturnsDirectlyAtMediumSpeed() = verifyHandoff(PanelAnimationSpeed.MEDIUM)
    @Test fun suspendedCardReturnsDirectlyAtSlowSpeed() = verifyHandoff(PanelAnimationSpeed.SLOW)
    @Test fun gentlePressReturnsWithoutResidualLift() = verifyHandoff(PanelAnimationSpeed.FAST, ShortcutTouchVariant.GentleFloat)
    @Test fun buoyantPressReturnsWithoutResidualLift() = verifyHandoff(PanelAnimationSpeed.FAST, ShortcutTouchVariant.BuoyantSpring)
    @Test fun scrolledSuspendedCardReturnsToItsCurrentRestingFace() = verifyHandoff(PanelAnimationSpeed.MEDIUM, scrollWhileOpen = true)
    @Test fun quickReverseSuspendedCardHasNoSecondMotion() = verifyHandoff(PanelAnimationSpeed.FAST, openingWaitMillis = 64L)
    @Test fun quickReverseGentleCardHasNoSecondMotion() = verifyHandoff(PanelAnimationSpeed.FAST, ShortcutTouchVariant.GentleFloat, openingWaitMillis = 64L)
    @Test fun quickReverseBuoyantCardHasNoSecondMotion() = verifyHandoff(PanelAnimationSpeed.FAST, ShortcutTouchVariant.BuoyantSpring, openingWaitMillis = 64L)
    @Test fun editedCardReturnsToItsReorderedRestingFace() = verifyHandoff(PanelAnimationSpeed.FAST, reorderBeforeOpen = true)
    @Test fun upperCornerTapHasTheSameReturnAnchor() = verifyHandoff(PanelAnimationSpeed.FAST, tapFraction = Offset(.12f, .12f))

    private fun verifyHandoff(
        speed: PanelAnimationSpeed,
        variant: ShortcutTouchVariant = ShortcutTouchVariant.FingerTilt,
        scrollWhileOpen: Boolean = false,
        openingWaitMillis: Long = speed.openDurationMillis.toLong() + 128,
        reorderBeforeOpen: Boolean = false,
        tapFraction: Offset = Offset(.82f, .8f)
    ) {
        val scroll = ScrollState(0)
        var reordered: List<String>? = null
        GlassDefaults.enabled = true
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "en",
                    LocalShortcutTouchVariant provides variant,
                    LocalShortcutMotionEnabled provides true,
                    LocalPanelAnimationSpeed provides speed
                ) {
                    ProvideGlassTheme {
                        // A nonzero host origin also catches mixing root/window/overlay coordinates.
                        Box(Modifier.fillMaxSize().padding(start = 9.dp, top = 23.dp)) {
                            NexusGlassBackground {
                                val shortcuts = listOf("profiles", "nodes", "rules", "request_logs", "dns", "routing", "traffic", "vpn")
                                DashboardScreen(
                                    scrollState = scroll,
                                    vpnState = VpnState(status = VpnState.Status.STOPPED),
                                    currentMode = TunnelMode.RULE,
                                    activeProfileName = "Fixture", mainGroupName = "",
                                    mainGroupProxies = emptyList(), activeNodeName = "",
                                    totalNodes = 0, totalRules = 0,
                                    visibleShortcuts = shortcuts.toSet(), shortcutOrder = shortcuts,
                                    onToggleVpn = {}, onModeSelected = {}, onSelectProxy = { _, _ -> },
                                    onHealthCheck = {}, onNavigateToProfiles = {}, onNavigateToAppRouting = {},
                                    onNavigateToLogs = {}, onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = { reordered = it }
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val slot = compose.onNodeWithTag("shortcut-profiles")
        if (reorderBeforeOpen) {
            compose.onNodeWithText("Edit").performClick()
            slot.performTouchInput {
                down(center)
                moveTo(center + Offset(1f, 1f), delayMillis = 650)
                moveTo(center + Offset(190f, 0f), delayMillis = 80)
                up()
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals("long-press editing still commits the new order", listOf("nodes", "profiles"), reordered?.take(2))
            }
            compose.onNodeWithTag(panelTag).assertDoesNotExist()
            compose.onNodeWithText("Done").performClick()
            compose.waitForIdle()
        }
        val slotBounds = slot.fetchSemanticsNode().boundsInRoot
        val restingLabel = sourceLabel()
        compose.mainClock.autoAdvance = false
        slot.performTouchInput { down(Offset(width * tapFraction.x, height * tapFraction.y)) }
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        assertEquals("press retains the original hit slot", slotBounds, slot.fetchSemanticsNode().boundsInRoot)
        slot.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(openingWaitMillis)
        compose.waitForIdle()
        compose.onNodeWithTag(panelTag).assertExists()
        val scrollDelta = if (scrollWhileOpen) 28f else 0f
        if (scrollWhileOpen) {
            compose.runOnIdle { assertEquals(scrollDelta, scroll.dispatchRawDelta(scrollDelta), .01f) }
            compose.waitForIdle()
        }
        val expected = restingLabel.translate(Offset(0f, -scrollDelta))
        if (openingWaitMillis < speed.openDurationMillis) {
            // The close icon is still outside the growing panel's clipped hit area. Back is
            // available from the first mounted frame and exercises the real dismissal handler.
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        } else {
            compose.onNodeWithTag("shortcut-panel-close").performClick()
        }
        var collapsedLabel: Rect? = null
        var collapsedFrames = 0
        var elapsed = 0L
        while (elapsed <= speed.closeDurationMillis + 96L) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            elapsed += 16L
            val panel = compose.onAllNodesWithTag(panelTag).fetchSemanticsNodes().singleOrNull() ?: break
            if (kotlin.math.abs(panel.boundsInRoot.width - slotBounds.width) < 1f &&
                kotlin.math.abs(panel.boundsInRoot.height - slotBounds.height) < 1f) {
                collapsedLabel = compose.onNode(
                    hasText("Profiles") and hasAnyAncestor(hasTestTag(panelTag)) and
                        !hasAnyAncestor(hasTestTag("shortcut-panel-header")),
                    useUnmergedTree = true
                ).fetchSemanticsNode().boundsInRoot
                collapsedFrames++
            }
        }
        assertNotNull("sample the final collapsed face before it hands back", collapsedLabel)
        assertTrue("the collapsed endpoint is actually rendered", collapsedFrames > 0)
        assertRectEquals("closing face lands directly on the resting card face", expected, collapsedLabel!!)
        compose.onNodeWithTag(panelTag).assertDoesNotExist()
        assertRectEquals("first returned card frame stays at the same endpoint", expected, sourceLabel())
        repeat(8) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertRectEquals("returned card must not drift in a second animation", expected, sourceLabel())
        }
    }

    private fun sourceLabel(): Rect = compose.onNode(
        hasText("Profiles") and hasAnyAncestor(hasTestTag("shortcut-profiles")),
        useUnmergedTree = true
    ).fetchSemanticsNode().boundsInRoot

    private fun assertRectEquals(message: String, expected: Rect, actual: Rect) {
        assertEquals("$message: left", expected.left, actual.left, 1f)
        assertEquals("$message: top", expected.top, actual.top, 1f)
        assertEquals("$message: right", expected.right, actual.right, 1f)
        assertEquals("$message: bottom", expected.bottom, actual.bottom, 1f)
    }
}
