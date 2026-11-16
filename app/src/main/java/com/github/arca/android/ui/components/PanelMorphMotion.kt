package com.github.arca.android.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf
import com.github.arca.android.data.PanelAnimationSpeed
import kotlin.math.abs
import kotlin.math.roundToInt

/** One timing policy for dashboard cards, settings rows and policy panels. */
internal val LocalPanelAnimationSpeed = compositionLocalOf { PanelAnimationSpeed.MEDIUM }

internal fun panelMorphAnimationSpec(
    speed: PanelAnimationSpeed,
    opening: Boolean,
    from: Float,
    linearEdgeMotion: Boolean = false
): TweenSpec<Float> {
    val target = if (opening) 1f else 0f
    // Reverse from the displayed position. A nearly closed panel should not take a full
    // closing duration to travel its remaining few pixels.
    val remaining = abs(target - from.coerceIn(0f, 1f))
    val fullDuration = if (opening) speed.openDurationMillis else speed.closeDurationMillis
    return tween(
        durationMillis = (fullDuration * remaining).roundToInt().coerceAtLeast(1),
        easing = if (linearEdgeMotion) LinearEasing else CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    )
}

/** The same short lift phase precedes size growth for both kinds of source control. */
internal fun panelMorphGrowth(progress: Float): Float =
    ((progress - MENU_LIFT_END) / (1f - MENU_LIFT_END)).coerceIn(0f, 1f)
