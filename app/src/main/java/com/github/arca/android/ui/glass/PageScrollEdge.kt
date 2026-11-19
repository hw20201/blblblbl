package com.github.arca.android.ui.glass

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur

private val PageEdgeTransition = 28.dp

/**
 * A header-sized Glasmio effect over the scroll body's existing display list. The title and
 * controls are siblings drawn afterwards, so the header never captures itself. No additional
 * source recorder, full-screen offscreen layer or per-scroll composition is required.
 */
@Composable
internal fun PageScrollEdge(
    scene: NavigationLensState,
    headerHeight: Dp,
    modifier: Modifier = Modifier,
    testTag: String,
    scrollOffsetPx: () -> Int
) {
    if (!GlassDefaults.enabled || headerHeight <= 0.dp) return
    val latestOffset by rememberUpdatedState(scrollOffsetPx)
    val scrolled by remember { derivedStateOf { latestOffset() > 0 } }
    if (!scrolled) return

    val palette = LocalGlassPalette.current
    val backdrop = remember(scene) { PageSceneBackdrop(scene) }
    val useGlasmio = Build.VERSION.SDK_INT >= 33 && LocalGlassSettings.current.glassPreset.usesGlasmioFrosted()
    val edge = modifier.fillMaxWidth().height(headerHeight + PageEdgeTransition)
        .clipToBounds().testTag(testTag)
    if (useGlasmio) {
        Box(
            edge.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val mask = Brush.verticalGradient(
                        0f to Color.White,
                        0.42f to Color.White,
                        1f to Color.Transparent,
                        endY = size.height.coerceAtLeast(1f)
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(mask, blendMode = BlendMode.DstIn)
                    }
                }
                // Native fixed-radius filtering avoids two variable-radius AGSL loops per pixel.
                // The bounded alpha mask above supplies the gradual scroll-edge transition.
                .drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = { blur(10.dp.toPx()) }
                )
        )
    } else {
        // API 26–32 and legacy materials retain the app's palette-based self-drawn frost.
        // No unsupported RuntimeShader, bitmap readback or software blur is introduced.
        Box(edge.drawWithCache {
            val frost = Brush.verticalGradient(
                0f to palette.base.copy(alpha = 0.88f),
                0.42f to palette.base.copy(alpha = 0.72f),
                1f to Color.Transparent,
                endY = size.height.coerceAtLeast(1f)
            )
            onDrawBehind { drawRect(frost) }
        })
    }
}

/** Replays the existing title source with Glasmio's source-change and coordinate contracts. */
private class PageSceneBackdrop(private val scene: NavigationLensState) : Backdrop {
    override val isCoordinatesDependent: Boolean = true
    private val sourceToConsumer = Matrix()

    override fun observeSourceChanges(observer: () -> Unit): () -> Unit = scene.observeSourceChanges(observer)

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val consumer = coordinates?.takeIf { it.isAttached } ?: return
        val source = scene.sourceCoordinates?.takeIf { it.isAttached } ?: return
        // Main-page source and header always share a Compose root. The coordinates already
        // contain pager translation, overscroll and consumer graphics-layer transforms.
        if (source.findRootCoordinates() !== consumer.findRootCoordinates()) return
        sourceToConsumer.reset()
        try {
            consumer.transformFrom(source, sourceToConsumer)
        } catch (_: IllegalStateException) {
            return
        }
        if (!sourceToConsumer.values.all { it.isFinite() }) return
        withTransform({ transform(sourceToConsumer) }) { drawLayer(scene.scene) }
    }
}
