package com.github.mihomo.android.ui.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.components.LoonFloatingBottomBar
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationLensMagnificationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun reset() { GlassDefaults.enabled = true; GlassDefaults.palette = GlassPalettes.CREAM }

    @Test fun spatialGainIsBoundedSmoothSymmetricAndNotBasedOnSelectedTab() {
        assertEquals(1.04f, lensTabMagnification(0, 0f, 0f), .0001f)
        assertEquals(1.11f, lensTabMagnification(1, 1f, 1f), .0001f)
        assertEquals(1f, lensTabMagnification(0, 1f, 1f), .0001f)
        for (i in 0..200) {
            val position = i / 100f
            for (slot in 0..2) {
                val gain = lensTabMagnification(slot, position, 1f)
                assertTrue(gain in 1f..1.111f)
                assertEquals(gain, lensTabMagnification(2 - slot, 2f - position, 1f), .0001f)
            }
        }
        assertEquals(lensTabMagnification(0, .5f, 1f), lensTabMagnification(1, .5f, 1f), .0001f)
    }

    @Test fun actualDockMagnifiesIncomingIconDuringDragWithoutMovingSemanticBounds() {
        val selected = mutableIntStateOf(0)
        val dark = mutableStateOf(false)
        val selections = mutableListOf<Int>()
        GlassDefaults.enabled = true
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides "en") {
                MihomoTheme(if (dark.value) ThemeMode.DARK else ThemeMode.LIGHT) {
                    ProvideGlassTheme {
                        val lens = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().recordNavigationScene(lens)
                                .background(if (dark.value) Color(0xFF192136) else Color(0xFFD6DFFF)))
                            LoonFloatingBottomBar(selected.intValue, {
                                selected.intValue = it; selections += it
                            }, Modifier.align(Alignment.Center), lens)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val dock = compose.onNodeWithTag("navigation-dock")
        val bounds = dock.getUnclippedBoundsInRoot()
        val labels = listOf("Dashboard", "Proxies", "Settings").map {
            compose.onNodeWithText(it).getUnclippedBoundsInRoot()
        }
        val before = screenshot("light-rest")
        fun greyIconPixels(image: Bitmap): Int {
            val cx = ((bounds.left.value + bounds.right.value) / 2f).toInt()
            val top = bounds.top.value.toInt()
            return (cx - 20..cx + 20).sumOf { x ->
                (top + 7..top + 36).count { y ->
                    val pixel = image.getPixel(x, y)
                    android.graphics.Color.red(pixel) < 170 && android.graphics.Color.green(pixel) < 170 &&
                        android.graphics.Color.blue(pixel) < 180
                }
            }
        }
        dock.performTouchInput {
            down(Offset(width / 6f, height / 2f))
            moveTo(Offset(width / 2f, height / 2f), 300)
        }
        compose.waitForIdle()
        assertTrue(selections.isEmpty())
        assertEquals(0, selected.intValue)
        val during = screenshot("light-drag-over-proxies")
        val beforePixels = greyIconPixels(before)
        val duringPixels = greyIconPixels(during)
        println("Robolectric actual incoming proxy icon pixels: rest=$beforePixels magnified=$duringPixels")
        assertTrue("incoming icon is visibly magnified before selection", duringPixels > beforePixels)
        listOf("Dashboard", "Proxies", "Settings").forEachIndexed { i, text ->
            assertEquals("drawing never changes accessibility/layout bounds", labels[i],
                compose.onNodeWithText(text).getUnclippedBoundsInRoot())
        }
        dock.performTouchInput { moveTo(Offset(width * 5f / 6f, height / 2f), 250) }
        compose.waitForIdle()
        screenshot("light-drag-over-settings").recycle()
        dock.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf(2), selections)
        screenshot("light-settings-settled").recycle()
        compose.runOnIdle { dark.value = true }
        compose.waitForIdle()
        dock.performTouchInput {
            down(Offset(width * 5f / 6f, height / 2f))
            moveTo(Offset(width / 6f, height / 2f), 300)
        }
        compose.waitForIdle()
        screenshot("dark-drag-over-dashboard").recycle()
        dock.performTouchInput { cancel() }
        compose.waitForIdle()
        assertEquals(listOf(2), selections)
        screenshot("dark-cancel-restores-settings").recycle()
        before.recycle(); during.recycle()
    }

    private fun screenshot(name: String): Bitmap {
        val window = compose.activity.window
        val view = window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle { PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle()
        assertEquals(PixelCopy.SUCCESS, result)
        val file = File("build/reports/navigation-magnification/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return bitmap
    }
}
