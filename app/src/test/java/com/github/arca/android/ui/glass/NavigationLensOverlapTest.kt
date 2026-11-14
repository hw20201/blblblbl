package com.github.arca.android.ui.glass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLensOverlapTest {
    @Test fun noIntersectionHasNoTintCoverage() {
        assertEquals(0f, roundedRectOverlapFraction(Rect(80f, 10f, 100f, 30f), Rect(0f, 0f, 50f, 40f), 0f), 0.001f)
    }

    @Test fun partialIntersectionIsARealFractionNotABoolean() {
        val coverage = roundedRectOverlapFraction(Rect(0f, 0f, 100f, 40f), Rect(0f, 0f, 50f, 40f), 0f)
        assertEquals(0.5f, coverage, 0.02f)
        assertTrue(coverage > 0f && coverage < 1f)
    }

    @Test fun fullyCoveredContentBoxHasFullTintCoverage() {
        assertEquals(1f, roundedRectOverlapFraction(Rect(20f, 10f, 80f, 30f), Rect(0f, 0f, 100f, 40f), 20f), 0.01f)
    }
    @Test fun selectedLensWindowBoundsUseTheMeasuredOrigin() {
        val bounds = navigationLensGlobalBounds(Rect(4f, 6f, 54f, 46f), Offset(100f, 200f), 20f)
        assertEquals(Rect(104f, 206f, 154f, 246f), bounds.rect)
        assertEquals(20f, bounds.cornerRadiusPx, 0.001f)
    }

    @Test fun dnsTintCoverageTracksTheActualFractionalLensOverlap() {
        val item = Rect(0f, 0f, 100f, 40f)
        val quarter = navigationLensGlobalBounds(Rect(0f, 0f, 25f, 40f), Offset.Zero, 0f)
        val half = navigationLensGlobalBounds(Rect(0f, 0f, 50f, 40f), Offset.Zero, 0f)
        val full = navigationLensGlobalBounds(Rect(0f, 0f, 100f, 40f), Offset.Zero, 0f)
        assertEquals(0.25f, overlapTintProgress(item, quarter), 0.02f)
        assertEquals(0.5f, overlapTintProgress(item, half), 0.02f)
        assertEquals(1f, overlapTintProgress(item, full), 0.01f)
        assertEquals(0f, overlapTintProgress(item, null), 0.001f)
    }

    @Test fun dnsTintColorMovesContinuouslyFromBaseToGreen() {
        val base = Color(0xFF3B82F6)
        val green = Color(0xFF22C55E)
        assertEquals(base, interpolateLensTint(base, green, 0f))
        assertEquals(green, interpolateLensTint(base, green, 1f))
        val partial = interpolateLensTint(base, green, 0.5f)
        assertTrue(partial.green > base.green)
        assertTrue(partial.green < green.green)
    }
}
