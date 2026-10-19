package com.github.mihomo.android.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.github.mihomo.android.ui.theme.IosBlue
import com.github.mihomo.android.ui.theme.IosGreen
import com.github.mihomo.android.ui.theme.LoonOrange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassPaletteCustomColorTest {
    @Test fun strictCustomKeysResolveArbitraryRgbAndRejectMalformedValues() {
        val palette = GlassPalettes.byKey("custom:17A2C9")
        assertEquals("custom:17A2C9", palette.key)
        assertEquals(Color(0xFF17A2C9), palette.accent)
        listOf("custom:FFF", "custom:1234567", "custom:GGGGGG", "Custom:123456", "custom:12 456").forEach {
            assertEquals(it, GlassPalettes.CREAM, GlassPalettes.byKey(it))
        }
    }

    @Test fun distinctCustomKeysProduceDistinctAccentsAndMesh() {
        val a = GlassPalettes.byKey("custom:FF2200")
        val b = GlassPalettes.byKey("custom:00A8D8")
        assertNotEquals(a.accent, b.accent)
        assertNotEquals(a.blobs.map { it.color }, b.blobs.map { it.color })
        assertEquals("custom:FF2200", GlassPalettes.byKey(GlassPalettes.keyForColor(a.accent)).key)
    }

    @Test fun customPaletteStaysLightForDarkResolutionAndKeepsChosenAccent() {
        val custom = GlassPalettes.byKey("custom:9A68D8")
        assertTrue(!custom.isDark)
        assertEquals(Color(0xFF9A68D8), custom.accent)
        assertEquals(GlassPalettes.MIDNIGHT, GlassPalettes.byKey("midnight"))
    }

    @Test fun gridChoiceReachesLegacyControlsButNotSemanticStateColors() {
        try {
            GlassDefaults.enabled = true
            val blue = GlassPalettes.byKey("custom:19AACC")
            GlassDefaults.palette = blue
            assertEquals(blue.uiAccent, LoonOrange)
            assertEquals(blue.uiAccent, IosBlue)
            val old = LoonOrange
            GlassDefaults.palette = GlassPalettes.byKey("custom:DD3366")
            assertNotEquals(old, LoonOrange)
            assertEquals(Color(0xFF34C759), IosGreen)
            assertEquals(Color(0xFF00C853), GlassAccent.upload)
            assertEquals(Color(0xFF4A69FF), GlassAccent.download)
            GlassDefaults.palette = GlassPalettes.CREAM
            assertEquals(Color(0xFFFF6B00), LoonOrange)
            assertEquals(Color(0xFF007AFF), IosBlue)
        } finally {
            GlassDefaults.enabled = true
            GlassDefaults.palette = GlassPalettes.CREAM
        }
    }

    @Test fun blackWhiteAndPaleSwatchesKeepExactStoredRgbWithoutInvisibleControls() {
        for (color in ThemeColorGrid) {
            val spec = GlassPalettes.byKey(GlassPalettes.keyForColor(color))
            assertEquals(color, spec.accent)
            assertTrue("control accent stays visible for ${spec.key}", spec.uiAccent.luminance() in .15f.. .25f)
        }
    }
}
