package com.github.mihomo.android.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** Straight fluted ribs only. The curved-refraction mode was removed:
 *  old saved "REFRACTION" values migrate to FLUTED instead of crashing. */
enum class GlassWallpaperMode {
    FLUTED;

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromKey(key: String?): GlassWallpaperMode = FLUTED
    }
}

@Immutable
data class GlassWallpaperSettings(
    val enabled: Boolean = false,
    val mode: GlassWallpaperMode = GlassWallpaperMode.FLUTED,
    val colorA: Int = COLOR_A,
    val colorB: Int = COLOR_B,
    val colorC: Int = COLOR_C,
    val colorFlow: Boolean = true
) {
    fun colorAt(index: Int): Color = Color(when (index) {
        0 -> colorA
        1 -> colorB
        else -> colorC
    })

    fun withColor(index: Int, color: Color): GlassWallpaperSettings {
        val argb = color.toArgb()
        return when (index) {
            0 -> copy(enabled = true, colorA = argb)
            1 -> copy(enabled = true, colorB = argb)
            else -> copy(enabled = true, colorC = argb)
        }
    }

    companion object {
        const val COLOR_A: Int = -4664849 // #FFB8D1EF
        const val COLOR_B: Int = -3222813 // #FFCED2E3
        const val COLOR_C: Int = -4661010 // #FFB8E0EE
    }
}

/** Cycles one slow, low-amplitude colour blend shift; no geometry or palette values change. */
internal fun wallpaperFlowOffset(seconds: Float, enabled: Boolean): Float {
    if (!enabled || !seconds.isFinite()) return 0f
    return sin(seconds.coerceAtLeast(0f) * (2.0 * PI / 54.0)).toFloat() * .045f
}

/** The three user-selected RGB values remain the only colour stops in the straight fluted wallpaper. */
internal fun wallpaperGradientStops(settings: GlassWallpaperSettings, phase: Float): List<Pair<Float, Color>> {
    val middle = (.5f + phase.coerceIn(-.045f, .045f)).coerceIn(.455f, .545f)
    return listOf(0f to settings.colorAt(0), middle to settings.colorAt(1), 1f to settings.colorAt(2))
}

/** Draws the generated wallpaper below the existing shared glass recorder/lens pipeline. */
internal fun DrawScope.drawGlassWallpaper(
    settings: GlassWallpaperSettings,
    seconds: Float,
    logicalHeight: Float = size.height
) {
    val phase = wallpaperFlowOffset(seconds, settings.colorFlow)
    val stops = wallpaperGradientStops(settings, phase)
    val colors = stops.map { it.second }
    val middle = stops[1].first
    // Straight fluted only: map the optimized script's .65*x + .35*y colour
    // coordinate across the whole portrait canvas. No curved-refraction branch.
    val xWeight = .65f / size.width.coerceAtLeast(1f)
    val yWeight = .35f / logicalHeight.coerceAtLeast(1f)
    val denominator = xWeight * xWeight + yWeight * yWeight
    val gradient = Brush.linearGradient(
        colorStops = arrayOf(0f to colors[0], middle to colors[1], 1f to colors[2]),
        start = Offset.Zero,
        end = Offset(xWeight / denominator, yWeight / denominator)
    )
    drawRect(gradient)
    drawFlutedToneProfile(logicalHeight)
    drawFlutedRibs()
}

/** Script's subdued dark-to-bright-to-dark value profile, with its gentle peak near 75% height. */
private fun DrawScope.drawFlutedToneProfile(logicalHeight: Float) {
    drawRect(
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = .035f),
            .75f to Color.White.copy(alpha = .105f),
            1f to Color.Black.copy(alpha = .055f),
            startY = 0f,
            endY = logicalHeight.coerceAtLeast(1f)
        )
    )
}

/** 29 evenly spaced fluted ribs, tilted about 7 degrees, leaning left as they go down
 *  (matching the Python generator's tilt direction).
 *
 *  FIX (2026-10-24, screenshot diagnosis): the old code only drew two thin
 *  bevel lines per rib (1.15dp white / 1.45dp dark). On a real phone that reads
 *  as white hairlines on a flat gradient: measured on the R8 v58 screenshot,
 *  bright spikes occupy only ~11% of the width, the other ~89% is flat.
 *  The target texture instead shades the WHOLE rib width with a slow
 *  sin-like ramp (dark at the left edge, bright at the right edge, then a
 *  sharp drop at the next rib). That is what drawFlutedRibs does now:
 *  each rib is a slanted parallelogram filled with a gradient across its
 *  full width, not a pair of lines. */
internal const val FLUTED_RIB_COUNT = 29
internal const val FLUTED_RIDGE_ANGLE_DEGREES = 7f
/** Left edge of each rib: slightly dark, so the drop from the previous rib's bright edge reads. */
internal const val FLUTED_RIB_DARK_ALPHA = .12f
/** Right edge of each rib: the bright end of the sin-like ramp. */
internal const val FLUTED_RIB_BRIGHT_ALPHA = .25f

internal fun flutedRibX(index: Int, width: Float): Float =
    (index + .5f) * width / FLUTED_RIB_COUNT.toFloat()

internal fun flutedRibXAtY(index: Int, width: Float, height: Float, y: Float): Float {
    val safeHeight = height.coerceAtLeast(1f)
    val slope = kotlin.math.tan(Math.toRadians(FLUTED_RIDGE_ANGLE_DEGREES.toDouble())).toFloat()
    return flutedRibX(index, width) - (y / safeHeight - .5f) * safeHeight * slope
}

private fun DrawScope.drawFlutedRibs() {
    val safeHeight = size.height.coerceAtLeast(1f)
    val spacing = size.width / FLUTED_RIB_COUNT.toFloat()
    val slope = kotlin.math.tan(Math.toRadians(FLUTED_RIDGE_ANGLE_DEGREES.toDouble())).toFloat()
    // Rib direction is (-slope, 1); width direction is its perpendicular (1, slope),
    // normalized. A gradient along the width direction is constant along a rib
    // and varies only across it, so the ramp follows the 7-degree slant at every height.
    val widthNorm = sqrt(1f + slope * slope)
    val widthDirX = 1f / widthNorm
    val widthDirY = slope / widthNorm
    val extraRibs = kotlin.math.ceil((safeHeight * slope * .5f) / spacing.coerceAtLeast(1f)).toInt() + 1
    for (index in -extraRibs..(FLUTED_RIB_COUNT - 1 + extraRibs)) {
        val centerX = flutedRibXAtY(index, size.width, safeHeight, safeHeight / 2f)
        val centerY = safeHeight / 2f
        val half = spacing / 2f
        val topX = flutedRibXAtY(index, size.width, safeHeight, 0f)
        val bottomX = flutedRibXAtY(index, size.width, safeHeight, safeHeight)
        val rib = Path().apply {
            moveTo(topX - half, 0f)
            lineTo(topX + half, 0f)
            lineTo(bottomX + half, safeHeight)
            lineTo(bottomX - half, safeHeight)
            close()
        }
        // Sin-like ramp, matching the Python hl2 model: fast rise, flat bright top.
        // 0.0 -> dark left edge, 0.5 -> ~70% bright, 1.0 -> full bright right edge.
        // The next rib restarts at its dark edge, giving the sharp rib separation.
        val ramp = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = FLUTED_RIB_DARK_ALPHA),
                .25f to Color.White.copy(alpha = .05f),
                .5f to Color.White.copy(alpha = .16f),
                .75f to Color.White.copy(alpha = .22f),
                1f to Color.White.copy(alpha = FLUTED_RIB_BRIGHT_ALPHA)
            ),
            start = Offset(centerX - widthDirX * half, centerY - widthDirY * half),
            end = Offset(centerX + widthDirX * half, centerY + widthDirY * half)
        )
        drawPath(rib, ramp)
    }
}
