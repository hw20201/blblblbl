package com.github.mihomo.android.data

import android.content.Context
import com.github.mihomo.android.ui.glass.GlassMotionTuning
import com.github.mihomo.android.ui.glass.GlassOptics
import com.github.mihomo.android.ui.glass.GlassSettings
import com.github.mihomo.android.ui.glass.GlassSurfaceCategory
import com.github.mihomo.android.ui.glass.ShortcutGlassLevel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsManagerShortcutGlassLevelTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun prefs() = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)

    @Test fun typedGlassSettingsUseOnePersistedPreferenceAndPreserveDefaults() {
        prefs().edit().clear().commit()
        val settings = SettingsManager(context)
        assertEquals(GlassSettings.DEFAULT, settings.glassSettings)
        assertFalse(prefs().contains("glass_settings_v1"))
        val custom = GlassSettings.DEFAULT.copy(
            globalOptics = GlassOptics(14f, 6.5f, 13f, 1.4f),
            surfaceOverrides = mapOf(GlassSurfaceCategory.TITLE_CAPSULES to GlassOptics(8f, 2f, 10f, 1f)),
            motion = GlassMotionTuning(1.2f, 10f, .7f),
            shortcutGlassLevel = ShortcutGlassLevel.Extreme
        )
        settings.glassSettings = custom
        assertEquals(custom, SettingsManager(context).glassSettings)
        assertTrue(prefs().contains("glass_settings_v1"))
        assertFalse(prefs().contains("shortcut_glass_refraction_dp"))
        assertFalse(prefs().contains("shortcut_glass_dispersion_dp"))
    }

    @Test fun oldShortcutKnobsMigrateIntoSurfaceOverrideAndOldKeysAreClearedAfterSave() {
        prefs().edit().clear()
            .putString("shortcut_glass_level", "Obvious")
            .putFloat("shortcut_glass_refraction_dp", 15f)
            .putFloat("shortcut_glass_dispersion_dp", 7f)
            .putFloat("shortcut_glass_edge_width_dp", 16f)
            .putFloat("shortcut_glass_edge_light", 1.6f)
            .commit()
        val settings = SettingsManager(context)
        val migrated = settings.glassSettings
        assertEquals(ShortcutGlassLevel.Obvious, migrated.shortcutGlassLevel)
        assertEquals(GlassOptics(15f, 7f, 16f, 1.6f), migrated.opticsFor(GlassSurfaceCategory.SHORTCUT_CARDS))
        assertEquals(GlassOptics.DEFAULT, migrated.opticsFor(GlassSurfaceCategory.TITLE_CAPSULES))
        settings.glassSettings = migrated
        assertTrue(prefs().contains("glass_settings_v1"))
        assertFalse(prefs().contains("shortcut_glass_level"))
        assertFalse(prefs().contains("shortcut_glass_refraction_dp"))
    }

    @Test fun configurationClampsUnsafeValuesAndResetRestoresEntireTypedDefault() {
        prefs().edit().clear().commit()
        val settings = SettingsManager(context)
        settings.glassSettings = GlassSettings(
            globalOptics = GlassOptics(99f, -1f, Float.NaN, Float.POSITIVE_INFINITY),
            surfaceOverrides = mapOf(GlassSurfaceCategory.BOTTOM_NAVIGATION to GlassOptics(-20f, 90f, 0f, 5f)),
            motion = GlassMotionTuning(90f, 200f, -4f)
        )
        val safe = SettingsManager(context).glassSettings
        assertEquals(18f, safe.globalOptics.refractionDp, 0f)
        assertEquals(0f, safe.globalOptics.dispersionDp, 0f)
        assertEquals(GlassOptics.DEFAULT_EDGE_WIDTH_DP, safe.globalOptics.edgeWidthDp, 0f)
        assertEquals(GlassOptics.DEFAULT_EDGE_LIGHT_MULTIPLIER, safe.globalOptics.edgeLightMultiplier, 0f)
        assertEquals(GlassOptics.MIN_REFRACTION_DP, safe.opticsFor(GlassSurfaceCategory.BOTTOM_NAVIGATION).refractionDp, 0f)
        assertEquals(GlassMotionTuning.MAX_TILT_GAIN, safe.motion.tiltGain, 0f)
        assertEquals(GlassMotionTuning.MAX_MAX_TILT_DEGREES, safe.motion.maxTiltDegrees, 0f)
        settings.resetGlassSettings()
        assertEquals(GlassSettings.DEFAULT, SettingsManager(context).glassSettings)
    }
}
