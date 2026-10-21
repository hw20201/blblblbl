package com.github.mihomo.android.ui.glass

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

/** Local regression switch for comparing the reusable liquid glass surface with the old path. */
internal val LocalShortcutDockLensEnabled = androidx.compose.runtime.staticCompositionLocalOf { true }
internal const val SHORTCUT_DOCK_MARGIN_DP = 10f
/** Backward-compatible test name for the reusable bottom-bar-derived kernel. */
internal val SHORTCUT_DOCK_LENS_SHADER = LIQUID_GLASS_LENS_SHADER

/**
 * A soft outline-only penumbra at rest. It stays behind the reusable glass surface and never draws
 * an opaque duplicate card or a native elevated tilt caster.
 */
internal fun Modifier.shortcutSuspensionShadow(shape: Shape, dark: Boolean): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = when (outline) {
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    }
    val penumbra = (24 downTo 1).map { step ->
        val radiusDp = step * .25f
        val nextDp = radiusDp + .25f
        val a = kotlin.math.exp(-radiusDp * radiusDp / (2f * 2.2f * 2.2f))
        val b = kotlin.math.exp(-nextDp * nextDp / (2f * 2.2f * 2.2f))
        (radiusDp * 2f).dp.toPx() to ((a - b) * if (dark) .18f else .12f)
    }
    onDrawBehind {
        clipPath(path, ClipOp.Difference) {
            translate(0f, 1.25.dp.toPx()) {
                penumbra.forEach { (width, alpha) ->
                    drawOutline(outline, Color.Black.copy(alpha = alpha), style = Stroke(width))
                }
            }
        }
    }
}

/** Compatibility adapter used by shortcut cards and rendering tests. */
@Composable
internal fun ShortcutDockGlassSurface(
    modifier: Modifier,
    shape: Shape,
    lens: NavigationLensState?,
    tokens: GlassTokens,
    refraction: () -> ShortcutRefraction,
    content: @Composable BoxScope.() -> Unit
) {
    val level = LocalShortcutGlassLevel.current
    val quality = when (rememberShortcutGlassQuality()) {
        ShortcutGlassQuality.Refracted -> LiquidGlassQuality.Refracted
        ShortcutGlassQuality.Shared -> LiquidGlassQuality.Shared
        ShortcutGlassQuality.Flat -> LiquidGlassQuality.Flat
    }
    LiquidGlassSurface(
        modifier = modifier,
        shape = shape,
        lens = lens,
        tokens = tokens,
        style = liquidGlassStyle(level),
        quality = quality,
        marginDp = SHORTCUT_DOCK_MARGIN_DP,
        cornerRadiusDp = ShortcutTunedParameters.lengthDp(60f),
        refraction = refraction,
        content = content
    )
}
