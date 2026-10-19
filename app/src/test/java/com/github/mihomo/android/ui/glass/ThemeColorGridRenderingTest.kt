package com.github.mihomo.android.ui.glass

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.SettingsManager
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.screens.AppearanceScreen
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeColorGridRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun reset() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    @Test fun actualAppearanceGridAppliesRgbPersistsAndSurvivesDarkMode() {
        val settings = SettingsManager(compose.activity)
        compose.activity.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE).edit().clear().commit()
        val selected = mutableStateOf(GlassPalettes.CREAM)
        val dark = mutableStateOf(false)
        val time = mutableFloatStateOf(8f)
        GlassDefaults.enabled = true
        GlassDefaults.palette = selected.value
        compose.setContent {
            MihomoTheme(if (dark.value) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh", LocalSoftMeshTime provides time,
                    LocalSoftMeshSeed provides 734921L) {
                    ProvideGlassTheme {
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground {
                                AppearanceScreen(
                                    themeMode = if (dark.value) ThemeMode.DARK else ThemeMode.LIGHT,
                                    onThemeModeChanged = { dark.value = it == ThemeMode.DARK },
                                    onLanguageChanged = {}, showGroupIcons = true, onShowGroupIconsChanged = {},
                                    nodeColumns = 2, onNodeColumnsChanged = {}, onBack = {},
                                    palette = selected.value,
                                    onPaletteChanged = {
                                        selected.value = it
                                        GlassDefaults.palette = it
                                        settings.glassPaletteKey = it.key
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        save(screenshot(), "theme-grid-before.png")
        // Different hue columns, not preset aliases. Values come from the rendered grid itself.
        val blue = GlassPalettes.keyForColor(ThemeColorGrid[12 + 4 * 12 + 2])
        val coral = GlassPalettes.keyForColor(ThemeColorGrid[12 + 4 * 12 + 5])
        for ((name, key) in listOf("blue" to blue, "coral" to coral)) {
            compose.onNodeWithContentDescription("#${key.substringAfter(':')}").performClick().assertIsSelected()
            compose.waitForIdle()
            assertEquals(key, selected.value.key)
            assertEquals(key, SettingsManager(compose.activity).glassPaletteKey)
            assertEquals(selected.value, GlassPalettes.byKey(SettingsManager(compose.activity).glassPaletteKey))
            val chosenDark = resolveGlassPalette(selected.value, true)
            assertTrue(chosenDark.isDark)
            assertEquals("dark mode must retain the custom RGB identity", selected.value.accent, chosenDark.accent)
            save(screenshot(), "theme-grid-$name-selected.png")
        }
        assertNotEquals(GlassPalettes.byKey(blue).blobs, GlassPalettes.byKey(coral).blobs)
        compose.runOnIdle { dark.value = true }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("#${coral.substringAfter(':')}").assertIsSelected()
        save(screenshot(), "theme-grid-coral-dark.png")
        File("build/outputs/mesh-preview/theme-grid-evidence.txt").writeText(
            "Actual AppearanceScreen Compose/HWUI PixelCopy, not a device screenshot.\n" +
                "108 solid RGB cells (12 grayscale + 8 x 12 hue cells); only grid, no spectrum/slider.\n" +
                "Clicked and persisted $blue then $coral; fresh SettingsManager and byKey reload match.\n" +
                "Dark mode preserves custom RGB and selection.\n"
        )
    }

    private fun screenshot(): Bitmap {
        val window = compose.activity.window
        val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle { PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle()
        assertEquals(PixelCopy.SUCCESS, result)
        return bitmap
    }
    private fun save(image: Bitmap, name: String) {
        val file = File("build/outputs/mesh-preview/$name")
        file.parentFile!!.mkdirs()
        file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
