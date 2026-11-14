package com.github.arca.android.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for the 3 -> 1 swipe (2026-11-05).
 *
 * FlClash _slide updates _pressedIndex as the finger crosses each slot, so
 * the release clamp in _fling (pressed +/- 1) is relative to the CURRENT
 * finger slot. The Arca port first set pressedIndex only on press, which
 * froze the clamp on the start slot: a drag from slot 2 to slot 0 could
 * only ever land on slot 1. These tests pin the clamp's contract given a
 * correctly tracked pressedIndex (the tracking itself lives in
 * navigationLensDrag's onHorizontalDrag).
 */
class LensFlingIndexTest {

    @Test
    fun twoSlotDragBackLandsOnFarSlot() {
        // Finger dragged from slot 2 all the way to slot 0; pressedIndex
        // tracked it to 0, so the clamp must not pull the landing back to 1.
        assertEquals(0, lensFlingIndex(position = 0.1f, velocitySlotsPerSec = -1f, pressedIndex = 0, slotCount = 3))
        assertEquals(0, lensFlingIndex(position = 0.0f, velocitySlotsPerSec = 0f, pressedIndex = 0, slotCount = 3))
    }

    @Test
    fun twoSlotDragForwardLandsOnFarSlot() {
        assertEquals(2, lensFlingIndex(position = 1.9f, velocitySlotsPerSec = 1f, pressedIndex = 2, slotCount = 3))
        assertEquals(2, lensFlingIndex(position = 2.0f, velocitySlotsPerSec = 0f, pressedIndex = 2, slotCount = 3))
    }

    @Test
    fun fastFlingCarriesAtMostOneSlotPastFinger() {
        // Finger is on slot 1, flicking hard toward slot 2: at most one past.
        assertEquals(2, lensFlingIndex(position = 1.2f, velocitySlotsPerSec = 12f, pressedIndex = 1, slotCount = 3))
        // Finger is on slot 0, flicking hard toward slot 2: still at most one past.
        assertEquals(1, lensFlingIndex(position = 0.2f, velocitySlotsPerSec = 12f, pressedIndex = 0, slotCount = 3))
    }

    @Test
    fun flingNeverLeavesTrack() {
        assertEquals(0, lensFlingIndex(position = 0.1f, velocitySlotsPerSec = -20f, pressedIndex = 0, slotCount = 3))
        assertEquals(2, lensFlingIndex(position = 1.9f, velocitySlotsPerSec = 20f, pressedIndex = 2, slotCount = 3))
    }
}
