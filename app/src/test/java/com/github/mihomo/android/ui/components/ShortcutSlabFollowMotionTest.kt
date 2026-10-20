package com.github.mihomo.android.ui.components

import androidx.activity.ComponentActivity
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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.ui.glass.LocalShortcutTunedMaterialEnabled
import com.github.mihomo.android.ui.glass.ShortcutRefraction
import org.junit.Assert.*
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
class ShortcutSlabFollowMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var optics: () -> ShortcutRefraction = { ShortcutRefraction() }
    private val variant = mutableStateOf(ShortcutTouchVariant.FingerTilt)
    private val tuned = mutableStateOf(true)
    private val motionEnabled = mutableStateOf(true)
    private val preview = mutableStateOf<ShortcutTouchPose?>(null)

    private fun content() {
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTunedMaterialEnabled provides tuned.value,
                LocalShortcutMotionEnabled provides motionEnabled.value) {
                val motion = rememberShortcutTouchMotion(variant.value, enabled = true, frozenPose = preview.value)
                SideEffect { optics = motion.refraction }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(190.dp, 118.dp).testTag("slab-slot")) {
                        Box(motion.modifier.fillMaxSize())
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }
    private fun advance(ms: Long) {
        // With autoAdvance disabled, waitForIdle alone does not commit recomposition.
        // First submit the changed target on a frame, then measure its animation.
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms); compose.waitForIdle()
    }
    private fun read() = compose.runOnIdle { optics() }

    @Test fun fourCornersKeepTrueContactAndOnlyBVisualPitchIsDampedOnce() {
        content()
        val slot = compose.onNodeWithTag("slab-slot")
        for (point in listOf(Offset(-.8f, -.8f), Offset(.8f, -.8f), Offset(-.8f, .8f), Offset(.8f, .8f))) {
            slot.performTouchInput { down(center + Offset(center.x * point.x, center.y * point.y)) }
            compose.waitForIdle()
            val initial = read()
            assertEquals("press must not create an animation already at its target", 0f, initial.roll, .001f)
            assertEquals(0f, initial.pitch, .001f)
            advance(160)
            val held = read()
            assertTrue(held.slabFollow)
            assertEquals("true touch x", point.x, held.x, .01f)
            assertEquals("true touch y must not be compressed", point.y, held.y, .01f)
            assertEquals("horizontal full throw", held.x * 1.25f, held.roll, .01f)
            assertEquals("vertical throw is 0.75deg, not 0.45deg", held.y * .75f, held.pitch, .01f)
            slot.performTouchInput { up() }; advance(1000)
            assertEquals(0f, read().pitch, .001f)
            assertEquals(0f, read().roll, .001f)
        }
    }

    @Test fun heldRetargetReleaseAndRepressUseOneContinuousAnimationState() {
        content()
        val slot = compose.onNodeWithTag("slab-slot")
        slot.performTouchInput { down(center + Offset(center.x * .6f, 0f)) }
        advance(160)
        val right = read().roll
        assertTrue(right > .6f)
        slot.performTouchInput { moveTo(center - Offset(center.x * .6f, 0f)) }
        compose.waitForIdle()
        assertEquals("retarget is not an immediate pose replacement", right, read().roll, .001f)
        advance(32)
        val mid = read().roll
        assertTrue("60ms follow interpolates toward the new contact: right=$right mid=$mid raw=${read()}", mid < right && mid > -.8f)
        advance(160)
        val left = read()
        assertEquals(left.x * 1.25f, left.roll, .01f)
        assertTrue(left.roll < -.5f)
        slot.performTouchInput { up() }; compose.waitForIdle()
        assertEquals("release must not switch to another animation state", left.roll, read().roll, .001f)
        advance(32)
        val releasing = read().roll
        assertTrue(abs(releasing) < abs(left.roll))
        slot.performTouchInput { down(center + Offset(center.x * .6f, 0f)) }
        compose.waitForIdle()
        assertEquals("repress starts from the current rebound", releasing, read().roll, .001f)
        advance(160)
        assertTrue(read().roll > .6f)
        slot.performTouchInput { cancel() }; advance(1000)
        assertEquals(0f, read().roll, .001f)
    }

    @Test fun otherVariantsAndLegacyKeepTheirOwnAnglesAndRawContact() {
        content()
        val slot = compose.onNodeWithTag("slab-slot")
        for ((style, material, pitch) in listOf(
            Triple(ShortcutTouchVariant.GentleFloat, true, .6f),
            Triple(ShortcutTouchVariant.BuoyantSpring, true, .8f),
            Triple(ShortcutTouchVariant.FingerTilt, false, 3.2f))) {
            compose.runOnIdle { variant.value = style; tuned.value = material }; advance(32)
            slot.performTouchInput { down(center + Offset(0f, center.y * .8f)) }
            advance(160)
            val held = read()
            assertFalse("new optical normal is isolated to tuned B", held.slabFollow)
            assertEquals("$style tuned=$material raw=$held", .8f, held.y, .01f)
            assertEquals(pitch, held.pitch, .01f)
            slot.performTouchInput { up() }; advance(1000)
        }
    }

    @Test fun reducedMotionAndFrozenPreviewDoNotCreateLiveFollowState() {
        motionEnabled.value = false
        content()
        val slot = compose.onNodeWithTag("slab-slot")
        slot.performTouchInput { down(center + Offset(center.x * .8f, center.y * .8f)) }
        advance(160)
        assertEquals(.8f, read().y, .01f)
        assertEquals(0f, read().roll, 0f)
        assertEquals(0f, read().pitch, 0f)
        slot.performTouchInput { up() }; advance(1000)
        compose.runOnIdle { motionEnabled.value = true; preview.value = ShortcutTouchPose(rotationX = -.5f, rotationY = .7f) }
        advance(1000)
        assertEquals(.5f, read().pitch, .001f)
        assertEquals(.7f, read().roll, .001f)
        slot.performTouchInput { down(center - Offset(center.x * .8f, center.y * .8f)) }
        advance(160)
        assertEquals("frozen pose is not replaced by a live touch target", .5f, read().pitch, .001f)
        assertEquals(.7f, read().roll, .001f)
        slot.performTouchInput { up() }; advance(1000)
    }
}
