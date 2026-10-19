package com.github.mihomo.android.ui.glass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardBackdropPolicyTest {
    @Test fun api31HardwareUsesGaussianBackdropBlur() {
        assertTrue(shouldBlurCardBackdrop(31, true))
        assertTrue(shouldBlurCardBackdrop(35, true))
    }

    @Test fun olderApiAndSoftwareCanvasUseUnfilteredFallback() {
        assertFalse(shouldBlurCardBackdrop(30, true))
        assertFalse(shouldBlurCardBackdrop(31, false))
        assertFalse(shouldBlurCardBackdrop(26, false))
    }
}
