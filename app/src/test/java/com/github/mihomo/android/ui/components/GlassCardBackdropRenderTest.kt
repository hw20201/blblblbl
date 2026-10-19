package com.github.mihomo.android.ui.components

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.UiStyle
import com.github.mihomo.android.ui.theme.LocalGlassEnabled
import com.github.mihomo.android.ui.theme.LocalGlassIntensity
import com.github.mihomo.android.ui.theme.MihomoTheme
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
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassCardBackdropRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun screenshot(): Bitmap {
        val window = compose.activity.window
        val view = window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle {
            PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
        }
        compose.waitForIdle()
        assertEquals("native hardware PixelCopy must succeed", PixelCopy.SUCCESS, result)
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File("build/reports/glass-card-backdrop/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun luminance(bitmap: Bitmap, x: Int, y: Int): Double =
        0.2126 * android.graphics.Color.red(bitmap.getPixel(x, y)) +
            0.7152 * android.graphics.Color.green(bitmap.getPixel(x, y)) +
            0.0722 * android.graphics.Color.blue(bitmap.getPixel(x, y))

    private fun contrast(bitmap: Bitmap, centerX: Int, centerY: Int, halfWidth: Int): Double {
        var min = Double.MAX_VALUE
        var max = Double.MIN_VALUE
        for (x in centerX - halfWidth..centerX + halfWidth) {
            val value = luminance(bitmap, x, centerY)
            min = minOf(min, value)
            max = maxOf(max, value)
        }
        return max - min
    }

    @Test
    fun cardBackdropBlursLiveSourceAndKeepsForegroundContentSharp() {
        val intensity = mutableStateOf(0f)
        val enabled = mutableStateOf(true)
        val replayEnabled = mutableStateOf(true)
        val paletteChanged = mutableStateOf(false)
        val tint = Color.White
        var cardCenter = Offset.Zero
        var textCenter = Offset.Zero

        compose.setContent {
            MihomoTheme(
                themeMode = ThemeMode.LIGHT,
                uiStyle = UiStyle.CUPERTINO,
                glassIntensity = intensity.value,
                glassEnabled = enabled.value
            ) {
                CompositionLocalProvider(
                    LocalGlassIntensity provides intensity.value,
                    LocalGlassEnabled provides enabled.value,
                    androidx.compose.ui.platform.LocalDensity provides Density(1f, 1f)
                ) {
                    val source = rememberGlassBackdrop()
                    CompositionLocalProvider(LocalGlassMaterialBackdrop provides if (replayEnabled.value) source else null) {
                        Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                            // Independent sibling source: it records only this high-frequency checkerboard.
                            Box(Modifier.fillMaxSize().glassBackdropSource(source)) {
                                ComposeCanvas(Modifier.fillMaxSize()) {
                                    val tile = 24.dp.toPx()
                                    var y = 0f
                                    var row = 0
                                    while (y < size.height) {
                                        var x = 0f
                                        var column = 0
                                        while (x < size.width) {
                                            val color = if (paletteChanged.value) {
                                                if ((row + column) % 2 == 0) Color.Red else Color.Blue
                                            } else if ((row + column) % 2 == 0) Color.Black else Color.White
                                            drawRect(color, Offset(x, y), Size(tile, tile))
                                            x += tile
                                            column++
                                        }
                                        y += tile
                                        row++
                                    }
                                }
                            }
                            Box(
                                Modifier.width(300.dp).height(180.dp)
                                    .glassCardFinish(tint, 20.dp)
                                    .onGloballyPositioned {
                                        val origin = it.positionInWindow()
                                        cardCenter = Offset(origin.x + it.size.width / 2f, origin.y + it.size.height / 2f)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Sharp foreground", color = Color(0xFF101010), modifier = Modifier.onGloballyPositioned {
                                    val origin = it.positionInWindow()
                                    textCenter = Offset(origin.x + it.size.width / 2f, origin.y + it.size.height / 2f)
                                })
                            }
                        }
                    }
                }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Sharp foreground").assertExists()
        val centerX = cardCenter.x.toInt()
        val centerY = cardCenter.y.toInt()
        val zero = screenshot()
        save(zero, "intensity-0")
        val zeroContrast = contrast(zero, centerX, centerY - 35, 110)

        compose.runOnIdle { intensity.value = 1f }
        compose.waitForIdle()
        val full = screenshot()
        save(full, "intensity-1")
        val fullContrast = contrast(full, centerX, centerY - 35, 110)
        assertTrue("intensity 1 must reduce high-frequency backdrop contrast (0=$zeroContrast, 1=$fullContrast)", fullContrast < zeroContrast - 8.0)

        // At identical material strength, disable only the replay layer. This leaves
        // an alpha-only pane over the same checkerboard, ruling out tint as fake blur.
        compose.runOnIdle { replayEnabled.value = false }
        compose.waitForIdle()
        val alphaOnly = screenshot()
        save(alphaOnly, "alpha-only-control")
        val alphaOnlyContrast = contrast(alphaOnly, centerX, centerY - 35, 110)
        assertTrue("real blur must outperform identical alpha-only material (blur=$fullContrast, alpha=$alphaOnlyContrast)", fullContrast < alphaOnlyContrast * 0.25)
        alphaOnly.recycle()
        compose.runOnIdle { replayEnabled.value = true }
        compose.waitForIdle()
        // paletteChanged is read only while drawing the sibling source, not by the card composition.
        compose.runOnIdle { paletteChanged.value = true }
        compose.waitForIdle()
        val updated = screenshot()
        save(updated, "live-source-update")
        assertNotEquals("source-only color change must update the sampled card", full.getPixel(centerX, centerY - 35), updated.getPixel(centerX, centerY - 35))

        // The separate replay effect samples only the source layer; foreground text remains composed above it.
        compose.onNodeWithText("Sharp foreground").assertExists()
        assertTrue("foreground text location is recorded", textCenter != Offset.Zero)

        compose.runOnIdle { enabled.value = false }
        compose.waitForIdle()
        val disabled = screenshot()
        save(disabled, "disabled-opaque-white")
        assertEquals("disabled card is opaque white", Color.White.toArgb(), disabled.getPixel(centerX, centerY - 35))
        compose.onNodeWithText("Sharp foreground").assertExists()

        zero.recycle()
        full.recycle()
        updated.recycle()
        disabled.recycle()
    }
}
