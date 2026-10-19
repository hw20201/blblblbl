package com.github.mihomo.android.ui.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
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
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** Production GlassSurface over the actual shared GraphicsLayer recorder; screenshots are native PixelCopy. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutGlassResponseRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun api35RuntimeShaderEdgeTracksDrawOnlySceneChanges() = verify()

    @Test
    @Config(sdk = [31], qualifiers = "w400dp-h800dp-mdpi")
    fun api31RestrainedRimEdgeTracksDrawOnlySceneChanges() = verify()

    private fun verify() {
        val phase = mutableFloatStateOf(0f)
        var compositions = 0
        var origin = Offset.Zero
        compose.setContent {
            val scene = rememberNavigationLens()
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().recordNavigationScene(scene)) {
                    // This read is in draw only: alternate broad red/blue soft gradients, with
                    // curved colour contours to prove the card samples the shared recorded scene.
                    val shift = phase.floatValue
                    val stops = if (shift < 0.5f) listOf(Color(0xFFE52E35), Color(0xFF2638D8))
                        else listOf(Color(0xFF2443D8), Color(0xFFE52E35))
                    drawRect(Brush.linearGradient(stops, start = Offset(0f, size.height * (0.28f + shift * .08f)),
                        end = Offset(size.width, size.height * (0.68f - shift * .08f))))
                    // A broad softly varying contour also makes accidental fixed-white rims obvious.
                    drawOval(Color(0xFF25A96B).copy(alpha = .38f),
                        topLeft = Offset(size.width * (.38f + shift * .08f), size.height * .18f),
                        size = Size(size.width * .34f, size.height * .54f))
                }
                Box(Modifier.align(Alignment.Center).width(240.dp).height(120.dp)
                    .onGloballyPositioned { origin = it.positionInRoot() }) {
                    SideEffect { compositions++ }
                    GlassSurface(modifier = Modifier.fillMaxSize(), lens = scene) {
                        // High-contrast production-independent foreground marker, deliberately
                        // overlaid above the real GlassSurface material.
                        Canvas(Modifier.align(Alignment.Center).width(72.dp).height(36.dp)) {
                            drawRect(Color.Yellow)
                            drawRect(Color.Black, Offset(size.width / 2f - 2f, 0f), Size(4f, size.height))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val count = compositions
        val first = screenshot()
        val fixedX = origin.x.roundToInt() + 48
        val edgeY = origin.y.roundToInt() + 1
        val edgeA = first.getPixel(fixedX, edgeY)
        val name = "api${android.os.Build.VERSION.SDK_INT}-shortcut-response"
        save(first, "$name-red-blue-a.png")

        compose.runOnIdle { phase.floatValue = 1f }
        compose.waitForIdle()
        val second = screenshot()
        save(second, "$name-red-blue-b.png")

        assertEquals("background draw-phase update must not recompose the card tree", count, compositions)
        assertEquals("card stays at same root x", origin.x.roundToInt() + 48, fixedX)
        val edgeB = second.getPixel(fixedX, edgeY)
        val delta = rgbDistance(edgeA, edgeB)
        println("shortcut edge response api=${android.os.Build.VERSION.SDK_INT}: origin=$origin sample=($fixedX,$edgeY) edgeRGB=${android.graphics.Color.red(edgeA)},${android.graphics.Color.green(edgeA)},${android.graphics.Color.blue(edgeA)} -> ${android.graphics.Color.red(edgeB)},${android.graphics.Color.green(edgeB)},${android.graphics.Color.blue(edgeB)} delta=$delta compositions=$compositions")
        assertTrue("same physical edge pixel must respond to changed shared backdrop, not remain fixed white: delta=$delta", delta > 24)
        // The centre marker is in front of the sampled material. Check crisp black/yellow cores
        // survive both background states at exactly the same pixel coordinates.
        val markerX = origin.x.roundToInt() + 120
        val markerY = origin.y.roundToInt() + 60
        for (image in listOf(first, second)) {
            val black = image.getPixel(markerX, markerY)
            val yellow = image.getPixel(markerX + 5, markerY)
            assertTrue("black foreground core remains sharp", android.graphics.Color.red(black) < 15 && android.graphics.Color.green(black) < 15)
            assertTrue("yellow foreground remains sharp", android.graphics.Color.red(yellow) > 235 && android.graphics.Color.green(yellow) > 220)
        }
        first.recycle()
        second.recycle()
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
        val file = File("build/reports/shortcut-response/$name")
        file.parentFile!!.mkdirs()
        file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun rgbDistance(a: Int, b: Int): Int =
        abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
            abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
            abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
}
