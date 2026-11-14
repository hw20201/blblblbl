package com.github.arca.android.ui.glass

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

/** FlClash motion, faithfully ported (navigation_dock.dart), except travel:
 *  travel  = stiffness (2*pi/0.4)^2, damping ratio 1.0 (critically damped).
 *            FlClash uses bounce 0.15 (~0.85), which overshoots the slot by
 *            ~1.3px and comes back; the owner banned that left-right sway
 *            (2026-11-05), and the impact bounce is delivered by the jelly,
 *            not by travel overshoot. Do NOT lower this to re-add the bounce.
 *  follow  = SpringDescription.withDampingRatio(stiffness 1024, ratio 0.9):
 *            while dragging the lens CHASES the finger on a spring instead
 *            of snapping to it; that lag is what feeds the jelly.
 *  jelly   = SpringDescription.withDampingRatio(stiffness 180, ratio 0.62):
 *            one restrained elastic rebound, then a quicker return to rest. */
internal object LensMotion {
    val travelStiffness = (4.0 * PI * PI / (0.4 * 0.4)).toFloat()
    const val travelDamping = 1.0f
    const val followStiffness = 1024f
    const val followDamping = 0.9f
    const val jellyStiffness = 180f
    const val jellyDamping = 0.62f
    val expansionStiffness = (4.0 * PI * PI / (0.25 * 0.25)).toFloat()
    const val jellyAspect = 1.4f
    const val expansionScale = 1.28f
}

/** Wobble, owner's rule (2026-11-05): no horizontal sway while travelling.
 *  FlClash _wobble has two terms (speed stretch 0.2, braking squash 0.15); the
 *  speed term is dropped here, only the braking term survives. On impact the
 *  lens lengthens vertically: negative stretch makes height grow by 1.4x and
 *  width narrow (see liquidLensRect). _jellySquash 0.15, _jellyBrake 25. */
internal fun lensJellyTarget(velocity: Float, acceleration: Float): Float {
    val braking = max(0f, -acceleration * sign(velocity))
    return -0.15f * tanh(braking / 25f)
}

/** FlClash applies _jelly.value straight into _lensRect with no velocity gate.
 *  The old gate faded stretch out exactly as travel arrived, hiding the impact
 *  lengthening. Jelly settles to 0 on its own when travel stops. */
@Suppress("UNUSED_PARAMETER")
internal fun lensVisibleStretch(stretch: Float, velocity: Float): Float = stretch

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
    // FlClash _lensRect applies stretch at all times, with no suppression for
    // lift/expansion. `expanded` only scales the shell; the squash stays full.
    val morph = stretch
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
    private val defaultStiffness: Float,
    private val defaultDampingRatio: Float
) {
    // FlClash _Spring has no fixed spec: every springTo carries its own
    // SpringDescription (travel vs follow vs hover). The construction spec is
    // only the default for the plain springTo call; a follow call must never
    // leak its spec into the next travel call (that was the release bug:
    // after a drag, release silently ran on the 1024/0.9 follow spring).
    private var currentStiffness = defaultStiffness
    private var currentDampingRatio = defaultDampingRatio
    fun springTo(next: Float, now: Long, stiffness: Float, dampingRatio: Float) {
        if (next == target && stiffness == currentStiffness && dampingRatio == currentDampingRatio) return
        tick(now)
        currentStiffness = stiffness
        currentDampingRatio = dampingRatio
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
        currentStiffness * (target - value) - 2f * currentDampingRatio * sqrt(currentStiffness) * velocity
    } else 0f

    fun springTo(next: Float, now: Long) {
        if (next == target && currentStiffness == defaultStiffness && currentDampingRatio == defaultDampingRatio) return
        tick(now)
        currentStiffness = defaultStiffness
        currentDampingRatio = defaultDampingRatio
        target = next
        animation = TargetBasedAnimation(
            animationSpec = spring<Float>(defaultDampingRatio, defaultStiffness, visibilityThreshold = 0.0001f),
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

/** Contract once the shell has peaked and the lens has arrived, as FlClash
 *  _dropLift does (lift >= 0.975 and |lens.target - lens.value| <= 0.04).
 *  The jelly is NOT part of this gate: it is a shape morph that settles to
 *  zero on its own, and waiting for it (~2s at stiffness 100 / damping 0.4)
 *  left the shell floating long after the lens had arrived. The width
 *  relaxing back to rest while the jelly decays is the jelly settling, not
 *  a second growth: the expansion returns in ~250ms, far faster than the
 *  jelly relaxes, so the shell is down before that relaxation shows. */
internal fun lensReadyToContract(expansion: LensSpring, travel: LensSpring): Boolean {
    val expansionSettled = expansion.value >= 0.995f && abs(expansion.velocity) <= 0.2f
    val travelArrived = abs(travel.target - travel.value) <= 0.02f && abs(travel.velocity) <= 0.35f
    return expansion.target == 1f && expansionSettled && travelArrived
}

internal data class LensFrame(val position: Float, val stretch: Float, val expansion: Float = 0f)

/** Preserve the original refractive liquid-glass lens for the bottom navigation selection. */
internal val NAVIGATION_SELECTED_LENS_STRENGTH = LensStrength.STRONG

/** Shared by the shell and foreground drawing; never changes tab layout or hit targets. */
@androidx.compose.runtime.Stable
class NavigationLensOpticsState(initialPosition: Float) {
    internal var frame by mutableStateOf(LensFrame(initialPosition, 0f))
}

/** Smooth spatial falloff follows the actual lens, including fractional drag positions. */
internal fun lensTabMagnification(slot: Int, position: Float, expansion: Float): Float {
    val proximity = (1f - abs(slot - position) / .95f).coerceIn(0f, 1f)
    val coverage = proximity * proximity * (3f - 2f * proximity)
    return 1f + coverage * (.04f + .07f * expansion.coerceIn(0f, 1f))
}

/** The shell and its sharp foreground share one motion clock. */
@Composable
fun NavigationLens(
    selectedSlot: Int,
    slotCount: Int,
    lens: NavigationLensState?,
    modifier: Modifier = Modifier,
    selectionPulse: Int = 0,
    dragPosition: Float? = null,
    optics: NavigationLensOpticsState? = null
) {
    if (lens == null || slotCount <= 0) return
    val destination = selectedSlot.coerceIn(0, slotCount - 1).toFloat()
    val travel = remember { LensSpring(destination, LensMotion.travelStiffness, LensMotion.travelDamping) }
    val jelly = remember { LensSpring(0f, LensMotion.jellyStiffness, LensMotion.jellyDamping) }
    val expansion = remember { LensSpring(0f, LensMotion.expansionStiffness, 1f) }
    val motion = optics ?: remember { NavigationLensOpticsState(destination) }
    var frame by motion::frame
    LaunchedEffect(destination, selectionPulse, dragPosition) {
        withFrameNanos { now ->
            expansion.tick(now)
            if (dragPosition != null) {
                // FlClash _slide: the lens follows the finger on _followSpring
                // (1024 / 0.9), it does NOT snap to it. The chase lag gives the
                // lens real velocity, and _wobble feeds the jelly from it, so
                // the squash is live while dragging. Overshoot range comes from
                // lensDragPosition: [-0.35, slots-1+0.35].
                travel.springTo(
                    dragPosition.coerceIn(-0.35f, (slotCount - 1).toFloat() + 0.35f),
                    now, LensMotion.followStiffness, LensMotion.followDamping
                )
                expansion.springTo(1f, now)
            } else if (destination != travel.target || selectionPulse != 0) {
                travel.springTo(destination, now)
                expansion.springTo(1f, now)
            }
            // The ticker restarts on every drag event, so the while loop never
            // runs while the finger moves. Jelly must advance here too, or it
            // freezes mid-drag (e.g. stuck tall after a quick re-press) and
            // only wakes up on release. Travel was just retargeted above, so
            // its velocity is fresh for the jelly target.
            jelly.tick(now)
            jelly.springTo(lensJellyTarget(travel.velocity, travel.acceleration), now)
            // Check contraction AFTER every spring has ticked this frame. The
            // jelly is the last spring to settle; checking before its tick lets
            // the loop exit on the frame it finishes, leaving the shell stuck
            // expanded (settled width 114 instead of 89).
            if (dragPosition == null && lensReadyToContract(expansion, travel)) {
                expansion.springTo(0f, now)
            }
            frame = LensFrame(travel.value, lensVisibleStretch(jelly.value, travel.velocity), expansion.value)
        }
        while (travel.running || jelly.running || expansion.running) {
            withFrameNanos { now ->
                travel.tick(now)
                expansion.tick(now)
                // Advance the OLD jelly leg before retargeting at this same timestamp.
                jelly.tick(now)
                jelly.springTo(lensJellyTarget(travel.velocity, travel.acceleration), now)
                // Hold whole-shell expansion until its peak and horizontal arrival, then settle.
                // This check must run AFTER all three ticks: the jelly settles
                // last, and checking before its tick lets the loop exit on the
                // frame it finishes, leaving the shell stuck expanded.
                if (dragPosition == null && lensReadyToContract(expansion, travel)) {
                    expansion.springTo(0f, now)
                }
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
                strength = NAVIGATION_SELECTED_LENS_STRENGTH,
                surfaceCategory = GlassSurfaceCategory.BOTTOM_NAVIGATION,
                expansionProgress = { frame.expansion },
                content = {}
            )
        }
    ) { measurables, constraints ->
        val barWidth = constraints.maxWidth
        val barHeight = constraints.maxHeight
        val pitch = barWidth.toFloat() / slotCount
        // Read frame-rate state only in measurement, not in the whole dock's composition.
        // FlClash: the lens is always _lens.value. The finger only sets the
        // target the follow spring chases; showing the raw finger position
        // during a drag made the capsule teleport back to the lagging spring
        // on every release (half a slot at fast swipe speed, both directions).
        // The tab icons already magnify from frame.position; the shell must agree.
        val position = frame.position
        val rect = insetLensRect(position, frame.stretch, frame.expansion, pitch,
            barWidth.toFloat(), barHeight.toFloat(), density.density)
        val child = measurables.single().measure(Constraints.fixed(
            rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)
        ))
        // The parent and vertical centre stay fixed. Foreground drawing responds
        // separately, without moving hit targets. Do not clip the shell's overhang.
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
