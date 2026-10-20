package com.github.mihomo.android.ui.glass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity

/** True Gaussian filtering is available only on API 31+ hardware canvases. */
internal fun shouldBlurCardBackdrop(apiLevel: Int, hardwareAccelerated: Boolean): Boolean =
    apiLevel >= 31 && hardwareAccelerated

/** Legacy preview switch; soft-mesh shortcuts now skip Gaussian filtering in either state. */
internal val LocalShortcutBlurEnabled = staticCompositionLocalOf { true }

/** Replay only the backdrop at its root-relative position; never record card content here. */
@Composable
internal fun BoxScope.CardBackdrop(
    lens: NavigationLensState,
    fallback: Brush,
    blurEnabled: Boolean = true,
    softMeshRim: Boolean = false,
    isDark: Boolean = false,
    hybrid: Boolean = false,
    shaderEnabled: Boolean = true,
    shortcutRefraction: () -> ShortcutRefraction = { ShortcutRefraction() }
) {
    val density = LocalDensity.current.density
    var origin by remember { mutableStateOf(Offset.Zero) }
    val rim = remember(softMeshRim, shaderEnabled) {
        if (softMeshRim && shaderEnabled && Build.VERSION.SDK_INT >= 33) ShortcutMeshOptics() else null
    }
    val blur = remember(density, softMeshRim, blurEnabled) {
        if (!softMeshRim && blurEnabled && Build.VERSION.SDK_INT >= 31) {
            RenderEffect.createBlurEffect(16f * density, 16f * density, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        } else null
    }
    Box(Modifier.matchParentSize()
        .onGloballyPositioned { origin = it.positionInRoot() }
        .graphicsLayer {
            // Read here, not during composition, so animated snapshot state invalidates the layer.
            val refraction = shortcutRefraction()
            if (Build.VERSION.SDK_INT >= 33 && rim != null) {
                rim.update(size.width, size.height, density, GlassRadius.card.toPx(), isDark, hybrid, refraction)
                renderEffect = rim.effect
            } else {
                renderEffect = blur
            }
        }
        .drawWithContent {
            if (shouldBlurCardBackdrop(Build.VERSION.SDK_INT, drawContext.canvas.nativeCanvas.isHardwareAccelerated)) {
                val offset = lens.origin - origin
                if (softMeshRim) {
                    // The shared display list is screen-sized. Bound its replay before the
                    // effect, otherwise its input extent displaces the card-local bevel.
                    // This is a draw clip, not another capture, layer or filtering pass.
                    clipRect { translate(offset.x, offset.y) { drawLayer(lens.scene) } }
                } else {
                    translate(offset.x, offset.y) { drawLayer(lens.scene) }
                }
            } else {
                // Software canvases and older Android never replay an unrecorded GPU scene.
                drawRect(fallback)
            }
        })
}
