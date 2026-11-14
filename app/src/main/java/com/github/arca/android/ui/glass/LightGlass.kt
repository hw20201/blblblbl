package com.github.arca.android.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * Low-cost self-developed glass for dense, repeated surfaces (proxy node capsules, notices).
 *
 * No backdrop replay, RenderEffect or RuntimeShader: a translucent
 * diagonal tint, one cached specular hairline on top, a soft inner rim and a faint bottom bounce.
 * Every brush is created in drawWithCache, so scrolling a long node list only replays paint.
 */
@Composable
fun LightGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    selected: Boolean = false,
    accent: Color? = null,
    tokens: GlassTokens = LocalGlassTokens.current,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.clip(shape).lightGlass(shape, tokens, selected, accent), content = content)
}

/** Neutral frost used for the selected node; matches the strategy-group capsule in the reference UI. */
val LightGlassSelectedTint = Color(0xFFABB1BC)

fun Modifier.lightGlass(
    shape: Shape,
    tokens: GlassTokens,
    selected: Boolean = false,
    accent: Color? = null
): Modifier = this.drawWithCache {
    val w = size.width
    val h = size.height
    val baseTint = when {
        selected -> LightGlassSelectedTint
        accent != null -> lerp(tokens.tintAccent, accent, .14f)
        else -> tokens.tint
    }
    val fillAlpha = if (selected) .80f else if (tokens.isDark) .30f else .38f
    val fill = Brush.linearGradient(
        listOf(
            baseTint.copy(alpha = (baseTint.alpha * fillAlpha).coerceIn(0f, 1f)),
            (if (selected) baseTint else tokens.tintEnd).copy(
                alpha = ((if (selected) baseTint.alpha else tokens.tintEnd.alpha) * fillAlpha * .86f).coerceIn(0f, 1f)
            )
        ),
        start = Offset.Zero,
        end = Offset(w, h)
    )
    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (tokens.isDark) .05f else .10f),
        .45f to Color.Transparent,
        1f to Color.Transparent
    )
    val outline = shape.createOutline(size, layoutDirection, this)
    val rim = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (tokens.isDark) .22f else .62f),
            tokens.edge.copy(alpha = tokens.edge.alpha * .45f),
            Color.White.copy(alpha = if (tokens.isDark) .06f else .22f)
        ),
        start = Offset.Zero,
        end = Offset(w * .4f, h)
    )
    val rimStroke = Stroke(width = .8.dp.toPx())
    val highlightW = w * .72f
    val highlight = Brush.horizontalGradient(
        0f to Color.Transparent,
        .5f to Color.White.copy(alpha = if (tokens.isDark) .28f else .78f),
        1f to Color.Transparent,
        startX = (w - highlightW) / 2f,
        endX = (w + highlightW) / 2f
    )
    val bounce = Brush.horizontalGradient(
        0f to Color.Transparent,
        .5f to tokens.rimBottom.copy(alpha = tokens.rimBottom.alpha * .7f),
        1f to Color.Transparent
    )
    onDrawBehind {
        drawOutline(outline, fill)
        drawOutline(outline, sheen)
        drawOutline(outline, rim, style = rimStroke)
        drawRect(highlight, topLeft = Offset((w - highlightW) / 2f, .6f), size = Size(highlightW, 1.1f))
        drawRect(bounce, topLeft = Offset(w * .2f, h - 1.4f), size = Size(w * .6f, 1f))
    }
}

/** Plain-background variant for screens that disabled glass entirely. */
fun Modifier.lightGlassOrFlat(shape: Shape, tokens: GlassTokens, flat: Color, selected: Boolean = false): Modifier =
    if (GlassDefaults.enabled) lightGlass(shape, tokens, selected) else background(flat, shape)
