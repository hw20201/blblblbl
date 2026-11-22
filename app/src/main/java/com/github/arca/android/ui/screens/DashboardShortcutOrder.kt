package com.github.arca.android.ui.screens

import kotlin.math.roundToInt

/** Nearest two-column slot, measured from the original slot in unscaled grid pixels. */
internal fun shortcutDragTargetSlot(
    startIndex: Int,
    dragX: Float,
    dragY: Float,
    columnStride: Float,
    rowStride: Float,
    itemCount: Int
): Int {
    if (startIndex !in 0 until itemCount) return -1
    val column = (startIndex % 2 + dragX / columnStride).roundToInt().coerceIn(0, 1)
    val row = (startIndex / 2 + dragY / rowStride).roundToInt().coerceIn(0, (itemCount - 1) / 2)
    return (row * 2 + column).coerceAtMost(itemCount - 1)
}

/** Move within the full saved order so hidden and currently unsupported entries survive. */
internal fun reorderDashboardShortcut(
    order: List<String>,
    displayedIds: List<String>,
    draggedId: String,
    targetSlot: Int
): List<String> {
    val sourceSlot = displayedIds.indexOf(draggedId)
    if (sourceSlot == -1 || targetSlot !in displayedIds.indices || sourceSlot == targetSlot) return order
    val source = order.indexOf(draggedId)
    val target = order.indexOf(displayedIds[targetSlot])
    if (source == -1 || target == -1) return order
    return order.toMutableList().apply { add(target, removeAt(source)) }
}
