package com.github.arca.android.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.github.arca.android.ui.components.LocalShortcutMotionEnabled
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/** A brief touch highlight followed by one simple outward-spreading ripple. */
internal fun Modifier.glassTouchRipple(): Modifier = composed {
    val motionEnabled = LocalShortcutMotionEnabled.current ?: true
    if (!motionEnabled) return@composed this

    val rippleProgress = remember { Animatable(1f) }
    val touchHighlight = remember { Animatable(0f) }
    var origin by remember { mutableStateOf(Offset.Unspecified) }
    var pressed by remember { mutableStateOf(false) }
    var gestureCompleted by remember { mutableStateOf(false) }
    var gestureGeneration by remember { mutableStateOf(0) }

    LaunchedEffect(gestureGeneration, pressed, gestureCompleted) {
        when {
            pressed -> {
                rippleProgress.snapTo(0f)
                touchHighlight.snapTo(0f)
                touchHighlight.animateTo(1f, tween(durationMillis = 85))
            }
            gestureCompleted -> {
                // Keep a quick tap visible even if release wins the press animation race.
                if (rippleProgress.value >= 0.999f) rippleProgress.snapTo(0f)
                if (touchHighlight.value <= 0.001f) touchHighlight.snapTo(1f)
                coroutineScope {
                    launch { touchHighlight.animateTo(0f, tween(durationMillis = 170)) }
                    launch {
                        rippleProgress.animateTo(
                            1f,
                            tween(durationMillis = 620, easing = LinearEasing)
                        )
                    }
                }
                gestureCompleted = false
            }
            else -> {
                touchHighlight.snapTo(0f)
                rippleProgress.snapTo(1f)
            }
        }
    }

    this
        .pointerInput(motionEnabled) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                origin = down.position
                pressed = true
                gestureCompleted = false
                gestureGeneration += 1
                val released = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                pressed = false
                gestureCompleted = released != null
            }
        }
        .drawWithContent {
            val center = origin
            if (center != Offset.Unspecified) {
                val highlight = touchHighlight.value.coerceIn(0f, 1f)
                if (pressed || highlight > 0.001f) {
                    val highlightRadius = 34.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to Color.White.copy(alpha = 0.18f * highlight),
                            0.62f to Color.White.copy(alpha = 0.06f * highlight),
                            1f to Color.Transparent,
                            center = center,
                            radius = highlightRadius
                        ),
                        radius = highlightRadius,
                        center = center
                    )
                }

                val progress = rippleProgress.value.coerceIn(0f, 1f)
                if (!pressed && progress < 0.999f) {
                    val farX = max(center.x, size.width - center.x)
                    val farY = max(center.y, size.height - center.y)
                    val maxRadius = hypot(farX, farY)
                    val radius = maxRadius * progress
                    val fade = 1f - progress
                    drawCircle(
                        color = Color.White.copy(alpha = 0.22f * fade),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.25.dp.toPx())
                    )
                }
            }
            drawContent()
        }
}
