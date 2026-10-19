package com.github.mihomo.android.ui.glass

import kotlin.math.floor

/** A normalized center, aspect scale and rotation for one softly moving color blob. */
internal data class MeshPose(
    val x: Float,
    val y: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float,
)

/**
 * Stateless, deterministic motion for a collection of independently-seeded soft color blobs.
 * Times below zero are treated as zero. Each blob follows a sequence of stratified targets,
 * changing targets every 33 seconds (inside the requested 24–42 second broad-motion range); quintic smootherstep eases each segment with zero
 * velocity at both ends. This avoids abrupt frame-to-frame changes without a short looping orbit.
 *
 * Sampling uses only integer hashing and scalar math: no Random, collections, or per-sample
 * object allocation (apart from the returned MeshPose value).
 */
internal class SoftMeshMotion(seed: Long) {
    private val seedBits = mix64(seed)

    fun sample(index: Int, seconds: Float): MeshPose {
        val safeIndex = index.toLong()
        val visibleTime = if (seconds.isFinite() && seconds > 0f) seconds.toDouble() else 0.0
        // Independent phases avoid a stationary launch and synchronized stops at target joins.
        val phase = 0.2 + unitFloat(mix64(seedBits xor (safeIndex * INDEX_SALT))) * 0.6
        val t = visibleTime + phase * SEGMENT_SECONDS
        val segment = floor(t / SEGMENT_SECONDS).toLong()
        val fraction = (t / SEGMENT_SECONDS - segment).toFloat()
        val eased = fraction * fraction * fraction * (fraction * (fraction * 6f - 15f) + 10f)

        val x = interpolate(target(safeIndex, segment, X), target(safeIndex, segment + 1L, X), eased)
        val y = interpolate(target(safeIndex, segment, Y), target(safeIndex, segment + 1L, Y), eased)
        val sx = interpolate(target(safeIndex, segment, SCALE_X), target(safeIndex, segment + 1L, SCALE_X), eased)
        val sy = interpolate(target(safeIndex, segment, SCALE_Y), target(safeIndex, segment + 1L, SCALE_Y), eased)
        val rotation = interpolate(target(safeIndex, segment, ROTATION), target(safeIndex, segment + 1L, ROTATION), eased)
        return MeshPose(x, y, sx, sy, rotation)
    }

    private fun target(index: Long, segment: Long, channel: Int): Float {
        val key = mix64(seedBits xor (index * INDEX_SALT) xor (segment * SEGMENT_SALT) xor channel.toLong() * CHANNEL_SALT)
        if (channel == X || channel == Y) {
            // A seed/index-specific affine permutation of 4x4 cells. Every block of 16
            // consecutive targets visits all cells; odd multipliers permute modulo 16.
            val block = segment / 16L
            val slot = (segment % 16L).toInt()
            val permKey = mix64(seedBits xor (index * INDEX_SALT) xor (block * BLOCK_SALT))
            val multiplier = ((permKey ushr 1).toInt() and 7) * 2 + 1
            val offset = (permKey ushr 9).toInt() and 15
            val cell = (slot * multiplier + offset) and 15
            val coordinate = if (channel == X) cell and 3 else cell ushr 2
            val jitter = unitFloat(key)
            return 0.02f + (coordinate + 0.08f + jitter * 0.84f) * (0.96f / 4f)
        }
        val unit = unitFloat(key)
        return when (channel) {
            SCALE_X, SCALE_Y -> 0.85f + unit * 0.35f
            else -> -22f + unit * 44f
        }
    }

    private fun interpolate(a: Float, b: Float, fraction: Float): Float = a + (b - a) * fraction

    private fun unitFloat(bits: Long): Float = ((bits ushr 40) and 0xFFFFFFL).toFloat() / 16_777_216f

    private companion object {
        const val SEGMENT_SECONDS = 33.0
        const val X = 0
        const val Y = 1
        const val SCALE_X = 2
        const val SCALE_Y = 3
        const val ROTATION = 4
        const val INDEX_SALT = -7046029254386353131L
        const val SEGMENT_SALT = -4658895280553007687L
        const val CHANNEL_SALT = -7723592293110705685L
        const val BLOCK_SALT = 6364136223846793005L

        fun mix64(input: Long): Long {
            var z = input
            z = (z xor (z ushr 30)) * -4658895280553007687L
            z = (z xor (z ushr 27)) * -7723592293110705685L
            return z xor (z ushr 31)
        }
    }
}
