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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.ui.theme.LocalLoonColors
import com.github.mihomo.android.ui.theme.LocalGlassIntensity

/** Selected dashboard surfaces only; settings groups deliberately remain opaque. */
@Composable
internal fun Modifier.glassCardFinish(tint: Color, radius: Dp): Modifier {
    val shape = RoundedCornerShape(radius)
    val intensity = LocalGlassIntensity.current
    return clip(shape)
        .background(Brush.verticalGradient(listOf(
            tint.copy(alpha = 1f - 0.04f * intensity),
            tint.copy(alpha = 1f - 0.12f * intensity)
        )))
        .border(0.75.dp, glassCardRim(), shape)
}

@Composable
internal fun glassCardRim(): Brush {
    val intensity = LocalGlassIntensity.current
    return Brush.verticalGradient(listOf(
        Color.White.copy(alpha = 0.85f * intensity),
        Color.White.copy(alpha = 0.12f * intensity),
        Color(0xFF74839C).copy(alpha = 0.16f * intensity)
    ))
}

/**
 * A restrained glass-inspired surface, not a backdrop/refraction shader.
 * Dense tint keeps scrolling text from competing with the navigation labels.
 * Only the surface is translucent: content remains sharp at every Android API level.
 * No continuous animation, screen capture, RenderEffect, or extra render dependency.
 */
@Composable
internal fun GlassNavigationSurface(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = LocalLoonColors.current.isDark
    val intensity = LocalGlassIntensity.current
    val shape = RoundedCornerShape(radius)
    val tint = if (dark) Color(0xFF202329) else Color(0xFFF9FBFF)
    val fill = Brush.verticalGradient(
        listOf(
            tint.copy(alpha = 1f - 0.06f * intensity),
            tint.copy(alpha = 1f - (if (dark) 0.12f else 0.16f) * intensity)
        )
    )
    val rim = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = (if (dark) 0.38f else 0.96f) * intensity),
            Color.White.copy(alpha = (if (dark) 0.12f else 0.52f) * intensity),
            (if (dark) Color.White else Color(0xFF74839C)).copy(alpha = 0.28f * intensity)
        )
    )
    Box(
        modifier = modifier
            .shadow(6.dp, shape, spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(shape)
            .background(fill)
            .border(0.75.dp, rim, shape),
        content = content
    )
}
