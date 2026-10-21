package com.github.mihomo.android.ui.glass

import android.graphics.RenderNode
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Checks the hardware recording contract; this is not device GPU playback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationSceneBoundsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun recordedPageCannotExpandToTranslatedFullWindowUnderlay() {
        lateinit var scene: NavigationLensState
        compose.setContent {
            val backdrop = rememberNavigationLens()
            val page = rememberNavigationLens()
            SideEffect { scene = page }
            Box(Modifier.size(400.dp, 900.dp).recordNavigationScene(backdrop).background(Color.Blue)) {
                Box(Modifier.size(100.dp).recordNavigationScene(page, backdrop).background(Color.Red))
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 400, 900)
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val node = RenderNode("bounded-capture")
            node.setPosition(0, 0, 400, 900)
            val canvas = node.beginRecording(400, 900)
            assertTrue(canvas.isHardwareAccelerated)
            view.draw(canvas)
            node.endRecording()
            assertTrue("A lens source must clip replay to its own local viewport", scene.scene.clip)
            assertEquals(100, scene.scene.size.width)
            assertEquals(100, scene.scene.size.height)
        }
    }
}
