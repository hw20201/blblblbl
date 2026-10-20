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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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

/** Pixel evidence for the actual shared scene and GlassSurface (not a material mock). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutTunedFrostRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun lightContactHalfLiftReleaseAndLegacy() = render(false)
    @Test fun darkContactHalfLiftReleaseAndLegacy() = render(true)
    @Test @Config(sdk = [31], qualifiers = "w400dp-h800dp-mdpi")
    fun api31Fallback() = render(false)

    private fun render(dark: Boolean) {
        val lift = mutableFloatStateOf(0f)
        val tuned = mutableStateOf(true)
        var card = Rect.Zero
        var textBounds = Rect.Zero
        var iconBounds = Rect.Zero
        val titleColor = if (dark) Color.White else Color(0xFF101820)
        compose.setContent {
            MihomoTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalShortcutTunedMaterialEnabled provides tuned.value) {
                    ProvideGlassTheme {
                        val lens = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().recordNavigationScene(lens)) {
                                Canvas(Modifier.fillMaxSize()) {
                                    drawRect(if (dark) Color(0xFF122030) else Color(0xFFD4E3EE))
                                    for (x in 0 until size.width.toInt() step 8) {
                                        drawRect(if ((x / 8) % 2 == 0) Color(0xFFE03B58) else Color(0xFF25BCD4), Offset(x.toFloat(), 0f), Size(8f, size.height))
                                    }
                                    for (y in 0 until size.height.toInt() step 64) drawRect(Color(0xFFFFCF48), Offset(0f, y.toFloat()), Size(size.width, 4f))
                                }
                                Text("NEXUS / OBJECT 01", Modifier.padding(start = 65.dp, top = 145.dp), color = Color.Black, fontSize = 22.sp)
                            }
                            GlassSurface(Modifier.align(Alignment.TopCenter).padding(top = 100.dp).size(320.dp, 180.dp)
                                .onGloballyPositioned { card = it.boundsInRoot() }, lens = lens, shortcutContactFrost = true,
                                shortcutRefraction = { ShortcutRefraction(lift = lift.floatValue, frostLift = lift.floatValue) }) {
                                Text("TUNED · readable label", Modifier.padding(16.dp).onGloballyPositioned { textBounds = it.boundsInRoot() }, color = titleColor, fontSize = 17.sp)
                                Text("Background detail softens", Modifier.align(Alignment.BottomStart).padding(16.dp), fontSize = 13.sp)
                                Canvas(Modifier.align(Alignment.Center).size(80.dp, 32.dp).onGloballyPositioned { iconBounds = it.boundsInRoot() }) {
                                    drawRect(Color.Yellow); drawRect(Color.Black, Offset(38f, 0f), Size(4f, size.height))
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val mode = if (dark) "dark" else "light"
        val dir = File("build/outputs/tuned-frost-static/api${android.os.Build.VERSION.SDK_INT}").also { it.mkdirs() }
        val contrasts = mutableListOf<Int>()
        var contact: Bitmap? = null
        listOf("contact" to 0f, "half" to .5f, "lift" to 1f, "release" to 0f).forEach { (label, value) ->
            compose.runOnIdle { tuned.value = true; lift.floatValue = value }
            compose.waitForIdle()
            val image = screenshot()
            save(image, dir, "$mode-$label.png")
            val contrast = contrast(image, card)
            contrasts += contrast
            if (label == "contact") contact = image.copy(Bitmap.Config.ARGB_8888, false)
            if (label == "release") assertTrue("tuned release returns exactly to tuned contact", contact!!.sameAs(image))
            if (value > 0f) assertOutsideSame(contact!!, image, card)
            assertOpaqueTextStable(contactOrImage(contact, image), image, textBounds, titleColor)
            val ix = iconBounds.center.x.toInt(); val iy = iconBounds.center.y.toInt()
            assertTrue("foreground black witness remains crisp", android.graphics.Color.red(image.getPixel(ix, iy)) < 10)
            assertTrue("foreground yellow witness remains crisp", android.graphics.Color.red(image.getPixel(ix + 4, iy)) > 245)
            image.recycle()
        }
        assertTrue("lift should soften backdrop more than half lift", contrasts[1] > contrasts[2])
        assertTrue("full lift should visibly soften static fine detail", contrasts[2] < contrasts[0] * .8)
        compose.runOnIdle { tuned.value = false; lift.floatValue = 1f }
        compose.waitForIdle()
        val legacy = screenshot(); save(legacy, dir, "$mode-legacy-lift.png")
        // Legacy is a visual comparison artifact only; tuned geometry/color is intentionally not pixel-equal.
        val metrics = "$mode contact=${contrasts[0]} half=${contrasts[1]} lift=${contrasts[2]} release=${contrasts[3]} legacyLift=${contrast(legacy, card)}\n"
        dir.resolve("$mode-metrics.txt").writeText(metrics)
        println(metrics)
        contact?.recycle(); legacy.recycle()
    }

    private fun contactOrImage(contact: Bitmap?, image: Bitmap) = contact ?: image
    private fun assertOpaqueTextStable(reference: Bitmap, image: Bitmap, rect: Rect, color: Color) {
        var cores = 0
        for (y in rect.top.toInt().coerceAtLeast(0) until rect.bottom.toInt().coerceAtMost(image.height))
            for (x in rect.left.toInt().coerceAtLeast(0) until rect.right.toInt().coerceAtMost(image.width))
                if (reference.getPixel(x, y) == color.toArgb()) { cores++; assertEquals("opaque glyph pixel", reference.getPixel(x, y), image.getPixel(x, y)) }
        assertTrue("glyph witness present", cores > 20)
    }
    private fun assertOutsideSame(a: Bitmap, b: Bitmap, r: Rect) {
        for (y in 0 until b.height) for (x in 0 until b.width) {
            val outsideX = x < r.left || x >= r.right
            val outsideY = y < r.top || y >= r.bottom
            if (outsideX || outsideY) assertEquals("outside surface changed at $x,$y", a.getPixel(x, y), b.getPixel(x, y))
        }
    }
    private fun contrast(image: Bitmap, r: Rect): Int {
        val y = (r.top + 120).toInt(); val values = ((r.left + 50).toInt()..(r.left + 100).toInt()).map { android.graphics.Color.red(image.getPixel(it, y)) }
        return values.max() - values.min()
    }
    private fun screenshot(): Bitmap {
        val window = compose.activity.window
        val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        val result = AtomicInteger(-1)
        compose.runOnIdle { PixelCopy.request(window, image, { result.set(it) }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle(); compose.waitUntil(5000) { result.get() != -1 }; assertEquals(PixelCopy.SUCCESS, result.get()); return image
    }
    private fun save(image: Bitmap, dir: File, name: String) { dir.resolve(name).outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
}
