package com.github.mihomo.android.data

import android.content.Context
import com.github.mihomo.android.ui.glass.ShortcutGlassLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsManagerShortcutGlassLevelTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun defaultsToNormalWithoutWritingPreference() {
        val prefs = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val settings = SettingsManager(context)
        assertEquals(ShortcutGlassLevel.Normal, settings.shortcutGlassLevel)
        assertFalse(prefs.contains("shortcut_glass_level"))
    }

    @Test
    fun allLevelsPersistAndUnknownValueFallsBackToNormal() {
        val prefs = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val settings = SettingsManager(context)
        ShortcutGlassLevel.entries.forEach { level ->
            settings.shortcutGlassLevel = level
            assertEquals(level.name, prefs.getString("shortcut_glass_level", null))
            assertEquals(level, SettingsManager(context).shortcutGlassLevel)
        }
        prefs.edit().putString("shortcut_glass_level", "future-level").commit()
        assertEquals(ShortcutGlassLevel.Normal, settings.shortcutGlassLevel)
    }
}
