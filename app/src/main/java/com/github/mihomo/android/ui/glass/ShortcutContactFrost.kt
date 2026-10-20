package com.github.mihomo.android.ui.glass

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.roundToInt

/** Contact is optically clear; separation diffuses only the sampled backdrop, up to 8dp.
 * Half-dp steps bound effect churn during the existing finite gesture animation.
 */
internal fun shortcutFrostRadiusDp(lift: Float, maximumDp: Float = 8f): Float {
    val bounded = if (lift.isFinite()) lift.coerceIn(0f, 1f) else 0f
    val eased = bounded * bounded * (3f - 2f * bounded)
    return (eased * maximumDp.coerceIn(0f, 8f) * 2f).roundToInt() * .5f
}

/** One-entry per-card cache. No captures, bitmap allocations, timers or idle filtering.
 * The shader is the OUTER effect: its bevel stays defined above the diffuse input.
 */
@RequiresApi(31)
internal class ShortcutContactFrost {
    private var radiusPx = -1f
    private var blur: RenderEffect? = null
    private var lastOptics: RenderEffect? = null
    private var result: androidx.compose.ui.graphics.RenderEffect? = null

    fun update(lift: Float, density: Float, optics: RenderEffect?, maximumDp: Float = 8f): androidx.compose.ui.graphics.RenderEffect? {
        val nextRadius = shortcutFrostRadiusDp(lift, maximumDp) * density
        if (nextRadius == radiusPx && optics === lastOptics) return result
        if (nextRadius != radiusPx) {
            blur = if (nextRadius > 0f) RenderEffect.createBlurEffect(nextRadius, nextRadius, Shader.TileMode.CLAMP) else null
        }
        val filter = blur
        val native = when {
            filter == null -> optics
            optics == null -> filter
            else -> RenderEffect.createChainEffect(optics, filter)
        }
        result = native?.asComposeRenderEffect()
        radiusPx = nextRadius
        lastOptics = optics
        return result
    }
}
