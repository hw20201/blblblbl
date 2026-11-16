package com.github.arca.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.NavigationLensGlobalBounds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class DashboardLensReadTest {
    @get:Rule val compose = createComposeRule()
    private val originalGlassEnabled = GlassDefaults.enabled

    @After fun restoreGlass() { GlassDefaults.enabled = originalGlassEnabled }

    @Test fun dockBoundsAreObservedOnlyWhenTheDnsCardIsPresent() {
        GlassDefaults.enabled = false
        val visible = mutableStateOf(setOf("nodes"))
        val bounds = mutableStateOf<NavigationLensGlobalBounds?>(null)
        var reads = 0
        val observedBounds = object : State<NavigationLensGlobalBounds?> {
            override val value: NavigationLensGlobalBounds?
                get() { reads++; return bounds.value }
        }
        val scrollState = ScrollState(0)
        compose.setContent {
            DashboardScreen(
                scrollState = scrollState,
                vpnState = VpnState(),
                currentMode = TunnelMode.RULE,
                activeProfileName = "Test",
                mainGroupName = "",
                mainGroupProxies = emptyList(),
                activeNodeName = "",
                totalNodes = 0,
                totalRules = 0,
                visibleShortcuts = visible.value,
                shortcutOrder = listOf("nodes", "dns"),
                onToggleVpn = {},
                onModeSelected = {},
                onSelectProxy = { _, _ -> },
                onHealthCheck = {},
                onNavigateToProfiles = {},
                onNavigateToAppRouting = {},
                onNavigateToLogs = {},
                onUpdateVisibleShortcuts = {},
                onUpdateShortcutOrder = {},
                navigationLensBoundsState = observedBounds
            )
        }
        compose.runOnIdle { bounds.value = NavigationLensGlobalBounds(Rect(0f, 0f, 100f, 60f), 20f) }
        compose.runOnIdle { assertEquals("dashboard without DNS does not observe dock animation", 0, reads) }
        compose.runOnIdle { visible.value = setOf("nodes", "dns") }
        compose.runOnIdle { assertTrue("DNS card observes its tint geometry", reads > 0) }
        var before = 0
        compose.runOnIdle {
            before = reads
            bounds.value = NavigationLensGlobalBounds(Rect(30f, 0f, 130f, 60f), 20f)
        }
        compose.runOnIdle { assertTrue("DNS tint keeps following animated bounds", reads > before) }
    }
}
