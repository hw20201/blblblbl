package com.github.arca.android.ui.screens

import com.github.arca.android.data.ProfileItem
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Move one profile into the target row's slot without changing the other rows' relative order. */
internal fun moveQuickProfile(profiles: List<ProfileItem>, draggedId: String, targetId: String): List<ProfileItem> {
    val from = profiles.indexOfFirst { it.id == draggedId }
    val to = profiles.indexOfFirst { it.id == targetId }
    if (from < 0 || to < 0 || from == to) return profiles
    return profiles.toMutableList().apply { add(to, removeAt(from)) }
}

internal fun profileCardQuietElevation(isSelected: Boolean): Dp = if (isSelected) 0.dp else 2.dp

/** The dragged top stays in padded list coordinates, independent of remeasured slots. */
internal data class ProfileDragPosition(
    val id: String,
    val top: Float,
    val released: Boolean = false
) {
    fun translationY(slotOffset: Int): Float = top - slotOffset
}

internal data class ProfileReorderItem(
    val id: String,
    val index: Int,
    val offset: Int,
    val size: Int
) {
    val center: Float get() = offset + size / 2f
}

internal sealed interface ProfileReorderResult {
    data object AwaitLayout : ProfileReorderResult
    data object Cancelled : ProfileReorderResult
    data object Settled : ProfileReorderResult
    data class Move(val from: Int, val to: Int) : ProfileReorderResult {
        fun affectsFirstVisibleItem(index: Int): Boolean = index in minOf(from, to)..maxOf(from, to)
    }
}

/** Lazy item offsets exclude top content padding; pointer input does not. */
internal fun profileDragStart(
    visibleItems: List<ProfileReorderItem>,
    viewportStartOffset: Int,
    pointerY: Float
): ProfileDragPosition? {
    val contentY = pointerY + viewportStartOffset
    return visibleItems.firstOrNull {
        contentY >= it.offset && contentY < it.offset + it.size
    }?.let { ProfileDragPosition(it.id, it.offset.toFloat()) }
}

/** Only reorder against geometry measured for the current order, never the previous frame. */
internal fun profileReorderResult(
    profileIds: List<String>,
    visibleItems: List<ProfileReorderItem>,
    drag: ProfileDragPosition,
    retainedDraggedItem: ProfileReorderItem? = null
): ProfileReorderResult {
    if (drag.id !in profileIds) return ProfileReorderResult.Cancelled
    if (visibleItems.any { profileIds.getOrNull(it.index) != it.id }) {
        return ProfileReorderResult.AwaitLayout
    }
    // A pinned card can be placed outside the viewport after scroll-offset normalization.
    // visibleItemsInfo omits it even though its translated layer is still under the finger.
    val dragged = visibleItems.firstOrNull { it.id == drag.id }
        ?: retainedDraggedItem?.takeIf {
            it.id == drag.id && profileIds.getOrNull(it.index) == it.id
        }
        ?: return ProfileReorderResult.AwaitLayout
    val orderedItems = visibleItems.sortedBy { it.index }
    val center = drag.top + dragged.size / 2f
    // Cross a neighbour's midpoint rather than merely entering its bounds. With unequal
    // heights, entering bounds can still target the same neighbour AFTER swapping it.
    val target = when {
        center > dragged.center -> orderedItems.lastOrNull {
            it.index > dragged.index && center >= it.center
        }
        center < dragged.center -> orderedItems.firstOrNull {
            it.index < dragged.index && center <= it.center
        }
        else -> null
    }
    return if (target == null) ProfileReorderResult.Settled
    else ProfileReorderResult.Move(dragged.index, target.index)
}
