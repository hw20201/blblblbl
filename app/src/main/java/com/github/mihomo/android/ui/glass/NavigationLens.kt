package com.github.mihomo.android.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
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
    /** Source-patch metadata only; the navigation renderer never creates a blur effect. */
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
        displacementDp = 4.0f, bevelDp = 12.0f, saturation = 1.10f,
        blurDp = 11f, aberrationDp = 1.15f, rimGain = 0.060f
    )
}

/**
 * Independently implemented capsule lens.
 *
 * A rounded-rectangle distance field confines all bending to the curved bevel, so the centre of the
 * surface stays optically flat — text passing under the middle of the dock is neither blurred nor
 * smeared. This adaptation removes Gaussian blur entirely. The optical cues are:
 *
 *  1. **Dispersion** — the red and blue channels are sampled at slightly different offsets along
 *     the edge normal. Real glass has a different refractive index per wavelength; without this the
 *     surface looks like warped cellophane.
 *  2. **Rim light gathering** — `rim` brightens the last few dp before the edge, which is what a
 *     bevelled edge does to light coming from behind.
 *  3. **Confinement** — `edge*edge` keeps displacement out of the flat centre; a linear falloff
 *     makes the whole surface look bulged.
 *  4. **Content response** — local colour and contrast modulate rim lighting as contours pass
 *     beneath it, instead of hiding them behind a fixed white stroke.
 */
// Keep the supplied bevel-only refraction; add scene-responsive edge light, never global blur/scale.
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

        // Restrained saturation of the sampled backdrop; no Gaussian filtering.
        half gray = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
        c.rgb = clamp(mix(half3(gray), c.rgb, saturation), 0.0, c.a);

        // Content-aware rim, not a fixed white outline. One extra nearby sample is
        // needed only in the bevel; the flat centre does not pay for edge lighting.
        if (edge > 0.001) {
            half4 inner = scene.eval(clamp(base - normal * 2.0 * density, lo, hi));
            half localLight = dot(cg.rgb, half3(0.2126, 0.7152, 0.0722));
            // A passing card/text contour can darken one side and brighten the other.
            // Bound this relief so text behind the edge does not turn into a halo.
            half3 contour = clamp((cg.rgb - inner.rgb) * 0.22, -0.055, 0.055);
            half3 bounce = mix(half3(1.0), cg.rgb, 0.65);
            half3 gathered = bounce * rimGain * (0.35 + 0.65 * localLight);
            c.rgb = clamp(c.rgb + (gathered + contour) * edge * edge, 0.0, c.a);
        }
        return c;
    }
"""

/** The shell expansion strengthens edge refraction; the scene is never globally scaled. */
internal fun lensRefractionScale(expansion: Float): Float = 1f + 0.2f * expansion.coerceIn(0f, 1f)

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
    expansionProgress: () -> Float = { 0f },
    refractionAmount: () -> Float = { 1f },
    content: @Composable BoxScope.() -> Unit
) {
    val palette = glassPalette
    val density = LocalDensity.current.density
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) RuntimeShader(NAVIGATION_LENS_SHADER) else null
    }
    // Shader-only refraction on API 33+. API 31/32 replay a clear translucent scene;
    // no Gaussian blur or global scene scaling is substituted for unsupported AGSL.
    // A faint palette tint over the sampled backdrop; the material is carried by the rim, not by an
    // opaque fill, so this stays low enough that the scene still shows through.
    val responsiveEdge = lens != null && Build.VERSION.SDK_INT >= 33
    val fill = Color.White.copy(alpha = if (responsiveEdge) 0.08f
        else if (palette.isDark) 0.10f else 0.18f)

    Box(
        modifier.graphicsLayer {
            this.shape = shape
            clip = false
            shadowElevation = (8f + 6f * expansionProgress().coerceIn(0f, 1f)) * density
            ambientShadowColor = Color.Black.copy(alpha = 0.05f)
            spotShadowColor = Color.Black.copy(alpha = 0.09f)
        }.clip(shape)
    ) {
        if (lens != null && Build.VERSION.SDK_INT >= 31) {
            // Only this scene child receives refraction. Tint/rim and stationary icons stay untouched.
            Box(Modifier.matchParentSize()
                .onGloballyPositioned { origin = it.positionInRoot() }
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= 33 && shader != null) {
                        if (size.width > 0f && size.height > 0f) {
                            shader.setFloatUniform("viewport", size.width, size.height)
                            shader.setFloatUniform("density", density)
                            val opticalScale = lensRefractionScale(expansionProgress()) * refractionAmount().coerceIn(0f, 1f)
                            shader.setFloatUniform("displacement", strength.displacementDp * opticalScale)
                            shader.setFloatUniform("bevel", strength.bevelDp)
                            shader.setFloatUniform("saturation", strength.saturation)
                            shader.setFloatUniform("aberration", strength.aberrationDp * opticalScale)
                            shader.setFloatUniform("rimGain", strength.rimGain)
                            shader.setFloatUniform("cornerRadius", cornerRadius.value)
                            // RenderEffect snapshots shader uniforms: create it AFTER setting them.
                            // Only this small lens layer invalidates, not the dock's icons or labels.
                            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "scene")
                                .asComposeRenderEffect()
                        }
                    }
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
            // On AGSL hardware the scene supplies the edge light. Keep only a faint
            // shell cue here, otherwise an opaque white stroke hides all contour response.
            val rimAttenuation = if (responsiveEdge) 0.18f else 1f
            val rimTop = Brush.verticalGradient(
                0f to Color.White.copy(alpha = (if (palette.isDark) 0.55f else 0.92f) * rimAttenuation),
                0.45f to Color.White.copy(alpha = 0.10f * rimAttenuation),
                1f to palette.rimBottom.copy(alpha = (if (palette.isDark) 0.18f else 0.30f) * rimAttenuation)
            )
            // Interior sheen: a soft top-down falloff, the "thickness" of the slab.
            val sheen = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (responsiveEdge) 0.055f
                    else if (palette.isDark) 0.10f else 0.26f),
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
