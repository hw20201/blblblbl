package com.github.mihomo.android.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Local regression switch: the previous contact/frost renderer remains independently testable. */
internal val LocalShortcutDockLensEnabled = staticCompositionLocalOf { true }
internal const val SHORTCUT_DOCK_MARGIN_DP = 8f

/** Exactly the dock's optical kernel, with an overscan origin and finite larger input only.
 * No second lens approximation, contact dent, Gaussian pass, magnification or duplicate projection.
 */
internal val SHORTCUT_DOCK_LENS_SHADER = NAVIGATION_LENS_SHADER
    .replace("uniform float2 viewport;", "uniform float2 viewport;\nuniform float2 inputOrigin;\nuniform float2 inputViewport;")
    .replace("uniform float aberration;", "uniform float aberration;\nuniform float chromaticSamples;")
    .replace("float2 q = p - viewport * 0.5;", "float2 q = (p - inputOrigin) - viewport * 0.5;")
    .replace("float2 hi = viewport - float2(0.5);", "float2 hi = inputViewport - float2(0.5);")
    .replace(
        """        half4 cr = scene.eval(clamp(base + normal * ca, lo, hi));
        half4 cb = scene.eval(clamp(base - normal * ca, lo, hi));
        half4 c = half4(cr.r, cg.g, cb.b, cg.a);""",
        """        half4 c = cg;
        // Flat centre: one sample. Normal retains subtle one-sided chromatic separation.
        if (edge > 0.001 && aberration > 0.001) {
            c.r = scene.eval(clamp(base + normal * ca, lo, hi)).r;
            if (chromaticSamples > 1.5) {
                c.b = scene.eval(clamp(base - normal * ca, lo, hi)).b;
            }
        }"""
    )

private class ShortcutDockEffectCache {
    var key: List<Float> = emptyList()
    var effect: androidx.compose.ui.graphics.RenderEffect? = null
}

/** A soft outline-only penumbra at rest: no filled duplicate card or native elevated tilt caster. */
internal fun Modifier.shortcutSuspensionShadow(shape: Shape, dark: Boolean): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = when (outline) {
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    }
    // Cached fine-step Gaussian falloff (24 sub-dp strokes), not four hard contour bands.
    // No per-frame blur pass/bitmap and no elevated tilted native caster.
    val penumbra = (24 downTo 1).map { step ->
        val radiusDp = step * .25f
        val nextDp = radiusDp + .25f
        val a = kotlin.math.exp(-radiusDp * radiusDp / (2f * 2.2f * 2.2f))
        val b = kotlin.math.exp(-nextDp * nextDp / (2f * 2.2f * 2.2f))
        (radiusDp * 2f).dp.toPx() to ((a - b) * if (dark) .18f else .12f)
    }
    onDrawBehind {
        // Exclude the glass interior: a transparent card must not reveal an opaque shadow slab.
        clipPath(path, ClipOp.Difference) {
            translate(0f, 1.25.dp.toPx()) {
                penumbra.forEach { (width, alpha) ->
                    drawOutline(outline, Color.Black.copy(alpha = alpha), style = Stroke(width))
                }
            }
        }
    }
}

/** Shortcut body using the same scene-dependent refraction/dispersion as the bottom thumb.
 * The shared recorder contains background only; labels/icons are always a final clear sibling.
 */
@Composable
internal fun ShortcutDockGlassSurface(
    modifier: Modifier,
    shape: Shape,
    lens: NavigationLensState?,
    tokens: GlassTokens,
    refraction: () -> ShortcutRefraction,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current.density
    val level = LocalShortcutGlassLevel.current
    val quality = rememberShortcutGlassQuality()
    val replay = lens != null && Build.VERSION.SDK_INT >= 31 && quality != ShortcutGlassQuality.Flat
    val shader = remember(replay, quality) {
        if (replay && quality == ShortcutGlassQuality.Refracted && Build.VERSION.SDK_INT >= 33)
            RuntimeShader(SHORTCUT_DOCK_LENS_SHADER) else null
    }
    val margin = (SHORTCUT_DOCK_MARGIN_DP * density).roundToInt()
    var origin by remember { mutableStateOf(Offset.Zero) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val effectCache = remember(shader) { ShortcutDockEffectCache() }
    Box(modifier.clip(shape)) {
        if (replay && lens != null) {
            val overscan = Modifier.layout { measurable, constraints ->
                val w = constraints.maxWidth; val h = constraints.maxHeight
                val child = measurable.measure(Constraints.fixed(w + margin * 2, h + margin * 2))
                layout(w, h) { child.place(-margin, -margin) }
            }
            Box(Modifier.matchParentSize().then(overscan)
                .onGloballyPositioned { origin = it.positionInRoot(); coordinates = it }
                .graphicsLayer {
                    refraction() // observe live pose even when the pointer stops moving
                    if (Build.VERSION.SDK_INT >= 33 && shader != null) {
                        val strength = level.strength
                        val key = listOf(size.width, size.height, density, level.ordinal.toFloat())
                        if (effectCache.key != key) {
                            shader.setFloatUniform("viewport", size.width - 2 * margin, size.height - 2 * margin)
                            shader.setFloatUniform("inputOrigin", margin.toFloat(), margin.toFloat())
                            shader.setFloatUniform("inputViewport", size.width, size.height)
                            shader.setFloatUniform("density", density)
                            shader.setFloatUniform("displacement", strength.displacementDp)
                            shader.setFloatUniform("bevel", strength.bevelDp)
                            shader.setFloatUniform("saturation", strength.saturation)
                            shader.setFloatUniform("aberration", level.aberrationDp)
                            shader.setFloatUniform("chromaticSamples", level.chromaticSamples.toFloat())
                            shader.setFloatUniform("rimGain", strength.rimGain)
                            shader.setFloatUniform("cornerRadius", ShortcutTunedParameters.lengthDp(60f))
                            effectCache.effect = RenderEffect.createRuntimeShaderEffect(shader, "scene").asComposeRenderEffect()
                            effectCache.key = key
                        }
                        renderEffect = effectCache.effect
                    }
                }
                .drawWithContent {
                    refraction()
                    if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                        clipRect { replayCardScene(lens, coordinates, origin, anchored = true) }
                    } else {
                        drawRect(if (tokens.isDark) Color(0xFF151D2C) else Color(0xFFDCE5F6))
                    }
                })
        }
        // Dock shell: faint white tint, transparent centre, asymmetric rim. Never drawn over labels.
        Box(Modifier.matchParentSize().drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val fallback = if (tokens.isDark) Color(0xFF151D2C) else Color(0xFFDCE5F6)
            val sheen = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (replay) .055f * level.rimAlpha else .15f * level.rimAlpha),
                .35f to Color.Transparent,
                .85f to Color.Transparent,
                1f to Color.White.copy(alpha = if (tokens.isDark) .03f else .08f)
            )
            val edge = Brush.verticalGradient(
                0f to Color.White.copy(alpha = (if (tokens.isDark) .28f else .35f) * level.rimAlpha),
                .45f to Color.White.copy(alpha = .025f),
                1f to Color(0xFF91B8EF).copy(alpha = if (tokens.isDark) .16f else .10f)
            )
            onDrawBehind {
                if (!replay) drawRect(fallback)
                drawRect(Color.White.copy(alpha = if (replay) level.shellAlpha else .10f))
                drawRect(sheen)
                drawOutline(outline, edge, style = Stroke(.9.dp.toPx()))
            }
        })
        Box(Modifier.matchParentSize(), content = content)
    }
}
