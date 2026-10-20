package com.github.mihomo.android.ui.glass

import org.junit.Assert.*
import org.junit.Test

class ShortcutContactFollowPolicyTest {
    @Test fun radiusUsesMinDimensionAndClampsToTwelveThroughSixtyFourDp() {
        assertEquals(12f, shortcutContactRadiusDp(10f, 100f), 0f)
        assertEquals(42f, shortcutContactRadiusDp(100f, 120f), 0f)
        assertEquals(120f * .42f, shortcutContactRadiusDp(240f, 120f), .00001f)
        assertEquals(shortcutContactRadiusDp(120f, 240f), shortcutContactRadiusDp(240f, 120f), 0f)
        assertEquals(64f, shortcutContactRadiusDp(1000f, 400f), 0f)
    }

    @Test fun contactMaskHasFullCoreSmoothMonotonicFeatherAndZeroOutside() {
        val radius = shortcutContactRadiusDp(240f, 120f)
        val cx = 60f; val cy = 60f
        assertEquals(1f, shortcutContactWeight(cx, cy, 240f, 120f, -.5f, 0f, 1f), 0f)
        assertEquals(1f, shortcutContactWeight(cx + radius * .34f, cy, 240f, 120f, -.5f, 0f, 1f), 0f)
        assertEquals(0f, shortcutContactWeight(cx + radius, cy, 240f, 120f, -.5f, 0f, 1f), .00001f)
        assertEquals(0f, shortcutContactWeight(cx + radius + .01f, cy, 240f, 120f, -.5f, 0f, 1f), 0f)
        val samples = (0..100).map { i -> shortcutContactWeight(cx + radius * (.35f + .65f * i / 100f), cy, 240f, 120f, -.5f, 0f, 1f) }
        assertTrue("feather decreases monotonically", samples.zipWithNext().all { (a, b) -> b <= a })
        assertTrue("mask is normalized", samples.all { it in 0f..1f })
        assertEquals(1f, samples.first(), .00001f); assertEquals(0f, samples.last(), .00001f)
        assertTrue("smoothstep feather has a nontrivial transition", samples.any { it in .1f.. .9f })
    }

    @Test fun normalizedTouchMapsToCardDpCoordinatesAndIsDensityIndependent() {
        assertEquals(1f, shortcutContactWeight(60f, 60f, 240f, 120f, -.5f, 0f, 1f), 0f)
        assertEquals(1f, shortcutContactWeight(180f, 60f, 240f, 120f, .5f, 0f, 1f), 0f)
        val sampleX = 89f; val sampleY = 67f
        val reference = shortcutContactWeight(sampleX, sampleY, 240f, 120f, -.5f, 0f, .8f)
        assertTrue("density witness lies in actual feather", reference > 0f && reference < .8f)
        for (d in listOf(1f, 1.5f, 2f, 3.25f)) {
            val px = sampleX * d; val py = sampleY * d; val w = 240f * d; val h = 120f * d
            assertEquals("physical pixels must be converted back to dp", reference,
                shortcutContactWeight(px / d, py / d, w / d, h / d, -.5f, 0f, .8f), .00001f)
        }
        assertEquals(0f, shortcutContactWeight(Float.NaN, 1f, 240f, 120f, 0f, 0f, 1f), 0f)
        assertEquals(0f, shortcutContactWeight(1f, 1f, Float.NaN, 120f, 0f, 0f, 1f), 0f)
        assertEquals(0f, shortcutContactWeight(1f, 1f, 240f, 120f, Float.NaN, 0f, 1f), 0f)
        assertEquals(0f, shortcutContactWeight(1f, 1f, 240f, 120f, 0f, 0f, Float.NaN), 0f)
    }
}
