package com.github.arca.android.ui.screens

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.components.LoonFloatingBottomBar
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.GlassPalettes
import com.github.arca.android.ui.glass.GlassPreset
import com.github.arca.android.ui.glass.GlassSettings
import com.github.arca.android.ui.glass.LocalGlassSettings
import com.github.arca.android.ui.glass.LocalSoftMeshSeed
import com.github.arca.android.ui.glass.LocalSoftMeshTime
import com.github.arca.android.ui.glass.NexusGlassBackground
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.glass.recordNavigationScene
import com.github.arca.android.ui.glass.rememberArcaGlasmioBackdrop
import com.github.arca.android.ui.glass.rememberNavigationLens
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
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Real Dashboard / PixelCopy frames on the Compose clock, not emulator or device video. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardModePickerAnimationCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val shortcuts = listOf("nodes", "profiles", "rules", "request_logs")
    private val wasGlassEnabled = GlassDefaults.enabled
    private val oldPalette = GlassDefaults.palette

    @After fun restoreGlassDefaults() {
        compose.mainClock.autoAdvance = true
        GlassDefaults.enabled = wasGlassEnabled
        GlassDefaults.palette = oldPalette
    }

    @Test fun captureActualRuleToDirectLensFramesWithoutMovingDashboard() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        val meshTime = mutableFloatStateOf(24f)
        val mode = mutableStateOf(TunnelMode.RULE)
        val selections = mutableListOf<TunnelMode>()
        val scroll = ScrollState(0)
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "zh",
                    LocalSoftMeshTime provides meshTime,
                    LocalSoftMeshSeed provides 734921L,
                    LocalGlassSettings provides GlassSettings(glassPreset = GlassPreset.UNIFIED)
                ) {
                    ProvideGlassTheme {
                        val backgroundLens = rememberNavigationLens()
                        val pageLens = rememberNavigationLens()
                        val backdrop = rememberArcaGlasmioBackdrop()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(
                                modifier = Modifier.recordNavigationScene(backgroundLens),
                                backdropRecorder = Modifier.recordNavigationScene(pageLens),
                                sharedGlasmioBackdrop = backdrop,
                                recordGlasmioBackdropSource = true
                            ) {
                                DashboardScreen(
                                    scrollState = scroll, vpnState = VpnState(status = VpnState.Status.STOPPED),
                                    currentMode = mode.value, activeProfileName = "本地配置", mainGroupName = "默认主组",
                                    mainGroupProxies = emptyList(), activeNodeName = "自动选择",
                                    totalNodes = 24, totalRules = 128, visibleShortcuts = shortcuts.toSet(), shortcutOrder = shortcuts,
                                    onToggleVpn = {}, onModeSelected = { selections += it; mode.value = it },
                                    onSelectProxy = { _, _ -> }, onHealthCheck = {},
                                    onNavigateToProfiles = {}, onNavigateToAppRouting = {}, onNavigateToLogs = {},
                                    onNavigateToRules = {}, onNavigateToDns = {}, onNavigateToRequests = {},
                                    onNavigateToNetworkShare = {}, onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = {},
                                    glassScene = pageLens
                                )
                            }
                            LoonFloatingBottomBar(0, {}, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), backgroundLens)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()

        val output = File("build/outputs/mode-segment-animation").also { directory ->
            check(directory.mkdirs() || directory.isDirectory)
            directory.listFiles { file -> file.extension == "png" || file.extension == "csv" }?.forEach { check(it.delete()) }
        }
        output.resolve("README.txt").writeText(
            "Production DashboardScreen, UNIFIED glass, real shared backdrop and bottom dock.\n" +
                "Rule to Direct: PixelCopy PNGs at every Compose animation frame; NOT emulator/device video.\n" +
                "The top control uses mode-selection-lens; the independent bottom dock is not the target.\n" +
                "CSV stores actual Compose clock times and root-coordinate bounds in pixels.\n"
        )
        compose.onNodeWithTag("mode-segment-control").assertIsDisplayed()
            .assertWidthIsEqualTo(264.dp).assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("mode-option-rule").assertIsSelected()
        listOf("rule", "global", "direct").forEach { compose.onNodeWithTag("mode-option-$it").assertIsDisplayed() }
        compose.onNodeWithTag("navigation-dock").assertIsDisplayed()

        val stableTags = listOf("mode-segment-control", "dashboard-nexus-title", "dashboard-traffic-card", "shortcut-nodes", "shortcut-profiles", "navigation-dock")
        val baseline = stableTags.associateWith(::bounds)
        val before = bounds("mode-selection-lens")
        val target = bounds("mode-option-direct")
        assertEquals("initial lens centers on Rule", bounds("mode-option-rule").center.x, before.center.x, 1f)
        val frames = mutableListOf(before)
        val timeline = mutableListOf<String>()
        val startedAt = compose.mainClock.currentTime

        fun capture(index: Int) {
            val lens = bounds("mode-selection-lens")
            fun Rect.csv() = "$left,$top,$width,$height"
            val fixedRects = stableTags.map(::bounds)
            timeline += "$index,${compose.mainClock.currentTime},${compose.mainClock.currentTime - startedAt},${lens.csv()}," +
                fixedRects.joinToString(",") { it.csv() }
            output.resolve("timeline.csv").writeText(
                "index,clock_ms,relative_ms,lens_left,lens_top,lens_width,lens_height," +
                    stableTags.joinToString(",") { "${it}_left,${it}_top,${it}_width,${it}_height" } + "\n" +
                    timeline.joinToString("\n") + "\n"
            )
            saveFrame(output, index)
            stableTags.forEach { tag -> assertSameRect("$tag frame $index", baseline.getValue(tag), bounds(tag)) }
            listOf("rule", "global", "direct").forEach { compose.onNodeWithTag("mode-option-$it").assertIsDisplayed() }
        }

        capture(0)
        // Actual touch input updates the owner state, then the real lens spring carries the glass.
        compose.onNodeWithTag("mode-option-direct").performTouchInput { click(center) }
        repeat(40) { index ->
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            capture(index + 1)
            frames += bounds("mode-selection-lens")
        }
        // Keep the first forty travel frames intact, then capture the independent
        // contraction/jelly settling endpoint after the shared spring has relaxed.
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        capture(41)
        val after = bounds("mode-selection-lens")
        compose.onNodeWithTag("mode-option-direct").assertIsSelected()
        compose.onNodeWithTag("mode-option-rule").assertIsNotSelected()
        compose.runOnIdle { assertEquals("touch dispatches exactly the intended mode", listOf(TunnelMode.DIRECT), selections) }
        assertEquals("lens lands at Direct", target.center.x, after.center.x, 1f)
        assertEquals("lens retains resting width", before.width, after.width, 1f)
        assertEquals("lens retains resting height", before.height, after.height, 1f)
        assertTrue("actual frame sequence includes intermediate horizontal positions", frames.any {
            it.center.x > before.center.x + 2f && it.center.x < after.center.x - 2f
        })
        assertTrue("the glass moves over multiple frames", frames.map { it.left.toInt() }.distinct().size >= 4)
        assertEquals("all travel frames and the settled endpoint are captured", 42, output.listFiles { it.extension == "png" }!!.size)
    }

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun assertSameRect(message: String, expected: Rect, actual: Rect) {
        assertEquals("$message left", expected.left, actual.left, .5f)
        assertEquals("$message top", expected.top, actual.top, .5f)
        assertEquals("$message right", expected.right, actual.right, .5f)
        assertEquals("$message bottom", expected.bottom, actual.bottom, .5f)
    }

    private fun saveFrame(directory: File, index: Int) {
        compose.waitForIdle()
        val window = compose.activity.window
        val width = window.decorView.width
        val height = window.decorView.height
        assertTrue("real Compose window must have a size", width > 0 && height > 0)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val result = AtomicInteger(-1)
            compose.runOnIdle { PixelCopy.request(window, bitmap, { result.set(it) }, Handler(Looper.getMainLooper())) }
            compose.waitUntil(5000) { result.get() != -1 }
            assertEquals("PixelCopy must capture a rendered app frame", PixelCopy.SUCCESS, result.get())
            directory.resolve("frame-%02d.png".format(Locale.ROOT, index)).outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
