package com.github.arca.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardShortcutOrderTest {
    private fun target(start: Int, dx: Float = 0f, dy: Float = 0f, count: Int = 6): Int =
        shortcutDragTargetSlot(start, dx, dy, columnStride = 186f, rowStride = 130f, itemCount = count)

    @Test fun verticalMovesCrossRowsInEitherColumn() {
        assertEquals(2, target(0, dy = 130f))
        assertEquals(3, target(1, dy = 130f))
        assertEquals(0, target(2, dy = -130f))
        assertEquals(1, target(3, dy = -130f))
    }

    @Test fun rowThresholdIsTheMidpointInBothDirectionsIncludingTheGap() {
        assertEquals(0, target(0, dy = 64f))
        assertEquals(2, target(0, dy = 66f))
        assertEquals(2, target(2, dy = -64f))
        assertEquals(0, target(2, dy = -66f))
    }

    @Test fun horizontalAndDiagonalMovesKeepTwoColumnSlots() {
        assertEquals(1, target(0, dx = 100f))
        assertEquals(0, target(1, dx = -100f))
        assertEquals(3, target(0, dx = 186f, dy = 130f))
        assertEquals(0, target(3, dx = -186f, dy = -130f))
    }

    @Test fun edgesAndTheEmptySlotOfAnOddLastRowStayInBounds() {
        assertEquals(0, target(0, dx = -1000f, dy = -1000f))
        assertEquals(5, target(0, dx = 1000f, dy = 1000f))
        assertEquals(4, target(1, dy = 260f, count = 5))
        assertEquals(0, target(0, dx = 1000f, dy = 1000f, count = 1))
        assertEquals(-1, target(0, count = 0))
        assertEquals(-1, target(-1))
    }

    @Test fun crossingSeveralRowsAndReturningUsesTheOriginalSlot() {
        assertEquals(4, target(0, dy = 260f))
        assertEquals(0, target(0, dy = 0f))
        assertEquals(1, target(5, dy = -260f))
    }

    private val order = listOf("nodes", "ip_test", "profiles", "future_shortcut", "rules", "dns", "vpn")
    private val displayed = listOf("nodes", "profiles", "rules", "dns")

    @Test fun downwardMoveInsertsAtThePreviewedVisibleSlotWithoutDroppingHiddenIds() {
        val result = reorderDashboardShortcut(order, displayed, "nodes", 2)
        assertEquals(listOf("ip_test", "profiles", "future_shortcut", "rules", "nodes", "dns", "vpn"), result)
        assertEquals(listOf("profiles", "rules", "nodes", "dns"), result.filter { it in displayed })
        assertEquals(order.toSet(), result.toSet())
        assertEquals(order.size, result.size)
    }

    @Test fun upwardMoveInsertsAtThePreviewedVisibleSlotWithoutDroppingHiddenIds() {
        val result = reorderDashboardShortcut(order, displayed, "dns", 1)
        assertEquals(listOf("nodes", "ip_test", "dns", "profiles", "future_shortcut", "rules", "vpn"), result)
        assertEquals(listOf("nodes", "dns", "profiles", "rules"), result.filter { it in displayed })
        assertEquals(order.filterNot { it in displayed }, result.filterNot { it in displayed })
    }

    @Test fun noMoveAndStaleTargetsLeaveTheFullSavedOrderAlone() {
        assertEquals(order, reorderDashboardShortcut(order, displayed, "nodes", 0))
        assertEquals(order, reorderDashboardShortcut(order, displayed, "nodes", -1))
        assertEquals(order, reorderDashboardShortcut(order, displayed, "nodes", 4))
        assertEquals(order, reorderDashboardShortcut(order, displayed, "hidden", 1))
        assertEquals(order, reorderDashboardShortcut(order, emptyList(), "nodes", 0))
        assertEquals(order, reorderDashboardShortcut(order, displayed + "removed", "nodes", 4))
    }

    @Test fun everyVisibleSourceAndTargetPreservesTheOrderPreviewAndAllSavedEntries() {
        for (from in displayed.indices) {
            for (to in displayed.indices) {
                val expected = displayed.toMutableList().apply { add(to, removeAt(from)) }
                val result = reorderDashboardShortcut(order, displayed, displayed[from], to)
                assertEquals("$from -> $to", expected, result.filter { it in displayed })
                assertEquals(order.filterNot { it in displayed }, result.filterNot { it in displayed })
                assertEquals(order.toSet(), result.toSet())
                assertEquals(order.size, result.size)
            }
        }
    }
}
