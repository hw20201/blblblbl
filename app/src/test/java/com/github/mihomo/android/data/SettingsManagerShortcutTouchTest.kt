package com.github.mihomo.android.data

import android.content.Context
import com.github.mihomo.android.ui.components.ShortcutTouchVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsManagerShortcutTouchTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun defaultsToGentleFloatWithoutWritingPreference() {
        val prefs = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val settings = SettingsManager(context)
        assertEquals(ShortcutTouchVariant.GentleFloat, settings.shortcutTouchVariant)
        assertFalse(prefs.contains("shortcut_touch_variant"))
    }

    @Test
    fun allVariantsPersistAndUnknownValueFallsBackToGentleFloat() {
        val prefs = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val settings = SettingsManager(context)
        ShortcutTouchVariant.entries.forEach { variant ->
            settings.shortcutTouchVariant = variant
            assertEquals(variant.name, prefs.getString("shortcut_touch_variant", null))
            assertEquals(variant, SettingsManager(context).shortcutTouchVariant)
        }
        prefs.edit().putString("shortcut_touch_variant", "removed-future-style").commit()
        assertEquals(ShortcutTouchVariant.GentleFloat, settings.shortcutTouchVariant)
    }
}
