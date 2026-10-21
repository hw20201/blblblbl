package com.github.mihomo.android.ui.glass

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
}

    @Test fun glassGroupUsesItsOwnRimWithoutAnExtraWhiteCardStroke() {
        assertFalse(proxyCardNeedsOuterBorder(groupGlass = true, selected = false))
        // Keep the blue selection indicator even when the material supplies its own bevel.
        assertTrue(proxyCardNeedsOuterBorder(groupGlass = true, selected = true))
        // Non-glass cards retain their ordinary outline in both states.
        assertTrue(proxyCardNeedsOuterBorder(groupGlass = false, selected = false))
        assertTrue(proxyCardNeedsOuterBorder(groupGlass = false, selected = true))
    }
