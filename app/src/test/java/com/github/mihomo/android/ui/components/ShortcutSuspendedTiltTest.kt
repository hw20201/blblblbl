package com.github.mihomo.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.ui.glass.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Checks fixed hit bounds and tap behavior at multiple touch points on the production card.
 * Robolectric's localToRoot matrix is orthographic here, so it cannot validate 3D visual
 * contraction; that still needs a physical-device check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutSuspendedTiltTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun lightEdgesKeepHitAreaAndTapBehavior() = verify(false)
    @Test fun darkEdgesKeepHitAreaAndTapBehavior() = verify(true)

    private fun verify(dark: Boolean) {
        val level = mutableStateOf(ShortcutGlassLevel.Normal)
        var clicks = 0
        compose.setContent {
            CompositionLocalProvider(
                LocalShortcutTouchVariant provides ShortcutTouchVariant.FingerTilt,
                LocalShortcutMotionEnabled provides true,
                LocalGlassSettings provides GlassSettings.DEFAULT.copy(shortcutGlassLevel = level.value),
                LocalGlassTokens provides (if (dark) GlassPalettes.MIDNIGHT else GlassPalettes.CREAM).toTokens()
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoonShortcutCard(Modifier.size(190.dp, 118.dp), "Tilt", "", icon = {}, onClick = { clicks++ })
                }
            }
        }
        compose.waitForIdle()
        val card = compose.onNodeWithTag("shortcut-card-Tilt")
        val restSlot = card.fetchSemanticsNode().boundsInRoot
        for (choice in ShortcutGlassLevel.entries) {
            compose.runOnIdle { level.value = choice }; compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            card.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(120); compose.waitForIdle()
            assertEquals("$choice centre press does not translate slot", restSlot, card.fetchSemanticsNode().boundsInRoot)
            card.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
            for ((x,y) in listOf(.5f to .1f,.5f to .9f,.1f to .5f,.9f to .5f)) {
                card.performTouchInput { down(Offset(width*x,height*y)) }
                compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
                assertEquals("fixed hit area survives tilt",restSlot,card.fetchSemanticsNode().boundsInRoot)
                card.performTouchInput { up() }
                compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
            }
            compose.mainClock.autoAdvance = true
        }
        compose.runOnIdle { assertEquals("all normal taps remain clickable",15,clicks) }
    }
}
