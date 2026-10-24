package com.github.mihomo.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.TunStack
import com.github.mihomo.android.ui.glass.NavigationLensState
import com.github.mihomo.android.ui.glass.GlassSettings
import com.github.mihomo.android.ui.glass.GlassOptics
import com.github.mihomo.android.ui.glass.GlassSurfaceCategory
import com.github.mihomo.android.ui.glass.LensStrength
import com.github.mihomo.android.ui.glass.navigationLensShaderParameters
import com.github.mihomo.android.ui.glass.ProvideGlassTheme
import com.github.mihomo.android.ui.glass.rememberNavigationLens
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class SettingsUiBehaviorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sectionGlassNeverSamplesItsOwnRecordedScene() {
        var recordedScene: NavigationLensState? = null
        var outerBackdrop: NavigationLensState? = null
        compose.setContent {
            val pageScene = rememberNavigationLens()
            val parentScene = rememberNavigationLens()
            SideEffect {
                recordedScene = pageScene
                outerBackdrop = parentScene
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val page = requireNotNull(recordedScene)
            val parent = requireNotNull(outerBackdrop)
            assertSame(parent, settingsSectionLens(page, parent))
            assertNull(settingsSectionLens(page, page))
        }
    }


    @Test fun dedicatedGlassSettingsEntryAndTuningScreenExposeLiveOverridesAndReset() {
        val scroll = ScrollState(0)
        val editorVisible = androidx.compose.runtime.mutableStateOf(false)
        val config = androidx.compose.runtime.mutableStateOf(GlassSettings.DEFAULT)
        var navigated = false
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        if (editorVisible.value) {
                            GlassEffectsScreen(
                                settings = config.value,
                                onSettingsChanged = { config.value = it },
                                onBack = { editorVisible.value = false }
                            )
                        } else {
                            SettingsScreen(
                                scrollState = scroll, tunStack = TunStack.DEFAULT,
                                bootOnStartup = false, allowLan = false, fakeIpEnabled = true,
                                onTunStackChanged = {}, onBootOnStartupChanged = {}, onAllowLanChanged = {},
                                onNavigateToConfigurationCenter = {}, onNavigateToLogs = {},
                                onNavigateToGlassEffects = {
                                    navigated = true
                                    editorVisible.value = true
                                }
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("settings-glass-effects-entry")
            .assertExists()
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("settings row opens dedicated editor", navigated) }

        compose.onNodeWithTag("glass-effects-live-lens").assertExists()
        val global = compose.onNodeWithTag("glass-global-refraction").assertExists()
        global.performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            assertTrue("global slider accepts direct values", setProgress(16f))
        }
        compose.runOnIdle { assertEquals(16f, config.value.globalOptics.refractionDp, 0f) }
        compose.runOnIdle {
            val description = compose.onNodeWithTag("glass-effects-live-lens").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
            assertTrue("live lens preview must reflect the edited global values", description.contains("折射 16.0 dp"))
        }

        val dispersion = compose.onNodeWithTag("glass-global-dispersion").assertExists()
        dispersion.performSemanticsAction(SemanticsActions.SetProgress) {
            assertTrue("dispersion slider accepts its minimum", it(GlassOptics.MIN_DISPERSION_DP))
        }
        compose.runOnIdle {
            val active = config.value.opticsFor(GlassSurfaceCategory.SHORTCUT_CARDS)
            assertEquals(GlassOptics.MIN_DISPERSION_DP, active.dispersionDp, 0f)
            assertEquals(0f, navigationLensShaderParameters(LensStrength.STRONG, active).aberrationDp, 0f)
            val description = compose.onNodeWithTag("glass-effects-live-lens").fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription].first()
            assertTrue("sample must report the exact minimum dispersion being rendered", description.contains("色散 0.0 dp"))
        }
        dispersion.performSemanticsAction(SemanticsActions.SetProgress) {
            assertTrue("dispersion slider accepts its widened maximum", it(GlassOptics.MAX_DISPERSION_DP))
        }
        compose.runOnIdle {
            val active = config.value.opticsFor(GlassSurfaceCategory.SHORTCUT_CARDS)
            val rendered = navigationLensShaderParameters(LensStrength.STRONG, active)
            assertEquals(12f, active.dispersionDp, 0f)
            assertTrue("max dispersion must produce a visibly larger chromatic shader offset", rendered.aberrationDp > 2.5f)
            val description = compose.onNodeWithTag("glass-effects-live-lens").fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription].first()
            assertTrue("sample must report the exact maximum dispersion being rendered", description.contains("色散 12.0 dp"))
        }

        compose.onNodeWithTag("glass-surface-title_capsules").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("glass-surface-override-switch").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("glass-surface-override-switch").assertIsOn()
        compose.onNodeWithTag("glass-surface-refraction").assertExists().performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(8f) }
        compose.runOnIdle {
            assertEquals(16f, config.value.opticsFor(com.github.mihomo.android.ui.glass.GlassSurfaceCategory.SHORTCUT_CARDS).refractionDp, 0f)
            assertEquals(8f, config.value.opticsFor(com.github.mihomo.android.ui.glass.GlassSurfaceCategory.TITLE_CAPSULES).refractionDp, 0f)
            val description = compose.onNodeWithTag("glass-effects-live-lens").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
            assertTrue("preview must follow the selected surface override", description.contains("折射 8.0 dp"))
        }
        // Each exposed category must store its own override and immediately update the preview.
        listOf(
            "shortcut_cards" to (com.github.mihomo.android.ui.glass.GlassSurfaceCategory.SHORTCUT_CARDS to 9f),
            "bottom_navigation" to (com.github.mihomo.android.ui.glass.GlassSurfaceCategory.BOTTOM_NAVIGATION to 10f),
            "large_panels" to (com.github.mihomo.android.ui.glass.GlassSurfaceCategory.LARGE_PANELS to 11f)
        ).forEach { (tag, item) ->
            val (surface, value) = item
            compose.onNodeWithTag("glass-surface-$tag").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("glass-surface-$tag").assertIsSelected()
            compose.onNodeWithTag("glass-surface-override-switch").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("glass-surface-override-switch").assertIsOn()
            compose.runOnIdle {
                assertTrue("$tag must persist its own override", config.value.surfaceOverrides.containsKey(surface))
            }
            val surfaceSlider = compose.onNodeWithTag("glass-surface-refraction")
                .assertExists("missing refraction slider for $tag")
                .performScrollTo()
            surfaceSlider.performSemanticsAction(SemanticsActions.SetProgress) {
                assertTrue("$tag slider accepts value", it(value))
            }
            compose.runOnIdle {
                assertEquals(value, config.value.opticsFor(surface).refractionDp, 0f)
                val description = compose.onNodeWithTag("glass-effects-live-lens").fetchSemanticsNode()
                    .config[SemanticsProperties.ContentDescription].first()
                assertTrue("$surface override must reach preview", description.contains("折射 %.1f dp".format(java.util.Locale.ROOT, value)))
            }
        }
        compose.onNodeWithTag("glass-motion-max-tilt").assertExists()
        compose.onNodeWithTag("glass-motion-smoothing").assertExists()
        compose.onNodeWithTag("glass-effects-reset-all").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(GlassSettings.DEFAULT, config.value) }
    }

    @Test fun settingsOpensWithPinnedTitleAndScrollsContinuously() {
        val scroll = ScrollState(0)
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        SettingsScreen(
                            scrollState = scroll, tunStack = TunStack.DEFAULT,
                            bootOnStartup = false, allowLan = false, fakeIpEnabled = true,
                            onTunStackChanged = {}, onBootOnStartupChanged = {}, onAllowLanChanged = {},
                            onNavigateToConfigurationCenter = {}, onNavigateToLogs = {}
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("settings-title-header").assertExists()
        val titleLeft = compose.onNodeWithTag("settings-title-capsule").fetchSemanticsNode().boundsInRoot.left
        assertEquals("Settings title capsule remains left aligned within the header", 20f, titleLeft, 1f)
        compose.onNodeWithText("设置").assertExists()
        compose.onNodeWithText("网络与核心").assertExists()
        compose.onNodeWithTag("settings-scroll").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("settings content should scroll continuously", scroll.value > 0) }
        compose.onNodeWithTag("settings-title-header").assertExists()
    }
}
