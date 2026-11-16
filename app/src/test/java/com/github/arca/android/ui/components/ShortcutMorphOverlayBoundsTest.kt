package com.github.arca.android.ui.components

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutMorphOverlayBoundsTest {
    @Test fun expandedPanelClampsAboveDockAndBelowStatusInset() {
        val target = shortcutMorphTargetRect(
            source = Rect(40f, 610f, 150f, 670f),
            rootWidthPx = 400f,
            rootHeightPx = 800f,
            requestedWidthPx = 368f,
            requestedHeightPx = 600f,
            topInsetPx = 32f,
            bottomInsetPx = 104f,
            sideMarginPx = 16f
        )
        assertEquals(96f, target.top, 0.001f)
        assertTrue(target.bottom <= 696f)
        assertEquals(368f, target.width, 0.001f)
        assertEquals(16f, target.left, 0.001f)
    }

    @Test fun panelKeepsItsSourceSideWhileRespectingSafeBounds() {
        val target = shortcutMorphTargetRect(
            source = Rect(280f, 700f, 320f, 740f),
            rootWidthPx = 400f,
            rootHeightPx = 800f,
            requestedWidthPx = 368f,
            requestedHeightPx = 520f,
            topInsetPx = 24f,
            bottomInsetPx = 96f,
            sideMarginPx = 16f
        )
        assertEquals(16f, target.left, 0.001f)
        assertTrue(target.top >= 24f)
        assertTrue(target.bottom <= 704f)
    }
}
