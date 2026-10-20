package com.github.mihomo.android.ui.glass

import android.graphics.Matrix
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** True Gaussian filtering is available only on API 31+ hardware canvases. */
internal fun shouldBlurCardBackdrop(apiLevel: Int, hardwareAccelerated: Boolean): Boolean =
    apiLevel >= 31 && hardwareAccelerated

/** Review switch: disable only Gaussian diffusion, retaining shortcut geometry and bevel. */
internal val LocalShortcutBlurEnabled = staticCompositionLocalOf { true }

/** Map the untransformed scene into this plane, including inverse scale/perspective, not
 * merely a top-left translation. No capture/readback and no change to navigation's recorder.
 */
private fun DrawScope.replayCardScene(lens: NavigationLensState, coordinates: LayoutCoordinates?, origin: Offset, anchored: Boolean) {
    val plane = coordinates?.takeIf { anchored && it.isAttached }
    val hasScene = lens.scene.size.width > 0 && lens.scene.size.height > 0
    if (plane != null && hasScene) {
        // Four forward-projected coplanar corners define the true 2D homography.
        // Inverting a full 3D matrix with an assumed root z=0 is not equivalent.
        val local = floatArrayOf(0f, 0f, size.width, 0f, size.width, size.height, 0f, size.height)
        val scene = FloatArray(8)
        for (i in 0..3) {
            val p = plane.localToRoot(Offset(local[i * 2], local[i * 2 + 1])) - lens.origin
            scene[i * 2] = p.x; scene[i * 2 + 1] = p.y
        }
        val matrix = Matrix()
        if (scene.all { it.isFinite() } && matrix.setPolyToPoly(scene, 0, local, 0, 4)) {
            drawContext.canvas.save()
            try {
                drawContext.canvas.nativeCanvas.concat(matrix)
                drawLayer(lens.scene)
            } finally { drawContext.canvas.restore() }
            return
        }
    }
    val offset = lens.origin - origin
    translate(offset.x, offset.y) { drawLayer(lens.scene) }
}

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
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val opticalReplay = softMeshRim && shaderEnabled
    val followup = tunedMaterial && LocalShortcutOpticalFollowupEnabled.current
    val bend = LocalShortcutRefractionEnabled.current
    val rim = remember(opticalReplay, tunedMaterial) {
        if (opticalReplay && !tunedMaterial && Build.VERSION.SDK_INT >= 33) ShortcutMeshOptics() else null
    }
    val legacyTuned = opticalReplay && tunedMaterial && !followup
    val tuned = remember(legacyTuned) {
        if (legacyTuned && Build.VERSION.SDK_INT >= 33) ShortcutTunedOptics() else null
    }
    val refractive = remember(opticalReplay, followup) {
        if (opticalReplay && followup && Build.VERSION.SDK_INT >= 33) ShortcutRefractiveOptics() else null
    }
    val frost = remember(contactFrost, blurEnabled) {
        if (contactFrost && blurEnabled && Build.VERSION.SDK_INT >= 31) ShortcutContactFrost() else null
    }
    val blur = remember(density, softMeshRim, blurEnabled) {
        if (!softMeshRim && blurEnabled && Build.VERSION.SDK_INT >= 31) {
            RenderEffect.createBlurEffect(16f * density, 16f * density, Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else null
    }
    val padded = refractive != null
    val margin = if (padded) (SHORTCUT_BACKDROP_OVERSCAN_DP * density).roundToInt().coerceAtLeast(1) else 0
    val overscan = if (padded) Modifier.layout { measurable, constraints ->
        val w = constraints.maxWidth; val h = constraints.maxHeight
        val child = measurable.measure(Constraints.fixed(w + 2 * margin, h + 2 * margin))
        layout(w, h) { child.place(-margin, -margin) }
    } else Modifier
    Box(Modifier.matchParentSize()
        .then(overscan)
        .onGloballyPositioned { origin = it.positionInRoot(); coordinates = it }
        .graphicsLayer {
            val refraction = shortcutRefraction()
            if (Build.VERSION.SDK_INT >= 33 && refractive != null) {
                renderEffect = refractive.update(size.width - margin * 2, size.height - margin * 2,
                    density, margin.toFloat(), isDark,
                    if (blurEnabled) refraction else refraction.copy(frostLift = 0f), bend)
            } else if (Build.VERSION.SDK_INT >= 33 && tuned != null) {
                tuned.update(size.width, size.height, density, isDark,
                    if (blurEnabled) refraction else refraction.copy(frostLift = 0f))
                renderEffect = tuned.effect
            } else if (Build.VERSION.SDK_INT >= 33 && rim != null) {
                rim.update(size.width, size.height, density, GlassRadius.card.toPx(), isDark, hybrid, refraction)
                renderEffect = frost?.update(refraction.frostLift, density, rim.nativeEffect) ?: rim.effect
            } else {
                val maximum = if (followup) (ShortcutFollowupParameters.blurRadiusDp * 2f).coerceAtMost(8f)
                    else if (tunedMaterial) 6f else 8f
                renderEffect = if (Build.VERSION.SDK_INT >= 31) frost?.update(refraction.frostLift, density, null, maximum) ?: blur else null
            }
        }
        .drawWithContent {
            // Observe actual plane animations too: clamped touch uniforms must not freeze
            // the replay while the card keeps translating or springs back.
            shortcutRefraction()
            if (shouldBlurCardBackdrop(Build.VERSION.SDK_INT, drawContext.canvas.nativeCanvas.isHardwareAccelerated)) {
                if (softMeshRim) {
                    // The outer GlassSurface clips the rounded result AFTER filtering,
                    // not its sampling neighbourhood. Input has a finite explicit extent.
                    clipRect { replayCardScene(lens, coordinates, origin, padded) }
                } else {
                    replayCardScene(lens, coordinates, origin, false)
                }
            } else {
                drawRect(fallback)
            }
        })
}
