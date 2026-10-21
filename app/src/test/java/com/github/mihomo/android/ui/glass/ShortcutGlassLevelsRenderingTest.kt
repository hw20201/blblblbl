package com.github.mihomo.android.ui.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

/** Non-production coloured witness lines make optical differences measurable, not subjective. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutGlassLevelsRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun lightLevelsRenderDistinctOptics() = verify(false)
    @Test fun darkLevelsRenderDistinctOptics() = verify(true)

    private fun verify(dark: Boolean) {
        val level=mutableStateOf(ShortcutGlassLevel.Normal)
        compose.setContent {
            val scene=rememberNavigationLens()
            CompositionLocalProvider(LocalShortcutGlassLevel provides level.value,
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
            compose.runOnIdle { level.value=choice }; compose.waitForIdle(); screenshot()
        }
        for((a,b) in frames.zipWithNext()) {
            var changed=0
            for(y in 0 until a.height) for(x in 0 until a.width) if(a.getPixel(x,y)!=b.getPixel(x,y)) changed++
            assertTrue("$dark: adjacent levels must visibly change pixels, got $changed",changed>300)
        }
        // Opaque centre witness is a sibling outside the filter in every level.
        for(frame in frames.drop(1)) assertEquals(frames[0].getPixel(frame.width/2,frame.height/2),frame.getPixel(frame.width/2,frame.height/2))
        frames.forEach { it.recycle() }
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
