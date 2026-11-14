package com.github.arca.android.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationGlassMaterialPolicyTest {
    /**
     * 2026-11-14 soft-edge fix: the selected capsule shares the dock tray's glass
     * material instead of carrying a second, stronger lens on top of it (the old
     * STRONG policy read as a separate piece of plastic with double refraction).
     * STANDARD keeps the local refraction/highlight enhancement; the selected state
     * is carried by the blue icon/label and the faint rim glow.
     */
    @Test fun bottomNavigationSelectionSharesTheDockTrayMaterial() {
        assertEquals(LensStrength.STANDARD, NAVIGATION_SELECTED_LENS_STRENGTH)
    }
}
