package com.github.mihomo.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class ShortcutSuspensionPolicyTest {
    @Test fun centerIsNeutralAndInteriorFollowsMoreClearly() {
        val center = shortcutSuspendedTilt(0f, 0f)
        val interior = shortcutSuspendedTilt(.5f, -.5f)
        assertEquals(0f, center.rotationX, 0f)
        assertEquals(0f, center.rotationY, 0f)
        assertTrue("interior pitch is stronger than the old half-range response", abs(interior.rotationX) > 3f)
        assertTrue("interior roll is stronger than the old half-range response", abs(interior.rotationY) > 3f)
    }

    @Test fun oppositeEdgesHaveOppositeTiltAndCornersStayBounded() {
        val top = shortcutSuspendedTilt(0f, -1f)
        val bottom = shortcutSuspendedTilt(0f, 1f)
        val left = shortcutSuspendedTilt(-1f, 0f)
        val right = shortcutSuspendedTilt(1f, 0f)
        assertTrue(top.rotationX * bottom.rotationX < 0f)
        assertTrue(left.rotationY * right.rotationY < 0f)
        for (x in -10..10) for (y in -10..10) {
            val pose = shortcutSuspendedTilt(x / 10f, y / 10f)
            assertTrue(abs(pose.rotationX) <= SHORTCUT_SUSPENDED_MAX_TILT + .0001f)
            assertTrue(abs(pose.rotationY) <= SHORTCUT_SUSPENDED_MAX_TILT + .0001f)
            assertTrue(hypot(pose.rotationX, pose.rotationY) <= SHORTCUT_SUSPENDED_MAX_TILT + .0001f)
        }
    }

    @Test fun invalidPointerValuesReturnNeutralPose() {
        val pose = shortcutSuspendedTilt(Float.NaN, Float.POSITIVE_INFINITY)
        assertEquals(0f, pose.rotationX, 0f)
        assertEquals(0f, pose.rotationY, 0f)
    }

    @Test fun suspensionHeightIsSmallShapedAndMonotonic() {
        assertEquals(0f, shortcutSuspensionElevationDp(false, false, 1f), 0f)
        assertEquals(SHORTCUT_REST_ELEVATION_DP, shortcutSuspensionElevationDp(true, false, 0f), 0f)
        assertEquals(SHORTCUT_PRESS_ELEVATION_DP, shortcutSuspensionElevationDp(true, false, 1f), 0f)
        assertEquals(SHORTCUT_REORDER_ELEVATION_DP, shortcutSuspensionElevationDp(true, true, 0f), 0f)
        assertTrue(shortcutSuspensionElevationDp(true, false, .5f) in
            SHORTCUT_REST_ELEVATION_DP..SHORTCUT_PRESS_ELEVATION_DP)
        assertEquals(SHORTCUT_REST_ELEVATION_DP, shortcutSuspensionElevationDp(true, false, Float.NaN), 0f)
    }

    @Test fun cameraDistanceHasAStableLowerBound() {
        assertEquals(160f, shortcutSuspendedCameraDp(0f), 0f)
        assertEquals(160f, shortcutSuspendedCameraDp(190f), 0f)
        assertEquals(252f, shortcutSuspendedCameraDp(300f), .0001f)
        assertEquals(160f, shortcutSuspendedCameraDp(Float.NaN), 0f)
    }
}
