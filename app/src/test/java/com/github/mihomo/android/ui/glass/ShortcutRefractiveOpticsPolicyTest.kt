package com.github.mihomo.android.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutRefractiveOpticsPolicyTest {
    @Test fun followupInheritsGeometryScaleButUsesLatestOpticalSnapshot() {
        val p = ShortcutFollowupParameters.resolve(240f, 120f, 2f)
        val old = ShortcutTunedParameters.resolve(240f, 120f, 2f)
        assertEquals(68f * ShortcutTunedParameters.referenceScale, p.thicknessDp, .0001f)
        assertEquals(12f * ShortcutTunedParameters.referenceScale, p.edgeWidthDp, .0001f)
        assertEquals(3f / (2f * kotlin.math.sqrt(2f)), p.blurDp, .0001f)
        assertEquals(2f, p.squircle, 0f)
        assertEquals(.75f, p.edgeClarity, 0f); assertEquals(2f, p.ior, 0f)
        assertEquals(.100f, p.distortion, 0f); assertEquals(15.5f, p.dispersion, 0f)
        assertEquals(.64f, p.fresnel, 0f); assertEquals(1f, p.edgeStrength, 0f)
        assertEquals(.86f, p.saturation, 0f); assertEquals(0f, p.tint, 0f)
        assertEquals(0f, p.grain, 0f)
        assertEquals(.052f, old.grain, 0f)
        assertTrue(p.grain < old.grain)
        assertEquals(old.radiusDp, p.radiusDp, 0f)
        assertEquals(old.pressLiftDp, p.pressLiftDp, 0f)
        assertEquals(old.pressScale, p.pressScale, 0f)
        assertEquals(old.tiltDegrees, p.tiltDegrees, 0f)
        assertEquals(old.shadowDp, p.shadowDp, 0f)
        assertEquals(old.damping, p.damping, 0f)
    }
}
