package com.github.mihomo.android.ui.glass

import com.github.mihomo.android.ui.components.ShortcutTouchVariant
import com.github.mihomo.android.ui.components.shortcutLiveTouchTarget
import org.junit.Assert.*
import org.junit.Test

class ShortcutGlassLevelPolicyTest {
    @Test fun newInstallsDefaultToExtremeAndEverySavedTierRoundTrips() {
        assertEquals(ShortcutGlassLevel.Extreme, ShortcutGlassLevel.fromPreference(null))
        assertEquals(ShortcutGlassLevel.Extreme, ShortcutGlassLevel.fromPreference("future"))
        ShortcutGlassLevel.entries.forEach { level ->
            assertEquals(level, ShortcutGlassLevel.fromPreference(level.name))
        }
    }

    @Test fun allTiersStrengthenTheSameLiveRefractionAndDispersionEffect() {
        val optics = ShortcutGlassLevel.entries.map(::shortcutDockLensOptics)
        assertEquals(LensStrength.SUBTLE, optics[0].strength)
        assertEquals(LensStrength.STANDARD, optics[1].strength)
        assertEquals(LensStrength.STRONG, optics[2].strength)
        optics.zipWithNext().forEach { (a, b) ->
            assertTrue(b.displacementDp > a.displacementDp)
            assertTrue(b.aberrationDp > a.aberrationDp)
            assertTrue(b.rimGain > a.rimGain)
        }
        assertEquals(5f, optics[0].displacementDp, 0f)
        assertEquals(1.8f, optics[0].aberrationDp, 0f)
        assertEquals(13f, optics[2].displacementDp, 0f)
        assertEquals(6f, optics[2].aberrationDp, 0f)
        assertEquals(2, ShortcutGlassLevel.Extreme.chromaticSamples)
    }

    @Test fun continuousControlsMapDirectlyToLiveShaderUniformsAndClampSafely() {
        val raw = ShortcutGlassTuning(15.5f, 7.25f, 18f, 1.75f)
        val optics = shortcutDockLensOptics(raw)
        assertEquals(15.5f, optics.displacementDp, 0f)
        assertEquals(7.25f, optics.aberrationDp, 0f)
        assertEquals(18f, optics.bevelDp, 0f)
        assertEquals(1.75f, optics.rimGain / LensStrength.STRONG.rimGain, .0001f)
        assertEquals(LensStrength.STRONG, optics.strength)

        val bounded = shortcutDockLensOptics(ShortcutGlassTuning(99f, -1f, 0f, 5f))
        assertEquals(18f, bounded.displacementDp, 0f)
        assertEquals(0f, bounded.aberrationDp, 0f)
        assertEquals(6f, bounded.bevelDp, 0f)
        assertEquals(2f, bounded.rimGain / LensStrength.STRONG.rimGain, .0001f)
        val nonFinite = ShortcutGlassTuning(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN).clamped()
        assertEquals(ShortcutGlassTuning.DEFAULT, nonFinite)
    }

    @Test fun shaderRetainsOverscanAndExtremeOpticalFootprintFitsItsBuffer() {
        assertTrue(SHORTCUT_DOCK_LENS_SHADER.contains("(p - inputOrigin) - viewport * 0.5"))
        assertTrue(SHORTCUT_DOCK_LENS_SHADER.contains("inputViewport - float2(0.5)"))
        assertTrue(SHORTCUT_DOCK_LENS_SHADER.contains("if (edge > 0.001 && aberration > 0.001)"))
        assertFalse(SHORTCUT_DOCK_LENS_SHADER.contains("half4 cr ="))
        val tuning = ShortcutGlassTuning(
            refractionDp = ShortcutGlassTuning.MAX_REFRACTION_DP,
            dispersionDp = ShortcutGlassTuning.MAX_DISPERSION_DP
        )
        val optics = shortcutDockLensOptics(tuning)
        val bound = optics.displacementDp + optics.aberrationDp + 2f
        assertTrue("max slider footprint $bound", bound < SHORTCUT_DOCK_MARGIN_DP)
    }
}

class GlassSettingsPolicyTest {
    @Test fun surfaceOverridesInheritAndMapTypedOpticsIntoTheGlassShader() {
        val shared = GlassOptics(14f, 6.5f, 13f, 1.4f)
        val local = GlassOptics(8f, 2f, 9f, .8f)
        val settings = GlassSettings.DEFAULT.copy(
            globalOptics = shared,
            surfaceOverrides = mapOf(GlassSurfaceCategory.TITLE_CAPSULES to local)
        )
        assertEquals(shared, settings.opticsFor(GlassSurfaceCategory.SHORTCUT_CARDS))
        assertEquals(local, settings.opticsFor(GlassSurfaceCategory.TITLE_CAPSULES))
        assertEquals(shared.refractionScale(), settings.opticsFor(GlassSurfaceCategory.SHORTCUT_CARDS).refractionScale(), 0f)
        val mapped = shortcutDockLensOptics(settings.opticsFor(GlassSurfaceCategory.SHORTCUT_CARDS))
        assertEquals(shared.refractionDp, mapped.displacementDp, 0f)
        assertEquals(shared.dispersionDp, mapped.aberrationDp, 0f)
        assertEquals(shared.edgeWidthDp, mapped.bevelDp, 0f)
    }

    @Test fun navigationLensRefractionRespondsAcrossStraightAndRoundedEdges() {
        val shader = NAVIGATION_LENS_SHADER
        assertTrue("shader must use rounded-rectangle signed distance, not corner-only radius", shader.contains("float signedDistance = outsideLength + min(max(d.x, d.y), 0.0) - r;"))
        assertTrue("straight edges need an axis normal", shader.contains("normal = float2(0.0, sign(q.y));"))
        assertTrue("bevel must fade inward from the full outline", shader.contains("float depth = max(-signedDistance, 0.0);"))
        assertTrue("shortcut card shader shares the corrected kernel", SHORTCUT_DOCK_LENS_SHADER.contains("float signedDistance = outsideLength + min(max(d.x, d.y), 0.0) - r;"))

        // Exercise the same signed-distance / bevel equations on top and side straight edges,
        // a rounded corner, and the flat center of a representative capsule.
        fun edgeAt(px: Float, py: Float, width: Float, height: Float, radius: Float, bevel: Float): Float {
            val qx = px - width / 2f
            val qy = py - height / 2f
            val dx = kotlin.math.abs(qx) - (width / 2f - radius)
            val dy = kotlin.math.abs(qy) - (height / 2f - radius)
            val ox = maxOf(dx, 0f)
            val oy = maxOf(dy, 0f)
            val outside = kotlin.math.hypot(ox, oy)
            val sd = outside + minOf(maxOf(dx, dy), 0f) - radius
            val depth = maxOf(-sd, 0f)
            val t = (depth / bevel).coerceIn(0f, 1f)
            val smooth = t * t * (3f - 2f * t)
            return 1f - smooth
        }
        val width = 300f; val height = 90f; val radius = 24f; val bevel = 12f
        assertEquals(1f, edgeAt(150f, 0f, width, height, radius, bevel), .001f) // top edge
        assertEquals(1f, edgeAt(0f, 45f, width, height, radius, bevel), .001f) // side edge
        assertTrue(edgeAt(5f, 5f, width, height, radius, bevel) > .9f) // curved corner
        assertEquals(0f, edgeAt(150f, 45f, width, height, radius, bevel), .001f) // flat center
    }

    @Test fun everyGlassSurfaceUsesTheSharedGlobalOrItsOverrideAtBothSliderExtremes() {
        val categories = GlassSurfaceCategory.entries
        for (category in categories) {
            val minimum = GlassOptics.MIN_REFRACTION_DP
            val maximum = GlassOptics.MAX_REFRACTION_DP
            val lowSettings = GlassSettings.DEFAULT.copy(globalOptics = GlassOptics(refractionDp = minimum))
            val highSettings = GlassSettings.DEFAULT.copy(globalOptics = GlassOptics(refractionDp = maximum))
            val low = navigationLensShaderParameters(LensStrength.STRONG, lowSettings.opticsFor(category))
            val high = navigationLensShaderParameters(LensStrength.STRONG, highSettings.opticsFor(category))
            assertTrue("$category refraction minimum and maximum must produce different shader uniforms", high.displacementDp > low.displacementDp * 2f)

            val local = GlassOptics(refractionDp = maximum)
            val overridden = lowSettings.withOptics(category, local)
            assertEquals(local, overridden.opticsFor(category))
            assertEquals(minimum, overridden.opticsFor(categories.first { it != category }).refractionDp, 0f)
        }
        val cardLow = shortcutDockLensOptics(GlassOptics(refractionDp = GlassOptics.MIN_REFRACTION_DP))
        val cardHigh = shortcutDockLensOptics(GlassOptics(refractionDp = GlassOptics.MAX_REFRACTION_DP))
        assertTrue("homepage shortcut-card renderer must receive the full refraction range", cardHigh.displacementDp > cardLow.displacementDp * 2f)
    }

    @Test fun dispersionRangeReachesTheLivePreviewShaderForEverySurfaceCategory() {
        assertEquals(12f, GlassOptics.MAX_DISPERSION_DP, 0f)
        for (category in GlassSurfaceCategory.entries) {
            val minimum = GlassSettings.DEFAULT.copy(
                globalOptics = GlassOptics(
                    refractionDp = GlassOptics.MAX_REFRACTION_DP,
                    dispersionDp = GlassOptics.MIN_DISPERSION_DP
                )
            )
            val maximum = GlassSettings.DEFAULT.copy(
                globalOptics = GlassOptics(
                    refractionDp = GlassOptics.MAX_REFRACTION_DP,
                    dispersionDp = GlassOptics.MAX_DISPERSION_DP
                )
            )
            val low = navigationLensShaderParameters(LensStrength.STRONG, minimum.opticsFor(category))
            val high = navigationLensShaderParameters(LensStrength.STRONG, maximum.opticsFor(category))
            assertEquals("$category minimum removes chromatic offset", 0f, low.aberrationDp, 0f)
            assertTrue("$category maximum must reach the preview's actual aberration uniform", high.aberrationDp > 2.9f)
            assertTrue("$category min/max must be materially distinct", high.aberrationDp - low.aberrationDp > 2.9f)

            val override = GlassOptics(
                refractionDp = GlassOptics.MAX_REFRACTION_DP,
                dispersionDp = GlassOptics.MAX_DISPERSION_DP
            )
            val overridden = minimum.withOptics(category, override)
            assertEquals(override, overridden.opticsFor(category))
            assertTrue("$category override reaches the same shader calculation",
                navigationLensShaderParameters(LensStrength.STRONG, overridden.opticsFor(category)).aberrationDp > 2.9f)
        }
    }

    @Test fun boundedTiltControlsChangeRealPoseWithoutUnboundedRotation() {
        val default = GlassMotionTuning.DEFAULT
        val maxed = GlassMotionTuning(1.5f, 14f, 0f)
        val a = shortcutLiveTouchTarget(ShortcutTouchVariant.FingerTilt, 1f, 0f, default)
        val b = shortcutLiveTouchTarget(ShortcutTouchVariant.FingerTilt, 1f, 0f, maxed)
        assertTrue("stronger amplitude must increase visible tilt", b.rotationY > a.rotationY)
        assertEquals(18f, b.rotationY, .001f)
        assertTrue("smoothness maps to a stable damping interval", maxed.dampingRatio() in .72f.. .99f)
        val bounded = GlassMotionTuning(Float.POSITIVE_INFINITY, 900f, Float.NaN).clamped()
        assertEquals(GlassMotionTuning.DEFAULT_TILT_GAIN, bounded.tiltGain, 0f)
        assertEquals(GlassMotionTuning.MAX_MAX_TILT_DEGREES, bounded.maxTiltDegrees, 0f)
        assertEquals(GlassMotionTuning.DEFAULT_SMOOTHING, bounded.smoothing, 0f)
    }
}
