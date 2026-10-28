package com.github.mihomo.android.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.github.mihomo.android.ui.theme.DarkLoonThemeColors
import com.github.mihomo.android.ui.theme.LightLoonThemeColors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.max
import kotlin.math.min
import org.junit.Test

class ProxySelectionStyleTest {
    @Test fun selectedNodeUsesAThreePercentVisualInset() {
        assertEquals(0.97f, selectedProxyCardScale(true), 0.0001f)
        assertEquals(1f, selectedProxyCardScale(false), 0f)
    }

    @Test fun selectedCardUsesReferenceGrayFill() {
        assertEquals(Color(0xFFABB1BC), SelectedProxyCardFill)
        assertTrue(SelectedProxyCardFill.luminance() in .4f..0.55f)
    }

    @Test fun selectedGrayAndLabelStayDistinctInLightAndDarkThemes() {
        val selectedLuminance = SelectedProxyCardFill.luminance()
        assertTrue(kotlin.math.abs(selectedLuminance - LightLoonThemeColors.card.luminance()) > .25f)
        assertTrue(kotlin.math.abs(selectedLuminance - DarkLoonThemeColors.card.luminance()) > .25f)
        assertTrue(contrastRatio(SelectedProxyCardText, SelectedProxyCardFill) >= 7f)
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val high = max(first.luminance(), second.luminance())
        val low = min(first.luminance(), second.luminance())
        return (high + .05f) / (low + .05f)
    }
}
