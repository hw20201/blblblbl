package com.github.mihomo.android.ui.glass

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyGroupGlassPolicyTest {
    @Test fun onlyRequestedTwoColumnGroupsOptIn() {
        assertTrue(useProxyGroupGlass(isGroup = true, columns = 2, hasSource = true))
        assertFalse(useProxyGroupGlass(isGroup = false, columns = 2, hasSource = true))
        assertFalse(useProxyGroupGlass(isGroup = true, columns = 1, hasSource = true))
        assertFalse(useProxyGroupGlass(isGroup = true, columns = 3, hasSource = true))
        assertFalse(useProxyGroupGlass(isGroup = true, columns = 2, hasSource = false))
    }

    @Test fun modernDevicesReuseOnlyAnAvailableSource() {
        assertTrue(replayProxyGroupBackdrop(35, lowRam = false, hasSource = true))
        assertTrue(replayProxyGroupBackdrop(31, lowRam = false, hasSource = true))
        assertFalse(replayProxyGroupBackdrop(35, lowRam = false, hasSource = false))
    }

    @Test fun lowRamAndOlderDevicesSkipCardReplay() {
        for (api in listOf(26, 30, 31, 35)) {
            assertFalse(replayProxyGroupBackdrop(api, lowRam = true, hasSource = true))
        }
        assertFalse(replayProxyGroupBackdrop(26, lowRam = false, hasSource = true))
        assertFalse(replayProxyGroupBackdrop(30, lowRam = false, hasSource = true))
    }

    @Test fun glassGroupUsesItsOwnRimWithoutAnExtraWhiteCardStroke() {
        assertFalse(proxyCardNeedsOuterBorder(groupGlass = true, selected = false))
        // Selected cards use a dark fill rather than a blue outline.
        assertFalse(proxyCardNeedsOuterBorder(groupGlass = true, selected = true))
        // Only unselected non-glass cards retain a neutral outline.
        assertTrue(proxyCardNeedsOuterBorder(groupGlass = false, selected = false))
        assertFalse(proxyCardNeedsOuterBorder(groupGlass = false, selected = true))
    }

}
