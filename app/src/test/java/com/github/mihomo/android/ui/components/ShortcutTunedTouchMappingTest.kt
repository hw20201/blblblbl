package com.github.mihomo.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.ui.glass.LocalShortcutTunedMaterialEnabled
import com.github.mihomo.android.ui.glass.ShortcutRefraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutTunedTouchMappingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun tunedFingerTiltUsesSubtleLabBPoseAndDirectionalRotations() {
        val center = shortcutTouchTarget(ShortcutTouchVariant.FingerTilt, 0f, 0f, tuned = true)
        assertEquals(1.003f, center.scale, 0f)
        assertEquals(0f, center.offsetXDp, 0f)
        assertEquals(0f, center.offsetYDp, 0f)
        listOf(-1f, 1f).forEach { x ->
            val pose = shortcutTouchTarget(ShortcutTouchVariant.FingerTilt, x, 0f, tuned = true)
            assertEquals(0f, pose.rotationX, 0f)
            assertEquals(1.25f * x, pose.rotationY, 0f)
        }
        listOf(-1f, 1f).forEach { y ->
            val pose = shortcutTouchTarget(ShortcutTouchVariant.FingerTilt, 0f, y, tuned = true)
            assertEquals(-1.25f * y, pose.rotationX, 0f)
            assertEquals(0f, pose.rotationY, 0f)
        }
    }

    @Test fun allDefaultVariantsHaveSmallVisualMotionAndLegacyMaterialMappingRemains() {
        val gentle = shortcutTouchTarget(ShortcutTouchVariant.GentleFloat, .5f, -.5f, tuned = true)
        assertEquals(.997f, gentle.scale, 0f)
        assertEquals(0f, gentle.offsetYDp, 0f)
        assertEquals(.375f, gentle.rotationX, 0f)
        assertEquals(.375f, gentle.rotationY, 0f)
        val buoyant = shortcutTouchTarget(ShortcutTouchVariant.BuoyantSpring, -.5f, .5f, tuned = true)
        assertEquals(.996f, buoyant.scale, 0f)
        assertEquals(0f, buoyant.offsetYDp, 0f)
        assertEquals(-.5f, buoyant.rotationX, 0f)
        assertEquals(-.5f, buoyant.rotationY, 0f)
        val legacy = shortcutTouchTarget(ShortcutTouchVariant.FingerTilt, 1f, 1f, tuned = false)
        assertEquals(.975f, legacy.scale, 0f)
        assertEquals(-2f, legacy.offsetYDp, 0f)
        assertEquals(4f, legacy.rotationY, 0f)
    }

    @Test fun actualPointerSamplesFourCornersCenterAndKeepsPointThroughRelease() {
        lateinit var readOptics: () -> ShortcutRefraction
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTunedMaterialEnabled provides true) {
                val motion = rememberShortcutTouchMotion(ShortcutTouchVariant.FingerTilt, enabled = true)
                SideEffect { readOptics = motion.refraction }
                // The app card is inside content, not under the system status bar.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(motion.modifier.size(190.dp, 118.dp).testTag("mapping-card"))
                }
            }
        }
        compose.waitForIdle()
        val target = compose.onNodeWithTag("mapping-card")
        // Sample the four visible rounded corners (10% inset), not transparent outer pixels.
        val points = listOf(
            Offset(19f, 12f) to (-.8f to -.8f),
            Offset(171f, 12f) to (.8f to -.8f),
            Offset(19f, 106f) to (-.8f to .8f),
            Offset(171f, 106f) to (.8f to .8f),
            Offset(95f, 59f) to (0f to 0f)
        )
        points.forEach { (point, expected) ->
            compose.mainClock.autoAdvance = false
            target.performTouchInput { down(point) }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(95)
            compose.waitForIdle()
            compose.runOnIdle {
                val actual = readOptics()
                assertEquals("normalized x at $point", expected.first, actual.x, .03f)
                assertEquals("normalized y at $point", expected.second, actual.y, .03f)
                assertTrue("contact lift starts visibly", actual.lift >= .35f)
            }
            target.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(450)
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals("release retains last x", expected.first, readOptics().x, .03f)
                assertEquals("release retains last y", expected.second, readOptics().y, .03f)
                assertEquals("release restores clear contact", 0f, readOptics().frostLift, .01f)
                assertEquals("release restores neutral optics", 0f, readOptics().lift, .01f)
            }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        }
    }

    @Test fun movingHeldPointerUpdatesPositionContinuously() {
        val optics = mutableStateOf(ShortcutRefraction())
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTunedMaterialEnabled provides true) {
                val motion = rememberShortcutTouchMotion(ShortcutTouchVariant.FingerTilt, enabled = true)
                SideEffect { optics.value = motion.refraction() }
                Box(motion.modifier.size(190.dp, 118.dp).testTag("moving-card"))
            }
        }
        val target = compose.onNodeWithTag("moving-card")
        compose.mainClock.autoAdvance = false
        target.performTouchInput {
            down(Offset(19f, 59f))
            moveTo(Offset(95f, 59f), delayMillis = 32)
        }
        compose.mainClock.advanceTimeBy(120); compose.waitForIdle()
        val middle = optics.value.x
        target.performTouchInput { moveTo(Offset(171f, 59f), delayMillis = 32) }
        compose.mainClock.advanceTimeBy(120); compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("held pointer continuously updates normalized x: $middle -> ${optics.value.x}", optics.value.x > middle + .5f)
            assertTrue(abs(optics.value.x - .8f) < .05f)
        }
        target.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        compose.mainClock.autoAdvance = true
    }
}
