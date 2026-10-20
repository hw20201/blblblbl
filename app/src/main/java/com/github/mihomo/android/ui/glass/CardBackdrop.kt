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

/** Review switch: disable only Gaussian diffusion, retaining shortcut geometry and bevel. */
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
    contactFrost: Boolean = false,
    tunedMaterial: Boolean = false,
    shortcutRefraction: () -> ShortcutRefraction = { ShortcutRefraction() }
) {
    val density = LocalDensity.current.density
    var origin by remember { mutableStateOf(Offset.Zero) }
    val opticalReplay = softMeshRim && shaderEnabled
    val rim = remember(opticalReplay, tunedMaterial) {
        if (opticalReplay && !tunedMaterial && Build.VERSION.SDK_INT >= 33) ShortcutMeshOptics() else null
    }
    val tuned = remember(opticalReplay, tunedMaterial, blurEnabled) {
        if (opticalReplay && tunedMaterial && Build.VERSION.SDK_INT >= 33) ShortcutTunedOptics() else null
    }
    val frost = remember(contactFrost, blurEnabled) {
        if (contactFrost && blurEnabled && Build.VERSION.SDK_INT >= 31) ShortcutContactFrost() else null
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
            if (Build.VERSION.SDK_INT >= 33 && tuned != null) {
                // One backdrop-only native effect. The scene and foreground never get filtered together.
                tuned.update(size.width, size.height, density, isDark,
                    if (blurEnabled) refraction else refraction.copy(frostLift = 0f))
                renderEffect = tuned.effect
            } else if (Build.VERSION.SDK_INT >= 33 && rim != null) {
                rim.update(size.width, size.height, density, GlassRadius.card.toPx(), isDark, hybrid, refraction)
                renderEffect = frost?.update(refraction.frostLift, density, rim.nativeEffect) ?: rim.effect
            } else {
                // API 31/32's native blur-radius convention differs from our AGSL sigma.
                // 6dp radius is visually calibrated against the 3dp finite-kernel path.
                renderEffect = if (Build.VERSION.SDK_INT >= 31) frost?.update(refraction.frostLift, density, null, if (tunedMaterial) 6f else 8f) ?: blur else null
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
