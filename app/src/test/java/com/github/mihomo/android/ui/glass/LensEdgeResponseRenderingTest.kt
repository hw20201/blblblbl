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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LensEdgeResponseRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun reset() { GlassDefaults.enabled = true; GlassDefaults.palette = GlassPalettes.CREAM }

    @Test fun movingLensRimPicksUpPassingColorContoursAndLiveSceneWithoutFixedWhiteOutline() {
        val lensOffset = mutableIntStateOf(0)
        val contourOffset = mutableFloatStateOf(20f)
        var center = Offset.Zero
        var compositions = 0
        GlassDefaults.enabled = true
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    val scene = rememberNavigationLens()
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize().recordNavigationScene(scene)) {
                            drawRect(Color(0xFF174EE8))
                            // The moving optical edge crosses this high-contrast content contour.
                            drawRect(Color(0xFFE42B32), Offset.Zero,
                                Size(size.width / 2f + contourOffset.floatValue, size.height))
                        }
                        NavigationGlassSurface(
                            modifier = Modifier.offset { IntOffset(lensOffset.intValue, 0) }
                                .width(180.dp).height(64.dp).onGloballyPositioned {
                                    val origin = it.positionInWindow()
                                    center = Offset(origin.x + it.size.width / 2f, origin.y + it.size.height / 2f)
                                },
                            lens = scene, cornerRadius = 1000.dp, strength = LensStrength.STRONG
                        ) { SideEffect { compositions++ } }
                    }
                }
            }
        }
        compose.waitForIdle()
        fun rimDominance(bitmap: Bitmap): Float {
            val y = (center.y - 32f).roundToInt()
            return (-8..8).map { dx ->
                val pixel = bitmap.getPixel(center.x.roundToInt() + dx, y)
                (android.graphics.Color.red(pixel) - android.graphics.Color.blue(pixel)).toFloat()
            }.average().toFloat()
        }
        val red = screenshot("edge-over-red-contour")
        val redRim = rimDominance(red)
        val compositionsBefore = compositions
        compose.runOnIdle { lensOffset.intValue = 60 }
        compose.waitForIdle()
        val blue = screenshot("edge-slid-over-blue-contour")
        val blueRim = rimDominance(blue)
        assertTrue("rim retains the red contour, not a fixed white stroke: $redRim", redRim > 60f)
        assertTrue("same rim follows the blue contour after sliding: $blueRim", blueRim < -60f)
        compose.runOnIdle { contourOffset.floatValue = 110f }
        compose.waitForIdle()
        val updated = screenshot("edge-live-contour-under-stationary-lens")
        val updatedRim = rimDominance(updated)
        assertTrue("a scene-only redraw changes rim color: $updatedRim", updatedRim > 60f)
        assertEquals("no surface recomposition for movement or scene drawing", compositionsBefore, compositions)
        println("Robolectric native AGSL rim R-B: red=$redRim blue=$blueRim scene-only=$updatedRim; surfaceCompositions=$compositions")
        red.recycle(); blue.recycle(); updated.recycle()
    }

    private fun screenshot(name: String): Bitmap {
        val window = compose.activity.window
        val view = window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle()
        assertEquals(PixelCopy.SUCCESS, result)
        val output = File("build/reports/lens-edge-response/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return image
    }
}
