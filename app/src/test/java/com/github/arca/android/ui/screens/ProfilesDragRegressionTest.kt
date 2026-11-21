package com.github.arca.android.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.ProfileItem
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Real list remeasurement, keyed anchoring and pointer routing; not endpoint-only swipes. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-mdpi")
class ProfilesDragRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val profiles = mutableStateOf<List<ProfileItem>>(emptyList())
    private var savedOrders = mutableListOf<List<String>>()
    private var selections = mutableListOf<String>()
    private var refreshes = mutableListOf<String>()
    private var pullRefreshes = 0
    private var pointerDown = false

    @After fun resetGlass() {
        if (pointerDown) {
            compose.onNodeWithTag("profiles-list").performTouchInput { cancel() }
            pointerDown = false
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        GlassDefaults.enabled = true
        GlassDefaults.quietPages = true
    }

    @Test fun glassDragFollowsFingerWithoutAnchorJumpAndPreservesActions() = checkDrag(true)
    @Test fun opaqueDragFollowsFingerWithoutAnchorJumpAndPreservesActions() = checkDrag(false)

    @Test fun cancelRestoresOriginalOrderWithoutSelectionOrPersistence() {
        show(glass = true)
        val original = profiles.value.map { it.id }
        val initialCenter = centerY("a")
        val displacement = centerY("b") - initialCenter + 4f
        pickup("a")
        move(displacement)
        assertEquals("cancel must exercise an active card drag", initialCenter + displacement, centerY("a"), 1.5f)
        compose.onNodeWithTag("profiles-list").performTouchInput { cancel() }
        pointerDown = false
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(original, profiles.value.map { it.id })
        assertTrue(centerY("a") < centerY("b"))
        assertTrue(savedOrders.isEmpty())
        assertTrue(selections.isEmpty())
        assertEquals(0, pullRefreshes)
    }

    @Test fun partiallyScrolledTallFirstSlotRetainsShortCardUnderFinger() {
        show(glass = true, tallFirst = true)
        val shortHeight = card("a").fetchSemanticsNode().size.height.toFloat()
        val tallHeight = card("b").fetchSemanticsNode().size.height.toFloat()
        val scroll = shortHeight + 24f
        assertTrue("fixture must normalize the shorter first slot out of the viewport", tallHeight > scroll)
        compose.onNodeWithTag("profiles-list").performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, scroll)
        }
        compose.waitForIdle()
        val initialCenter = centerY("a")
        val targetCenter = centerY("b") - 4f
        val outsideRangeNeighbor = centerY("c")
        pickup("a")
        move(targetCenter - initialCenter)
        // The short card's natural new slot is wholly above the viewport. Its pinned,
        // translated layer must nevertheless remain composed and track the held pointer.
        card("a").assertExists()
        assertEquals(targetCenter, centerY("a"), 1.5f)
        assertEquals(outsideRangeNeighbor, centerY("c"), 1.5f)
        repeat(4) {
            move(1f)
            assertEquals(targetCenter + it + 1f, centerY("a"), 1.5f)
            assertEquals(outsideRangeNeighbor, centerY("c"), 1.5f)
        }
        release()
        assertEquals(1, savedOrders.size)
        assertEquals(listOf("a", "b", "c"), savedOrders.single().take(3))
        assertTrue(selections.isEmpty())
        assertEquals(0, pullRefreshes)
    }

    @Test fun releaseBeforeNextLayoutPersistsLatestCrossedPositionExactlyOnce() {
        show(glass = true)
        val delta = centerY("c") - centerY("a") + 4f
        pickup("a")
        compose.onNodeWithTag("profiles-list").performTouchInput {
            moveBy(Offset(0f, delta / 2f), delayMillis = 16)
            moveBy(Offset(0f, delta / 2f), delayMillis = 16)
            up()
        }
        pointerDown = false
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1, savedOrders.size)
        assertEquals(listOf("b", "c", "a"), savedOrders.single().take(3))
        assertTrue(selections.isEmpty())
    }

    @Test fun pickupAndImmediateMovesBeforeAnotherFrameStillPersistExactlyOnce() {
        show(glass = true)
        val bounds = card("a").getUnclippedBoundsInRoot()
        val listBounds = compose.onNodeWithTag("profiles-list").getUnclippedBoundsInRoot()
        val point = Offset(bounds.left.value + (bounds.right.value - bounds.left.value) * .25f - listBounds.left.value,
            centerY("a") - listBounds.top.value)
        val delta = centerY("c") - centerY("a") + 4f
        compose.mainClock.autoAdvance = false
        pointerDown = true
        compose.onNodeWithTag("profiles-list").performTouchInput {
            down(point)
            advanceEventTime(650)
            moveBy(Offset.Zero)
            moveBy(Offset(0f, delta / 2f), delayMillis = 1)
            moveBy(Offset(0f, delta / 2f), delayMillis = 1)
            up()
        }
        pointerDown = false
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1, savedOrders.size)
        assertEquals(listOf("b", "c", "a"), savedOrders.single().take(3))
        assertTrue(selections.isEmpty())
        assertEquals(0, pullRefreshes)
    }

    @Test fun longHoldInCardGapDoesNotStealOrdinaryScrolling() = checkNonCardLongHoldScroll(inGap = true)
    @Test fun longHoldInTopPaddingDoesNotStealOrdinaryScrolling() = checkNonCardLongHoldScroll(inGap = false)

    private fun checkNonCardLongHoldScroll(inGap: Boolean) {
        show(glass = true)
        val list = compose.onNodeWithTag("profiles-list")
        val listBounds = list.getUnclippedBoundsInRoot()
        val y = if (inGap) {
            val first = card("a").getUnclippedBoundsInRoot()
            val second = card("b").getUnclippedBoundsInRoot()
            assertTrue("fixture includes the 12dp inter-card gap", second.top.value > first.bottom.value)
            (first.bottom.value + second.top.value) / 2f - listBounds.top.value
        } else 5f // Middle of the 10dp top content padding, not the first card.
        val before = centerY("c")
        val original = profiles.value.map { it.id }
        compose.mainClock.autoAdvance = false
        pointerDown = true
        list.performTouchInput {
            down(Offset(width / 2f, y))
            advanceEventTime(650)
            moveBy(Offset.Zero)
        }
        compose.waitForIdle()
        move(-24f)
        move(-24f)
        assertTrue("a rejected long hold must leave upward scrolling available", centerY("c") < before - 8f)
        release()
        assertEquals(original, profiles.value.map { it.id })
        assertTrue(savedOrders.isEmpty())
        assertTrue(selections.isEmpty())
        assertTrue(refreshes.isEmpty())
        assertEquals(0, pullRefreshes)
    }

    private fun checkDrag(glass: Boolean) {
        show(glass)
        val initialCenter = centerY("a")
        val initialNeighborCenter = centerY("b")
        var previousNeighborCenter = initialNeighborCenter
        var sawSmoothNeighborMotion = false
        val outsideRangeNeighbor = centerY("c")
        val displacement = centerY("b") - initialCenter + 4f
        pickup("a")
        var travelled = 0f
        repeat(8) { frame ->
            val step = displacement / 8f
            travelled += step
            move(step) {
                assertEquals("dragged center follows finger at frame $frame", initialCenter + travelled, centerY("a"), 1.5f)
                assertEquals("keyed first-item anchoring must not move the unaffected neighbor", outsideRangeNeighbor, centerY("c"), 1.5f)
                val currentNeighborCenter = centerY("b")
                if (currentNeighborCenter < previousNeighborCenter - 0.1f &&
                    currentNeighborCenter > initialCenter + 1f && currentNeighborCenter < initialNeighborCenter - 1f
                ) sawSmoothNeighborMotion = true
                previousNeighborCenter = currentNeighborCenter
            }
        }
        assertTrue("the adjacent card must pass through intermediate positions during its placement animation", sawSmoothNeighborMotion)
        // Small reversal after a swap must not exchange the unequal-height neighbor again.
        move(-2f)
        assertEquals(initialCenter + travelled - 2f, centerY("a"), 1.5f)
        release()
        assertEquals(1, savedOrders.size)
        assertEquals(listOf("b", "a", "c"), savedOrders.single().take(3))
        assertEquals(savedOrders.single(), profiles.value.map { it.id })
        assertTrue(centerY("b") < centerY("a"))
        assertTrue(selections.isEmpty())
        assertEquals(0, pullRefreshes)
        card("a").performTouchInput { click(Offset(width * .25f, height * .3f)) }
        compose.onNodeWithContentDescription("刷新").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(listOf("a"), selections)
        assertEquals(listOf("b"), refreshes)
        assertEquals(1, savedOrders.size)
    }

    private fun show(glass: Boolean, tallFirst: Boolean = false) {
        GlassDefaults.enabled = glass
        GlassDefaults.quietPages = true
        val short = ProfileItem("a", "Local short", null, File("/tmp/profile-drag-a.yaml"), lastUpdated = 1L)
        val tall = ProfileItem("b", "Remote with traffic", "https://example.invalid/sub", File("/tmp/profile-drag-b.yaml"),
            lastUpdated = 1L, totalBytes = 1_000_000L, downloadBytes = 20_000L)
        profiles.value = (if (tallFirst) listOf(tall, short) else listOf(short, tall)) +
            (2..13).map { index ->
                val id = ('a'.code + index).toChar().toString()
                ProfileItem(id, "Local $id", null, File("/tmp/profile-drag-$id.yaml"), lastUpdated = 1L)
            }
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        ProfilesScreen(
                            profiles = profiles.value,
                            selectedProfileId = "a",
                            onSelectProfile = { selections.add(it.id) },
                            onUpdateProfile = {},
                            onDeleteProfile = {},
                            onRefreshSubscription = { refreshes.add(it.id) },
                            onRefreshAllSubscriptions = { pullRefreshes++ },
                            onReorderProfiles = {
                                savedOrders.add(it.map { profile -> profile.id })
                                profiles.value = it
                            },
                            embeddedPanel = true
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun card(id: String) = compose.onNodeWithTag("profile-card-$id")
    private fun centerY(id: String): Float {
        // Unclipped semantics bounds combine a transformed origin with unscaled size.
        // Map the actual center, including pickup scale and offscreen pinned cards.
        val coordinates = card(id).fetchSemanticsNode().layoutInfo.coordinates
        return coordinates.localToRoot(Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)).y
    }

    private fun pickup(id: String) {
        val cardBounds = card(id).getUnclippedBoundsInRoot()
        val listBounds = compose.onNodeWithTag("profiles-list").getUnclippedBoundsInRoot()
        val point = Offset(cardBounds.left.value + (cardBounds.right.value - cardBounds.left.value) * .25f - listBounds.left.value,
            (cardBounds.top.value + cardBounds.bottom.value) / 2f - listBounds.top.value)
        compose.mainClock.autoAdvance = false
        pointerDown = true
        compose.onNodeWithTag("profiles-list").performTouchInput {
            down(point)
            advanceEventTime(650)
            moveBy(Offset.Zero)
        }
        compose.waitForIdle()
    }

    private fun move(delta: Float, checkFrame: (() -> Unit)? = null) {
        compose.onNodeWithTag("profiles-list").performTouchInput { moveBy(Offset(0f, delta), delayMillis = 16) }
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            checkFrame?.invoke()
        }
    }

    private fun release() {
        compose.onNodeWithTag("profiles-list").performTouchInput { up() }
        pointerDown = false
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }
}
