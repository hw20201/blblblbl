package com.github.mihomo.android.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.glass.ProvideGlassTheme
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-mdpi")
class DnsScreenGlassTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dnsGlassPanelsKeepContentAndFakeIpSwitchAction() {
        val previousGlassSetting = GlassDefaults.enabled
        val changes = mutableListOf<Boolean>()
        try {
            GlassDefaults.enabled = true
            compose.setContent {
                MihomoTheme(ThemeMode.LIGHT) {
                    CompositionLocalProvider(LocalAppLanguage provides "zh") {
                        ProvideGlassTheme {
                            DnsScreen(
                                fakeIpEnabled = false,
                                onFakeIpEnabledChanged = { changes += it },
                                onBack = {}
                            )
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.onNodeWithTag("dns-panel-status").assertIsDisplayed()
            compose.onNodeWithTag("dns-panel-diagnostic").assertIsDisplayed()
            compose.onNodeWithText("DNS 实时解析诊断").assertIsDisplayed()
            val list = compose.onNodeWithTag("dns-page-list")
            list.performScrollToNode(hasTestTag("dns-panel-upstream"))
            compose.onNodeWithTag("dns-panel-upstream").assertIsDisplayed()
            list.performScrollToNode(hasTestTag("dns-panel-bypass"))
            compose.onNodeWithTag("dns-panel-bypass").assertIsDisplayed()
            list.performScrollToNode(hasTestTag("dns-panel-status"))
            compose.onNodeWithTag("dns-fakeip-switch").performClick()
            compose.runOnIdle { assertEquals(listOf(true), changes) }
        } finally {
            GlassDefaults.enabled = previousGlassSetting
        }
    }
    @Test fun dnsListScrollsUnderPinnedTransparentHeader() {
        val previousGlassSetting = GlassDefaults.enabled
        try {
            GlassDefaults.enabled = true
            compose.setContent {
                MihomoTheme(ThemeMode.LIGHT) {
                    CompositionLocalProvider(LocalAppLanguage provides "zh") {
                        ProvideGlassTheme {
                            DnsScreen(fakeIpEnabled = true, onFakeIpEnabledChanged = {}, onBack = {})
                        }
                    }
                }
            }
            compose.waitForIdle()
            val list = compose.onNodeWithTag("dns-page-list")
            val header = compose.onNodeWithTag("dns-page-header")
            header.assertIsDisplayed()
            val initialHeaderTop = header.fetchSemanticsNode().boundsInRoot.top
            assertEquals("DNS list begins at the screen edge behind its overlay header", 0f,
                list.fetchSemanticsNode().boundsInRoot.top, 1f)
            list.performScrollToNode(hasTestTag("dns-panel-bypass"))
            compose.waitForIdle()
            compose.onNodeWithTag("dns-panel-bypass").assertIsDisplayed()
            header.assertIsDisplayed()
            assertEquals("Pinned DNS header stays in place while list scrolls", initialHeaderTop,
                header.fetchSemanticsNode().boundsInRoot.top, 1f)
        } finally {
            GlassDefaults.enabled = previousGlassSetting
        }
    }

}
