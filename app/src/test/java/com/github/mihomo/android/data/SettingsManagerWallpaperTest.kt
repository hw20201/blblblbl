package com.github.mihomo.android.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.github.mihomo.android.ui.glass.GlassWallpaperMode
import com.github.mihomo.android.ui.glass.GlassWallpaperSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsManagerWallpaperTest {
    private lateinit var context: Context
    private lateinit var settings: SettingsManager

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsManager(context)
    }

    @Test fun wallpaperChoicesPersistAcrossSettingsManagerInstances() {
        assertFalse(settings.glassWallpaperSettings.enabled)
        val expected = GlassWallpaperSettings(
            enabled = true,
            mode = GlassWallpaperMode.FLUTED,
            colorA = Color(0xFF26313E).toArgb(),
            colorB = Color(0xFFC9A77B).toArgb(),
            colorC = Color(0xFF8BAFC6).toArgb(),
            colorFlow = false
        )
        settings.glassWallpaperSettings = expected
        assertEquals(expected, SettingsManager(context).glassWallpaperSettings)
    }

    @Test fun invalidSavedModeUsesSafeDefaultWithoutDiscardingColors() {
        context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("glass_wallpaper_enabled", true)
            .putString("glass_wallpaper_mode", "MISSING")
            .putInt("glass_wallpaper_color_a", Color.Red.toArgb())
            .commit()
        val loaded = SettingsManager(context).glassWallpaperSettings
        assertEquals(GlassWallpaperMode.FLUTED, loaded.mode)
        assertEquals(Color.Red.toArgb(), loaded.colorA)
    }
}
