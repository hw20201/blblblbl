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
import androidx.compose.ui.unit.dp

/** One GPU display list, shared by the scene and its navigation lens. No bitmap readback. */
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

// Independently implemented capsule lens, not a copy of the Flutter reference shader.
// A rounded-rectangle distance field confines bending to the curved bevel; the centre stays calm.
internal const val NAVIGATION_LENS_SHADER = """
    uniform shader scene;
    uniform float2 viewport;
    uniform float density;
    half4 main(float2 p) {
        float r = min(32.0 * density, min(viewport.x, viewport.y) * 0.5);
        float2 q = p - viewport * 0.5;
        float2 core = viewport * 0.5 - float2(r);
        float2 nearest = clamp(q, -core, core);
        float2 n = q - nearest;
        float len = length(n);
        float depth = r - len;
        float edge = 1.0 - smoothstep(0.0, 12.0 * density, depth);
        float2 normal = n / max(len, 0.001);
        float2 samplePoint = p - normal * edge * edge * 5.0 * density;
        half4 c = scene.eval(clamp(samplePoint, float2(0.5), viewport - float2(0.5)));
        half gray = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
        c.rgb = clamp(mix(half3(gray), c.rgb, 1.12), 0.0, c.a);
        return c;
    }
"""

/** Only this navigation surface samples the scene; cards keep their inexpensive existing material. */
@Composable
fun NavigationGlassSurface(
    modifier: Modifier = Modifier,
    lens: NavigationLensState? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = glassPalette
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(32.dp)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) RuntimeShader(NAVIGATION_LENS_SHADER) else null
    }
    val effect = remember(shader, density) {
        if (Build.VERSION.SDK_INT >= 31) {
            val blur = RenderEffect.createBlurEffect(12f * density, 12f * density, Shader.TileMode.CLAMP)
            if (Build.VERSION.SDK_INT >= 33 && shader != null) {
                RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "scene"), blur)
                    .asComposeRenderEffect()
            } else blur.asComposeRenderEffect()
        } else null
    }
    val fill = if (palette.isDark) Color(0xFF252830).copy(alpha = 0.48f)
        else Color(0xFFF4F4F4).copy(alpha = 0.46f)

    Box(modifier.shadow(8.dp, shape, clip = false,
        ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.09f))
        .clip(shape)) {
        if (lens != null && Build.VERSION.SDK_INT >= 31) {
            // This empty child alone receives the effect. Neither tint/rim nor icons/text are blurred.
            Box(Modifier.matchParentSize()
                .onGloballyPositioned { origin = it.positionInRoot() }
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= 33 && shader != null) {
                        if (size.width > 0f && size.height > 0f) {
                            shader.setFloatUniform("viewport", size.width, size.height)
                            shader.setFloatUniform("density", density)
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
            val radius = (minOf(32.dp.toPx(), size.height / 2f, size.width / 2f) - inset).coerceAtLeast(0f)
            val rim = Path().apply {
                addRoundRect(RoundRect(inset, inset, size.width - inset, size.height - inset, radius, radius))
            }
            val edge = if (palette.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
            val sheen = Brush.verticalGradient(listOf(
                Color.White.copy(alpha = if (palette.isDark) 0.26f else 0.78f),
                Color.White.copy(alpha = 0.035f),
                Color.White.copy(alpha = if (palette.isDark) 0.12f else 0.40f)
            ))
            onDrawBehind {
                drawRect(fill)
                drawPath(rim, edge, style = Stroke(1.dp.toPx()))
                drawPath(rim, sheen, style = Stroke(0.65.dp.toPx()))
            }
        })
        content()
    }
}
