package com.github.mihomo.android.data

object GlassEffect {
    const val DEFAULT_INTENSITY = 0.5f

    fun normalize(value: Float): Float =
        if (value.isNaN() || value.isInfinite()) DEFAULT_INTENSITY else value.coerceIn(0f, 1f)
}
