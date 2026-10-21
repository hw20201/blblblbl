package com.github.mihomo.android.ui.glass

import org.junit.Assert.*
import org.junit.Test

class ShortcutGlassLevelPolicyTest {
    @Test fun levelsHaveDistinctOpticsAndNormalRetainsTenDispersion() {
        val levels=ShortcutGlassLevel.entries
        assertEquals(ShortcutGlassLevel.Normal,ShortcutGlassLevel.fromPreference(null))
        assertEquals(ShortcutGlassLevel.Normal,ShortcutGlassLevel.fromPreference("future"))
        assertEquals(10f,levels[0].dispersion,0f)
        assertEquals(.24f,levels[0].aberrationDp,.0001f)
        assertEquals(1,levels[0].chromaticSamples)
        assertEquals(2,levels[1].chromaticSamples)
        assertEquals(2,levels[2].chromaticSamples)
        levels.zipWithNext().forEach { (a,b) ->
            assertTrue(b.strength.displacementDp > a.strength.displacementDp * 1.7f)
            assertTrue(b.strength.bevelDp > a.strength.bevelDp)
            assertTrue(b.strength.rimGain > a.strength.rimGain)
            assertTrue(b.shellAlpha > a.shellAlpha)
        }
    }

    @Test fun sharedDockKernelHasCorrectOverscanAndCheapCentreBranch() {
        assertTrue(SHORTCUT_DOCK_LENS_SHADER.contains("(p - inputOrigin) - viewport * 0.5"))
        assertTrue(SHORTCUT_DOCK_LENS_SHADER.contains("inputViewport - float2(0.5)"))
        assertTrue(SHORTCUT_DOCK_LENS_SHADER.contains("if (edge > 0.001 && aberration > 0.001)"))
        assertFalse(SHORTCUT_DOCK_LENS_SHADER.contains("half4 cr ="))
        for (level in ShortcutGlassLevel.entries) {
            val s=level.strength
            val bound=s.displacementDp+level.aberrationDp+2f
            assertTrue("${level.name}: footprint $bound",bound<SHORTCUT_DOCK_MARGIN_DP)
        }
    }
}
