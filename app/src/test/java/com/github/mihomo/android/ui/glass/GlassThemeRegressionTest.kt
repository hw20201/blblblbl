package com.github.mihomo.android.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.theme.DarkLoonThemeColors
import com.github.mihomo.android.ui.theme.LightLoonThemeColors
import com.github.mihomo.android.ui.theme.LocalLoonColors
import com.github.mihomo.android.ui.theme.LoonThemeColors
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class GlassThemeRegressionTest {
    @get:Rule val compose = createComposeRule()

    @After
    fun resetGlassGlobals() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    @Test
    fun resolvesEveryPaletteForBothThemeModes() {
        GlassPalettes.all.forEach { selected ->
            val expected = if (selected.isDark) GlassPalettes.CREAM else selected
            assertEquals("${selected.key} in light mode", expected, resolveGlassPalette(selected, false))
            assertEquals("${selected.key} in dark mode", GlassPalettes.MIDNIGHT, resolveGlassPalette(selected, true))
        }
    }

    @Test
    fun loonColorBridgeUsesPaletteBackgroundTextAndBorders() {
        GlassPalettes.all.forEach { palette ->
            val colors = palette.toLoonColors()
            assertEquals(palette.base, colors.bg)
            assertEquals(palette.textPrimary, colors.textPrimary)
            assertEquals(palette.textSecondary, colors.textSecondary)
            assertEquals(palette.textMuted, colors.textMuted)
            assertEquals(palette.edge, colors.cardBorder)
            assertEquals(palette.edge, colors.bottomBarBorder)
            assertEquals(palette.isDark, colors.isDark)
        }
    }

    @Test
    fun lightModeResolvesMidnightToReadableCream() = assertTheme(
        ThemeMode.LIGHT, GlassPalettes.MIDNIGHT, GlassPalettes.CREAM
    )

    @Test
    fun darkModeResolvesCreamToReadableMidnight() = assertTheme(
        ThemeMode.DARK, GlassPalettes.CREAM, GlassPalettes.MIDNIGHT
    )

    @Test
    fun lightModeUsesSelectedBluePalette() = assertTheme(
        ThemeMode.LIGHT, GlassPalettes.BLUE, GlassPalettes.BLUE
    )

    @Test
    @Config(sdk = [35], qualifiers = "night")
    fun systemNightUsesMidnightWithoutOverwritingSavedBlue() {
        assertTheme(ThemeMode.SYSTEM, GlassPalettes.BLUE, GlassPalettes.MIDNIGHT)
        assertEquals(GlassPalettes.BLUE, GlassDefaults.palette)
    }

    @Test
    @Config(sdk = [35], qualifiers = "notnight")
    fun systemDayUsesSavedBlue() = assertTheme(
        ThemeMode.SYSTEM, GlassPalettes.BLUE, GlassPalettes.BLUE
    )

    @Test
    fun disabledGlassPreservesOriginalLightTheme() = assertTheme(
        ThemeMode.LIGHT, GlassPalettes.BLUE, GlassPalettes.BLUE, false
    )

    @Test
    fun disabledGlassPreservesOriginalDarkTheme() = assertTheme(
        ThemeMode.DARK, GlassPalettes.BLUE, GlassPalettes.MIDNIGHT, false
    )

    private fun assertTheme(
        mode: ThemeMode,
        selected: GlassPaletteSpec,
        expected: GlassPaletteSpec,
        enabled: Boolean = true
    ) {
        GlassDefaults.enabled = enabled
        GlassDefaults.palette = selected
        var observedLoon: LoonThemeColors? = null
        var observedPalette: GlassPaletteSpec? = null
        var observedOnSurface: Color? = null
        compose.setContent {
            MihomoTheme(themeMode = mode) {
                ProvideGlassTheme {
                    val loon = LocalLoonColors.current
                    val palette = LocalGlassPalette.current
                    val onSurface = MaterialTheme.colorScheme.onSurface
                    SideEffect {
                        observedLoon = loon
                        observedPalette = palette
                        observedOnSurface = onSurface
                    }
                }
            }
        }
        compose.waitForIdle()
        val expectedLoon = if (enabled) expected.toLoonColors()
            else if (mode == ThemeMode.DARK) DarkLoonThemeColors else LightLoonThemeColors
        compose.runOnIdle {
            assertEquals(expected, observedPalette)
            assertEquals(expectedLoon, observedLoon)
            assertEquals(expectedLoon.textPrimary, observedOnSurface)
        }
    }
}
