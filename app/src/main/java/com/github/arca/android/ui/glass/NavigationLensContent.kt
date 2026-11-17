package com.github.arca.android.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

/** The exact rendered shell: measurement rounds its size, but placement retains its float centre. */
internal fun navigationLensContentBounds(
    frame: LensFrame,
    width: Float,
    height: Float,
    slotCount: Int,
    density: Float
): NavigationLensGlobalBounds? {
    if (width <= 0f || height <= 0f || slotCount <= 0) return null
    val animated = insetLensRect(frame.position, frame.stretch, frame.expansion,
        width / slotCount, width, height, density)
    val renderedWidth = animated.width.roundToInt().coerceAtLeast(1).toFloat()
    val renderedHeight = animated.height.roundToInt().coerceAtLeast(1).toFloat()
    return NavigationLensGlobalBounds(
        Rect(animated.center.x - renderedWidth / 2f, height / 2f - renderedHeight / 2f,
            animated.center.x + renderedWidth / 2f, height / 2f + renderedHeight / 2f),
        minOf(renderedWidth, renderedHeight) / 2f
    )
}

/** Small optical gain only inside the shell; never scales the semantic or touch bounds. */
internal fun navigationLensContentMagnification(expansion: Float): Float =
    1.04f + .07f * expansion.coerceIn(0f, 1f)

/**
 * One foreground input, one output pixel: outside the capsule the input is unchanged; inside it
 * the sampled alpha is refracted and tinted. In particular, the original glyph must not be drawn
 * beneath its displaced copy (which produces a grey/blue double edge on a transparent backdrop).
 * The rounded distance field is the same geometry as the existing navigation shell.
 */
internal const val NAVIGATION_LENS_CONTENT_SHADER = """
    uniform shader content;
    uniform float4 shell;
    uniform float radius;
    uniform float density;
    uniform float magnification;
    uniform float4 selectedTint;

    half4 main(float2 p) {
        float2 centre = (shell.xy + shell.zw) * 0.5;
        float2 halfSize = (shell.zw - shell.xy) * 0.5;
        float2 q = p - centre;
        float2 d = abs(q) - (halfSize - float2(radius));
        float2 outside = max(d, float2(0.0));
        float outsideLength = length(outside);
        float signedDistance = outsideLength + min(max(d.x, d.y), 0.0) - radius;
        // One physical pixel of antialiasing. The coverage is spatial, never a per-tab tint.
        float coverage = 1.0 - smoothstep(-0.5, 0.5, signedDistance);
        half4 original = content.eval(p);
        if (coverage <= 0.0) return original;

        float2 normal;
        if (outsideLength > 0.001) {
            normal = sign(q) * outside / outsideLength;
        } else if (d.x > d.y) {
            normal = float2(sign(q.x), 0.0);
        } else {
            normal = float2(0.0, sign(q.y));
        }
        float edge = 1.0 - smoothstep(0.0, 7.0 * density, max(-signedDistance, 0.0));
        // Keep text crisp: a shallow bevel bend plus 4–11% magnification, no blur or dispersion.
        float2 sampleAt = centre + q / magnification - normal * edge * edge * 0.45 * density;
        half alpha = content.eval(sampleAt).a * selectedTint.a;
        half4 covered = half4(selectedTint.rgb * alpha, alpha);
        return mix(original, covered, coverage);
    }
"""

private data class ContentEffectKey(
    val bounds: NavigationLensGlobalBounds,
    val magnification: Float,
    val density: Float,
    val tint: Color
)

/**
 * Draw monochrome navigation foreground through the moving capsule. Size this box to the same
 * track as [NavigationLens] and pass its [optics]. [content] supplies ordinary-colour glyphs/text;
 * only pixels currently covered by the shell take [selectedTint]. Content is composed once, so
 * clicks, accessibility and focus stay on the original stationary nodes. The shell material and
 * page backdrop remain on their existing renderer, independent of this small foreground layer.
 */
@Composable
fun NavigationLensContent(
    modifier: Modifier = Modifier,
    optics: NavigationLensOpticsState,
    slotCount: Int,
    selectedTint: Color,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current.density
    val source = rememberGraphicsLayer()
    val output = rememberGraphicsLayer()
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) RuntimeShader(NAVIGATION_LENS_CONTENT_SHADER) else null
    }
    // Native RenderEffect snapshots uniforms. Rebuild on optical geometry, never merely because
    // the recorded content invalidates. Record into a distinct source to avoid sampling ourselves.
    val cache = remember { arrayOfNulls<Any>(2) }
    val fallbackFilter = remember(selectedTint) { ColorFilter.tint(selectedTint) }
    Box(modifier.drawWithContent {
        val bounds = navigationLensContentBounds(optics.frame, size.width, size.height, slotCount, density)
        if (bounds == null || !drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
            drawContent()
            return@drawWithContent
        }
        source.record { this@drawWithContent.drawContent() }
        val magnification = navigationLensContentMagnification(optics.frame.expansion)
        if (Build.VERSION.SDK_INT >= 33 && shader != null) {
            val key = ContentEffectKey(bounds, magnification, density, selectedTint)
            if (cache[0] != key) {
                val rect = bounds.rect
                shader.setFloatUniform("shell", rect.left, rect.top, rect.right, rect.bottom)
                shader.setFloatUniform("radius", bounds.cornerRadiusPx)
                shader.setFloatUniform("density", density)
                shader.setFloatUniform("magnification", magnification)
                shader.setFloatUniform("selectedTint", selectedTint.red, selectedTint.green,
                    selectedTint.blue, selectedTint.alpha)
                cache[0] = key
                cache[1] = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            }
            output.renderEffect = cache[1] as androidx.compose.ui.graphics.RenderEffect
            output.record { drawLayer(source) }
            drawLayer(output)
        } else {
            // API 26–32 still has exact local tint/magnification, without claiming AGSL refraction.
            val mask = Path().apply {
                addRoundRect(RoundRect(bounds.rect, bounds.cornerRadiusPx, bounds.cornerRadiusPx))
            }
            clipPath(mask, ClipOp.Difference) { drawLayer(source) }
            output.renderEffect = null
            output.colorFilter = fallbackFilter
            output.record { drawLayer(source) }
            clipPath(mask) {
                scale(magnification, magnification, Offset(bounds.rect.center.x, bounds.rect.center.y)) {
                    drawLayer(output)
                }
            }
        }
    }, content = content)
}
