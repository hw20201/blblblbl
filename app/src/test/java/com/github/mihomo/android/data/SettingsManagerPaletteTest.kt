package com.github.mihomo.android.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.github.mihomo.android.ui.glass.GlassPalettes
import com.github.mihomo.android.ui.glass.toTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The palette selection is user-visible state that survives restarts, so it gets the same treatment
 * as [SettingsManagerTunStackTest]: every value round-trips, and an unknown value must not crash.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsManagerPaletteTest {
    private lateinit var context: Context
    private lateinit var settings: SettingsManager

    private val prefs
        get() = context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs.edit().clear().commit()
        settings = SettingsManager(context)
    }

    @Test
    fun newInstallDefaultsToCreamWithoutWritingPreference() {
        assertEquals("cream", settings.glassPaletteKey)
        assertFalse(prefs.contains("glass_palette"))
    }

    @Test
    fun everyPalettePersistsAndReadsBack() {
        GlassPalettes.all.forEach { palette ->
            settings.glassPaletteKey = palette.key
            assertEquals(palette.key, settings.glassPaletteKey)
            assertEquals(palette.key, prefs.getString("glass_palette", null))
        }
    }

    @Test
    fun unknownKeyFallsBackToTheDefaultInsteadOfCrashing() {
        // A palette removed in a future build must not strand a user on a blank screen.
        settings.glassPaletteKey = "does-not-exist"
        assertEquals("does-not-exist", settings.glassPaletteKey)
        assertEquals(GlassPalettes.CREAM, GlassPalettes.byKey(settings.glassPaletteKey))
    }

    @Test
    fun nullKeyResolvesToTheDefault() {
        assertEquals(GlassPalettes.CREAM, GlassPalettes.byKey(null))
    }

    @Test
    fun paletteKeysAreUnique() {
        val keys = GlassPalettes.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun everyPaletteIsCompleteEnoughToRender() {
        GlassPalettes.all.forEach { p ->
            assertTrue("${p.key}: blobs", p.blobs.isNotEmpty())
            assertTrue("${p.key}: swatch", p.swatch.size >= 3)
            // A palette whose panel is fully opaque hides the backdrop and stops looking like glass.
            assertTrue("${p.key}: panelStart alpha", p.panelStart.alpha < 1f)
            assertTrue("${p.key}: panelEnd alpha", p.panelEnd.alpha < 1f)
            // Text has to be readable against the panel, so the two must differ in luminance.
            assertTrue("${p.key}: contrast", luminance(p.textPrimary) != luminance(p.base))
        }
    }

    @Test
    fun darkPaletteIsTheOnlyDarkOne() {
        assertEquals(listOf("midnight"), GlassPalettes.all.filter { it.isDark }.map { it.key })
    }

    @Test
    fun tokensPreservePaletteIdentity() {
        GlassPalettes.all.forEach { p ->
            val tokens = p.toTokens()
            assertEquals(p.panelStart, tokens.tint)
            assertEquals(p.panelEnd, tokens.tintEnd)
            assertEquals(p.isDark, tokens.isDark)
            assertNotNull(tokens.edge)
        }
    }

    private fun luminance(c: Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
}
