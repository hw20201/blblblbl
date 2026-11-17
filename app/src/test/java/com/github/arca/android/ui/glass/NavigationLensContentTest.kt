package com.github.arca.android.ui.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.media.ImageReader
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/** Tests the production AGSL directly, without relying on Robolectric's Compose HW-canvas flag. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationLensContentTest {
    private val grey = android.graphics.Color.rgb(142, 142, 147)
    private val blue = android.graphics.Color.rgb(0, 122, 255)

    private fun source(width: Int = 180, height: Int = 64, draw: Canvas.(Paint) -> Unit): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).draw(Paint().apply { color = grey })
        }

    private fun render(source: Bitmap, shell: Rect, radius: Float, magnification: Float = 1f): Bitmap {
        val shader = RuntimeShader(NAVIGATION_LENS_CONTENT_SHADER)
        shader.setFloatUniform("shell", shell.left, shell.top, shell.right, shell.bottom)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("density", 1f)
        shader.setFloatUniform("magnification", magnification)
        shader.setFloatUniform("selectedTint", 0f, 122f / 255f, 1f, 1f)
        val node = RenderNode("navigation-lens-content-test")
        node.setPosition(0, 0, source.width, source.height)
        val canvas = node.beginRecording()
        canvas.drawBitmap(source, 0f, 0f, Paint())
        node.endRecording()
        node.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
        // RuntimeShader is hardware-only. Exercise the same RenderNode effect path as Compose,
        // then read the rendered surface; a Bitmap Canvas would reject the shader before drawing.
        ImageReader.newInstance(source.width, source.height, PixelFormat.RGBA_8888, 1).use { reader ->
            val renderer = HardwareRenderer()
            try {
                renderer.isOpaque = false
                renderer.setSurface(reader.surface)
                renderer.setContentRoot(node)
                renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
                val image = checkNotNull(reader.acquireNextImage()) { "native renderer produced no frame" }
                image.use {
                    val plane = it.planes.single()
                    // Native graphics exposes the platform bitmap byte order. Preserve it with
                    // copyPixelsFromBuffer, as Robolectric's own createHardwareBitmap does.
                    val padded = Bitmap.createBitmap(plane.rowStride / plane.pixelStride,
                        source.height, Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val result = Bitmap.createBitmap(padded, 0, 0, source.width, source.height)
                    if (result !== padded) padded.recycle()
                    return result
                }
            } finally {
                renderer.destroy()
                node.discardDisplayList()
            }
        }
    }

    private fun assertColor(expected: Int, actual: Int, message: String) {
        for (shift in intArrayOf(0, 8, 16, 24)) {
            val a = actual ushr shift and 255
            val e = expected ushr shift and 255
            assertTrue("$message channel $shift expected=$e actual=$a", abs(a - e) <= 2)
        }
    }

    @Test fun passingShellMakesOneGlyphPartBlueAndPartOriginalWhileOtherGlyphsStayGrey() {
        val input = source { paint ->
            drawRect(10f, 22f, 30f, 42f, paint)
            drawRect(60f, 22f, 100f, 42f, paint)
            drawRect(140f, 22f, 160f, 42f, paint)
        }
        val output = render(input, Rect(40f, 4f, 80f, 60f), 20f)
        assertColor(blue, output.getPixel(67, 32), "covered part of the middle glyph")
        assertColor(grey, output.getPixel(91, 32), "uncovered part of the same glyph")
        assertColor(grey, output.getPixel(20, 32), "previous slot outside the shell")
        assertColor(grey, output.getPixel(150, 32), "unvisited slot outside the shell")
        assertEquals("transparent source must not turn into a blue panel", 0,
            android.graphics.Color.alpha(output.getPixel(50, 32)))
        output.recycle(); input.recycle()
    }

    @Test fun blueFrontMovesContinuouslyWithTheShellInsteadOfWaitingForASelectedIndex() {
        val input = source { paint -> drawRect(60f, 22f, 100f, 42f, paint) }
        val blueWidths = (64..104 step 8).map { right ->
            val image = render(input, Rect(right - 70f, 4f, right.toFloat(), 60f), 24f)
            val count = (60 until 100).count { x ->
                val color = image.getPixel(x, 32)
                android.graphics.Color.blue(color) > 220 && android.graphics.Color.red(color) < 20
            }
            image.recycle()
            count
        }
        assertTrue("partial frames exist before arrival: $blueWidths", blueWidths.any { it in 1..35 })
        assertTrue("the blue area grows with every shell step: $blueWidths",
            blueWidths.zipWithNext().all { (before, after) -> after > before })
        assertTrue("the final covered glyph is blue", blueWidths.last() >= 39)
        input.recycle()
    }

    @Test fun curvedCornersExcludeContentOutsideTheCapsuleEvenInsideItsBoundingBox() {
        val input = source { paint -> drawRect(0f, 0f, 180f, 64f, paint) }
        val output = render(input, Rect(40f, 4f, 100f, 60f), 28f)
        assertColor(grey, output.getPixel(43, 7), "outside the rounded corner")
        assertColor(blue, output.getPixel(70, 8), "inside the top arc")
        assertColor(blue, output.getPixel(45, 32), "inside the straight side")
        output.recycle(); input.recycle()
    }

    @Test fun magnifiedSampleReplacesOriginalStrokeRatherThanLeavingAGreyDoubleEdge() {
        val input = source { paint -> drawRect(70f, 20f, 74f, 44f, paint) }
        val output = render(input, Rect(35f, 4f, 145f, 60f), 28f,
            navigationLensContentMagnification(1f))
        // About centre x=90, the 70..74 stroke grows to 67.8..72.2. Use a full pixel
        // beyond the filtered edge so a bilinear antialias fringe is not mistaken for a ghost.
        assertTrue("magnification expands the stroke into a previously transparent pixel",
            android.graphics.Color.alpha(output.getPixel(68, 32)) > 128)
        assertColor(blue, output.getPixel(70, 32), "the new stroke is selected ink")
        assertTrue("the old rightmost stroke pixel must be cleared, not drawn under the sample",
            android.graphics.Color.alpha(output.getPixel(73, 32)) < 30)
        assertEquals("unrelated transparent space remains clear", 0,
            android.graphics.Color.alpha(output.getPixel(120, 32)))
        output.recycle(); input.recycle()
    }

    @Test fun foregroundMaskUsesTheSameRoundedDimensionsAndCentreAsTheMeasuredShell() {
        for (height in listOf(48f, 64f)) {
            for (position in listOf(0f, .37f, 1f, 1.83f, 2f)) {
                for (expansion in listOf(0f, .42f, 1f)) {
                    val frame = LensFrame(position, -.083f, expansion)
                    val width = if (height == 48f) 264f else 304f
                    val animated = insetLensRect(position, frame.stretch, expansion, width / 3f,
                        width, height, 1f)
                    val mask = navigationLensContentBounds(frame, width, height, 3, 1f)!!
                    assertEquals(animated.width.roundToInt().toFloat(), mask.rect.width, .0001f)
                    assertEquals(animated.height.roundToInt().toFloat(), mask.rect.height, .0001f)
                    assertEquals(animated.center.x, mask.rect.center.x, .0001f)
                    assertEquals(height / 2f, mask.rect.center.y, .0001f)
                    assertEquals(minOf(mask.rect.width, mask.rect.height) / 2f, mask.cornerRadiusPx, .0001f)
                }
            }
        }
        assertEquals(1.04f, navigationLensContentMagnification(-1f), .0001f)
        assertEquals(1.11f, navigationLensContentMagnification(2f), .0001f)
    }
}
