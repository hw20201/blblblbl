package com.github.mihomo.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpstreamUiRestorationTest {
    @Test fun legacyGlassPreferencesDoNotAffectFunctionalSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putString("ui_style", "MIUIX")
            .putBoolean("glass_enabled", true)
            .putFloat("glass_intensity", 1f)
            .putString("app_language", "zh")
            .putString("tun_stack", "mips")
            .putBoolean("haptic_enabled", false)
            .commit()
        val settings = SettingsManager(context)
        assertEquals("zh", settings.appLanguage)
        assertEquals(TunStack.MIPS, settings.tunStack)
        assertFalse(settings.hapticEnabled)
        settings.appLanguage = "SYSTEM"
        assertEquals("SYSTEM", SettingsManager(context).appLanguage)
        assertEquals(TunStack.MIPS, SettingsManager(context).tunStack)
        prefs.edit().clear().commit()
    }

    @Test fun productionUiHasNoGlassRendererOrAppearanceControls() {
        val root = listOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
        val removedReferences = listOf(
            "GlassBackdrop", "GlassSurface", "GlassWallpaper", "GlassEffect",
            "GlassThemeSection", "glassEnabled", "glassIntensity", "LocalUiStyle"
        )
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            removedReferences.forEach { name ->
                assertFalse("$name remains in $file", text.contains(name))
            }
        }
        val theme = File(root, "com/github/mihomo/android/ui/theme/Theme.kt").readText()
        assertTrue(theme.contains("bg = Color(0xFFF2F2F7)"))
        assertTrue(theme.contains("card = Color(0xFFFFFFFF)"))
        assertTrue(theme.contains("card = Color(0xFF1C1C1E)"))
    }
}
