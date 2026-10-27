package com.github.mihomo.android.ui.glass

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

    @Test fun appearanceUsesTheCustomWallpaperEditorInsteadOfTheLegacyThemePalette() {
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        AppearanceScreen(
                            themeMode = ThemeMode.LIGHT,
                            onThemeModeChanged = {},
                            onLanguageChanged = {}, showGroupIcons = true, onShowGroupIconsChanged = {},
                            nodeColumns = 2, onNodeColumnsChanged = {}, onBack = {}
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("appearance-theme-color-grid").assertDoesNotExist()
        compose.onNodeWithTag("appearance-wallpaper-controls").performScrollTo()
        compose.onNodeWithTag("wallpaper-toggle-color-grid").performScrollTo().assertExists()
        compose.onNodeWithTag("wallpaper-color-grid").assertDoesNotExist()
        compose.onNodeWithTag("wallpaper-toggle-color-grid").performClick()
        compose.onNodeWithTag("wallpaper-color-grid").assertExists()
    }

    @Test fun renderedSwatchesAreSquareAndShareExactRowAndColumnEdges() {
        val density = mutableFloatStateOf(1f)
        val width = mutableFloatStateOf(333f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density.floatValue)) {
                GlassPalettePicker(selected = GlassPalettes.byKey(GlassPalettes.keyForColor(ThemeColorGrid[38])),
                    onSelect = {}, modifier = Modifier.width(width.floatValue.dp))
            }
        }
        for ((d, w) in listOf(1f to 333f, 1f to 363f, 2f to 173f, 2.75f to 137f)) {
        compose.runOnIdle { density.floatValue = d; width.floatValue = w }
        compose.waitForIdle()
        // Use the actual laid-out Compose semantics bounds, not calculated dp estimates.
        val bounds = ThemeColorGrid.map { color ->
            val key = GlassPalettes.keyForColor(color)
            compose.onNodeWithContentDescription("#${key.substringAfter(':')}")
                .fetchSemanticsNode().boundsInRoot
        }
        val first = bounds.first()
        assertTrue("swatches must be square: ${first.width} x ${first.height}",
            kotlin.math.abs(first.width - first.height) <= 1f)
        for (index in bounds.indices) {
            val cell = bounds[index]
            val rowFirst = bounds[index / 12 * 12]
            assertTrue("row cells share a top", kotlin.math.abs(cell.top - rowFirst.top) < 0.01f)
            assertTrue("row cells share a bottom", kotlin.math.abs(cell.bottom - rowFirst.bottom) < 0.01f)
            assertTrue("square within pixel rounding", kotlin.math.abs(cell.width - cell.height) <= 1f)
            if (index % 12 > 0) assertTrue("adjacent swatch edges touch",
                kotlin.math.abs(cell.left - bounds[index - 1].right) < 0.01f)
            if (index >= bounds.size - 12) continue
            val nextRow = bounds[index + 12]
            assertTrue("rows share identical columns", kotlin.math.abs(cell.left - nextRow.left) < 0.01f)
            assertTrue("successive rows touch", kotlin.math.abs(nextRow.top - cell.bottom) < 0.01f)
        }
        save(screenshot(), "theme-grid-geometry-${d}-${w}.png")
        }
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
