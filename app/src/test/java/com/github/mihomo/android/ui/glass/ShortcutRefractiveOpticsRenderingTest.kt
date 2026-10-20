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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.abs
import kotlin.math.roundToInt

/** TEST-ONLY high-contrast source. No stripes are added to production UI. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutRefractiveOpticsRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val followup = mutableStateOf(true)
    private val bend = mutableStateOf(true)
    private val visible = mutableStateOf(true)
    private val pose = mutableStateOf(Pose())
    private val refraction = mutableStateOf(ShortcutRefraction())
    private var card: LayoutCoordinates? = null
    private var d = 1f
    private val out = File("build/outputs/refractive-followup-static").also { it.mkdirs() }
    private data class Pose(val x: Float = 0f, val y: Float = 0f, val scale: Float = 1f, val rx: Float = 0f, val ry: Float = 0f)

    @Test fun mdpiStripeContoursActuallyMoveWhileCenterAndForegroundStaySharp() = lineEvidence("mdpi")
    @Test @Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
    fun xhdpiStripeContoursRetainRoundedClipAndPosition() = lineEvidence("xhdpi")

    private fun content(flat: Boolean = false) {
        compose.setContent {
            d = androidx.compose.ui.platform.LocalDensity.current.density
            val lens = rememberNavigationLens()
            CompositionLocalProvider(LocalShortcutOpticalFollowupEnabled provides followup.value,
                LocalShortcutRefractionEnabled provides bend.value) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().recordNavigationScene(lens)) {
                        drawRect(if (flat) Color(0xFFCDD7E5) else Color.White)
                        if (!flat) {
                            // Non-periodic coded stripes: edge witnesses at 8dp; a separate
                            // 156dp witness in the optically flat centre for world anchoring.
                            for (x in listOf(80f, 228f)) drawRect(Color.Black, Offset(x * d, 0f), Size(3f * d, size.height))
                            drawRect(Color.Black, Offset(0f, 168f * d), Size(size.width, 3f * d))
                        }
                    }
                    if (visible.value) Box(Modifier.offset(72.dp, 160.dp).size(240.dp, 120.dp)
                        .graphicsLayer {
                            val p = pose.value
                            translationX = p.x * d; translationY = p.y * d
                            scaleX = p.scale; scaleY = p.scale
                            rotationX = p.rx; rotationY = p.ry; cameraDistance = 500f * d
                        }
                        .onGloballyPositioned { card = it }
                        .clip(if (followup.value) ShortcutFollowupCardShape else ShortcutTunedCardShape)) {
                        CardBackdrop(lens, Brush.linearGradient(listOf(Color.Gray, Color.LightGray)), softMeshRim = true,
                            tunedMaterial = true, shortcutRefraction = {
                                // Same draw-phase pose observation used by the actual motion callback.
                                pose.value
                                refraction.value
                            })
                        Text("LIVE GLASS", Modifier.padding(start = 16.dp, top = 18.dp), color = Color.Blue, fontSize = 14.sp)
                        Canvas(Modifier.align(Alignment.BottomEnd).padding(12.dp).size(28.dp)) {
                            drawRect(Color.Yellow)
                            drawLine(Color.Black, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 2f * d)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
    private fun origin(): Offset = requireNotNull(card).localToWindow(Offset.Zero)

    private fun lineEvidence(name: String) {
        content()
        val o = origin()
        val on = screenshot("$name-refraction-on.png")
        compose.runOnIdle { bend.value = false }; compose.waitForIdle()
        val off = screenshot("$name-refraction-off.png")
        val leftOn = darkCentroid(on, o.x, o.y + 60f * d, 0f, 18f * d, horizontal = true)
        val leftOff = darkCentroid(off, o.x, o.y + 60f * d, 0f, 18f * d, horizontal = true)
        val topOn = darkCentroid(on, o.x + 120f * d, o.y, 0f, 18f * d, horizontal = false)
        val topOff = darkCentroid(off, o.x + 120f * d, o.y, 0f, 18f * d, horizontal = false)
        val dx = abs(leftOn - leftOff) / d; val dy = abs(topOn - topOff) / d
        assertTrue("known vertical stripe must change geometric position by >=1dp, measured $dx", dx >= 1f)
        assertTrue("known horizontal stripe must change geometric position by >=1dp, measured $dy", dy >= 1f)
        var centerChanged = 0
        for (y in 45..75) for (x in 120..180) if (pixel(on, o, x, y) != pixel(off, o, x, y)) centerChanged++
        assertEquals("coded centre line is not moved by the bevel", 0, centerChanged)
        // An opaque icon witness, outside the filtered backdrop, must be byte-identical.
        for (y in 83..105) for (x in 205..223) assertEquals("foreground stays sharp", pixel(on, o, x, y), pixel(off, o, x, y))
        compose.runOnIdle { visible.value = false }; compose.waitForIdle()
        val source = screenshot("$name-source-only.png")
        val r = ShortcutTunedParameters.lengthDp(60f)
        var outside = 0
        for (y in 0 until (120f * d).toInt()) for (x in 0 until (240f * d).toInt()) {
            if (shortcutTunedSdfDp(x / d, y / d, 240f, 120f, r, 2f) > 2f / d) {
                val px = (o.x + x).roundToInt(); val py = (o.y + y).roundToInt()
                assertEquals("rounded outside pixel must remain source at $x,$y", source.getPixel(px, py), on.getPixel(px, py)); outside++
            }
        }
        assertTrue("non-vacuous round-corner witnesses", outside > 100)
        metric("$name-lines", "leftShiftDp=$dx topShiftDp=$dy centerChanged=$centerChanged outsidePixels=$outside")
        on.recycle(); off.recycle(); source.recycle()
    }

    @Test fun constantBackdropHasNoContactTextureAndMuchLessLiftGrainThanRelease() {
        content(flat = true)
        val o = origin()
        val contact = screenshot("flat-new-contact.png")
        compose.runOnIdle { refraction.value = ShortcutRefraction(lift = 1f, frostLift = 1f) }; compose.waitForIdle()
        val lift = screenshot("flat-new-lift.png")
        compose.runOnIdle { followup.value = false; refraction.value = ShortcutRefraction() }; compose.waitForIdle()
        val old = screenshot("flat-1.0.26-contact.png")
        fun range(b: Bitmap): Int {
            val values = (40..75).flatMap { y -> (65..95).map { x -> android.graphics.Color.red(pixel(b, o, x, y)) } }
            return values.max() - values.min()
        }
        val contactRange = range(contact); val liftRange = range(lift); val oldRange = range(old)
        assertTrue("flat contact must not gain a fake texture: $contactRange", contactRange <= 1)
        assertTrue("old coarse grain must be visible in this counterexample: $oldRange", oldRange >= 4)
        assertTrue("new physical-pixel grain is substantially smaller: $liftRange vs $oldRange", liftRange < oldRange * .6f)
        metric("flat-grain", "contactRange=$contactRange liftRange=$liftRange oldRange=$oldRange")
        contact.recycle(); lift.recycle(); old.recycle()
    }

    @Test fun transformedCardSamplesWorldAnchoredContentRatherThanMovingASticker() {
        bend.value = false
        content()
        val o = origin()
        val reference = screenshot("world-new-rest.png")
        val wx = o.x + 156f * d; val wy = o.y + 60f * d
        val resting = darkCentroid(reference, wx, wy, -14f * d, 14f * d, true)
        compose.runOnIdle { pose.value = Pose(18f, 12f, .90f, -9f, 9f) }; compose.waitForIdle()
        val anchored = screenshot("world-new-transformed.png")
        val corrected = darkCentroid(anchored, wx, wy, -14f * d, 14f * d, true)
        // Isolate clip leakage from native elevation shadows (this fixture has none).
        val local = floatArrayOf(0f, 0f, 240f * d, 0f, 240f * d, 120f * d, 0f, 120f * d)
        val window = FloatArray(8)
        for (i in 0..3) {
            val p = requireNotNull(card).localToWindow(Offset(local[i * 2], local[i * 2 + 1]))
            window[i * 2] = p.x; window[i * 2 + 1] = p.y
        }
        val inverse = android.graphics.Matrix()
        assertTrue(inverse.setPolyToPoly(window, 0, local, 0, 4))
        compose.runOnIdle { visible.value = false }; compose.waitForIdle()
        val source = screenshot("world-source-only.png")
        var outside = 0
        val point = FloatArray(2)
        for (y in 0 until source.height step 2) for (x in 0 until source.width step 2) {
            point[0] = x + .5f; point[1] = y + .5f; inverse.mapPoints(point)
            if (shortcutTunedSdfDp(point[0] / d, point[1] / d, 240f, 120f,
                    ShortcutTunedParameters.lengthDp(60f), 2f) > 3f / d) {
                assertEquals("transformed clip must not leak outside $x,$y", source.getPixel(x, y), anchored.getPixel(x, y))
                outside++
            }
        }
        assertTrue("non-vacuous full-window transformed clip witnesses", outside > 10000)
        compose.runOnIdle { visible.value = true; followup.value = false }; compose.waitForIdle()
        val legacy = screenshot("world-1.0.26-transformed.png")
        val old = darkCentroid(legacy, wx, wy, -18f * d, 18f * d, true)
        val error = abs(resting - corrected) / d; val oldError = abs(resting - old) / d
        assertTrue("world marker remains anchored after translation+scale+both tilts: $error dp", error <= 1.1f)
        assertTrue("counterexample exposes legacy translation-only replay: $oldError vs $error", oldError > error + 2f)
        metric("world-anchor", "newErrorDp=$error legacyErrorDp=$oldError outsideTransformed=$outside")
        reference.recycle(); anchored.recycle(); legacy.recycle(); source.recycle()
    }

    private fun pixel(image: Bitmap, o: Offset, xDp: Int, yDp: Int) = image.getPixel((o.x + xDp * d).roundToInt(), (o.y + yDp * d).roundToInt())
    private fun darkCentroid(b: Bitmap, x: Float, y: Float, from: Float, to: Float, horizontal: Boolean): Float {
        var total = 0f; var moment = 0f
        for (v in from.roundToInt()..to.roundToInt()) {
            val px = (x + if (horizontal) v else 0).roundToInt().coerceIn(0, b.width - 1)
            val py = (y + if (horizontal) 0 else v).roundToInt().coerceIn(0, b.height - 1)
            val weight = (200 - android.graphics.Color.red(b.getPixel(px, py))).coerceAtLeast(0).toFloat()
            total += weight; moment += v * weight
        }
        assertTrue("known stripe must be visible; cannot prove refraction with noise", total > 100f)
        return moment / total
    }
    private fun metric(name: String, values: String) { println("$name $values"); out.resolve("$name-metrics.txt").writeText(values + "\n") }
    private fun screenshot(name: String): Bitmap {
        val window = compose.activity.window
        val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        val result = AtomicInteger(-1)
        compose.runOnIdle { PixelCopy.request(window, image, { result.set(it) }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle(); compose.waitUntil(5000) { result.get() != -1 }; assertEquals(PixelCopy.SUCCESS, result.get())
        out.resolve(name).outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return image
    }
}
