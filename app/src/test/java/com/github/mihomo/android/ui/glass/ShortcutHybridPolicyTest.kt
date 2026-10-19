package com.github.mihomo.android.ui.glass

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutHybridPolicyTest {
    @Test fun qualityThresholds() {
        for (api in listOf(26, 30)) assertEquals(ShortcutGlassQuality.Flat, shortcutGlassQuality(api, false))
        for (api in listOf(31, 32)) assertEquals(ShortcutGlassQuality.Shared, shortcutGlassQuality(api, false))
        for (api in listOf(33, 35)) assertEquals(ShortcutGlassQuality.Refracted, shortcutGlassQuality(api, false))
        for (api in listOf(26, 31, 35)) assertEquals(ShortcutGlassQuality.Flat, shortcutGlassQuality(api, true))
    }

    @Test fun effectSnapshotIsCachedUntilGeometryThemeOrDesignChanges() {
        val optics = ShortcutMeshOptics()
        optics.update(174f, 118f, 1f, 18f, false, true)
        val first = optics.effect
        assertNotNull(first)
        repeat(60) { optics.update(174f, 118f, 1f, 18f, false, true) }
        assertSame("identical hybrid parameters reuse effect across mesh frames", first, optics.effect)
        optics.update(174f, 118f, 1f, 18f, true, true)
        val dark = optics.effect
        assertNotSame("theme change refreshes effect snapshot", first, dark)
        optics.update(174f, 118f, 1f, 18f, true, false)
        val baseline = optics.effect
        assertNotSame("design change refreshes effect snapshot", dark, baseline)
        optics.update(175f, 118f, 1f, 18f, true, false)
        assertNotSame("geometry change refreshes effect snapshot", baseline, optics.effect)
    }
}
