package com.github.mihomo.android.ui.glass

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutContactFrostPolicyTest {
    @Test fun radiusIsFiniteMonotonicBoundedAndClearAtContact() {
        assertEquals(0f, shortcutFrostRadiusDp(-1f), 0f)
        assertEquals(0f, shortcutFrostRadiusDp(0f), 0f)
        assertEquals(0f, shortcutFrostRadiusDp(Float.NaN), 0f)
        assertEquals(0f, shortcutFrostRadiusDp(Float.POSITIVE_INFINITY), 0f)
        assertEquals(8f, shortcutFrostRadiusDp(1f), 0f)
        assertEquals(8f, shortcutFrostRadiusDp(10f), 0f)
        val radii = (0..100).map { shortcutFrostRadiusDp(it / 100f) }
        assertTrue(radii.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("at most 17 half-dp levels", radii.distinct().size <= 17)
    }
    @Test fun noIdleFilterAndFiniteMotionEffectsAreReused() {
        val frost = ShortcutContactFrost()
        assertNull(frost.update(0f, 1f, null))
        val raised = frost.update(1f, 1f, null)
        assertNotNull(raised)
        repeat(120) { assertSame(raised, frost.update(1f, 1f, null)) }
        assertNotSame(raised, frost.update(.5f, 1f, null))
        assertNull("release removes Gaussian filter", frost.update(0f, 1f, null))
    }
    @Test fun effectPreservesOpticalSnapshotsAndDensityChanges() {
        val optics = ShortcutMeshOptics()
        optics.update(174f, 118f, 1f, 18f, false)
        val frost = ShortcutContactFrost()
        val idle = frost.update(0f, 1f, optics.nativeEffect)
        assertNotNull(idle)
        assertSame(idle, frost.update(0f, 1f, optics.nativeEffect))
        val lifted = frost.update(1f, 1f, optics.nativeEffect)
        assertNotSame(idle, lifted)
        assertSame(lifted, frost.update(1f, 1f, optics.nativeEffect))
        assertNotSame(lifted, frost.update(1f, 2f, optics.nativeEffect))
        optics.update(174f, 118f, 1f, 18f, true)
        assertNotSame(idle, frost.update(0f, 1f, optics.nativeEffect))
    }
}
