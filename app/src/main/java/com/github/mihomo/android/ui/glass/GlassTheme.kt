package com.github.mihomo.android.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.github.mihomo.android.ui.theme.LocalLoonColors

/**
 * Opt-in wrapper for screens that use the glass material.
 *
 * It piggybacks on the existing `LocalLoonColors` dark flag so the app keeps a single source of
 * truth for light/dark, and a screen can adopt glass without threading `isDark` through its
 * parameters.
 *
 * ```kotlin
 * ProvideGlassTheme {
 *     NexusGlassBackground(dark = glassIsDark) {
 *         // existing content
 *     }
 * }
 * ```
 */
@Composable
fun ProvideGlassTheme(content: @Composable () -> Unit) {
    val dark = LocalLoonColors.current.isDark
    CompositionLocalProvider(
        LocalGlassTokens provides if (dark) DarkGlassTokens else LightGlassTokens,
        content = content
    )
}

val glassIsDark: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalGlassTokens.current.isDark

/** Type colours that follow the glass tokens rather than the flat Cupertino palette. */
val glassTextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (glassIsDark) Color(0xFFF5F6F8) else Color(0xFF12151C)

val glassTextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (glassIsDark) Color(0x99EBF0FF) else Color(0x8512151C)

val glassTextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = if (glassIsDark) Color(0x66EBF0FF) else Color(0x6612151C)

/**
 * Accents kept from the existing brand palette so the redesign doesn't lose the app's identity.
 * The orange is the NEXUS wordmark colour and is used sparingly — as a large area it reads as a
 * warning state.
 */
object GlassAccent {
    val brand = Color(0xFFFF6B00)
    val brandLight = Color(0xFFFF8533)
    val upload = Color(0xFF00C853)
    val download = Color(0xFF4A69FF)
    val violet = Color(0xFF9650FF)
    val teal = Color(0xFF00BEAA)
    val iosBlue = Color(0xFF007AFF)
    val iosGreen = Color(0xFF34C759)
    val danger = Color(0xFFFF3B30)
}

/**
 * Single switch for the surface family.
 *
 * Screens adopt the glass material one at a time; flipping this to `false` restores the previous
 * flat Cupertino look everywhere without touching any layout code. It is a plain global rather than
 * a `CompositionLocal` on purpose: nothing toggles it at runtime, it exists so the redesign can be
 * rolled back in one line.
 */
object GlassDefaults {
    var enabled: Boolean = true
}
