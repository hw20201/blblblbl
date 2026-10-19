package com.github.mihomo.android.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.UiStyle
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassCardMaterialTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun screenshot(): Bitmap {
        val view = compose.activity.window.decorView
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
            view.draw(Canvas(it))
        }
    }

    @Test
    fun diagonalHighlightRespectsIntensityThemesStylesAndOpaqueOffState() {
        val style = mutableStateOf(UiStyle.CUPERTINO)
        val theme = mutableStateOf(ThemeMode.LIGHT)
        val enabled = mutableStateOf(true)
        val intensity = mutableStateOf(1f)
        val tint = Color(0xFF586878)
        var left = 0
        var top = 0
        var width = 0
        var height = 0
        compose.setContent {
            MihomoTheme(themeMode = theme.value,
                glassEnabled = enabled.value, glassIntensity = intensity.value) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    Box(Modifier.fillMaxSize().background(Color.Magenta).padding(24.dp)) {
                        Box(Modifier.width(280.dp).height(100.dp).glassCardFinish(tint, 18.dp)
                            .onGloballyPositioned {
                                left = it.positionInWindow().x.toInt()
                                top = it.positionInWindow().y.toInt()
                                width = it.size.width
                                height = it.size.height
                            })
                    }
                }
            }
        }
        for (targetStyle in listOf(UiStyle.CUPERTINO)) {
            for (targetTheme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                compose.runOnIdle {
                    style.value = targetStyle
                    theme.value = targetTheme
                    enabled.value = true
                    intensity.value = 1f
                }
                compose.waitForIdle()
                val full = screenshot()
                val highlight = full.getPixel(left + 35, top + 30)
                val coolEdge = full.getPixel(left + width - 35, top + 30)
                assertNotEquals("material has a horizontal component, not just a vertical wash", highlight, coolEdge)
                if (targetTheme == ThemeMode.DARK) {
                    assertTrue("dark highlight must remain restrained", android.graphics.Color.green(highlight) < 130)
                }
                compose.runOnIdle { intensity.value = 0f }
                compose.waitForIdle()
                val zero = screenshot()
                // Minimum strength is still frosted glass; only the switch makes it opaque.
                assertNotEquals("zero intensity still reveals the backdrop", tint.toArgb(), zero.getPixel(left + 35, top + 30))
                assertNotEquals("intensity still changes material", highlight, zero.getPixel(left + 35, top + 30))
                compose.runOnIdle { enabled.value = false }
                compose.waitForIdle()
                val off = screenshot()
                assertEquals(tint.toArgb(), off.getPixel(left + 35, top + 30))
                assertEquals(tint.toArgb(), off.getPixel(left + width - 35, top + height - 30))
                compose.runOnIdle { intensity.value = 1f }
                compose.waitForIdle()
                val offFull = screenshot()
                assertEquals("disabled glass ignores intensity", off.getPixel(left + 35, top + 30), offFull.getPixel(left + 35, top + 30))
                assertEquals("geometry stays compact", 280, width)
                assertEquals(100, height)
                full.recycle()
                zero.recycle()
                off.recycle()
                offFull.recycle()
            }
        }
    }
}
