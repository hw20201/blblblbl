package com.github.mihomo.android.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Panel material for one palette.
 *
 * No per-panel blur is used. `Modifier.blur` requires API 31 while `minSdk` is 26, and each blurred
 * panel costs a full-size `saveLayer` — on the dashboard that is a dozen layers redrawn every time
 * the traffic counters tick. The material is expressed as *tint + rim + sheen* over the colour
 * field in [NexusGlassBackground], which is what actually sells the effect at a fraction of the cost.
 */
@Immutable
data class GlassTokens(
    /** Panel fill at the top-left. */
    val tint: Color,
    /** Panel fill at the bottom-right; the pair creates the soft diagonal falloff. */
    val tintEnd: Color,
    /** Fill used when an accent is blended underneath (upload / download cards). */
    val tintAccent: Color,
    val edge: Color,
    val rimTop: Color,
    val rimBottom: Color,
    val sheen: Float,
    val isDark: Boolean
)

val LocalGlassTokens = staticCompositionLocalOf { GlassPalettes.CREAM.toTokens() }

/**
 * Rim + sheen. Apply this to a modifier chain that is already clipped to the panel shape.
 *
 * The top rim is the single most important cue: a bright hairline that fades at both ends reads as
 * a lens edge, whereas a uniform 1px border reads as a plain card.
 */
fun Modifier.glassSurface(tokens: GlassTokens = LocalGlassTokens.current): Modifier =
    this.drawWithContent {
        drawContent()
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@drawWithContent

        // Diagonal sheen from the upper left.
        drawRect(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = tokens.sheen),
                0.40f to Color.White.copy(alpha = tokens.sheen * 0.30f),
                1f to Color.Transparent,
                start = Offset(0f, 0f),
                end = Offset(w, h * 0.9f)
            )
        )
        // Top rim, dimmed in the middle.
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Transparent,
                0.24f to tokens.rimTop,
                0.50f to tokens.rimTop.copy(alpha = tokens.rimTop.alpha * 0.38f),
                0.76f to tokens.rimTop,
                1f to Color.Transparent,
                startX = 0f,
                endX = w
            ),
            topLeft = Offset(w * 0.06f, 0f),
            size = Size(w * 0.88f, 1.2f)
        )
        // Bottom bounce.
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Transparent,
                0.5f to tokens.rimBottom,
                1f to Color.Transparent,
                startX = 0f,
                endX = w
            ),
            topLeft = Offset(w * 0.16f, h - 1f),
            size = Size(w * 0.68f, 1f)
        )
    }

/**
 * A glass slab. [accent] carries the semantic colour of a card (upload green, download blue);
 * leave it null for a neutral surface.
 *
 * Expects [NexusGlassBackground] somewhere behind it: a glass panel over flat colour is just a
 * plain card, because the material reads its colour from what is behind it.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassRadius.card),
    accent: Color? = null,
    tokens: GlassTokens = LocalGlassTokens.current,
    content: @Composable BoxScope.() -> Unit
) {
    val fill = if (accent == null) {
        Brush.linearGradient(
            listOf(tokens.tint, tokens.tintEnd),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    } else {
        // Blend the accent *below* the tint so a saturated hue cannot wash out the type.
        Brush.linearGradient(
            listOf(lerp(tokens.tintAccent, accent, 0.26f), tokens.tintEnd),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .glassSurface(tokens),
        content = content
    )
}

/** Convenience for the "row inside a section" case. */
@Composable
fun GlassSection(
    modifier: Modifier = Modifier,
    tokens: GlassTokens = LocalGlassTokens.current,
    content: @Composable BoxScope.() -> Unit
) = GlassSurface(
    modifier = modifier,
    shape = RoundedCornerShape(GlassRadius.section),
    tokens = tokens,
    content = content
)

/** Corner radii, aligned with the Cupertino surface family the restored UI uses. */
object GlassRadius {
    val section: Dp = 14.dp
    val card: Dp = 18.dp
    val bar: Dp = 32.dp
    val sheet: Dp = 28.dp
}
