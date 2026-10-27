package com.github.mihomo.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.activity.ComponentActivity
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.glass.GlassSettings
import com.github.mihomo.android.ui.glass.ProvideGlassTheme
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class ImmersiveGlassSettingsScrollTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun appearanceScrollsContinuouslyBehindPinnedFrostedTitle() {
        val appearanceListState = LazyListState()
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        Box(Modifier.fillMaxSize().background(Color(0xFFB8D5F2))) {
                            AppearanceScreen(
                                themeMode = ThemeMode.LIGHT,
                                onThemeModeChanged = {},
                                onLanguageChanged = {},
                                showGroupIcons = false,
                                onShowGroupIconsChanged = {},
                                nodeColumns = 1,
                                onNodeColumnsChanged = {},
                                onBack = {},
                                listState = appearanceListState
                            )
                        }
                    }
                }
            }
        }

        val scroll = compose.onNodeWithTag("appearance-scroll-content")
        val title = compose.onNodeWithTag("navigation-title-capsule")
        val header = compose.onNodeWithTag("appearance-pinned-header")
        scroll.assertExists()
        title.assertIsDisplayed()
        val scrollBounds = scroll.fetchSemanticsNode().boundsInRoot
        val headerBefore = header.fetchSemanticsNode().boundsInRoot
        val titleBefore = title.fetchSemanticsNode().boundsInRoot
        assertTrue("the scroll viewport must start at the top edge", scrollBounds.top <= 1f)
        assertTrue("the scroll viewport must extend below the pinned title", scrollBounds.bottom > headerBefore.bottom)

        val firstSection = compose.onNodeWithText("外观主题").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "the first settings group should clear the pinned title by the added spacing",
            firstSection.top >= headerBefore.bottom + 20f
        )
        val firstVisibleItemBefore = appearanceListState.firstVisibleItemIndex
        // Reveal a distant lazy item through the page's single scroll container. The first
        // section may be disposed after the jump, so assert list-state movement rather than
        // querying an off-screen node that LazyColumn no longer composes.
        scroll.performScrollToNode(hasText("显示策略组远程图标"))
        compose.onNodeWithText("显示策略组远程图标").assertIsDisplayed()
        compose.waitForIdle()
        assertTrue("revealing a later setting should advance the page scroll", appearanceListState.firstVisibleItemIndex > firstVisibleItemBefore)
        val titleAfter = title.fetchSemanticsNode().boundsInRoot
        assertEquals("the title capsule stays pinned while content scrolls", titleBefore.top, titleAfter.top, 1f)
        title.assertIsDisplayed()
    }

    @Test fun glassEffectsKeepsAllControlsWhileContentScrollsBehindFrostedTitle() {
        val settings = mutableStateOf(GlassSettings.DEFAULT)
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        Box(Modifier.fillMaxSize().background(Color(0xFFD8C7F0))) {
                            GlassEffectsScreen(
                                settings = settings.value,
                                onSettingsChanged = { settings.value = it },
                                onBack = {}
                            )
                        }
                    }
                }
            }
        }

        val scroll = compose.onNodeWithTag("glass-effects-scroll")
        val title = compose.onNodeWithTag("glass-effects-title-capsule")
        val header = compose.onNodeWithTag("glass-effects-pinned-header")
        scroll.assertExists()
        title.assertIsDisplayed()
        compose.onNodeWithTag("glass-effects-preview").assertExists()
        compose.onNodeWithTag("glass-global-refraction").assertExists()
        compose.onNodeWithTag("glass-surface-override-switch").assertExists()
        compose.onNodeWithTag("glass-motion-gain").assertExists()

        val viewport = scroll.fetchSemanticsNode().boundsInRoot
        val titleBefore = title.fetchSemanticsNode().boundsInRoot
        val headerBounds = header.fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithText("实时预览")
        val previewBefore = preview.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "the first glass setting content should clear the pinned title by the added spacing",
            previewBefore.top >= headerBounds.bottom + 24f
        )
        assertTrue("the full-page scroll viewport begins at the screen edge", viewport.top <= 1f)
        assertTrue("the viewport passes behind the pinned title", viewport.bottom > headerBounds.bottom)

        scroll.performTouchInput { swipeUp() }
        compose.waitForIdle()
        val previewAfter = preview.fetchSemanticsNode().boundsInRoot
        val titleAfter = title.fetchSemanticsNode().boundsInRoot
        assertTrue("the preview should move when the page is swiped", previewAfter.top < previewBefore.top)
        assertEquals("the title capsule remains pinned", titleBefore.top, titleAfter.top, 1f)

        // Exercise the override branch as well as the shared/global controls.
        compose.onNodeWithTag("glass-surface-override-switch").performScrollTo().performClick()
        compose.onNodeWithTag("glass-surface-refraction").assertExists()
        compose.onNodeWithTag("glass-surface-dispersion").assertExists()
        compose.onNodeWithTag("glass-surface-edge-width").assertExists()
        compose.onNodeWithTag("glass-surface-edge-light").assertExists()
        compose.onNodeWithText("恢复全部默认").performScrollTo().assertIsDisplayed()
        title.assertIsDisplayed()
    }
}
