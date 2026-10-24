package com.github.mihomo.android.ui.glass

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassWallpaperPickerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun pickerSwitchesStyleAndEditsThreeIndependentColors() {
        val settings = mutableStateOf(GlassWallpaperSettings())
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ProvideGlassTheme { GlassWallpaperPicker(settings.value, { settings.value = it }) }
                }
            }
        }
        compose.onNodeWithTag("wallpaper-mode-fluted").performClick()
        assertEquals(GlassWallpaperMode.FLUTED, settings.value.mode)
        assertTrue(settings.value.enabled)
        compose.onNodeWithTag("wallpaper-randomize-curve").assertDoesNotExist()
        compose.onNodeWithTag("wallpaper-rgb-slider-R").assertExists()
        compose.onNodeWithTag("wallpaper-rgb-slider-G").assertExists()
        compose.onNodeWithTag("wallpaper-rgb-slider-B").assertExists()

        compose.onNodeWithTag("wallpaper-color-slot-1").performScrollTo().performClick()
        val key = GlassPalettes.keyForColor(ThemeColorGrid[20])
        compose.onNodeWithContentDescription("#${key.substringAfter(':')}").performScrollTo().performClick()
        assertEquals(GlassPalettes.byKey(key).accent.toArgb(), settings.value.colorB)
        assertTrue("selecting another swatch must update the second stop", settings.value.colorB != GlassWallpaperSettings.COLOR_B)
        compose.onNodeWithTag("wallpaper-color-slot-2").performScrollTo().performClick()
        assertEquals("one swatch edit must not overwrite the other colors", GlassWallpaperSettings.COLOR_A, settings.value.colorA)
        compose.onNodeWithTag("wallpaper-mode-fluted").performScrollTo().performClick()
        compose.onNodeWithTag("wallpaper-randomize-curve").assertDoesNotExist()
    }
}
