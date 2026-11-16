package com.github.arca.android.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Subscriptions may repeat a node name; lazy lists crash on duplicate keys, so row keys must be unique. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class ProxiesDuplicateNamesTest {
    @get:Rule val compose = createComposeRule()
    private val originalGlassEnabled = GlassDefaults.enabled

    @After fun restoreGlass() { GlassDefaults.enabled = originalGlassEnabled }

    @Test fun groupRowKeysStayUniqueWhenFirstNamesRepeat() {
        val proxies = listOf("A", "B", "A", "B", "A").map { Proxy(it) }
        for (columns in 1..3) {
            val keys = proxyGroupRowKeys("G", proxies, columns)
            assertEquals((proxies.size + columns - 1) / columns, keys.size)
            assertEquals("keys unique at $columns columns: $keys", keys.size, keys.toSet().size)
        }
        // First occurrence keeps the plain name-based key so reorders still animate the node.
        assertEquals("grp_node_1:G:A", proxyGroupRowKeys("G", proxies, 1).first())
        assertEquals(proxyGroupRowKeys("G", proxies, 2), proxyGroupRowKeys("G", proxies, 2))
    }

    @Test fun groupRowKeysAreScopedByGroupName() {
        val proxies = listOf(Proxy("A"))
        val first = proxyGroupRowKeys("G1", proxies, 1)
        val second = proxyGroupRowKeys("G22", proxies, 1)
        assertEquals(emptySet<String>(), first.toSet().intersect(second.toSet()))
    }

    @Test fun tabRowKeysStayUnique() {
        val rows = listOf("A", "B", "A", "A").map { listOf(Proxy(it)) }
        val keys = proxyRowKeysOf(rows)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun expandingGroupWithRepeatedNodeNamesDoesNotCrash() {
        GlassDefaults.enabled = false
        val nodes = listOf("Dup", "Other", "Dup", "Dup", "Other", "Dup").map { Proxy(it, delay = 20) }
        val expanded = mutableStateMapOf<String, Boolean>()
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    val group = ProxyGroup("select", nodes, "Dup")
                    ProxiesScreen(
                        groups = listOf("Dups"),
                        groupMap = mapOf("Dups" to group),
                        currentGroup = group,
                        selectedGroupName = "Dups",
                        currentViewMode = "LIST",
                        nodeColumns = 1,
                        expandedGroups = expanded,
                        onViewModeChanged = {},
                        onShowGroupIconsChanged = {},
                        onSelectGroup = {},
                        onSelectProxy = { _, _ -> },
                        onHealthCheck = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("policy-group-header-Dups").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("policy-group-list")
    }
}
