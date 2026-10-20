package com.github.mihomo.android.ui.components

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
    // Keep the last contact point while the lift springs back. Resetting x/y on release
    // would move the shader's lens target to the card centre in the very same frame.
    fun reset() { pressed = false }
}

internal class ShortcutTouchMotion(val modifier: Modifier, val refraction: () -> ShortcutRefraction)

/** One finite gesture animation drives slab and backdrop optics; no idle clock or capture. */
@Composable
internal fun rememberShortcutTouchMotion(
    variant: ShortcutTouchVariant,
    enabled: Boolean,
    frozenPose: ShortcutTouchPose? = null
): ShortcutTouchMotion {
    val tuned = LocalShortcutTunedMaterialEnabled.current
    val input = remember(variant, enabled, tuned) { TouchInput() }
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
    val modifier = Modifier.pointerInput(input, enabled) {
        if (!enabled) return@pointerInput
        try {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                input.pressed = true
                fun update(position: Offset) {
                    input.x = ((position.x / size.width.coerceAtLeast(1)) * 2f - 1f).coerceIn(-1f, 1f)
                    input.y = ((position.y / size.height.coerceAtLeast(1)) * 2f - 1f).coerceIn(-1f, 1f)
                }
                update(down.position)
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (event.changes.count { it.pressed } > 1) break
                        if (change.previousPressed && change.pressed && change.isConsumed) break
                        if (change.position.x !in 0f..size.width.toFloat() || change.position.y !in 0f..size.height.toFloat()) break
                        if (!change.pressed) break
                        update(change.position)
                    }
                } finally { input.reset() }
            }
        } finally { input.reset() }
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
        translationX = (if (animate) dx.value else 0f).dp.toPx()
        translationY = (when { !animate -> 0f; immediate -> dy.value.coerceAtMost(-maximumLift * .18f); else -> dy.value }).dp.toPx()
        rotationX = if (animate) rx.value else 0f
        rotationY = if (animate) ry.value else 0f
        cameraDistance = (if (tuned) 800f * (118f / 190f) else 16f).dp.toPx()
        shadowElevation = (visibleLift() * if (tuned && variant == ShortcutTouchVariant.FingerTilt) 12f * (118f / 190f) else maximumLift).dp.toPx()
        shape = if (tuned) ShortcutTunedCardShape else RoundedCornerShape(18.dp)
        clip = false
    }
    return ShortcutTouchMotion(modifier) {
        // Shader touch uniforms are normalized card-local coordinates, not tilt angles.
        // Feed the actual finger position so every press location produces a consistent lens.
        // Keep that point through release while lift animates down; reset() only clears pressed.
        ShortcutRefraction(visibleLift(), if (animate) input.x else 0f, if (animate) input.y else 0f,
            // Contact starts clear: unlike the bevel kick, frost follows actual separation.
            frostLift = if (animate) lift.value.coerceIn(0f, 1f) else 0f)
    }
}
