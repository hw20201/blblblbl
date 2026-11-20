package com.github.arca.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
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
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.data.TunStack
import com.github.arca.android.ui.components.ShortcutTouchVariant
import com.github.arca.android.ui.glass.GlassSettings
import com.github.arca.android.ui.glass.NexusGlassBackground
import com.github.arca.android.ui.glass.rememberArcaGlasmioBackdrop
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import androidx.compose.ui.unit.dp
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

    @Test fun appearanceScrollsContinuouslyBehindPinnedPlainTitle() {
        val appearanceListState = LazyListState()
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        val rootBackdrop = rememberArcaGlasmioBackdrop()
                        NexusGlassBackground(
                            modifier = Modifier.fillMaxSize(),
                            sharedGlasmioBackdrop = rootBackdrop,
                            recordGlasmioBackdropSource = true
                        ) {
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
        }

        val scroll = compose.onNodeWithTag("appearance-scroll-content")
        compose.onNodeWithTag("navigation-title-capsule").assertDoesNotExist()
        val title = compose.onNodeWithTag("navigation-title-plain")
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
        assertEquals("the plain title stays pinned while content scrolls", titleBefore.top, titleAfter.top, 1f)
        title.assertIsDisplayed()
    }

    @Test fun glassEffectsKeepsAllControlsWhileContentScrollsBehindFrostedTitle() {
        val settings = mutableStateOf(GlassSettings.DEFAULT)
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        val rootBackdrop = rememberArcaGlasmioBackdrop()
                        NexusGlassBackground(
                            modifier = Modifier.fillMaxSize(),
                            sharedGlasmioBackdrop = rootBackdrop,
                            recordGlasmioBackdropSource = true
                        ) {
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
        compose.onNodeWithTag("glass-glasmio-no-blur").assertDoesNotExist()

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

        compose.onNodeWithTag("glass-arca-tuning-group").performScrollTo().performClick()
        compose.onNodeWithTag("glass-global-refraction").assertExists()
        compose.onNodeWithTag("glass-surface-override-switch").assertExists()
        compose.onNodeWithTag("glass-motion-group").performScrollTo().performClick()
        compose.onNodeWithTag("glass-motion-gain").assertExists()

        // Exercise the override branch as well as the shared/global controls.
        compose.onNodeWithTag("glass-surface-override-switch").performScrollTo().performClick()
        compose.onNodeWithTag("glass-surface-refraction").assertExists()
        compose.onNodeWithTag("glass-surface-dispersion").assertExists()
        compose.onNodeWithTag("glass-surface-edge-width").assertExists()
        compose.onNodeWithTag("glass-surface-edge-light").assertExists()
        compose.onNodeWithText("恢复全部默认").performScrollTo().assertIsDisplayed()
        title.assertIsDisplayed()
    }

    @Test
    fun embeddedGlassDetailKeepsPreviewAndFinalControlsInsideSafePanel() {
        val settings = mutableStateOf(GlassSettings.DEFAULT)
        val glassScrollState = ScrollState(0)
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        val rootBackdrop = rememberArcaGlasmioBackdrop()
                        NexusGlassBackground(
                            modifier = Modifier.fillMaxSize(),
                            sharedGlasmioBackdrop = rootBackdrop,
                            recordGlasmioBackdropSource = true
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                Column(
                                    Modifier.fillMaxSize()
                                        .padding(bottom = 112.dp)
                                        .testTag("glass-detail-safe-panel")
                                ) {
                                    Box(Modifier.fillMaxSize().padding(14.dp)) {
                                        SettingsDetailMorphPage(
                                            page = SettingsDetailPage.GLASS_EFFECTS,
                                            tunStack = TunStack.DEFAULT,
                                            onTunStackChanged = {},
                                            logLevel = "info",
                                            onLogLevelChanged = {},
                                            shortcutTouchVariant = ShortcutTouchVariant.GentleFloat,
                                            onShortcutTouchVariantChanged = {},
                                            tunMtuDraft = "1500",
                                            onTunMtuDraftChanged = {},
                                            onTunMtuSaved = {},
                                            mipsCongestionController = "bbr",
                                            onMipsCongestionControllerChanged = {},
                                            onClose = {},
                                            glassEffectsContent = {
                                                GlassEffectsScreen(
                                                    settings = settings.value,
                                                    onSettingsChanged = { settings.value = it },
                                                    onBack = {},
                                                    scrollState = glassScrollState,
                                                    embeddedPanel = true
                                                )
                                            }
                                        )
                                    }
                                }
                                Box(
                                    Modifier.align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .testTag("glass-detail-test-dock")
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        val panelBounds = compose.onNodeWithTag("glass-detail-safe-panel").fetchSemanticsNode().boundsInRoot
        val dockBounds = compose.onNodeWithTag("glass-detail-test-dock").fetchSemanticsNode().boundsInRoot
        val headerBounds = compose.onNodeWithTag("settings-detail-fixed-header").fetchSemanticsNode().boundsInRoot
        val scroll = compose.onNodeWithTag("glass-effects-scroll")
        val initialViewport = scroll.fetchSemanticsNode().boundsInRoot
        // The scroll viewport now runs under the pinned title so content can dissolve into the
        // progressive edge blur; what must hold is that nothing sits under the title at rest.
        assertTrue("the scroll viewport extends under the pinned title", initialViewport.top <= headerBounds.top)
        val restingPreview = compose.onNodeWithTag("glass-effects-preview").fetchSemanticsNode().boundsInRoot
        assertTrue("at rest the first content clears the fixed title", restingPreview.top >= headerBounds.bottom + 27f)
        assertTrue("the panel remains above the floating dock", panelBounds.bottom <= dockBounds.top)

        compose.onNodeWithTag("glass-effects-preview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("glass-effects-preview-category").assertIsDisplayed()
        compose.onNodeWithTag("glass-effects-preview-description").assertIsDisplayed()

        compose.onNodeWithTag("glass-glasmio-alpha-row").performScrollTo().assertIsDisplayed()
        val alphaRow = compose.onNodeWithTag("glass-glasmio-alpha-row").fetchSemanticsNode().boundsInRoot
        val alphaValue = compose.onNodeWithTag("glass-glasmio-alpha-value").fetchSemanticsNode().boundsInRoot
        val alphaSlider = compose.onNodeWithTag("glass-glasmio-alpha").fetchSemanticsNode().boundsInRoot
        val alphaViewport = scroll.fetchSemanticsNode().boundsInRoot
        assertTrue("材质浓度 label and slider row start inside the scroll viewport", alphaRow.top >= alphaViewport.top)
        assertTrue("材质浓度 value remains inside the scroll viewport", alphaValue.bottom <= alphaViewport.bottom)
        assertTrue("材质浓度 slider track and thumb remain inside the scroll viewport", alphaSlider.bottom <= alphaViewport.bottom)

        compose.onNodeWithTag("glass-effects-reset-all").performScrollTo().assertIsDisplayed()
        scroll.performTouchInput { swipeUp() }
        compose.waitForIdle()
        val finalViewport = scroll.fetchSemanticsNode().boundsInRoot
        val finalAction = compose.onNodeWithTag("glass-effects-reset-all").fetchSemanticsNode().boundsInRoot
        assertTrue("the last settings action can be fully revealed above the rounded panel edge", finalAction.bottom <= finalViewport.bottom)
        assertTrue("the last settings action stays inside the panel's bottom safety margin", finalAction.bottom <= panelBounds.bottom - 12f)
        assertTrue("the last settings action is never covered by the dock", finalAction.bottom < dockBounds.top)
    }
}
