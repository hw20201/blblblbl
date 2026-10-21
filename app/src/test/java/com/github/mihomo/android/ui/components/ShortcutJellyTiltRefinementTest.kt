package com.github.mihomo.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.ui.glass.ShortcutRefraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutJellyTiltRefinementTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun tiltTracksAbsolutePositionInEightDirectionsWithoutTranslationOrScale() {
        val directions = listOf(
            Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f), Offset(-1f, 1f),
            Offset(-1f, 0f), Offset(-1f, -1f), Offset(0f, -1f), Offset(1f, -1f), Offset.Zero
        )
        directions.forEach { point ->
            val pose = shortcutJellyTiltTarget(point.x, point.y)
            assertEquals("pitch follows vertical contact", -point.y * 3f, pose.rotationX, 0f)
            assertEquals("roll follows horizontal contact", point.x * 3f, pose.rotationY, 0f)
            assertEquals(1f, pose.scale, 0f)
            assertEquals(0f, pose.offsetXDp, 0f)
            assertEquals(0f, pose.offsetYDp, 0f)
            val halfway = shortcutJellyTiltTarget(point.x / 2f, point.y / 2f)
            assertEquals("no dead zone or edge-only response", pose.rotationX / 2f, halfway.rotationX, 0f)
            assertEquals("no dead zone or edge-only response", pose.rotationY / 2f, halfway.rotationY, 0f)
        }
        assertEquals(shortcutJellyTiltTarget(1f, -1f), shortcutJellyTiltTarget(4f, -4f))
        // The older non-dock comparison path is deliberately not retuned.
        assertEquals(6f, shortcutTouchTarget(ShortcutTouchVariant.FingerTilt, 1f, 0f).rotationY, 0f)
    }

    @Test fun travelHasHalfGainAndTwoDpRadialCapIncludingDiagonals() {
        val small = shortcutJellyTravel(Offset(10f, -6f), 2f)
        assertEquals(.45f, small.x, .00001f)
        assertEquals(-.27f, small.y, .00001f)
        assertEquals(Offset.Zero, shortcutJellyTravel(Offset.Zero, 2f))
        listOf(Offset(100f, 0f), Offset(0f, -100f), Offset(100f, 100f), Offset(-100f, 100f)).forEach { point ->
            val out = shortcutJellyTravel(point, 2f)
            assertEquals("saturates at 2dp, not 4dp", 2f, out.getDistance(), .00001f)
            assertTrue("preserves signed direction", out.x * point.x + out.y * point.y > 0f)
            assertEquals("no diagonal axis bias", 0f, out.x * point.y - out.y * point.x, .0001f)
        }
    }

    @Test fun reducedVisualTiltDoesNotCompressOpticalContactAndReleaseKeepsLastContact() {
        lateinit var optics: () -> ShortcutRefraction
        lateinit var slot: LayoutCoordinates
        lateinit var plane: LayoutCoordinates
        compose.setContent {
            val motion = rememberShortcutTouchMotion(ShortcutTouchVariant.FingerTilt, enabled = true)
            SideEffect { optics = motion.refraction }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.size(174.dp, 118.dp).onGloballyPositioned { slot = it }
                    .then(motion.gestureModifier).testTag("jelly-optics")) {
                    Box(motion.visualModifier.fillMaxSize().onGloballyPositioned { plane = it })
                }
            }
        }
        val card = compose.onNodeWithTag("jelly-optics")
        val point = Offset(156f, 20f)
        card.performTouchInput { down(point) }
        compose.waitForIdle()
        // Repeat at the same real point after the plane has tilted. This checks the actual
        // inverse-mapped plane contact rather than assuming orthographic/perspective support.
        card.performTouchInput { moveTo(point + Offset(1f, 0f), delayMillis = 30) }
        compose.waitForIdle()
        card.performTouchInput { moveTo(point, delayMillis = 30) }
        compose.waitForIdle()
        card.performTouchInput { moveTo(point, delayMillis = 30) }
        compose.waitForIdle()
        lateinit var held: ShortcutRefraction
        compose.runOnIdle {
            held = optics()
            val local = plane.localPositionOf(slot, point)
            assertEquals("optics uses full plane-local X", local.x / plane.size.width * 2f - 1f, held.x, .015f)
            assertEquals("optics uses full plane-local Y", local.y / plane.size.height * 2f - 1f, held.y, .015f)
            assertTrue("halving visual tilt must not halve optical X", held.x > .7f)
            assertTrue("halving visual tilt must not halve optical Y", held.y < -.5f)
            assertTrue("stationary edge contact retains optical response", held.lift >= .35f)
        }
        card.performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("release does not jump lens to centre", held.x, optics().x, .015f)
            assertEquals("release does not jump lens to centre", held.y, optics().y, .015f)
            assertEquals("release returns optical lift to rest", 0f, optics().lift, .001f)
        }
    }

    @Test fun ordinaryHorizontalFollowDoesNotClickOrDisableScrollAndNextTapWorks() {
        var clicks = 0
        val pickups = mutableListOf<Boolean>()
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTouchVariant provides ShortcutTouchVariant.FingerTilt) {
                LoonShortcutCard(Modifier.size(174.dp, 118.dp), "JellyFollow", "", icon = {},
                    onPickupChanged = { pickups += it }, onClick = { clicks++ })
            }
        }
        val card = compose.onNodeWithTag("shortcut-card-JellyFollow")
        val rest = card.fetchSemanticsNode().boundsInRoot
        card.performTouchInput {
            down(center)
            moveTo(center + Offset(60f, 2f), delayMillis = 80)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("ordinary drag is not navigation", 0, clicks)
            assertTrue("ordinary follow cannot disable page scrolling", pickups.isEmpty())
            assertEquals("hit rectangle stays fixed", rest, card.fetchSemanticsNode().boundsInRoot)
        }
        card.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals("next intentional tap works", 1, clicks) }
    }

    @Test fun smallJitterThenVerticalSlopYieldsWithoutPickupOrReacquisition() = verifyVerticalYield(false)

    @Test fun ordinaryHorizontalFollowThenVerticalSlopYieldsWithoutPickup() = verifyVerticalYield(true)

    @Test fun ordinaryHorizontalFollowCannotBecomeHeldPickupAfterTimeout() = verifyVerticalYield(true, 650)

    private fun verifyVerticalYield(horizontalFirst: Boolean, verticalDelay: Long = 40) {
        lateinit var scroll: ScrollState
        lateinit var optics: () -> ShortcutRefraction
        val pickups = mutableListOf<Boolean>()
        compose.setContent {
            scroll = rememberScrollState()
            var pickedUp by remember { mutableStateOf(false) }
            val motion = rememberShortcutTouchMotion(ShortcutTouchVariant.FingerTilt, enabled = true,
                onPickupChanged = { pickups += it; pickedUp = it })
            SideEffect { optics = motion.refraction }
            // Match Dashboard: a pickup notification disables the scroll detector.
            Column(Modifier.fillMaxSize().verticalScroll(scroll, enabled = !pickedUp)) {
                Box(Modifier.size(300.dp, 180.dp))
                Box(motion.gestureModifier.size(174.dp, 118.dp).testTag("jelly-yield")) {
                    Box(motion.visualModifier.fillMaxSize())
                }
                Box(Modifier.size(300.dp, 900.dp))
            }
        }
        val card = compose.onNodeWithTag("jelly-yield")
        card.performTouchInput {
            down(center)
            moveTo(center + Offset(4f, -3f), delayMillis = 40)
        }
        if (horizontalFirst) {
            card.performTouchInput { moveTo(center + Offset(60f, -3f), delayMillis = 40) }
            compose.runOnIdle { assertTrue("ordinary follow must not disable page scrolling", pickups.isEmpty()) }
        }
        card.performTouchInput {
            moveTo(center + Offset(if (horizontalFirst) 60f else 5f, -20f), delayMillis = verticalDelay)
            moveTo(center + Offset(if (horizontalFirst) 60f else 6f, -65f), delayMillis = 40)
        }
        val expectedPickups = emptyList<Boolean>()
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("vertical slop hands scrolling to page", scroll.value > 0)
            assertEquals("yield closes any ordinary pickup exactly once", expectedPickups, pickups)
            assertEquals("yielded contact returns to rest before finger-up", 0f, optics().lift, .001f)
        }
        card.performTouchInput {
            moveTo(center + Offset(60f, -70f), delayMillis = 650)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("same contact cannot reacquire after long timeout", expectedPickups, pickups) }
    }
}
