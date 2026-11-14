package com.github.arca.android.ui.glass

import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import org.junit.Assert.assertEquals
import org.junit.Test

class GlasmioMaterialDispatchTest {
    @Test
    fun largePanelsUseGlasmioMediumWhileBaseDockKeepsCompactMaterial() {
        assertEquals(NavigationGlassMaterial.RegularMedium, glasmioMaterialFor(GlassSurfaceCategory.LARGE_PANELS))
        assertEquals(NavigationGlassMaterial.RegularSmall, glasmioMaterialFor(GlassSurfaceCategory.BOTTOM_NAVIGATION))
        assertEquals(NavigationGlassMaterial.RegularSmall, glasmioMaterialFor(GlassSurfaceCategory.SHORTCUT_CARDS))
    }
}
