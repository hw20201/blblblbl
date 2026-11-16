package com.github.arca.android.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Identical source for V93/V94: report initial composition without assuming either list structure. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class ProxiesCompositionBaselineTest {
    @get:Rule val compose = createComposeRule()
    private val originalGlassEnabled = GlassDefaults.enabled

    @After fun restoreGlass() { GlassDefaults.enabled = originalGlassEnabled }

    @Test fun reportInitiallyExpandedFiveHundredNodeComposition() {
        // Compare composition/layout independently of the native glass material renderer.
        GlassDefaults.enabled = false
        val nodes = List(500) { Proxy("Bulk node $it", delay = 10 + it) }
        val group = ProxyGroup("select", nodes, now = nodes.first().name)
        val expanded = mutableStateMapOf("Large" to true)
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProxiesScreen(
                        groups = listOf("Large"),
                        groupMap = mapOf("Large" to group),
                        currentGroup = group,
                        selectedGroupName = "Large",
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
        compose.waitForIdle()
        val count = compose.onAllNodes(
            SemanticsMatcher("bulk proxy card") { node ->
                node.config.getOrElse(SemanticsProperties.TestTag) { "" }
                    .startsWith("proxy-card-Bulk node ")
            },
            useUnmergedTree = true
        ).fetchSemanticsNodes().size
        println("PROXIES_COMPOSITION_BASELINE viewport=400x800 nodes=500 mode=LIST expanded=true columns=1 glass=false composedNodeSemantics=$count")
    }
}
