package com.github.arca.android.ui.glass

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

/** FlClash _rubberBand: asymptotic pull, limit _overdrag 0.35 slots. A small
 * overshoot follows almost fully; a big one saturates at the limit. */
internal fun lensRubberBand(overshoot: Float, limit: Float): Float {
    val pull = 1f - 1f / (kotlin.math.abs(overshoot) * 0.55f / limit + 1f)
    return limit * pull * kotlin.math.sign(overshoot)
}

/** Fractional slot centre, with FlClash _positionAt rubber-banding past the ends. */
internal fun lensDragPosition(x: Float, width: Float, slotCount: Int): Float {
    if (width <= 0f || slotCount <= 1) return 0f
    val max = (slotCount - 1).toFloat()
    val raw = x / (width / slotCount) - 0.5f
    return when {
        raw < 0f -> lensRubberBand(raw, 0.35f)
        raw > max -> max + lensRubberBand(raw - max, 0.35f)
        else -> raw
    }
}

/** FlClash _fling: project the release velocity 100ms ahead, carry the lens at
 * most one slot past the finger, then clamp to the track. */
internal fun lensFlingIndex(position: Float, velocitySlotsPerSec: Float, pressedIndex: Int, slotCount: Int): Int {
    val lead = velocitySlotsPerSec * 0.1f
    return (position + lead).roundToInt()
        .coerceIn(pressedIndex - 1, pressedIndex + 1)
        .coerceIn(0, slotCount - 1)
}

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
        var pressedIndex = 0
        // Last two drag samples for release-velocity projection (FlClash _fling).
        // FlClash uses the pointer's own VelocityTracker; here the time comes
        // from PointerInputChange.uptimeMillis, not the wall clock (coarse and
        // unrelated to the input stream). onDragStart only gives an Offset, so
        // the clock starts at the first move event.
        var lastX = 0f
        var lastTime = -1L
        var velocity = 0f
        detectHorizontalDragGestures(
            onDragStart = { start ->
                position = lensDragPosition(start.x, size.width.toFloat(), slotCount)
                pressedIndex = position.roundToInt().coerceIn(0, slotCount - 1)
                lastX = start.x
                lastTime = -1L
                velocity = 0f
                updatePosition(position)
            },
            onHorizontalDrag = { change, _ ->
                change.consume()
                if (lastTime < 0L) {
                    lastX = change.position.x
                    lastTime = change.uptimeMillis
                } else {
                    val dt = (change.uptimeMillis - lastTime) / 1000f
                    if (dt > 0.001f) {
                        val slotWidth = size.width.toFloat() / slotCount
                        velocity = (change.position.x - lastX) / slotWidth / dt
                        lastX = change.position.x
                        lastTime = change.uptimeMillis
                    }
                }
                position = lensDragPosition(change.position.x, size.width.toFloat(), slotCount)
                // FlClash _slide: _pressedIndex tracks the finger across slots
                // (with a haptic on each crossing). The release clamp in
                // lensFlingIndex is relative to the CURRENT finger slot, so a
                // multi-slot drag (e.g. slot 2 -> slot 0) still lands. Without
                // this the clamp froze on the press slot and a 2-slot swipe
                // could only ever move one slot.
                pressedIndex = position.roundToInt().coerceIn(0, slotCount - 1)
                updatePosition(position)
            },
            onDragEnd = {
                select(lensFlingIndex(position, velocity, pressedIndex, slotCount))
                updatePosition(null)
            },
            onDragCancel = { updatePosition(null) }
        )
    }
}
