package com.github.mihomo.android.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LensOpticsTest {
    @Test
    fun strengthPresetsKeepUploadedBaseAndSlightlyStrongerActiveLens() {
        assertStrength(LensStrength.SUBTLE, 1.2f, 7f, 1.02f, 7f, .55f, .030f)
        assertStrength(LensStrength.STANDARD, 2.2f, 9f, 1.05f, 9f, .80f, .045f)
        assertStrength(LensStrength.STRONG, 4.0f, 12f, 1.10f, 11f, 1.15f, .060f)
        assertTrue(LensStrength.SUBTLE.displacementDp < LensStrength.STANDARD.displacementDp)
        assertTrue(LensStrength.STANDARD.displacementDp < LensStrength.STRONG.displacementDp)
    }

    @Test
    fun expansionScaleClampsAndIsMonotonic() {
        assertEquals(1f, lensRefractionScale(-1f), 0f)
        assertEquals(1f, lensRefractionScale(0f), 0f)
        assertEquals(1.1f, lensRefractionScale(.5f), .00001f)
        assertEquals(1.2f, lensRefractionScale(1f), 0f)
        assertEquals(1.2f, lensRefractionScale(2f), 0f)
        val values = listOf(-2f, -.1f, 0f, .2f, .7f, 1f, 2f).map(::lensRefractionScale)
        assertTrue("refraction scale is nondecreasing", values.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test
    fun originalAgslCompilesAsApi35RuntimeShader() {
        assertTrue(NAVIGATION_LENS_SHADER.contains("uniform shader scene"))
        assertTrue(NAVIGATION_LENS_SHADER.contains("scene.eval"))
        assertTrue(NAVIGATION_LENS_SHADER.contains("aberration"))
        assertTrue(NAVIGATION_LENS_SHADER.contains("rimGain"))
        assertTrue(Build.VERSION.SDK_INT >= 33)
        val shader = RuntimeShader(NAVIGATION_LENS_SHADER)
        // Exercise the same runtime-effect construction path used by the composable.
        val effect = RenderEffect.createRuntimeShaderEffect(shader, "scene")
        assertNotNull(effect)
    }

    private fun assertStrength(
        strength: LensStrength,
        displacement: Float,
        bevel: Float,
        saturation: Float,
        blur: Float,
        aberration: Float,
        rim: Float
    ) {
        assertEquals(displacement, strength.displacementDp, 0f)
        assertEquals(bevel, strength.bevelDp, 0f)
        assertEquals(saturation, strength.saturation, 0f)
        assertEquals(blur, strength.blurDp, 0f)
        assertEquals(aberration, strength.aberrationDp, 0f)
        assertEquals(rim, strength.rimGain, .000001f)
    }
}
