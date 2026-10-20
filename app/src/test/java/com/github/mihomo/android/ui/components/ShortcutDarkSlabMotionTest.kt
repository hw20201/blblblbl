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
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.glass.GlassPalettes
import com.github.mihomo.android.ui.glass.LocalGlassTokens
import com.github.mihomo.android.ui.glass.LocalShortcutContactFollowEnabled
import com.github.mihomo.android.ui.glass.LocalShortcutOpticalFollowupEnabled
import com.github.mihomo.android.ui.glass.LocalShortcutTunedMaterialEnabled
import com.github.mihomo.android.ui.glass.ShortcutRefraction
import com.github.mihomo.android.ui.glass.toTokens
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutDarkSlabMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var previousGlassEnabled = true
    private val variant = mutableStateOf(ShortcutTouchVariant.FingerTilt)
    private val tuned = mutableStateOf(true)
    private val followup = mutableStateOf(true)
    private val contactFollow = mutableStateOf(true)
    private val darkLift = mutableStateOf(true)
    private val motionEnabled = mutableStateOf(true)
    private var optics: () -> ShortcutRefraction = { ShortcutRefraction() }

    @Before fun saveAndEnableGlassDefaults() {
        previousGlassEnabled = GlassDefaults.enabled
        GlassDefaults.enabled = true
    }

    @After fun restoreGlassDefaults() {
        GlassDefaults.enabled = previousGlassEnabled
    }

    private fun content() {
        compose.setContent {
            CompositionLocalProvider(
                LocalGlassTokens provides GlassPalettes.MIDNIGHT.toTokens(),
                LocalShortcutTunedMaterialEnabled provides tuned.value,
                LocalShortcutOpticalFollowupEnabled provides followup.value,
                LocalShortcutContactFollowEnabled provides contactFollow.value,
                LocalShortcutDarkLiftEnabled provides darkLift.value,
                LocalShortcutMotionEnabled provides motionEnabled.value
            ) {
                val motion = rememberShortcutTouchMotion(variant.value, enabled = true)
                SideEffect { optics = motion.refraction }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(190.dp, 118.dp).testTag("dark-slab-slot")) {
                        Box(motion.modifier.fillMaxSize())
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun advance(ms: Long) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun read() = compose.runOnIdle { optics() }

    @Test fun darkFingerTiltKeepsTrueCornerCoordinatesAndDoublesAnimatedAngles() {
        content()
        val slot = compose.onNodeWithTag("dark-slab-slot")
        assertTrue("test explicitly supplies a dark token palette", GlassPalettes.MIDNIGHT.toTokens().isDark)
        for (point in listOf(
            Offset(-.8f, -.8f), Offset(.8f, -.8f),
            Offset(-.8f, .8f), Offset(.8f, .8f)
        )) {
            slot.performTouchInput { down(center + Offset(center.x * point.x, center.y * point.y)) }
            compose.waitForIdle()
            advance(160)
            val held = read()
            assertTrue("dark B retains slab-follow optics", held.slabFollow)
            assertEquals("raw contact x", point.x, held.x, .01f)
            assertEquals("raw contact y is not visually damped", point.y, held.y, .01f)
            assertEquals("dark pitch target doubles the B target", point.y * 1.5f, held.pitch, .02f)
            assertEquals("dark roll target doubles the B target", point.x * 2.5f, held.roll, .02f)
            slot.performTouchInput { up() }
            compose.waitForIdle()
            assertEquals("release preserves optical contact x", held.x, read().x, 0f)
            assertEquals("release preserves optical contact y", held.y, read().y, 0f)
            advance(1000)
            assertEquals("release restores pitch", 0f, read().pitch, .001f)
            assertEquals("release restores roll", 0f, read().roll, .001f)
        }
    }

    @Test fun reducedMotionLeavesDarkSlabPoseUnanimatedButTracksRawContact() {
        motionEnabled.value = false
        content()
        val slot = compose.onNodeWithTag("dark-slab-slot")
        slot.performTouchInput { down(center + Offset(center.x * .8f, center.y * .8f)) }
        advance(160)
        val held = read()
        assertEquals(.8f, held.x, .01f)
        assertEquals(.8f, held.y, .01f)
        assertEquals("no animated pitch under reduced motion", 0f, held.pitch, 0f)
        assertEquals("no animated roll under reduced motion", 0f, held.roll, 0f)
        slot.performTouchInput { up() }
        advance(1000)
    }

    @Test fun darkLiftGateDoesNotChangeOtherVariantsLegacyMaterialOrDisabledSwitches() {
        content()
        val slot = compose.onNodeWithTag("dark-slab-slot")
        val cases = listOf(
            Triple(ShortcutTouchVariant.GentleFloat, true, .6f),
            Triple(ShortcutTouchVariant.BuoyantSpring, true, .8f),
            Triple(ShortcutTouchVariant.FingerTilt, false, 3.2f)
        )
        for ((choice, material, expectedPitch) in cases) {
            compose.runOnIdle { variant.value = choice; tuned.value = material }
            advance(32)
            slot.performTouchInput { down(center + Offset(0f, center.y * .8f)) }
            advance(160)
            val held = read()
            assertEquals("$choice raw y", .8f, held.y, .01f)
            assertEquals("$choice keeps its original pitch", expectedPitch, held.pitch, .02f)
            assertEquals("$choice does not receive the dark slab gate", false, held.slabFollow)
            slot.performTouchInput { up() }
            advance(1000)
        }

        // FingerTilt, tuned, dark and contact-follow are all present; independently
        // disable the global glass gate and the local comparison gate in turn.
        compose.runOnIdle { variant.value = ShortcutTouchVariant.FingerTilt; tuned.value = true }
        advance(32)
        GlassDefaults.enabled = false
        slot.performTouchInput { down(center + Offset(center.x * .8f, center.y * .8f)) }
        advance(160)
        assertEquals("global glass disable retains the old pitch", .6f, read().pitch, .02f)
        assertEquals("global glass disable retains the old roll", 1f, read().roll, .02f)
        slot.performTouchInput { up() }; advance(1000)
        GlassDefaults.enabled = true

        compose.runOnIdle { darkLift.value = false }
        advance(32)
        slot.performTouchInput { down(center + Offset(center.x * .8f, center.y * .8f)) }
        advance(160)
        val disabled = read()
        assertEquals("local switch retains the old pitch", .6f, disabled.pitch, .02f)
        assertEquals("local switch retains the old roll", 1f, disabled.roll, .02f)
        assertTrue("dark lift switch does not disable ordinary slab-follow", disabled.slabFollow)
        slot.performTouchInput { up() }; advance(1000)
    }
}
