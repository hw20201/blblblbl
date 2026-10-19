package com.github.mihomo.android.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

/** The source must be a sibling *below* the glass, never an ancestor of it.
 * Replays the live display list, not a screenshot; no bitmap capture, timer or
 * readback. The source does not contain the navigation or its labels.
 */
@Stable
internal class GlassBackdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}

internal val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

@Composable
internal fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

internal fun Modifier.glassBackdropSource(backdrop: GlassBackdrop, enabled: Boolean = true): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return onGloballyPositioned { backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                backdrop.layer.record { this@drawWithContent.drawContent() }
                drawLayer(backdrop.layer)
            } else {
                // Bitmap Canvas and software windows cannot draw RenderNodes.
                drawContent()
            }
        }
}

/** Apply only to an empty background child, never to the text/content container. */
@Composable
internal fun Modifier.glassBackdrop(backdrop: GlassBackdrop?, intensity: Float): Modifier {
    if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    var origin by remember(backdrop) { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { origin = it.positionInRoot() }
        .graphicsLayer {
            // A minimum frost remains at 0%; the switch, not 0%, disables glass.
            val radius = (6.dp + 22.dp * intensity).toPx()
            renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
        }
        .drawWithContent {
            if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                val offset = backdrop.origin - origin
                translate(offset.x, offset.y) { drawLayer(backdrop.layer) }
            }
        }
}
