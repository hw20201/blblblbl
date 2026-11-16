package com.github.arca.android.ui.glass

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassWallpaperCacheTest {
    private val colors = GlassWallpaperSettings(colorA = 0xFF15263A.toInt(),
        colorB = 0xFFB68A62.toInt(), colorC = 0xFF7DB5C8.toInt())

    @Test fun staticWallpaperDoesNotNeedTheClockButFallbackMeshDoes() {
        assertFalse(backgroundUsesAnimatedTime(colors.copy(colorFlow = false)))
        assertFalse(backgroundUsesAnimatedTime(GlassWallpaperSettings()))
        assertTrue(backgroundUsesAnimatedTime(colors))
        assertTrue(backgroundUsesAnimatedTime(colors.copy(enabled = false, colorFlow = false)))
        assertTrue(backgroundUsesAnimatedTime(colors.copy(colorA = colors.colorB)))
    }

    @Test fun cachedGeometryIsReusedAndOnlyTheCurrentSizeIsRetained() {
        val cache = FlutedWallpaperDrawCache()
        val first = cache.getOrBuild(300f, 600f, 600f, 7f)
        repeat(240) { assertSame(first, cache.getOrBuild(300f, 600f, 600f, 7f)) }
        assertEquals(1, cache.geometryBuildCount)
        for (key in listOf(floatArrayOf(301f, 600f, 600f, 7f),
            floatArrayOf(300f, 601f, 600f, 7f), floatArrayOf(300f, 600f, 900f, 7f),
            floatArrayOf(300f, 600f, 600f, -7f))) {
            val changed = cache.getOrBuild(key[0], key[1], key[2], key[3])
            assertNotSame(first, changed)
            assertSame(changed, cache.getOrBuild(key[0], key[1], key[2], key[3]))
        }
        assertNotSame("returning to the old size rebuilds; this is not an unbounded size map",
            first, cache.getOrBuild(300f, 600f, 600f, 7f))
    }

    @Test fun cacheGeometryIsIndependentOfDynamicColorStopsAndInvalidatesAngle() {
        val cache = FlutedWallpaperDrawCache()
        val first = cache.getOrBuild(180f, 320f, 320f, 7f)
        val same = cache.getOrBuild(180f, 320f, 320f, 7f)
        assertSame("static geometry is reused across animated colour frames", first, same)
        val changed = cache.getOrBuild(180f, 320f, 320f, -7f)
        assertNotSame("rib angle changes must invalidate the geometry", first, changed)
        assertEquals(2, cache.geometryBuildCount)
        assertTrue("cached geometry includes the 29 visible ribs plus overscan", first.ribs.size >= FLUTED_RIB_COUNT)
    }
}
