package com.github.mihomo.android.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GlassIntensitySettingsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun defaultsToHalfIntensity() {
        assertEquals(0.5f, SettingsManager(context).glassIntensity, 0f)
    }

    @Test
    fun intensityPersistsAcrossSettingsManagerInstances() {
        SettingsManager(context).glassIntensity = 0.73f
        assertEquals(0.73f, SettingsManager(context).glassIntensity, 0f)
        assertEquals(0.73f, prefs.getFloat("glass_intensity", -1f), 0f)
    }

    @Test
    fun enabledPreferencePersistsIndependentlyOfIntensityAndUiStyle() {
        val settings = SettingsManager(context)
        settings.uiStyle = UiStyle.MIUIX
        settings.glassIntensity = 0.82f
        settings.glassEnabled = false

        val reopened = SettingsManager(context)
        assertEquals(false, reopened.glassEnabled)
        assertEquals(0.82f, reopened.glassIntensity, 0f)
        assertEquals(UiStyle.MIUIX, reopened.uiStyle)
        reopened.glassEnabled = true
        assertEquals(0.82f, SettingsManager(context).glassIntensity, 0f)
    }

    @Test
    fun outOfRangeAndNonFiniteValuesNormalize() {
        val settings = SettingsManager(context)
        settings.glassIntensity = -0.25f
        assertEquals(0f, settings.glassIntensity, 0f)
        settings.glassIntensity = 1.25f
        assertEquals(1f, settings.glassIntensity, 0f)
        settings.glassIntensity = Float.NaN
        assertEquals(GlassEffect.DEFAULT_INTENSITY, settings.glassIntensity, 0f)
        settings.glassIntensity = Float.POSITIVE_INFINITY
        assertEquals(GlassEffect.DEFAULT_INTENSITY, settings.glassIntensity, 0f)
        settings.glassIntensity = Float.NEGATIVE_INFINITY
        assertEquals(GlassEffect.DEFAULT_INTENSITY, settings.glassIntensity, 0f)
    }

    @Test
    fun glassIntensityDoesNotChangeUiStyleOrThemeMode() {
        val settings = SettingsManager(context)
        settings.uiStyle = UiStyle.MIUIX
        settings.themeMode = ThemeMode.DARK
        settings.glassIntensity = 0.82f

        val reopened = SettingsManager(context)
        assertEquals(UiStyle.MIUIX, reopened.uiStyle)
        assertEquals(ThemeMode.DARK, reopened.themeMode)
        assertEquals(0.82f, reopened.glassIntensity, 0f)
    }
}
