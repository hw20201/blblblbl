package com.github.arca.android.ui.screens

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
import androidx.compose.runtime.mutableFloatStateOf
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
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.data.TunStack
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.LocalSoftMeshTime
import com.github.arca.android.ui.glass.LocalSoftMeshSeed
import com.github.arca.android.ui.glass.NexusGlassBackground
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
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

/** Regression coverage for the fixed viewport background behind independently scrolling pages. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SoftMeshBackgroundScrollTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixedTime = mutableFloatStateOf(24f)

    private val oldGlassEnabled = GlassDefaults.enabled
    private val oldQuietPages = GlassDefaults.quietPages

    @After fun restoreGlassSettings() { restoreGlobals() }

    @Test fun dashboardShortcutCardsScrollOverTheFixedQuietBackground() {
        val scroll = ScrollState(0)
        GlassDefaults.enabled = true
        GlassDefaults.quietPages = true // Dashboard now shares the solid quiet-page background.
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh", LocalSoftMeshTime provides fixedTime, LocalSoftMeshSeed provides 734921L) {
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
        val backgroundBefore = captureBackgroundEdge(quiet = true)
        compose.runOnIdle { scroll.dispatchRawDelta(240f) }
        compose.waitForIdle()
        assertTrue("the dashboard must actually scroll", scroll.value > 0)
        val cardTopAfter = compose.onNodeWithTag("shortcut-card-节点").fetchSemanticsNode().boundsInRoot.top
        assertTrue("shortcut cards should move with the scrolling page content", cardTopAfter < cardTopBefore - 100f)
        val backgroundAfter = captureBackgroundEdge(quiet = true)
        assertTrue("scrolling dashboard content must not change the quiet background",
            backgroundBefore.size == backgroundAfter.size &&
                backgroundBefore.zip(backgroundAfter).all { (before, after) -> matchesQuietPixel(before, after) })
        restoreGlobals()
    }

    @Test fun configurationPageScrollsWithoutMovingTheBackground() {
        GlassDefaults.enabled = true
        GlassDefaults.quietPages = false // Exercise the generic mesh at a fixed injected time.
        val scroll = ScrollState(0)
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh", LocalSoftMeshTime provides fixedTime, LocalSoftMeshSeed provides 734921L) {
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
        val backgroundBefore = captureBackgroundEdge()
        scroller.performTouchInput { swipeUp() }
        compose.waitForIdle()
        val sectionTopAfter = compose.onNodeWithTag("configuration-center-section-header-0").fetchSemanticsNode().boundsInRoot.top
        assertTrue("the configuration page content should move under a real swipe", sectionTopAfter < sectionTopBefore - 20f)
        val backgroundAfter = captureBackgroundEdge()
        assertTrue("the fixed background must remain visible at the viewport edge", backgroundAfter.distinct().size > 8)
        assertEquals("scrolling page content must not translate the background", backgroundBefore, backgroundAfter)
        restoreGlobals()
    }

    @Test fun nestedLazyListScrollLeavesBackgroundFixedAndWithoutShowingEdges() {
        GlassDefaults.enabled = true
        GlassDefaults.quietPages = false // Exercise the generic mesh at a fixed injected time.
        lateinit var lazyState: LazyListState
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalSoftMeshTime provides fixedTime, LocalSoftMeshSeed provides 734921L) {
                ProvideGlassTheme {
                    lazyState = rememberLazyListState()
                    NexusGlassBackground() {
                        LazyColumn(state = lazyState, modifier = Modifier.fillMaxSize().testTag("fixed-background-lazy-list")) {
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
        }
        compose.onNodeWithTag("fixed-background-lazy-list").assertExists()
        val backgroundBefore = captureBackgroundEdge()
        compose.onNodeWithTag("fixed-background-lazy-list").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("lazy-list content must scroll vertically", lazyState.firstVisibleItemIndex > 0 || lazyState.firstVisibleItemScrollOffset > 100)
        }
        val backgroundAfter = captureBackgroundEdge()
        assertEquals("nested list scrolling must not translate the background", backgroundBefore, backgroundAfter)
        assertTrue("the background must still cover the viewport edge after a long-list scroll", backgroundAfter.distinct().size > 8)
        restoreGlobals()
    }

    private fun captureBackgroundEdge(quiet: Boolean = false): List<Int> {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val result = AtomicInteger(-1)
        val latch = CountDownLatch(1)
        compose.runOnIdle {
            PixelCopy.request(compose.activity.window, bitmap, { status -> result.set(status); latch.countDown() }, Handler(Looper.getMainLooper()))
        }
        assertTrue("background frame capture should complete", latch.await(3, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, result.get())
        val pixels = buildList {
            val xEnd = minOf(11, bitmap.width)
            // Start below the dashboard top-edge blur (PageScrollEdge: header + 28dp),
            // which only appears once scrolled and would otherwise tint the sampled edge.
            val yStart = minOf(160, bitmap.height / 4)
            val yEnd = maxOf(yStart, bitmap.height - minOf(100, bitmap.height / 4))
            for (y in yStart until yEnd step 4) for (x in 2 until xEnd step 2) add(bitmap.getPixel(x, y))
        }
        bitmap.recycle()
        if (quiet) {
            // Native edge compositing can round the quiet background down by one channel step.
            assertTrue("quiet background covers the viewport edge: ${pixels.distinct()}",
                pixels.isNotEmpty() && pixels.all { matchesQuietPixel(it, Color(0xFFF2F2F5).toArgb()) })
        } else {
            assertTrue("test background should be visible in the sampled edge", pixels.distinct().size > 8)
        }
        return pixels
    }

    private fun matchesQuietPixel(actual: Int, expected: Int): Boolean =
        (actual ushr 24) == (expected ushr 24) && listOf(16, 8, 0).all { shift ->
            kotlin.math.abs(((actual ushr shift) and 0xFF) - ((expected ushr shift) and 0xFF)) <= 1
        }

    private fun restoreGlobals() {
        GlassDefaults.enabled = oldGlassEnabled
        GlassDefaults.quietPages = oldQuietPages
    }

}
