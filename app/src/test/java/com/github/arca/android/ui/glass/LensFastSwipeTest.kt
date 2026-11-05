package com.github.arca.android.ui.glass

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the fast-swipe capsule jitter (2026-11-05).
 *
 * Replays a fast swipe between two slots (0 -> 1 -> 0, quick re-press 3
 * frames after release) frame by frame, mirroring the production
 * NavigationLens effect + Layout:
 *  - a drag event restarts the effect, so only the initial block runs
 *    (travel retarget on the follow spring; jelly advances there too);
 *  - otherwise the while-loop body runs once per frame;
 *  - the displayed position is always frame.position (the spring value),
 *    never the raw finger position.
 *
 * Two invariants:
 *  1. The displayed position never steps against the finger's direction by
 *     more than 0.05 slots. The old display rule (finger position while
 *     dragging, spring value after release) teleported the capsule back
 *     half a slot on every release, both directions; the spring-only rule
 *     measures ~0.0002 slots on a clean swipe. The 0.05 bound leaves room
 *     for honest spring momentum: on a fast reversal the retarget keeps
 *     the lens's velocity (as FlClash does), so it glides forward a few
 *     hundredths of a slot before turning around. That glide is continuous
 *     motion, not a teleport.
 *  2. Jelly keeps moving during a drag after a quick re-press. The old
 *     effect left jelly frozen mid-drag (it only ticked in the while loop,
 *     which never runs while the finger moves), so the capsule stayed stuck
 *     tall/narrow for a whole swipe.
 */
class LensFastSwipeTest {

    private val frameNanos = 16_666_667L
    private val slotCount = 3
    private val barWidth = 304f

    private inner class Replay {
        val travel = LensSpring(0f, LensMotion.travelStiffness, LensMotion.travelDamping)
        val jelly = LensSpring(0f, LensMotion.jellyStiffness, LensMotion.jellyDamping)
        val expansion = LensSpring(0f, LensMotion.expansionStiffness, 1f)
        var destination = 0f
        var dragPosition: Float? = null
        var frame = LensFrame(0f, 0f, 0f)
        var stateChanged = true
        var step = 0
        // Per-frame records: displayed position, jelly value, phase B?
        val positions = mutableListOf<Float>()
        val jellies = mutableListOf<Float>()
        val phaseBFlags = mutableListOf<Boolean>()
        var phaseB = false

        fun initialBlock(now: Long) {
            expansion.tick(now)
            if (dragPosition != null) {
                travel.springTo(
                    dragPosition!!.coerceIn(-0.35f, (slotCount - 1).toFloat() + 0.35f),
                    now, LensMotion.followStiffness, LensMotion.followDamping
                )
                expansion.springTo(1f, now)
            } else if (destination != travel.target) {
                travel.springTo(destination, now)
                expansion.springTo(1f, now)
            }
            if (dragPosition == null && lensReadyToContract(expansion, travel, jelly)) {
                expansion.springTo(0f, now)
            }
            jelly.tick(now)
            jelly.springTo(lensJellyTarget(travel.velocity, travel.acceleration), now)
            frame = LensFrame(travel.value, lensVisibleStretch(jelly.value, travel.velocity), expansion.value)
        }

        fun loopBody(now: Long) {
            travel.tick(now)
            expansion.tick(now)
            if (dragPosition == null && lensReadyToContract(expansion, travel, jelly)) {
                expansion.springTo(0f, now)
            }
            jelly.tick(now)
            jelly.springTo(lensJellyTarget(travel.velocity, travel.acceleration), now)
            frame = LensFrame(travel.value, lensVisibleStretch(jelly.value, travel.velocity), expansion.value)
        }

        fun runFrame() {
            val now = step * frameNanos
            if (stateChanged) initialBlock(now) else loopBody(now)
            stateChanged = false
            positions.add(frame.position)
            jellies.add(jelly.value)
            phaseBFlags.add(phaseB)
            step++
        }
    }

    private fun replay(): Replay {
        val r = Replay()
        val pitch = barWidth / slotCount
        fun fingerX(slot: Float) = (slot + 0.5f) * pitch
        // Forward swipe: 8 drag events, finger 0 -> 1.
        for (i in 1..8) {
            r.dragPosition = lensDragPosition(fingerX(i / 8f), barWidth, slotCount)
            r.stateChanged = true
            r.runFrame()
        }
        // Release on slot 1, only 3 settle frames, then quick re-press.
        r.destination = 1f
        r.dragPosition = null
        r.stateChanged = true
        r.runFrame()
        repeat(3) { r.runFrame() }
        // Back swipe: 8 drag events, finger 1 -> 0.
        r.phaseB = true
        for (i in 1..8) {
            r.dragPosition = lensDragPosition(fingerX(1f - i / 8f), barWidth, slotCount)
            r.stateChanged = true
            r.runFrame()
        }
        // Release on slot 0, settle.
        r.destination = 0f
        r.dragPosition = null
        r.stateChanged = true
        r.runFrame()
        repeat(20) { r.runFrame() }
        return r
    }

    @Test
    fun fastSwipeNeverStepsAgainstFingerDirection() {
        val r = replay()
        var againstForward = 0f
        var againstBack = 0f
        for (i in 1 until r.positions.size) {
            val d = r.positions[i] - r.positions[i - 1]
            if (!r.phaseBFlags[i] && d < 0) againstForward = maxOf(againstForward, -d)
            if (r.phaseBFlags[i] && d > 0) againstBack = maxOf(againstBack, d)
        }
        println("fastSwipe againstForward=$againstForward againstBack=$againstBack")
        assertTrue("capsule stepped back during forward swipe: $againstForward", againstForward < 0.05f)
        assertTrue("capsule stepped forward during back swipe: $againstBack", againstBack < 0.05f)
    }

    @Test
    fun jellyKeepsMovingDuringDragAfterQuickRepress() {
        val r = replay()
        // Phase B drag frames: the first 8 frames with phaseB true. Frozen
        // jelly would not move at all; live jelly relaxes from the release
        // impact and heads for the next one.
        val firstB = r.phaseBFlags.indexOfFirst { it }
        val dragJellies = r.jellies.subList(firstB, firstB + 8)
        val range = (dragJellies.maxOrNull() ?: 0f) - (dragJellies.minOrNull() ?: 0f)
        println("fastSwipe jellyRangeDuringBackDrag=$range")
        assertTrue("jelly froze during drag after quick re-press", range > 0.001f)
    }
}
