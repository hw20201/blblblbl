package com.github.mihomo.android.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Fixed lab snapshot. Lengths scale from the 190 CSS-px lab to the 118dp shortcut.
 * Browser mip LOD 3 is NOT 3 Android pixels: 3dp sigma is a deliberately calibrated
 * finite-kernel approximation, not a pixel-identical browser mipmap implementation.
 */
internal object ShortcutTunedParameters {
    const val referenceScale: Float = 118f / 190f
    const val blurRadiusDp: Float = 3f
    const val maxBlurDp: Float = blurRadiusDp
    const val squircle: Float = 2.4f
    fun lengthDp(labCssPx: Float): Float =
        (if (labCssPx.isFinite()) labCssPx.coerceAtLeast(0f) else 0f) * referenceScale

    fun resolve(widthDp: Float, heightDp: Float, density: Float): Resolved {
        val d = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val w = widthDp.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        val h = heightDp.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        return Resolved(max(w, 1f) * d, max(h, 1f) * d, d,
            min(lengthDp(60f), min(w, h) * .5f), lengthDp(54f), lengthDp(32f), blurRadiusDp,
            squircle, .55f, 2f, .1f, 14f, .22f, .8f, 1.04f, .04f, .052f,
            lengthDp(2f), 1.004f, 9f, lengthDp(12f), .78f)
    }
}

internal data class Resolved(
    val widthPx: Float, val heightPx: Float, val density: Float,
    val radiusDp: Float, val thicknessDp: Float, val edgeWidthDp: Float, val blurDp: Float,
    val squircle: Float, val edgeClarity: Float, val ior: Float, val distortion: Float,
    val dispersion: Float, val fresnel: Float, val edgeStrength: Float, val saturation: Float,
    val tint: Float, val grain: Float, val pressLiftDp: Float, val pressScale: Float,
    val tiltDegrees: Float, val shadowDp: Float, val damping: Float,
) {
    val radiusPx get() = radiusDp * density
    val thicknessPx get() = thicknessDp * density
    val edgeWidthPx get() = edgeWidthDp * density
}

/** Exact same Lp contour as the Compose clip. Stable normalization avoids powers of DPR. */
internal fun shortcutTunedSdfDp(xDp: Float, yDp: Float, widthDp: Float, heightDp: Float, radiusDp: Float, exponent: Float): Float {
    val w = widthDp.takeIf { it.isFinite() && it > 0f } ?: 1f
    val h = heightDp.takeIf { it.isFinite() && it > 0f } ?: 1f
    val r = (radiusDp.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, min(w, h) * .5f)
    val n = (exponent.takeIf { it.isFinite() } ?: 2f).coerceIn(2f, 7f)
    val dx = abs(xDp - w * .5f) - w * .5f + r
    val dy = abs(yDp - h * .5f) - h * .5f + r
    val cx = max(dx, 0f); val cy = max(dy, 0f); val m = max(cx, cy)
    val norm = if (m > 0f) m * ((cx / m).coerceAtLeast(1e-4f).pow(n) + (cy / m).coerceAtLeast(1e-4f).pow(n)).pow(1f / n) else 0f
    return min(max(dx, dy), 0f) + norm - r
}

internal fun shortcutTunedNormalDp(xDp: Float, yDp: Float, widthDp: Float, heightDp: Float, radiusDp: Float, exponent: Float): Pair<Float, Float> {
    val e = .7f * ShortcutTunedParameters.referenceScale
    val dx = shortcutTunedSdfDp(xDp + e, yDp, widthDp, heightDp, radiusDp, exponent) - shortcutTunedSdfDp(xDp - e, yDp, widthDp, heightDp, radiusDp, exponent)
    val dy = shortcutTunedSdfDp(xDp, yDp + e, widthDp, heightDp, radiusDp, exponent) - shortcutTunedSdfDp(xDp, yDp - e, widthDp, heightDp, radiusDp, exponent)
    val l = sqrt(dx * dx + dy * dy)
    return if (l > 1e-5f) (dx / l) to (dy / l) else 0f to 0f
}

/** Single existing scene input; at most 27 scene evaluations, only 3 at clear contact.
 * Nine normalized 2D Gaussian-quadrature taps per colour sample, not two unnormalized
 * separable sums. This is bounded local filtering, not a bitmap capture or new clock.
 */
internal const val SHORTCUT_TUNED_SHADER = """
    uniform shader backdrop;
    uniform float2 viewport;
    uniform float density;
    uniform float radius;
    uniform float exponent;
    uniform float thickness;
    uniform float edgeWidth;
    uniform float blurRadius;
    uniform float edgeClarity;
    uniform float ior;
    uniform float distortion;
    uniform float dispersion;
    uniform float fresnel;
    uniform float edgeStrength;
    uniform float saturation;
    uniform float tint;
    uniform float grain;
    uniform float dark;
    uniform float lift;
    uniform float motionLift;
    uniform float2 touch;
    float2 bounded(float2 p) { return clamp(p, float2(0.5), viewport - float2(0.5)); }
    half4 sampleBlur(float2 p, float sigma) {
        if (sigma < 0.01) return backdrop.eval(bounded(p));
        float s = sigma * 1.41421356;
        half4 c = backdrop.eval(bounded(p)) * 0.25;
        c += (backdrop.eval(bounded(p + float2(s, 0.0))) + backdrop.eval(bounded(p - float2(s, 0.0)))
            + backdrop.eval(bounded(p + float2(0.0, s))) + backdrop.eval(bounded(p - float2(0.0, s)))) * 0.125;
        c += (backdrop.eval(bounded(p + float2(s, s))) + backdrop.eval(bounded(p + float2(-s, s)))
            + backdrop.eval(bounded(p + float2(s, -s))) + backdrop.eval(bounded(p - float2(s, s)))) * 0.0625;
        return c;
    }
    float sdf(float2 p) {
        float2 hs = viewport / density * 0.5;
        float r = min(radius, min(hs.x, hs.y));
        float2 d = abs(p - hs) - hs + float2(r);
        float2 corner = max(d, float2(0.0));
        float m = max(corner.x, corner.y);
        float norm = 0.0;
        if (m > 0.0) {
            float2 u = max(corner / m, float2(0.0001));
            norm = m * pow(pow(u.x, exponent) + pow(u.y, exponent), 1.0 / exponent);
        }
        return min(max(d.x, d.y), 0.0) + norm - r;
    }
    float2 sdfNormal(float2 dp) {
        float e = 0.43473684;
        float2 g = float2(sdf(dp + float2(e, 0.0)) - sdf(dp - float2(e, 0.0)),
            sdf(dp + float2(0.0, e)) - sdf(dp - float2(0.0, e)));
        return g / max(length(g), 0.00001);
    }
    half4 main(float2 p) {
        float2 dp = p / density;
        float depth = max(-sdf(dp), 0.0);
        float thick = max(thickness * (1.0 + motionLift * 0.55), 0.001);
        float rim = 1.0 - smoothstep(0.0, max(edgeWidth, 0.01), depth);
        float fade = clamp(lift, 0.0, 1.0);
        fade = fade * fade * (3.0 - 2.0 * fade);
        float sigma = blurRadius * density * fade * (1.0 - clamp(edgeClarity, 0.0, 1.0) * rim);
        float ratio = clamp(1.0 - depth / thick, 0.0, 1.0);
        float thetaI = asin(clamp(ratio * ratio, 0.0, 1.0));
        float thetaT = asin(clamp(sin(thetaI) / max(ior, 1.001), 0.0, 1.0));
        float edgeFactor = depth < thick ? -tan(thetaT - thetaI) : 0.0;
        float dispDp = min(edgeFactor * distortion * 80.0 * 0.62105263 * (1.0 + motionLift * 0.45), 14.5 * 0.62105263);
        float rimLimit = min(edgeWidth * 0.6, 18.0 * 0.62105263);
        float rimRaw = edgeStrength * distortion * 80.0 * 0.62105263 * (1.0 - 1.0 / max(ior, 1.001)) * 2.0;
        dispDp += rimLimit * (1.0 - exp(-rimRaw / max(rimLimit, 0.001))) * rim;
        float2 normal = sdfNormal(dp);
        // Android/Compose and this backdrop both use downward +Y. No second Y inversion.
        float tx = -touch.y * motionLift * 0.15707963;
        float ty = touch.x * motionLift * 0.15707963;
        float3 surface = normalize(float3(sin(ty), -sin(tx) * cos(ty), cos(tx) * cos(ty)));
        float3 transmitted = refract(float3(0.0, 0.0, -1.0), surface, 1.0 / max(ior, 1.001));
        float2 tiltOffset = transmitted.xy / max(-transmitted.z, 0.15) * (thick + distortion * 80.0 * 0.62105263) * density;
        float2 off = -normal * dispDp * density;
        float2 uv = p + tiltOffset;
        float chroma = clamp(dispersion * 0.02, 0.0, 0.35);
        half4 base = sampleBlur(uv + off * (1.0 - 0.2 * chroma), sigma);
        base.r = sampleBlur(uv + off * (1.0 - 0.3 * chroma), sigma).r;
        base.b = sampleBlur(uv + off * (1.0 - 0.1 * chroma), sigma).b;
        half lum = dot(base.rgb, half3(0.2126, 0.7152, 0.0722));
        base.rgb = mix(half3(lum), base.rgb, saturation);
        base.rgb = mix(base.rgb, half3(base.a), tint);
        float fres = pow(1.0 - clamp(depth / thick, 0.0, 1.0), 3.0) * fresnel;
        base.rgb = mix(base.rgb, half3(base.a), fres * 0.42);
        // Grain is deterministic in dp, so density does not change its physical grain size.
        float noise = fract(sin(dot(floor(dp) + dark * float2(17.0, 31.0), float2(127.1, 311.7))) * 43758.5453) - 0.5;
        base.rgb = clamp(base.rgb + half(noise * grain) * base.a, 0.0, base.a);
        return base;
    }
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class ShortcutTunedOptics {
    private val shader = RuntimeShader(SHORTCUT_TUNED_SHADER)
    private data class Cache(val width: Float, val height: Float, val density: Float, val dark: Boolean, val refraction: ShortcutRefraction)
    private var cache: Cache? = null
    var nativeEffect: RenderEffect? = null
        private set
    var effect: ComposeRenderEffect? = null
        private set

    fun update(width: Float, height: Float, density: Float, dark: Boolean,
        refraction: ShortcutRefraction = ShortcutRefraction()): ComposeRenderEffect? {
        val w = width.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
        val h = height.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
        val d = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        fun bounded(v: Float, min: Float = 0f) = if (v.isFinite()) v.coerceIn(min, 1f) else 0f
        val r = refraction.copy(lift = bounded(refraction.lift), x = bounded(refraction.x, -1f),
            y = bounded(refraction.y, -1f), frostLift = bounded(refraction.frostLift))
        val next = Cache(w, h, d, dark, r)
        if (next == cache) return effect
        val params = ShortcutTunedParameters.resolve(w / d, h / d, d)
        shader.setFloatUniform("viewport", w, h)
        shader.setFloatUniform("density", d)
        shader.setFloatUniform("radius", params.radiusDp)
        shader.setFloatUniform("exponent", params.squircle)
        shader.setFloatUniform("thickness", params.thicknessDp)
        shader.setFloatUniform("edgeWidth", params.edgeWidthDp)
        shader.setFloatUniform("blurRadius", params.blurDp)
        shader.setFloatUniform("edgeClarity", params.edgeClarity)
        shader.setFloatUniform("ior", params.ior)
        shader.setFloatUniform("distortion", params.distortion)
        shader.setFloatUniform("dispersion", params.dispersion)
        shader.setFloatUniform("fresnel", params.fresnel)
        shader.setFloatUniform("edgeStrength", params.edgeStrength)
        shader.setFloatUniform("saturation", params.saturation)
        shader.setFloatUniform("tint", params.tint)
        shader.setFloatUniform("grain", params.grain)
        shader.setFloatUniform("dark", if (dark) 1f else 0f)
        shader.setFloatUniform("lift", r.frostLift)
        shader.setFloatUniform("motionLift", r.lift)
        shader.setFloatUniform("touch", r.x, r.y)
        nativeEffect = RenderEffect.createRuntimeShaderEffect(shader, "backdrop")
        effect = nativeEffect?.asComposeRenderEffect()
        cache = next
        return effect
    }
}
