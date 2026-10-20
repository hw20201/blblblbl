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
    const val blurLod = 1.5f
    val blurRadiusDp = 3f * 2f.pow(blurLod - 3f)
    fun resolve(widthDp: Float, heightDp: Float, density: Float): Resolved =
        ShortcutTunedParameters.resolve(widthDp, heightDp, density).copy(
            thicknessDp = ShortcutTunedParameters.lengthDp(68f),
            edgeWidthDp = ShortcutTunedParameters.lengthDp(12f),
            blurDp = blurRadiusDp, squircle = 2f, edgeClarity = .75f,
            ior = 2f, distortion = .100f, dispersion = 15.5f, fresnel = .64f,
            edgeStrength = 1f, saturation = .86f, tint = 0f, grain = 0f)
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
    uniform float lift;
    uniform float motionLift;
    uniform float2 touch;
    uniform float contact;
    uniform float contactMode;
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
        float contactRadius = clamp(min(viewport.x, viewport.y) / density * 0.42, 12.0, 64.0);
        float2 contactPoint = (touch * 0.5 + 0.5) * viewport / density;
        float2 contactDelta = dp - contactPoint;
        float contactMask = contactMode * contact * (1.0 - smoothstep(0.35, 1.0, length(contactDelta) / contactRadius));
        float thick = max(thickness * (1.0 + motionLift * 0.55 * (1.0 - contactMode)) * (1.0 - contactMask * 0.18), 0.001);
        float rim = 1.0 - smoothstep(0.0, max(edgeWidth, 0.01), depth);
        float fade = clamp(lift, 0.0, 1.0);
        fade = fade * fade * (3.0 - 2.0 * fade);
        float sigma = blurRadius * density * fade * (1.0 - clamp(edgeClarity, 0.0, 1.0) * rim) * (1.0 - contactMask);
        float ratio = clamp(1.0 - depth / thick, 0.0, 1.0);
        float thetaI = asin(clamp(ratio * ratio, 0.0, 1.0));
        float thetaT = asin(clamp(sin(thetaI) / max(ior, 1.001), 0.0, 1.0));
        float edgeFactor = depth < thick ? -tan(thetaT - thetaI) : 0.0;
        // LOD 1.5 in the HTML permits 2.5 + 4*1.5 = 8.5 CSS px, not 14.5dp.
        // Smooth saturation avoids the hard plateau in min(raw, cap).
        float maxDisplacement = 8.5 * 0.62105263;
        float rawDisplacement = edgeFactor * distortion * 80.0 * 0.62105263 * (1.0 + motionLift * 0.45 * (1.0 - contactMode));
        float dispDp = maxDisplacement * (1.0 - exp(-rawDisplacement / maxDisplacement));
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
        // Outward source gathering describes a shallow concave dent, not a raised lens.
        // The footprint is compact and never paints an invented glow or texture.
        float2 dentOffset = contactDelta / contactRadius * contactMask * 1.4 * density;
        float2 uv = p + (tiltOffset * mix(1.0, 0.12, contactMode) + dentOffset) * bend;
        off *= bend;
        float chroma = clamp(dispersion * 0.02, 0.0, 0.35);
        half4 base = sampleBlur(uv + off * (1.0 - 0.2 * chroma), sigma);
        // Interior has no dispersion offset: avoid two identical nine-tap gathers.
        if (dot(off, off) > 0.0001 && chroma > 0.001) {
            base.r = sampleBlur(uv + off * (1.0 - 0.3 * chroma), sigma).r;
            base.b = sampleBlur(uv + off * (1.0 - 0.1 * chroma), sigma).b;
        }
        half lum = dot(base.rgb, half3(0.2126, 0.7152, 0.0722));
        base.rgb = mix(half3(lum), base.rgb, mix(saturation, 1.0, contactMask * 0.75));
        base.rgb = mix(base.rgb, half3(base.a), tint * (1.0 - contactMask * 0.75));
        // Fresnel is a narrow, scene-coloured directional reflection, NOT a white
        // wash across the entire thickness. Flat input stays flat; dark stays dark.
        float fres = pow(rim, 3.0) * fresnel;
        float facing = clamp(0.5 - normal.y * 0.35 - normal.x * 0.15, 0.0, 1.0);
        if (fres > 0.001 && bend > 0.0) {
            half3 reflection = backdrop.eval(bounded(uv - off * 0.35)).rgb;
            base.rgb = mix(base.rgb, reflection, fres * facing * 0.18 * bend);
        }
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
            y = bounded(refraction.y, -1f), frostLift = bounded(refraction.frostLift), contact = bounded(refraction.contact))
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
        shader.setFloatUniform("lift", r.frostLift)
        shader.setFloatUniform("motionLift", r.lift)
        shader.setFloatUniform("touch", r.x, r.y)
        shader.setFloatUniform("contact", r.contact)
        shader.setFloatUniform("contactMode", if (r.contactFollow) 1f else 0f)
        effect = RenderEffect.createRuntimeShaderEffect(shader, "backdrop").asComposeRenderEffect()
        cache = next
        return effect
    }
}
