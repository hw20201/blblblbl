package com.github.mihomo.android.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftMeshClockTest {
    @Test fun requiresForegroundFocusVisibilityAndSystemMotionPermission() {
        assertTrue(meshMayAnimate(true, true, true, true))
        assertFalse(meshMayAnimate(false, true, true, true))
        assertFalse(meshMayAnimate(true, false, true, true))
        assertFalse(meshMayAnimate(true, true, false, true))
        assertFalse(meshMayAnimate(true, true, true, false))
    }

    @Test fun elapsedTimeFreezesAcrossBackgroundAndResumesWithoutJump() {
        val clock = MeshActiveTime()
        clock.frame(1_000_000_000L)
        assertEquals(.05f, clock.frame(1_050_000_000L), .0001f)
        clock.pause()
        assertEquals(.05f, clock.seconds, .0001f)
        assertEquals(.05f, clock.frame(90_000_000_000L), .0001f)
        assertEquals(.10f, clock.frame(90_050_000_000L), .0001f)
    }

    @Test fun stalledFramesCannotCauseLargeVisualJumpsOrReverseTime() {
        val clock = MeshActiveTime()
        clock.frame(1_000_000_000L)
        assertEquals(.1f, clock.frame(30_000_000_000L), .0001f)
        assertEquals(.1f, clock.frame(29_000_000_000L), .0001f)
    }

    @Test fun maximumWakeupCadenceIsRestrained() {
        assertTrue("at most 24 updates/s before display-frame alignment", 1000.0 / MESH_UPDATE_DELAY_MS <= 24.0)
        println("Clock: ${MESH_UPDATE_DELAY_MS}ms delay + one display-frame alignment; <=${1000.0 / MESH_UPDATE_DELAY_MS} Hz, zero loop while inactive")
    }
}
