package com.github.mihomo.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
import kotlin.math.hypot

/** Measures real foreground contraction and fixed hit bounds on the production card.
 * Robolectric's localToRoot matrix here is orthographic: opposite-sign perspective is
 * NOT validated by this test and still needs a physical-device check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutSuspendedTiltTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun lightEdgesTiltWithoutCentreJump() = verify(false)
    @Test fun darkEdgesTiltWithoutCentreJump() = verify(true)

    private fun verify(dark: Boolean) {
        val level = mutableStateOf(ShortcutGlassLevel.Normal)
        var coordinates: LayoutCoordinates? = null
        var clicks = 0
        compose.setContent {
            CompositionLocalProvider(
                LocalShortcutTouchVariant provides ShortcutTouchVariant.FingerTilt,
                LocalShortcutMotionEnabled provides true,
                LocalGlassSettings provides GlassSettings.DEFAULT.copy(shortcutGlassLevel = level.value),
                LocalGlassTokens provides (if (dark) GlassPalettes.MIDNIGHT else GlassPalettes.CREAM).toTokens()
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoonShortcutCard(Modifier.size(190.dp, 118.dp), "Tilt", "", icon = {
                        // Foreground descendant inherits the glass plane's actual graphics transform.
                        Box(Modifier.size(26.dp).onGloballyPositioned { coordinates = it })
                    }, onClick = { clicks++ })
                }
            }
        }
        compose.waitForIdle()
        val card = compose.onNodeWithTag("shortcut-card-Tilt")
        val restSlot = card.fetchSemanticsNode().boundsInRoot
        fun lengths(): List<Float> {
            val c = requireNotNull(coordinates)
            fun at(x: Float, y: Float) = c.localToRoot(Offset(x, y))
            fun dist(a: Offset,b: Offset)=hypot(a.x-b.x,a.y-b.y)
            val w=c.size.width.toFloat(); val h=c.size.height.toFloat()
            return listOf(dist(at(0f,0f),at(w,0f)),dist(at(0f,h),at(w,h)),
                dist(at(0f,0f),at(0f,h)),dist(at(w,0f),at(w,h)))
        }
        for (choice in ShortcutGlassLevel.entries) {
            compose.runOnIdle { level.value = choice }; compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            card.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(120); compose.waitForIdle()
            assertEquals("$choice centre press does not translate slot", restSlot, card.fetchSemanticsNode().boundsInRoot)
            val flat = compose.runOnIdle { lengths() }
            assertEquals(flat[0],flat[1],.01f); assertEquals(flat[2],flat[3],.01f)
            card.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
            val rest = compose.runOnIdle { lengths() }
            // Top/bottom change X pitch; left/right change Y roll, before drag slop.
            for ((x,y) in listOf(.5f to .1f,.5f to .9f,.1f to .5f,.9f to .5f)) {
                card.performTouchInput { down(Offset(width*x,height*y)) }
                compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
                val l=compose.runOnIdle { lengths() }
                val contracted = if (x==.5f) 2 else 0
                val unchanged = if (x==.5f) 0 else 2
                assertTrue("$choice $x/$y must actually tilt foreground: $l vs $rest",
                    rest[contracted]-l[contracted]>.05f)
                assertEquals("orthogonal axis must retain size",rest[unchanged],l[unchanged],.02f)
                assertEquals("fixed hit area survives tilt",restSlot,card.fetchSemanticsNode().boundsInRoot)
                card.performTouchInput { up() }
                compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
                val returned = compose.runOnIdle { lengths() }
                rest.zip(returned).forEach { (expected,actual) ->
                    assertEquals("release restores actual foreground",expected,actual,.01f)
                }
            }
            val targets=listOf(0f to -.8f,0f to .8f,-.8f to 0f,.8f to 0f)
                .map { (x,y) -> shortcutTouchTarget(ShortcutTouchVariant.FingerTilt,x,y) }
            assertTrue("target pitch signs oppose",targets[0].rotationX*targets[1].rotationX<0f)
            assertTrue("target roll signs oppose",targets[2].rotationY*targets[3].rotationY<0f)
            compose.mainClock.autoAdvance = true
        }
        compose.runOnIdle { assertEquals("all normal taps remain clickable",15,clicks) }
    }
}
