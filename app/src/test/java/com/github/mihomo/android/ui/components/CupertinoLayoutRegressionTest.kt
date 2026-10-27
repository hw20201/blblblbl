package com.github.mihomo.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.screens.AppearanceScreen
import com.github.mihomo.android.ui.theme.AppStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CupertinoLayoutRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun navigationBarGrowsToContainLargeTitleAndSubtitle() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                Box(Modifier.width(320.dp)) {
                    CupertinoNavBar(
                        title = "Core logs", subtitle = "200 entries",
                        modifier = Modifier.testTag("nav"),
                        showBackButton = true, onBack = {},
                        actions = { Text("+") },
                    )
                }
            }
        }
        val bar = compose.onNodeWithTag("nav").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("Core logs", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val subtitle = compose.onNodeWithText("200 entries", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(title.top >= bar.top)
        assertTrue(subtitle.bottom <= bar.bottom)
        assertTrue(title.bottom <= subtitle.top)
    }

    @Test fun longCoreVersionCannotConsumeTheSettingsTitleOnNarrowScreens() {
        val version = "v1.99.0-custom-" + "abcdef0123456789".repeat(64)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.5f)) {
                Box(Modifier.width(280.dp)) {
                    CupertinoCell(title = "Core version", value = version, modifier = Modifier.testTag("cell"))
                }
            }
        }
        val title = compose.onNodeWithText("Core version", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val value = compose.onNodeWithText(version, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("title=$title value=$value", title.width > 0f)
        assertTrue(value.width > 0f)
        assertTrue(title.right <= value.left + 1f || title.bottom <= value.top + 1f)
    }

    @Test fun switchingAppLanguageThenSystemDoesNotOverwriteTheSystemLocale() {
        val originalLocale = Locale.getDefault()
        val selected = mutableListOf<String>()
        compose.setContent {
            AppearanceScreen(
                themeMode = ThemeMode.SYSTEM, onThemeModeChanged = {},
                onLanguageChanged = { selected += it },
                showGroupIcons = true, onShowGroupIconsChanged = {},
                nodeColumns = 2, onNodeColumnsChanged = {}, onBack = {},
            )
        }
        compose.onNodeWithTag("appearance-scroll-content").performScrollToNode(hasText("简体中文"))
        compose.onNodeWithText("简体中文").performClick()
        compose.onAllNodesWithText(AppStrings.get("theme_system")).onLast().performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("zh", "SYSTEM"), selected)
            assertEquals(originalLocale, Locale.getDefault())
        }
    }

    @Test fun floatingBottomNavigationFitsNarrowWidthAndKeepsTabCallbacks() {
        var selected = -1
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                Box(Modifier.width(280.dp)) {
                    LoonFloatingBottomBar(0, { selected = it }, Modifier.testTag("bar"))
                }
            }
        }
        val bar = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
        listOf("nav_dashboard", "nav_proxies", "nav_settings").forEach { key ->
            val label = compose.onNodeWithText(AppStrings.get(key), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue(label.left >= bar.left)
            assertTrue(label.right <= bar.right)
        }
        compose.onNodeWithText(AppStrings.get("nav_settings")).performClick()
        compose.runOnIdle { assertEquals(2, selected) }
    }
}
