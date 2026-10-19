package com.github.mihomo.android.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** The lens stretches horizontally, squashes vertically and overhangs the dock. */
object LensMorph {
    val restSize: Dp = 62.dp
    val maxStretch: Dp = 96.dp
    val overhang: Dp = 7.dp
    val squash: Dp = 8.dp
}

internal fun stretchFor(progress: Float): Float =
    sin(progress.coerceIn(0f, 1f) * PI.toFloat()).coerceIn(0f, 1f)

/** Carry the in-flight shape into a new leg without jumping back to a circle. */
internal fun lensStretchFor(progress: Float, initialStretch: Float): Float =
    (initialStretch * (1f - progress.coerceIn(0f, 1f)) + stretchFor(progress)).coerceIn(0f, 1f)

internal fun lensCenterFor(start: Float, target: Float, progress: Float): Float =
    start + (target - start) * progress.coerceIn(0f, 1f)

/** Clamp only when a narrow dock cannot fit a lens centred on the edge slot. */
internal fun lensLeftFor(slot: Int, count: Int, barWidth: Float, lensWidth: Float): Float {
    if (count <= 0 || barWidth <= 0f) return 0f
    val centre = barWidth / count * (slot.coerceIn(0, count - 1) + 0.5f)
    return (centre - lensWidth / 2f).coerceIn(0f, (barWidth - lensWidth).coerceAtLeast(0f))
}

/** One progress value drives both travel and shape. The dock never records itself. */
@Composable
fun NavigationLens(
    selectedSlot: Int,
    slotCount: Int,
    lens: NavigationLensState?,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    if (lens == null || slotCount <= 0) return
    BoxWithConstraints(modifier.fillMaxWidth().graphicsLayer { clip = false }) {
        val density = LocalDensity.current
        val barWidthPx = constraints.maxWidth.toFloat()
        val restPx = with(density) { LensMorph.restSize.toPx() }
        val effectiveRestPx = minOf(restPx, barWidthPx.coerceAtLeast(0f))
        val maxStretchPx = minOf(with(density) { LensMorph.maxStretch.toPx() }, barWidthPx.coerceAtLeast(0f))
        val squashPx = with(density) { LensMorph.squash.toPx() }
        val overhangPx = with(density) { LensMorph.overhang.toPx() }
        val targetX = lensLeftFor(selectedSlot, slotCount, barWidthPx, effectiveRestPx) + effectiveRestPx / 2f
        val progress = remember { Animatable(1f) }
        var travelStart by remember { mutableFloatStateOf(targetX) }
        var travelTarget by remember { mutableFloatStateOf(targetX) }
        var initialStretch by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(targetX) {
            if (barWidthPx <= 0f || targetX == travelTarget) return@LaunchedEffect
            // Read the OLD endpoints before changing either: using the new target here would
            // teleport a settled lens directly to its destination before the animation starts.
            val currentX = lensCenterFor(travelStart, travelTarget, progress.value)
            val currentStretch = lensStretchFor(progress.value, initialStretch)
            travelStart = currentX
            travelTarget = targetX
            initialStretch = currentStretch
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow))
        }

        val stretch = lensStretchFor(progress.value, initialStretch)
        val centreX = lensCenterFor(travelStart, travelTarget, progress.value)
        val widthPx = effectiveRestPx + (maxStretchPx - effectiveRestPx) * stretch
        val heightPx = (effectiveRestPx - squashPx * stretch).coerceAtLeast(0f)
        val left = (centreX - widthPx / 2f).coerceIn(0f, (barWidthPx - widthPx).coerceAtLeast(0f))
        val top = (restPx - heightPx) / 2f + overhangPx
        Box(
            Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                .graphicsLayer { clip = false },
            contentAlignment = Alignment.Center
        ) {
            NavigationGlassSurface(
                modifier = Modifier.fillMaxSize(),
                lens = lens,
                shape = CircleShape,
                cornerRadius = with(density) { (heightPx / 2f).toDp() },
                strength = LensStrength.STRONG,
                content = {}
            )
            icon()
        }
    }
}
