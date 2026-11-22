package com.github.arca.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.data.ConfigManager
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.components.LocalHapticFeedbackEnabled
import com.github.arca.android.ui.components.LocalHapticFeedbackPerformer
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.GlassPalettes
import com.github.arca.android.ui.glass.GlassPreset
import com.github.arca.android.ui.glass.GlassSettings
import com.github.arca.android.ui.glass.LocalGlassSettings
import com.github.arca.android.ui.glass.NexusGlassBackground
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import com.github.kr328.clash.core.model.Proxy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class DashboardQuickNodeActionsTest {
    @get:Rule val compose = createComposeRule()

    @After
    fun resetGlass() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    @Test
    fun addFromEmptyNodePanelClosesItWithoutNavigatingOrSelecting() {
        val fixture = Fixture(nodes = emptyList(), selected = "", group = "")
        show(fixture)
        openPanel()
        compose.onNodeWithTag("quick-node-add").assertIsDisplayed().assertTextContains("添加独立节点").performClick()
        assertPanelClosed()
        compose.runOnIdle {
            assertEquals(1, fixture.added)
            assertTrue(fixture.selections.isEmpty())
            assertTrue(fixture.management.isEmpty())
            assertEquals(0, fixture.profileNavigations)
        }
    }

    @Test
    fun addFromPopulatedNodePanelDoesNotReselectItsCurrentNode() {
        val fixture = Fixture()
        show(fixture)
        openPanel()
        compose.onNodeWithTag("quick-node-${fixture.selected.value}").assertExists()
        compose.onNodeWithTag("quick-node-add").performClick()
        assertPanelClosed()
        compose.runOnIdle {
            assertEquals(1, fixture.added)
            assertTrue(fixture.selections.isEmpty())
            assertTrue(fixture.management.isEmpty())
            assertEquals(0, fixture.profileNavigations)
        }
    }

    @Test
    fun selectedNodeRenameDispatchesExactRawNameWithoutReselecting() {
        assertSelectedAction("rename")
    }

    @Test
    fun selectedNodeDeleteDispatchesExactRawNameWithoutReselecting() {
        assertSelectedAction("delete")
    }

    @Test
    fun unselectedNodeSwitchesUsingRealGroupAndProxyThenClosesPanel() {
        val next = Proxy("🇯🇵 Next · raw", type = "Trojan", delay = 43)
        val group = "Select (123e4567-e89b-12d3-a456-426614174000)"
        val fixture = Fixture(nodes = listOf(LocalNode, next), group = group)
        show(fixture)
        openPanel()
        compose.onNodeWithTag("quick-node-${next.name}").performClick()
        assertPanelClosed()
        compose.runOnIdle {
            assertEquals(listOf(group to next), fixture.selections)
            assertTrue(fixture.management.isEmpty())
            assertEquals(0, fixture.added)
            assertEquals(0, fixture.profileNavigations)
        }
    }

    @Test
    fun nullOrEmptyAllowlistNeverEnablesManagementEvenForDefaultProfile() {
        val fixture = Fixture(allowed = null)
        show(fixture)
        openPanel()
        openMenu(LocalNode.name)
        assertActionsDisabled()
        compose.runOnIdle { fixture.allowed.value = emptySet() }
        compose.waitForIdle()
        // A permission refresh may dismiss the popup; reopening must still fail closed.
        if (compose.onAllNodesWithTag("quick-node-action-menu").fetchSemanticsNodes().isEmpty()) {
            openMenu(LocalNode.name)
        }
        assertActionsDisabled()
        assertNoMutationOrSelection(fixture)
    }

    @Test
    fun providerAndForeignNodesOutsideActiveDefaultAllowlistAreNotEditable() {
        val provider = Proxy("Provider node", type = "Shadowsocks")
        val remote = Proxy("Remote node", type = "Trojan")
        val fixture = Fixture(nodes = listOf(provider, remote), selected = provider.name, allowed = setOf(LocalNode.name))
        show(fixture)
        openPanel()
        openMenu(provider.name)
        assertActionsDisabled()
        compose.runOnIdle {
            fixture.profileId.value = "remote-profile"
            fixture.profileName.value = "远程配置"
            fixture.selected.value = remote.name
        }
        assertMenuCleared()
        openMenu(remote.name)
        assertActionsDisabled()
        assertNoMutationOrSelection(fixture)
    }

    @Test
    fun builtinsAndNonConcreteNodesStayDisabledEvenWhenTheirNamesAreAllowlisted() {
        val blocked = listOf(
            Proxy("direct", type = "Direct"),
            Proxy("REJECT", type = "Reject"),
            Proxy("REJECT-DROP", type = "Reject-Drop"),
            Proxy("PASS", type = "Pass"),
            Proxy("COMPATIBLE", type = "Compatible"),
            Proxy("GLOBAL", type = "Global"),
            Proxy("Nested selector", type = "Selector", isGroup = true),
            Proxy("Group placeholder", type = "Group"),
            Proxy("Built-in alias", type = "Reject_Drop")
        )
        val fixture = Fixture(nodes = listOf(blocked.first()), selected = blocked.first().name,
            allowed = blocked.map { it.name }.toSet())
        show(fixture)
        openPanel()
        blocked.forEachIndexed { index, node ->
            if (index > 0) {
                compose.runOnIdle {
                    fixture.nodes.value = listOf(node)
                    fixture.selected.value = node.name
                }
                assertMenuCleared()
            }
            openMenu(node.name)
            assertActionsDisabled()
            assertNoMutationOrSelection(fixture)
        }
    }

    @Test
    fun externalSelectionClearsMenuAndDoesNotDispatchItsOldAction() {
        val next = Proxy("Next", type = "Socks5")
        val fixture = Fixture(nodes = listOf(LocalNode, next), allowed = setOf(LocalNode.name, next.name))
        show(fixture)
        openPanel()
        openMenu(LocalNode.name)
        compose.runOnIdle { fixture.selected.value = next.name }
        assertMenuCleared()
        compose.onNodeWithTag("quick-node-selector-sheet").assertExists()
        openMenu(next.name)
        compose.onNodeWithTag("quick-node-rename").performClick()
        assertPanelClosed()
        compose.runOnIdle {
            assertEquals(listOf("rename" to next.name), fixture.management)
            assertTrue(fixture.selections.isEmpty())
        }
    }

    @Test
    fun externalProfileAndGroupChangesClearMenuEvenWhenNodeNameIsUnchanged() {
        val fixture = Fixture()
        show(fixture)
        openPanel()
        openMenu(LocalNode.name)
        compose.runOnIdle {
            fixture.profileId.value = "other-profile"
            fixture.profileName.value = "另一个配置"
            fixture.allowed.value = emptySet()
        }
        assertMenuCleared()
        openMenu(LocalNode.name)
        assertActionsDisabled()
        compose.runOnIdle { fixture.group.value = "Other select" }
        assertMenuCleared()
        compose.onNodeWithTag("quick-node-selector-sheet").assertExists()
        assertNoMutationOrSelection(fixture)
    }

    @Test
    fun removingSelectedNodeClearsItsMenuWithoutWritingOrSelecting() {
        val fixture = Fixture()
        show(fixture)
        openPanel()
        openMenu(LocalNode.name)
        compose.runOnIdle { fixture.nodes.value = emptyList() }
        assertMenuCleared()
        compose.onNodeWithTag("quick-node-${LocalNode.name}").assertDoesNotExist()
        compose.onNodeWithTag("quick-node-add").assertExists()
        assertNoMutationOrSelection(fixture)
    }

    @Test
    fun replacingSelectedNodeUnderSameNameClearsItsStaleMenu() {
        val fixture = Fixture()
        show(fixture)
        openPanel()
        openMenu(LocalNode.name)
        compose.runOnIdle { fixture.nodes.value = listOf(Proxy(LocalNode.name, type = "Trojan")) }
        assertMenuCleared()
        compose.onNodeWithTag("quick-node-${LocalNode.name}").assertExists()
        assertNoMutationOrSelection(fixture)
    }

    @Test
    fun delayOnlyRefreshKeepsCurrentNodeActionMenuOpen() {
        val fixture = Fixture()
        show(fixture)
        openPanel()
        openMenu(LocalNode.name)
        compose.runOnIdle { fixture.nodes.value = listOf(Proxy(LocalNode.name, type = LocalNode.type, delay = 78)) }
        compose.waitForIdle()
        compose.onNodeWithTag("quick-node-action-menu").assertExists()
        compose.onNodeWithTag("quick-node-rename").assertIsEnabled()
        compose.onNodeWithTag("quick-node-delete").assertIsEnabled()
        assertNoMutationOrSelection(fixture)
    }

    @Test
    fun dismissingPanelClearsMenuBeforeNextOpen() {
        val fixture = Fixture()
        show(fixture)
        openPanel()
        openMenu(LocalNode.name)
        compose.onNodeWithContentDescription("关闭").performClick()
        assertPanelClosed()
        openPanel()
        compose.onNodeWithTag("quick-node-action-menu").assertDoesNotExist()
        assertNoMutationOrSelection(fixture)
    }

    @Test
    fun otherShortcutsRetainDirectVpnAndInPanelProfileSemantics() {
        val fixture = Fixture(shortcuts = listOf("vpn", "profiles"))
        show(fixture)
        compose.onNodeWithTag("shortcut-vpn").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("quick-node-selector-sheet").assertDoesNotExist()
        compose.onNodeWithTag("shortcut-panel-vpn-sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, fixture.vpnToggles) }
        compose.onNodeWithTag("shortcut-profiles").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("shortcut-panel-profiles-sheet").assertExists()
        compose.onNodeWithTag("quick-profile-add").assertExists()
        compose.onNodeWithTag("quick-node-add").assertDoesNotExist()
        compose.onNodeWithTag("quick-node-action-menu").assertDoesNotExist()
        assertNoMutationOrSelection(fixture)
    }

    private fun assertSelectedAction(action: String) {
        val fixture = Fixture()
        show(fixture)
        openPanel()
        openMenu(LocalNode.name)
        compose.onNodeWithTag("quick-node-rename").assertIsEnabled().assertTextEquals("重命名")
        compose.onNodeWithTag("quick-node-delete").assertIsEnabled().assertTextEquals("删除")
        compose.runOnIdle { assertTrue("opening current-node actions must not reselect", fixture.selections.isEmpty()) }
        compose.onNodeWithTag("quick-node-$action").performClick()
        assertPanelClosed()
        compose.runOnIdle {
            assertEquals(listOf(action to LocalNode.name), fixture.management)
            assertTrue(fixture.selections.isEmpty())
            assertEquals(0, fixture.added)
            assertEquals(0, fixture.profileNavigations)
        }
    }

    private fun openPanel() {
        compose.onNodeWithTag("shortcut-nodes").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("quick-node-selector-sheet").assertExists()
    }

    private fun openMenu(name: String) {
        compose.onNodeWithTag("quick-node-$name").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("quick-node-action-menu").assertExists()
    }

    private fun assertActionsDisabled() {
        compose.onNodeWithTag("quick-node-rename").assertIsNotEnabled()
        compose.onNodeWithTag("quick-node-delete").assertIsNotEnabled()
    }

    private fun assertMenuCleared() {
        compose.waitForIdle()
        compose.onNodeWithTag("quick-node-action-menu").assertDoesNotExist()
        compose.onNodeWithTag("quick-node-rename").assertDoesNotExist()
        compose.onNodeWithTag("quick-node-delete").assertDoesNotExist()
    }

    private fun assertPanelClosed() {
        compose.waitForIdle()
        compose.onNodeWithTag("quick-node-selector-sheet").assertDoesNotExist()
        compose.onNodeWithTag("quick-node-action-menu").assertDoesNotExist()
    }

    private fun assertNoMutationOrSelection(fixture: Fixture) {
        compose.runOnIdle {
            assertTrue(fixture.management.isEmpty())
            assertTrue(fixture.selections.isEmpty())
            assertEquals(0, fixture.added)
            assertEquals(0, fixture.profileNavigations)
        }
    }

    private fun show(fixture: Fixture) {
        GlassDefaults.enabled = true
        val scrollState = ScrollState(0)
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "zh",
                    LocalGlassSettings provides GlassSettings(glassPreset = GlassPreset.UNIFIED),
                    LocalHapticFeedbackEnabled provides { false },
                    LocalHapticFeedbackPerformer provides {}
                ) {
                    ProvideGlassTheme {
                        NexusGlassBackground {
                            DashboardScreen(
                                scrollState = scrollState,
                                vpnState = VpnState(status = VpnState.Status.STOPPED),
                                currentMode = TunnelMode.RULE,
                                activeProfileName = fixture.profileName.value,
                                selectedProfileId = fixture.profileId.value,
                                mainGroupName = fixture.group.value,
                                mainGroupProxies = fixture.nodes.value,
                                activeNodeName = fixture.selected.value,
                                totalNodes = fixture.nodes.value.size,
                                totalRules = 0,
                                visibleShortcuts = fixture.shortcuts.toSet(),
                                shortcutOrder = fixture.shortcuts,
                                onToggleVpn = { fixture.vpnToggles++ },
                                onModeSelected = {},
                                onSelectProxy = { group, proxy -> fixture.selections += group to proxy },
                                onHealthCheck = {},
                                onNavigateToProfiles = { fixture.profileNavigations++ },
                                onNavigateToAppRouting = {},
                                onNavigateToLogs = {},
                                onUpdateVisibleShortcuts = {},
                                onUpdateShortcutOrder = {},
                                onAddNode = { fixture.added++ },
                                onRenameNode = { fixture.management += "rename" to it },
                                onDeleteNode = { fixture.management += "delete" to it },
                                editableNodeNames = fixture.allowed.value
                            )
                        }
                    }
                }
            }
        }
    }

    private class Fixture(
        nodes: List<Proxy> = listOf(LocalNode),
        selected: String = LocalNode.name,
        group: String = "Select",
        allowed: Set<String>? = setOf(LocalNode.name),
        val shortcuts: List<String> = listOf("nodes")
    ) {
        val nodes = mutableStateOf(nodes)
        val selected = mutableStateOf(selected)
        val group = mutableStateOf(group)
        val allowed = mutableStateOf(allowed)
        val profileId = mutableStateOf<String?>(ConfigManager.DEFAULT_PROFILE_ID)
        val profileName = mutableStateOf("我的节点")
        val selections = mutableListOf<Pair<String, Proxy>>()
        val management = mutableListOf<Pair<String, String>>()
        var added = 0
        var profileNavigations = 0
        var vpnToggles = 0
    }

    private companion object {
        val LocalNode = Proxy("🇭🇰 Local · raw name", type = "Socks5", delay = 21)
    }
}
