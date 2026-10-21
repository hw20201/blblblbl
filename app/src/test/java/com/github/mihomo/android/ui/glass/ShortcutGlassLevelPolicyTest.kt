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
        assertTrue(LIQUID_GLASS_LENS_SHADER.contains("(p - inputOrigin) - viewport * 0.5"))
        assertTrue(LIQUID_GLASS_LENS_SHADER.contains("inputViewport - float2(0.5)"))
        assertTrue(LIQUID_GLASS_LENS_SHADER.contains("if (edge > 0.001 && aberration > 0.001)"))
        assertTrue(LIQUID_GLASS_LENS_SHADER.contains("uniform float chromaticSamples"))
        assertFalse(LIQUID_GLASS_LENS_SHADER.contains("half4 cr ="))
        for (level in ShortcutGlassLevel.entries) {
            val style=liquidGlassStyle(level)
            assertEquals(level.aberrationDp,style.aberrationDp,.0001f)
            assertEquals(level.chromaticSamples,style.chromaticSamples)
            val bound=style.displacementDp+style.aberrationDp+2f
            assertTrue("${level.name}: footprint $bound",bound<SHORTCUT_DOCK_MARGIN_DP)
        }
    }

    @Test fun reusableStylesIncreaseOpticalStrengthByLevel() {
        val styles = ShortcutGlassLevel.entries.map(::liquidGlassStyle)
        assertEquals(LensStrength.STRONG.displacementDp,styles[0].displacementDp,0f)
        assertEquals(LensStrength.STRONG.bevelDp,styles[0].bevelDp,0f)
        assertTrue(styles[1].rimAlpha > styles[0].rimAlpha)
        assertTrue(styles[2].rimAlpha > styles[1].rimAlpha)
        assertTrue(styles[1].displacementDp > styles[0].displacementDp)
        assertTrue(styles[2].bevelDp > styles[1].bevelDp)
    }
}
