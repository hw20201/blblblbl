package com.github.mihomo.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.ui.theme.LocalLoonColors
import com.github.mihomo.android.ui.theme.LocalGlassIntensity
import com.github.mihomo.android.ui.theme.LocalGlassEnabled

/** Selected dashboard surfaces only; settings groups deliberately remain opaque. */
@Composable
internal fun Modifier.glassCardFinish(tint: Color, radius: Dp): Modifier {
    val shape = RoundedCornerShape(radius)
    val intensity = LocalGlassIntensity.current.coerceIn(0f, 1f)
    if (!LocalGlassEnabled.current) return clip(shape).background(tint.copy(alpha = 1f))
    // Keep the tint genuinely translucent; the underlying screen remains visible.
    val base = tint.copy(alpha = 0.62f + 0.16f * intensity)
    val dark = LocalLoonColors.current.isDark
    // Adapt the source preview's diagonal specular/cool tint, not its fixed
    // light palette, larger geometry or decorative (non-live) traffic waveform.
    val highlight = lerp(base, Color.White.copy(alpha = base.alpha), (if (dark) 0.025f else 0.06f) * intensity)
    val coolTint = if (dark) Color(0xFF35445B) else Color(0xFFBBD9FF)
    val coolEdge = lerp(base, coolTint.copy(alpha = base.alpha), 0.08f * intensity)
    return clip(shape)
        .background(Brush.linearGradient(listOf(
            highlight.copy(alpha = base.alpha + 0.08f * intensity),
            base,
            coolEdge
        )))
        .border(0.8.dp, glassCardRim(), shape)
}

@Composable
internal fun glassCardRim(): Brush {
    if (!LocalGlassEnabled.current) return Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    val intensity = LocalGlassIntensity.current.coerceIn(0f, 1f)
    return Brush.verticalGradient(listOf(
        Color.White.copy(alpha = 0.48f * intensity),
        Color.White.copy(alpha = 0.13f * intensity),
        Color(0xFF74839C).copy(alpha = 0.20f * intensity)
    ))
}

/** Two layers: live backdrop frost (API 31+) then translucent tint and rim.
 * Foreground icons/labels are never blurred. Older/software renderers and hosts
 * without a backdrop use the translucent material as a safe, legible fallback.
 */
@Composable
internal fun GlassNavigationSurface(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = LocalLoonColors.current.isDark
    val intensity = LocalGlassIntensity.current.coerceIn(0f, 1f)
    val enabled = LocalGlassEnabled.current
    val shape = RoundedCornerShape(radius)
    val tint = if (dark) Color(0xFF202329) else Color(0xFFF9FBFF)
    val fill = if (!enabled) Brush.verticalGradient(listOf(tint, tint)) else {
        val alphaTop = (if (dark) 0.60f else 0.30f) + 0.12f * intensity
        val alphaBottom = (if (dark) 0.66f else 0.39f) + 0.12f * intensity
        Brush.verticalGradient(listOf(
            tint.copy(alpha = alphaTop),
            tint.copy(alpha = alphaBottom)
        ))
    }
    val rim = if (!enabled) Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)) else {
        Brush.verticalGradient(listOf(
            Color.White.copy(alpha = (if (dark) 0.16f else 0.32f) + 0.30f * intensity),
            Color.White.copy(alpha = 0.06f + 0.18f * intensity),
            (if (dark) Color(0xFF9BA9C0) else Color(0xFF66758D)).copy(alpha = 0.10f + 0.18f * intensity)
        ))
    }
    Box(
        modifier = modifier
            .then(if (enabled) Modifier.shadow(8.dp + 8.dp * intensity, shape, spotColor = Color.Black.copy(alpha = 0.10f)) else Modifier)
            .clip(shape)
    ) {
        if (enabled) {
            Box(Modifier.matchParentSize().glassBackdrop(LocalGlassBackdrop.current, intensity))
        }
        Box(Modifier.matchParentSize().background(fill).border(0.8.dp, rim, shape))
        content()
    }
}
