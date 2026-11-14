package com.github.arca.android.ui.glass

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Numerical regressions for the lens motion; no Compose runtime or frame clock is required. */
class LensMotionSmoothnessTest {
    private val frameNanos = 16_000_000L

    @Test
    fun ordinaryTravelOvershootsOnlyWithinFlClashBounce() {
        // FlClash travel is withDurationAndBounce(400ms, bounce 0.15): it
        // overshoots the slot a touch and bounces back at the edge. That is
        // the intended feel, so the bound is the FlClash overshoot (~1% of
        // the 2-slot travel), not zero.
        val spring = LensSpring(0f, LensMotion.travelStiffness, LensMotion.travelDamping)
        spring.springTo(2f, 0L)
        var previous = spring.value
        var samples = 0
        while (spring.running && samples < 2_000) {
            spring.tick((samples + 1L) * frameNanos)
            assertTrue("travel moved backwards too far at sample $samples: ${spring.value} < $previous", spring.value >= previous - 0.05f)
            assertTrue("travel overshot beyond FlClash bounce", spring.value <= 2.05f)
            previous = spring.value
            samples++
        }
        assertEquals(2f, spring.value, 0f)
        assertTrue("spring should settle", !spring.running)
        println("ordinaryTravel samples=$samples settle=${spring.value}")
    }

    @Test
    fun repeatedlyRetargetedJellyHasBoundedVisibleStretchAndSettles() {
        val travel = LensSpring(0f, LensMotion.travelStiffness, LensMotion.travelDamping)
        val jelly = LensSpring(0f, LensMotion.jellyStiffness, LensMotion.jellyDamping)
        var maxVisible = 0f
        var now = 0L
        var tick = 0
        // Repeated destination changes keep travel moving and continuously retarget jelly.
        val destinations = listOf(2f, 0f, 2f, 1f, 0f, 2f)
        for (destination in destinations) {
            travel.springTo(destination, now)
            repeat(24) {
                now += frameNanos
                travel.tick(now)
                jelly.tick(now)
                jelly.springTo(lensJellyTarget(travel.velocity, travel.acceleration), now)
                val visible = lensVisibleStretch(jelly.value, travel.velocity)
                maxVisible = maxOf(maxVisible, abs(visible))
                assertTrue("visible stretch escaped FlClash bounds: $visible", abs(visible) <= .32f)
                tick++
            }
        }
        travel.springTo(2f, now)
        repeat(2_000) {
            if (!travel.running && !jelly.running && abs(jelly.value) < .0001f) return@repeat
            now += frameNanos
            travel.tick(now)
            jelly.tick(now)
            jelly.springTo(lensJellyTarget(travel.velocity, travel.acceleration), now)
            maxVisible = maxOf(maxVisible, abs(lensVisibleStretch(jelly.value, travel.velocity)))
        }
        // Finish the final travel before the jelly settles to zero on its own.
        repeat(2_000) {
            if (!travel.running && !jelly.running) return@repeat
            now += frameNanos
            travel.tick(now)
            jelly.tick(now)
            jelly.springTo(lensJellyTarget(travel.velocity, travel.acceleration), now)
        }
        val settledVisible = lensVisibleStretch(jelly.value, travel.velocity)
        assertTrue("jelly did not settle: $settledVisible", abs(settledVisible) < .002f)
        assertTrue("travel did not settle", !travel.running)
        println("jelly maxVisibleStretch=$maxVisible settle=$settledVisible samples=$tick")
    }

    @Test
    fun jellyImpactReturnsWithOneRestrainedRebound() {
        val jelly = LensSpring(0f, LensMotion.jellyStiffness, LensMotion.jellyDamping)
        var now = 0L
        jelly.springTo(-0.15f, now)
        // Hold a brief braking impulse, then let the deformation spring back.
        repeat(10) {
            now += frameNanos
            jelly.tick(now)
        }
        val impactStretch = jelly.value
        jelly.springTo(0f, now)
        var positiveRebound = 0f
        var peakAbsStretch = abs(impactStretch)
        var settleFrames = 0
        while (jelly.running && settleFrames < 120) {
            now += frameNanos
            jelly.tick(now)
            positiveRebound = maxOf(positiveRebound, jelly.value)
            peakAbsStretch = maxOf(peakAbsStretch, abs(jelly.value))
            settleFrames++
        }
        assertTrue("braking impulse should remain visibly elastic: $impactStretch", impactStretch < -0.06f)
        assertTrue("return should include a small liquid rebound: $positiveRebound", positiveRebound > 0.001f)
        assertTrue("rebound must stay restrained: $peakAbsStretch", peakAbsStretch < 0.21f)
        assertTrue("jelly should settle promptly; frames=$settleFrames", settleFrames <= 72)
        assertEquals(0f, jelly.value, 0f)
    }

    @Test
    fun expansionDropsWhenTravelArrivesWithoutWaitingForJelly() {
        // FlClash _dropLift drops the lift once the lens has arrived
        // (lift >= 0.975 and |lens.target - lens.value| <= 0.04); the jelly
        // is a shape morph that settles on its own and is NOT part of the
        // gate. Its elastic return runs independently and must not hold the
        // shell floating after arrival, which the owner
        // reported as the lens taking too long to fall back down.
        val pitch = 304f / 3f
        val barWidth = 304f
        val barHeight = 64f
        val density = 1f
        val travel = LensSpring(0f, LensMotion.travelStiffness, LensMotion.travelDamping)
        val expansion = LensSpring(0f, LensMotion.expansionStiffness, 1f)
        val jelly = LensSpring(0f, LensMotion.jellyStiffness, LensMotion.jellyDamping)
        travel.springTo(2f, 0L)
        expansion.springTo(1f, 0L)
        var now = 0L
        var dropStarted = false
        var dropFrame = -1
        var travelArrivedFrame = -1
        var peakHeight = 0f
        var peakWidth = 0f
        var peakHeightBeforeDrop = 0f
        var peakWidthBeforeDrop = 0f
        var restWidth = 0f
        var restHeight = 0f
        for (step in 1..2_000) {
            now = step * frameNanos
            travel.tick(now)
            expansion.tick(now)
            jelly.tick(now)
            jelly.springTo(lensJellyTarget(travel.velocity, travel.acceleration), now)
            if (travelArrivedFrame < 0 && abs(travel.target - travel.value) <= 0.02f && abs(travel.velocity) <= 0.35f) {
                travelArrivedFrame = step
            }
            if (!dropStarted && lensReadyToContract(expansion, travel)) {
                expansion.springTo(0f, now)
                dropStarted = true
                dropFrame = step
            }
            val rect = insetLensRect(travel.value, lensVisibleStretch(jelly.value, travel.velocity),
                expansion.value, pitch, barWidth, barHeight, density)
            peakHeight = maxOf(peakHeight, rect.height)
            peakWidth = maxOf(peakWidth, rect.width)
            assertEquals("lens center must stay fixed vertically", barHeight / 2f, rect.center.y, .001f)
            if (!dropStarted) {
                peakHeightBeforeDrop = maxOf(peakHeightBeforeDrop, rect.height)
                peakWidthBeforeDrop = maxOf(peakWidthBeforeDrop, rect.width)
            } else {
                // The lens never re-expands above the peak it reached while
                // floating; the width relaxing back to rest as the jelly
                // decays is the jelly settling, not a second growth.
                assertTrue("width re-expanded above its float peak: ${rect.width} > $peakWidthBeforeDrop",
                    rect.width <= peakWidthBeforeDrop + .03f)
                assertTrue("height re-expanded above its float peak: ${rect.height} > $peakHeightBeforeDrop",
                    rect.height <= peakHeightBeforeDrop + .03f)
            }
            val settled = !expansion.running && !travel.running && !jelly.running
            if (dropStarted && settled) {
                restWidth = rect.width
                restHeight = rect.height
                break
            }
        }
        assertTrue("settled expansion never reached its production drop gate", dropStarted)
        assertTrue("drop must follow travel arrival promptly, not wait for the jelly: dropFrame=$dropFrame travelArrivedFrame=$travelArrivedFrame",
            dropFrame >= 0 && travelArrivedFrame >= 0 && dropFrame - travelArrivedFrame <= 5)
        assertTrue("drop must not wait ~2s for the jelly to decay: dropFrame=$dropFrame", dropFrame <= 90)
        assertTrue("expanded lens must cover bar height: peak=$peakHeight", peakHeight >= barHeight)
        assertTrue("expanded lens must cover its slot: peak=$peakWidth", peakWidth >= pitch)
        assertTrue("return did not finish", !expansion.running)
        assertTrue("lens must settle to rest width: $restWidth", abs(restWidth - (pitch - 2f * 6f)) < 1f)
        assertTrue("lens must settle to rest height: $restHeight", abs(restHeight - (barHeight - 2f * 6f)) < 1f)
        println("expansion peakHeight=$peakHeight peakWidth=$peakWidth dropFrame=$dropFrame travelArrivedFrame=$travelArrivedFrame restWidth=$restWidth restHeight=$restHeight")
    }

    @Test
    fun fastReversalRetainsInstantaneousVelocityAndPositionThenSettles() {
        val spring = LensSpring(0f, LensMotion.travelStiffness, LensMotion.travelDamping)
        spring.springTo(2f, 0L)
        var now = 0L
        repeat(8) {
            now += frameNanos
            spring.tick(now)
        }
        val beforePosition = spring.value
        val beforeVelocity = spring.velocity
        assertTrue("expected meaningful velocity before reversal", abs(beforeVelocity) > .01f)
        spring.springTo(-1f, now)
        assertEquals("retarget must not teleport position", beforePosition, spring.value, 0f)
        assertEquals("retarget must preserve instantaneous velocity", beforeVelocity, spring.velocity, 0f)
        var samples = 0
        while (spring.running && samples < 2_000) {
            now += frameNanos
            spring.tick(now)
            samples++
        }
        assertTrue("reversed spring failed to settle", !spring.running)
        assertEquals(-1f, spring.value, 0f)
        assertEquals(0f, spring.velocity, 0f)
        println("fastReversal position=$beforePosition velocity=$beforeVelocity settle=${spring.value} samples=$samples")
    }
}
