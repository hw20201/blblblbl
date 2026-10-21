package com.github.mihomo.android.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Reusable scene-backed liquid glass surface.
 *
 * The foreground stays outside the RenderEffect. Only the recorded background scene is refracted,
 * so text and icons remain sharp. The same component can be reused by cards, sheets, or previews.
 */
@Immutable
data class LiquidGlassStyle(
    val displacementDp: Float = 4f,
    val bevelDp: Float = 12f,
    val saturation: Float = 1.10f,
    val aberrationDp: Float = 1.15f,
    val chromaticSamples: Int = 2,
    val rimGain: Float = .060f,
    val shellAlpha: Float = .12f,
    val rimAlpha: Float = 1f
)

/** Quality controls whether the surface uses live AGSL refraction or a shared replay fallback. */
enum class LiquidGlassQuality { Refracted, Shared, Flat }

/** Stronger material grades keep the level setting visible without changing the public card API. */
internal fun liquidGlassStyle(level: ShortcutGlassLevel): LiquidGlassStyle = when (level) {
    ShortcutGlassLevel.Normal -> LiquidGlassStyle(
        // The default bend and bevel match the bottom bar's STRONG lens.
        displacementDp = 4f,
        bevelDp = 12f,
        saturation = 1.10f,
        aberrationDp = ShortcutGlassLevel.Normal.aberrationDp,
        chromaticSamples = ShortcutGlassLevel.Normal.chromaticSamples,
        rimGain = .060f,
        shellAlpha = .10f,
        rimAlpha = .84f
    )
    ShortcutGlassLevel.Obvious -> LiquidGlassStyle(
        displacementDp = 5f,
        bevelDp = 14f,
        saturation = 1.12f,
        aberrationDp = ShortcutGlassLevel.Obvious.aberrationDp,
        chromaticSamples = ShortcutGlassLevel.Obvious.chromaticSamples,
        rimGain = .075f,
        shellAlpha = .12f,
        rimAlpha = .92f
    )
    ShortcutGlassLevel.Extreme -> LiquidGlassStyle(
        displacementDp = 6f,
        bevelDp = 16f,
        saturation = 1.14f,
        aberrationDp = ShortcutGlassLevel.Extreme.aberrationDp,
        chromaticSamples = ShortcutGlassLevel.Extreme.chromaticSamples,
        rimGain = .090f,
        shellAlpha = .16f,
        rimAlpha = 1f
    )
}

/** Bottom-bar optical kernel with finite overscan and a low-cost flat-centre branch. */
internal val LIQUID_GLASS_LENS_SHADER = NAVIGATION_LENS_SHADER
    .replace("uniform float2 viewport;", "uniform float2 viewport;\nuniform float2 inputOrigin;\nuniform float2 inputViewport;")
    .replace("uniform float aberration;", "uniform float aberration;\nuniform float chromaticSamples;")
    .replace("float2 q = p - viewport * 0.5;", "float2 q = (p - inputOrigin) - viewport * 0.5;")
    .replace("float2 hi = viewport - float2(0.5);", "float2 hi = inputViewport - float2(0.5);")
    .replace(
        """        half4 cr = scene.eval(clamp(base + normal * ca, lo, hi));
        half4 cb = scene.eval(clamp(base - normal * ca, lo, hi));
        half4 c = half4(cr.r, cg.g, cb.b, cg.a);""",
        """        half4 c = cg;
        // Keep the flat centre cheap; colour separation is only paid for on the bevel.
        if (edge > 0.001 && aberration > 0.001) {
            c.r = scene.eval(clamp(base + normal * ca, lo, hi)).r;
            if (chromaticSamples > 1.5) {
                c.b = scene.eval(clamp(base - normal * ca, lo, hi)).b;
            }
        }"""
    )

private class LiquidSurfaceEffectCache {
    var key: List<Float> = emptyList()
    var effect: androidx.compose.ui.graphics.RenderEffect? = null
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier,
    shape: Shape,
    lens: NavigationLensState?,
    tokens: GlassTokens,
    style: LiquidGlassStyle = LiquidGlassStyle(),
    quality: LiquidGlassQuality = LiquidGlassQuality.Refracted,
    marginDp: Float = 10f,
    cornerRadiusDp: Float = 32f,
    refraction: () -> ShortcutRefraction = { ShortcutRefraction() },
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current.density
    val replay = lens != null && Build.VERSION.SDK_INT >= 31 && quality != LiquidGlassQuality.Flat
    val shader = remember(replay, quality) {
        if (replay && quality == LiquidGlassQuality.Refracted && Build.VERSION.SDK_INT >= 33)
            RuntimeShader(LIQUID_GLASS_LENS_SHADER) else null
    }
    val margin = (marginDp * density).roundToInt()
    val refracted = replay && shader != null
    val effectiveCorner = cornerRadiusDp.coerceAtLeast(0f)
    val effectCache = remember(shader) { LiquidSurfaceEffectCache() }
    val origin = remember { mutableStateOf(Offset.Zero) }
    val coordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(modifier.clip(shape)) {
        if (replay && lens != null) {
            val overscan = Modifier.layout { measurable, constraints ->
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val child = measurable.measure(Constraints.fixed(w + margin * 2, h + margin * 2))
                layout(w, h) { child.place(-margin, -margin) }
            }
            Box(
                Modifier.matchParentSize().then(overscan)
                    .onGloballyPositioned {
                        origin.value = it.positionInRoot()
                        coordinates.value = it
                    }
                    .graphicsLayer {
                        if (Build.VERSION.SDK_INT >= 33 && shader != null) {
                            val key = listOf(
                                size.width, size.height, density, style.displacementDp,
                                style.bevelDp, style.saturation, style.aberrationDp,
                                style.chromaticSamples.toFloat(), style.rimGain, effectiveCorner,
                                margin.toFloat()
                            )
                            if (effectCache.key != key) {
                                shader.setFloatUniform("viewport", size.width - 2 * margin, size.height - 2 * margin)
                                shader.setFloatUniform("inputOrigin", margin.toFloat(), margin.toFloat())
                                shader.setFloatUniform("inputViewport", size.width, size.height)
                                shader.setFloatUniform("density", density)
                                shader.setFloatUniform("displacement", style.displacementDp)
                                shader.setFloatUniform("bevel", style.bevelDp)
                                shader.setFloatUniform("saturation", style.saturation)
                                shader.setFloatUniform("aberration", style.aberrationDp)
                                shader.setFloatUniform("chromaticSamples", style.chromaticSamples.coerceIn(1, 2).toFloat())
                                shader.setFloatUniform("rimGain", style.rimGain)
                                shader.setFloatUniform("cornerRadius", effectiveCorner)
                                effectCache.effect = RenderEffect.createRuntimeShaderEffect(shader, "scene").asComposeRenderEffect()
                                effectCache.key = key
                            }
                            renderEffect = effectCache.effect
                        }
                    }
                    .drawWithContent {
                        refraction()
                        if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                            clipRect { replayCardScene(lens, coordinates.value, origin.value, anchored = true) }
                        } else {
                            drawRect(if (tokens.isDark) Color(0xFF151D2C) else Color(0xFFDCE5F6))
                        }
                    }
            )
        }

        Box(Modifier.matchParentSize().drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val fallback = if (tokens.isDark) Color(0xFF151D2C) else Color(0xFFDCE5F6)
            val sheen = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (refracted) .055f * style.rimAlpha else .15f * style.rimAlpha),
                .32f to Color.Transparent,
                .78f to Color.Transparent,
                1f to Color.White.copy(alpha = .035f * style.rimAlpha)
            )
            val edge = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color.White.copy(alpha = (if (refracted) .18f else if (tokens.isDark) .34f else .46f) * style.rimAlpha),
                .42f to tokens.rimBottom.copy(alpha = tokens.rimBottom.alpha * .35f),
                .78f to Color.White.copy(alpha = .035f),
                1f to tokens.rimBottom.copy(alpha = tokens.rimBottom.alpha * .5f)
            )
            onDrawBehind {
                if (!replay) drawRect(fallback)
                drawRect(Color.White.copy(alpha = if (refracted) style.shellAlpha * .55f else if (replay) style.shellAlpha else .10f))
                drawRect(sheen)
                drawOutline(outline, edge, style = androidx.compose.ui.graphics.drawscope.Stroke(.95.dp.toPx()))
            }
        })
        Box(Modifier.matchParentSize(), content = content)
    }
}
