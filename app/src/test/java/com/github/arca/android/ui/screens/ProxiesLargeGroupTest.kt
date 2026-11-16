package com.github.arca.android.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class ProxiesLargeGroupTest {
    @get:Rule val compose = createComposeRule()
    private val originalGlassEnabled = GlassDefaults.enabled

    @After fun restoreGlass() { GlassDefaults.enabled = originalGlassEnabled }

    @Test fun expandingFiveHundredNodesKeepsCompositionBoundedAndLastNodeSelectableAtEveryColumnCount() {
        // Exercise actual screen/layout/semantics without GPU material cost obscuring laziness.
        GlassDefaults.enabled = false
        val nodes = List(500) { Proxy("Bulk node $it", delay = 10 + it) }
        val expanded = mutableStateMapOf<String, Boolean>()
        val columns = mutableIntStateOf(1)
        val selected = mutableStateOf(nodes.first().name)
        var selection: Pair<String, String>? = null
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    val group = ProxyGroup("select", nodes, selected.value)
                    ProxiesScreen(
                        groups = listOf("Large", "Following"),
                        groupMap = mapOf("Large" to group, "Following" to ProxyGroup("select")),
                        currentGroup = group,
                        selectedGroupName = "Large",
                        currentViewMode = "LIST",
                        nodeColumns = columns.intValue,
                        expandedGroups = expanded,
                        onViewModeChanged = {},
                        onShowGroupIconsChanged = {},
                        onSelectGroup = {},
                        onSelectProxy = { name, proxy ->
                            selection = name to proxy.name
                            selected.value = proxy.name
                        },
                        onHealthCheck = {}
                    )
                }
            }
        }
        assertEquals(0, composedNodeCount())
        for (columnCount in 1..3) {
            compose.runOnIdle { columns.intValue = columnCount }
            compose.onNodeWithTag("policy-group-header-Large").performClick()
            compose.waitForIdle()
            val startCount = composedNodeCount()
            println("500-node policy group, $columnCount columns: $startCount composed node semantics at start")
            assertTrue("expanded group composes visible rows, not all 500 nodes ($startCount)", startCount in 1..80)
            compose.onNodeWithTag("proxy-card-Bulk node 499").assertDoesNotExist()

            val rowCount = (nodes.size + columnCount - 1) / columnCount
            compose.onNodeWithTag("policy-group-list").performScrollToIndex(rowCount)
            compose.onNodeWithText("Bulk node 499").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals("Large" to "Bulk node 499", selection) }
            val endCount = composedNodeCount()
            println("500-node policy group, $columnCount columns: $endCount composed node semantics at end")
            assertTrue("scrolled group keeps bounded composition ($endCount)", endCount in 1..80)
            compose.onNodeWithTag("policy-group-header-Following").assertIsDisplayed()

            compose.onNodeWithTag("policy-group-list").performScrollToIndex(0)
            compose.onNodeWithTag("policy-group-header-Large").performClick()
            compose.waitForIdle()
            assertEquals("collapse removes all node cards", 0, composedNodeCount())
        }
    }

    private fun composedNodeCount(): Int = (0 until 500).sumOf { index ->
        compose.onAllNodesWithTag("proxy-card-Bulk node $index", useUnmergedTree = true)
            .fetchSemanticsNodes().size
    }
}
