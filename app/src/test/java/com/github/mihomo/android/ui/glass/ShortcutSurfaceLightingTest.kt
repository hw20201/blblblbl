package com.github.mihomo.android.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ShortcutSurfaceLightingTest {
    @Test fun neutralPoseKeepsTheOriginalUpperEdgeLightDirection() {
        val direction = shortcutSurfaceLightDirection(0f, 0f)
        assertTrue(direction.y < 0f)
        assertEquals(1f, hypot(direction.x, direction.y), .0001f)
    }

    @Test fun oppositeEdgeTiltsMoveTheHighlightInOppositeDirections() {
        val left = shortcutSurfaceLightDirection(0f, -6f)
        val right = shortcutSurfaceLightDirection(0f, 6f)
        val top = shortcutSurfaceLightDirection(-6f, 0f)
        val bottom = shortcutSurfaceLightDirection(6f, 0f)
        assertTrue("left/right tilt moves the light horizontally", left.x * right.x < 0f)
        assertTrue("top/bottom tilt changes the light's vertical weight", top.y > bottom.y + .15f)
        listOf(left, right, top, bottom).forEach {
            assertTrue(hypot(it.x, it.y) in .9999f..1.0001f)
        }
    }

    @Test fun NaNAndOutOfRangeAnglesAreSafeAndClamped() {
        assertEquals(shortcutSurfaceLightDirection(0f, 0f),
            shortcutSurfaceLightDirection(Float.NaN, Float.POSITIVE_INFINITY))
        val capped = shortcutSurfaceLightDirection(-100f, 100f)
        val bounded = shortcutSurfaceLightDirection(-6f, 6f)
        assertEquals(bounded, capped)
    }
}
