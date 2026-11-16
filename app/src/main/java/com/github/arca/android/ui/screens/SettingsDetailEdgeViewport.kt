package com.github.arca.android.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.arca.android.ui.glass.recordArcaGlasmioBackdropSource
import com.github.arca.android.ui.glass.rememberArcaGlasmioBackdrop
import io.github.neoruaa.glasmio.glass.progressiveBlurBackdrop

/** Clear space a scrolling body must keep above its first row (under the pinned title) and below its last. */
@Stable
internal class SettingsDetailEdgeInsets(val top: Dp, val bottom: Dp)

/** Non-null only inside [SettingsDetailEdgeViewport]; bodies use it instead of their own fixed padding. */
internal val LocalSettingsDetailEdgeInsets = compositionLocalOf<SettingsDetailEdgeInsets?> { null }

/** Scroll distance (px) reported by the body; drives how much of the top blur is revealed. */
@Stable
internal class SettingsDetailScrollEdge {
    var offsetPx by mutableIntStateOf(0)
}

internal val LocalSettingsDetailScrollEdge = compositionLocalOf<SettingsDetailScrollEdge?> { null }

/** Report the scroll distance of an embedded body without recomposing it per frame. */
@Composable
internal fun ReportSettingsDetailScrollOffset(offsetPx: () -> Int) {
    val edge = LocalSettingsDetailScrollEdge.current ?: return
    val latest by rememberUpdatedState(offsetPx)
    LaunchedEffect(edge) {
        snapshotFlow { latest() }.collect { edge.offsetPx = it }
    }
}

private val EdgeRevealDistance = 24.dp
private val EdgeBlurRadius = 10.dp
private val EdgeTransition = 28.dp
private val EdgeBottomFade = 20.dp
internal val SettingsDetailEdgeBottomInset = 40.dp

/** Smoothstep samples used for both complementary masks, so sharp + blurred alpha sum to 1. */
private val EdgeSteps = floatArrayOf(0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f)
/** Sharp coverage vs. position: stays low through the title block, then rises quickly. */
private fun smooth(t: Float): Float {
    val s = t * t * (3f - 2f * t)
    return s * s
}

/**
 * Scroll edge for translucent glass panels. The body is recorded unfaded as the blur source and
 * drawn sharp under a mask that dissolves toward the top. A progressive blur of that same source is
 * drawn with the complementary mask and added (Plus) inside one isolated layer, so
 * sharp + blurred coverage is exactly 1 at every row: content passing under the title becomes
 * blurred instead of being erased or doubled, with no rectangular strip and no extra tint.
 *
 * The viewport extends [bleed] past the host panel padding on top and bottom so scrolled content
 * dissolves into the panel's own rounded edge instead of being cut by a straight line.
 */
@Composable
internal fun SettingsDetailEdgeViewport(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    bleed: Dp = 14.dp,
    body: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val edge = remember { SettingsDetailScrollEdge() }
    val backdrop = rememberArcaGlasmioBackdrop(enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val bleedPx = with(density) { bleed.roundToPx() }
    val headerHeight = with(density) { headerHeightPx.toDp() } + bleed
    val revealDistancePx = with(density) { EdgeRevealDistance.toPx() }
    // After the first 24dp the mask is constant; later scroll pixels must not recompose this viewport.
    val reveal by remember(edge, revealDistancePx) {
        derivedStateOf { (edge.offsetPx / revealDistancePx).coerceIn(0f, 1f) }
    }
    val insets = remember(headerHeight) {
        SettingsDetailEdgeInsets(top = headerHeight + 10.dp, bottom = SettingsDetailEdgeBottomInset + bleed)
    }
    // The transition starts inside the title block and ends a little below it.
    val transitionStart = headerHeight * 0.4f
    val transitionEnd = headerHeight + EdgeTransition

    Box(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(
                        maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + bleedPx * 2 else constraints.maxHeight
                    )
                )
                layout(placeable.width, (placeable.height - bleedPx * 2).coerceAtLeast(0)) {
                    placeable.place(0, -bleedPx)
                }
            }
            .clipToBounds()
            // One isolated layer: the Plus-blended blur must never add onto the panel or wallpaper.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .testTag("settings-detail-edge-viewport")
    ) {
        CompositionLocalProvider(
            LocalSettingsDetailScrollEdge provides edge,
            LocalSettingsDetailEdgeInsets provides insets
        ) {
            Box(
                Modifier.fillMaxSize()
                    // Fade is outside the recorder: the source keeps its unfaded pixels.
                    .sharpEdgeMask(reveal, transitionStart, transitionEnd, EdgeBottomFade + bleed)
                    .recordArcaGlasmioBackdropSource(backdrop)
                    .testTag("settings-detail-edge-body"),
                content = body
            )
        }
        if (backdrop != null) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .height(transitionEnd)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        blendMode = BlendMode.Plus
                    }
                    .blurEdgeMask(reveal, transitionStart, transitionEnd)
                    .progressiveBlurBackdrop(
                        backdrop,
                        progress = if (reveal > 0f) 1f else 0f,
                        maxRadius = EdgeBlurRadius
                    )
                    .testTag("settings-detail-edge-blur")
            )
        }
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .padding(top = bleed)
                .onSizeChanged { headerHeightPx = it.height }
                .testTag("settings-detail-edge-header")
        ) { header() }
    }
}

/** Sharp body: alpha (1 - reveal) at the top rising smoothly to 1, plus a soft bottom dissolve. */
private fun Modifier.sharpEdgeMask(
    reveal: Float,
    start: Dp,
    end: Dp,
    bottomFade: Dp
): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val h = size.height.coerceAtLeast(1f)
        val s = start.toPx().coerceIn(0f, h)
        val e = end.toPx().coerceIn(s + 1f, h)
        val bottomStart = (h - bottomFade.toPx()).coerceAtLeast(e + 1f).coerceAtMost(h)
        val stops = ArrayList<Pair<Float, Color>>()
        stops += 0f to Color.White.copy(alpha = 1f - reveal)
        EdgeSteps.forEach { t ->
            stops += ((s + (e - s) * t) / h) to Color.White.copy(alpha = 1f - reveal * (1f - smooth(t)))
        }
        stops += (bottomStart / h) to Color.White
        stops += 1f to Color.Transparent
        val mask = Brush.verticalGradient(colorStops = stops.toTypedArray())
        onDrawWithContent {
            drawContent()
            drawRect(mask, blendMode = BlendMode.DstIn)
        }
    }

/** Blurred copy: alpha reveal at the top falling to 0 with the same curve, the exact complement. */
private fun Modifier.blurEdgeMask(
    reveal: Float,
    start: Dp,
    end: Dp
): Modifier = drawWithCache {
    val h = size.height.coerceAtLeast(1f)
    val s = start.toPx().coerceIn(0f, h)
    val e = end.toPx().coerceIn(s + 1f, h)
    val stops = ArrayList<Pair<Float, Color>>()
    stops += 0f to Color.White.copy(alpha = reveal)
    EdgeSteps.forEach { t ->
        stops += ((s + (e - s) * t) / h) to Color.White.copy(alpha = reveal * (1f - smooth(t)))
    }
    val mask = Brush.verticalGradient(colorStops = stops.toTypedArray())
    onDrawWithContent {
        drawContent()
        drawRect(mask, blendMode = BlendMode.DstIn)
    }
}
