package com.github.mihomo.android.ui.components
import com.github.mihomo.android.ui.glass.GlassMotionTuning
import com.github.mihomo.android.ui.glass.LocalGlassSettings

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.ui.glass.ShortcutRefraction
import com.github.mihomo.android.ui.glass.SHORTCUT_CONTACT_REBOUND
import com.github.mihomo.android.ui.glass.LocalShortcutTunedMaterialEnabled
import com.github.mihomo.android.ui.glass.ShortcutTunedCardShape
import com.github.mihomo.android.ui.glass.ShortcutFollowupCardShape
import com.github.mihomo.android.ui.glass.LocalShortcutOpticalFollowupEnabled
import com.github.mihomo.android.ui.glass.LocalShortcutContactFollowEnabled
import com.github.mihomo.android.ui.glass.SHORTCUT_CONTACT_REST_SOFTNESS
import com.github.mihomo.android.ui.glass.LocalShortcutDockLensEnabled
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Persisted comparison choices; names are stable preference values. */
enum class ShortcutTouchVariant { GentleFloat, FingerTilt, BuoyantSpring }

/** The B slab sits visibly above its slot even before contact; press tilt pivots around it. */
internal const val FINGER_TILT_REST_FLOAT_DP = 6f
internal val FINGER_TILT_MAX_DEGREES: Float get() = GlassMotionTuning.DEFAULT.effectiveMaxTiltDegrees()
internal fun shortcutLiveRestingPose(variant: ShortcutTouchVariant): ShortcutTouchPose =
    if (variant == ShortcutTouchVariant.FingerTilt) ShortcutTouchPose(offsetYDp = -FINGER_TILT_REST_FLOAT_DP)
    else ShortcutTouchPose()

/** Frozen poses are available only to deterministic local tests, not a running animation clock. */
data class ShortcutTouchPose(
    val scale: Float = 1f,
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f
)
data class ShortcutTouchPreview(val title: String, val pose: ShortcutTouchPose)
val LocalShortcutTouchVariant = staticCompositionLocalOf<ShortcutTouchVariant?> { null }
val LocalShortcutTouchPreview = staticCompositionLocalOf<ShortcutTouchPreview?> { null }

fun shortcutTouchTarget(variant: ShortcutTouchVariant, x: Float, y: Float, tuned: Boolean = true): ShortcutTouchPose {
    val nx = x.coerceIn(-1f, 1f)
    val ny = y.coerceIn(-1f, 1f)
    return when (variant) {
        ShortcutTouchVariant.GentleFloat -> ShortcutTouchPose(
            scale = 0.997f, rotationX = -ny * 0.75f, rotationY = nx * 0.75f)
        ShortcutTouchVariant.FingerTilt -> if (tuned) ShortcutTouchPose(
            // Deterministic legacy-preview pose: live cards use shortcutLiveTouchTarget instead.
            scale = 1f, rotationX = -ny * 6f, rotationY = nx * 6f
        ) else ShortcutTouchPose(
            scale = 0.975f, offsetXDp = nx * 2f, offsetYDp = -4f + ny * 2f,
            rotationX = -ny * 4f, rotationY = nx * 4f)
        ShortcutTouchVariant.BuoyantSpring -> ShortcutTouchPose(
            scale = 0.996f, rotationX = -ny * 1f, rotationY = nx * 1f)
    }
}

/** Live dock-card response. Stable slot coordinates avoid transform/gesture feedback.
 * A rises on press from a neutral rest; B starts elevated and tilts under the contact;
 * C lifts farther and rebounds with a softer spring. Legacy/frozen comparisons stay unchanged.
 */
internal fun shortcutLiveTouchTarget(variant: ShortcutTouchVariant, x: Float, y: Float, motion: GlassMotionTuning = GlassMotionTuning.DEFAULT): ShortcutTouchPose {
    // A soft centre suppresses sensor jitter; normalizing the vector caps diagonal tilt too.
    val radius = sqrt(x * x + y * y)
    val amount = ((radius - .045f) / .955f).coerceIn(0f, 1f)
    val nx = if (radius > 0f) x / radius * amount else 0f
    val ny = if (radius > 0f) y / radius * amount else 0f
    return when (variant) {
        ShortcutTouchVariant.GentleFloat -> ShortcutTouchPose(
            scale = 1.008f, offsetYDp = -2.8f, rotationX = -ny * .55f, rotationY = nx * .55f)
        ShortcutTouchVariant.FingerTilt -> ShortcutTouchPose(
            // Pivot the rigid card plane toward the pressed corner. Under Compose's y-down
            // convention this sign pair brings the contact side forward; every child (including
            // the two lower-right rectangles) inherits this one transform and stays in-plane.
            offsetYDp = -FINGER_TILT_REST_FLOAT_DP,
            rotationX = ny * motion.effectiveMaxTiltDegrees(),
            rotationY = -nx * motion.effectiveMaxTiltDegrees())
        ShortcutTouchVariant.BuoyantSpring -> ShortcutTouchPose(
            scale = 1.018f, offsetYDp = -6.2f, rotationX = -ny * 1.6f, rotationY = nx * 1.6f)
    }
}

/** Normalized screen-local pointer coordinates used as the stable tilt anchor. */
internal fun shortcutNormalizedTiltPosition(position: Offset, size: IntSize): Offset = Offset(
    ((position.x / size.width.coerceAtLeast(1)) * 2f - 1f).coerceIn(-1f, 1f),
    ((position.y / size.height.coerceAtLeast(1)) * 2f - 1f).coerceIn(-1f, 1f)
)

/**
 * Additional, contact-anchored perspective for the clear foreground only. The outer glass plane
 * keeps its existing touch-follow transform; this bounded residual tilt gives glyph strokes and
 * every icon a stronger shared foreshortening, matching the supplied perspective reference.
 */
internal const val SHORTCUT_CONTENT_PERSPECTIVE_FRACTION = 0.42f

data class ShortcutContentPerspectivePose(
    val rotationX: Float,
    val rotationY: Float,
    val pivotX: Float,
    val pivotY: Float,
    val strength: Float
)

internal fun shortcutContentPerspectivePose(
    x: Float,
    y: Float,
    baseRotationX: Float,
    baseRotationY: Float
): ShortcutContentPerspectivePose {
    val nx = x.coerceIn(-1f, 1f)
    val ny = y.coerceIn(-1f, 1f)
    val radius = sqrt(nx * nx + ny * ny)
    val radial = ((radius - .045f) / .955f).coerceIn(0f, 1f)
    val strength = radial * SHORTCUT_CONTENT_PERSPECTIVE_FRACTION
    return ShortcutContentPerspectivePose(
        rotationX = baseRotationX * SHORTCUT_CONTENT_PERSPECTIVE_FRACTION,
        rotationY = baseRotationY * SHORTCUT_CONTENT_PERSPECTIVE_FRACTION,
        pivotX = ((nx + 1f) * .5f).coerceIn(0f, 1f),
        pivotY = ((ny + 1f) * .5f).coerceIn(0f, 1f),
        strength = strength
    )
}

internal fun shortcutReleaseDamping(variant: ShortcutTouchVariant): Float = when (variant) {
    ShortcutTouchVariant.GentleFloat -> .92f
    ShortcutTouchVariant.FingerTilt -> .78f
    ShortcutTouchVariant.BuoyantSpring -> .50f
}

internal fun shortcutReleaseStiffness(variant: ShortcutTouchVariant): Float = when (variant) {
    ShortcutTouchVariant.GentleFloat -> 850f
    ShortcutTouchVariant.FingerTilt -> 650f
    ShortcutTouchVariant.BuoyantSpring -> 420f
}

/** Test override also verifies reduced motion without modifying global settings. */
internal val LocalShortcutMotionEnabled = staticCompositionLocalOf<Boolean?> { null }

@Composable
internal fun rememberShortcutAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = ValueAnimator.areAnimatorsEnabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

private class TouchInput {
    var pressed by mutableStateOf(false)
    var cancelled by mutableStateOf(false)
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var dragging by mutableStateOf(false)
    var dragX by mutableFloatStateOf(0f)
    var dragY by mutableFloatStateOf(0f)
    var tiltX by mutableFloatStateOf(0f)
    var tiltY by mutableFloatStateOf(0f)
    var slotCoordinates: LayoutCoordinates? = null
    var planeCoordinates: LayoutCoordinates? = null
    // Keep the last contact point while the lift springs back. Resetting x/y on release
    // would move the shader's lens target to the card centre in the very same frame.
    fun reset() { pressed = false; dragging = false; dragX = 0f; dragY = 0f }
}

internal class ShortcutTouchMotion(
    val gestureModifier: Modifier,
    val visualModifier: Modifier,
    val refraction: () -> ShortcutRefraction,
    val contentModifier: Modifier
) {
    val modifier: Modifier = gestureModifier.then(visualModifier)
}

/** Directional slop arbitration: ambiguous diagonals are deliberately left to the parent. */
internal fun shortcutHorizontalIntent(dx: Float, dy: Float, touchSlop: Float): Boolean =
    abs(dx) > max(0f, touchSlop) * 1.25f && abs(dx) > abs(dy) * 1.5f

/** B keeps the 4dp travel radius in every direction; diagonals never exceed it. */
internal fun shortcutJellyTravel(delta: Offset, limit: Float): Offset {
    val raw = delta * .09f
    val length = raw.getDistance()
    return if (length > limit && length > 0f) raw * (limit / length) else raw
}

/** One finite gesture animation drives slab and backdrop optics; no idle clock or capture. */
@Composable
internal fun rememberShortcutTouchMotion(
    variant: ShortcutTouchVariant,
    enabled: Boolean,
    frozenPose: ShortcutTouchPose? = null,
    onPickupChanged: (Boolean) -> Unit = {}
): ShortcutTouchMotion {
    val tuned = LocalShortcutTunedMaterialEnabled.current
    val followup = LocalShortcutOpticalFollowupEnabled.current
    val dockLens = LocalShortcutDockLensEnabled.current
    val refined = dockLens && tuned
    val glassMotion = LocalGlassSettings.current.motion
    val jelly = refined && variant == ShortcutTouchVariant.FingerTilt
    val splitShadow = tuned && followup && !jelly
    val contactFollow = tuned && followup && LocalShortcutContactFollowEnabled.current && enabled && frozenPose == null
    val input = remember(variant, enabled, tuned) { TouchInput() }
    val pickupCallback by rememberUpdatedState(onPickupChanged)
    fun currentTarget(x: Float, y: Float): ShortcutTouchPose = when {
        frozenPose != null -> shortcutTouchTarget(variant, x, y, tuned)
        refined -> shortcutLiveTouchTarget(variant, x, y, glassMotion)
        // B's raised resting position belongs to the live card, even when the optional
        // refined dock-lens path is off. Keep legacy/frozen preview mappings untouched.
        variant == ShortcutTouchVariant.FingerTilt ->
            shortcutTouchTarget(variant, x, y, tuned).copy(offsetYDp = -FINGER_TILT_REST_FLOAT_DP)
        else -> shortcutTouchTarget(variant, x, y, tuned)
    }
    val animate = (LocalShortcutMotionEnabled.current ?: true) && enabled
    val damping = if (refined) shortcutReleaseDamping(variant) else when (variant) {
        ShortcutTouchVariant.GentleFloat -> 0.88f
        ShortcutTouchVariant.FingerTilt -> if (tuned) 0.72f else 0.76f
        ShortcutTouchVariant.BuoyantSpring -> 0.55f
    }
    val target = when {
        !animate -> ShortcutTouchPose()
        frozenPose != null -> frozenPose
        input.pressed -> currentTarget(if (jelly) input.tiltX else input.x, if (jelly) input.tiltY else input.y)
        else -> shortcutLiveRestingPose(variant)
    }
    // Compose's animation clock applies the system duration scale, including slow scales.
    val releaseSpec = spring<Float>(
        // B's tilted glass plane should settle promptly and without residual wobble after
        // either release or cancellation; its touch-follow spring already uses this range.
        dampingRatio = if (input.cancelled || variant == ShortcutTouchVariant.FingerTilt) 1f else damping,
        stiffness = when {
            input.cancelled && variant == ShortcutTouchVariant.FingerTilt -> 10000f
            input.cancelled -> 1100f
            refined -> shortcutReleaseStiffness(variant)
            variant == ShortcutTouchVariant.FingerTilt -> 1100f
            else -> Spring.StiffnessMedium
        })
    // A/C lift quietly on contact. B has a fast, critically damped approach, not a
    // one-frame snap or a continually restarted tween. Release retains velocity.
    val spec = if (input.pressed) tween<Float>(if (refined && variant == ShortcutTouchVariant.BuoyantSpring) 110 else 85)
        else releaseSpec
    val scale = animateFloatAsState(target.scale, spec, label = "shortcut_scale")
    val dx = animateFloatAsState(target.offsetXDp, spec, label = "shortcut_x")
    val dy = animateFloatAsState(target.offsetYDp, spec, label = "shortcut_y")
    // B follows the contact with a short, critically damped spring: responsive without a
    // one-frame jump, including when the pointer moves while held.
    val tiltSpec = if (input.pressed && tuned && variant == ShortcutTouchVariant.FingerTilt)
        spring<Float>(dampingRatio = glassMotion.dampingRatio(), stiffness = if (refined) 2200f else Spring.StiffnessHigh) else spec
    val rx = animateFloatAsState(target.rotationX, tiltSpec, label = "shortcut_rx")
    val ry = animateFloatAsState(target.rotationY, tiltSpec, label = "shortcut_ry")
    val liftTarget = when {
        !animate -> 0f
        frozenPose != null -> (abs(1f - frozenPose.scale) / abs(1f - currentTarget(0f, 0f).scale).coerceAtLeast(.0001f)).coerceIn(0f, 1f)
        input.pressed -> 1f
        else -> 0f
    }
    val lift = animateFloatAsState(liftTarget, spec, label = "shortcut_lift")
    val contactTarget = if (contactFollow && input.pressed) 1f else 0f
    val contactSpec = when {
        !animate -> snap<Float>()
        contactTarget > 0f -> tween<Float>(85)
        else -> spring(dampingRatio = SHORTCUT_CONTACT_REBOUND, stiffness = Spring.StiffnessMedium)
    }
    val animatedContact = animateFloatAsState(contactTarget, contactSpec, label = "shortcut_contact")
    fun contactValue(): Float = if (!contactFollow) 0f else if (!animate) contactTarget
        else if (input.pressed) animatedContact.value.coerceAtLeast(.35f)
        else animatedContact.value.coerceIn(0f, 1f)
    val maximumLift = when (variant) {
        ShortcutTouchVariant.GentleFloat -> 2.8f
        ShortcutTouchVariant.FingerTilt -> if (tuned) FINGER_TILT_REST_FLOAT_DP else 4f
        ShortcutTouchVariant.BuoyantSpring -> 6.2f
    }
    fun visibleLift(): Float = when {
        !animate -> 0f
        // A short tap may end before the 85 ms lift tween advances much. Keep a visible
        // minimum so the optical state is perceptible from the first rendered press frame.
        input.pressed && frozenPose == null -> lift.value.coerceAtLeast(.35f)
        else -> lift.value.coerceIn(0f, 1f)
    }
    // Releasing a held pointer preserves spring velocity; active gestures can still use the
    // separately measured contact/drag channels without jumping the slab pose.
    val directlyManipulated = input.dragging
    val dragSpec = if (directlyManipulated || !animate) snap<Float>()
        else releaseSpec
    val travelActive = animate && input.dragging
    val dragX = animateFloatAsState(if (travelActive) input.dragX else 0f, dragSpec, label = "shortcut_drag_x")
    val dragY = animateFloatAsState(if (travelActive) input.dragY else 0f, dragSpec, label = "shortcut_drag_y")
    // Read input live inside long-lived pointerInput, not a captured composition Boolean.
    fun travelX(): Float = if (!animate) 0f else if (input.dragging) input.dragX else dragX.value
    fun travelY(): Float = if (!animate) 0f else if (input.dragging) input.dragY else dragY.value
    val gestureModifier = Modifier.onGloballyPositioned { input.slotCoordinates = it }
        .pointerInput(input, enabled, jelly, animate) {
        if (!enabled) return@pointerInput
        try {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.isConsumed) return@awaitEachGesture
                input.cancelled = true
                input.pressed = true
                var horizontallyLocked = false
                fun update(position: Offset) {
                    // Intent stays in the unmoving slot; do not feed a rotating plane back
                    // into its own tilt target. Optical contact uses the actual plane instead.
                    val normalizedTiltPosition = shortcutNormalizedTiltPosition(position, size)
                    input.tiltX = normalizedTiltPosition.x
                    input.tiltY = normalizedTiltPosition.y
                    val slot = input.slotCoordinates?.takeIf { it.isAttached }
                    val plane = input.planeCoordinates?.takeIf { it.isAttached }
                    val local = if (jelly && slot != null && plane != null)
                        plane.localPositionOf(slot, position)
                    else position - Offset(travelX(), travelY())
                    val width = if (jelly && plane != null) plane.size.width else size.width
                    val height = if (jelly && plane != null) plane.size.height else size.height
                    input.x = ((local.x / width.coerceAtLeast(1)) * 2f - 1f).coerceIn(-1f, 1f)
                    input.y = ((local.y / height.coerceAtLeast(1)) * 2f - 1f).coerceIn(-1f, 1f)
                }
                update(down.position)
                try {
                    // Keep the established four-dp pickup radius; the contact lens follows
                    // the translated plane so its highlight stays under the finger.
                    val maxDragX = 4.dp.toPx()
                    // Initial pass wins before verticalScroll's Main-pass slop detector.
                    // A normal tap stays unconsumed; the first slop-crossing move cancels click.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (event.changes.count { it.pressed } > 1) {
                            event.changes.forEach { it.consume() }
                            break
                        }
                        if (!change.pressed) {
                            input.cancelled = false
                            update(change.position)
                            if (horizontallyLocked) change.consume()
                            break
                        }
                        if (change.isConsumed) {
                            // A parent won the Main-pass gesture; this contact must not be
                            // reacquired by another iteration of this same awaitEachGesture.
                            break
                        }
                        val delta = change.position - down.position
                        // A normal vertical swipe must still scroll the dashboard. A deliberate
                        // hold then drag can pick B up in any direction; horizontal intent stays fast.
                        val heldForDrag = change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
                        val jellyIntent = jelly && heldForDrag && delta.getDistance() > viewConfiguration.touchSlop
                        if (!horizontallyLocked && jellyIntent) {
                            horizontallyLocked = true
                            input.dragging = true
                            pickupCallback(true)
                        } else if (!horizontallyLocked && shortcutHorizontalIntent(delta.x, delta.y, viewConfiguration.touchSlop)) {
                            horizontallyLocked = true
                            input.dragging = true
                            pickupCallback(true)
                        } else if (!horizontallyLocked && abs(delta.y) > viewConfiguration.touchSlop &&
                            abs(delta.y) > abs(delta.x) / 1.5f) {
                            // Let the parent's Main-pass scroll detector process this move first.
                            // Then cancel any still-waiting clickable at Final: without a scroll
                            // parent (or at a boundary), a vertical drag must not become a tap.
                            update(change.position)
                            input.pressed = false
                            awaitPointerEvent(PointerEventPass.Final).changes
                                .firstOrNull { it.id == down.id }?.consume()
                            break
                        }
                        if (jelly && animate) {
                            val travel = shortcutJellyTravel(delta, maxDragX)
                            input.dragX = travel.x
                            input.dragY = travel.y
                        }
                        if (horizontallyLocked) {
                            // This pointer node is outside the visual graphicsLayer: stable anchor,
                            // no feedback from the moved card, no exit when the finger leaves it.
                            // Legacy A/C preserve their horizontal-only pickup. B keeps the
                            // radial 4dp travel computed above, including diagonal/vertical drift.
                            if (!jelly) {
                                input.dragX = (delta.x * 0.09f).coerceIn(-maxDragX, maxDragX)
                                input.dragY = 0f
                            }
                            change.consume()
                        }
                        update(change.position)
                    }
                } finally {
                    input.reset()
                    if (horizontallyLocked) pickupCallback(false)
                }
            }
        } finally { input.reset() }
    }
    // Keep the glass slab on its critically damped touch-follow spring. The shader's
    // contact point remains direct, while the physical plane eases into each tilt target.
    fun visiblePitch(): Float = if (!animate) 0f else rx.value
    fun visibleRoll(): Float = if (!animate) 0f else ry.value
    val visualModifier = Modifier.graphicsLayer {
        val immediate = animate && input.pressed && frozenPose == null
        val targetScale = currentTarget(0f, 0f).scale
        val kickScale = 1f + (targetScale - 1f) * .18f
        val visibleScale = when {
            !animate -> 1f
            immediate && targetScale < 1f -> scale.value.coerceAtMost(kickScale)
            immediate -> scale.value.coerceAtLeast(kickScale)
            else -> scale.value
        }
        // Keep the slab's footprint isotropic: perspective comes from the 3-D rotation and
        // matching refraction, never from squeezing one axis (which reads as a stretch).
        scaleX = visibleScale
        scaleY = visibleScale
        translationX = (if (animate) dx.value else 0f).dp.toPx() + travelX()
        translationY = (when { !animate -> 0f; immediate && target.offsetYDp != 0f -> dy.value.coerceAtMost(-maximumLift * .18f); else -> dy.value }).dp.toPx() + travelY()
        rotationX = if (!splitShadow) visiblePitch() else 0f
        rotationY = if (!splitShadow) visibleRoll() else 0f
        cameraDistance = (if (tuned && variant == ShortcutTouchVariant.FingerTilt) 160f
            else if (tuned) 800f * (118f / 190f) else 16f).dp.toPx()
        shadowElevation = if (dockLens) 0f else if (contactFollow) (.8f - .6f * contactValue()).dp.toPx()
            else ((if (animate) 0.45f else 0f) + visibleLift() * 1.1f).dp.toPx()
        shape = if (tuned && followup) ShortcutFollowupCardShape else if (tuned) ShortcutTunedCardShape else RoundedCornerShape(18.dp)
        clip = false
    }.graphicsLayer {
        rotationX = if (animate && splitShadow) rx.value else 0f
        rotationY = if (animate && splitShadow) ry.value else 0f
        cameraDistance = (if (tuned && variant == ShortcutTouchVariant.FingerTilt) 160f
            else if (tuned) 800f * (118f / 190f) else 16f).dp.toPx()
        clip = false
    }
    val directJellyLayer = Modifier.graphicsLayer {
        translationX = travelX()
        translationY = travelY() + if (animate && frozenPose == null && variant == ShortcutTouchVariant.FingerTilt)
            -FINGER_TILT_REST_FLOAT_DP.dp.toPx() else 0f
        rotationX = visiblePitch(); rotationY = visibleRoll()
        // Keep the measured card bounds stable; the rotations and shader normals carry the tilt.
        scaleX = 1f
        scaleY = 1f
        cameraDistance = 100.dp.toPx(); shadowElevation = 0f; clip = false
    }.onGloballyPositioned { input.planeCoordinates = it }
    val activeVisualModifier = if (jelly && frozenPose == null) directJellyLayer else visualModifier
    val contentPerspectiveModifier = if (variant == ShortcutTouchVariant.FingerTilt && enabled) {
        Modifier.graphicsLayer {
            // Use the last live contact while the root plane springs back. The nested projection
            // inherits the same spring values as the glass, so it never introduces a second clock.
            val contactX = if (frozenPose != null && !input.pressed) {
                (-frozenPose.rotationY / glassMotion.effectiveMaxTiltDegrees().coerceAtLeast(.001f)).coerceIn(-1f, 1f)
            } else input.tiltX
            val contactY = if (frozenPose != null && !input.pressed) {
                (frozenPose.rotationX / glassMotion.effectiveMaxTiltDegrees().coerceAtLeast(.001f)).coerceIn(-1f, 1f)
            } else input.tiltY
            val projection = shortcutContentPerspectivePose(contactX, contactY, rx.value, ry.value)
            rotationX = projection.rotationX
            rotationY = projection.rotationY
            transformOrigin = TransformOrigin(projection.pivotX, projection.pivotY)
            cameraDistance = 100.dp.toPx()
            clip = false
        }
    } else Modifier
    return ShortcutTouchMotion(gestureModifier, activeVisualModifier, {

        scale.value; dx.value; dy.value; rx.value; ry.value
        input.dragX; input.dragY; dragX.value; dragY.value
        ShortcutRefraction(visibleLift(),
            if (animate || contactFollow) input.x else 0f, if (animate || contactFollow) input.y else 0f,
            frostLift = if (contactFollow) SHORTCUT_CONTACT_REST_SOFTNESS else if (animate) lift.value.coerceIn(0f, 1f) else 0f,
            contact = if (contactFollow) contactValue() else 0f,
            contactFollow = contactFollow,
            depression = if (variant == ShortcutTouchVariant.FingerTilt) visibleLift() else 0f)
    }, contentPerspectiveModifier)
}
