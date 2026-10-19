package com.github.mihomo.android.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One GPU display list, shared by the scene and every lens that samples it. No bitmap readback. */
@Stable
class NavigationLensState internal constructor(internal val scene: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberNavigationLens(): NavigationLensState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { NavigationLensState(layer) }
}

/** Record only the scene sibling below the dock: never include the dock in its own input. */
fun Modifier.recordNavigationScene(state: NavigationLensState): Modifier {
    if (Build.VERSION.SDK_INT < 31) return this
    return onGloballyPositioned { state.origin = it.positionInRoot() }
        .drawWithContent {
            if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                state.scene.record { this@drawWithContent.drawContent() }
                drawLayer(state.scene)
            } else {
                drawContent()
            }
        }
}

/**
 * How far the glass bends what is behind it.
 *
 * Note the relationship between [displacementDp] and [aberrationDp]: strong bending with no
 * dispersion looks like a warped photograph, while mild bending *with* dispersion is what the eye
 * reads as thick glass. So the weaker presets keep a relatively larger share of aberration.
 */
enum class LensStrength(
    internal val displacementDp: Float,
    internal val bevelDp: Float,
    internal val saturation: Float,
    internal val blurDp: Float,
    internal val aberrationDp: Float,
    internal val rimGain: Float
) {
    /** Barely there: a hint of thickness, no visible warping of text. */
    SUBTLE(
        displacementDp = 1.2f, bevelDp = 7.0f, saturation = 1.02f,
        blurDp = 7f, aberrationDp = 0.55f, rimGain = 0.030f
    ),

    /** Default. Reads as glass against cards without smearing body text. */
    STANDARD(
        displacementDp = 2.2f, bevelDp = 9.0f, saturation = 1.05f,
        blurDp = 9f, aberrationDp = 0.80f, rimGain = 0.045f
    ),

    /** For small decorative surfaces only; body text behind it starts to swim. */
    STRONG(
        displacementDp = 3.6f, bevelDp = 12.0f, saturation = 1.10f,
        blurDp = 11f, aberrationDp = 1.10f, rimGain = 0.060f
    )
}

/**
 * Independently implemented capsule lens.
 *
 * A rounded-rectangle distance field confines all bending to the curved bevel, so the centre of the
 * surface stays optically flat — text passing under the middle of the dock is only blurred, never
 * smeared. Three things make it read as glass rather than frosted plastic:
 *
 *  1. **Dispersion** — the red and blue channels are sampled at slightly different offsets along
 *     the edge normal. Real glass has a different refractive index per wavelength; without this the
 *     surface looks like warped cellophane.
 *  2. **Rim light gathering** — `rim` brightens the last few dp before the edge, which is what a
 *     bevelled edge does to light coming from behind.
 *  3. **Confinement** — `edge*edge` keeps displacement out of the flat centre; a linear falloff
 *     makes the whole surface look bulged.
 */
internal const val NAVIGATION_LENS_SHADER = """
    uniform shader scene;
    uniform float2 viewport;
    uniform float density;
    uniform float displacement;
    uniform float bevel;
    uniform float saturation;
    uniform float aberration;
    uniform float rimGain;
    uniform float cornerRadius;

    half4 main(float2 p) {
        float r = min(cornerRadius * density, min(viewport.x, viewport.y) * 0.5);
        float2 q = p - viewport * 0.5;
        float2 core = viewport * 0.5 - float2(r);
        float2 nearest = clamp(q, -core, core);
        float2 n = q - nearest;
        float len = length(n);
        float depth = r - len;

        // 1.0 at the very edge, 0.0 once we are a bevel-width inside the surface.
        float edge = 1.0 - smoothstep(0.0, bevel * density, depth);
        float2 normal = n / max(len, 0.001);
        float2 lo = float2(0.5);
        float2 hi = viewport - float2(0.5);

        // Displacement is quadratic in `edge`, so it vanishes before the flat centre.
        float bend = edge * edge * displacement * density;
        float2 base = clamp(p - normal * bend, lo, hi);

        // Dispersion: split the channels along the same normal.
        float ca = edge * aberration * density;
        half4 cg = scene.eval(base);
        half4 cr = scene.eval(clamp(base + normal * ca, lo, hi));
        half4 cb = scene.eval(clamp(base - normal * ca, lo, hi));
        half4 c = half4(cr.r, cg.g, cb.b, cg.a);

        // Slight desaturation of the blurred backdrop keeps white type legible.
        half gray = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
        c.rgb = clamp(mix(half3(gray), c.rgb, saturation), 0.0, c.a);

        // Light gathering along the bevelled rim.
        c.rgb = clamp(c.rgb + half3(edge * edge * rimGain), 0.0, c.a);
        return c;
    }
"""

/**
 * A floating surface that samples the recorded scene through the lens.
 *
 * Only this surface pays for the shader; content cards keep the inexpensive static material. Pass
 * [shape] and [cornerRadius] to reuse the lens on something other than a capsule, and [strength] to
 * dial it down where the backdrop is busy.
 */
@Composable
fun NavigationGlassSurface(
    modifier: Modifier = Modifier,
    lens: NavigationLensState? = null,
    shape: Shape = RoundedCornerShape(32.dp),
    cornerRadius: Dp = 32.dp,
    strength: LensStrength = LensStrength.STANDARD,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = glassPalette
    val density = LocalDensity.current.density
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) RuntimeShader(NAVIGATION_LENS_SHADER) else null
    }
    val effect = remember(shader, density, strength) {
        if (Build.VERSION.SDK_INT >= 31) {
            val blur = RenderEffect.createBlurEffect(
                strength.blurDp * density,
                strength.blurDp * density,
                Shader.TileMode.CLAMP
            )
            if (Build.VERSION.SDK_INT >= 33 && shader != null) {
                RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "scene"), blur)
                    .asComposeRenderEffect()
            } else blur.asComposeRenderEffect()
        } else null
    }
    // A faint palette tint over the sampled backdrop; the material is carried by the rim, not by an
    // opaque fill, so this stays low enough that the scene still shows through.
    val fill = if (palette.isDark) Color(0xFF252830).copy(alpha = 0.34f)
        else Color(0xFFF4F4F4).copy(alpha = 0.30f)

    Box(
        modifier.shadow(
            8.dp, shape, clip = false,
            ambientColor = Color.Black.copy(alpha = 0.05f),
            spotColor = Color.Black.copy(alpha = 0.09f)
        ).clip(shape)
    ) {
        if (lens != null && Build.VERSION.SDK_INT >= 31) {
            // This empty child alone receives the effect. Neither tint/rim nor icons/text are blurred.
            Box(Modifier.matchParentSize()
                .onGloballyPositioned { origin = it.positionInRoot() }
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= 33 && shader != null) {
                        if (size.width > 0f && size.height > 0f) {
                            shader.setFloatUniform("viewport", size.width, size.height)
                            shader.setFloatUniform("density", density)
                            shader.setFloatUniform("displacement", strength.displacementDp)
                            shader.setFloatUniform("bevel", strength.bevelDp)
                            shader.setFloatUniform("saturation", strength.saturation)
                            shader.setFloatUniform("aberration", strength.aberrationDp)
                            shader.setFloatUniform("rimGain", strength.rimGain)
                            shader.setFloatUniform("cornerRadius", cornerRadius.value)
                        }
                    }
                    renderEffect = effect
                }
                .drawWithContent {
                    if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                        val offset = lens.origin - origin
                        translate(offset.x, offset.y) { drawLayer(lens.scene) }
                    }
                })
        }
        Box(Modifier.matchParentSize().drawWithCache {
            val inset = 0.55.dp.toPx()
            val radius = (minOf(cornerRadius.toPx(), size.height / 2f, size.width / 2f) - inset)
                .coerceAtLeast(0f)
            val rim = Path().apply {
                addRoundRect(RoundRect(inset, inset, size.width - inset, size.height - inset, radius, radius))
            }
            // Two-tone rim: bright where light lands on the top edge, cool bounce along the bottom.
            val rimTop = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (palette.isDark) 0.55f else 0.92f),
                0.45f to Color.White.copy(alpha = 0.10f),
                1f to palette.rimBottom.copy(alpha = if (palette.isDark) 0.18f else 0.30f)
            )
            // Interior sheen: a soft top-down falloff, the "thickness" of the slab.
            val sheen = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (palette.isDark) 0.10f else 0.26f),
                0.35f to Color.Transparent,
                0.85f to Color.Transparent,
                1f to Color.White.copy(alpha = if (palette.isDark) 0.03f else 0.08f)
            )
            onDrawBehind {
                drawRect(fill)
                drawRect(sheen)
                drawPath(rim, rimTop, style = Stroke(0.9.dp.toPx()))
            }
        })
        content()
    }
}
