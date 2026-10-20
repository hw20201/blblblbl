package com.github.mihomo.android.ui.glass

import androidx.test.ext.junit.runners.AndroidJUnit4
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
class ShortcutMeshOpticsCacheTest {
    @Test fun effectIsInitializedAfterUniformsAndCachedAcrossMeshFrames() {
        val optics = ShortcutMeshOptics()
        optics.update(174f, 118f, 1f, 18f, false)
        val first = optics.effect
        assertNotNull(first)
        repeat(60) { optics.update(174f, 118f, 1f, 18f, false) }
        assertSame("identical geometry must reuse the same effect, never rebuild per frame", first, optics.effect)
        optics.update(174f, 118f, 1f, 18f, true)
        val dark = optics.effect
        assertNotSame("theme changes must snapshot updated uniforms", first, dark)
        optics.update(348f, 236f, 2f, 36f, true)
        assertNotSame("density/size changes must refresh the geometry snapshot", dark, optics.effect)
    }
}
