package com.github.mihomo.android.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/** Local-only A/B switches; not persisted settings and not a dependency/Compose upgrade. */
internal val LocalShortcutOpticalFollowupEnabled = staticCompositionLocalOf { true }
internal val LocalShortcutRefractionEnabled = staticCompositionLocalOf { true }
internal const val SHORTCUT_BACKDROP_OVERSCAN_DP = 24f

/** Latest user snapshot. Preserve the 1.0.26 constants for an honest old/new comparison. */
internal object ShortcutFollowupParameters {
    const val blurLod = 3.5f
    val blurRadiusDp = 3f * 2f.pow(blurLod - 3f)
    fun resolve(widthDp: Float, heightDp: Float, density: Float): Resolved =
        ShortcutTunedParameters.resolve(widthDp, heightDp, density).copy(
            thicknessDp = ShortcutTunedParameters.lengthDp(42f),
            edgeWidthDp = ShortcutTunedParameters.lengthDp(18.5f),
            blurDp = blurRadiusDp, squircle = 2f, edgeClarity = .70f,
            ior = 1.60f, distortion = .100f, dispersion = 15.5f, fresnel = .56f,
            edgeStrength = 1.20f, saturation = .82f, tint = .10f, grain = .010f)
}

// n=2 is a true circular corner; native round-rect clipping matches its SDF exactly.
internal val ShortcutFollowupCardShape = RoundedCornerShape(ShortcutTunedParameters.lengthDp(60f).dp)

/** Latest material snapshot; corrected sampling coordinates and physical-pixel grain.
 * The original 1.0.26 shader remains intact as a reproducible before reference.
 */
internal const val SHORTCUT_REFRACTIVE_SHADER = """
    uniform shader backdrop;
    uniform float2 viewport;
    uniform float2 inputOrigin;
    uniform float2 inputViewport;
    uniform float bend;
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
    float2 bounded(float2 p) { return clamp(p, float2(0.5), inputViewport - float2(0.5)); }
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
        float2 dp = (p - inputOrigin) / density;
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
        float2 uv = p + tiltOffset * bend;
        off *= bend;
        float chroma = clamp(dispersion * 0.02, 0.0, 0.35);
        half4 base = sampleBlur(uv + off * (1.0 - 0.2 * chroma), sigma);
        base.r = sampleBlur(uv + off * (1.0 - 0.3 * chroma), sigma).r;
        base.b = sampleBlur(uv + off * (1.0 - 0.1 * chroma), sigma).b;
        // Scene-dependent relief: amplify ONLY the difference from actual unbent input.
        // A flat source produces no invented optical contour or bright/neon stroke.
        if (rim > 0.001 && bend > 0.0) {
            half3 straight = backdrop.eval(bounded(p)).rgb;
            half3 relief = clamp(base.rgb - straight, -0.025, 0.025);
            base.rgb = clamp(base.rgb + relief * half(2.5 * rim), 0.0, base.a);
        }
        half lum = dot(base.rgb, half3(0.2126, 0.7152, 0.0722));
        base.rgb = mix(half3(lum), base.rgb, saturation);
        base.rgb = mix(base.rgb, half3(base.a), tint);
        float fres = pow(1.0 - clamp(depth / thick, 0.0, 1.0), 3.0) * fresnel;
        base.rgb = mix(base.rgb, half3(base.a), fres * 0.42);
        // Microscopic roughness uses physical pixels, not one coarse visible tile per dp.
        // At contact it vanishes; on lift the new 0.010 setting replaces old 0.052.
        float noise = fract(sin(dot(floor(p) + dark * float2(17.0, 31.0), float2(127.1, 311.7))) * 43758.5453) - 0.5;
        base.rgb = clamp(base.rgb + half(noise * grain * fade) * base.a, 0.0, base.a);
        return base;
    }
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class ShortcutRefractiveOptics {
    private val shader = RuntimeShader(SHORTCUT_REFRACTIVE_SHADER)
    private data class Cache(val width: Float, val height: Float, val density: Float,
        val margin: Float, val dark: Boolean, val bend: Boolean, val refraction: ShortcutRefraction)
    private var cache: Cache? = null
    var effect: ComposeRenderEffect? = null
        private set

    fun update(width: Float, height: Float, density: Float, marginPx: Float, dark: Boolean,
        refraction: ShortcutRefraction, bend: Boolean = true): ComposeRenderEffect? {
        val w = width.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
        val h = height.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
        val d = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val margin = marginPx.takeIf { it.isFinite() }?.coerceIn(0f, 64f * d) ?: 0f
        fun bounded(v: Float, min: Float = 0f) = if (v.isFinite()) v.coerceIn(min, 1f) else 0f
        val r = refraction.copy(lift = bounded(refraction.lift), x = bounded(refraction.x, -1f),
            y = bounded(refraction.y, -1f), frostLift = bounded(refraction.frostLift))
        val next = Cache(w, h, d, margin, dark, bend, r)
        if (next == cache) return effect
        val params = ShortcutFollowupParameters.resolve(w / d, h / d, d)
        shader.setFloatUniform("viewport", w, h)
        shader.setFloatUniform("inputOrigin", margin, margin)
        shader.setFloatUniform("inputViewport", w + margin * 2f, h + margin * 2f)
        shader.setFloatUniform("bend", if (bend) 1f else 0f)
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
        effect = RenderEffect.createRuntimeShaderEffect(shader, "backdrop").asComposeRenderEffect()
        cache = next
        return effect
    }
}
