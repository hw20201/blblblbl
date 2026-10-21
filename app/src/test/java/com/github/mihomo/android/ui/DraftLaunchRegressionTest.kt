package com.github.mihomo.android.ui

import android.graphics.RuntimeShader
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import android.view.View
import android.graphics.RenderNode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.github.mihomo.android.ui.glass.*

/** API 35 host regression only: RecordingCanvas is not a device GPU or Android 16 test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DraftLaunchRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun nativeShortcutShaderAcceptsEveryUniform() {
        val s = RuntimeShader(SHORTCUT_DOCK_LENS_SHADER)
        s.setFloatUniform("viewport", 180f, 118f)
        s.setFloatUniform("inputOrigin", 8f, 8f)
        s.setFloatUniform("inputViewport", 196f, 134f)
        for (name in listOf("density", "displacement", "bevel", "saturation", "aberration", "chromaticSamples", "rimGain", "cornerRadius", "depression")) s.setFloatUniform(name, 1f)
        s.setFloatUniform("contactPoint", .5f, .5f)
    }
    @Test fun launcherComposesDashboard() {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 400, 900)
        }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        compose.runOnUiThread {
            val node = RenderNode("launch-smoke")
            node.setPosition(0, 0, 400, 900)
            val canvas = node.beginRecording(400, 900)
            assertTrue("RecordingCanvas must select the hardware-gated recording path", canvas.isHardwareAccelerated)
            compose.activity.window.decorView.draw(canvas)
            node.endRecording()
        }
        compose.onNodeWithText("NEXUS").assert(!hasAnyAncestor(hasScrollAction()))
        compose.onNodeWithText("NEXUS").assertIsDisplayed()
    }
}
