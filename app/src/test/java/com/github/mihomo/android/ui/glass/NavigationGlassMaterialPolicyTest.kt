package com.github.mihomo.android.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationGlassMaterialPolicyTest {
    @Test fun bottomNavigationSelectionKeepsTheOriginalLiquidLensMaterial() {
        assertEquals(LensStrength.STRONG, NAVIGATION_SELECTED_LENS_STRENGTH)
    }
}
