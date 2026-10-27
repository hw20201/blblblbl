package com.github.mihomo.android.ui

import android.graphics.Bitmap
import android.graphics.RenderNode
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Full production pager, not individual-screen fixtures. Host API35, not vivo GPU validation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DraftPagesVisibleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun lightPagerShowsForegroundOnAllThreePages() = checkPages("light")

    @Test @Config(qualifiers = "zh-w400dp-h900dp-night-mdpi")
    fun darkPagerShowsForegroundOnAllThreePages() = checkPages("dark")

    private fun checkPages(theme: String) {
        compose.mainClock.autoAdvance = false
        settle()
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(900, android.view.View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 400, 900)
        }
        settle()
        checkPage("dashboard", "ARCA", "快捷方式", theme)
        dock("策略").performClick()
        settle()
        // This empty-state message is actual page content, not the surviving bottom dock.
        checkPage("policy", "策略", "暂无节点数据", theme, true)
        dock("设置").performClick()
        settle()
        checkPage("settings", "设置", "TUN 堆栈", theme)
        dock("仪表").performClick()
        settle()
        checkPage("dashboard-return", "ARCA", "快捷方式", theme)
    }

    private fun dock(text: String) = compose.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag("navigation-dock")))

    private fun settle() {
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    private fun checkPage(page: String, title: String, body: String, theme: String, substring: Boolean = false) {
        val notDock = !hasAnyAncestor(hasTestTag("navigation-dock"))
        compose.onNode(hasText(title) and notDock).assertIsDisplayed()
        compose.onNode(hasText(body, substring = substring) and notDock).assertIsDisplayed()
        // Execute recording-time code too. This does NOT execute a real GPU compositor.
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val node = RenderNode("$page-content-regression")
            node.setPosition(0, 0, view.width, view.height)
            val canvas = node.beginRecording(view.width, view.height)
            assertTrue(canvas.isHardwareAccelerated)
            view.draw(canvas)
            node.endRecording()
        }
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle {
            PixelCopy.request(compose.activity.window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
        }
        compose.waitForIdle()
        assertEquals(PixelCopy.SUCCESS, result)
        // A background-only image has smooth gradients, not hundreds of sharp foreground edges.
        // Exclude status/navigation bars so the dock cannot make this assertion pass.
        var foregroundEdges = 0
        for (y in 70 until minOf(bitmap.height - 150, 700)) {
            for (x in 18 until bitmap.width - 19) {
                val a = bitmap.getPixel(x, y)
                val b = bitmap.getPixel(x + 1, y)
                val delta = maxOf(kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
                    kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)),
                    kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)))
                if (delta > 45) foregroundEdges++
            }
        }
        assertTrue("$theme/$page has no visible sharp foreground ($foregroundEdges edges)", foregroundEdges > 100)
        val out = File("build/reports/draft-pages/$theme-$page.png")
        out.parentFile!!.mkdirs()
        out.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
