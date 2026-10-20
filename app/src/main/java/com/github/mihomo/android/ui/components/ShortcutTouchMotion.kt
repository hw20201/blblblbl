package com.github.mihomo.android.ui.components

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.ui.glass.ShortcutRefraction
import com.github.mihomo.android.ui.glass.LocalShortcutTunedMaterialEnabled
import com.github.mihomo.android.ui.glass.ShortcutTunedCardShape
import com.github.mihomo.android.ui.glass.ShortcutFollowupCardShape
import com.github.mihomo.android.ui.glass.LocalShortcutOpticalFollowupEnabled
import kotlin.math.abs

/** Persisted comparison choices; names are stable preference values. */
enum class ShortcutTouchVariant { GentleFloat, FingerTilt, BuoyantSpring }

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
            scale = 0.985f, offsetYDp = -3f, rotationX = -ny * 1.5f, rotationY = nx * 1.5f)
        ShortcutTouchVariant.FingerTilt -> if (tuned) ShortcutTouchPose(
            // Lab B lengths use the 118dp / 190 CSS-px card-height ratio.
            scale = 1.004f, offsetYDp = -2f * (118f / 190f),
            rotationX = -ny * 9f, rotationY = nx * 9f
        ) else ShortcutTouchPose(
            scale = 0.975f, offsetXDp = nx * 2f, offsetYDp = -4f + ny * 2f,
            rotationX = -ny * 4f, rotationY = nx * 4f)
        ShortcutTouchVariant.BuoyantSpring -> ShortcutTouchPose(
            scale = 0.965f, offsetYDp = -5f, rotationX = -ny * 2f, rotationY = nx * 2f)
    }
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
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var dragging by mutableStateOf(false)
    var dragX by mutableFloatStateOf(0f)
    var dragY by mutableFloatStateOf(0f)
    // Keep the last contact point while the lift springs back. Resetting x/y on release
    // would move the shader's lens target to the card centre in the very same frame.
    fun reset() { pressed = false; dragging = false; dragX = 0f; dragY = 0f }
}

internal class ShortcutTouchMotion(val modifier: Modifier, val refraction: () -> ShortcutRefraction)

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
    val splitShadow = tuned && followup
    val input = remember(variant, enabled, tuned) { TouchInput() }
    val pickupCallback by rememberUpdatedState(onPickupChanged)
    fun currentTarget(x: Float, y: Float) = shortcutTouchTarget(variant, x, y, tuned)
    val animate = (LocalShortcutMotionEnabled.current ?: true) && enabled
    val damping = when (variant) {
        ShortcutTouchVariant.GentleFloat -> 0.88f
        ShortcutTouchVariant.FingerTilt -> if (tuned) 0.78f else 0.76f
        ShortcutTouchVariant.BuoyantSpring -> 0.55f
    }
    val target = when {
        !animate -> ShortcutTouchPose()
        frozenPose != null -> frozenPose
        input.pressed -> currentTarget(input.x, input.y)
        else -> ShortcutTouchPose()
    }
    // Compose's animation clock applies the system duration scale, including slow scales.
    val spec = if (input.pressed) tween<Float>(85) else spring(dampingRatio = damping, stiffness = Spring.StiffnessMedium)
    val scale = animateFloatAsState(target.scale, spec, label = "shortcut_scale")
    val dx = animateFloatAsState(target.offsetXDp, spec, label = "shortcut_x")
    val dy = animateFloatAsState(target.offsetYDp, spec, label = "shortcut_y")
    val rx = animateFloatAsState(target.rotationX, spec, label = "shortcut_rx")
    val ry = animateFloatAsState(target.rotationY, spec, label = "shortcut_ry")
    val liftTarget = when {
        !animate -> 0f
        frozenPose != null -> (abs(1f - frozenPose.scale) / abs(1f - currentTarget(0f, 0f).scale).coerceAtLeast(.0001f)).coerceIn(0f, 1f)
        input.pressed -> 1f
        else -> 0f
    }
    val lift = animateFloatAsState(liftTarget, spec, label = "shortcut_lift")
    val maximumLift = when (variant) {
        ShortcutTouchVariant.GentleFloat -> 3f
        ShortcutTouchVariant.FingerTilt -> if (tuned) 2f * (118f / 190f) else 4f
        ShortcutTouchVariant.BuoyantSpring -> 5f
    }
    fun visibleLift(): Float = when {
        !animate -> 0f
        // A short tap may end before the 85 ms lift tween advances much. Keep a visible
        // minimum so the optical state is perceptible from the first rendered press frame.
        input.pressed && frozenPose == null -> lift.value.coerceAtLeast(.35f)
        else -> lift.value.coerceIn(0f, 1f)
    }
    // Direct manipulation tracks immediately; only release uses the selected rebound.
    val dragSpec = if (input.dragging || !animate) snap<Float>() else spring(dampingRatio = damping, stiffness = Spring.StiffnessMedium)
    val dragX = animateFloatAsState(if (input.dragging) input.dragX else 0f, dragSpec, label = "shortcut_drag_x")
    val dragY = animateFloatAsState(if (input.dragging) input.dragY else 0f, dragSpec, label = "shortcut_drag_y")
    val modifier = Modifier.pointerInput(input, enabled) {
        if (!enabled) return@pointerInput
        try {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.isConsumed) return@awaitEachGesture
                input.pressed = true
                pickupCallback(true)
                fun update(position: Offset) {
                    // The gesture anchor stays in the original slot, but the optical contact
                    // belongs to the translated plane, not that stationary slot.
                    val local = position - Offset(if (input.dragging) input.dragX else dragX.value,
                        if (input.dragging) input.dragY else dragY.value)
                    input.x = ((local.x / size.width.coerceAtLeast(1)) * 2f - 1f).coerceIn(-1f, 1f)
                    input.y = ((local.y / size.height.coerceAtLeast(1)) * 2f - 1f).coerceIn(-1f, 1f)
                }
                update(down.position)
                try {
                    val maxDragX = 24.dp.toPx()
                    val maxDragY = 18.dp.toPx()
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
                            if (input.dragging) change.consume()
                            break
                        }
                        if (change.isConsumed) break
                        val delta = change.position - down.position
                        if (!input.dragging && delta.getDistance() > viewConfiguration.touchSlop) input.dragging = true
                        if (input.dragging) {
                            // This pointer node is outside the visual graphicsLayer: stable anchor,
                            // no feedback from the moved card, no exit when the finger leaves it.
                            input.dragX = delta.x.coerceIn(-maxDragX, maxDragX)
                            input.dragY = delta.y.coerceIn(-maxDragY, maxDragY)
                            change.consume()
                        }
                        update(change.position)
                    }
                } finally { input.reset(); pickupCallback(false) }
            }
        } finally { input.reset(); pickupCallback(false) }
    }.graphicsLayer {
        val immediate = animate && input.pressed && frozenPose == null
        val targetScale = currentTarget(0f, 0f).scale
        val kickScale = 1f + (targetScale - 1f) * .18f
        val visibleScale = when {
            !animate -> 1f
            immediate && targetScale < 1f -> scale.value.coerceAtMost(kickScale)
            immediate -> scale.value.coerceAtLeast(kickScale)
            else -> scale.value
        }
        scaleX = visibleScale; scaleY = visibleScale
        translationX = (if (animate) dx.value else 0f).dp.toPx() + if (input.dragging) input.dragX else dragX.value
        translationY = (when { !animate -> 0f; immediate -> dy.value.coerceAtMost(-maximumLift * .18f); else -> dy.value }).dp.toPx() + if (input.dragging) input.dragY else dragY.value
        rotationX = if (animate && !splitShadow) rx.value else 0f
        rotationY = if (animate && !splitShadow) ry.value else 0f
        cameraDistance = (if (tuned) 800f * (118f / 190f) else 16f).dp.toPx()
        // Keep the ground shadow flat. Native round-rect outlines otherwise use
        // tilted vertex Z as extra caster height, producing very long penumbrae.
        shadowElevation = (visibleLift() * if (tuned && variant == ShortcutTouchVariant.FingerTilt) 12f * (118f / 190f) else maximumLift).dp.toPx()
        shape = if (tuned && followup) ShortcutFollowupCardShape else if (tuned) ShortcutTunedCardShape else RoundedCornerShape(18.dp)
        clip = false
    }.graphicsLayer {
        rotationX = if (animate && splitShadow) rx.value else 0f
        rotationY = if (animate && splitShadow) ry.value else 0f
        cameraDistance = (if (tuned) 800f * (118f / 190f) else 16f).dp.toPx()
        clip = false
    }
    return ShortcutTouchMotion(modifier) {
        // Shader touch uniforms are normalized card-local coordinates, not tilt angles.
        // Feed the actual finger position so every press location produces a consistent lens.
        // Keep that point through release while lift animates down; reset() only clears pressed.
        // The backdrop draw observes the actual plane pose too, including when the finger
        // is beyond +/-1 and touch uniforms stop changing. This invalidates world anchoring.
        scale.value; dx.value; dy.value; rx.value; ry.value
        input.dragX; input.dragY; dragX.value; dragY.value
        ShortcutRefraction(visibleLift(), if (animate) input.x else 0f, if (animate) input.y else 0f,
            // Contact starts clear: unlike the bevel kick, frost follows actual separation.
            frostLift = if (animate) lift.value.coerceIn(0f, 1f) else 0f)
    }
}
