package com.github.mihomo.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Production B (jelly) gesture regressions. Robolectric measures visual translation and
 * recovery only; its orthographic coordinates do not prove perspective/tilt direction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutJellyGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun shortVerticalSwipeYieldsToParentScroll() {
        lateinit var scroll: androidx.compose.foundation.ScrollState
        var clicks = 0
        compose.setContent {
            scroll = rememberScrollState()
            CompositionLocalProvider(LocalShortcutTouchVariant provides ShortcutTouchVariant.FingerTilt) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    Box(Modifier.size(300.dp, 180.dp))
                    LoonShortcutCard(Modifier.size(174.dp, 118.dp), "JellyScroll", "", icon = {}, onClick = { clicks++ })
                    Box(Modifier.size(300.dp, 900.dp))
                }
            }
        }
        val card = compose.onNodeWithTag("shortcut-card-JellyScroll")
        card.performTouchInput {
            down(center)
            moveTo(center + Offset(2f, -24f), delayMillis = 70)
            moveTo(center + Offset(4f, -70f), delayMillis = 70)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("ordinary short vertical swipe remains parent-scrollable", scroll.value > 0)
            assertEquals("vertical swipe is not card activation", 0, clicks)
        }
    }

    @Test fun longHoldLocksEightDirectionsAtFixedSlotAndReturnsToRest() {
        // Explicitly use the production built-in global dock path, not a helper override.
        val pickups = mutableListOf<Boolean>()
        var foreground: LayoutCoordinates? = null
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTouchVariant provides ShortcutTouchVariant.FingerTilt) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    LoonShortcutCard(Modifier.size(174.dp, 118.dp), "JellyEight", "", icon = {
                        // The illustration host is bottom/end aligned in the card. A 154x98 box
                        // anchored bottom/end with the same 10dp inset has its centre exactly at
                        // the 174x118 foreground plane's centre, and is transformed with that plane.
                        Box(Modifier.requiredSize(154.dp, 98.dp).onGloballyPositioned { foreground = it })
                    }, onPickupChanged = { pickups += it }, onClick = {})
                }
            }
        }
        val card = compose.onNodeWithTag("shortcut-card-JellyEight")
        val slotRest = card.fetchSemanticsNode().boundsInRoot
        fun center(): Offset {
            val c = requireNotNull(foreground)
            return c.localToRoot(Offset(c.size.width / 2f, c.size.height / 2f))
        }
        compose.waitForIdle()
        val rest = center()
        val directions = listOf(
            Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f), Offset(-1f, 1f),
            Offset(-1f, 0f), Offset(-1f, -1f), Offset(0f, -1f), Offset(1f, -1f)
        )
        directions.forEachIndexed { index, direction ->
            val before = center()
            card.performTouchInput {
                down(center)
                moveTo(center + direction * 2f, delayMillis = 600)
                moveTo(center + direction * 48f, delayMillis = 80)
            }
            compose.waitForIdle()
            val moved = center()
            val displacement = moved - before
            assertTrue("direction $index locks and translates the real foreground", displacement.getDistance() > .5f)
            assertTrue("direction $index follows its signed drag", displacement.x * direction.x + displacement.y * direction.y > 0f)
            assertTrue("radial translation stays within the 4dp cap", displacement.getDistance() <= 4.6f)
            assertEquals("slot hit rectangle remains fixed", slotRest, card.fetchSemanticsNode().boundsInRoot)
            compose.runOnIdle { assertEquals("one pickup notification per held gesture", listOf(true, false).take((index + 1) * 2), pickups) }
            card.performTouchInput { up() }
            compose.waitForIdle()
            assertTrue("release springs visual plane back", (center() - rest).getDistance() < .75f)
            assertEquals("release leaves hit rectangle fixed", slotRest, card.fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test fun cancelAndSecondPointerDoNotLeakIntoTapOrNextGesture() {
        var clicks = 0
        val pickups = mutableListOf<Boolean>()
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTouchVariant provides ShortcutTouchVariant.FingerTilt) {
                LoonShortcutCard(Modifier.size(174.dp, 118.dp), "JellyCancel", "", icon = {},
                    onPickupChanged = { pickups += it }, onClick = { clicks++ })
            }
        }
        val card = compose.onNodeWithTag("shortcut-card-JellyCancel")
        card.performTouchInput {
            down(center)
            moveTo(center + Offset(2f, 2f), delayMillis = 600)
            moveTo(center + Offset(30f, 25f), delayMillis = 60)
            cancel()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("cancel never navigates", 0, clicks)
            assertEquals("cancel closes a single lock", listOf(true, false), pickups)
        }
        card.performTouchInput {
            down(center)
            moveTo(center + Offset(2f, 2f), delayMillis = 600)
            moveTo(center + Offset(30f, 25f), delayMillis = 60)
            down(pointerId = 1, position = center + Offset(10f, 0f))
            up(pointerId = 1)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("multi-pointer cancellation does not click", 0, clicks)
            assertEquals("second pointer cannot duplicate pickup", listOf(true, false, true, false), pickups)
        }
        card.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals("a later ordinary tap still works", 1, clicks) }
    }
}
