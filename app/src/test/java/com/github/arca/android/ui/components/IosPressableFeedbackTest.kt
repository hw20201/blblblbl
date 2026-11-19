package com.github.arca.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.click
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosPressableFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun heldPressAndReleaseClickOnceWithoutFrameRecomposition() {
        var clicks = 0
        var compositions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                SideEffect { compositions++ }
                Box(Modifier.size(160.dp).testTag("button").iosPressable(role = Role.Button) { clicks++ }.background(Color.White))
            }
        }
        compose.onNodeWithTag("button").assertHasClickAction()
        val before = compose.runOnIdle { compositions }
        compose.onNodeWithTag("button").performTouchInput { down(center) }
        repeat(12) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.runOnIdle { assertEquals(0, clicks) }
        compose.onNodeWithTag("button").performTouchInput { up() }
        repeat(36) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertEquals("Press animation must not recompose its content", before, compositions)
        }
        compose.onNodeWithTag("button").performClick()
        compose.runOnIdle { assertEquals("Accessibility click is preserved", 2, clicks) }
    }

    @Test fun cancelAndScrollDoNotClick() {
        var clicks = 0
        val scroll = androidx.compose.foundation.ScrollState(0)
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    Box(Modifier.size(200.dp).testTag("button").iosPressable { clicks++ })
                    Box(Modifier.height(1400.dp))
                }
            }
        }
        compose.onNodeWithTag("button").performTouchInput { down(center); advanceEventTime(200); cancel() }
        compose.onNodeWithTag("button").performTouchInput {
            down(Offset(100f, 170f))
            advanceEventTime(200)
            moveTo(Offset(100f, 10f))
            up()
        }
        compose.runOnIdle {
            assertEquals("Cancelled/scroll gestures must not activate the button", 0, clicks)
            assertTrue("Parent scrolling is preserved", scroll.value > 0)
        }
    }

    @Test fun disabledButtonKeepsDisabledSemantics() {
        var clicks = 0
        compose.setContent { Box(Modifier.size(100.dp).testTag("button").iosPressable(enabled = false, role = Role.Button) { clicks++ }) }
        compose.onNodeWithTag("button").assertIsNotEnabled()
        compose.onNodeWithTag("button").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(0, clicks) }
    }
}
