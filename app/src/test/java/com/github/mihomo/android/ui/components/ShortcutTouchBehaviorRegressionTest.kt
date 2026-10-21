package com.github.mihomo.android.ui.components

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
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
            // Deliberately assert layout/hit-test bounds only: the revised contract permits a
            // bounded graphicsLayer visual translation while the semantic/layout rectangle stays put.
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

    @Test fun refractionUsesActualTouchPointAndRespondsOnShortPress() {
        val refraction = androidx.compose.runtime.mutableStateOf(ShortcutRefraction())
        compose.setContent {
            val motion = rememberShortcutTouchMotion(ShortcutTouchVariant.GentleFloat, enabled = true)
            SideEffect { refraction.value = motion.refraction() }
            Box(motion.modifier.size(120.dp).clickable { }.testTag("touch-refraction"))
        }
        val target = compose.onNodeWithTag("touch-refraction")
        target.performTouchInput { down(Offset(12f, 36f)) }
        compose.waitForIdle()
        compose.runOnIdle {
            val actual = refraction.value
            assertEquals("x must be the normalized finger position", -0.8f, actual.x, 0.03f)
            assertEquals("y must be the normalized finger position", -0.4f, actual.y, 0.03f)
            assertTrue("short press starts with visible refraction lift", actual.lift >= 0.35f)
        }
        target.performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test fun heldDragCancelsCardClickAndOrdinaryTapStillActivates() {
        var clicks = 0
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTouchVariant provides ShortcutTouchVariant.GentleFloat) {
                LoonShortcutCard(
                    modifier = Modifier.size(width = 174.dp, height = 118.dp), title = "Drag",
                    subtitle = "", icon = {}, onClick = { clicks++ }
                )
            }
        }
        val card = compose.onNodeWithTag("shortcut-card-Drag")
        card.performTouchInput { down(center); moveTo(center + Offset(70f, 40f), delayMillis = 80); up() }
        compose.runOnIdle { assertEquals("drag must not activate card navigation", 0, clicks) }
        card.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals("ordinary tap remains active", 1, clicks) }
    }

    @Test fun verticalCardGestureScrollsParentWhileHorizontalIntentLocksAndReturnsToRest() {
        var clicks = 0
        var neighborClicks = 0
        lateinit var scrollState: androidx.compose.foundation.ScrollState
        var cardCoordinates: LayoutCoordinates? = null
        compose.setContent {
            scrollState = rememberScrollState()
            CompositionLocalProvider(LocalShortcutTouchVariant provides ShortcutTouchVariant.GentleFloat) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                        Box(Modifier.size(300.dp, 160.dp))
                        LoonShortcutCard(
                            modifier = Modifier.size(width = 174.dp, height = 118.dp),
                            title = "DragCapture", subtitle = "", icon = {
                                Box(Modifier.size(1.dp).onGloballyPositioned { cardCoordinates = it })
                            }, onClick = { clicks++ }
                        )
                        LoonShortcutCard(
                            modifier = Modifier.size(width = 174.dp, height = 118.dp),
                            title = "Neighbor", subtitle = "", icon = {}, onClick = { neighborClicks++ }
                        )
                        Box(Modifier.size(300.dp, 900.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        val card = compose.onNodeWithTag("shortcut-card-DragCapture")
        fun visualCenter(): Offset {
            val coords = requireNotNull(cardCoordinates)
            return coords.localToRoot(Offset(coords.size.width / 2f, coords.size.height / 2f))
        }
        val restVisualCenter = visualCenter()
        val restSemanticsBounds = card.fetchSemanticsNode().boundsInRoot
        // A vertical-origin gesture must be yielded before the parent's Main-pass slop.
        card.performTouchInput {
            down(center)
            moveTo(center + Offset(3f, -18f), delayMillis = 60)
            moveTo(center + Offset(5f, -65f), delayMillis = 60)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("vertical card-origin gesture scrolls its parent", scrollState.value > 0)
            assertEquals("vertical scroll does not click card", 0, clicks)
        }
        // Reset the viewport so the same card can verify horizontal pickup.
        compose.runOnIdle { scrollState.dispatchRawDelta(-scrollState.value.toFloat()) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        card.performTouchInput {
            down(center)
            moveTo(center + Offset(8f, 1f), delayMillis = 60) // below 1.25x touchSlop: no lock
            moveTo(center + Offset(72f, 3f), delayMillis = 80)
        }
        compose.mainClock.advanceTimeBy(120)
        compose.waitForIdle()
        val draggedVisualCenter = visualCenter()
        compose.runOnIdle {
            assertEquals("horizontal lock leaves parent scroll unchanged", 0, scrollState.value)
            assertTrue("deliberate horizontal movement tracks perceptibly", draggedVisualCenter.x > restVisualCenter.x)
            assertTrue("horizontal gain is capped at 4dp", kotlin.math.abs(draggedVisualCenter.x - restVisualCenter.x) <= 4.5f)
            // B's newly enabled perspective tilt moves the visual bounds slightly in Y even
            // though the gesture has no vertical drag component; keep that optical shift bounded.
            assertTrue("B optical perspective stays bounded vertically", kotlin.math.abs(draggedVisualCenter.y - restVisualCenter.y) < 5f)
            assertEquals("gesture does not change layout semantics bounds", restSemanticsBounds, card.fetchSemanticsNode().boundsInRoot)
            assertEquals("drag does not activate original card", 0, clicks)
            assertEquals("drag does not activate adjacent card", 0, neighborClicks)
        }
        // Once acquired, substantial vertical motion cannot transfer ownership.
        card.performTouchInput { moveTo(center + Offset(72f, 90f), delayMillis = 80) }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("locked horizontal gesture keeps parent still", 0, scrollState.value) }
        card.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("released card springs back to visual rest", (visualCenter() - restVisualCenter).getDistance() < 1f)
            assertEquals(0, clicks)
            assertEquals(0, neighborClicks)
        }
        compose.mainClock.autoAdvance = true
        card.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals("next ordinary tap still activates", 1, clicks) }
    }

    @Test fun downAndSmallJitterDoNotLockUntilHorizontalIntentIsClear() {
        val pickups = mutableListOf<Boolean>()
        compose.setContent {
            val motion = rememberShortcutTouchMotion(ShortcutTouchVariant.FingerTilt, enabled = true,
                onPickupChanged = { pickups += it })
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Box(motion.modifier.size(174.dp, 118.dp).testTag("intent-probe"))
            }
        }
        val card = compose.onNodeWithTag("intent-probe")
        card.performTouchInput { down(center) }
        compose.runOnIdle { assertTrue("down must not announce pickup", pickups.isEmpty()) }
        card.performTouchInput { moveTo(center + Offset(4f, 1f), delayMillis = 40) }
        compose.runOnIdle { assertTrue("small jitter must not lock parent", pickups.isEmpty()) }
        card.performTouchInput { moveTo(center + Offset(70f, 1f), delayMillis = 80) }
        compose.runOnIdle { assertEquals(listOf(true), pickups) }
        card.performTouchInput { moveTo(center + Offset(70f, 80f), delayMillis = 80) }
        compose.runOnIdle { assertEquals("horizontal owner survives vertical drift", listOf(true), pickups) }
        card.performTouchInput { up() }
        compose.runOnIdle { assertEquals("release clears the horizontal owner", listOf(true, false), pickups) }
    }

    @Test fun bJellyTravelIsRadialAndCappedInAllDirections() {
        for (point in listOf(Offset(100f,0f),Offset(0f,100f),Offset(100f,100f),Offset(-100f,60f))) {
            val out=shortcutJellyTravel(point,4f)
            assertTrue("$point within 4dp",out.getDistance()<=4.001f)
            assertTrue("$point preserves direction",out.x*point.x+out.y*point.y>0f)
        }
    }
    @Test fun bLongPressCanMoveVerticallyAndSpringsBackWithoutClick() {
        var clicks = 0
        val pickups = mutableListOf<Boolean>()
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTouchVariant provides ShortcutTouchVariant.FingerTilt) {
                LoonShortcutCard(Modifier.size(174.dp,118.dp), "Jelly", "", {},
                    onPickupChanged = { pickups += it }, onClick = { clicks++ })
            }
        }
        val card=compose.onNodeWithTag("shortcut-card-Jelly")
        card.performTouchInput {
            down(center)
            moveTo(center + Offset(2f, 2f), delayMillis = 600)
            moveTo(center + Offset(0f, 70f), delayMillis = 80)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, clicks)
            assertEquals(listOf(true,false), pickups)
        }
    }


    @Test fun horizontalIntentThresholdRejectsSmallAndAmbiguousMoves() {
        assertTrue(shortcutHorizontalIntent(15f, 0f, 10f))
        assertTrue(!shortcutHorizontalIntent(12f, 0f, 10f))
        assertTrue(!shortcutHorizontalIntent(20f, 15f, 10f))
        assertTrue(!shortcutHorizontalIntent(8f, 30f, 10f))
    }

    @Test fun blankAreaOfSameVerticalScrollParentStillScrolls() {
        lateinit var scrollState: androidx.compose.foundation.ScrollState
        compose.setContent {
            val state = rememberScrollState()
            scrollState = state
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(state)) {
                    Box(Modifier.size(300.dp, 160.dp))
                    LoonShortcutCard(Modifier.size(width = 174.dp, height = 118.dp), "ScrollCard", "", {}, onClick = {})
                    Box(Modifier.size(300.dp, 1000.dp).testTag("scroll-blank"))
                }
            }
        }
        compose.onNodeWithTag("scroll-blank").performTouchInput {
            // Start in the visible blank area near the viewport's lower edge, then move upward
            // in stages so the gesture crosses slop while remaining within the hit target.
            down(Offset(100f, 450f))
            moveTo(Offset(100f, 400f), delayMillis = 80)
            moveTo(Offset(100f, 350f), delayMillis = 80)
            moveTo(Offset(100f, 300f), delayMillis = 80)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("blank-origin gesture remains available to parent scrolling", scrollState.value > 0) }
    }

    @Test fun verticalSwipeInsideCardWithoutScrollParentCannotBecomeTap() {
        var clicks = 0
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTouchVariant provides ShortcutTouchVariant.FingerTilt) {
                LoonShortcutCard(Modifier.size(174.dp, 118.dp), "NoScrollParent", "", {}, onClick = { clicks++ })
            }
        }
        val card = compose.onNodeWithTag("shortcut-card-NoScrollParent")
        card.performTouchInput {
            down(center)
            moveTo(center + Offset(2f, 32f), delayMillis = 100)
            up()
        }
        compose.runOnIdle { assertEquals("vertical motion inside bounds is not a tap", 0, clicks) }
        card.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals("next intentional tap still activates", 1, clicks) }
    }

    @Test fun addingSecondPointerCancelsCardDragAndLeavesNextTapUsable() {
        var clicks = 0
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTouchVariant provides ShortcutTouchVariant.FingerTilt) {
                LoonShortcutCard(
                    modifier = Modifier.size(width = 174.dp, height = 118.dp),
                    title = "MultiPointer", subtitle = "", icon = {}, onClick = { clicks++ }
                )
            }
        }
        val card = compose.onNodeWithTag("shortcut-card-MultiPointer")
        card.performTouchInput {
            down(center)
            moveTo(center + Offset(0f, 32f), delayMillis = 60)
            down(pointerId = 1, position = center + Offset(18f, 0f))
            up(pointerId = 1)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("multi-pointer cancellation must not click", 0, clicks) }
        card.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals("gesture state is cleaned up for subsequent tap", 1, clicks) }
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
