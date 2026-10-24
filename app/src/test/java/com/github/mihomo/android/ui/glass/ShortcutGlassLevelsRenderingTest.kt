package com.github.mihomo.android.ui.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger
import java.io.File

/** Non-production coloured witness lines make optical differences measurable, not subjective. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutGlassLevelsRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun lightLevelsRenderDistinctOptics() = verify(false)
    @Test fun darkLevelsRenderDistinctOptics() = verify(true)


    @Test fun liveTuningChangesRealShortcutCardRender() = verifyTuning(false)
    @Test fun liveTuningChangesRealShortcutCardRenderInDarkTheme() = verifyTuning(true)

    private fun verifyTuning(dark: Boolean) {
        val tuning = mutableStateOf(ShortcutGlassTuning.DEFAULT)
        compose.setContent {
            val scene = rememberNavigationLens()
            val tokens = (if (dark) GlassPalettes.MIDNIGHT else GlassPalettes.CREAM).toTokens()
            CompositionLocalProvider(
                LocalGlassSettings provides GlassSettings.DEFAULT.copy(
                    shortcutGlassLevel = ShortcutGlassLevel.Extreme,
                    surfaceOverrides = mapOf(GlassSurfaceCategory.SHORTCUT_CARDS to tuning.value)
                ),
                LocalGlassTokens provides tokens
            ) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().recordNavigationScene(scene)) {
                        drawRect(if (dark) Color(0xFF101829) else Color(0xFFCEDCF3))
                        for (x in 0..400 step 7) drawLine(
                            if (x % 2 == 0) Color.Cyan else Color(0xFF20509F),
                            Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), 2f
                        )
                    }
                    ShortcutDockGlassSurface(
                        Modifier.align(Alignment.Center).size(190.dp, 118.dp),
                        ShortcutFollowupCardShape, scene, tokens, { ShortcutRefraction() }
                    ) {
                        Canvas(Modifier.align(Alignment.Center).size(20.dp)) { drawRect(Color.Magenta) }
                    }
                }
            }
        }
        compose.waitForIdle()
        val frames = listOf(ShortcutGlassTuning.DEFAULT, ShortcutGlassTuning(
            refractionDp = ShortcutGlassTuning.MAX_REFRACTION_DP,
            dispersionDp = ShortcutGlassTuning.MAX_DISPERSION_DP,
            edgeWidthDp = ShortcutGlassTuning.MAX_EDGE_WIDTH_DP,
            edgeLightMultiplier = ShortcutGlassTuning.MAX_EDGE_LIGHT_MULTIPLIER
        )).map { choice ->
            compose.runOnIdle { tuning.value = choice }
            compose.waitForIdle()
            screenshot()
        }
        val changed = changedPixels(frames[0], frames[1])
        assertTrue("$dark: live slider values must visibly alter rendered shortcut optics; changed=$changed", changed > 800)
        assertEquals(frames[0].getPixel(frames[0].width / 2, frames[0].height / 2),
            frames[1].getPixel(frames[1].width / 2, frames[1].height / 2))
        frames.forEach { it.recycle() }
    }

    private fun verify(dark: Boolean) {
        val level=mutableStateOf(ShortcutGlassLevel.Extreme)
        compose.setContent {
            val scene=rememberNavigationLens()
            CompositionLocalProvider(LocalGlassSettings provides GlassSettings.DEFAULT.copy(shortcutGlassLevel = level.value),
                LocalGlassTokens provides (if(dark)GlassPalettes.MIDNIGHT else GlassPalettes.CREAM).toTokens()) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().recordNavigationScene(scene)) {
                        drawRect(if(dark)Color(0xFF101829) else Color(0xFFCEDCF3))
                        for(x in 0..400 step 7) drawLine(if(x%2==0)Color.Cyan else Color(0xFF20509F),Offset(x.toFloat(),0f),Offset(x.toFloat(),size.height),2f)
                    }
                    ShortcutDockGlassSurface(Modifier.align(Alignment.Center).size(190.dp,118.dp),
                        ShortcutFollowupCardShape,scene,LocalGlassTokens.current,{ShortcutRefraction()}) {
                        Canvas(Modifier.align(Alignment.Center).size(20.dp)) { drawRect(Color.Magenta) }
                    }
                }
            }
        }
        compose.waitForIdle()
        val frames=ShortcutGlassLevel.entries.map { choice ->
            compose.runOnIdle { level.value=choice }; compose.waitForIdle()
            screenshot().also { image ->
                val directory = File("build/reports/shortcut-glass-levels").also { it.mkdirs() }
                directory.resolve("${if (dark) "dark" else "light"}-${choice.name.lowercase()}.png")
                    .outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
        }
        for((a,b) in frames.zipWithNext()) {
            val changed = changedPixels(a, b)
            assertTrue("$dark: adjacent levels must visibly change pixels, got $changed", changed > 300)
        }
        val normal = frames[ShortcutGlassLevel.entries.indexOf(ShortcutGlassLevel.Normal)]
        val extreme = frames[ShortcutGlassLevel.entries.indexOf(ShortcutGlassLevel.Extreme)]
        val extremeDelta = changedPixels(normal, extreme)
        assertTrue("$dark: Extreme must produce a conspicuous real-render change, got $extremeDelta pixels", extremeDelta > 800)
        // Opaque centre witness is a sibling outside the filter in every level.
        for(frame in frames.drop(1)) assertEquals(frames[0].getPixel(frame.width/2,frame.height/2),frame.getPixel(frame.width/2,frame.height/2))
        frames.forEach { it.recycle() }
    }

    private fun changedPixels(a: Bitmap, b: Bitmap): Int {
        var changed = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            if (a.getPixel(x, y) != b.getPixel(x, y)) changed++
        }
        return changed
    }

    private fun screenshot(): Bitmap {
        val window=compose.activity.window
        val b=Bitmap.createBitmap(window.decorView.width,window.decorView.height,Bitmap.Config.ARGB_8888)
        val result=AtomicInteger(-1)
        compose.runOnIdle { PixelCopy.request(window,b,{result.set(it)},Handler(Looper.getMainLooper())) }
        compose.waitUntil(5000) { result.get()!=-1 }
        assertEquals(PixelCopy.SUCCESS,result.get())
        return b
    }
}
