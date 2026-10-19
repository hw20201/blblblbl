package com.github.mihomo.android.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * The shared mesh is already a diffuse colour field: shortcut cards do not need another Gaussian
 * pass. One sampled colour supplies the fill and a restrained, hue-preserving bevel. Only the
 * narrow bevel reads a second nearby colour. No clock, bitmap capture or foreground is involved.
 * This is deliberately separate from the user-approved navigation lens.
 */
internal const val SHORTCUT_GLASS_SHADER = """
    uniform shader backdrop;
    uniform float2 viewport;
    uniform float density;
    uniform float radius;
    uniform float dark;
    half4 main(float2 p) {
        float r = min(radius, min(viewport.x, viewport.y) * 0.5);
        float2 q = p - viewport * 0.5;
        float2 n = q - clamp(q, -viewport * 0.5 + r, viewport * 0.5 - r);
        float depth = r - length(n);
        float edge = 1.0 - smoothstep(0.0, 2.6 * density, depth);
        half4 c = backdrop.eval(p);
        if (edge > 0.001) {
            float2 normal = n / max(length(n), 0.001);
            half4 inner = backdrop.eval(clamp(p - normal * 4.0 * density,
                float2(0.5), viewport - float2(0.5)));
            half light = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
            half3 relief = clamp((c.rgb - inner.rgb) * 0.30, -0.025, 0.025);
            // Gather local colour, not white. The upper face catches slightly more light.
            half facing = 0.65 - normal.y * 0.25;
            half gain = mix(0.052, 0.075, dark) * (0.35 + light * 0.65);
            half3 bounce = c.rgb * gain * facing;
            c.rgb = clamp(c.rgb + (bounce + relief) * edge, 0.0, c.a);
        }
        return c;
    }
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class ShortcutMeshOptics {
    private val shader = RuntimeShader(SHORTCUT_GLASS_SHADER)
    val effect: ComposeRenderEffect = RenderEffect.createRuntimeShaderEffect(shader, "backdrop")
        .asComposeRenderEffect()

    fun update(width: Float, height: Float, density: Float, radius: Float, dark: Boolean) {
        shader.setFloatUniform("viewport", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
        shader.setFloatUniform("density", density)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("dark", if (dark) 1f else 0f)
    }
}
