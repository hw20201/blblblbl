package com.github.arca.android.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class ProxiesAddedNodeFocusTest {
    @get:Rule val compose = createComposeRule()
    private val originalGlassEnabled = GlassDefaults.enabled
    @After fun restoreGlass() { GlassDefaults.enabled = originalGlassEnabled }

    @Test fun delayedAddedCardIsRevealedBeforeProviderTailInBothLayoutsAndEveryColumnCount() {
        GlassDefaults.enabled = false
        val groups = listOf("Hidden", "Collapsed", "Expanded", "Target", "Following")
        val oldNodes = List(45) { Proxy("Old $it") }
        val providerTail = List(90) { Proxy("Provider $it") }
        val base = linkedMapOf(
            "Hidden" to ProxyGroup("select", List(80) { Proxy("Hidden $it") }, hidden = true),
            "Collapsed" to ProxyGroup("select", List(80) { Proxy("Collapsed $it") }),
            "Expanded" to ProxyGroup("select", List(31) { Proxy("Expanded $it") }),
            "Target" to ProxyGroup("select", oldNodes + providerTail),
            "Following" to ProxyGroup("select", listOf(Proxy("Following")))
        )
        val display = mutableStateOf<Map<String, ProxyGroup>>(base)
        val selectedGroup = mutableStateOf("Collapsed")
        val mode = mutableStateOf("TABS")
        val columns = mutableIntStateOf(1)
        val expanded = mutableStateMapOf("Hidden" to true, "Expanded" to true)
        val request = mutableStateOf<ProxyNodeFocusRequest?>(null)
        var consumed: ProxyNodeFocusRequest? = null
        var selection: String? = null
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProxiesScreen(
                        groups = groups, groupMap = display.value,
                        currentGroup = display.value[selectedGroup.value], selectedGroupName = selectedGroup.value,
                        currentViewMode = mode.value, nodeColumns = columns.intValue,
                        expandedGroups = expanded,
                        onViewModeChanged = {}, onShowGroupIconsChanged = {},
                        onSelectGroup = { selectedGroup.value = it },
                        onSelectProxy = { _, proxy -> selection = proxy.name }, onHealthCheck = {},
                        nodeFocusRequest = request.value,
                        onNodeFocusConsumed = { consumed = it; request.value = null }
                    )
                }
            }
        }
        for (view in listOf("TABS", "LIST")) for (column in 1..3) {
            val name = "Added $view $column (2)"
            val focus = ProxyNodeFocusRequest("builtin-default", "Target", listOf(name))
            compose.runOnIdle {
                display.value = base
                selectedGroup.value = "Collapsed"
                mode.value = view
                columns.intValue = column
                expanded.remove("Target")
                consumed = null
                selection = null
                request.value = focus
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertNull("old display must retain the request", consumed)
                assertEquals("Collapsed", selectedGroup.value)
                display.value = base + ("Target" to ProxyGroup("select", oldNodes + Proxy(name) + providerTail))
            }
            compose.waitForIdle()
            compose.waitUntil(5_000) { consumed == focus }
            compose.onNodeWithTag("proxy-card-$name").assertIsDisplayed().performClick()
            compose.onNodeWithTag("proxy-card-Provider 89").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals("Target", selectedGroup.value)
                assertTrue(expanded["Target"] == true)
                assertEquals(name, selection)
            }
        }
    }

    @Test fun sameNameProviderCardCannotBorrowAnotherGroupsInlineActions() {
        GlassDefaults.enabled = false
        val inline = ProxyGroup("select", listOf(Proxy("HK", type = "ss")))
        val provider = ProxyGroup("select", listOf(Proxy("HK", type = "trojan")))
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProxiesScreen(
                        groups = listOf("Inline", "Provider"), groupMap = mapOf("Inline" to inline, "Provider" to provider),
                        currentGroup = inline, selectedGroupName = "Inline", currentViewMode = "LIST",
                        expandedGroups = mutableStateMapOf("Inline" to true, "Provider" to true),
                        editableNodeNames = setOf("HK"), editableNodeNamesByGroup = mapOf("Inline" to setOf("HK"), "Provider" to emptySet()),
                        onEditNode = {}, onRenameNode = {}, onDeleteNode = {},
                        onViewModeChanged = {}, onShowGroupIconsChanged = {}, onSelectGroup = {}, onSelectProxy = { _, _ -> }, onHealthCheck = {}
                    )
                }
            }
        }
        val cards = compose.onAllNodesWithTag("proxy-card-HK", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(2, cards.size)
        assertTrue(cards[0].config.contains(SemanticsActions.OnLongClick))
        assertFalse(cards[1].config.contains(SemanticsActions.OnLongClick))
    }
}
