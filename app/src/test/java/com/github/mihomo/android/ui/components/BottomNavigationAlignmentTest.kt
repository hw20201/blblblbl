package com.github.mihomo.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.UiStyle
import com.github.mihomo.android.ui.screens.GlassThemeSection
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h1000dp-mdpi")
class BottomNavigationAlignmentTest {
    @get:Rule val compose = createComposeRule()

    private fun assertAligned() {
        val cells = (0..2).map { compose.onNodeWithTag("bottom-tab-$it").fetchSemanticsNode().boundsInRoot }
        val icons = (0..2).map { compose.onNodeWithTag("bottom-icon-$it", true).fetchSemanticsNode().boundsInRoot }
        val labels = (0..2).map { compose.onNodeWithTag("bottom-label-$it", true).fetchSemanticsNode().boundsInRoot }
        val baselines = (0..2).map { index ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("bottom-label-$index", true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            assertEquals(1, layouts.single().lineCount)
            labels[index].top + layouts.single().firstBaseline
        }
        for (i in 0..2) {
            assertEquals(cells[0].width, cells[i].width, 1f)
            assertEquals(cells[0].top, cells[i].top, 0.5f)
            assertEquals(cells[0].height, cells[i].height, 0.5f)
            assertEquals(icons[0].top, icons[i].top, 0.5f)
            assertEquals(labels[0].top, labels[i].top, 0.5f)
            assertEquals(baselines[0], baselines[i], 0.5f)
            assertEquals(cells[i].center.x, icons[i].center.x, 0.5f)
            assertEquals(cells[i].center.x, labels[i].center.x, 0.5f)
            assertEquals(3f, labels[i].top - icons[i].bottom, 0.5f)
            assertTrue(cells[i].height >= 48f)
        }
        assertEquals(cells[1].center.x - cells[0].center.x, cells[2].center.x - cells[1].center.x, 1f)
    }

    @Test fun equalCellsAndBaselinesSurviveStylesThemesSelectionAndFontScaling() {
        val style = mutableStateOf(UiStyle.CUPERTINO)
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val font = mutableFloatStateOf(1f)
        val width = mutableIntStateOf(360)
        val selected = mutableIntStateOf(0)
        compose.setContent {
            MihomoTheme(uiStyle = style.value, themeMode = mode.value) {
                CompositionLocalProvider(LocalDensity provides Density(1f, font.floatValue), LocalAppLanguage provides "zh") {
                    Box(Modifier.width(width.intValue.dp)) {
                        LoonFloatingBottomBar(selected.intValue, { selected.intValue = it })
                    }
                }
            }
        }
        for (targetStyle in UiStyle.entries) for (targetMode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            for (scale in listOf(1f, 1.3f, 2f)) {
                compose.runOnIdle {
                    style.value = targetStyle; mode.value = targetMode
                    font.floatValue = scale; width.intValue = if (scale == 2f) 320 else 360
                }
                for (i in 0..2) {
                    compose.onNodeWithTag("bottom-tab-$i").performClick().assertIsSelected()
                    for (other in 0..2) if (other != i) compose.onNodeWithTag("bottom-tab-$other").assertIsNotSelected()
                    assertAligned()
                }
            }
        }
        listOf("仪表", "策略", "设置").forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test fun settingsPreviewUsesThreeRealInteractiveTabCells() {
        val style = mutableStateOf(UiStyle.CUPERTINO)
        compose.setContent {
            MihomoTheme(uiStyle = style.value) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalAppLanguage provides "zh") {
                    Box(Modifier.width(360.dp)) { GlassThemeSection(0.5f, {}) }
                }
            }
        }
        for (targetStyle in UiStyle.entries) {
            compose.runOnIdle { style.value = targetStyle }
            for (i in 0..2) {
                compose.onNodeWithTag("bottom-tab-$i").performClick().assertIsSelected()
                assertAligned()
            }
        }
    }
}
