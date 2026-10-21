package com.github.mihomo.android.ui.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag

/** Only the two-column GROUP tiles requested in this experiment opt in. */
internal fun useProxyGroupGlass(isGroup: Boolean, columns: Int, hasSource: Boolean): Boolean =
    isGroup && columns == 2 && hasSource

/** Group glass owns its unselected rim; an extra card border double-strokes that edge. */
internal fun proxyCardNeedsOuterBorder(groupGlass: Boolean, selected: Boolean): Boolean =
    !groupGlass || selected

/** The dense strategy grid deliberately never creates an AGSL shader or a blur effect. */
internal fun replayProxyGroupBackdrop(apiLevel: Int, lowRam: Boolean, hasSource: Boolean): Boolean =
    hasSource && apiLevel >= 31 && !lowRam

/**
 * Replays the existing background-only display list, not the screen or another card.
 * There is no per-card source, clock, readback, Gaussian pass or RuntimeShader.
 * The fixed soft-light bevel modulates the local backdrop; labels are a separate foreground.
 */
@Composable
internal fun ProxyGroupGlass(
    modifier: Modifier,
    shape: RoundedCornerShape,
    scene: NavigationLensState,
    accent: Color?,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = LocalGlassTokens.current
    val quality = rememberShortcutGlassQuality()
    val replay = replayProxyGroupBackdrop(Build.VERSION.SDK_INT,
        quality == ShortcutGlassQuality.Flat, true)
    val tint = if (accent == null) tokens.tint else lerp(tokens.tintAccent, accent, .12f)
    val fill = Brush.linearGradient(listOf(
        tint.copy(alpha = tint.alpha * if (replay) .24f else .42f),
        tokens.tintEnd.copy(alpha = tokens.tintEnd.alpha * if (replay) .24f else .42f)
    ))
    Box(modifier.clip(shape)) {
        if (replay) {
            // No effect target/layer: just replay the one existing background display list.
            var origin by remember { mutableStateOf(Offset.Zero) }
            Box(Modifier.matchParentSize().onGloballyPositioned { origin = it.positionInRoot() }
                .drawWithContent {
                    if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                        val offset = scene.origin - origin
                        clipRect { translate(offset.x, offset.y) { drawLayer(scene.scene) } }
                    }
                })
        }
        Box(Modifier.matchParentSize().testTag("proxy-group-glass-material").background(fill).proxyGroupBevel(shape, tokens.isDark))
        Box(Modifier.matchParentSize(), content = content)
    }
}

/** Cached two-face polish. Drawn behind the foreground, with no geometry/time state reads. */
private fun Modifier.proxyGroupBevel(shape: RoundedCornerShape, dark: Boolean): Modifier = drawWithCache {
    val inset = 2.4.dp.toPx()
    val outer = shape.createOutline(size, layoutDirection, this)
    val innerSize = Size((size.width - inset * 2).coerceAtLeast(0f),
        (size.height - inset * 2).coerceAtLeast(0f))
    val innerShape = RoundedCornerShape((shape.topStart.toPx(size, this) - inset).coerceAtLeast(0f))
    val inner = innerShape.createOutline(innerSize, layoutDirection, this)
    val face = Brush.linearGradient(listOf(
        Color(0xFFD7E3F4).copy(alpha = if (dark) .65f else .85f),
        Color(0xFF8295B0).copy(alpha = .42f),
        Color(0xFF42536D).copy(alpha = .55f)
    ), start = Offset.Zero, end = Offset(size.width * .3f, size.height))
    val returnFace = Brush.verticalGradient(listOf(
        Color(0xFF50617B).copy(alpha = .38f), Color(0xFFC7D7EB).copy(alpha = .55f)
    ))
    val sheen = Brush.linearGradient(listOf(
        Color(0xFFB4C3D8).copy(alpha = .10f), Color(0xFF65758C).copy(alpha = .06f)
    ), start = Offset.Zero, end = Offset(size.width, size.height))
    val outerStroke = Stroke(1.3.dp.toPx())
    val innerStroke = Stroke(.75.dp.toPx())
    onDrawBehind {
        drawRect(sheen, blendMode = BlendMode.Softlight)
        drawOutline(outer, face, style = outerStroke, blendMode = BlendMode.Softlight)
        translate(inset, inset) {
            drawOutline(inner, returnFace, style = innerStroke, blendMode = BlendMode.Softlight)
        }
    }
}
