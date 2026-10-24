package com.github.mihomo.android.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassWallpaperTest {
    @Test fun threeColorStopsStayInOrderAndFlowOnlyGentlyMovesTheMiddle() {
        val settings = GlassWallpaperSettings(colorA = Color.Red.toArgb(), colorB = Color.Green.toArgb(), colorC = Color.Blue.toArgb())
        val still = wallpaperGradientStops(settings, wallpaperFlowOffset(0f, enabled = false))
        val moving = wallpaperGradientStops(settings, wallpaperFlowOffset(13.5f, enabled = true))
        assertEquals(listOf(Color.Red, Color.Green, Color.Blue), still.map { it.second })
        assertEquals(listOf(0f, .5f, 1f), still.map { it.first })
        assertNotEquals(still[1].first, moving[1].first)
        assertTrue(kotlin.math.abs(moving[1].first - .5f) <= .046f)
        assertEquals(0f, wallpaperFlowOffset(13.5f, enabled = false), 0f)
    }

    @Test fun selectingEachSlotChangesOnlyThatColorAndEnablesWallpaper() {
        val initial = GlassWallpaperSettings(enabled = false)
        val first = initial.withColor(0, Color.Red)
        val second = first.withColor(1, Color.Green)
        val third = second.withColor(2, Color.Blue)
        assertTrue(third.enabled)
        assertEquals(Color.Red.toArgb(), third.colorA)
        assertEquals(Color.Green.toArgb(), third.colorB)
        assertEquals(Color.Blue.toArgb(), third.colorC)
        assertFalse(GlassWallpaperSettings().enabled)
    }

    @Test fun flutedGeometryUsesScriptRibCountWithAVisibleStableSlant() {
        val width = 1000f
        val height = 2000f
        val ribPositions = (0 until 29).map { flutedRibX(it, width) }
        assertEquals(29, ribPositions.size)
        assertEquals(width / 58f, ribPositions.first(), .001f)
        assertEquals(width - width / 58f, ribPositions.last(), .001f)
        assertEquals(500f, ribPositions[14], .001f)
        assertTrue(ribPositions.zipWithNext().all { (left, right) -> right > left })

        val top = flutedRibXAtY(14, width, height, 0f)
        val middle = flutedRibXAtY(14, width, height, height / 2f)
        val bottom = flutedRibXAtY(14, width, height, height)
        assertTrue("the rib leans consistently from top to bottom, leftward", top > middle && middle > bottom)
        assertEquals(
            height * kotlin.math.tan(Math.toRadians(FLUTED_RIDGE_ANGLE_DEGREES.toDouble())).toFloat(),
            top - bottom,
            .01f
        )
    }

    @Test fun flutedRibsUseBroadRampNotThinLines() {
        // Regression for the R8 v58 screenshot: thin bevel lines made bright
        // spikes occupy only ~11% of the width, the rest flat. The ramp must
        // cover the whole rib, dark at the left edge and bright at the right.
        assertEquals(29, FLUTED_RIB_COUNT)
        assertEquals("straight fluted ribs tilt about 7 degrees", 7f, FLUTED_RIDGE_ANGLE_DEGREES, 0f)
        assertTrue("left edge must be slightly dark for the rib separation", FLUTED_RIB_DARK_ALPHA in .08f.. .16f)
        assertTrue("right edge must be clearly bright", FLUTED_RIB_BRIGHT_ALPHA in .20f.. .30f)
        assertTrue("ramp must rise from dark to bright across the rib", FLUTED_RIB_BRIGHT_ALPHA > FLUTED_RIB_DARK_ALPHA)
    }

    @Test fun selectedColorsAreUsedByStraightFlutedWallpaperAndColorEditKeepsThem() {
        val settings = GlassWallpaperSettings(
            enabled = true,
            colorA = Color(0xFF15263A).toArgb(),
            colorB = Color(0xFFB68A62).toArgb(),
            colorC = Color(0xFF7DB5C8).toArgb()
        )
        val stops = wallpaperGradientStops(settings, 0f)
        assertEquals(listOf(settings.colorAt(0), settings.colorAt(1), settings.colorAt(2)), stops.map { it.second })
        val changed = settings.withColor(1, Color(0xFF55AA33))
        assertEquals(Color(0xFF15263A).toArgb(), changed.colorA)
        assertEquals(Color(0xFF55AA33).toArgb(), changed.colorB)
        assertEquals(Color(0xFF7DB5C8).toArgb(), changed.colorC)
    }

    @Test fun unknownPersistedModeFallsBackToTheStraightFlutedStyle() {
        assertEquals(GlassWallpaperMode.FLUTED, GlassWallpaperMode.fromKey("not-a-mode"))
        assertEquals(GlassWallpaperMode.FLUTED, GlassWallpaperMode.fromKey("REFRACTION")) // removed mode migrates to straight fluted
    }
}
