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
import com.github.mihomo.android.ui.glass.LocalShortcutOpticalFollowupEnabled
import com.github.mihomo.android.ui.glass.LocalShortcutTunedMaterialEnabled
import com.github.mihomo.android.ui.glass.ShortcutRefraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ShortcutPickupPoseRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var optics: () -> ShortcutRefraction = { ShortcutRefraction() }
    private val variant = mutableStateOf(ShortcutTouchVariant.FingerTilt)
    private val tuned = mutableStateOf(true)
    private val followup = mutableStateOf(true)
    private val motionEnabled = mutableStateOf(true)
    private val preview = mutableStateOf<ShortcutTouchPose?>(null)

    private fun content() {
        compose.setContent {
            CompositionLocalProvider(
                LocalShortcutTunedMaterialEnabled provides tuned.value,
                LocalShortcutOpticalFollowupEnabled provides followup.value,
                LocalShortcutMotionEnabled provides motionEnabled.value
            ) {
                val motion = rememberShortcutTouchMotion(variant.value, enabled = true, frozenPose = preview.value)
                SideEffect { optics = motion.refraction }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // Keep pointer coordinates in an untransformed slot; animate only its inner visual.
                    Box(Modifier.size(190.dp, 118.dp).testTag("pickup-pose-slot")) {
                        Box(motion.modifier.fillMaxSize())
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun advance(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun read() = compose.runOnIdle { optics() }

    @Test fun tunedBFourCornersKeepRawContactAndFollowTargetPose() {
        content()
        val slot = compose.onNodeWithTag("pickup-pose-slot")
        for (point in listOf(Offset(-.8f, -.8f), Offset(.8f, -.8f), Offset(-.8f, .8f), Offset(.8f, .8f))) {
            slot.performTouchInput { down(center + Offset(center.x * point.x, center.y * point.y)) }
            compose.waitForIdle()
            val start = read()
            assertEquals(0f, start.pitch, .001f)
            assertEquals(0f, start.roll, .001f)
            advance(160)
            val held = read()
            assertTrue("tuned B with followup marks pose-follow", held.poseFollow)
            assertEquals("actual normalized touch x", point.x, held.x, .01f)
            assertEquals("actual normalized touch y", point.y, held.y, .01f)
            assertEquals("pitch follows -rotationX", point.y * 2.8f, held.pitch, .02f)
            assertEquals("roll follows rotationY", point.x * 3.2f, held.roll, .02f)
            slot.performTouchInput { up() }
            advance(1000)
        }
    }

    @Test fun heldRetargetAndReleaseRepressReuseContinuousAnimationValues() {
        content()
        val slot = compose.onNodeWithTag("pickup-pose-slot")
        slot.performTouchInput { down(center + Offset(center.x * .7f, 0f)) }
        advance(160)
        val right = read().roll
        assertTrue(right > 2f)
        slot.performTouchInput { moveTo(center - Offset(center.x * .7f, 0f)) }
        compose.waitForIdle()
        assertEquals("retarget does not jump", right, read().roll, .001f)
        advance(24)
        val halfway = read().roll
        assertTrue("48ms retarget is progressing without a snap", halfway < right && halfway > -2.24f)
        advance(160)
        val left = read()
        assertEquals("pose uses the true contact after bounded horizontal translation", left.x * 3.2f, left.roll, .02f)
        slot.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals("release preserves the current animated value", left.roll, read().roll, .001f)
        advance(32)
        val rebounding = read().roll
        assertTrue(abs(rebounding) < abs(left.roll))
        slot.performTouchInput { down(center + Offset(center.x * .7f, 0f)) }
        compose.waitForIdle()
        assertEquals("repress reuses current animation value", rebounding, read().roll, .001f)
        advance(160)
        val repressed = read()
        assertEquals(repressed.x * 3.2f, repressed.roll, .02f)
        slot.performTouchInput { cancel() }
        advance(1000)
    }

    @Test fun otherModesKeepTheirRealPoseAndRawContactWithoutPoseFollow() {
        content()
        val slot = compose.onNodeWithTag("pickup-pose-slot")
        val cases = listOf(
            Triple(ShortcutTouchVariant.GentleFloat, true, true),
            Triple(ShortcutTouchVariant.BuoyantSpring, true, true),
            Triple(ShortcutTouchVariant.FingerTilt, false, true),
            Triple(ShortcutTouchVariant.FingerTilt, true, false)
        )
        for ((style, material, opticalFollowup) in cases) {
            compose.runOnIdle {
                variant.value = style
                tuned.value = material
                followup.value = opticalFollowup
            }
            compose.waitForIdle()
            slot.performTouchInput { down(center + Offset(center.x * .6f, center.y * .5f)) }
            advance(180)
            val held = read()
            assertFalse("pose-follow is exclusive to tuned B with followup", held.poseFollow)
            assertEquals(.6f, held.x, .01f)
            assertEquals(.5f, held.y, .01f)
            val pitchMultiplier = when (style) {
                ShortcutTouchVariant.GentleFloat -> .75f
                ShortcutTouchVariant.BuoyantSpring -> 1f
                ShortcutTouchVariant.FingerTilt -> if (material) 2.8f else 4f
            }
            val rollMultiplier = when (style) {
                ShortcutTouchVariant.FingerTilt -> if (material) 3.2f else 4f
                else -> pitchMultiplier
            }
            assertEquals("preserve true pitch from rotationX", .5f * pitchMultiplier, held.pitch, .02f)
            assertEquals("preserve true roll from rotationY", .6f * rollMultiplier, held.roll, .02f)
            slot.performTouchInput { up() }
            advance(1000)
        }
    }

    @Test fun reducedMotionIsZeroAndFrozenPreviewUsesRotationConventionNotContact() {
        motionEnabled.value = false
        content()
        val slot = compose.onNodeWithTag("pickup-pose-slot")
        slot.performTouchInput { down(center + Offset(center.x * .8f, center.y * -.6f)) }
        advance(160)
        val reduced = read()
        assertEquals(.8f, reduced.x, .01f)
        assertEquals(-.6f, reduced.y, .01f)
        assertEquals(0f, reduced.pitch, 0f)
        assertEquals(0f, reduced.roll, 0f)
        assertFalse(reduced.poseFollow)
        slot.performTouchInput { up() }
        advance(1000)

        compose.runOnIdle {
            motionEnabled.value = true
            preview.value = ShortcutTouchPose(rotationX = -.55f, rotationY = .72f)
        }
        advance(1000)
        val frozen = read()
        assertEquals("preview pitch is -rotationX", .55f, frozen.pitch, .001f)
        assertEquals("preview roll is rotationY", .72f, frozen.roll, .001f)
        slot.performTouchInput { down(center + Offset(center.x * -.8f, center.y * .8f)) }
        advance(160)
        assertEquals("live touch cannot replace frozen pitch", .55f, read().pitch, .001f)
        assertEquals("live touch cannot replace frozen roll", .72f, read().roll, .001f)
        assertEquals(-.8f, read().x, .01f)
        assertEquals(.8f, read().y, .01f)
        slot.performTouchInput { up() }
        advance(1000)
    }
}
