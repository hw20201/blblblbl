package com.github.mihomo.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigurationNavigationTest {
    @Test fun centerChildrenReturnToCenterRatherThanSettings() {
        listOf(SubScreen.PROFILES, SubScreen.SCRIPTS, SubScreen.RULES, SubScreen.APP_ROUTING).forEach {
            assertEquals(SubScreen.CONFIGURATION_CENTER, configurationParent(it, true, false))
            assertEquals(2, configurationDepth(it, true, false))
        }
        assertNull(configurationParent(SubScreen.CONFIGURATION_CENTER, false, false))
    }

    @Test fun overrideScriptRetainsTheWholeConfigurationReturnChain() {
        assertEquals(SubScreen.PROFILE_OVERRIDE, configurationParent(SubScreen.SCRIPTS, true, true))
        assertEquals(SubScreen.PROFILES, configurationParent(SubScreen.PROFILE_OVERRIDE, true, true))
        assertEquals(SubScreen.CONFIGURATION_CENTER, configurationParent(SubScreen.PROFILES, true, true))
        assertEquals(4, configurationDepth(SubScreen.SCRIPTS, true, true))
        assertEquals(3, configurationDepth(SubScreen.PROFILE_OVERRIDE, true, true))
    }

    @Test fun dashboardShortcutsKeepTheirOriginalReturnDestination() {
        listOf(SubScreen.PROFILES, SubScreen.SCRIPTS, SubScreen.RULES, SubScreen.APP_ROUTING).forEach {
            assertNull(configurationParent(it, false, false))
            assertEquals(1, configurationDepth(it, false, false))
        }
        assertEquals(SubScreen.PROFILE_OVERRIDE, configurationParent(SubScreen.SCRIPTS, false, true))
        assertEquals(SubScreen.PROFILES, configurationParent(SubScreen.PROFILE_OVERRIDE, false, true))
        assertEquals(0, configurationDepth(null, false, false))
    }
}
