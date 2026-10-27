package com.github.mihomo.android.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySelectionStyleTest {
    @Test fun selectedNodeUsesAThreePercentVisualInset() {
        assertEquals(0.97f, selectedProxyCardScale(true), 0.0001f)
        assertEquals(1f, selectedProxyCardScale(false), 0f)
    }

    @Test fun selectedCardUsesDarkNeutralFill() {
        assertEquals(Color(0xFF26313E), SelectedProxyCardFill)
        assertTrue(SelectedProxyCardFill.luminance() < .05f)
    }
}
