package com.github.mihomo.android.ui.glass

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Numerical regressions for the lens motion; no Compose runtime or frame clock is required. */
class LensMotionSmoothnessTest {
    private val frameNanos = 16_000_000L

    @Test
    fun ordinaryTravelIsMonotonicAndDoesNotOvershoot() {
        val spring = LensSpring(0f, LensMotion.travelStiffness, LensMotion.travelDamping)
        spring.springTo(2f, 0L)
        var previous = spring.value
        var samples = 0
        while (spring.running && samples < 2_000) {
            spring.tick((samples + 1L) * frameNanos)
            assertTrue("travel moved backwards at sample $samples: ${spring.value} < $previous", spring.value >= previous)
            assertTrue("travel overshot destination", spring.value <= 2f)
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
                assertTrue("visible stretch escaped reasonable bounds: $visible", abs(visible) <= .12f)
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
        // Finish the final travel before allowing the velocity-gated visible stretch to vanish.
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
    fun expansionWaitsForSettledRiseAndReturnsWithoutSecondGrowth() {
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
        var peakHeight = 0f
        var peakWidth = 0f
        var previousHeightAfterDrop = Float.POSITIVE_INFINITY
        var previousWidthAfterDrop = Float.POSITIVE_INFINITY
        var framesAfterDrop = 0
        for (step in 1..2_000) {
            now = step * frameNanos
            travel.tick(now)
            expansion.tick(now)
            jelly.tick(now)
            jelly.springTo(lensJellyTarget(travel.velocity, travel.acceleration), now)
            if (!dropStarted && lensReadyToContract(expansion, travel)) {
                expansion.springTo(0f, now)
                dropStarted = true
            }
            val rect = insetLensRect(travel.value, lensVisibleStretch(jelly.value, travel.velocity),
                expansion.value, pitch, barWidth, barHeight, density)
            peakHeight = maxOf(peakHeight, rect.height)
            peakWidth = maxOf(peakWidth, rect.width)
            assertEquals("lens center must stay fixed vertically", barHeight / 2f, rect.center.y, .001f)
            if (dropStarted) {
                assertTrue("height grew again during return", rect.height <= previousHeightAfterDrop + .03f)
                assertTrue("width grew again during return", rect.width <= previousWidthAfterDrop + .03f)
                previousHeightAfterDrop = rect.height
                previousWidthAfterDrop = rect.width
                framesAfterDrop++
            }
            val settled = !expansion.running && !travel.running && !jelly.running
            if (dropStarted && settled) break
        }
        assertTrue("settled expansion never reached its production drop gate", dropStarted)
        assertTrue("expanded lens must cover bar height: peak=$peakHeight", peakHeight >= barHeight)
        assertTrue("expanded lens must cover its slot: peak=$peakWidth", peakWidth >= pitch)
        assertTrue("return did not finish", !expansion.running)
        println("expansion peakHeight=$peakHeight peakWidth=$peakWidth dropFrames=$framesAfterDrop")
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
