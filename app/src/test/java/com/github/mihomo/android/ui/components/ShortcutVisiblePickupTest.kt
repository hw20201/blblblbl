package com.github.mihomo.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.ui.glass.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Runs the actual shortcut composition, not an HTML model. Requires Android test backend. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutVisiblePickupTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun lightShortcutPhysicallyRisesAndRestores() = verify(false)
    @Test fun darkShortcutPhysicallyRisesAndRestores() = verify(true)

    private fun verify(dark: Boolean) {
        var clicks = 0
        compose.setContent {
            val scene = rememberNavigationLens()
            CompositionLocalProvider(
                LocalShortcutTouchVariant provides ShortcutTouchVariant.FingerTilt,
                LocalShortcutMotionEnabled provides true,
                LocalGlassTokens provides (if (dark) GlassPalettes.MIDNIGHT else GlassPalettes.CREAM).toTokens()
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().recordNavigationScene(scene)
                        .background(if (dark) Color(0xFF080C14) else Color(0xFFE7EEFA)))
                    Box(Modifier.align(Alignment.Center).size(190.dp, 118.dp).testTag("fixed-slot")) {
                        LoonShortcutCard(Modifier.fillMaxSize(), "Pickup", "", icon = {
                            Box(Modifier.size(18.dp).testTag("plane-witness").background(Color.Cyan))
                        }, lens = scene, onClick = { clicks++ })
                    }
                }
            }
        }
        compose.waitForIdle()
        val slot = compose.onNodeWithTag("fixed-slot")
        val witness = compose.onNodeWithTag("plane-witness", useUnmergedTree = true)
        val origin = witness.fetchSemanticsNode().boundsInRoot.center
        val fixed = slot.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        // Centre press has zero pitch/roll: measures real translation, not a tilted bounding box.
        slot.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160); compose.waitForIdle()
        val raised = witness.fetchSemanticsNode().boundsInRoot.center
        assertTrue("$dark: actual foreground visibly rises >=6dp", raised.y < origin.y - 6f)
        assertEquals("layout slot does not move", fixed, slot.fetchSemanticsNode().boundsInRoot)
        slot.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1200); compose.waitForIdle()
        val restored = witness.fetchSemanticsNode().boundsInRoot.center
        assertEquals("release restores the plane", origin.y, restored.y, .2f)
        assertEquals("ordinary tap still activates", 1, clicks)
        compose.mainClock.autoAdvance = true
        // The original bottom strip must remain clickable even though the visible plane
        // is eight dp higher while pressed. This is a real pointer event, not performClick.
        slot.performTouchInput { click(bottomCenter - androidx.compose.ui.geometry.Offset(0f, 2f)) }
        compose.runOnIdle { assertEquals("fixed lower-slot hit area remains usable", 2, clicks) }
    }
}
