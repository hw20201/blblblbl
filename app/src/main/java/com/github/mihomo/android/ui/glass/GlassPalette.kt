package com.github.mihomo.android.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * A colour stop in the backdrop field.
 *
 * Positions are fractions of the canvas, so a palette composed on a 412dp phone keeps its balance
 * on tablets and foldables. [radius] is also a fraction of the diagonal, not a pixel value.
 */
@Immutable
data class GlassBlob(
    val color: Color,
    val x: Float,
    val y: Float,
    val radius: Float
)

/**
 * Everything needed to render one colour scheme: the backdrop field, the panel material and the
 * type colours. Keeping them in one object is deliberate — a palette that only changes the accent
 * but leaves the tint alone looks broken, because glass derives its colour from what is behind it.
 */
@Immutable
data class GlassPaletteSpec(
    val key: String,
    val labelZh: String,
    val labelEn: String,
    val isDark: Boolean,
    /** Brand colour: the NEXUS wordmark, the active tab, accent strokes. */
    val accent: Color,
    /** Page background behind the colour field. */
    val base: Color,
    /** Soft veil drawn over the field: lightens for light palettes, darkens for dark ones. */
    val veil: Color,
    val blobs: List<GlassBlob>,
    /** Panel fill, top-left to bottom-right. */
    val panelStart: Color,
    val panelEnd: Color,
    /** Panel fill when an accent is blended underneath. */
    val panelAccent: Color,
    val edge: Color,
    val rimTop: Color,
    val rimBottom: Color,
    /** Diagonal sheen strength. Light palettes need more to separate from the backdrop. */
    val sheen: Float,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    /** Colour used for the swatch preview in Settings. */
    val swatch: List<Color>,
    /** Contrast-limited control color; the original chosen RGB remains in accent/key. */
    val uiAccent: Color = accent
)

/**
 * Built-in palettes.
 *
 * Ordered light-to-dark on purpose: the picker renders them in this order so the list reads as a
 * scale rather than a random set of chips.
 */
object GlassPalettes {

    val WHITE = GlassPaletteSpec(
        key = "white",
        labelZh = "白玻璃",
        labelEn = "Porcelain",
        isDark = false,
        accent = Color(0xFFE85D04),
        base = Color(0xFFF7F9FD),
        veil = Color(0x6BFFFFFF),
        blobs = listOf(
            GlassBlob(Color(0x57B4D2FF), 0.10f, -0.02f, 0.46f),
            GlassBlob(Color(0x40DCC8FF), 0.96f, 0.18f, 0.42f),
            GlassBlob(Color(0x3DFFD9C2), -0.04f, 0.44f, 0.42f),
            GlassBlob(Color(0x33FFC9DE), 0.98f, 1.02f, 0.44f)
        ),
        panelStart = Color(0xB8FFFFFF),
        panelEnd = Color(0x6BFFFFFF),
        panelAccent = Color(0x8AFFFFFF),
        edge = Color(0xF2FFFFFF),
        rimTop = Color(0xFFFFFFFF),
        rimBottom = Color(0x3370A0FF),
        sheen = 0.34f,
        textPrimary = Color(0xFF12151C),
        textSecondary = Color(0x9912151C),
        textMuted = Color(0x6612151C),
        swatch = listOf(Color(0xFFFFFFFF), Color(0xFFBFD4FF), Color(0xFFF7F9FD))
    )

    val BLUE = GlassPaletteSpec(
        key = "blue",
        labelZh = "浅蓝",
        labelEn = "Azure",
        isDark = false,
        accent = Color(0xFF2563EB),
        base = Color(0xFFEAF1FF),
        veil = Color(0x5CFFFFFF),
        blobs = listOf(
            GlassBlob(Color(0x669EC0FF), 0.10f, -0.02f, 0.48f),
            GlassBlob(Color(0x5286AEFF), 0.96f, 0.16f, 0.40f),
            GlassBlob(Color(0x47C4D8FF), -0.04f, 0.42f, 0.40f),
            GlassBlob(Color(0x40B9CEF5), 0.98f, 1.02f, 0.44f)
        ),
        panelStart = Color(0xADFFFFFF),
        panelEnd = Color(0x66F0F6FF),
        panelAccent = Color(0x8AF2F8FF),
        edge = Color(0xF2FFFFFF),
        rimTop = Color(0xFFFFFFFF),
        rimBottom = Color(0x3360A5FF),
        sheen = 0.32f,
        textPrimary = Color(0xFF101828),
        textSecondary = Color(0x99101828),
        textMuted = Color(0x66101828),
        swatch = listOf(Color(0xFFDDE8FA), Color(0xFF86AEFF), Color(0xFF2563EB))
    )

    val CREAM = GlassPaletteSpec(
        key = "cream",
        labelZh = "奶白",
        labelEn = "Cream",
        isDark = false,
        accent = Color(0xFFC2410C),
        base = Color(0xFFFDF8F3),
        veil = Color(0x70FFFFFF),
        blobs = listOf(
            GlassBlob(Color(0x61FFD7B8), 0.10f, -0.02f, 0.46f),
            GlassBlob(Color(0x4DFFC6C2), 0.96f, 0.16f, 0.40f),
            GlassBlob(Color(0x52FFE4C9), -0.04f, 0.42f, 0.40f),
            GlassBlob(Color(0x40F3D9E8), 0.98f, 1.02f, 0.44f)
        ),
        panelStart = Color(0xBDFFFDF9),
        panelEnd = Color(0x70FFF8F0),
        panelAccent = Color(0x8AFFFAF4),
        edge = Color(0xF2FFFFFF),
        rimTop = Color(0xFFFFFFFF),
        rimBottom = Color(0x33FFB27A),
        sheen = 0.36f,
        textPrimary = Color(0xFF1C1410),
        textSecondary = Color(0x991C1410),
        textMuted = Color(0x661C1410),
        swatch = listOf(Color(0xFFFFF8F0), Color(0xFFFFD7B8), Color(0xFFC2410C))
    )

    val MINT = GlassPaletteSpec(
        key = "mint",
        labelZh = "薄荷",
        labelEn = "Mint",
        isDark = false,
        accent = Color(0xFF0F766E),
        base = Color(0xFFEFFAF7),
        veil = Color(0x66FFFFFF),
        blobs = listOf(
            GlassBlob(Color(0x5CA7F3E0), 0.10f, -0.02f, 0.46f),
            GlassBlob(Color(0x4A9FDCFF), 0.96f, 0.16f, 0.40f),
            GlassBlob(Color(0x47D8F5C0), -0.04f, 0.42f, 0.40f),
            GlassBlob(Color(0x40C9E8D6), 0.98f, 1.02f, 0.44f)
        ),
        panelStart = Color(0xB3FFFFFF),
        panelEnd = Color(0x66F2FDFA),
        panelAccent = Color(0x8AF4FEFA),
        edge = Color(0xF2FFFFFF),
        rimTop = Color(0xFFFFFFFF),
        rimBottom = Color(0x3340C8B0),
        sheen = 0.32f,
        textPrimary = Color(0xFF0C1A18),
        textSecondary = Color(0x990C1A18),
        textMuted = Color(0x660C1A18),
        swatch = listOf(Color(0xFFF0FDFA), Color(0xFFA7F3E0), Color(0xFF0F766E))
    )

    val LAVENDER = GlassPaletteSpec(
        key = "lavender",
        labelZh = "薰衣草",
        labelEn = "Lavender",
        isDark = false,
        accent = Color(0xFF7C3AED),
        base = Color(0xFFF6F3FE),
        veil = Color(0x66FFFFFF),
        blobs = listOf(
            GlassBlob(Color(0x5FC4B5FF), 0.10f, -0.02f, 0.46f),
            GlassBlob(Color(0x4DE0C4FF), 0.96f, 0.16f, 0.40f),
            GlassBlob(Color(0x47FFC9E4), -0.04f, 0.42f, 0.40f),
            GlassBlob(Color(0x40D9CCF7), 0.98f, 1.02f, 0.44f)
        ),
        panelStart = Color(0xB3FFFFFF),
        panelEnd = Color(0x66F9F6FF),
        panelAccent = Color(0x8AFBF8FF),
        edge = Color(0xF2FFFFFF),
        rimTop = Color(0xFFFFFFFF),
        rimBottom = Color(0x337C3AED),
        sheen = 0.32f,
        textPrimary = Color(0xFF17111F),
        textSecondary = Color(0x9917111F),
        textMuted = Color(0x6617111F),
        swatch = listOf(Color(0xFFF6F3FE), Color(0xFFC4B5FF), Color(0xFF7C3AED))
    )

    val MIDNIGHT = GlassPaletteSpec(
        key = "midnight",
        labelZh = "午夜",
        labelEn = "Midnight",
        isDark = true,
        accent = Color(0xFFFF7A33),
        base = Color(0xFF05060A),
        veil = Color(0x4D020307),
        blobs = listOf(
            GlassBlob(Color(0xD9FF6B00), 0.16f, -0.06f, 0.42f),
            GlassBlob(Color(0x754A69FF), 0.92f, 0.30f, 0.36f),
            GlassBlob(Color(0x4D9650FF), 0.02f, 0.66f, 0.40f),
            GlassBlob(Color(0x4700BEAA), 0.98f, 0.70f, 0.38f)
        ),
        panelStart = Color(0x2EFFFFFF),
        panelEnd = Color(0x0FFFFFFF),
        panelAccent = Color(0x26FFFFFF),
        edge = Color(0x24FFFFFF),
        rimTop = Color(0x8FFFFFFF),
        rimBottom = Color(0x3380B4FF),
        sheen = 0.09f,
        textPrimary = Color(0xFFF5F6F8),
        textSecondary = Color(0x99EBF0FF),
        textMuted = Color(0x66EBF0FF),
        swatch = listOf(Color(0xFF12161F), Color(0xFF4A69FF), Color(0xFFFF6B00))
    )

    /** Display order: light to dark. */
    val all: List<GlassPaletteSpec> = listOf(WHITE, BLUE, CREAM, MINT, LAVENDER, MIDNIGHT)

    private val customKey = Regex("custom:([0-9a-fA-F]{6})")

    /** Strict, stable key format lets any RGB swatch survive settings persistence. */
    fun keyForColor(color: Color): String =
        "custom:%06X".format(color.toArgb() and 0xFFFFFF)

    fun byKey(key: String?): GlassPaletteSpec {
        all.firstOrNull { it.key == key }?.let { return it }
        val match = key?.let(customKey::matchEntire) ?: return CREAM
        val rgb = match.groupValues[1].toLongOrNull(16)?.toInt() ?: return CREAM
        return custom(Color(0xFF000000.toInt() or rgb), key!!)
    }

    private fun custom(accent: Color, key: String): GlassPaletteSpec {
        // Gently mix the selected hue into a cool ice-blue/lavender mesh, avoiding neon saturation.
        val softened = androidx.compose.ui.graphics.lerp(accent, Color(0xFFB9C8F5), 0.48f)
        val pale = androidx.compose.ui.graphics.lerp(softened, Color.White, 0.48f)
        val warm = androidx.compose.ui.graphics.lerp(pale, Color(0xFFE2D9F6), 0.42f)
        val base = androidx.compose.ui.graphics.lerp(Color(0xFFF7F9FD), pale, 0.12f)
        val panel = androidx.compose.ui.graphics.lerp(Color.White, pale, 0.10f)
        val blobs = listOf(
            GlassBlob(softened.copy(alpha = 0.34f), 0.10f, -0.02f, 0.46f),
            GlassBlob(warm.copy(alpha = 0.30f), 0.96f, 0.16f, 0.40f),
            GlassBlob(androidx.compose.ui.graphics.lerp(pale, Color(0xFFD5E8FA), 0.35f).copy(alpha = 0.30f), -0.04f, 0.42f, 0.40f),
            GlassBlob(androidx.compose.ui.graphics.lerp(warm, pale, 0.5f).copy(alpha = 0.25f), 0.98f, 1.02f, 0.44f)
        )
        return GlassPaletteSpec(
            key = key, labelZh = "自定义颜色", labelEn = "Custom color", isDark = false,
            accent = accent, base = base, veil = Color(0x66FFFFFF), blobs = blobs,
            panelStart = Color(0xB8FFFFFF), panelEnd = androidx.compose.ui.graphics.lerp(Color(0x66FFFFFF), panel, 0.15f),
            panelAccent = Color(0x8AFFFFFF), edge = Color(0xF2FFFFFF), rimTop = Color.White,
            rimBottom = softened.copy(alpha = 0.20f), sheen = 0.34f,
            textPrimary = Color(0xFF12151C), textSecondary = Color(0x9912151C), textMuted = Color(0x6612151C),
            swatch = listOf(base, pale, accent), uiAccent = balancedControlAccent(accent)
        )
    }
}

/** Keep black/white/pastel swatches from making controls disappear in either brightness mode. */
private fun balancedControlAccent(color: Color): Color {
    val luminance = color.luminance()
    if (luminance in .16f.. .24f) return color
    val increasing = luminance < .16f
    val end = if (increasing) Color.White else Color.Black
    var lo = 0f
    var hi = 1f
    repeat(12) {
        val mid = (lo + hi) / 2f
        val value = lerp(color, end, mid).luminance()
        if ((value < .20f) == increasing) lo = mid else hi = mid
    }
    return lerp(color, end, (lo + hi) / 2f)
}
