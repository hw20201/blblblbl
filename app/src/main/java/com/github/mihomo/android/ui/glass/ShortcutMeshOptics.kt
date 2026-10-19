package com.github.mihomo.android.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Reuse the already-diffuse shared mesh: no Gaussian pass, capture or per-card clock.
 * A colour-gathering outer face and darker inset return retain the baseline glass depth.
 * Hybrid adds restrained chroma and bevel-only refraction in this same effect, not a second layer.
 * Independently implemented; no upstream library source is incorporated.
 */
internal const val SHORTCUT_GLASS_SHADER = """
    uniform shader backdrop;
    uniform float2 viewport;
    uniform float density;
    uniform float radius;
    uniform float dark;
    uniform float hybrid;
    half4 main(float2 p) {
        float r = min(radius, min(viewport.x, viewport.y) * 0.5);
        float2 q = p - viewport * 0.5;
        float2 n = q - clamp(q, -viewport * 0.5 + r, viewport * 0.5 - r);
        float depth = r - length(n);
        float edge = 1.0 - smoothstep(0.0, 4.6 * density, depth);
        half4 c = backdrop.eval(p);
        // Same-pass chroma lift; no colour-filter layer or second blur.
        half gray = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
        c.rgb = clamp(mix(half3(gray), c.rgb, 1.0 + hybrid * mix(0.045, 0.025, dark)), 0.0, c.a);
        if (edge > 0.001) {
            float2 normal = n / max(length(n), 0.001);
            half4 unbent = c;
            // Only the curved 4.6dp bevel bends. Foreground is an untouched sibling.
            if (hybrid > 0.5) {
                c = backdrop.eval(clamp(p - normal * edge * edge * 1.25 * density,
                    float2(0.5), viewport - float2(0.5)));
            }
            half4 inner = backdrop.eval(clamp(p - normal * 4.0 * density,
                float2(0.5), viewport - float2(0.5)));
            half light = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
            half3 relief = clamp((unbent.rgb - inner.rgb) * (0.24 + hybrid * 0.10), -0.025, 0.025);
            half facing = 0.65 - normal.y * 0.25 - normal.x * 0.12;
            float outer = 1.0 - smoothstep(0.0, 1.35 * density, depth);
            float innerFace = smoothstep(1.25 * density, 1.85 * density, depth)
                * (1.0 - smoothstep(2.15 * density, 3.15 * density, depth));
            // Local luminance limits the catch: never a fixed white/neon outline.
            half3 bounce = c.rgb * mix(0.60, 0.45, dark) * (1.0 - light) * facing;
            half3 shade = c.rgb * (0.045 + normal.y * 0.015);
            c.rgb = clamp(c.rgb + bounce * outer - shade * innerFace * (1.0 + hybrid * 0.35)
                + relief * edge, 0.0, c.a);
        }
        return c;
    }
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class ShortcutMeshOptics {
    private val shader = RuntimeShader(SHORTCUT_GLASS_SHADER)
    private data class Parameters(val width: Float, val height: Float, val density: Float,
        val radius: Float, val dark: Boolean, val hybrid: Boolean)
    private var parameters: Parameters? = null
    var effect: ComposeRenderEffect? = null
        private set

    fun update(width: Float, height: Float, density: Float, radius: Float, dark: Boolean, hybrid: Boolean = false) {
        val next = Parameters(width, height, density, radius, dark, hybrid)
        if (next == parameters) return
        shader.setFloatUniform("viewport", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
        shader.setFloatUniform("density", density)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("dark", if (dark) 1f else 0f)
        shader.setFloatUniform("hybrid", if (hybrid) 1f else 0f)
        // RenderEffect snapshots uniforms. Reuse for draw-only mesh motion; update only for
        // geometry, theme or A/B selection. Source display-list changes remain live.
        effect = RenderEffect.createRuntimeShaderEffect(shader, "backdrop").asComposeRenderEffect()
        parameters = next
    }
}
