package com.github.mihomo.android.ui.glass

import android.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Allocation contracts, not a frame-rate benchmark or a vivo GPU test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassScrollAllocationTest {
    private val base = NavigationLensEffectKey(180f, 64f, 1f, 1f,
        LensStrength.STANDARD, 32f, false)

    @Test fun stationaryOpticsReuseOneNativeEffectAcross240SceneFrames() {
        val cache = NavigationLensEffectCache()
        var builds = 0
        val create = { builds++; RenderEffect.createOffsetEffect(0f, 0f).asComposeRenderEffect() }
        val first = cache.getOrCreate(base, create)
        repeat(240) {
            // New but equal keys model repeated layer updates while the scene scrolls.
            assertSame(first, cache.getOrCreate(base.copy(), create))
        }
        assertEquals("Scene updates must not rebuild the stationary native effect graph", 1, builds)
    }

    @Test fun everyGeometryAndOpticsChangeRefreshesItsUniformSnapshot() {
        val variants = listOf(base.copy(width = 181f), base.copy(height = 65f),
            base.copy(density = 2f), base.copy(opticalScale = 1.2f),
            base.copy(strength = LensStrength.STRONG), base.copy(cornerRadius = 24f),
            base.copy(frosted = true))
        for (variant in variants) {
            val cache = NavigationLensEffectCache()
            val create = { RenderEffect.createOffsetEffect(0f, 0f).asComposeRenderEffect() }
            val before = cache.getOrCreate(base, create)
            val changed = cache.getOrCreate(variant, create)
            assertNotSame(before, changed)
            assertSame(changed, cache.getOrCreate(variant.copy(), create))
            assertNotSame(changed, cache.getOrCreate(base, create))
        }
    }

    @Test fun reusableReplayMatrixTracksNewCornersRatherThanFreezingThePose() {
        val scratch = SceneReplayScratch()
        val local = scratch.local
        val scene = scratch.scene
        val matrix = scratch.matrix
        floatArrayOf(0f, 0f, 180f, 0f, 180f, 100f, 0f, 100f).copyInto(local)
        repeat(240) { frame ->
            val x = frame * .5f
            val y = frame * .3f
            // Changing trapezoid exercises perspective, not just an idle translation.
            floatArrayOf(x, y, 180f + x, y + 4f, 176f + x, 100f + y, x + 3f, 96f + y).copyInto(scene)
            assertTrue(matrix.setPolyToPoly(scene, 0, local, 0, 4))
            val mapped = scene.copyOf()
            matrix.mapPoints(mapped)
            for (i in mapped.indices) assertEquals(local[i], mapped[i], .002f)
            assertSame(local, scratch.local)
            assertSame(scene, scratch.scene)
            assertSame(matrix, scratch.matrix)
        }
        // Each card has independent storage; neighbouring cards cannot share a mutable pose.
        assertNotSame(scratch.matrix, SceneReplayScratch().matrix)
    }
}
