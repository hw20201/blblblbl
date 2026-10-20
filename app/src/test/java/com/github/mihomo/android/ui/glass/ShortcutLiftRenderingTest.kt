package com.github.mihomo.android.ui.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Focused proof that lift bends the sampled bevel only; the slab and its foreground stay put. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutLiftRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun reset() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    @Test fun liftChangesBevelOnlyAndZeroLiftMatchesOriginalIdle() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        val refraction = mutableStateOf(ShortcutRefraction())
        val explicitRefraction = mutableStateOf(false)
        val centerText = "OPTICAL CENTER"
        val textColor = Color(0xFF101820)
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "en",
                    LocalSoftMeshTime provides androidx.compose.runtime.mutableFloatStateOf(0f),
                    LocalSoftMeshSeed provides 734921L,
                    LocalShortcutGlassDesign provides ShortcutGlassDesign.Hybrid,
                    LocalShortcutGlassQualityOverride provides ShortcutGlassQuality.Refracted) {
                    ProvideGlassTheme {
                        val scene = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            // Flat saturated stripe/grid input, deliberately static across every capture.
                            Box(Modifier.fillMaxSize().recordNavigationScene(scene)) {
                                for (i in 0..15) {
                                    Box(Modifier.align(Alignment.TopStart).padding(start = (i * 25).dp)
                                        .fillMaxSize().padding(end = (400 - i * 25 - 12).coerceAtLeast(0).dp)
                                        .background(if (i % 2 == 0) Color(0xFFE43452) else Color(0xFF20B8D0)))
                                }
                                for (i in 0..11) {
                                    Box(Modifier.align(Alignment.TopStart).padding(top = (i * 66).dp)
                                        .fillMaxSize().padding(bottom = (800 - i * 66 - 10).coerceAtLeast(0).dp)
                                        .background(if (i % 2 == 0) Color(0xFF3247D5) else Color(0xFFFFC52E)))
                                }
                            }
                            if (explicitRefraction.value) {
                                GlassSurface(
                                    modifier = Modifier.align(Alignment.Center).size(width = 280.dp, height = 210.dp),
                                    lens = scene, blurEnabled = false, shortcutRefraction = { refraction.value }
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(centerText, color = textColor)
                                    }
                                }
                            } else {
                                GlassSurface(
                                    modifier = Modifier.align(Alignment.Center).size(width = 280.dp, height = 210.dp),
                                    lens = scene, blurEnabled = false
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(centerText, color = textColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val textBounds = compose.onNodeWithText(centerText).fetchSemanticsNode().boundsInRoot
        val originalIdle = screenshot()
        compose.runOnIdle { explicitRefraction.value = true }
        compose.waitForIdle()
        val idle = screenshot()
        save(idle, "idle")
        assertEquals("explicit zero lift must match original no-supplier idle rendering", 0.0,
            difference(originalIdle, idle, Rect(0f, 0f, idle.width.toFloat(), idle.height.toFloat())), 0.0)

        compose.runOnIdle { refraction.value = ShortcutRefraction(lift = 1f, x = .75f, y = -.55f) }
        compose.waitForIdle()
        val lifted = screenshot()
        save(lifted, "lift")

        compose.runOnIdle { refraction.value = ShortcutRefraction() }
        compose.waitForIdle()
        val restored = screenshot()
        save(restored, "restored")

        // The scene is fixed: only the card-local bevel can differ, not its outer geometry.
        val card = Rect((idle.width - 280f) / 2f, (idle.height - 210f) / 2f,
            (idle.width + 280f) / 2f, (idle.height + 210f) / 2f)
        val bevel = Rect(card.left + 1f, card.top + 2f, card.right - 1f, card.top + 7f)
        val center = Rect(card.left + 45f, card.top + 45f, card.right - 45f, card.bottom - 45f)
        val textRegion = Rect(textBounds.left, textBounds.top, textBounds.right, textBounds.bottom)
        val bevelDelta = difference(idle, lifted, bevel)
        val centerDelta = difference(idle, lifted, center)
        println("lift optics: bevelDelta=$bevelDelta centerDelta=$centerDelta card=$card text=$textBounds")
        assertTrue("lift must alter colored background pixels in the bevel: $bevelDelta", bevelDelta > 0.35)
        assertEquals("flat center must not be refracted", 0.0, centerDelta, 0.0)
        assertEquals("lift=0 exactly restores the idle render", 0.0, difference(idle, restored,
            Rect(0f, 0f, idle.width.toFloat(), idle.height.toFloat())), 0.0)
        assertEquals("opaque foreground glyph region stays pixel-identical", 0.0,
            difference(idle, lifted, textRegion), 0.0)
        assertTrue("test text color available", textColor.toArgb() != Color.Transparent.toArgb())
        originalIdle.recycle(); idle.recycle(); lifted.recycle(); restored.recycle()
    }

    private fun screenshot(): Bitmap {
        val window = compose.activity.window
        val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        val result = AtomicInteger(-1)
        compose.runOnIdle { PixelCopy.request(window, bitmap, { result.set(it) }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) { result.get() != -1 }
        assertEquals(PixelCopy.SUCCESS, result.get())
        return bitmap
    }

    private fun save(image: Bitmap, name: String) {
        val dir = File("build/outputs/shortcut-refraction").also { it.mkdirs() }
        dir.resolve("$name.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun difference(a: Bitmap, b: Bitmap, area: Rect): Double {
        var total = 0L
        var count = 0
        for (y in area.top.toInt() until area.bottom.toInt()) for (x in area.left.toInt() until area.right.toInt()) {
            total += rgb(a.getPixel(x, y), b.getPixel(x, y)); count++
        }
        return total.toDouble() / count
    }

    private fun rgb(a: Int, b: Int): Int = abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
        abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
        abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
}
