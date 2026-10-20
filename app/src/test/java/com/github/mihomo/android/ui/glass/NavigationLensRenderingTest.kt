package com.github.mihomo.android.ui.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
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
class NavigationLensRenderingTest {
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
        val file = File("build/reports/navigation-lens-polished/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun contrast(bitmap: Bitmap, center: Offset, yOffset: Int = 0): Double {
        val values = (center.x.toInt() - 100..center.x.toInt() + 100).map { x ->
            val p = bitmap.getPixel(x, center.y.toInt() + yOffset)
            .2126 * android.graphics.Color.red(p) + .7152 * android.graphics.Color.green(p) + .0722 * android.graphics.Color.blue(p)
        }
        return values.max() - values.min()
    }

    @Test fun api35LensRefractsAndTracksSceneDrawWithoutSurfaceRecomposition() = verifyLiveLens("api35")

    @Test @Config(sdk = [31], qualifiers = "w400dp-h800dp-mdpi")
    fun api31FallsBackToSharpSceneWithoutClaimingRefraction() = verifyLiveLens("api31")

    private fun verifyLiveLens(name: String) {
        val colorful = mutableStateOf(false)
        var surfaceCompositions = 0
        var center = Offset.Zero
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    val lens = rememberNavigationLens()
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize().recordNavigationScene(lens)) {
                            var x = 0f
                            var index = 0
                            while (x < size.width) {
                                val color = if (colorful.value) {
                                    if (index % 2 == 0) Color.Red else Color.Blue
                                } else if (index % 2 == 0) Color.Black else Color.White
                                drawRect(color, Offset(x, 0f), Size(8f, size.height))
                                x += 8f
                                index++
                            }
                        }
                        NavigationGlassSurface(Modifier.width(300.dp).height(100.dp).onGloballyPositioned {
                            val p = it.positionInWindow()
                            center = Offset(p.x + it.size.width / 2f, p.y + it.size.height / 2f)
                        }, lens) { SideEffect { surfaceCompositions++ } }
                    }
                }
            }
        }
        compose.waitForIdle()
        val first = screenshot()
        val inside = contrast(first, center)
        val outside = contrast(first, center, 90)
        println("$name stripe contrast: lens=$inside clear=$outside")
        assertTrue("clear stripes are present", outside > 180)
        assertTrue("no Gaussian blur: centre stripes remain sharp: lens=$inside clear=$outside", inside > outside * .65)
        save(first, "$name-sharp-scene")
        val compositionsBefore = surfaceCompositions
        compose.runOnIdle { colorful.value = true }
        compose.waitForIdle()
        val updated = screenshot()
        assertEquals("scene drawing updates without recomposing the lens", compositionsBefore, surfaceCompositions)
        assertNotEquals("scene-only redraw reaches the navigation material", first.getPixel(center.x.toInt(), center.y.toInt()),
            updated.getPixel(center.x.toInt(), center.y.toInt()))
        save(updated, "$name-live-update")
        // Bitmap Canvas must bypass RenderNode replay safely, even on Android 35.
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val software = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(software))
            assertTrue(android.graphics.Color.alpha(software.getPixel(center.x.toInt(), center.y.toInt())) > 0)
            save(software, "$name-software-fallback")
            software.recycle()
        }
        first.recycle()
        updated.recycle()
    }

    @Test @Config(sdk = [30], qualifiers = "w400dp-h800dp-mdpi")
    fun api30UsesTranslucentFallbackWithoutRecordingScene() {
        var state: NavigationLensState? = null
        var center = Offset.Zero
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    val lens = rememberNavigationLens()
                    state = lens
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxSize().recordNavigationScene(lens).background(Color(0xFF253B55)))
                        NavigationGlassSurface(Modifier.width(280.dp).height(90.dp).onGloballyPositioned {
                            val p = it.positionInWindow()
                            center = Offset(p.x + it.size.width / 2f, p.y + it.size.height / 2f)
                        }, lens) { }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, state!!.scene.size.width) }
        val image = screenshot()
        assertNotEquals("fallback tint remains visible", Color(0xFF253B55).toArgb(), image.getPixel(center.x.toInt(), center.y.toInt()))
        save(image, "api30-fallback")
        image.recycle()
    }
}
