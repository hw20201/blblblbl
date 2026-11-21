package com.github.arca.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileReorderTest {
    @Test fun translationPreservesViewportTopBeforeAndAfterUnequalHeightMove() {
        val drag = ProfileDragPosition("a", 143.25f)
        for (slot in listOf(10, 202, 177, -15)) {
            assertEquals(143.25f, slot + drag.translationY(slot), 0f)
        }
    }

    @Test fun shortCardMovingDownDoesNotSwapBackInsideTallerNeighbour() {
        val drag = ProfileDragPosition("a", 143f)
        assertEquals(ProfileReorderResult.Move(0, 1), decide(listOf("a", "b"), listOf(80, 180), drag))
        assertEquals(ProfileReorderResult.Settled, decide(listOf("b", "a"), listOf(180, 80), drag.copy(top = 144f)))
        // Reversing a few pixels is not a reason to exchange again; cross the new midpoint.
        assertEquals(ProfileReorderResult.Settled, decide(listOf("b", "a"), listOf(180, 80), drag.copy(top = 140f)))
        assertEquals(ProfileReorderResult.Move(1, 0), decide(listOf("b", "a"), listOf(180, 80), drag.copy(top = 49f)))
    }

    @Test fun shortCardMovingUpDoesNotSwapBackInsideTallerNeighbour() {
        val drag = ProfileDragPosition("b", 49f)
        assertEquals(ProfileReorderResult.Move(1, 0), decide(listOf("a", "b"), listOf(180, 80), drag))
        assertEquals(ProfileReorderResult.Settled, decide(listOf("b", "a"), listOf(80, 180), drag.copy(top = 48f)))
        assertEquals(ProfileReorderResult.Settled, decide(listOf("b", "a"), listOf(80, 180), drag.copy(top = 52f)))
        assertEquals(ProfileReorderResult.Move(0, 1), decide(listOf("b", "a"), listOf(80, 180), drag.copy(top = 143f)))
    }

    @Test fun enteringNeighbourOrSpacingWithoutCrossingMidpointDoesNotReorder() {
        val ids = listOf("a", "b")
        for (top in listOf(0f, 50f, 53f, 100f, 141f)) {
            assertEquals(ProfileReorderResult.Settled, decide(ids, listOf(80, 180), ProfileDragPosition("a", top)))
        }
        assertEquals(ProfileReorderResult.Move(0, 1), decide(ids, listOf(80, 180), ProfileDragPosition("a", 142f)))
    }

    @Test fun largeDeltasChooseFarthestCrossedMidpointInEitherDirection() {
        val ids = listOf("a", "b", "c", "d")
        val sizes = List(4) { 80 }
        assertEquals(ProfileReorderResult.Move(0, 2), decide(ids, sizes, ProfileDragPosition("a", 220f)))
        assertEquals(ProfileReorderResult.Move(0, 3), decide(ids, sizes, ProfileDragPosition("a", 500f)))
        assertEquals(ProfileReorderResult.Move(3, 0), decide(ids, sizes, ProfileDragPosition("d", -20f)))
    }

    @Test fun multiplePointerEventsWaitForLayoutWithoutChangingLatestTop() {
        val oldLayout = layout(listOf("a", "b", "c", "d"), List(4) { 80 })
        val order = listOf("b", "a", "c", "d")
        val latest = ProfileDragPosition("a", 220f)
        repeat(3) {
            assertEquals(ProfileReorderResult.AwaitLayout, profileReorderResult(order, oldLayout, latest))
            assertEquals(220f, oldLayout[0].offset + latest.translationY(oldLayout[0].offset), 0f)
        }
        assertEquals(ProfileReorderResult.Move(1, 2), decide(order, List(4) { 80 }, latest))
    }

    @Test fun releaseWhileLayoutIsStaleStillResolvesLastCrossedCard() {
        val oldLayout = layout(listOf("a", "b", "c", "d"), List(4) { 80 })
        val released = ProfileDragPosition("a", 220f, released = true)
        assertEquals(ProfileReorderResult.AwaitLayout,
            profileReorderResult(listOf("b", "a", "c", "d"), oldLayout, released))
        assertEquals(ProfileReorderResult.Move(1, 2),
            decide(listOf("b", "a", "c", "d"), List(4) { 80 }, released))
        assertEquals(ProfileReorderResult.Settled,
            decide(listOf("b", "c", "a", "d"), List(4) { 80 }, released))
    }

    @Test fun staleNonDraggedKeysAlsoInvalidateGeometry() {
        val visible = layout(listOf("a", "c", "b"), List(3) { 80 })
        assertEquals(ProfileReorderResult.AwaitLayout,
            profileReorderResult(listOf("a", "b", "c"), visible, ProfileDragPosition("a", 200f)))
    }

    @Test fun partiallyVisibleItemsAndContentPaddingUseViewportCoordinates() {
        val ids = listOf("a", "b", "c")
        val visible = layout(ids, List(3) { 80 }, firstOffset = -25, firstIndex = 4)
        assertEquals(ProfileReorderResult.Move(4, 5),
            profileReorderResult(listOf("w", "x", "y", "z") + ids, visible, ProfileDragPosition("a", 67f)))
        assertEquals(ProfileReorderResult.Move(0, 1),
            profileReorderResult(ids, layout(ids, List(3) { 80 }, firstOffset = 10), ProfileDragPosition("a", 102f)))
    }

    @Test fun pinOnlyWhenMoveChangesFirstVisibleKeyIndex() {
        val downward = ProfileReorderResult.Move(1, 3)
        val upward = ProfileReorderResult.Move(3, 1)
        for (move in listOf(downward, upward)) {
            assertFalse(move.affectsFirstVisibleItem(0))
            assertTrue(move.affectsFirstVisibleItem(1))
            assertTrue(move.affectsFirstVisibleItem(2))
            assertTrue(move.affectsFirstVisibleItem(3))
            assertFalse(move.affectsFirstVisibleItem(4))
        }
    }

    @Test fun missingMeasuredSlotWaitsAndRemovedProfileCancels() {
        assertEquals(ProfileReorderResult.AwaitLayout,
            profileReorderResult(listOf("a", "b"), layout(listOf("b"), listOf(80), firstIndex = 1), ProfileDragPosition("a", 0f)))
        assertEquals(ProfileReorderResult.Cancelled,
            profileReorderResult(listOf("b"), layout(listOf("b"), listOf(80)), ProfileDragPosition("a", 0f, released = true)))
    }

    @Test fun hitTestingSubtractsTopPaddingAndRejectsPaddingAndSpacing() {
        val visible = layout(listOf("a", "b"), listOf(80, 180))
        assertEquals(null, profileDragStart(visible, -10, 5f))
        assertEquals(ProfileDragPosition("a", 0f), profileDragStart(visible, -10, 89f))
        assertEquals(null, profileDragStart(visible, -10, 95f))
        assertEquals(ProfileDragPosition("b", 92f), profileDragStart(visible, -10, 102f))
    }

    @Test fun retainedShortCardOutsideNormalizedViewportStillSettlesAndCanMoveBack() {
        val order = listOf("a", "b", "c")
        val retained = ProfileReorderItem("a", 0, -150, 80)
        val visible = listOf(ProfileReorderItem("b", 1, -58, 180), ProfileReorderItem("c", 2, 134, 80))
        assertEquals(ProfileReorderResult.Settled,
            profileReorderResult(order, visible, ProfileDragPosition("a", -100f, released = true), retained))
        assertEquals(ProfileReorderResult.Move(0, 1),
            profileReorderResult(order, visible, ProfileDragPosition("a", 0f), retained))
    }

    @Test fun retainedGeometryMustMatchTheCurrentDraggedKeyAndIndex() {
        val order = listOf("a", "b")
        val visible = listOf(ProfileReorderItem("b", 1, 20, 180))
        for (retained in listOf(ProfileReorderItem("a", 1, -72, 80), ProfileReorderItem("b", 0, -72, 80))) {
            assertEquals(ProfileReorderResult.AwaitLayout,
                profileReorderResult(order, visible, ProfileDragPosition("a", 0f, released = true), retained))
        }
    }

    @Test fun measuredNeighbourOrderingIsByIndexNotCollectionIteration() {
        val ids = listOf("a", "b", "c", "d")
        assertEquals(ProfileReorderResult.Move(0, 2),
            profileReorderResult(ids, layout(ids, List(4) { 80 }).reversed(), ProfileDragPosition("a", 220f)))
        assertEquals(ProfileReorderResult.Move(3, 0),
            profileReorderResult(ids, layout(ids, List(4) { 80 }).reversed(), ProfileDragPosition("d", -20f)))
    }

    private fun decide(ids: List<String>, sizes: List<Int>, drag: ProfileDragPosition): ProfileReorderResult =
        profileReorderResult(ids, layout(ids, sizes), drag)

    private fun layout(
        ids: List<String>,
        sizes: List<Int>,
        firstOffset: Int = 0,
        firstIndex: Int = 0
    ): List<ProfileReorderItem> {
        var offset = firstOffset
        return ids.mapIndexed { index, id ->
            ProfileReorderItem(id, firstIndex + index, offset, sizes[index]).also {
                offset += sizes[index] + 12
            }
        }
    }
}
