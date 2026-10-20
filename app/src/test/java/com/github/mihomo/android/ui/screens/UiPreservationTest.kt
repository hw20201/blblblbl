package com.github.mihomo.android.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.data.ParsedRule
import com.github.mihomo.android.ui.theme.AppStrings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UiPreservationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rulesScreenCombinesTypeAndBehaviorFilters() {
        val rules = listOf(
            ParsedRule(type = "DOMAIN-SUFFIX", payload = "direct-example.test", target = "DIRECT"),
            ParsedRule(type = "DOMAIN-SUFFIX", payload = "proxy-example.test", target = "Proxy"),
            ParsedRule(type = "IP-CIDR", payload = "192.0.2.0/24", target = "DIRECT"),
            ParsedRule(type = "IP-CIDR", payload = "198.51.100.0/24", target = "Proxy")
        )

        compose.setContent {
            RulesScreen(rules = rules, totalRules = rules.size, tunnelMode = TunnelMode.RULE, onBack = {})
        }

        compose.onNodeWithText("direct-example.test").assertExists()
        compose.onNodeWithText("proxy-example.test").assertExists()
        compose.onNodeWithText("192.0.2.0/24").assertExists()

        // Select a rule type and then a behavior; the resulting list must satisfy both filters.
        compose.onAllNodesWithText("DOMAIN-SUFFIX").onFirst().performClick()
        compose.onNodeWithText("直连").performClick()
        compose.onNodeWithText("direct-example.test").assertExists()
        compose.onNodeWithText("proxy-example.test").assertDoesNotExist()
        compose.onNodeWithText("192.0.2.0/24").assertDoesNotExist()

        compose.onNodeWithText("代理").performClick()
        compose.onNodeWithText("direct-example.test").assertDoesNotExist()
        compose.onNodeWithText("proxy-example.test").assertExists()
        compose.onNodeWithText("198.51.100.0/24").assertDoesNotExist()
    }

    @Test
    fun scriptsScreenBackButtonInvokesNavigationCallback() {
        var backCalls = 0
        compose.setContent {
            ScriptsScreen(onBack = { backCalls++ })
        }

        compose.onNodeWithContentDescription(AppStrings.get("back")).assertExists().performClick()
        compose.runOnIdle { assertEquals(1, backCalls) }
    }
}
