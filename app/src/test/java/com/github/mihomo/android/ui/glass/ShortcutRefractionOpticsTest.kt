package com.github.mihomo.android.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutRefractionOpticsTest {
    @Test fun defaultsAreIdentityRefraction() {
        assertEquals(ShortcutRefraction(), ShortcutRefraction(0f, 0f, 0f))
    }

    @Test fun shaderKeepsTouchRefractionInsideBevelAndLiftZeroBaseline() {
        // The original hybrid bevel math remains the inner mix's lift-zero value.
        assertTrue(SHORTCUT_GLASS_SHADER.contains("mix(mix(1.0, 7.2 / 4.6, hybrid), 1.82, lift)"))
        assertTrue(SHORTCUT_GLASS_SHADER.contains("if (edge > 0.001)"))
        assertTrue(SHORTCUT_GLASS_SHADER.contains("if (hybrid > 0.5 || lift > 0.0)"))
        assertTrue(SHORTCUT_GLASS_SHADER.contains("* lift * density * touchWeight"))
        assertTrue(SHORTCUT_GLASS_SHADER.contains("uniform float2 touch"))
        assertTrue("touch response must not drop to zero for mid-card taps",
            SHORTCUT_GLASS_SHADER.contains("1.0 - 0.38 * smoothstep(0.0, 1.35, touchDistance)"))
    }
}
