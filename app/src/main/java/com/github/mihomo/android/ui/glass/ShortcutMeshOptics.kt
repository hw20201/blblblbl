package com.github.mihomo.android.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/** Lift in [0, 1], touch direction in [-1, 1]; neutral touch is the card centre. */
data class ShortcutRefraction(val lift: Float = 0f, val x: Float = 0f, val y: Float = 0f,
    val frostLift: Float = lift, val contact: Float = 0f, val contactFollow: Boolean = false,
    /** Degrees: pitch = -graphicsLayer.rotationX; roll = graphicsLayer.rotationY. */
    val pitch: Float = 0f, val roll: Float = 0f, val slabFollow: Boolean = false)

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
    uniform float lift;
    uniform float2 touch;
    half4 main(float2 p) {
        float r = min(radius, min(viewport.x, viewport.y) * 0.5);
        float2 q = p - viewport * 0.5;
        float2 n = q - clamp(q, -viewport * 0.5 + r, viewport * 0.5 - r);
        float depth = r - length(n);
        // Widen only Hybrid. Baseline and the unsupported-device paths retain their optics.
        float bevelScale = mix(mix(1.0, 7.2 / 4.6, hybrid), 1.82, lift);
        float edge = 1.0 - smoothstep(0.0, 4.6 * density * bevelScale, depth);
        float faceDepth = depth / bevelScale;
        half4 c = backdrop.eval(p);
        // Same-pass chroma lift; no colour-filter layer or second blur.
        half gray = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
        c.rgb = clamp(mix(half3(gray), c.rgb, 1.0 + hybrid * mix(0.045, 0.025, dark)), 0.0, c.a);
        if (edge > 0.001) {
            float2 normal = n / max(length(n), 0.001);
            half4 unbent = c;
            // Hybrid bends only the curved 7.2dp bevel; keep the centre and foreground flat.
            if (hybrid > 0.5 || lift > 0.0) {
                // Touch creates a local, asymmetric lens displacement, gated to the curved bevel.
                float2 touchVector = (p - touch) / max(min(viewport.x, viewport.y) * 0.5, 1.0);
                // Keep a baseline response around the whole bevel. A hard 1-distance cutoff
                // made mid-card taps produce almost no press refraction, while corner taps did.
                float touchDistance = length(touchVector);
                float touchWeight = 1.0 - 0.38 * smoothstep(0.0, 1.35, touchDistance);
                float2 shift = float2(normal.x * 1.2 + touchVector.x * 1.5,
                    normal.y * 1.2 + touchVector.y * 2.2) * lift * density * touchWeight;
                c = backdrop.eval(clamp(p - normal * edge * edge * (2.1 * density + lift * 2.6 * density) - shift,
                    float2(0.5), viewport - float2(0.5)));
            }
            half4 inner = backdrop.eval(clamp(p - normal * 4.0 * density,
                float2(0.5), viewport - float2(0.5)));
            half light = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
            half3 relief = clamp((unbent.rgb - inner.rgb) * (0.24 + hybrid * 0.10), -0.025, 0.025);
            half facing = 0.65 - normal.y * 0.25 - normal.x * 0.12;
            // Scale both faces with the band, rather than adding a brighter/whiter outline.
            float outer = 1.0 - smoothstep(0.0, 1.35 * density, faceDepth);
            float innerFace = smoothstep(1.25 * density, 1.85 * density, faceDepth)
                * (1.0 - smoothstep(2.15 * density, 3.15 * density, faceDepth));
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
        val radius: Float, val dark: Boolean, val hybrid: Boolean, val refraction: ShortcutRefraction)
    private var parameters: Parameters? = null
    var effect: ComposeRenderEffect? = null
        private set
    var nativeEffect: RenderEffect? = null
        private set

    fun update(width: Float, height: Float, density: Float, radius: Float, dark: Boolean, hybrid: Boolean = false,
        refraction: ShortcutRefraction = ShortcutRefraction()) {
        val r = refraction.copy(lift = refraction.lift.coerceIn(0f, 1f), x = refraction.x.coerceIn(-1f, 1f), y = refraction.y.coerceIn(-1f, 1f))
        val next = Parameters(width, height, density, radius, dark, hybrid, r)
        if (next == parameters) return
        shader.setFloatUniform("viewport", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
        shader.setFloatUniform("density", density)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("dark", if (dark) 1f else 0f)
        shader.setFloatUniform("hybrid", if (hybrid) 1f else 0f)
        shader.setFloatUniform("lift", r.lift)
        shader.setFloatUniform("touch", (r.x * .5f + .5f) * width, (r.y * .5f + .5f) * height)
        // RenderEffect snapshots uniforms. Reuse for draw-only mesh motion; update only for
        // geometry, theme or finite touch motion. Source display-list changes remain live.
        nativeEffect = RenderEffect.createRuntimeShaderEffect(shader, "backdrop")
        effect = nativeEffect?.asComposeRenderEffect()
        parameters = next
    }
}
