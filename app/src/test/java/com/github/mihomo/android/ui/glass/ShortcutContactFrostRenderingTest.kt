package com.github.mihomo.android.ui.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Actual shared-scene material, not a painted approximation. Geometry is held still so
 * backdrop filtering can be measured independently of the production slab's transform.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutContactFrostRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun lightContactLiftRelease() = render(false)
    @Test fun darkContactLiftRelease() = render(true)
    @Test @Config(sdk = [31], qualifiers = "w400dp-h800dp-mdpi")
    fun api31NativeFrost() = render(false)

    private fun render(dark: Boolean) {
        val separation = mutableFloatStateOf(0f)
        val frostEnabled = mutableStateOf(true)
        var card = Rect.Zero
        var witness = Rect.Zero
        var titleBounds = Rect.Zero
        val titleColor = if (dark) Color.White else Color(0xFF101820)
        var sceneDraws = 0
        compose.setContent {
            MihomoTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    val scene = rememberNavigationLens()
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().recordNavigationScene(scene)) {
                            Canvas(Modifier.fillMaxSize()) {
                                sceneDraws++
                                drawRect(if (dark) Color(0xFF122030) else Color(0xFFD4E3EE))
                                // Fixed, high-frequency detail makes contact-vs-lift unambiguous.
                                for (x in 0 until size.width.toInt() step 8) {
                                    drawRect(if ((x / 8) % 2 == 0) Color(0xFFE03B58) else Color(0xFF25BCD4),
                                        Offset(x.toFloat(), 0f), Size(8f, size.height))
                                }
                                for (y in 0 until size.height.toInt() step 64) {
                                    drawRect(Color(0xFFFFCF48), Offset(0f, y.toFloat()), Size(size.width, 4f))
                                }
                            }
                            Text("NEXUS / OBJECT 01", Modifier.padding(start = 65.dp, top = 145.dp),
                                color = Color.Black, fontSize = 22.sp)
                        }
                        Column(Modifier.align(Alignment.TopCenter).padding(top = 100.dp), verticalArrangement = Arrangement.spacedBy(36.dp)) {
                            GlassSurface(Modifier.size(320.dp, 180.dp).onGloballyPositioned { card = it.boundsInRoot() },
                                lens = scene, shortcutContactFrost = frostEnabled.value,
                                shortcutRefraction = { ShortcutRefraction(lift = separation.floatValue) }) {
                                Text("TOUCHED · Glass surface", Modifier.padding(16.dp)
                                    .onGloballyPositioned { titleBounds = it.boundsInRoot() }, color = titleColor, fontSize = 17.sp)
                                Text("Only the object below softens", Modifier.align(Alignment.BottomStart).padding(16.dp), fontSize = 13.sp)
                                Canvas(Modifier.align(Alignment.Center).size(80.dp, 32.dp)
                                    .onGloballyPositioned { witness = it.boundsInRoot() }) {
                                    drawRect(Color.Yellow)
                                    drawRect(Color.Black, Offset(38f, 0f), Size(4f, size.height))
                                }
                            }
                            GlassSurface(Modifier.size(320.dp, 180.dp), lens = scene, shortcutContactFrost = true) {
                                Text("UNTOUCHED · stays clear", Modifier.padding(16.dp), fontSize = 17.sp)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val api = android.os.Build.VERSION.SDK_INT
        val prefix = "api$api-${if (dark) "dark" else "light"}"
        val dir = File("build/outputs/contact-frost").also { it.mkdirs() }
        val idle = screenshot()
        save(idle, dir, "$prefix-0-idle")
        val sourceDrawsAtIdle = sceneDraws
        val frames = listOf("1-contact" to 0f, "2-half-lift" to .5f, "3-lift" to 1f, "4-release" to 0f)
        val contrasts = mutableListOf<Int>()
        val baselineContrast = contrast(idle, card)
        frames.forEach { (name, lift) ->
            compose.runOnIdle { separation.floatValue = lift }
            compose.waitForIdle()
            val image = screenshot()
            save(image, dir, "$prefix-$name")
            val currentContrast = contrast(image, card)
            contrasts += currentContrast
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val outsideX = x < card.left || x >= card.right
                val outsideY = y < card.top || y >= card.bottom
                if (outsideX || outsideY) {
                    assertEquals("frost must not affect outside/touch neighbour at $x,$y", idle.getPixel(x, y), image.getPixel(x, y))
                }
            }
            var glyphCores = 0
            for (y in titleBounds.top.toInt() until titleBounds.bottom.toInt()) {
                for (x in titleBounds.left.toInt() until titleBounds.right.toInt()) {
                    if (idle.getPixel(x, y) == titleColor.toArgb()) {
                        glyphCores++
                        assertEquals("opaque label pixels must not be diffused", idle.getPixel(x, y), image.getPixel(x, y))
                    }
                }
            }
            assertTrue("verify actual glyphs, not only the edge witness", glyphCores > 40)
            val cx = witness.center.x.toInt()
            val cy = witness.center.y.toInt()
            assertTrue("black foreground edge stays crisp", android.graphics.Color.red(image.getPixel(cx, cy)) < 10)
            assertTrue("yellow foreground beside edge stays crisp", android.graphics.Color.red(image.getPixel(cx + 4, cy)) > 245)
            if (lift == 0f) assertTrue("contact and release exactly match idle", idle.sameAs(image))
            if (lift == 1f) assertTrue("8dp frost removes fine backdrop detail: $currentContrast / $baselineContrast", currentContrast < baselineContrast * .25)
            image.recycle()
        }
        assertTrue("frost must increase with separation", contrasts[1] > contrasts[2])
        assertEquals("card effect changes must not recapture static scene", sourceDrawsAtIdle, sceneDraws)
        // Compare with the previous material at exactly the same lifted optical pose.
        compose.runOnIdle { separation.floatValue = 1f; frostEnabled.value = false }
        compose.waitForIdle()
        val oldLift = screenshot()
        save(oldLift, dir, "$prefix-old-material-lift")
        assertEquals("old material retains clear centre", baselineContrast, contrast(oldLift, card))
        val metrics = "$prefix: contact=$baselineContrast half=${contrasts[1]} lift=${contrasts[2]} release=${contrasts[3]} staticSourceDraws=$sourceDrawsAtIdle\n"
        println(metrics)
        dir.resolve("$prefix-metrics.txt").writeText(metrics)
        idle.recycle(); oldLift.recycle()
    }

    private fun contrast(image: Bitmap, card: Rect): Int {
        val y = (card.top + 120f).toInt()
        val values = ((card.left + 50).toInt()..(card.left + 100).toInt()).map { android.graphics.Color.red(image.getPixel(it, y)) }
        return values.max() - values.min()
    }
    private fun screenshot(): Bitmap {
        val window = compose.activity.window
        val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        val result = AtomicInteger(-1)
        compose.runOnIdle { PixelCopy.request(window, image, { result.set(it) }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle()
        compose.waitUntil(5000) { result.get() != -1 }
        assertEquals(PixelCopy.SUCCESS, result.get())
        return image
    }
    private fun save(image: Bitmap, dir: File, name: String) {
        dir.resolve("$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
