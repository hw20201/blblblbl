package com.github.arca.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardShortcutsTest {
    @Test fun defaultHomeShortcutsAreTheRequestedEight() {
        assertEquals(listOf("vpn", "ip_test", "nodes", "profiles", "request_logs", "dns", "rules", "routing"), DashboardShortcuts.defaults)
    }

    @Test fun storedCustomOrderKeepsItemsAndAddsOnlyCoreActions() {
        assertEquals(listOf("rules", "nodes", "vpn", "ip_test"), DashboardShortcuts.order("rules,nodes"))
        assertTrue(DashboardShortcuts.v76Defaults.contains("traffic"))
    }
}
