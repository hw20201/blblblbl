package com.github.arca.android.ui.glass

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.SettingsManager
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.screens.AppearanceScreen
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h1000dp-mdpi")
class AppearanceControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val oldGlassEnabled = GlassDefaults.enabled
    private val oldPalette = GlassDefaults.palette

    @After fun reset() {
        GlassDefaults.enabled = oldGlassEnabled
        GlassDefaults.palette = oldPalette
    }

    @Test fun appearanceKeepsThemeAndLanguageControlsWithoutRetiredColorEditors() {
        var selectedTheme = ThemeMode.LIGHT
        var selectedLanguage = "SYSTEM"
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        AppearanceScreen(
                            themeMode = ThemeMode.LIGHT,
                            onThemeModeChanged = { selectedTheme = it },
                            onLanguageChanged = { selectedLanguage = it },
                            showGroupIcons = true, onShowGroupIconsChanged = {},
                            nodeColumns = 2, onNodeColumnsChanged = {}, onBack = {}
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("appearance-theme-dark").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ThemeMode.DARK, selectedTheme) }
        compose.onNodeWithTag("appearance-language-en").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("en", selectedLanguage)
            assertEquals("en", SettingsManager(compose.activity).appLanguage)
        }
        compose.onNodeWithTag("appearance-theme-color-grid").assertDoesNotExist()
        compose.onNodeWithTag("appearance-wallpaper-controls").assertDoesNotExist()
    }
}
