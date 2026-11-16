package com.github.arca.android.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.LogRepository
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.GlassPalettes
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "zh-w400dp-h800dp-mdpi")
class CoreLogsLayoutRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun setUp() {
        LogRepository.clear()
        GlassDefaults.enabled = false
    }

    @After
    fun tearDown() {
        LogRepository.clear()
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    @Test
    fun debugFilterStaysOnOneLineIsSlightlyNarrowerAndRemainsAccessible() {
        showLogs()

        compose.onNodeWithTag("logs-level-filters").assertExists()
        compose.onNodeWithTag("logs-level-filter-DEBUG").performScrollTo().assertIsDisplayed()
        val filterViewport = compose.onNodeWithTag("logs-level-filters").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("logs-level-label-DEBUG", useUnmergedTree = true).assertIsDisplayed()
        val debugLabel = compose.onNodeWithTag("logs-level-label-DEBUG", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val debugChip = compose.onNodeWithTag("logs-level-filter-DEBUG").fetchSemanticsNode().boundsInRoot
        val errorChip = compose.onNodeWithTag("logs-level-filter-ERROR").fetchSemanticsNode().boundsInRoot
        assertTrue("debug label remains one line including its vertical padding: $debugLabel", debugLabel.height <= 36f)
        assertTrue("debug chip uses less horizontal padding without shrinking its label: debug=$debugChip error=$errorChip", debugChip.width < errorChip.width - 4f)
        assertTrue("debug chip remains fully accessible inside the horizontal-scroll viewport: chip=$debugChip viewport=$filterViewport", debugChip.left >= filterViewport.left && debugChip.right <= filterViewport.right)

        compose.onNodeWithTag("logs-level-filter-DEBUG").performClick().assertIsSelected()
    }

    @Test
    fun emptyStateSitsAboveTheReservedLogViewportAndLongLogsRemainScrollable() {
        showLogs()

        compose.onNodeWithTag("logs-console-empty-state").assertIsDisplayed()
        val console = compose.onNodeWithTag("logs-console-area").fetchSemanticsNode().boundsInRoot
        val empty = compose.onNodeWithTag("logs-console-empty-state").fetchSemanticsNode().boundsInRoot
        val reserved = compose.onNodeWithTag("logs-console-future-logs-space").fetchSemanticsNode().boundsInRoot
        assertTrue("the empty prompt stays within the upper console zone: empty=$empty console=$console", empty.bottom <= console.top + console.height * 0.46f + 1f)
        assertTrue("the lower half remains free for future logs: reserved=$reserved console=$console", reserved.top >= empty.bottom && reserved.height >= console.height * 0.50f)
        assertTrue("the reserved area remains inside the console", reserved.bottom <= console.bottom + 1f)

        repeat(60) { index -> LogRepository.addLog("info", "scroll-row-$index") }
        compose.waitForIdle()
        compose.onNodeWithTag("logs-console-log-viewport").assertIsDisplayed()
        compose.onNodeWithTag("logs-console-list").performScrollToNode(hasText("scroll-row-59"))
        compose.onNodeWithText("scroll-row-59").assertIsDisplayed()
    }

    private fun showLogs() {
        compose.setContent {
            MihomoTheme(ThemeMode.DARK) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    LogsScreen(onBack = {}, embeddedPanel = true)
                }
            }
        }
        compose.waitForIdle()
    }
}
