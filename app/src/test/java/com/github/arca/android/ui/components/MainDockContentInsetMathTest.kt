package com.github.arca.android.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM guard for the shared content inset used above the floating dock. */
class MainDockContentInsetMathTest {
    @Test
    fun reservesDockHeightMarginSafetyGapAndSystemNavigationInset() {
        assertEquals(96.dp, mainDockContentInset(0.dp))
        assertEquals(120.dp, mainDockContentInset(24.dp))
        assertEquals(144.dp, mainDockContentInset(48.dp))
    }

    @Test
    fun contentTailClearsDockTopBySixteenDpForEveryNavigationMode() {
        listOf(0.dp, 24.dp, 48.dp).forEach { navigationInset ->
            assertEquals(
                16.dp,
                mainDockContentInset(navigationInset) - BOTTOM_BAR_HEIGHT - BOTTOM_BAR_BOTTOM_MARGIN - navigationInset
            )
        }
    }
}
