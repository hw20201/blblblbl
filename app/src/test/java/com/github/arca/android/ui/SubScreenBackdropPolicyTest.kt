package com.github.arca.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubScreenBackdropPolicyTest {
    @Test fun nonGlassSubScreensAreIsolatedFromUnderlyingSettingsContent() {
        listOf(SubScreen.CONFIGURATION_CENTER, SubScreen.PROFILES, SubScreen.APP_ROUTING,
            SubScreen.LOGS, SubScreen.SCRIPTS, SubScreen.PROFILE_OVERRIDE, SubScreen.RULES,
            SubScreen.DNS, SubScreen.REQUESTS, SubScreen.NETWORK_SHARE, SubScreen.APPEARANCE,
            SubScreen.TRAFFIC, SubScreen.CONNECTIONS).forEach { screen ->
            val policy = subScreenBackdropPolicy(screen, apiLevel = 35, pageBackdropAvailable = true)
            assertTrue(screen.name, policy.useRootWallpaperBackdrop)
            assertTrue(screen.name, policy.drawWallpaperBackground)
        }
    }

    @Test fun glassEffectsRestoresItsIndependentWallpaperBackground() {
        val policy = subScreenBackdropPolicy(SubScreen.GLASS_EFFECTS, apiLevel = 35, pageBackdropAvailable = true)
        assertTrue(policy.useRootWallpaperBackdrop)
        assertTrue(policy.drawWallpaperBackground)
    }

    @Test fun settingsParentIsHiddenOnlyForActiveSettingsChildRoutes() {
        assertTrue(shouldHideSettingsParentContent(true, currentPage = 2, targetPage = 2))
        assertTrue(shouldHideSettingsParentContent(true, currentPage = 0, targetPage = 2))
        assertFalse(shouldHideSettingsParentContent(false, currentPage = 2, targetPage = 2))
        assertFalse(shouldHideSettingsParentContent(true, currentPage = 0, targetPage = 0))
    }
}
