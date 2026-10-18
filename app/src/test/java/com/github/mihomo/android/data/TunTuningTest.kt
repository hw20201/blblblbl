package com.github.mihomo.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TunTuningTest {
    @Test
    fun mtuIsClampedToTheVpnServiceRange() {
        assertEquals(TunTuning.MIN_MTU, TunTuning.normalizeMtu(0))
        assertEquals(TunTuning.MIN_MTU, TunTuning.normalizeMtu(1279))
        assertEquals(1500, TunTuning.normalizeMtu(1500))
        assertEquals(TunTuning.MAX_MTU, TunTuning.normalizeMtu(65536))
    }

    @Test
    fun congestionControllerIsNormalizedToTheMipsSet() {
        assertEquals("cubic", TunTuning.normalizeCongestionController(null))
        assertEquals("cubic", TunTuning.normalizeCongestionController(""))
        assertEquals("bbr", TunTuning.normalizeCongestionController(" BBR "))
        assertEquals("cubic", TunTuning.normalizeCongestionController("vegas"))
    }
}
