package com.github.mihomo.android.ui.components

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.ui.glass.ShortcutRefraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutTouchBehaviorRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val variants = listOf(ShortcutTouchVariant.GentleFloat, ShortcutTouchVariant.FingerTilt, ShortcutTouchVariant.BuoyantSpring)

    @Test fun everyStyleIsNeutralAtRestAndReducedMotionKeepsClick() {
        var clicks = 0
        val style = mutableStateOf(variants.first())
        val refraction = mutableStateOf<ShortcutRefraction?>(null)
        compose.setContent {
            CompositionLocalProvider(LocalShortcutMotionEnabled provides false) {
                val motion = rememberShortcutTouchMotion(style.value, enabled = true)
                val optics = motion.refraction()
                SideEffect { refraction.value = optics }
                Box(motion.modifier.size(120.dp).clickable { clicks++ }.testTag("motion"))
            }
        }
        val target = compose.onNodeWithTag("motion")
        variants.forEach { variant ->
            compose.runOnIdle { style.value = variant; clicks = 0 }
            compose.waitForIdle()
            compose.runOnIdle {
                val optics = requireNotNull(refraction.value)
                assertEquals(0f, optics.lift, 0f)
                assertEquals(0f, optics.x, 0f)
                assertEquals(0f, optics.y, 0f)
            }
            target.performTouchInput { click(center) }
            compose.runOnIdle { assertEquals("$variant click remains active", 1, clicks) }
        }
    }

    @Test fun allStylesKeepCardBoundsStableAndCancelOrReleaseCleanly() {
        var clicks = 0
        val style = mutableStateOf(variants.first())
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTouchVariant provides style.value) {
                LoonShortcutCard(
                    modifier = Modifier.size(width = 174.dp, height = 118.dp), title = "Regression",
                    subtitle = "", icon = {}, onClick = { clicks++ }
                )
            }
        }
        val card = compose.onNodeWithTag("shortcut-card-Regression")
        card.assertIsDisplayed()
        variants.forEach { variant ->
            compose.runOnIdle { style.value = variant; clicks = 0 }
            compose.waitForIdle()
            val rest = card.fetchSemanticsNode().boundsInRoot
            compose.mainClock.autoAdvance = false
            card.performTouchInput { down(Offset(2f, 2f)) }
            compose.mainClock.advanceTimeBy(120)
            compose.waitForIdle()
            assertEquals("$variant semantic hit bounds stable", rest, card.fetchSemanticsNode().boundsInRoot)
            compose.runOnIdle { assertEquals("down is not activation", 0, clicks) }
            card.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(350)
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.runOnIdle { assertEquals("corner release clicks", 1, clicks) }

            card.performTouchInput { down(center); cancel() }
            compose.mainClock.advanceTimeBy(350)
            compose.waitForIdle()
            compose.runOnIdle { assertEquals("cancel does not click", 1, clicks) }
            assertTrue("bounds remain stable after cancellation", card.fetchSemanticsNode().boundsInRoot == rest)
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun editModeRetainsDeleteAndDisablesCardActivation() {
        var clicks = 0
        var deletes = 0
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTouchVariant provides ShortcutTouchVariant.BuoyantSpring) {
                LoonShortcutCard(
                    modifier = Modifier.size(width = 174.dp, height = 118.dp), title = "Editing",
                    subtitle = "", icon = {}, isEditing = true, onDelete = { deletes++ }, onClick = { clicks++ }
                )
            }
        }
        compose.onNodeWithTag("shortcut-card-Editing").performTouchInput { click(center) }
        compose.onNodeWithText("−").performClick()
        compose.runOnIdle {
            assertEquals(0, clicks)
            assertEquals(1, deletes)
        }
    }
}
