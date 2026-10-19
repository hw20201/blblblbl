package com.github.mihomo.android.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.components.LoonFloatingBottomBar
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.glass.GlassPalettes
import com.github.mihomo.android.ui.glass.LocalSoftMeshSeed
import com.github.mihomo.android.ui.glass.LocalSoftMeshTime
import com.github.mihomo.android.ui.glass.NexusGlassBackground
import com.github.mihomo.android.ui.glass.ProvideGlassTheme
import com.github.mihomo.android.ui.glass.rememberNavigationLens
import com.github.mihomo.android.ui.screens.AppearanceScreen
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NestedPageDockVisibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun activeNestedRouteHidesDockEvenBeforeTransitionTargetUpdates() {
        val transition = MutableTransitionState(false)
        compose.setContent {
            NestedPageDockVisibility(transition, nestedPageVisible = true) {
                Box(Modifier.fillMaxSize().semantics { contentDescription = "unexpected-dock" })
            }
        }
        compose.onNodeWithContentDescription("unexpected-dock").assertDoesNotExist()
    }

    @Test fun actualAppearanceAndDockHideThroughEntryAndExitThenReturn() {
        val nested = mutableStateOf(false)
        val transition = MutableTransitionState(false)
        val dark = mutableStateOf(false)
        val meshTime = mutableFloatStateOf(8f)
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        compose.mainClock.autoAdvance = false
        compose.setContent {
            transition.targetState = nested.value
            MihomoTheme(if (dark.value) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "zh",
                    LocalSoftMeshTime provides meshTime,
                    LocalSoftMeshSeed provides 734921L
                ) {
                    ProvideGlassTheme {
                        val lens = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground {
                                Box(Modifier.fillMaxSize()) {
                                    AnimatedVisibility(
                                        visibleState = transition,
                                        enter = slideInHorizontally(animationSpec = tween(300)),
                                        exit = slideOutHorizontally(animationSpec = tween(260))
                                    ) {
                                        AppearanceScreen(
                                            themeMode = if (dark.value) ThemeMode.DARK else ThemeMode.LIGHT,
                                            onThemeModeChanged = { dark.value = it == ThemeMode.DARK },
                                            onLanguageChanged = {}, showGroupIcons = true,
                                            onShowGroupIconsChanged = {}, nodeColumns = 2,
                                            onNodeColumnsChanged = {}, onBack = { nested.value = false },
                                            palette = GlassPalettes.CREAM,
                                            onPaletteChanged = { GlassDefaults.palette = it }
                                        )
                                    }
                                    NestedPageDockVisibility(transition, nested.value, Modifier.align(Alignment.BottomCenter)) {
                                        Box(Modifier.semantics { contentDescription = "main-tab-dock" }) {
                                            LoonFloatingBottomBar(
                                                selectedTab = 0,
                                                onTabSelected = {},
                                                lens = lens
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("main-tab-dock").assertExists()
        capture("dock-main-tab.png")

        compose.runOnIdle { nested.value = true }
        awaitDockPresence(false)
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("main-tab-dock").assertDoesNotExist()
        compose.onNodeWithText("外观与显示").assertExists()
        capture("appearance-no-dock.png")

        compose.runOnIdle { nested.value = false }
        compose.mainClock.advanceTimeBy(150)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("main-tab-dock").assertDoesNotExist()
        capture("dock-exit-mid-transition.png")
        awaitDockPresence(true)
        capture("dock-returned-main-tab.png")
        File("build/reports/nested-dock/evidence.txt").apply {
            parentFile!!.mkdirs()
            writeText("Actual AppearanceScreen + production LoonFloatingBottomBar Compose render, Robolectric Native PixelCopy (GraphicsMode.NATIVE), not a real-device screenshot. Fixed mesh seed/time. Dock semantics absent for nested appearance and mid-exit, restored once the shared transition is idle.\n")
        }
    }

    private fun awaitDockPresence(expected: Boolean) {
        val dock = compose.onNodeWithContentDescription("main-tab-dock")
        repeat(40) {
            compose.waitForIdle()
            val exists = runCatching { dock.assertExists(); true }.getOrDefault(false)
            if (exists == expected) return
            compose.mainClock.advanceTimeByFrame()
        }
        if (expected) dock.assertExists() else dock.assertDoesNotExist()
    }

    private fun capture(name: String) {
        val window = compose.activity.window
        val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle { PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle()
        assertEquals(PixelCopy.SUCCESS, result)
        val file = File("build/reports/nested-dock/$name")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
