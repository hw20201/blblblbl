package com.github.mihomo.android.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

internal const val SHORTCUT_REST_ELEVATION_DP = 3f
internal const val SHORTCUT_PRESS_ELEVATION_DP = 5f
internal const val SHORTCUT_REORDER_ELEVATION_DP = 8f
internal const val SHORTCUT_SUSPENDED_MAX_TILT = 6f

/** More response near the centre, not more extreme corner perspective. No dead zone/jump. */
internal fun shortcutSuspendedTilt(x: Float, y: Float): ShortcutTouchPose {
    fun weight(value: Float): Float {
        val n = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
        return n * (1.8f - .8f * kotlin.math.abs(n))
    }
    val wx = weight(x)
    val wy = weight(y)
    val length = kotlin.math.sqrt(wx * wx + wy * wy)
    val divisor = maxOf(1f, length)
    return ShortcutTouchPose(rotationX = -wy / divisor * SHORTCUT_SUSPENDED_MAX_TILT,
        rotationY = wx / divisor * SHORTCUT_SUSPENDED_MAX_TILT)
}

/** Size-aware camera in dp; prevents a wide card's near edge from ballooning. */
internal fun shortcutSuspendedCameraDp(widthDp: Float): Float =
    if (widthDp.isFinite()) maxOf(160f, widthDp * .84f) else 160f

internal fun shortcutSuspensionElevationDp(enabled: Boolean, reordering: Boolean, lift: Float): Float = when {
    !enabled -> 0f
    reordering -> SHORTCUT_REORDER_ELEVATION_DP
    else -> SHORTCUT_REST_ELEVATION_DP +
        (SHORTCUT_PRESS_ELEVATION_DP - SHORTCUT_REST_ELEVATION_DP) *
        (if (lift.isFinite()) lift.coerceIn(0f, 1f) else 0f)
}

/** One Android outline caster, INSIDE the moving plane and OUTSIDE the material clip.
 * No bitmap, blur pass, filled ground duplicate, 24-stroke approximation or idle animator.
 * The shape must be the very same outline used by GlassSurface's material clip.
 */
internal fun Modifier.shortcutNativeSuspension(
    shape: Shape,
    dark: Boolean,
    reordering: Boolean,
    enabled: Boolean,
    lift: () -> Float
): Modifier = graphicsLayer {
    this.shape = shape
    clip = false // Do not crop the shadow, remove badge, or existing refractive overscan.
    shadowElevation = shortcutSuspensionElevationDp(enabled, reordering, lift()).dp.toPx()
    ambientShadowColor = Color.Black.copy(alpha = if (dark) .16f else .10f)
    spotShadowColor = Color.Black.copy(alpha = if (dark) .28f else .18f)
}
