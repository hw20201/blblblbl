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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/** Actual recorded backdrop + native PixelCopy; the texture exists only in this test fixture. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutContactFollowRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val refraction = mutableStateOf(ShortcutRefraction(frostLift = SHORTCUT_CONTACT_REST_SOFTNESS, contactFollow = true))
    private var card: LayoutCoordinates? = null
    private var density = 1f
    private val visible = mutableStateOf(true)
    private val out = File("build/outputs/contact-follow").also { it.mkdirs() }

    @Test fun localizedClearCoreTracksLeftAndRightTouchAndReleaseRestoresPixels() {
        render("mdpi")
    }

    @Test @Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
    fun xhdpiLocalizedContrastAndUnchangedFarProbe() {
        render("xhdpi")
    }

    @Test @Config(sdk = [31], qualifiers = "w400dp-h800dp-mdpi")
    fun api31BoundedFallbackHasClearCoreAndExactFarProbe() {
        render("api31")
    }

    @Test fun sharedQualityUsesBoundedClarityWithoutRuntimeShader() {
        render("shared", allowShader = false)
    }

    @Test @Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
    fun android13NativeContactShaderPreservesLocality() {
        // Capability-check the unmodified standard syntax first: this runner can have an
        // older SkSL parser even when its Java framework SDK is 33. Never rewrite production
        // AGSL merely to make a test backend green; retain the exact failure as evidence.
        val limitation = try {
            android.graphics.RuntimeShader("uniform shader src; half4 main(float2 p) { return src.eval(p); }")
            null
        } catch (e: IllegalArgumentException) { e.message ?: e.javaClass.simpleName }
        out.resolve("api33-capability.txt").writeText("standardChildShader=" + (limitation ?: "PASS") + "\n")
        if (limitation != null) assertTrue("only the known native parser limitation may be skipped",
            limitation.contains("cannot swizzle value of type 'shader'"))
        org.junit.Assume.assumeTrue("SDK33 native test backend cannot compile standard child.eval: $limitation", limitation == null)
        render("api33")
    }

    @Test @Config(sdk = [31], qualifiers = "w400dp-h800dp-xhdpi")
    fun api31XhdpiFallbackKeepsPhysicalRadiusAndClearCore() = render("api31-xhdpi")

    private fun render(label: String, allowShader: Boolean = true) {
        refraction.value = ShortcutRefraction(frostLift = SHORTCUT_CONTACT_REST_SOFTNESS, contactFollow = true)
        compose.setContent {
            density = androidx.compose.ui.platform.LocalDensity.current.density
            val lens = rememberNavigationLens()
            CompositionLocalProvider(
                LocalShortcutRefractionEnabled provides false,
                LocalShortcutContactFollowEnabled provides true
            ) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().recordNavigationScene(lens)) {
                        drawRect(Color(0xFFE7EAF0))
                        // Equal-frequency, spatially duplicated high-frequency texture for fair probes.
                        for (x in 0 until size.width.toInt() step (4f * density).roundToInt().coerceAtLeast(1)) {
                            val col = if ((x / (4f * density).roundToInt().coerceAtLeast(1)) % 2 == 0) Color(0xFF142638) else Color(0xFFF5B52E)
                            drawRect(col, Offset(x.toFloat(), 0f), Size((2f * density).coerceAtLeast(1f), size.height))
                        }
                        for (y in (20f * density).roundToInt() until size.height.toInt() step (20f * density).roundToInt().coerceAtLeast(1)) {
                            drawRect(Color(0xFF15202B), Offset(0f, y.toFloat()), Size(size.width, (1f * density).coerceAtLeast(1f)))
                        }
                        // Small glyphs and paired fine lines appear at both sampling locations.
                        for (base in listOf(36f, 156f)) {
                            drawLine(Color.Black, Offset(base * density, 35f * density), Offset((base + 32f) * density, 35f * density), 1f * density)
                            drawLine(Color(0xFF202020), Offset(base * density, 86f * density), Offset((base + 32f) * density, 86f * density), 1f * density)
                        }
                    }
                    if (visible.value) Box(Modifier.offset(72.dp, 160.dp).size(240.dp, 120.dp)
                        .onGloballyPositioned { card = it }
                        .clip(ShortcutFollowupCardShape)) {
                        CardBackdrop(lens, androidx.compose.ui.graphics.SolidColor(Color(0xFFE7EAF0)),
                            softMeshRim = true, tunedMaterial = true, contactFrost = true,
                            shaderEnabled = allowShader && android.os.Build.VERSION.SDK_INT >= 33,
                            shortcutRefraction = { refraction.value })
                        // Opaque foreground witness INSIDE the right contact core, outside the variance ROI.
                        Canvas(Modifier.offset(174.dp, 42.dp).size(12.dp, 4.dp)) {
                            drawRect(Color.Yellow)
                            drawLine(Color.Black, Offset(6f * density, 0f), Offset(6f * density, size.height), 2f * density)
                        }
                        Text("TEST", Modifier.padding(start = 13.dp, top = 10.dp), color = Color(0xFF17212A), fontSize = 10.sp)
                        Canvas(Modifier.align(Alignment.BottomEnd).padding(12.dp).size(24.dp)) {
                            drawRect(Color.Yellow)
                            drawLine(Color.Black, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 2f * density)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val o = requireNotNull(card).localToWindow(Offset.Zero)
        val prefix = "${label}-api${android.os.Build.VERSION.SDK_INT}"
        val rest = screenshot("$prefix-rest.png")
        setTouch(-.5f, 0f, 1f)
        val left = screenshot("$prefix-left-contact.png")
        setTouch(.5f, 0f, 1f)
        val right = screenshot("$prefix-right-contact.png")
        setTouch(.5f, 0f, 0f)
        val released = screenshot("$prefix-released.png")
        val leftRest = patchVariance(rest, o, 60, 60, 12)
        val leftTouch = patchVariance(left, o, 60, 60, 12)
        val rightRest = patchVariance(rest, o, 180, 60, 12)
        val rightTouch = patchVariance(right, o, 180, 60, 12)
        assertTrue("left contact clear core must recover backdrop detail: $leftRest -> $leftTouch", leftTouch > leftRest + 8.0)
        assertTrue("right contact clear core must recover backdrop detail: $rightRest -> $rightTouch", rightTouch > rightRest + 8.0)
        val exactFarLeft = exactPatch(left, rest, o, 180, 60, 12)
        val exactFarRight = exactPatch(right, rest, o, 60, 60, 12)
        assertEquals("left tap cannot alter distant right probe byte", 0, exactFarLeft)
        assertEquals("right tap cannot alter distant left probe byte", 0, exactFarRight)
        val releasedDiff = exactPatch(released, rest, o, 0, 0, 0, fullCard = true)
        assertEquals("release restores entire card backdrop exactly", 0, releasedDiff)
        // Opaque icon witness: yellow and black cores are stable through every contact state.
        val iconX = o.x + 240f * density - 24f * density - 12f * density
        val iconY = o.y + 120f * density - 24f * density - 12f * density
        for (image in listOf(left, right, released)) {
            for (x in listOf(176f, 180f)) {
                val px = (o.x + x * density).roundToInt(); val py = (o.y + 44f * density).roundToInt()
                assertEquals("opaque fine icon inside full-clear core is never filtered", rest.getPixel(px, py), image.getPixel(px, py))
            }
            assertEquals("opaque icon yellow remains identical", rest.getPixel((iconX + 4f * density).roundToInt(), (iconY + 12f * density).roundToInt()), image.getPixel((iconX + 4f * density).roundToInt(), (iconY + 12f * density).roundToInt()))
            assertEquals("opaque icon black remains identical", rest.getPixel((iconX + 12f * density).roundToInt(), (iconY + 12f * density).roundToInt()), image.getPixel((iconX + 12f * density).roundToInt(), (iconY + 12f * density).roundToInt()))
        }
        // Check outer round-corner witnesses against the independently captured source scene.
        compose.runOnIdle { visible.value = false }; compose.waitForIdle()
        val source = screenshot("$prefix-source.png")
        val outside = listOf(1 to 1, 238 to 1, 1 to 118, 238 to 118)
        for ((x,y) in outside) {
            val px = (o.x + x * density).roundToInt(); val py = (o.y + y * density).roundToInt()
            assertEquals("rounded exterior pixel is source at $x,$y", source.getPixel(px, py), left.getPixel(px, py))
        }
        val metrics = "$prefix leftVarianceRest=$leftRest leftVarianceContact=$leftTouch rightVarianceRest=$rightRest rightVarianceContact=$rightTouch farLeftChanged=$exactFarLeft farRightChanged=$exactFarRight releasedChanged=$releasedDiff outsideWitnesses=${outside.size}"
        println(metrics); out.resolve("$prefix-metrics.txt").writeText(metrics + "\n")
        listOf(rest,left,right,released,source).forEach { it.recycle() }
    }

    private fun setTouch(x: Float, y: Float, contact: Float) {
        compose.runOnIdle { refraction.value = ShortcutRefraction(frostLift = SHORTCUT_CONTACT_REST_SOFTNESS, x = x, y = y, contact = contact, contactFollow = true) }
        compose.waitForIdle()
    }
    private fun patchVariance(image: Bitmap, o: Offset, x: Int, y: Int, half: Int): Double {
        val vals = mutableListOf<Double>()
        for (py in -half until half) for (px in -half until half) {
            val c = image.getPixel((o.x + (x + px) * density).roundToInt(), (o.y + (y + py) * density).roundToInt())
            vals += (android.graphics.Color.red(c) * .2126 + android.graphics.Color.green(c) * .7152 + android.graphics.Color.blue(c) * .0722)
        }
        val mean = vals.average(); return vals.sumOf { (it - mean) * (it - mean) } / vals.size
    }
    private fun exactPatch(a: Bitmap, b: Bitmap, o: Offset, x: Int, y: Int, half: Int, fullCard: Boolean = false): Int {
        var changed = 0
        val xs = if (fullCard) 0 until (240 * density).roundToInt() else ((x-half)*density).roundToInt() until ((x+half)*density).roundToInt()
        val ys = if (fullCard) 0 until (120 * density).roundToInt() else ((y-half)*density).roundToInt() until ((y+half)*density).roundToInt()
        for (dy in ys) for (dx in xs) if (a.getPixel((o.x+dx).roundToInt(),(o.y+dy).roundToInt()) != b.getPixel((o.x+dx).roundToInt(),(o.y+dy).roundToInt())) changed++
        return changed
    }
    private fun screenshot(name: String): Bitmap {
        val window = compose.activity.window
        val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        val result = AtomicInteger(-1)
        compose.runOnIdle { PixelCopy.request(window, image, { result.set(it) }, Handler(Looper.getMainLooper())) }
        compose.waitUntil(5000) { result.get() != -1 }; assertEquals("native PixelCopy", PixelCopy.SUCCESS, result.get())
        out.resolve(name).outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return image
    }
}
