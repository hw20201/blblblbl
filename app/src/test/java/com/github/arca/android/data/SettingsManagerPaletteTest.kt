package com.github.arca.android.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsManagerPaletteTest {
    private lateinit var context: Context
    private val prefs get() = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs.edit().clear().commit()
    }

    @Test fun openingSettingsRetiresLegacyThemePalettePreferences() {
        prefs.edit()
            .putString("glass_palette", "custom:12AB34")
            .putString("glass_saved_palettes_v1", "[]")
            .commit()

        SettingsManager(context)

        assertFalse(prefs.contains("glass_palette"))
        assertFalse(prefs.contains("glass_saved_palettes_v1"))
    }

    @Test fun openingSettingsClearsAllLegacyWallpaperPreferencesAndPreservesActiveSettings() {
        val legacyKeys = listOf(
            "glass_wallpaper_enabled", "glass_wallpaper_mode", "glass_wallpaper_color_a",
            "glass_wallpaper_color_b", "glass_wallpaper_color_c", "glass_wallpaper_color_flow",
            "glass_wallpaper_rib_angle", "glass_wallpaper_saved_palettes_v1",
            "glass_wallpaper_unknown_retired_option"
        )
        prefs.edit()
            .putBoolean(legacyKeys[0], true)
            .putString(legacyKeys[1], "FLUTED")
            .putInt(legacyKeys[2], 0xFF15263A.toInt())
            .putInt(legacyKeys[3], 0xFFB68A62.toInt())
            .putInt(legacyKeys[4], 0xFF7DB5C8.toInt())
            .putBoolean(legacyKeys[5], false)
            .putFloat(legacyKeys[6], 7f)
            .putString(legacyKeys[7], "[]")
            .putString(legacyKeys[8], "obsolete")
            .putString("theme_mode", ThemeMode.DARK.name)
            .putString("app_language", "en")
            .putString("glass_settings_v1", "{}")
            .putString("selected_profile_id", "active-profile")
            .commit()

        val settings = SettingsManager(context)

        legacyKeys.forEach { key -> assertFalse("retired key remains: $key", prefs.contains(key)) }
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals("en", settings.appLanguage)
        assertEquals("active-profile", settings.selectedProfileId)
        assertEquals("{}", prefs.getString("glass_settings_v1", null))
        assertEquals(ThemeMode.DARK, SettingsManager(context).themeMode)
    }
}
