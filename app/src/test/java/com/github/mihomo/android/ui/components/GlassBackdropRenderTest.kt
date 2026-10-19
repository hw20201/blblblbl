package com.github.mihomo.android.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.UiStyle
import com.github.mihomo.android.ui.theme.LocalGlassEnabled
import com.github.mihomo.android.ui.theme.LocalGlassIntensity
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassBackdropRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun screenshot(): Bitmap {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle {
            PixelCopy.request(compose.activity.window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
        }
        compose.waitForIdle()
        org.junit.Assert.assertEquals("native hardware PixelCopy must succeed", PixelCopy.SUCCESS, result)
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File("build/reports/glass-backdrop/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun luminance(bitmap: Bitmap, x: Int, y: Int) =
        (0.2126 * android.graphics.Color.red(bitmap.getPixel(x, y)) +
            0.7152 * android.graphics.Color.green(bitmap.getPixel(x, y)) +
            0.0722 * android.graphics.Color.blue(bitmap.getPixel(x, y)))

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
    fun backdropBlurReducesHighFrequencyContrastAndIntensityChangesBlur() {
        val intensity = mutableStateOf(0f)
        val paletteChanged = mutableStateOf(false)
        var centerX = 0
        var centerY = 0
        var referenceX = 0
        var surfaceCenter = Offset.Zero

        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT, uiStyle = UiStyle.CUPERTINO, glassIntensity = intensity.value, glassEnabled = true) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalGlassIntensity provides intensity.value) {
                    val layer = rememberGlassBackdrop()
                    CompositionLocalProvider(LocalGlassBackdrop provides layer) {
                        Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                            // This sibling records only the striped artwork, excluding the glass surface.
                            Box(Modifier.fillMaxSize().glassBackdropSource(layer)) {
                                ComposeCanvas(Modifier.fillMaxSize()) {
                                    val stripeWidth = 24.dp.toPx()
                                    var x = 0f
                                    var index = 0
                                    while (x < size.width) {
                                        drawRect(if (paletteChanged.value) {
                                            if (index % 2 == 0) Color.Red else Color.Blue
                                        } else if (index % 2 == 0) Color.Black else Color.White,
                                            topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(stripeWidth, size.height))
                                        x += stripeWidth
                                        index++
                                    }
                                }
                            }
                            GlassNavigationSurface(
                                Modifier.width(300.dp).height(100.dp).onGloballyPositioned {
                                    val origin = it.positionInWindow()
                                    surfaceCenter = Offset(origin.x + it.size.width / 2f, origin.y + it.size.height / 2f)
                                }
                            ) { }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        // Measure the centered surface and a clear region by its deterministic screen geometry.
        compose.runOnIdle {
            centerX = surfaceCenter.x.toInt()
            centerY = surfaceCenter.y.toInt()
            referenceX = centerX + 165
        }
        val zero = screenshot()
        val zeroGlass = contrast(zero, centerX, centerY, 110)
        val zeroClear = contrast(zero, referenceX, centerY, 18)
        save(zero, "intensity-0")

        compose.runOnIdle { intensity.value = 1f }
        compose.waitForIdle()
        val full = screenshot()
        val fullGlass = contrast(full, centerX, centerY, 110)
        save(full, "intensity-1")

        println("Backdrop contrast: clear=$zeroClear, minimum=$zeroGlass, maximum=$fullGlass")
        assertTrue("full blur should suppress high-frequency contrast beyond translucency alone (glass=$fullGlass, clear alpha-only=$zeroClear)", fullGlass < zeroClear * 0.25)
        assertTrue("intensity 1 should blur more strongly than intensity 0 (0=$zeroGlass, 1=$fullGlass)", fullGlass < zeroGlass - 8.0)
        assertNotEquals("intensity changes the rendered backdrop", zeroGlass, fullGlass)
        // Only the source draw lambda observes this state. The glass composition
        // does not: this verifies the shared display list updates without a timer
        // or unrelated pane recomposition (the same mechanism used on scroll).
        compose.runOnIdle { paletteChanged.value = true }
        compose.waitForIdle()
        val updated = screenshot()
        assertNotEquals("source-only redraw must update the sampled pane",
            full.getPixel(centerX, centerY), updated.getPixel(centerX, centerY))
        save(updated, "live-source-update")
        updated.recycle()
        // A software screenshot must bypass RenderNode replay instead of crashing.
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val software = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(software))
            save(software, "software-fallback")
            assertTrue(android.graphics.Color.alpha(software.getPixel(centerX, centerY)) > 0)
            software.recycle()
        }
        zero.recycle()
        full.recycle()
    }

    @Test
    fun disabledGlassIsOpaqueAndThemeStyleCombinationsRemainRenderable() {
        val enabled = mutableStateOf(false)
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.DARK, uiStyle = UiStyle.MIUIX, glassEnabled = enabled.value) {
                CompositionLocalProvider(LocalGlassEnabled provides enabled.value) {
                    Box(Modifier.fillMaxSize().background(Color.Magenta), contentAlignment = Alignment.Center) {
                        GlassNavigationSurface(Modifier.width(300.dp).height(100.dp)) { }
                    }
                }
            }
        }
        compose.waitForIdle()
        val opaque = screenshot()
        val pixel = opaque.getPixel(opaque.width / 2, opaque.height / 2)
        // A disabled dark navigation surface uses its fixed #202329 tint.
        assertTrue("disabled glass must not reveal magenta", android.graphics.Color.red(pixel) < 80)
        save(opaque, "disabled-dark-miui")
        opaque.recycle()

        compose.runOnIdle { enabled.value = true }
        compose.waitForIdle()
        val on = screenshot()
        save(on, "enabled-dark-miui")
        on.recycle()
    }

    @Test
    @Config(sdk = [30])
    fun olderAndroidUsesTranslucentFallbackAndDisablesItSafely() {
        val enabled = mutableStateOf(true)
        var source: GlassBackdrop? = null
        var centre = Offset.Zero
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT, glassEnabled = enabled.value) {
                val backdrop = rememberGlassBackdrop()
                source = backdrop
                CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxSize().glassBackdropSource(backdrop, enabled.value).background(Color.Magenta))
                        GlassNavigationSurface(Modifier.width(300.dp).height(100.dp).onGloballyPositioned {
                            val origin = it.positionInWindow()
                            centre = Offset(origin.x + it.size.width / 2f, origin.y + it.size.height / 2f)
                        }) { }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            org.junit.Assert.assertEquals("API 30 must not record an unsupported blur layer", 0, source!!.layer.size.width)
        }
        val on = screenshot()
        val onPixel = on.getPixel(centre.x.toInt(), centre.y.toInt())
        save(on, "api30-fallback-on")
        compose.runOnIdle { enabled.value = false }
        compose.waitForIdle()
        val off = screenshot()
        val offPixel = off.getPixel(centre.x.toInt(), centre.y.toInt())
        org.junit.Assert.assertEquals(Color(0xFFF9FBFF).toArgb(), offPixel)
        assertNotEquals("fallback still obeys the glass toggle", onPixel, offPixel)
        save(off, "api30-fallback-off")
        on.recycle()
        off.recycle()
    }
}
