package com.github.arca.android.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsManagerPanelAnimationSpeedTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun defaultsToMediumWithoutWritingPreference() {
        val prefs = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        assertEquals(PanelAnimationSpeed.MEDIUM, SettingsManager(context).panelAnimationSpeed)
        assertFalse(prefs.contains("panel_animation_speed"))
    }

    @Test
    fun everySpeedPersistsAcrossSettingsManagerInstances() {
        val prefs = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val settings = SettingsManager(context)

        PanelAnimationSpeed.entries.forEach { speed ->
            settings.panelAnimationSpeed = speed
            assertEquals(speed.name, prefs.getString("panel_animation_speed", null))
            assertEquals(speed, SettingsManager(context).panelAnimationSpeed)
        }
    }

    @Test
    fun unknownStoredSpeedFallsBackWithoutOverwritingThePreference() {
        val prefs = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("panel_animation_speed", "removed-future-speed").commit()

        assertEquals(PanelAnimationSpeed.MEDIUM, SettingsManager(context).panelAnimationSpeed)
        assertEquals("removed-future-speed", prefs.getString("panel_animation_speed", null))
    }

    @Test
    fun preferenceDecoderHandlesKnownUnknownAndMissingValues() {
        PanelAnimationSpeed.entries.forEach { speed ->
            assertEquals(speed, PanelAnimationSpeed.fromPreference(speed.name))
        }
        assertEquals(PanelAnimationSpeed.MEDIUM, PanelAnimationSpeed.fromPreference(null))
        assertEquals(PanelAnimationSpeed.MEDIUM, PanelAnimationSpeed.fromPreference(""))
        assertEquals(PanelAnimationSpeed.MEDIUM, PanelAnimationSpeed.fromPreference("future-speed"))
    }
}
