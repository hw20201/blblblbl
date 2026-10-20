package com.github.mihomo.android.ui.glass

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Comparison fixture only; production uses the structured field. No diagnostic pattern. */
internal val LocalStructuredMeshEnabled = staticCompositionLocalOf { true }

/** Stratified colour cells prevent all independently moving blobs drifting into one corner. */
internal fun structuredMeshCells(palette: GlassPaletteSpec, baseBlobs: List<GlassBlob>): List<GlassBlob> {
    if (baseBlobs.isEmpty()) return emptyList()
    val custom = palette.key.startsWith("custom:")
    val colours = if (palette.isDark || custom) baseBlobs else baseBlobs + palette.blobs.take(2)
    val contrast = when { custom -> 1f; palette.isDark -> 1.10f; else -> 1.45f }
    val alphaGain = when { custom -> 1f; palette.isDark -> 1.12f; else -> 1.35f }
    val base = if (palette.isDark || custom) palette.base else Color(0xFFECF3FF)
    return List(15) { index ->
        val row = index / 3
        val column = index % 3
        val source = colours[(row * 2 + column) % colours.size]
        fun channel(value: Float, background: Float) = (background + (value - background) * contrast).coerceIn(0f, 1f)
        val colour = Color(channel(source.color.red, base.red), channel(source.color.green, base.green),
            channel(source.color.blue, base.blue), (source.color.alpha * alphaGain).coerceAtMost(.90f))
        GlassBlob(colour, (column + .5f + if (row % 2 == 0) -.08f else .08f) / 3f,
            (row + .5f) / 5f, .36f + (index % 3) * .015f)
    }
}
