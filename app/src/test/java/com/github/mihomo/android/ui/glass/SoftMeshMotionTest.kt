package com.github.mihomo.android.ui.glass

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftMeshMotionTest {
    @Test
    fun sameSeedIndexAndTimeAreDeterministic() {
        val a = SoftMeshMotion(42L)
        val b = SoftMeshMotion(42L)
        for (i in 0..20) assertEquals(a.sample(i % 4, i * 17.25f), b.sample(i % 4, i * 17.25f))
    }

    @Test
    fun posesStayFiniteAndWithinVisualBounds() {
        val motion = SoftMeshMotion(Long.MIN_VALUE)
        for (blob in 0..15) for (step in 0..900) {
            val pose = motion.sample(blob, step * .25f)
            assertTrue(pose.x.isFinite() && pose.x in .02f.. .98f)
            assertTrue(pose.y.isFinite() && pose.y in .02f.. .98f)
            assertTrue(pose.scaleX.isFinite() && pose.scaleX in .85f..1.20f)
            assertTrue(pose.scaleY.isFinite() && pose.scaleY in .85f..1.20f)
            assertTrue(pose.rotation.isFinite() && pose.rotation in -22f..22f)
        }
    }

    @Test
    fun stratifiedTargetsExploreAllQuadrants() {
        val motion = SoftMeshMotion(781L)
        val quadrants = mutableSetOf<Int>()
        // One visit per target slot, at each segment boundary.
        for (segment in 0..15) {
            val p = motion.sample(3, segment * 33f)
            quadrants += (if (p.x >= .5f) 1 else 0) + (if (p.y >= .5f) 2 else 0)
        }
        assertEquals(setOf(0, 1, 2, 3), quadrants)
    }

    @Test
    fun seedsAndBlobIndicesProduceDistinctTrajectories() {
        val a = SoftMeshMotion(100L)
        val b = SoftMeshMotion(101L)
        assertNotEquals(a.sample(0, 91f), b.sample(0, 91f))
        assertNotEquals(a.sample(0, 91f), a.sample(1, 91f))
    }

    @Test
    fun adjacentFramesAndSegmentJoinsAreContinuous() {
        val motion = SoftMeshMotion(3L)
        for (t in 0..2_000) {
            val before = motion.sample(2, t * .016f)
            val after = motion.sample(2, t * .016f + .016f)
            assertTrue(abs(after.x - before.x) < .003f)
            assertTrue(abs(after.y - before.y) < .003f)
            assertTrue(abs(after.rotation - before.rotation) < .2f)
        }
        val left = motion.sample(2, 32.999f)
        val join = motion.sample(2, 33f)
        val right = motion.sample(2, 33.001f)
        assertTrue(abs(left.x - join.x) < .0001f && abs(right.x - join.x) < .0001f)
        assertTrue(abs(left.y - join.y) < .0001f && abs(right.y - join.y) < .0001f)
    }

    @Test
    fun zeroNegativeAndNonFiniteTimesAreHandledAsZero() {
        val motion = SoftMeshMotion(9L)
        val zero = motion.sample(0, 0f)
        assertEquals(zero, motion.sample(0, -1f))
        assertEquals(zero, motion.sample(0, Float.NaN))
        assertEquals(zero, motion.sample(0, Float.NEGATIVE_INFINITY))
    }

    /** HOST-ONLY microbenchmark: informational JVM timing; not an Android/device performance claim. */
    @Test
    fun hostOnlySamplingBenchmark() {
        val motion = SoftMeshMotion(1234L)
        var checksum = 0f
        val start = System.nanoTime()
        repeat(500_000) { i ->
            val p = motion.sample(i and 7, (i % 100_000) * .001f)
            checksum += p.x + p.y + p.scaleX + p.scaleY + p.rotation
        }
        val elapsed = System.nanoTime() - start
        println("HOST-ONLY SoftMeshMotion: 500000 samples in ${elapsed / 1_000_000.0} ms; checksum=$checksum")
        assertTrue(checksum.isFinite())
    }
}
