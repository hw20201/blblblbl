package com.github.mihomo.android.ui.glass

/*
 * Jelly motion and lens-rectangle geometry adapted from FlClash:
 * https://github.com/chen08209/FlClash/blob/8d4f974/lib/ui/navigation_dock.dart
 * FlClash contributors, GPL-3.0. See LICENSE and THIRD_PARTY_NOTICES.md.
 * Modified for Nexus: Kotlin/Compose port; keep Nexus's existing slot/icon layout.
 */

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.tanh

/** FlClash-derived motion, tuned to critically damped travel and restrained shape follow-through. */
internal object LensMotion {
    val travelStiffness = (4.0 * PI * PI / (0.4 * 0.4)).toFloat()
    const val travelDamping = 1f
    const val jellyStiffness = 180f
    const val jellyDamping = 1f
    val expansionStiffness = (4.0 * PI * PI / (0.25 * 0.25)).toFloat()
    const val jellyAspect = 1.4f
    const val expansionScale = 1.28f
}

/** FlClash _wobble: speed stretches; braking squashes; both feed a second spring. */
internal fun lensJellyTarget(velocity: Float, acceleration: Float): Float {
    val braking = max(0f, -acceleration * sign(velocity))
    return 0.08f * tanh(abs(velocity) / 3f) - 0.03f * tanh(braking / 40f)
}

/** Fade residual shape energy as travel settles, so shrinking cannot reveal a second wobble. */
internal fun lensVisibleStretch(stretch: Float, velocity: Float): Float =
    stretch * tanh(abs(velocity) / 1.5f)

/**
 * Direct port of _lensRect, without FlClash's slot redistribution. Width follows the
 * existing Nexus slot pitch. The centre never moves down when the lens deforms.
 */
internal fun liquidLensRect(
    position: Float, stretch: Float, pitch: Float, width: Float, height: Float, growth: Float = 0f
): Rect {
    val lensWidth = (width + growth) * (1f + stretch)
    val lensHeight = (height + growth) * (1f - stretch * LensMotion.jellyAspect)
    val center = (position + 0.5f) * pitch
    return Rect(center - lensWidth / 2f, (height - lensHeight) / 2f,
        center + lensWidth / 2f, (height + lensHeight) / 2f)
}

/** Resting inset; expansion grows around the same centre to cover the selected option. */
internal fun insetLensRect(
    position: Float, stretch: Float, expansion: Float, pitch: Float,
    barWidth: Float, barHeight: Float, density: Float
): Rect {
    val inset = minOf(6f * density, minOf(barWidth, barHeight) / 4f)
    val restWidth = (pitch - 2f * inset).coerceAtLeast(1f)
    val restHeight = (barHeight - 2f * inset).coerceAtLeast(1f)
    val expanded = expansion.coerceIn(0f, 1f)
    val scale = 1f + (LensMotion.expansionScale - 1f) * expanded
    // Keep the travel wobble at departure/settling, but a fully expanded shell must
    // cover the whole option rather than squash its height while moving sideways.
    val morph = stretch * (1f - expanded) * (1f - expanded)
    val liquid = liquidLensRect(position, morph, pitch, restWidth * scale, restHeight * scale)
    // Symmetric bounds contain extreme rapid reversals without moving the optical centre.
    // Deliberate overhang is allowed: a 1.28x shell covers the full selected tab at its peak.
    val overhang = 16f * density
    val maxWidth = (2f * minOf(liquid.center.x + overhang,
        barWidth + overhang - liquid.center.x)).coerceAtLeast(1f)
    val width = minOf(liquid.width, maxWidth)
    val height = minOf(liquid.height, barHeight + 2f * overhang)
    val left = liquid.center.x - width / 2f
    val top = (barHeight - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/**
 * Port of FlClash _Spring. Retarget with the current velocity, not a fresh tween.
 * In particular, the jelly's constantly changing target must NOT restart its clock
 * with a zero-time frame on each update (which would freeze its deformation).
 */
internal class LensSpring(
    initial: Float,
    private val stiffness: Float,
    private val dampingRatio: Float
) {
    var value: Float = initial
        private set
    var velocity: Float = 0f
        private set
    var target: Float = initial
        private set
    private var animation: TargetBasedAnimation<Float, AnimationVector1D>? = null
    private var startNanos = 0L
    val running: Boolean get() = animation != null
    val acceleration: Float get() = if (running) {
        stiffness * (target - value) - 2f * dampingRatio * sqrt(stiffness) * velocity
    } else 0f

    fun springTo(next: Float, now: Long) {
        if (next == target) return
        tick(now)
        target = next
        animation = TargetBasedAnimation(
            animationSpec = spring<Float>(dampingRatio, stiffness, visibilityThreshold = 0.0001f),
            typeConverter = Float.VectorConverter,
            initialValue = value,
            targetValue = next,
            initialVelocityVector = AnimationVector1D(velocity)
        )
        startNanos = now
    }

    fun snapTo(next: Float) {
        value = next
        target = next
        velocity = 0f
        animation = null
    }

    fun tick(now: Long) {
        val current = animation ?: return
        val elapsed = (now - startNanos).coerceAtLeast(0L)
        value = current.getValueFromNanos(elapsed)
        velocity = current.getVelocityVectorFromNanos(elapsed).value
        if (current.isFinishedFromNanos(elapsed)) {
            value = target
            velocity = 0f
            animation = null
        }
    }
}

/** Contract only after both motions have slowed, without cutting a rising spring short. */
internal fun lensReadyToContract(expansion: LensSpring, travel: LensSpring): Boolean {
    val expansionSettled = expansion.value >= 0.995f && abs(expansion.velocity) <= 0.2f
    val travelArrived = abs(travel.target - travel.value) <= 0.02f && abs(travel.velocity) <= 0.35f
    return expansion.target == 1f && expansionSettled && travelArrived
}

private data class LensFrame(val position: Float, val stretch: Float, val expansion: Float = 0f)

/** Only the small lens animates; the original Nexus icons and labels stay put. */
@Composable
fun NavigationLens(
    selectedSlot: Int,
    slotCount: Int,
    lens: NavigationLensState?,
    modifier: Modifier = Modifier,
    selectionPulse: Int = 0,
    dragPosition: Float? = null
) {
    if (lens == null || slotCount <= 0) return
    val destination = selectedSlot.coerceIn(0, slotCount - 1).toFloat()
    val travel = remember { LensSpring(destination, LensMotion.travelStiffness, LensMotion.travelDamping) }
    val jelly = remember { LensSpring(0f, LensMotion.jellyStiffness, LensMotion.jellyDamping) }
    val expansion = remember { LensSpring(0f, LensMotion.expansionStiffness, 1f) }
    var frame by remember { mutableStateOf(LensFrame(destination, 0f)) }
    LaunchedEffect(destination, selectionPulse, dragPosition) {
        withFrameNanos { now ->
            expansion.tick(now)
            if (dragPosition != null) {
                travel.snapTo(dragPosition.coerceIn(0f, (slotCount - 1).toFloat()))
                jelly.snapTo(0f)
                expansion.springTo(1f, now)
            } else if (destination != travel.target || selectionPulse != 0) {
                travel.springTo(destination, now)
                expansion.springTo(1f, now)
            }
            if (dragPosition == null && lensReadyToContract(expansion, travel)) {
                expansion.springTo(0f, now)
            }
            frame = LensFrame(travel.value, lensVisibleStretch(jelly.value, travel.velocity), expansion.value)
        }
        while (travel.running || jelly.running || expansion.running) {
            withFrameNanos { now ->
                travel.tick(now)
                expansion.tick(now)
                // Hold whole-shell expansion until its peak and horizontal arrival, then settle.
                if (dragPosition == null && lensReadyToContract(expansion, travel)) {
                    expansion.springTo(0f, now)
                }
                // Advance the OLD jelly leg before retargeting at this same timestamp.
                jelly.tick(now)
                jelly.springTo(lensJellyTarget(travel.velocity, travel.acceleration), now)
                frame = LensFrame(travel.value, lensVisibleStretch(jelly.value, travel.velocity), expansion.value)
            }
        }
    }
    val density = LocalDensity.current
    Layout(
        modifier = modifier.graphicsLayer { clip = false },
        content = {
            NavigationGlassSurface(
                modifier = Modifier.fillMaxSize().testTag("navigation-liquid-lens"),
                lens = lens,
                shape = CircleShape,
                cornerRadius = 1000.dp,
                strength = LensStrength.STRONG,
                expansionProgress = { frame.expansion },
                content = {}
            )
        }
    ) { measurables, constraints ->
        val barWidth = constraints.maxWidth
        val barHeight = constraints.maxHeight
        val pitch = barWidth.toFloat() / slotCount
        // Read frame-rate state only in measurement, not in the whole dock's composition.
        val position = dragPosition?.coerceIn(0f, (slotCount - 1).toFloat()) ?: frame.position
        val rect = insetLensRect(position, frame.stretch, frame.expansion, pitch,
            barWidth.toFloat(), barHeight.toFloat(), density.density)
        val child = measurables.single().measure(Constraints.fixed(
            rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)
        ))
        // The parent and vertical centre stay fixed. Only the glass grows; never the
        // hit targets, labels or icons. Do not clip its symmetric overhang.
        layout(barWidth, barHeight) {
            // Integer child dimensions must not nudge its centre by +/- half a pixel each frame.
            // Fractional layer placement preserves a fixed vertical centre and subpixel travel;
            // it does NOT scale the recorded scene or move any icon/label/hit target.
            child.placeWithLayer(0, 0) {
                translationX = rect.center.x - child.width / 2f
                translationY = barHeight / 2f - child.height / 2f
                clip = false
            }
        }
    }
}
