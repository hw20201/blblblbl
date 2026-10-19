package com.github.mihomo.android.ui.glass

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

/** Fractional slot centre, bounded to the existing navigation track. */
internal fun lensDragPosition(x: Float, width: Float, slotCount: Int): Float =
    if (width <= 0f || slotCount <= 1) 0f
    else (x / (width / slotCount) - 0.5f).coerceIn(0f, (slotCount - 1).toFloat())

/** Take ownership only after horizontal touch slop; ordinary tab clicks remain untouched. */
@Composable
internal fun Modifier.navigationLensDrag(
    slotCount: Int,
    onPosition: (Float?) -> Unit,
    onSelected: (Int) -> Unit
): Modifier {
    val updatePosition by rememberUpdatedState(onPosition)
    val select by rememberUpdatedState(onSelected)
    return pointerInput(slotCount) {
        var position = 0f
        detectHorizontalDragGestures(
            onDragStart = { start ->
                position = lensDragPosition(start.x, size.width.toFloat(), slotCount)
                updatePosition(position)
            },
            onHorizontalDrag = { change, _ ->
                change.consume()
                position = lensDragPosition(change.position.x, size.width.toFloat(), slotCount)
                updatePosition(position)
            },
            onDragEnd = {
                select(position.roundToInt().coerceIn(0, slotCount - 1))
                updatePosition(null)
            },
            onDragCancel = { updatePosition(null) }
        )
    }
}
