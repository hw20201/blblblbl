package com.github.arca.android.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPaletteTest {
    @Test
    fun `delay colours follow shared thresholds`() {
        val muted = Color.Gray
        assertEquals(AppPalette.delayGood, AppPalette.delayColor(120, muted))
        assertEquals(AppPalette.delayFair, AppPalette.delayColor(400, muted))
        assertEquals(AppPalette.delayPoor, AppPalette.delayColor(900, muted))
        assertEquals(AppPalette.delayPoor, AppPalette.delayColor(-2, muted))
        assertEquals(AppPalette.delayPoor, AppPalette.delayColor(65535, muted))
        assertEquals(muted, AppPalette.delayColor(-1, muted))
    }
}
