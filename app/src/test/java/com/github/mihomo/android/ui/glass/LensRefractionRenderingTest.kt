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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LensRefractionRenderingTest {
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
        assertEquals("native PixelCopy must succeed", PixelCopy.SUCCESS, result)
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File("build/reports/navigation-lens-polished/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun rgbDifference(a: Bitmap, b: Bitmap, xs: IntRange, ys: IntRange): Long {
        var total = 0L
        for (y in ys) for (x in xs) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            total += abs(android.graphics.Color.red(p) - android.graphics.Color.red(q))
            total += abs(android.graphics.Color.green(p) - android.graphics.Color.green(q))
            total += abs(android.graphics.Color.blue(p) - android.graphics.Color.blue(q))
        }
        return total
    }

    private fun contrast(bitmap: Bitmap, cx: Int, cy: Int): Int {
        val gray = (cx - 12..cx + 12).map { x ->
            val p = bitmap.getPixel(x, cy)
            (android.graphics.Color.red(p) + android.graphics.Color.green(p) + android.graphics.Color.blue(p)) / 3
        }
        return gray.max() - gray.min()
    }

    @Test
    fun api35ComposeSurfaceRefractsOnlyBevelAndPreservesSharpFlatCenter() {
        val refraction = mutableStateOf(0f)
        val sceneColor = mutableStateOf(false)
        var center = Offset.Zero
        var surfaceCompositions = 0
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    val lens = rememberNavigationLens()
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize().recordNavigationScene(lens)) {
                            // 12px checker/grid with saturated tiles: both displacement and RGB split are measurable.
                            val tile = 12f
                            var y = 0f
                            var row = 0
                            while (y < size.height) {
                                var x = 0f
                                var col = 0
                                while (x < size.width) {
                                    val index = (row + col) % 4
                                    val color = when (index) {
                                        0 -> if (sceneColor.value) Color.Yellow else Color(0xFFE32929)
                                        1 -> Color(0xFF24D8E8)
                                        2 -> Color(0xFF2736E8)
                                        else -> Color.White
                                    }
                                    drawRect(color, Offset(x, y), Size(tile, tile))
                                    x += tile
                                    col++
                                }
                                y += tile
                                row++
                            }
                        }
                        NavigationGlassSurface(
                            Modifier.width(160.dp).height(64.dp).onGloballyPositioned {
                                val p = it.positionInWindow()
                                center = Offset(p.x + it.size.width / 2f, p.y + it.size.height / 2f)
                            },
                            lens = lens,
                            cornerRadius = 1000.dp,
                            strength = LensStrength.STRONG,
                            expansionProgress = { 0f },
                            refractionAmount = { refraction.value }
                        ) { SideEffect { surfaceCompositions++ } }
                    }
                }
            }
        }
        compose.waitForIdle()
        val flat = screenshot()
        save(flat, "optics-flat")
        compose.runOnIdle { refraction.value = 1f }
        compose.waitForIdle()
        val refracted = screenshot()
        save(refracted, "optics-refracted")

        val cx = center.x.toInt()
        val cy = center.y.toInt()
        // Flat interior excludes the fixed rim/tint and must remain exactly (or nearly) unchanged.
        val centerDiff = rgbDifference(flat, refracted, cx - 16..cx + 16, cy - 8..cy + 8)
        val bevelDiff = rgbDifference(flat, refracted, cx - 79..cx + 79, cy - 31..cy + 31) - centerDiff
        val centerContrast = minOf(contrast(flat, cx, cy), contrast(refracted, cx, cy))
        println("navigation lens optical metrics: centerDiff=$centerDiff bevelRegionDiff=$bevelDiff centerContrast=$centerContrast")
        assertTrue("center pixels should be unchanged by edge-only refraction: $centerDiff", centerDiff <= 3L * 33 * 17)
        assertTrue("bevel must visibly displace/dispersion-split the sampled scene: $bevelDiff", bevelDiff > 20_000L)
        assertTrue("high-frequency grid contrast remains sharp in the flat center (not Gaussian blurred): $centerContrast", centerContrast > 35)
        assertTrue("surface is centered in its window", abs(center.x - compose.activity.window.decorView.width / 2f) < 2f)

        // A scene-only redraw updates the recorded GPU scene, with no surface recomposition.
        val compositionsBefore = surfaceCompositions
        compose.runOnIdle { sceneColor.value = true }
        compose.waitForIdle()
        val updated = screenshot()
        val liveDifference = rgbDifference(refracted, updated, cx - 60..cx + 60, cy - 24..cy + 24)
        println("navigation lens live scene: surfaceCompositions=$surfaceCompositions liveRegionDiff=$liveDifference")
        assertEquals("scene redraw must not recompose the glass surface", compositionsBefore, surfaceCompositions)
        assertTrue("live scene update reaches the refracted surface", liveDifference > 10_000L)
        save(updated, "optics-live-scene-updated")
        flat.recycle()
        refracted.recycle()
        updated.recycle()
    }
}
