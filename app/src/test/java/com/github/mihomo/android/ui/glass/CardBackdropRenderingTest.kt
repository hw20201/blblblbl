package com.github.mihomo.android.ui.glass

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class CardBackdropRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
        val values = (x - 10..x + 10).map { android.graphics.Color.red(bitmap.getPixel(it, y)) }
        return values.max() - values.min()
    }

    @Test fun api35BlurKeepsForegroundSharpAndAlignsBackdrop() = verifyBackdrop()

    @Test
    @Config(sdk = [31], qualifiers = "w400dp-h800dp-mdpi")
    fun api31UsesTrueGaussianBackdrop() = verifyBackdrop()

    private fun verifyBackdrop() {
        var cardOrigin = Offset.Zero
        var foregroundCenter = Offset.Zero
        compose.setContent {
            val lens = rememberNavigationLens()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().recordNavigationScene(lens)) {
                    // Different vertical regions expose root-coordinate replay mistakes.
                    var x = 0f
                    while (x < size.width) {
                        val bright = (x / 8f).toInt() % 2 == 0
                        drawRect(if (bright) Color.Blue else Color.Black, Offset(x, 0f), Size(8f, size.height))
                        drawRect(if (bright) Color.Red else Color.Black, Offset(x, size.height * 0.30f), Size(8f, size.height * 0.70f))
                        x += 8f
                    }
                }
                Box(Modifier.width(240.dp).height(120.dp)
                    .onGloballyPositioned { cardOrigin = it.positionInRoot() }) {
                    CardBackdrop(lens, Brush.verticalGradient(listOf(Color.Gray, Color.LightGray)))
                    Canvas(Modifier.align(Alignment.Center).width(80.dp).height(40.dp)
                        .onGloballyPositioned {
                            val p = it.positionInRoot()
                            foregroundCenter = Offset(p.x + it.size.width / 2f, p.y + it.size.height / 2f)
                        }) {
                        drawRect(Color.Yellow)
                        drawRect(Color.Black, Offset(size.width / 2f - 2f, 0f), Size(4f, size.height))
                    }
                }
            }
        }
        compose.waitForIdle()
        val image = hardwareScreenshot()
        val x = cardOrigin.x.roundToInt() + 35
        val y = cardOrigin.y.roundToInt() + 25
        val sharp = redContrast(image, 20, y)
        val blurred = redContrast(image, x, y)
        println("card Gaussian metrics: sharp=$sharp blurred=$blurred origin=$cardOrigin")
        assertTrue("uncovered backdrop retains sharp bars: $sharp", sharp > 200)
        assertTrue("card backdrop is actually blurred: $blurred", blurred < 80)
        val sample = image.getPixel(x, y)
        assertTrue("card samples its root-relative red region, not the blue origin", android.graphics.Color.red(sample) > android.graphics.Color.blue(sample) + 60)
        val fx = foregroundCenter.x.roundToInt()
        val fy = foregroundCenter.y.roundToInt()
        assertTrue("foreground black detail stays crisp", android.graphics.Color.red(image.getPixel(fx, fy)) < 10)
        assertTrue("foreground yellow remains sharp beside detail", android.graphics.Color.red(image.getPixel(fx + 4, fy)) > 245)
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val software = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(AndroidCanvas(software))
            assertTrue("software fallback remains visible", android.graphics.Color.alpha(software.getPixel(x, y)) > 240)
            software.recycle()
        }
        image.recycle()
    }
}
