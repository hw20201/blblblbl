package com.github.mihomo.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ProxySelectionStyleTest {
    @Test fun selectedNodeUsesAThreePercentVisualInset() {
        assertEquals(0.97f, selectedProxyCardScale(true), 0.0001f)
        assertEquals(1f, selectedProxyCardScale(false), 0f)
    }
}
