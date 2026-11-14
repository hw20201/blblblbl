package com.github.arca.android.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM guard for the shared content inset used above the floating dock. */
class MainDockContentInsetMathTest {
    @Test
    fun reservesDockHeightMarginAndSystemNavigationInset() {
        assertEquals(80.dp, mainDockContentInset(0.dp))
        assertEquals(104.dp, mainDockContentInset(24.dp))
    }
}
