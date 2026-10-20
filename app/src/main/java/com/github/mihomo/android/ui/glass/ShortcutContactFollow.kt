package com.github.mihomo.android.ui.glass

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.hypot
import kotlin.math.min

/** Local review switch, not a persisted preference or a change to the original material settings. */
internal val LocalShortcutContactFollowEnabled = staticCompositionLocalOf { true }
internal const val SHORTCUT_CONTACT_REST_SOFTNESS = .32f
internal const val SHORTCUT_CONTACT_REBOUND = .78f

internal fun shortcutContactRadiusDp(width: Float, height: Float): Float =
    (min(width, height) * .42f).coerceIn(12f, 64f)

/** Compact C1 feather: a real clear core, round transition, exactly no change outside the footprint. */
internal fun shortcutContactWeight(
    x: Float, y: Float, width: Float, height: Float, touchX: Float, touchY: Float, contact: Float
): Float {
    if (listOf(x, y, width, height, touchX, touchY, contact).any { !it.isFinite() } || width <= 0f || height <= 0f) return 0f
    val cx = (touchX.coerceIn(-1f, 1f) * .5f + .5f) * width
    val cy = (touchY.coerceIn(-1f, 1f) * .5f + .5f) * height
    val distance = hypot(x - cx, y - cy) / shortcutContactRadiusDp(width, height)
    val t = ((distance - .35f) / .65f).coerceIn(0f, 1f)
    return contact.coerceIn(0f, 1f) * (1f - t * t * (3f - 2f * t))
}
