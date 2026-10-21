package com.github.mihomo.android.ui.glass

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

/** Directional surface cue, not a captured environment map or a second refraction pass.
 * It reads the ACTUAL visible angles so a stationary hold and release keep coherent lighting.
 */
internal fun shortcutSurfaceLightDirection(pitchDegrees: Float, rollDegrees: Float): Offset {
    fun angle(value: Float): Float = if (value.isFinite()) value.coerceIn(-6f, 6f) / 6f else 0f
    val x = -.35f + angle(rollDegrees) * .95f
    // Y never crosses zero: no normalisation singularity or light flip on release.
    val y = -.85f - angle(pitchDegrees) * .60f
    val length = sqrt(x * x + y * y)
    return if (length > .001f) Offset(x / length, y / length) else Offset(0f, -1f)
}
