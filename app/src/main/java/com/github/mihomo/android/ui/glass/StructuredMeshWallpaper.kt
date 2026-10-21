package com.github.mihomo.android.ui.glass

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Comparison fixture only; production uses the structured field. No diagnostic pattern. */
internal val LocalStructuredMeshEnabled = staticCompositionLocalOf { true }

/** Stratified colour cells prevent all independently moving blobs drifting into one corner. */
internal fun structuredMeshCells(palette: GlassPaletteSpec, baseBlobs: List<GlassBlob>): List<GlassBlob> {
    if (baseBlobs.isEmpty()) return emptyList()
    val custom = palette.key.startsWith("custom:")
    // Cool-only default field: don't import orange/green preset accents into glass.
    // Custom palettes remain user-owned; dark cells use restrained blue/violet/cyan.
    val darkColours = listOf(0xFF203652, 0xFF303052, 0xFF19414A,
        0xFF283E60, 0xFF393054, 0xFF234653).map {
        GlassBlob(Color(it).copy(alpha = .72f), 0f, 0f, .34f)
    }
    val colours = when { custom -> baseBlobs; palette.isDark -> darkColours; else -> IceLavenderMesh }
    val contrast = when { custom -> 1f; palette.isDark -> 1f; else -> 1.28f }
    val alphaGain = when { custom -> 1f; palette.isDark -> 1f; else -> 1.12f }
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
