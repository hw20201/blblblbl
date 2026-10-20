package com.github.mihomo.android.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShortcutTunedMaterialPolicyTest {
    @Test fun referenceLengthsAndDimensionlessSettingsResolveAsExpected() {
        assertEquals(118f / 190f, ShortcutTunedParameters.referenceScale, 0f)
        assertEquals(37.26316f, ShortcutTunedParameters.lengthDp(60f), .001f)
        val p = ShortcutTunedParameters.resolve(118f, 118f, 2f)
        assertEquals(37.26316f, p.radiusDp, .001f)
        assertEquals(33.53684f, p.thicknessDp, .001f)
        assertEquals(19.87368f, p.edgeWidthDp, .001f)
        assertEquals(.55f, p.edgeClarity, 0f)
        assertEquals(2.4f, p.squircle, 0f)
        assertEquals(3f, p.blurDp, 0f)
    }

    @Test fun radiusIsClampedAndShapeFunctionsStayFiniteAtDegenerateInputs() {
        val tiny = ShortcutTunedParameters.resolve(30f, 22f, 0f)
        assertEquals(11f, tiny.radiusDp, 0f)
        assertTrue(shortcutTunedSdfDp(0f, 0f, 0f, 0f, Float.NaN, Float.NaN).isFinite())
        val n = shortcutTunedNormalDp(0f, 0f, 100f, 80f, 20f, 2.4f)
        assertTrue(n.first.isFinite() && n.second.isFinite())
    }

    @Test fun contourAndNormalsStayRoundedAcrossDensities() {
        for (density in listOf(1f, 1.25f, 1.5f, 2f, 3f, 3.5f)) {
            val p = ShortcutTunedParameters.resolve(174f, 118f, density)
            assertEquals(p.radiusDp, p.radiusPx / density, .0001f)
            assertEquals(14f, p.dispersion, 0f)
            for (i in 0..32) {
                val angle = PI * .5 * i / 32
                val x = 174f - p.radiusDp + p.radiusDp * cos(angle).toFloat().pow(2f / p.squircle)
                val y = p.radiusDp - p.radiusDp * sin(angle).toFloat().pow(2f / p.squircle)
                val sdf = shortcutTunedSdfDp(x, y, 174f, 118f, p.radiusDp, p.squircle)
                assertTrue("clip and shader contour agree at density=$density: $sdf", abs(sdf) < .002f)
                val n = shortcutTunedNormalDp(x, y, 174f, 118f, p.radiusDp, p.squircle)
                assertTrue(n.first.isFinite() && n.second.isFinite())
                assertTrue("top-right outward normal", n.first >= -.001f && n.second <= .001f)
            }
        }
    }

    @Test fun effectCacheIsIdleStableButHeldTouchChangesTheNativeSnapshot() {
        val optics = ShortcutTunedOptics()
        val first = optics.update(348f, 236f, 2f, false, ShortcutRefraction(1f, -.8f, .5f))
        assertSame(first, optics.update(348f, 236f, 2f, false, ShortcutRefraction(1f, -.8f, .5f)))
        assertNotSame(first, optics.update(348f, 236f, 2f, false, ShortcutRefraction(1f, .8f, .5f)))
        assertTrue(optics.update(0f, Float.NaN, 0f, true, ShortcutRefraction(Float.NaN, Float.NaN, Float.NaN)) != null)
    }

    @Test fun runtimeShaderAndNativeEffectCompileOnApi35() {
        if (Build.VERSION.SDK_INT < 33) return
        val shader = RuntimeShader(SHORTCUT_TUNED_SHADER)
        shader.setFloatUniform("viewport", 236f, 236f)
        shader.setFloatUniform("density", 2f)
        shader.setFloatUniform("radius", 37.26f)
        shader.setFloatUniform("exponent", 2.4f)
        shader.setFloatUniform("thickness", 33.54f)
        shader.setFloatUniform("edgeWidth", 19.87f)
        shader.setFloatUniform("blurRadius", 3f)
        shader.setFloatUniform("edgeClarity", .55f)
        shader.setFloatUniform("ior", 2f)
        shader.setFloatUniform("distortion", .1f)
        shader.setFloatUniform("dispersion", 14f)
        shader.setFloatUniform("fresnel", .22f)
        shader.setFloatUniform("edgeStrength", .8f)
        shader.setFloatUniform("saturation", 1.04f)
        shader.setFloatUniform("tint", .04f)
        shader.setFloatUniform("grain", .052f)
        shader.setFloatUniform("dark", 0f)
        shader.setFloatUniform("lift", 0f)
        val effect = RenderEffect.createRuntimeShaderEffect(shader, "backdrop")
        assertTrue(effect is RenderEffect)
    }
}
