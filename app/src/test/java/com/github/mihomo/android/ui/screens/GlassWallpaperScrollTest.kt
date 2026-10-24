package com.github.mihomo.android.ui.screens

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.activity.ComponentActivity
import com.github.kr328.clash.core.model.Proxy
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.TunStack
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.glass.GlassWallpaperMode
import com.github.mihomo.android.ui.glass.GlassWallpaperSettings
import com.github.mihomo.android.ui.glass.NexusGlassBackground
import com.github.mihomo.android.ui.glass.ProvideGlassTheme
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Regression coverage for the fixed viewport wallpaper behind independently scrolling pages. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassWallpaperScrollTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val oldWallpaper = GlassDefaults.wallpaper
    private val oldGlassEnabled = GlassDefaults.enabled

    @After fun restoreGlassSettings() { restoreGlobals() }

    @Test fun dashboardShortcutCardsScrollWithoutMovingTheWallpaper() {
        val scroll = ScrollState(0)
        GlassDefaults.enabled = true
        GlassDefaults.wallpaper = fixedTestWallpaper
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground() {
                                Box(Modifier.fillMaxWidth().height(480.dp)) {
                                DashboardScreen(
                                    scrollState = scroll,
                                    vpnState = VpnState(status = VpnState.Status.STOPPED),
                                    currentMode = TunnelMode.RULE,
                                    activeProfileName = "测试配置",
                                    mainGroupName = "",
                                    mainGroupProxies = emptyList<Proxy>(),
                                    activeNodeName = "",
                                    totalNodes = 0,
                                    totalRules = 0,
                                    visibleShortcuts = setOf("nodes", "profiles", "rules", "request_logs"),
                                    shortcutOrder = listOf("nodes", "profiles", "rules", "request_logs"),
                                    onToggleVpn = {}, onModeSelected = {}, onSelectProxy = { _, _ -> },
                                    onHealthCheck = {}, onNavigateToProfiles = {}, onNavigateToAppRouting = {},
                                    onNavigateToLogs = {}, onNavigateToRules = {}, onNavigateToDns = {},
                                    onNavigateToRequests = {}, onNavigateToNetworkShare = {},
                                    onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = {}
                                )
                                }
                            }
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("dashboard-scroll").assertExists()
        val card = compose.onNodeWithTag("shortcut-card-节点").assertExists()
        val cardTopBefore = card.fetchSemanticsNode().boundsInRoot.top
        val wallpaperBefore = captureWallpaperEdge()
        compose.runOnIdle { scroll.dispatchRawDelta(240f) }
        compose.waitForIdle()
        assertTrue("the dashboard must actually scroll", scroll.value > 0)
        val cardTopAfter = compose.onNodeWithTag("shortcut-card-节点").fetchSemanticsNode().boundsInRoot.top
        assertTrue("shortcut cards should move with the scrolling page content", cardTopAfter < cardTopBefore - 100f)
        val wallpaperAfter = captureWallpaperEdge()
        assertTrue("the fixed wallpaper must remain visible at the viewport edge", wallpaperAfter.distinct().size > 8)
        assertEquals("scrolling page content must not translate the wallpaper", wallpaperBefore, wallpaperAfter)
        restoreGlobals()
    }

    @Test fun configurationPageScrollsWithoutMovingTheWallpaper() {
        GlassDefaults.enabled = true
        GlassDefaults.wallpaper = fixedTestWallpaper
        val scroll = ScrollState(0)
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        NexusGlassBackground() {
                            Box(Modifier.fillMaxWidth().height(480.dp)) {
                            ConfigurationCenterScreen(
                                onNavigateToProfiles = {}, onNavigateToScripts = {}, onNavigateToRules = {},
                                onNavigateToAppRouting = {}, onBack = {}, scrollState = scroll
                            )
                            }
                        }
                    }
                }
            }
        }

        val scroller = compose.onNodeWithTag("configuration-center-scroll").assertExists()
        val section = compose.onNodeWithTag("configuration-center-section-header-0").assertExists()
        val sectionTopBefore = section.fetchSemanticsNode().boundsInRoot.top
        val wallpaperBefore = captureWallpaperEdge()
        scroller.performTouchInput { swipeUp() }
        compose.waitForIdle()
        val sectionTopAfter = compose.onNodeWithTag("configuration-center-section-header-0").fetchSemanticsNode().boundsInRoot.top
        assertTrue("the configuration page content should move under a real swipe", sectionTopAfter < sectionTopBefore - 20f)
        val wallpaperAfter = captureWallpaperEdge()
        assertTrue("the fixed wallpaper must remain visible at the viewport edge", wallpaperAfter.distinct().size > 8)
        assertEquals("scrolling page content must not translate the wallpaper", wallpaperBefore, wallpaperAfter)
        restoreGlobals()
    }

    @Test fun nestedLazyListScrollLeavesWallpaperFixedAndWithoutShowingEdges() {
        GlassDefaults.enabled = true
        GlassDefaults.wallpaper = fixedTestWallpaper
        lateinit var lazyState: LazyListState
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    lazyState = rememberLazyListState()
                    NexusGlassBackground() {
                        LazyColumn(state = lazyState, modifier = Modifier.fillMaxSize().testTag("fixed-wallpaper-lazy-list")) {
                            items((0 until 40).toList()) { index ->
                                androidx.compose.material3.Text(
                                    text = "row $index",
                                    modifier = Modifier.fillMaxWidth().height(72.dp).padding(start = 40.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("fixed-wallpaper-lazy-list").assertExists()
        val wallpaperBefore = captureWallpaperEdge()
        compose.onNodeWithTag("fixed-wallpaper-lazy-list").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("lazy-list content must scroll vertically", lazyState.firstVisibleItemIndex > 0 || lazyState.firstVisibleItemScrollOffset > 100)
        }
        val wallpaperAfter = captureWallpaperEdge()
        assertEquals("nested list scrolling must not translate the wallpaper", wallpaperBefore, wallpaperAfter)
        assertTrue("the wallpaper must still cover the viewport edge after a long-list scroll", wallpaperAfter.distinct().size > 8)
        restoreGlobals()
    }

    private fun captureWallpaperEdge(): List<Int> {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val result = AtomicInteger(-1)
        val latch = CountDownLatch(1)
        compose.runOnIdle {
            PixelCopy.request(compose.activity.window, bitmap, { status -> result.set(status); latch.countDown() }, Handler(Looper.getMainLooper()))
        }
        assertTrue("wallpaper frame capture should complete", latch.await(3, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, result.get())
        val pixels = buildList {
            val xEnd = minOf(11, bitmap.width)
            val yStart = minOf(90, bitmap.height / 4)
            val yEnd = maxOf(yStart, bitmap.height - minOf(100, bitmap.height / 4))
            for (y in yStart until yEnd step 4) for (x in 2 until xEnd step 2) add(bitmap.getPixel(x, y))
        }
        bitmap.recycle()
        assertTrue("test wallpaper should be visible in the sampled edge", pixels.distinct().size > 8)
        return pixels
    }

    private fun restoreGlobals() {
        GlassDefaults.wallpaper = oldWallpaper
        GlassDefaults.enabled = oldGlassEnabled
    }

    companion object {
        private val fixedTestWallpaper = GlassWallpaperSettings(
            enabled = true,
            mode = GlassWallpaperMode.FLUTED,
            colorA = Color(0xFF16324F).toArgb(),
            colorB = Color(0xFF3F88C5).toArgb(),
            colorC = Color(0xFFE6C79C).toArgb(),
            colorFlow = false
        )
    }
}
