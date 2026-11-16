package com.github.arca.android.ui.screens

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import kotlin.math.abs
import kotlin.math.max

/**
 * Bitmap comparison helpers for the shortcut glass tests.
 *
 * v91 moved the shortcut glass onto the Glasmio backdrop. The backdrop is re-captured
 * whenever a card pose changes, and each re-capture shifts anti-aliased edges by a
 * sub-pixel. The most visible spot is the VPN switch thumb at the top of the Dashboard
 * (up to ~69 levels per channel); the striped background and the other glass cards
 * drift by up to ~21. Exact whole-frame pixel equality is therefore no longer a stable
 * assertion. These helpers keep the tests meaningful by splitting the frame:
 * - Inside the card, the interior is deterministic for an unchanged pose (observed drift
 *   1 level), but the card edge itself is an anti-aliased boundary against the backdrop
 *   and drifts by up to ~14 levels on a backdrop re-capture. A tolerance of 32 absorbs
 *   that edge noise while still catching a real card move: the smallest real press
 *   (GentleFloat, scale 0.997) shifts 52-75 card pixels by more than 32 levels.
 * - Outside the card, a loose tolerance absorbs the backdrop re-capture noise while
 *   still catching a real glass leak (frosted glass over the dark background shifts a
 *   pixel by ~140 levels).
 */
internal const val SHORTCUT_CARD_PIXEL_TOLERANCE = 32
internal const val SHORTCUT_OUTSIDE_PIXEL_TOLERANCE = 80
internal const val SHORTCUT_VISIBLE_CHANGE_MIN_PIXELS = 40

internal fun maxChannelDiff(a: Int, b: Int): Int {
    val dr = Color.red(a) - Color.red(b)
    val dg = Color.green(a) - Color.green(b)
    val db = Color.blue(a) - Color.blue(b)
    return max(abs(dr), max(abs(dg), abs(db)))
}

private fun clampedRegion(bitmap: Bitmap, card: Rect): IntArray {
    val l = card.left.toInt().coerceIn(0, bitmap.width)
    val t = card.top.toInt().coerceIn(0, bitmap.height)
    val r = card.right.toInt().coerceIn(0, bitmap.width)
    val b = card.bottom.toInt().coerceIn(0, bitmap.height)
    return intArrayOf(l, t, r, b)
}

/** Counts pixels inside [card] whose per-channel difference exceeds [tolerance]. */
internal fun countCardPixelsBeyond(baseline: Bitmap, image: Bitmap, card: Rect, tolerance: Int): Int {
    val region = clampedRegion(baseline, card)
    val l = region[0]; val t = region[1]; val r = region[2]; val b = region[3]
    var count = 0
    for (y in t until b) for (x in l until r) {
        if (maxChannelDiff(baseline.getPixel(x, y), image.getPixel(x, y)) > tolerance) count++
    }
    return count
}

/** The card itself must not move: every pixel inside [card] stays within a tight tolerance. */
internal fun assertCardRegionUnchanged(baseline: Bitmap, image: Bitmap, card: Rect, message: String) {
    val region = clampedRegion(baseline, card)
    val l = region[0]; val t = region[1]; val r = region[2]; val b = region[3]
    for (y in t until b) for (x in l until r) {
        val diff = maxChannelDiff(baseline.getPixel(x, y), image.getPixel(x, y))
        if (diff > SHORTCUT_CARD_PIXEL_TOLERANCE) {
            fail(message + ": card pixel changed at " + x + "," + y + " by " + diff + " (tolerance " + SHORTCUT_CARD_PIXEL_TOLERANCE + ")")
        }
    }
}

/** The card must visibly move: at least [SHORTCUT_VISIBLE_CHANGE_MIN_PIXELS] card pixels shift. */
internal fun assertCardRegionVisiblyChanged(baseline: Bitmap, image: Bitmap, card: Rect, message: String) {
    val changed = countCardPixelsBeyond(baseline, image, card, SHORTCUT_CARD_PIXEL_TOLERANCE)
    assertTrue(
        message + ": only " + changed + " card pixels changed (need " + SHORTCUT_VISIBLE_CHANGE_MIN_PIXELS + ")",
        changed >= SHORTCUT_VISIBLE_CHANGE_MIN_PIXELS
    )
}

/** Pixels outside [card] (expanded by [marginX]/[marginY]) must stay within a loose tolerance. */
internal fun assertOutsideRegionNearlyUnchanged(
    baseline: Bitmap,
    image: Bitmap,
    card: Rect,
    marginX: Float,
    marginY: Float,
    message: String
) {
    for (y in 0 until image.height) for (x in 0 until image.width) {
        val outsideX = x < card.left - marginX || x > card.right + marginX
        val outsideY = y < card.top - marginY || y > card.bottom + marginY
        if (outsideX || outsideY) {
            val diff = maxChannelDiff(baseline.getPixel(x, y), image.getPixel(x, y))
            if (diff > SHORTCUT_OUTSIDE_PIXEL_TOLERANCE) {
                fail(message + ": outside pixel changed at " + x + "," + y + " by " + diff + " (tolerance " + SHORTCUT_OUTSIDE_PIXEL_TOLERANCE + ")")
            }
        }
    }
}

/**
 * Whole-frame near-equality: the card itself must be unchanged (tight tolerance) and
 * everything outside it must stay within the loose backdrop-noise tolerance.
 * Replaces Bitmap.sameAs for frames that only differ by a backdrop re-capture.
 */
internal fun assertFramesNearlyEqual(baseline: Bitmap, image: Bitmap, card: Rect, message: String) {
    assertCardRegionUnchanged(baseline, image, card, message)
    assertOutsideRegionNearlyUnchanged(baseline, image, card, 0f, 0f, message)
}
