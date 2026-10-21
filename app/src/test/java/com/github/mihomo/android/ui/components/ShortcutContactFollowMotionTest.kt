package com.github.mihomo.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutContactFollowMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun variantsTrackLocalContactAndKeepRestFrostAndReleaseCoordinates() {
        val variant = mutableStateOf(ShortcutTouchVariant.GentleFloat)
        var optics: () -> ShortcutRefraction = { ShortcutRefraction() }
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTunedMaterialEnabled provides true,
                LocalShortcutOpticalFollowupEnabled provides true, LocalShortcutContactFollowEnabled provides true,
                LocalShortcutMotionEnabled provides true) {
                val motion = rememberShortcutTouchMotion(variant.value, enabled = true)
                SideEffect { optics = motion.refraction }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(190.dp, 118.dp).testTag("slot")) {
                        Box(motion.modifier.fillMaxSize().testTag("contact"))
                    }
                }
            }
        }
        ShortcutTouchVariant.entries.forEach { choice ->
            compose.runOnIdle { variant.value = choice }; compose.waitForIdle()
            val node = compose.onNodeWithTag("contact")
            val frame = compose.onNodeWithTag("slot")
            val original = node.fetchSemanticsNode().boundsInRoot
            compose.mainClock.autoAdvance = false
            frame.performTouchInput { down(center) }
            compose.waitForIdle()
            compose.runOnIdle {
                assertTrue("$choice: contact starts perceptibly", optics().contact >= .35f)
                assertEquals(SHORTCUT_CONTACT_REST_SOFTNESS, optics().frostLift, 0f)
                assertTrue(optics().contactFollow)
            }
            compose.mainClock.advanceTimeBy(120); compose.waitForIdle()
            val pressed = node.fetchSemanticsNode().boundsInRoot
            if (choice == ShortcutTouchVariant.FingerTilt) {
                assertEquals("B centre press does not resize the plane", original.width, pressed.width, .01f)
            } else {
                assertTrue("A/C retain contraction", pressed.width < original.width)
            }
            frame.performTouchInput { moveTo(center + Offset(center.x * .5f, 0f)) }
            compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
            val horizontal = compose.runOnIdle { optics() }
            assertTrue(horizontal.contact > .9f)
            assertTrue("horizontal contact follows finger", horizontal.x > .3f)
            assertEquals("middle-height touch stays middle", 0f, horizontal.y, .05f)
            frame.performTouchInput { moveTo(center + Offset(center.x * .5f, center.y * .4f)) }
            compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
            val moved = compose.runOnIdle { optics() }
            assertTrue("vertical motion after horizontal lock updates local press", moved.y > horizontal.y + .2f)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            if (choice == ShortcutTouchVariant.FingerTilt) {
                // Perspective changes the enclosing rectangle slightly; it is not a Y pickup.
                assertTrue("B remains near its slot", kotlin.math.abs(bounds.center.y - original.center.y) < 5f)
                assertTrue("B horizontal follow stays bounded including perspective", kotlin.math.abs(bounds.center.x - original.center.x) < 8f)
            } else {
                assertTrue("horizontal follow stays bounded", kotlin.math.abs(bounds.center.x - original.center.x) <= 4.5f)
                assertEquals("A/C have no vertical module drag", original.center.y, bounds.center.y, .3f)
            }
            var last = moved
            for (point in listOf(Offset(-.65f, -.6f), Offset(.65f, -.6f), Offset(.65f, .6f), Offset.Zero, Offset(-.65f, .6f))) {
                frame.performTouchInput { moveTo(center + Offset(center.x * point.x, center.y * point.y)) }
                compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
                last = compose.runOnIdle { optics() }
                val followX = (original.width * .5f * point.x * .09f).coerceIn(-4f, 4f)
                val expectedX = point.x - 2f * followX / original.width
                assertEquals("$choice: contact x follows the finger on the translated plane at $point", expectedX, last.x, .01f)
                assertEquals("$choice: contact y follows $point", point.y, last.y, .01f)
                assertTrue(last.contact > .9f)
            }
            frame.performTouchInput { up() }; compose.waitForIdle()
            compose.runOnIdle {
                assertEquals("release keeps last x", last.x, optics().x, 0f)
                assertEquals("release keeps last y", last.y, optics().y, 0f)
                assertEquals(SHORTCUT_CONTACT_REST_SOFTNESS, optics().frostLift, 0f)
            }
            compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
            compose.runOnIdle { assertEquals(0f, optics().contact, .001f) }
            compose.mainClock.autoAdvance = true; compose.waitForIdle()
        }
    }

    @Test fun verticalCancellationKeepsLatestPointWithoutPickupOrClick() {
        var optics: () -> ShortcutRefraction = { ShortcutRefraction() }
        var pickups = 0; var clicks = 0
        compose.setContent {
            CompositionLocalProvider(LocalShortcutContactFollowEnabled provides true,
                LocalShortcutMotionEnabled provides false) {
                val motion = rememberShortcutTouchMotion(ShortcutTouchVariant.FingerTilt, true,
                    onPickupChanged = { if (it) pickups++ })
                SideEffect { optics = motion.refraction }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(190.dp, 118.dp).testTag("cancel-slot")) {
                        Box(motion.gestureModifier.then(Modifier.clickable { clicks++ })
                            .then(motion.visualModifier).fillMaxSize())
                    }
                }
            }
        }
        val frame = compose.onNodeWithTag("cancel-slot")
        frame.performTouchInput { down(center) }; compose.waitForIdle()
        compose.runOnIdle { assertEquals(1f, optics().contact, 0f) }
        frame.performTouchInput { moveTo(center + Offset(0f, center.y * .4f)) }; compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("latest yield-point is kept", .4f, optics().y, .01f)
            assertEquals("vertical intent releases local press", 0f, optics().contact, 0f)
            assertEquals(0, pickups)
        }
        frame.performTouchInput { up() }; compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test fun reducedMotionTracksContactImmediatelyWithoutFrostFlash() {
        var optics: () -> ShortcutRefraction = { ShortcutRefraction() }
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTunedMaterialEnabled provides true,
                LocalShortcutOpticalFollowupEnabled provides true, LocalShortcutContactFollowEnabled provides true,
                LocalShortcutMotionEnabled provides false) {
                val motion = rememberShortcutTouchMotion(ShortcutTouchVariant.GentleFloat, enabled = true)
                SideEffect { optics = motion.refraction }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(motion.modifier.size(190.dp, 118.dp).testTag("reduced-contact"))
                }
            }
        }
        val node = compose.onNodeWithTag("reduced-contact")
        node.performTouchInput { down(center); moveTo(center + Offset(center.x * .5f, center.y * .25f)) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1f, optics().contact, 0f)
            assertEquals(SHORTCUT_CONTACT_REST_SOFTNESS, optics().frostLift, 0f)
            assertTrue(optics().x > .3f); assertTrue(optics().y > .1f)
            assertEquals("reduced motion has no animated lift", 0f, optics().lift, 0f)
        }
        node.performTouchInput { up() }; compose.waitForIdle()
        compose.runOnIdle { assertEquals(0f, optics().contact, 0f) }
    }
}
