package com.github.arca.android.ui.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageScrollEdgeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val originalGlass = GlassDefaults.enabled
    @After fun restore() { GlassDefaults.enabled = originalGlass }

    @Test fun topEdgeReusesLiveBodyAndStopsAtRest() {
        GlassDefaults.enabled = true
        val scroll = mutableIntStateOf(0)
        val color = mutableStateOf(Color.Red)
        var compositions = 0
        var windowTop = 0
        compose.setContent {
            val scene = rememberNavigationLens()
            SideEffect { compositions++ }
            ProvideGlassTheme {
                Box(Modifier.fillMaxSize().background(Color.White).onGloballyPositioned {
                    windowTop = it.boundsInWindow().top.roundToInt()
                }) {
                    Box(Modifier.fillMaxSize().recordNavigationScene(scene).drawBehind { drawRect(color.value) })
                    PageScrollEdge(scene, 80.dp, testTag = "edge") { scroll.intValue }
                    // Pinned foreground is drawn after the effect and must remain perfectly sharp.
                    Box(Modifier.fillMaxWidth().height(8.dp).background(Color.Green))
                }
            }
        }
        compose.onNodeWithTag("edge").assertDoesNotExist()
        compose.runOnIdle { scroll.intValue = 100 }
        compose.onNodeWithTag("edge").assertHeightIsEqualTo(108.dp)
        val first = screenshot()
        val before = compose.runOnIdle { compositions }
        compose.runOnIdle {
            scroll.intValue = 1000
            color.value = Color.Blue
        }
        val second = screenshot()
        try {
            assertEquals("Source-only changes must not recompose the page", before, compositions)
            assertNotEquals("The reused source must remain live", first.getPixel(150, windowTop + 24), second.getPixel(150, windowTop + 24))
            assertEquals("Pinned foreground stays above the blur", android.graphics.Color.GREEN, second.getPixel(150, windowTop + 4))
            assertEquals("Pixels below the bounded edge stay sharp", android.graphics.Color.BLUE, second.getPixel(150, windowTop + 140))
        } finally {
            first.recycle()
            second.recycle()
        }
        compose.runOnIdle { scroll.intValue = 0 }
        compose.onNodeWithTag("edge").assertDoesNotExist()
    }

    @Test fun nativeFrostMixesSourcePixelsAcrossAnEdge() {
        GlassDefaults.enabled = true
        val scroll = mutableIntStateOf(0)
        var windowTop = 0
        compose.setContent {
            val scene = rememberNavigationLens()
            ProvideGlassTheme {
                Box(Modifier.fillMaxSize().onGloballyPositioned {
                    windowTop = it.boundsInWindow().top.roundToInt()
                }) {
                    Box(Modifier.fillMaxSize().recordNavigationScene(scene).drawBehind {
                        drawRect(Color.Black)
                        drawRect(Color.White, topLeft = Offset(150f, 0f), size = Size(size.width - 150f, size.height))
                    })
                    PageScrollEdge(scene, 80.dp, testTag = "edge") { scroll.intValue }
                }
            }
        }
        val sharp = screenshot()
        compose.runOnIdle { scroll.intValue = 100 }
        compose.onNodeWithTag("edge").assertHeightIsEqualTo(108.dp)
        val frosted = screenshot()
        try {
            val x = 148
            val y = windowTop + 24
            assertEquals("The unfiltered source has a sharp black edge", 0, android.graphics.Color.red(sharp.getPixel(x, y)))
            val mixed = android.graphics.Color.red(frosted.getPixel(x, y))
            assertTrue("Frost must sample both sides of the source edge: $mixed", mixed in 10..245)
            assertEquals("The source below the header stays sharp", 0,
                android.graphics.Color.red(frosted.getPixel(x, windowTop + 140)))
        } finally {
            sharp.recycle()
            frosted.recycle()
        }
    }

    @Test @Config(sdk = [32]) fun api32UsesSelfDrawnFallback() = verifyFallback()
    @Test @Config(sdk = [26]) fun api26UsesSelfDrawnFallback() = verifyFallback()

    private fun verifyFallback() {
        GlassDefaults.enabled = true
        compose.setContent {
            ProvideGlassTheme {
                Box(Modifier.fillMaxSize()) {
                    PageScrollEdge(rememberNavigationLens(), 60.dp, testTag = "edge") { 100 }
                }
            }
        }
        compose.onNodeWithTag("edge").assertHeightIsEqualTo(88.dp)
        screenshot().recycle()
    }

    private fun screenshot(): Bitmap {
        val window = compose.activity.window
        val view = window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle { PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle()
        assertEquals("Native PixelCopy result", PixelCopy.SUCCESS, result)
        assertTrue(bitmap.width > 0)
        return bitmap
    }
}
