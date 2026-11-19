package com.github.arca.android.ui.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.neoruaa.glasmio.glass.glassBackdropSource
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import org.junit.Assert.assertEquals
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
class BackdropSourceDrawCountTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun defaultGlasmioSourceDrawsTwicePerPass() = verifySourceDraws(useArcaRecorder = false, expected = 2)

    @Test
    fun arcaSourceDrawsOncePerPass() = verifySourceDraws(useArcaRecorder = true, expected = 1)

    private fun verifySourceDraws(useArcaRecorder: Boolean, expected: Int) {
        val phaseColor = mutableStateOf(Color.Red)
        var outerPasses = 0
        var contentDraws = 0
        var compositions = 0
        compose.setContent {
            val backdrop = rememberLayerBackdrop()
            val recorder = if (useArcaRecorder) {
                Modifier.recordArcaGlasmioBackdropSource(backdrop)
            } else {
                Modifier.glassBackdropSource(backdrop).trackBackdropPosition(backdrop)
            }
            SideEffect { compositions++ }
            Box(
                Modifier.size(96.dp)
                    .graphicsLayer()
                    .drawWithContent {
                        outerPasses++
                        drawContent()
                    }
                    .then(recorder)
                    .drawBehind {
                        contentDraws++
                        drawRect(phaseColor.value)
                    }
            )
        }

        screenshot().recycle()
        val before = compose.runOnIdle {
            DrawCounts(outerPasses, contentDraws, compositions).also {
                phaseColor.value = Color.Blue
            }
        }
        screenshot().recycle()
        val after = compose.runOnIdle { DrawCounts(outerPasses, contentDraws, compositions) }
        val passes = after.outer - before.outer
        val draws = after.content - before.content
        val recorder = if (useArcaRecorder) "Arca" else "Glasmio default"
        // Normalize against the adjacent draw node to account for any additional frame traversals.
        val diagnostic = "$recorder draw-only update: $draws content draws across $passes source passes"
        println(diagnostic)
        assertTrue("$diagnostic (expected at least one invalidation)", passes > 0)
        assertEquals(diagnostic, expected * passes, draws)
        assertEquals("A draw-only color update keeps the source composition", before.compositions, after.compositions)
    }

    @Test
    fun sourceAndCaptureStayInSyncWhenNestedLayerMovesAndRedraws() {
        val spriteColor = mutableStateOf(Color.Red)
        val spriteOffset = mutableStateOf(0.dp)
        val backdropColor = Color(0xFF203040)
        var sourceBounds = Rect.Zero
        var captureBounds = Rect.Zero
        compose.setContent {
            val backdrop = rememberLayerBackdrop()
            Column {
                Box(
                    Modifier.size(96.dp)
                        .onGloballyPositioned { sourceBounds = it.boundsInWindow() }
                        .graphicsLayer()
                        .recordArcaGlasmioBackdropSource(backdrop)
                        .drawBehind { drawRect(backdropColor) }
                ) {
                    Box(
                        Modifier.size(24.dp)
                            .graphicsLayer { translationX = spriteOffset.value.toPx() }
                            .drawBehind { drawRect(spriteColor.value) }
                    )
                }
                Box(
                    Modifier.size(96.dp)
                        .onGloballyPositioned { captureBounds = it.boundsInWindow() }
                        .drawBehind { drawLayer(backdrop.graphicsLayer) }
                )
            }
        }

        assertSourceAndCapture(screenshot(), sourceBounds, captureBounds, Color.Red, backdropColor)
        compose.runOnIdle {
            spriteOffset.value = 48.dp
            spriteColor.value = Color.Blue
        }
        assertSourceAndCapture(screenshot(), sourceBounds, captureBounds, backdropColor, Color.Blue)
    }

    private fun assertSourceAndCapture(
        bitmap: Bitmap,
        source: Rect,
        capture: Rect,
        leftColor: Color,
        rightColor: Color
    ) {
        try {
            for ((label, bounds) in listOf("visible source" to source, "capture replay" to capture)) {
                assertTrue("$label is laid out", bounds.width > 0 && bounds.height > 0)
                val y = (bounds.top + bounds.height / 8).roundToInt()
                val left = (bounds.left + bounds.width / 8).roundToInt()
                val right = (bounds.left + bounds.width * 5 / 8).roundToInt()
                assertEquals("$label left sprite position", leftColor.toArgb(), bitmap.getPixel(left, y))
                assertEquals("$label right sprite position", rightColor.toArgb(), bitmap.getPixel(right, y))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun screenshot(): Bitmap {
        val window = compose.activity.window
        val view = window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle {
            PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
        }
        compose.waitForIdle()
        assertEquals("Native PixelCopy result", PixelCopy.SUCCESS, result)
        return bitmap
    }

    private data class DrawCounts(val outer: Int, val content: Int, val compositions: Int)
}
