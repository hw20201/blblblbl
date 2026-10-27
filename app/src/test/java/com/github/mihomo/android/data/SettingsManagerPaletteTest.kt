package com.github.mihomo.android.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.github.mihomo.android.ui.glass.GlassWallpaperSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun openingSettingsRetiresLegacyThemePalettesAndUsesCustomWallpaper() {
        prefs.edit()
            .putString("glass_palette", "custom:12AB34")
            .putString("glass_saved_palettes_v1", "[]")
            .putBoolean("glass_wallpaper_enabled", false)
            .commit()

        val settings = SettingsManager(context)
        assertFalse(prefs.contains("glass_palette"))
        assertFalse(prefs.contains("glass_saved_palettes_v1"))
        assertFalse(prefs.contains("glass_wallpaper_enabled"))
        assertTrue(settings.glassWallpaperSettings.enabled)
    }

    @Test fun customWallpaperPalettesCanBeSavedReloadedAppliedAndDeleted() {
        val manager = SettingsManager(context)
        val custom = GlassWallpaperSettings(
            colorA = Color.Red.toArgb(), colorB = Color.Green.toArgb(), colorC = Color.Blue.toArgb(), colorFlow = false
        )
        val saved = manager.saveWallpaperPalette("evening", custom)
        assertEquals("evening", saved.single().name)
        assertEquals(saved, SettingsManager(context).savedWallpaperPalettes)
        assertEquals(custom.colorB, saved.single().applyTo(GlassWallpaperSettings()).colorB)
        assertFalse(saved.single().applyTo(GlassWallpaperSettings()).colorFlow)
        assertTrue(manager.deleteWallpaperPalette("evening").isEmpty())
        assertTrue(SettingsManager(context).savedWallpaperPalettes.isEmpty())
    }
}
