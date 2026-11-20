package com.github.arca.android.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.data.ParsedRule
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "zh-w360dp-h800dp-mdpi")
class RulesTextLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun searchAndMatchTextFitAtNormalAndLargerFontSizes() {
        val fontScale = mutableStateOf(1f)
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(
                    LocalDensity provides Density(density, fontScale.value),
                    LocalAppLanguage provides "zh"
                ) {
                    RulesScreen(
                        rules = listOf(ParsedRule(type = "MATCH", payload = "", target = "主策略")),
                        totalRules = 1, tunnelMode = TunnelMode.RULE,
                        onBack = {}, embeddedPanel = true
                    )
                }
            }
        }
        for (scale in listOf(1f, 1.3f, 1.6f)) {
            compose.runOnIdle { fontScale.value = scale }
            compose.waitForIdle()
            val labels = listOf("搜索规则类型、域名、目标策略...", "MATCH", "MATCH ALL")
            for (label in labels) {
                val count = compose.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().size
                assertTrue("$label exists at font scale $scale", count > 0)
                repeat(count) { index ->
                    val results = mutableListOf<TextLayoutResult>()
                    compose.onAllNodesWithText(label, useUnmergedTree = true)[index]
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                    assertTrue("$label provides layout at font scale $scale", results.isNotEmpty())
                    assertTrue("$label fits its height at font scale $scale: ${results.first().size}",
                        !results.first().didOverflowHeight)
                }
            }
            val header = compose.onNodeWithTag("navigation-bar-transparent-row").fetchSemanticsNode().boundsInRoot
            val search = compose.onNodeWithTag("shortcut-search-field").fetchSemanticsNode().boundsInRoot
            assertTrue("search stays below the actual header at font scale $scale: header=$header search=$search",
                search.top >= header.bottom)
        }
    }
}
