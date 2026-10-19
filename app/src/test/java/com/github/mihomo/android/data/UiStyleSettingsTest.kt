package com.github.mihomo.android.data

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class UiStyleSettingsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)

    @Before fun clear() { prefs.edit().clear().commit() }

    @Test fun styleDefaultsToCupertinoAndRoundTripsWithoutChangingThemeOrTunnel() {
        val settings = SettingsManager(context)
        settings.themeMode = ThemeMode.DARK
        settings.tunStack = TunStack.MIPS
        assertEquals(UiStyle.CUPERTINO, settings.uiStyle)
        settings.uiStyle = settings.uiStyle.toggled()
        assertEquals(UiStyle.MIUIX, SettingsManager(context).uiStyle)
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(TunStack.MIPS, settings.tunStack)
        settings.uiStyle = settings.uiStyle.toggled()
        assertEquals(UiStyle.CUPERTINO, SettingsManager(context).uiStyle)
        prefs.edit().putString("ui_style", "future").commit()
        assertEquals(UiStyle.CUPERTINO, settings.uiStyle)
    }

    @Test fun freshInstallShowsAllNewActions() {
        val settings = SettingsManager(context)
        assertTrue(settings.visibleShortcuts.containsAll(DashboardShortcuts.added))
        assertEquals(DashboardShortcuts.added, settings.shortcutOrder.take(3))
    }

    @Test fun upgradePreservesUserOrderingAndExistingHiddenShortcuts() {
        prefs.edit().putString("shortcut_order", "rules,nodes,profiles")
            .putStringSet("visible_shortcuts", setOf("rules")).commit()
        val settings = SettingsManager(context)
        assertEquals(listOf("rules", "nodes", "profiles") + DashboardShortcuts.added, settings.shortcutOrder)
        assertEquals(setOf("rules") + DashboardShortcuts.added, settings.visibleShortcuts)
        assertFalse(settings.visibleShortcuts.contains("profiles"))
        settings.visibleShortcuts = setOf("vpn")
        assertEquals(setOf("vpn"), SettingsManager(context).visibleShortcuts)
    }

    @Test fun hiddenNewActionsStayHiddenAndOrderHasNoDuplicates() {
        val settings = SettingsManager(context)
        settings.visibleShortcuts = emptySet()
        settings.shortcutOrder = listOf("ip_test", "nodes", "vpn", "ui_style")
        assertEquals(emptySet<String>(), SettingsManager(context).visibleShortcuts)
        assertEquals(listOf("ip_test", "nodes", "vpn", "ui_style"), settings.shortcutOrder)
    }
}
