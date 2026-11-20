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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.data.PanelAnimationSpeed
import com.github.arca.android.data.ProfileItem
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.data.TunStack
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.components.LocalPanelAnimationSpeed
import com.github.arca.android.ui.components.LocalShortcutMotionEnabled
import com.github.arca.android.ui.components.LocalShortcutTouchVariant
import com.github.arca.android.ui.components.LoonFloatingBottomBar
import com.github.arca.android.ui.components.ShortcutTouchVariant
import com.github.arca.android.ui.glass.*
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
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Real production composables rendered by Robolectric Native Graphics and captured via PixelCopy.
 * These are deterministic Compose animation-clock screenshots, NOT emulator/device video frames.
 * Six cases produce twelve open/close sequences (12 PNGs each), plus one interrupted sequence.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelMorphSequenceCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val oldGlassEnabled = GlassDefaults.enabled
    private val oldPalette = GlassDefaults.palette

    private enum class Page(val directory: String, val source: String, val panel: String, val close: String) {
        DASHBOARD("dashboard", "shortcut-profiles", "shortcut-panel-profiles-sheet", "shortcut-panel-close"),
        SETTINGS("settings", "settings-panel-speed-entry", "settings-detail-page", "settings-detail-back")
    }

    @After fun restoreGlobals() {
        compose.mainClock.autoAdvance = true
        GlassDefaults.enabled = oldGlassEnabled
        GlassDefaults.palette = oldPalette
    }

    @Test fun dashboardFast() = captureNormal(Page.DASHBOARD, PanelAnimationSpeed.FAST)
    @Test fun dashboardMedium() = captureNormal(Page.DASHBOARD, PanelAnimationSpeed.MEDIUM)
    @Test fun dashboardSlow() = captureNormal(Page.DASHBOARD, PanelAnimationSpeed.SLOW)
    @Test fun settingsFast() = captureNormal(Page.SETTINGS, PanelAnimationSpeed.FAST)
    @Test fun settingsMedium() = captureNormal(Page.SETTINGS, PanelAnimationSpeed.MEDIUM)
    @Test fun settingsSlow() = captureNormal(Page.SETTINGS, PanelAnimationSpeed.SLOW)

    private fun mount(page: Page, speed: PanelAnimationSpeed) {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        val fixedMeshTime = mutableFloatStateOf(24f)
        val scroll = ScrollState(0)
        val shortcuts = listOf("nodes", "profiles", "rules", "request_logs")
        val profiles = listOf(
            ProfileItem("sequence-home", "日常配置", null, File(compose.activity.cacheDir, "sequence-home.yaml"), 0L),
            ProfileItem("sequence-travel", "备用配置", null, File(compose.activity.cacheDir, "sequence-travel.yaml"), 0L)
        )
        // Let the test clock auto-advance during setup. Settings' performScrollTo waits for
        // scroll settling, while the capture phase below deliberately freezes the clock.
        compose.mainClock.autoAdvance = true
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "zh",
                    LocalPanelAnimationSpeed provides speed,
                    LocalShortcutMotionEnabled provides true,
                    LocalShortcutTouchVariant provides ShortcutTouchVariant.GentleFloat,
                    LocalSoftMeshTime provides fixedMeshTime,
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
                                when (page) {
                                    Page.DASHBOARD -> DashboardScreen(
                                        scrollState = scroll,
                                        vpnState = VpnState(status = VpnState.Status.STOPPED),
                                        currentMode = TunnelMode.RULE,
                                        activeProfileName = "日常配置", mainGroupName = "默认主组",
                                        mainGroupProxies = emptyList(), activeNodeName = "自动选择",
                                        totalNodes = 24, totalRules = 128,
                                        visibleShortcuts = shortcuts.toSet(), shortcutOrder = shortcuts,
                                        profiles = profiles, selectedProfileId = "sequence-home",
                                        onToggleVpn = {}, onModeSelected = {}, onSelectProxy = { _, _ -> },
                                        onHealthCheck = {}, onNavigateToProfiles = {},
                                        onNavigateToAppRouting = {}, onNavigateToLogs = {},
                                        onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = {},
                                        glassScene = pageLens
                                    )
                                    Page.SETTINGS -> SettingsScreen(
                                        scrollState = scroll, tunStack = TunStack.DEFAULT,
                                        bootOnStartup = false, allowLan = false, fakeIpEnabled = false,
                                        panelAnimationSpeed = speed,
                                        onPanelAnimationSpeedChanged = {},
                                        onTunStackChanged = {}, onBootOnStartupChanged = {},
                                        onAllowLanChanged = {}, onNavigateToConfigurationCenter = {},
                                        onNavigateToLogs = {}, glassScene = pageLens
                                    )
                                }
                            }
                            LoonFloatingBottomBar(
                                selectedTab = if (page == Page.DASHBOARD) 0 else 2,
                                onTabSelected = {},
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                                lens = backgroundLens
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithTag(page.source).performScrollTo()
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        val headerTag = if (page == Page.DASHBOARD) "dashboard-nexus-title" else "settings-title-header"
        if (page == Page.SETTINGS) {
            // performScrollTo only sees the full-height scroll viewport, not the overlaid
            // dock. Move the actual row to the usable viewport's center before capturing.
            val source = bounds(page.source)!!
            val usableCenterY = (bounds(headerTag)!!.bottom + bounds("navigation-dock")!!.top) / 2f
            compose.runOnIdle { scroll.dispatchRawDelta(source.center.y - usableCenterY) }
            compose.waitForIdle()
        }
        compose.onNodeWithTag(page.source).assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("navigation-dock").assertIsDisplayed()
        compose.onNodeWithTag(headerTag).assertIsDisplayed()
        val source = bounds(page.source)!!
        assertTrue("capture source clears the dock: $source", source.bottom < bounds("navigation-dock")!!.top)
        assertTrue("capture source clears the header: $source", source.top > bounds(headerTag)!!.bottom)
        compose.mainClock.autoAdvance = false
    }

    private fun captureNormal(page: Page, speed: PanelAnimationSpeed) {
        mount(page, speed)
        val directory = outputDirectory("${page.directory}-${speed.name.lowercase(Locale.ROOT)}")
        val rows = mutableListOf<String>()
        val source = bounds(page.source)!!
        val opening = mutableListOf<Rect>()
        val closing = mutableListOf<Rect>()
        val openedAt = compose.mainClock.currentTime
        capture(directory, "open-00", page, "open", 0, openedAt, rows)
        clickWithoutAdvancingClock(page.source)
        val openStep = frameStep(speed.openDurationMillis + 96)
        for (index in 1..11) {
            compose.mainClock.advanceTimeBy(openStep)
            capture(directory, "open-%02d".format(Locale.ROOT, index), page, "open", index, openedAt, rows)
            bounds(page.panel)?.let(opening::add)
        }
        compose.onNodeWithTag(page.panel).assertIsDisplayed()
        val full = bounds(page.panel)!!
        assertIntermediateDimensions("${page.directory} opening", opening, source, full)
        val dock = bounds("navigation-dock")!!
        assertTrue("fully opened panel must clear the real dock: panel=$full dock=$dock", full.bottom <= dock.top + 1f)
        if (page == Page.DASHBOARD) {
            compose.onNodeWithTag("quick-profile-add").assertIsDisplayed()
            compose.onNodeWithText("备用配置").assertIsDisplayed()
        } else {
            PanelAnimationSpeed.entries.forEach {
                compose.onNodeWithTag("settings-detail-panel-speed-${it.name.lowercase(Locale.ROOT)}").assertIsDisplayed()
            }
        }
        val closedAt = compose.mainClock.currentTime
        capture(directory, "close-00", page, "close", 0, closedAt, rows)
        clickWithoutAdvancingClock(page.close)
        val closeStep = frameStep(speed.closeDurationMillis + 64)
        for (index in 1..11) {
            compose.mainClock.advanceTimeBy(closeStep)
            capture(directory, "close-%02d".format(Locale.ROOT, index), page, "close", index, closedAt, rows)
            bounds(page.panel)?.let(closing::add)
        }
        writeTimeline(directory, rows)
        assertIntermediateDimensions("${page.directory} closing", closing, source, full)
        assertSourceReturned(page, source)
        assertEquals("twelve opening screenshots", 12, directory.listFiles { f -> f.name.startsWith("open-") && f.extension == "png" }!!.size)
        assertEquals("twelve closing screenshots", 12, directory.listFiles { f -> f.name.startsWith("close-") && f.extension == "png" }!!.size)
    }

    @Test fun dashboardMediumReversesFromCurrentSizeAndReturnsSource() {
        val page = Page.DASHBOARD
        mount(page, PanelAnimationSpeed.MEDIUM)
        val directory = outputDirectory("dashboard-medium-interrupted")
        val rows = mutableListOf<String>()
        val source = bounds(page.source)!!
        val start = compose.mainClock.currentTime
        capture(directory, "reverse-00", page, "baseline", 0, start, rows)
        clickWithoutAdvancingClock(page.source)
        // Two preparation frames, then enough motion to reveal the real close control.
        compose.mainClock.advanceTimeBy(224)
        capture(directory, "reverse-01", page, "opening", 1, start, rows)
        val beforeClose = bounds(page.panel)!!
        val closeClock = compose.mainClock.currentTime
        clickWithoutAdvancingClock(page.close)
        capture(directory, "reverse-02", page, "close-trigger-same-clock", 2, start, rows)
        assertSameClockGeometry("opening to closing", closeClock, beforeClose, bounds(page.panel)!!)
        compose.mainClock.advanceTimeByFrame()
        capture(directory, "reverse-03", page, "close-first-frame", 3, start, rows)
        val closeStart = bounds(page.panel)!!
        // The first clock tick also applies the recomposition that changes visible;
        // the old animation has advanced once before its replacement starts.
        // Check the new direction against that actual start, not the prior frame.
        compose.mainClock.advanceTimeBy(32)
        capture(directory, "reverse-04", page, "closing", 4, start, rows)
        val shrinking = bounds(page.panel)!!
        assertTrue("closing reverses width", shrinking.width < closeStart.width)
        assertTrue("closing reverses height", shrinking.height < closeStart.height)
        assertTrue("interrupted close has not snapped to source width", shrinking.width > source.width + 1f)
        assertTrue("interrupted close has not snapped to source height", shrinking.height > source.height + 1f)
        // Invoke the source's actual OnClick while its visual handoff is still hidden.
        // No touch injection may advance the clock or hit an overlying panel/dock.
        val reopenClock = compose.mainClock.currentTime
        clickWithoutAdvancingClock(page.source)
        capture(directory, "reverse-05", page, "reopen-trigger-same-clock", 5, start, rows)
        assertSameClockGeometry("closing to reopening", reopenClock, shrinking, bounds(page.panel)!!)
        compose.mainClock.advanceTimeByFrame()
        capture(directory, "reverse-06", page, "reopen-first-frame", 6, start, rows)
        val reopenStart = bounds(page.panel)!!
        repeat(8) { index ->
            compose.mainClock.advanceTimeBy(48)
            capture(directory, "reverse-%02d".format(Locale.ROOT, index + 7), page, "reopening", index + 7, start, rows)
            if (index == 0) {
                val growing = bounds(page.panel)!!
                assertTrue("reopening reverses width", growing.width > reopenStart.width)
                assertTrue("reopening reverses height", growing.height > reopenStart.height)
            }
        }
        val full = bounds(page.panel)!!
        assertTrue("reopening reaches beyond interrupted width", full.width > shrinking.width)
        assertTrue("reopening reaches beyond interrupted height", full.height > shrinking.height)
        clickWithoutAdvancingClock(page.close)
        compose.mainClock.advanceTimeBy(400)
        capture(directory, "reverse-15", page, "finished", 15, start, rows)
        writeTimeline(directory, rows)
        assertSourceReturned(page, source)
    }

    private fun frameStep(totalMillis: Int): Long =
        (ceil(totalMillis / 11.0 / 16.0).toLong() * 16L).coerceAtLeast(16L)

    private fun assertIntermediateDimensions(label: String, frames: List<Rect>, source: Rect, full: Rect) {
        if (abs(source.width - full.width) <= 1f) {
            assertTrue("$label preserves its equal endpoint width: $frames", frames.isNotEmpty() && frames.all {
                abs(it.width - full.width) <= 1f
            })
        } else {
            assertTrue("$label has changing width, not just endpoints: $frames", frames.any {
                it.width > minOf(source.width, full.width) + 0.5f && it.width < maxOf(source.width, full.width) - 0.5f
            })
        }
        assertTrue("$label has changing height, not just endpoints: $frames", frames.any {
            it.height > minOf(source.height, full.height) + 1f && it.height < maxOf(source.height, full.height) - 1f
        })
        assertTrue("$label contains multiple intermediate geometries", frames.map { it.width.roundToIntSafe() to it.height.roundToIntSafe() }.distinct().size >= 3)
    }

    private fun Float.roundToIntSafe(): Int = kotlin.math.round(this).toInt()

    private fun clickWithoutAdvancingClock(tag: String) {
        compose.waitForIdle()
        val clock = compose.mainClock.currentTime
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { action ->
            assertTrue("$tag accepts its real click handler", action())
        }
        compose.waitForIdle()
        assertEquals("semantic click must not advance the animation clock", clock, compose.mainClock.currentTime)
    }

    private fun assertSameClockGeometry(label: String, clock: Long, before: Rect, after: Rect) {
        assertEquals("$label compares the same animation instant", clock, compose.mainClock.currentTime)
        assertEquals("$label keeps width at the trigger", before.width, after.width, 1f)
        assertEquals("$label keeps height at the trigger", before.height, after.height, 1f)
        assertEquals("$label keeps left at the trigger", before.left, after.left, 1f)
        assertEquals("$label keeps top at the trigger", before.top, after.top, 1f)
    }

    private fun assertSourceReturned(page: Page, expected: Rect) {
        compose.onNodeWithTag(page.panel).assertDoesNotExist()
        compose.onNodeWithTag(page.source).assertIsDisplayed().assertHasClickAction()
        val actual = bounds(page.source)!!
        assertEquals("source left returns", expected.left, actual.left, 1f)
        assertEquals("source top returns", expected.top, actual.top, 1f)
        assertEquals("source width returns", expected.width, actual.width, 1f)
        assertEquals("source height returns", expected.height, actual.height, 1f)
    }

    private fun bounds(tag: String): Rect? =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    private fun outputDirectory(name: String): File = File("build/outputs/panel-morph-sequences/$name").also { dir ->
        check(dir.mkdirs() || dir.isDirectory)
        dir.listFiles { f -> f.extension == "png" || f.extension == "csv" }?.forEach { check(it.delete()) }
        dir.resolve("README.txt").writeText(
            "Robolectric Native Graphics / Compose animation-clock PixelCopy screenshots.\n" +
                "NOT emulator or device video. Production DashboardScreen/SettingsScreen, real header/content/dock.\n" +
                "UNIFIED glass; cream palette; fixed mesh time 24s and seed 734921.\n" +
                "CSV clocks are actual Compose mainClock times; bounds are pixels in the Compose root.\n"
        )
    }

    private fun capture(directory: File, name: String, page: Page, phase: String, index: Int, start: Long, rows: MutableList<String>) {
        compose.waitForIdle()
        val panel = bounds(page.panel)
        val source = bounds(page.source)
        val dock = bounds("navigation-dock")
        fun Rect?.csv() = if (this == null) ",,," else "$left,$top,$width,$height"
        val clock = compose.mainClock.currentTime
        rows += "$phase,$index,$clock,${clock - start},${panel != null},${panel.csv()},${source.csv()},${dock.csv()}"
        writeTimeline(directory, rows) // Preserve measurements even if a later assertion fails.
        val window = compose.activity.window
        val width = window.decorView.width
        val height = window.decorView.height
        assertTrue("real Compose window has a size", width > 0 && height > 0)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val result = AtomicInteger(-1)
            compose.runOnIdle {
                PixelCopy.request(window, bitmap, { result.set(it) }, Handler(Looper.getMainLooper()))
            }
            compose.waitUntil(5000) { result.get() != -1 }
            assertEquals("PixelCopy captures the rendered frame", PixelCopy.SUCCESS, result.get())
            directory.resolve("$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeTimeline(directory: File, rows: List<String>) {
        directory.resolve("timeline.csv").writeText(
            "phase,index,clock_ms,relative_ms,panel_present,panel_left,panel_top,panel_width,panel_height," +
                "source_left,source_top,source_width,source_height,dock_left,dock_top,dock_width,dock_height\n" +
                rows.joinToString("\n") + "\n"
        )
    }
}
