package com.github.mihomo.android.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsManagerTunStackTest {
    private lateinit var context: Context
    private lateinit var settings: SettingsManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        settings = SettingsManager(context)
    }

    @Test
    fun newInstallDefaultsToMipsWithoutWritingPreference() {
        assertEquals(TunStack.MIPS, settings.tunStack)
        assertFalse(context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE).contains("tun_stack"))
    }

    @Test
    fun everySupportedStackPersistsAndReadsBack() {
        TunStack.entries.forEach { stack ->
            settings.tunStack = stack
            assertEquals(stack, settings.tunStack)
            assertEquals(stack.key, context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE).getString("tun_stack", null))
        }
        assertEquals(setOf("system", "mixed", "gvisor", "mips"), TunStack.entries.map { it.key }.toSet())
    }

    @Test
    fun unknownLegacyValueFallsBackToMixed() {
        context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
            .edit().putString("tun_stack", "legacy-value").commit()
        assertEquals(TunStack.MIXED, settings.tunStack)
        assertEquals("legacy-value", context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE).getString("tun_stack", null))
    }

    @Test
    fun rawSetterRejectsInvalidValueWithoutOverwritingPreference() {
        settings.tunStack = TunStack.GVISOR
        assertFalse(settings.setTunStack("not-a-stack"))
        assertEquals(TunStack.GVISOR, settings.tunStack)
        assertTrue(settings.setTunStack("mips"))
        assertEquals(TunStack.MIPS, settings.tunStack)
    }
}
