package com.github.mihomo.android.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.composed
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.ui.theme.LocalLoonColors
import com.github.mihomo.android.ui.theme.LocalGlassIntensity
import com.github.mihomo.android.ui.theme.LocalGlassEnabled

/** Selected panes only. Blur the background display list, never the foreground. */
@Composable
internal fun Modifier.glassCardFinish(tint: Color, radius: Dp): Modifier {
    val shape = RoundedCornerShape(radius)
    val intensity = LocalGlassIntensity.current.coerceIn(0f, 1f)
    val dark = LocalLoonColors.current.isDark
    if (!LocalGlassEnabled.current) return clip(shape).background(tint.copy(alpha = 1f))
    return shadow(5.dp + 5.dp * intensity, shape, spotColor = Color(0xFF617FAB).copy(alpha = 0.12f))
        .clip(shape)
        .glassMaterialReplay(LocalGlassMaterialBackdrop.current, intensity)
        .background(Brush.linearGradient(listOf(
            tint.copy(alpha = (if (dark) 0.61f else 0.52f) + 0.13f * intensity),
            (if (dark) Color(0xFF24364A) else Color(0xFFE5F3FF)).copy(alpha = 0.24f + 0.12f * intensity),
            tint.copy(alpha = (if (dark) 0.50f else 0.42f) + 0.12f * intensity)
        )))
        .border(0.85.dp, glassCardRim(), shape)
}

private class PanePosition { var origin = Offset.Zero }

/** Replay through a separate effect layer: labels are never recorded or blurred. */
private fun Modifier.glassMaterialReplay(backdrop: GlassBackdrop?, intensity: Float): Modifier = composed {
    val position = remember { PanePosition() }
    val frost = rememberGraphicsLayer()
    onGloballyPositioned { position.origin = it.positionInRoot() }.drawWithContent {
        val hardwareBlur = Build.VERSION.SDK_INT >= 31 && drawContext.canvas.nativeCanvas.isHardwareAccelerated
        if (backdrop != null && hardwareBlur && backdrop.layer.size.width > 0) {
            val radius = (6.dp + 22.dp * intensity).toPx()
            frost.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
            frost.record {
                val offset = backdrop.origin - position.origin
                translate(offset.x, offset.y) { drawLayer(backdrop.layer) }
            }
            drawLayer(frost)
        }
        drawContent()
    }
}

@Composable
internal fun glassCardRim(): Brush {
    val intensity = LocalGlassIntensity.current.coerceIn(0f, 1f)
    val dark = LocalLoonColors.current.isDark
    if (!LocalGlassEnabled.current) return Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    return Brush.linearGradient(listOf(
        Color.White.copy(alpha = if (dark) 0.10f + 0.12f * intensity else 0.58f + 0.24f * intensity),
        Color.White.copy(alpha = if (dark) 0.04f + 0.06f * intensity else 0.08f + 0.16f * intensity),
        Color(0xFFA8CBEA).copy(alpha = if (dark) 0.10f + 0.10f * intensity else 0.18f + 0.20f * intensity),
        Color.White.copy(alpha = if (dark) 0.08f + 0.10f * intensity else 0.28f + 0.28f * intensity)
    ))
}

/** Navigation samples live scrolling content independently of wallpaper-only cards. */
@Composable
internal fun GlassNavigationSurface(
    modifier: Modifier = Modifier,
    radius: Dp = 32.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = LocalLoonColors.current.isDark
    val intensity = LocalGlassIntensity.current.coerceIn(0f, 1f)
    val enabled = LocalGlassEnabled.current
    val shape = RoundedCornerShape(radius)
    val tint = if (dark) Color(0xFF202329) else Color(0xFFF9FBFF)
    val fill = if (!enabled) Brush.verticalGradient(listOf(tint, tint)) else Brush.verticalGradient(listOf(
        tint.copy(alpha = (if (dark) 0.60f else 0.30f) + 0.12f * intensity),
        tint.copy(alpha = (if (dark) 0.66f else 0.39f) + 0.12f * intensity)
    ))
    Box(modifier = modifier
        .then(if (enabled) Modifier.shadow(8.dp + 8.dp * intensity, shape, spotColor = Color(0xFF63799D).copy(alpha = 0.14f)) else Modifier)
        .clip(shape)) {
        if (enabled) Box(Modifier.matchParentSize().glassBackdrop(LocalGlassBackdrop.current, intensity))
        Box(Modifier.matchParentSize().background(fill).border(0.85.dp, glassCardRim(), shape))
        content()
    }
}
