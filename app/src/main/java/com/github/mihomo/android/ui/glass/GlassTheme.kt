package com.github.mihomo.android.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.github.mihomo.android.ui.theme.DarkLoonThemeColors
import com.github.mihomo.android.ui.theme.LightLoonThemeColors
import com.github.mihomo.android.ui.theme.LocalLoonColors
import com.github.mihomo.android.ui.theme.LoonThemeColors

/**
 * Global switches for the glass surface family.
 *
 * [enabled] exists so the whole redesign can be rolled back in one line.
 * [palette] is Compose state on purpose: changing it from Settings has to recompose every screen
 * that draws glass, and threading the value through every call site would be noise.
 */
object GlassDefaults {
    var enabled by mutableStateOf(true)
    var palette by mutableStateOf(GlassPalettes.CREAM)
}

val LocalGlassPalette = staticCompositionLocalOf { GlassPalettes.CREAM }

/** Theme mode remains authoritative; a preset keeps its identity as brightness changes. */
fun resolveGlassPalette(selected: GlassPaletteSpec, isDark: Boolean): GlassPaletteSpec {
    if (!isDark) return selected.takeUnless { it.isDark } ?: GlassPalettes.CREAM
    if (selected.isDark) return selected

    val darkBase = when (selected.key) {
        "white" -> Color(0xFF171A21)
        "blue" -> Color(0xFF101A2B)
        "cream" -> Color(0xFF211A17)
        "mint" -> Color(0xFF10211F)
        "lavender" -> Color(0xFF1B1728)
        else -> Color(0xFF171A21)
    }
    return selected.copy(
        isDark = true,
        base = darkBase,
        veil = Color(0x66000000),
        blobs = selected.blobs.map { it.copy(color = it.color.copy(alpha = it.color.alpha * 0.58f)) },
        panelStart = Color(0xD022252D),
        panelEnd = Color(0xB0181B23),
        panelAccent = Color(0xC0252932),
        edge = Color(0x55FFFFFF),
        rimTop = Color(0x88FFFFFF),
        rimBottom = selected.accent.copy(alpha = 0.35f),
        sheen = 0.10f,
        textPrimary = Color(0xFFF5F6F8),
        textSecondary = Color(0xFFD0D4DD),
        textMuted = Color(0xFFAEB4C0),
        swatch = listOf(darkBase, selected.accent.copy(alpha = 0.8f), selected.accent)
    )
}

/** Legacy Cupertino text and opaque dialogs must agree with the glass backdrop. */
fun GlassPaletteSpec.toLoonColors(): LoonThemeColors =
    (if (isDark) DarkLoonThemeColors else LightLoonThemeColors).copy(
        bg = base,
        cardBorder = edge,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        bottomBarBorder = edge
    )

/**
 * Provides the active palette to every descendant.
 *
 * Screens do not pass colours around; they read [glassPalette] and let [GlassSurface] pick the
 * right tokens. Switching the palette in Settings therefore needs no changes in screen code.
 */
@Composable
fun ProvideGlassTheme(content: @Composable () -> Unit) {
    val palette = resolveGlassPalette(GlassDefaults.palette, LocalLoonColors.current.isDark)
    CompositionLocalProvider(
        LocalGlassPalette provides palette,
        LocalGlassTokens provides palette.toTokens(),
        content = content
    )
}

/** Derives the panel material from a palette, so the two can never drift apart. */
fun GlassPaletteSpec.toTokens(): GlassTokens = GlassTokens(
    tint = panelStart,
    tintEnd = panelEnd,
    tintAccent = panelAccent,
    edge = edge,
    rimTop = rimTop,
    rimBottom = rimBottom,
    sheen = sheen,
    isDark = isDark
)

val glassPalette: GlassPaletteSpec
    @Composable
    @ReadOnlyComposable
    get() = LocalGlassPalette.current

val glassIsDark: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalGlassPalette.current.isDark

val glassTextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlassPalette.current.textPrimary

val glassTextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlassPalette.current.textSecondary

val glassTextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlassPalette.current.textMuted

/** Brand accent of the active palette (wordmark, active tab, selection strokes). */
val glassAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlassPalette.current.uiAccent

/**
 * Semantic accents that stay constant across palettes.
 *
 * Upload/download keep their own hues because they encode meaning, not decoration — a green download
 * arrow in the blue palette would read as a different state rather than a different theme.
 */
object GlassAccent {
    val upload = Color(0xFF00C853)
    val download = Color(0xFF4A69FF)
    val violet = Color(0xFF9650FF)
    val teal = Color(0xFF00BEAA)
    val iosBlue = Color(0xFF007AFF)
    val iosGreen = Color(0xFF34C759)
    val danger = Color(0xFFFF3B30)
}
