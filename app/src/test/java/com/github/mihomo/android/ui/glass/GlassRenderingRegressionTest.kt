package com.github.mihomo.android.ui.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.theme.LocalLoonColors
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassRenderingRegressionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var rootView: View

    @After
    fun resetGlobals() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    @Test
    fun pageBackdropOccludesUnderlyingContentForEveryPalette() {
        val selected = mutableStateOf(GlassPalettes.CREAM)
        val underlay = mutableStateOf(Color.Red)
        compose.setContent {
            rootView = LocalView.current
            Box(Modifier.size(100.dp).background(underlay.value)) {
                NexusGlassBackground(Modifier.testTag("page"), selected.value)
            }
        }
        GlassPalettes.all.forEach { palette ->
            compose.runOnIdle {
                selected.value = palette
                underlay.value = Color.Red
            }
            compose.waitForIdle()
            val red = capture("page")
            compose.runOnIdle { underlay.value = Color.Blue }
            compose.waitForIdle()
            val blue = capture("page")
            for (x in listOf(1, red.width / 2, red.width - 2)) {
                for (y in listOf(1, red.height / 2, red.height - 2)) {
                    assertEquals("${palette.key} leaks its underlay at $x,$y", red.getPixel(x, y), blue.getPixel(x, y))
                    assertEquals(255, android.graphics.Color.alpha(red.getPixel(x, y)))
                }
            }
            red.recycle()
            blue.recycle()
        }
    }

    @Test
    fun disabledGlassUsesOriginalOpaquePageBackground() {
        GlassDefaults.enabled = false
        var expected = Color.Unspecified
        compose.setContent {
            rootView = LocalView.current
            MihomoTheme(ThemeMode.LIGHT) {
                expected = LocalLoonColors.current.bg
                Box(Modifier.size(100.dp).background(Color.Red)) {
                    NexusGlassBackground(Modifier.testTag("fallback"))
                }
            }
        }
        val image = capture("fallback")
        assertEquals(expected.toArgb(), image.getPixel(image.width / 2, image.height / 2))
        image.recycle()
    }

    // PixelCopy/captureToImage waits for a hardware Window redraw under Robolectric.
    // Drawing the Compose host into a native software Canvas exercises the same draw modifiers.
    private fun capture(tag: String): Bitmap {
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        lateinit var output: Bitmap
        compose.runOnIdle {
            val full = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
            rootView.draw(Canvas(full))
            output = Bitmap.createBitmap(full, bounds.left.roundToInt(), bounds.top.roundToInt(),
                bounds.width.roundToInt(), bounds.height.roundToInt())
            if (output !== full) full.recycle()
        }
        return output
    }

    @Test
    fun swatchExposesSelectionAndUpdatesItOnClick() {
        compose.setContent {
            val selected = remember { mutableStateOf(GlassPalettes.CREAM) }
            MihomoTheme(ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    GlassPalettePicker(selected.value, { selected.value = it })
                }
            }
        }
        compose.onNodeWithText("奶白").assertIsSelected()
        compose.onNodeWithText("白玻璃").assertIsNotSelected().performClick()
        compose.onNodeWithText("白玻璃").assertIsSelected()
        compose.onNodeWithText("奶白").assertIsNotSelected()
    }
}
