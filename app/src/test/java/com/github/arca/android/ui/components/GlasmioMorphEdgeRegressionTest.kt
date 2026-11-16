package com.github.arca.android.ui.components

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.GlassSurfaceCategory
import com.github.arca.android.ui.glass.LocalArcaGlasmioBackdrop
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.theme.MihomoTheme
import io.github.neoruaa.glasmio.glass.glassBackdropSource
import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlasmioMorphEdgeRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsTopBandUsesTheActualGlasmioBackdropBlur() {
        compose.setContent {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize().background(Color.DarkGray)) {
                Canvas(
                    Modifier.fillMaxSize()
                        .glassBackdropSource(backdrop)
                        .trackBackdropPosition(backdrop)
                ) {
                    var x = 0f
                    var index = 0
                    while (x < size.width) {
                        val barWidth = 6f
                        drawRect(
                            color = if (index % 2 == 0) Color.Red else Color.Black,
                            topLeft = Offset(x, 0f),
                            size = Size(barWidth, size.height)
                        )
                        x += barWidth
                        index++
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(SETTINGS_MORPH_TOP_BACKDROP_BLUR_HEIGHT)
                        .settingsMorphTopBackdropBlur(backdrop)
                        .testTag("settings-top-glasmio-blur")
                )
            }
        }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("settings-top-glasmio-blur").fetchSemanticsNode().boundsInRoot
        assertEquals(64f, bounds.height, 1f)

        val image = hardwareScreenshot()
        val x = (bounds.left + bounds.width / 2f).roundToInt()
        val blurredY = (bounds.top + 3f).roundToInt()
        val sharpY = (bounds.bottom + 8f).roundToInt()
        val blurredContrast = redContrast(image, x, blurredY)
        val sharpContrast = redContrast(image, x, sharpY)
        assertTrue("the shared Glasmio backdrop source remains sharp outside the strip: $sharpContrast", sharpContrast > 180)
        assertTrue("the top strip applies Glasmio's native backdrop blur: blurred=$blurredContrast sharp=$sharpContrast", blurredContrast < sharpContrast - 5)
        image.recycle()
    }

    @Test
    fun realMorphPanelShowsVisibleTopBlurAndFadesBackIntoItsGlasmioMaterial() {
        val previousGlassEnabled = GlassDefaults.enabled
        try {
            GlassDefaults.enabled = true
            compose.setContent {
                val backdrop = rememberLayerBackdrop()
                MihomoTheme(ThemeMode.DARK) {
                    ProvideGlassTheme {
                        CompositionLocalProvider(LocalArcaGlasmioBackdrop provides backdrop) {
                            Box(Modifier.fillMaxSize()) {
                                Canvas(
                                    Modifier.fillMaxSize()
                                        .glassBackdropSource(backdrop)
                                        .trackBackdropPosition(backdrop)
                                ) {
                                    var x = 0f
                                    var index = 0
                                    while (x < size.width) {
                                        drawRect(
                                            color = if (index % 2 == 0) Color.Red else Color.Black,
                                            topLeft = Offset(x, 0f),
                                            size = Size(6.dp.toPx(), size.height)
                                        )
                                        x += 6.dp.toPx()
                                        index++
                                    }
                                }
                                ShortcutMorphOverlay(
                                    visible = true,
                                    sourceBounds = Rect(24f, 64f, 80f, 120f),
                                    sourceTitle = "DNS 设置",
                                    sourceSubtitle = "Fake-IP、解析诊断与 DNS 记录",
                                    sourceIcon = {},
                                    lens = null,
                                    onDismissRequest = {},
                                    onDismissed = {},
                                    panelTestTag = "pixel-settings-detail-page",
                                    panelHeight = 520.dp,
                                    reserveDockSpace = false,
                                    settingsTopBackdropBlur = true,
                                    settingsBottomCloseFade = false,
                                    panelSurfaceCategory = GlassSurfaceCategory.LARGE_PANELS
                                ) {
                                    Box(Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            val panel = compose.onNodeWithTag("pixel-settings-detail-page").fetchSemanticsNode().boundsInRoot
            val band = compose.onNodeWithTag(
                "pixel-settings-detail-page-glasmio-top-backdrop-blur",
                useUnmergedTree = true
            ).fetchSemanticsNode().boundsInRoot
            assertEquals(panel.top, band.top, 1f)
            assertEquals(64f, band.height, 1f)

            val image = hardwareScreenshot()
            val x = (panel.left + panel.width / 2f).roundToInt()
            val sourceContrast = redContrast(image, x, (panel.top - 10f).roundToInt())
            val topBlurContrast = redContrast(image, x, (panel.top + 8f).roundToInt())
            val fadeContrast = redContrast(image, x, (panel.top + 62f).roundToInt())
            val materialContrast = redContrast(image, x, (panel.top + 82f).roundToInt())
            assertTrue("the real panel top must visibly blur the shared striped backdrop: source=$sourceContrast top=$topBlurContrast", topBlurContrast < sourceContrast - 30)
            assertTrue("the fading blur must blend back into the same main material instead of ending as a hard band: fade=$fadeContrast material=$materialContrast", abs(fadeContrast - materialContrast) < 35)
            image.recycle()
        } finally {
            GlassDefaults.enabled = previousGlassEnabled
        }
    }

    @Test
    fun closeFadeIsFaintAndLeavesTheRoundedBottomRimUntouched() {
        assertEquals(0f, settingsMorphBottomFadeAlpha(progress = 1f, closing = true), 0f)
        assertEquals(0f, settingsMorphBottomFadeAlpha(progress = 0f, closing = false), 0f)
        assertEquals(0.0225f, settingsMorphBottomFadeAlpha(progress = 0.5f, closing = true), 0.0001f)
        assertEquals(0.045f, settingsMorphBottomFadeAlpha(progress = 0f, closing = true), 0.0001f)

        val fade = settingsMorphBottomFadeRect(
            widthPx = 400f,
            heightPx = 600f,
            fadeHeightPx = 10f,
            rimClearancePx = 2f
        )
        assertEquals(0f, fade.left, 0f)
        assertEquals(588f, fade.top, 0f)
        assertEquals(400f, fade.right, 0f)
        assertEquals(598f, fade.bottom, 0f)
        assertTrue("fade remains inside the clipped panel and clear of its rim", fade.bottom <= 600f - 2f)
    }

    private fun hardwareScreenshot(): Bitmap {
        val window = compose.activity.window
        val view = window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle {
            PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
        }
        compose.waitForIdle()
        assertEquals(PixelCopy.SUCCESS, result)
        return bitmap
    }

    private fun redContrast(bitmap: Bitmap, x: Int, y: Int): Int {
        val values = (x - 12..x + 12).map { android.graphics.Color.red(bitmap.getPixel(it, y)) }
        return values.max() - values.min()
    }
}
