package com.github.arca.android.ui.glass

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GlasmioFrostedTuningTest {
    @Test
    fun presetAndItsIndependentParametersSurviveSerialization() {
        val original = GlassSettings(
            glassPreset = GlassPreset.UNIFIED,
            glasmioFrosted = GlasmioFrostedTuning(
                blurRadiusDp = 7.5f,
                refractionHeightDp = 18f,
                refractionAmountDp = 20f,
                tintStrength = 0.23f,
                surfaceAlphaMultiplier = 1.4f,
            ),
        )
        val restored = Json.decodeFromString<GlassSettings>(Json.encodeToString(original))
        assertEquals(GlassPreset.UNIFIED, restored.glassPreset)
        assertEquals(original.glasmioFrosted, restored.glasmioFrosted)
    }

    @Test
    fun glasmioModeIsRestrictedToApi33AndAbove() {
        assertEquals(false, GlassPreset.UNIFIED.usesGlasmioFrosted(26))
        assertEquals(false, GlassPreset.UNIFIED.usesGlasmioFrosted(32))
        assertEquals(true, GlassPreset.UNIFIED.usesGlasmioFrosted(33))
        assertEquals(true, GlassPreset.UNIFIED.usesGlasmioFrosted(36))
    }

    @Test
    fun oldPresetNamesMigrateToUnifiedGlass() {
        listOf("CUSTOM", "LIQUID", "GLASMIO_FROSTED").forEach { legacyName ->
            val restored = Json.decodeFromString<GlassSettings>("{\"glassPreset\":\"$legacyName\"}")
            assertEquals(GlassPreset.UNIFIED, restored.glassPreset)
        }
    }

    @Test
    fun glasmioParametersAreClampedToSafeRanges() {
        val safe = GlasmioFrostedTuning(
            blurRadiusDp = 90f,
            refractionHeightDp = -3f,
            refractionAmountDp = Float.NaN,
            tintStrength = 4f,
            surfaceAlphaMultiplier = 0.1f,
        ).clamped()
        assertEquals(GlasmioFrostedTuning.MAX_BLUR_RADIUS_DP, safe.blurRadiusDp)
        assertEquals(GlasmioFrostedTuning.MIN_REFRACTION_HEIGHT_DP, safe.refractionHeightDp)
        assertEquals(GlasmioFrostedTuning.DEFAULT_REFRACTION_AMOUNT_DP, safe.refractionAmountDp)
        assertEquals(GlasmioFrostedTuning.MAX_TINT_STRENGTH, safe.tintStrength)
        assertEquals(GlasmioFrostedTuning.MIN_SURFACE_ALPHA_MULTIPLIER, safe.surfaceAlphaMultiplier)
    }

    @Test
    fun zeroBlurRemainsAvailableWithoutDisablingRefraction() {
        assertEquals(0f, GlasmioFrostedTuning.MIN_BLUR_RADIUS_DP, 0f)
        val encoded = Json.encodeToString(
            GlassSettings(glasmioFrosted = GlasmioFrostedTuning(
                blurRadiusDp = 0f,
                refractionHeightDp = 20f,
                refractionAmountDp = 12f
            ))
        )
        val restored = Json.decodeFromString<GlassSettings>(encoded).clamped().glasmioFrosted
        assertEquals(0f, restored.blurRadiusDp, 0f)
        assertEquals(20f, restored.refractionHeightDp, 0f)
        assertEquals(12f, restored.refractionAmountDp, 0f)
    }
}
