package com.github.mihomo.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.core.model.Proxy
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.data.ParsedRule
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.components.ShortcutSearchField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class NexusNavigationRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sharedShortcutSearchFieldKeepsTypingAndClearBehavior() {
        val query = mutableStateOf("")
        compose.setContent {
            ShortcutSearchField(value = query.value, onValueChange = { query.value = it }, placeholder = "搜索")
        }
        compose.onNodeWithTag("shortcut-search-field").performTextInput("example.com")
        compose.runOnIdle { assertEquals("example.com", query.value) }
        compose.onNodeWithContentDescription("清除").performClick()
        compose.runOnIdle { assertEquals("", query.value) }
    }

    @Test fun nodeShortcutOpensSelectorAndSelectionReturnsThroughCallback() {
        var selected: Pair<String, Proxy>? = null
        val node = Proxy("Tokyo", type = "Shadowsocks", delay = 45)
        compose.setContent {
            DashboardScreen(
                scrollState = ScrollState(0),
                vpnState = VpnState(status = VpnState.Status.STOPPED),
                currentMode = TunnelMode.RULE,
                activeProfileName = "Profile",
                mainGroupName = "Proxy",
                mainGroupProxies = listOf(node),
                activeNodeName = "",
                totalNodes = 1,
                totalRules = 0,
                visibleShortcuts = setOf("nodes"),
                shortcutOrder = listOf("nodes"),
                onToggleVpn = {},
                onModeSelected = {},
                onSelectProxy = { group, proxy -> selected = group to proxy },
                onHealthCheck = {},
                onNavigateToProfiles = {},
                onNavigateToAppRouting = {},
                onNavigateToLogs = {},
                onNavigateToRules = {},
                onNavigateToDns = {},
                onNavigateToRequests = {},
                onNavigateToNetworkShare = {},
                onUpdateVisibleShortcuts = {},
                onUpdateShortcutOrder = {},
            )
        }

        compose.onNodeWithTag("shortcut-nodes").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("quick-node-selector-sheet").assertExists()
        compose.onNodeWithText("Tokyo", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("Proxy" to node, selected) }
        compose.onNodeWithTag("quick-node-selector-sheet").assertDoesNotExist()
    }

    @Test fun rulesPageTitleUsesCapsuleAndBackStillWorks() {
        var backed = false
        compose.setContent {
            RulesScreen(emptyList(), 0, TunnelMode.RULE) { backed = true }
        }
        compose.onNodeWithTag("navigation-title-capsule").assertExists()
        compose.onNodeWithTag("navigation-bar-transparent-row").assertExists()
        compose.onNodeWithText("返回").performClick()
        compose.runOnIdle { assertTrue(backed) }
    }

    @Test fun rulesContentScrollsUnderPinnedTransparentTitle() {
        val rules = (0 until 60).map { index ->
            ParsedRule(type = "DOMAIN", payload = "host-$index.example", target = "Proxy")
        }
        compose.setContent {
            RulesScreen(rules, rules.size, TunnelMode.RULE) {}
        }
        compose.onNodeWithTag("rules-list").assertExists()
        repeat(8) {
            compose.onNodeWithTag("rules-list").performTouchInput { swipeUp() }
        }
        compose.waitForIdle()
        compose.onNodeWithText("host-59.example").assertIsDisplayed()
        compose.onNodeWithTag("navigation-bar-transparent-row").assertExists()
    }
}
