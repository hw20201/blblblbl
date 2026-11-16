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
import com.github.arca.android.data.PanelAnimationSpeed
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.data.TunStack
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

/** Real SettingsScreen/PANEL_SPEED, with a low source that exposes top-edge rushing.
 * PNGs are Robolectric Native Graphics PixelCopy frames, not device video.
 * Geometry assertions run only after the entire sequence has been persisted.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsPanelEdgeMotionCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val oldGlassEnabled = GlassDefaults.enabled
    private val oldPalette = GlassDefaults.palette
    private val oldWallpaper = GlassDefaults.wallpaper

    private data class Frame(
        val phase: String,
        val index: Int,
        val clock: Long,
        val panel: Rect?,
        val source: Rect?,
        val dock: Rect?
    )

    private data class Click(val before: Long, val after: Long, val accepted: Boolean)

    @After fun restoreGlobals() {
        compose.mainClock.autoAdvance = true
        GlassDefaults.enabled = oldGlassEnabled
        GlassDefaults.palette = oldPalette
        GlassDefaults.wallpaper = oldWallpaper
    }

    @Test fun settingsFastClosesWithUniformEdges() = captureClosing(PanelAnimationSpeed.FAST, 240, 180)
    @Test fun settingsMediumClosesWithUniformEdges() = captureClosing(PanelAnimationSpeed.MEDIUM, 360, 270)
    @Test fun settingsSlowClosesWithUniformEdges() = captureClosing(PanelAnimationSpeed.SLOW, 540, 405)

    private fun mount(speed: PanelAnimationSpeed) {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        GlassDefaults.wallpaper = GlassWallpaperSettings()
        val fixedMeshTime = mutableFloatStateOf(24f)
        val scroll = ScrollState(0)
        // performScrollTo needs automatic advancement to finish its setup work.
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
                                SettingsScreen(
                                    scrollState = scroll, tunStack = TunStack.DEFAULT,
                                    bootOnStartup = false, allowLan = false, fakeIpEnabled = false,
                                    panelAnimationSpeed = speed,
                                    onPanelAnimationSpeedChanged = {},
                                    onTunStackChanged = {}, onBootOnStartupChanged = {},
                                    onAllowLanChanged = {}, onNavigateToConfigurationCenter = {},
                                    onNavigateToLogs = {}, glassScene = pageLens
                                )
                            }
                            LoonFloatingBottomBar(
                                selectedTab = 2, onTabSelected = {},
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
        compose.onNodeWithTag(SOURCE).performScrollTo()
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        val source = requireNotNull(bounds(SOURCE))
        val dock = requireNotNull(bounds(DOCK))
        // The scroll viewport includes the overlaid dock. Deliberately keep the row
        // low, but fully above it: approximately top=639 versus expanded top=352.
        compose.runOnIdle { scroll.dispatchRawDelta(source.bottom - (dock.top - 12f)) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun captureClosing(speed: PanelAnimationSpeed, openDuration: Int, closeDuration: Int) {
        mount(speed)
        val directory = outputDirectory(speed.name.lowercase(Locale.ROOT))
        val frames = mutableListOf<Frame>()
        val baseline = capture(directory, "source", 0, frames)
        val openClick = click(SOURCE)
        compose.mainClock.advanceTimeBy((openDuration + 128).toLong())
        val fullFrame = capture(directory, "opened", 0, frames)
        val closeClick = click(CLOSE)
        val trigger = capture(directory, "close-trigger", 0, frames)
        val closing = mutableListOf(fullFrame, trigger)
        // Include startup frames and several post-completion frames. Do not stop
        // early when the overlay disappears: disappearance itself is evidence.
        repeat(ceil(closeDuration / 16.0).toInt() + 8) { index ->
            compose.mainClock.advanceTimeBy(16)
            closing += capture(directory, "close", index + 1, frames)
        }

        // Everything below is intentionally after the complete capture loop.
        assertEquals("opening duration remains unchanged", openDuration, speed.openDurationMillis)
        assertEquals("closing duration remains unchanged", closeDuration, speed.closeDurationMillis)
        assertClick(openClick)
        assertClick(closeClick)
        val source = requireNotNull(baseline.source)
        val dock = requireNotNull(baseline.dock)
        val full = requireNotNull(fullFrame.panel)
        assertEquals("low source ends 12px above the dock", dock.top - 12f, source.bottom, 1f)
        assertTrue("source is low enough to exercise the top shift: $source -> $full", source.top - full.top > 200f)
        assertTrue("bottom travels much less than top", abs(source.bottom - full.bottom) < abs(source.top - full.top) / 4f)
        assertTrue("expanded panel clears dock", full.bottom <= dock.top + 1f)
        assertEquals("close trigger is captured at the baseline clock", fullFrame.clock, trigger.clock)
        assertRect("no instantaneous close-trigger jump", full, requireNotNull(trigger.panel), 0.01f)
        assertLinearClosing(closing, source, full, closeDuration)
        assertRestored(source, frames.last())
    }

    private fun assertLinearClosing(frames: List<Frame>, source: Rect, full: Rect, duration: Int) {
        val samples = frames.drop(2)
        assertTrue("at least eight successive closing frames", samples.size >= 8)
        frames.drop(1).zipWithNext().forEach { (a, b) ->
            assertEquals("each PNG is one actual 16ms tick apart", 16L, b.clock - a.clock)
        }
        val firstAbsent = samples.indexOfFirst { it.panel == null }
        assertTrue("overlay disappears by the end of the sequence", firstAbsent >= 0)
        assertTrue("overlay stays absent after completion", samples.drop(firstAbsent).all { it.panel == null })
        // Once the overlay disappears its rendered replacement is the original row.
        // Include that endpoint so early unmounting cannot evade the step checks.
        val rectangles = listOf(full) + samples.take(firstAbsent + 1).map { it.panel ?: source }
        val topDistance = source.top - full.top
        val bottomDistance = source.bottom - full.bottom
        assertTrue("both edge paths have measurable endpoints", abs(topDistance) > 2f && abs(bottomDistance) > 2f)
        rectangles.forEachIndexed { index, rect ->
            val topProgress = (rect.top - full.top) / topDistance
            val bottomProgress = (rect.bottom - full.bottom) / bottomDistance
            // Position and size round separately; the bottom has up to two pixels
            // of endpoint uncertainty, disproportionately large for its short path.
            val normalizedTolerance = 2f / abs(topDistance) + 2f / abs(bottomDistance)
            assertEquals("frame $index top/bottom share normalized progress", topProgress, bottomProgress, normalizedTolerance)
        }
        assertEdgeSteps("top", rectangles.map { it.top }, full.top, source.top, duration)
        assertEdgeSteps("bottom", rectangles.map { it.bottom }, full.bottom, source.bottom, duration)
        // Top moves many pixels per tick, unlike the short bottom path, where an
        // occasional zero is legitimate integer rounding. Only startup may pause.
        val topSteps = rectangles.zipWithNext { a, b -> (b.top - a.top) * if (topDistance > 0f) 1f else -1f }
        val firstMoving = topSteps.indexOfFirst { it > 0.5f }
        assertTrue("closing starts moving", firstMoving >= 0)
        assertTrue("startup permits at most three preparation ticks", firstMoving <= 3)
        topSteps.drop(firstMoving).dropLast(1).forEachIndexed { index, step ->
            assertTrue("no pause after motion starts, step $index=$step", step > 0.5f)
        }
        val firstMoveClock = samples[firstMoving].clock
        val finishClock = samples[firstAbsent].clock
        assertTrue(
            "motion duration includes all full-length steps: ${finishClock - firstMoveClock}ms vs ${duration}ms",
            abs((finishClock - firstMoveClock) - (duration - 16L)) <= 32L
        )
    }

    private fun assertEdgeSteps(label: String, values: List<Float>, start: Float, end: Float, duration: Int) {
        val direction = if (end > start) 1f else -1f
        val distance = abs(end - start)
        val expectedStep = distance * 16f / duration
        var started = false
        values.zipWithNext().forEachIndexed { index, (before, after) ->
            val step = (after - before) * direction
            // Top offset and height are rounded independently, so their sum
            // (bottom) can wobble by one pixel on this deliberately short path.
            val roundingBackstep = if (label == "bottom") 1f else 0.01f
            assertTrue("$label never moves away beyond pixel rounding at $index: $step", step >= -roundingBackstep)
            assertTrue("$label stays between endpoints at $index", (after - start) * direction >= -1f && (end - after) * direction >= -1f)
            assertTrue("$label maximum 16ms step at $index: $step <= $expectedStep + 2", step <= expectedStep + 2f)
            // Startup zeros are legal. The last step can cover a partial frame.
            // The dominant edge independently prohibits mid-animation pauses.
            if (step > 0.01f) started = true
            val remainingBefore = (end - before) * direction
            if (started && index < values.size - 2 && remainingBefore > expectedStep + 2f) {
                assertEquals("$label uniform interior 16ms step $index", expectedStep, step, 2f)
            }
        }
    }

    @Test fun settingsInterruptedOpeningCanCloseAndReopenWithoutJump() {
        val speed = PanelAnimationSpeed.MEDIUM
        mount(speed)
        val directory = outputDirectory("medium-interrupted")
        val frames = mutableListOf<Frame>()
        val baseline = capture(directory, "source", 0, frames)
        val openClick = click(SOURCE)
        compose.mainClock.advanceTimeBy(224)
        val opening = capture(directory, "opening", 0, frames)
        val closeClick = click(CLOSE)
        val closeTrigger = capture(directory, "close-trigger", 0, frames)
        compose.mainClock.advanceTimeBy(16)
        val closeStart = capture(directory, "close-first-frame", 0, frames)
        repeat(2) {
            compose.mainClock.advanceTimeBy(16)
            capture(directory, "interrupted-close", it + 1, frames)
        }
        val shrinking = frames.last()
        val reopenClick = click(SOURCE)
        val reopenTrigger = capture(directory, "reopen-trigger", 0, frames)
        compose.mainClock.advanceTimeBy(16)
        val reopenStart = capture(directory, "reopen-first-frame", 0, frames)
        repeat(ceil(speed.openDurationMillis / 16.0).toInt() + 8) {
            compose.mainClock.advanceTimeBy(16)
            capture(directory, "reopen", it + 1, frames)
        }
        val full = frames.last()
        val finalClick = click(CLOSE)
        capture(directory, "final-close-trigger", 0, frames)
        repeat(ceil(speed.closeDurationMillis / 16.0).toInt() + 8) {
            compose.mainClock.advanceTimeBy(16)
            capture(directory, "final-close", it + 1, frames)
        }

        listOf(openClick, closeClick, reopenClick, finalClick).forEach(::assertClick)
        assertEquals("interrupt close retains clock", opening.clock, closeTrigger.clock)
        assertRect("interrupt close retains geometry", requireNotNull(opening.panel), requireNotNull(closeTrigger.panel), 0.01f)
        assertEquals("interrupt reopen retains clock", shrinking.clock, reopenTrigger.clock)
        assertRect("interrupt reopen retains geometry", requireNotNull(shrinking.panel), requireNotNull(reopenTrigger.panel), 0.01f)
        val source = requireNotNull(baseline.source)
        // Recomposition can advance the OLD animation on the first new frame.
        // Reversal direction must be measured from that new animation's baseline.
        val closingFrom = requireNotNull(closeStart.panel)
        val closingTo = requireNotNull(shrinking.panel)
        assertTrue("interrupted top reverses toward source", abs(source.top - closingTo.top) < abs(source.top - closingFrom.top))
        assertTrue("interrupted bottom reverses toward source", abs(source.bottom - closingTo.bottom) <= abs(source.bottom - closingFrom.bottom) + 1f)
        val reopeningFrom = requireNotNull(reopenStart.panel)
        val growing = requireNotNull(frames.first { it.phase == "reopen" && it.index == 3 }.panel)
        assertTrue("reopening top reverses away from source", abs(growing.top - source.top) > abs(reopeningFrom.top - source.top))
        assertTrue("reopening grows height", growing.height > reopeningFrom.height)
        assertTrue("reopening completes beyond interrupted size", requireNotNull(full.panel).height > closingTo.height)
        assertRestored(source, frames.last())
    }

    private fun click(tag: String): Click {
        compose.waitForIdle()
        val before = compose.mainClock.currentTime
        var accepted = false
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { accepted = it() }
        compose.waitForIdle()
        return Click(before, compose.mainClock.currentTime, accepted)
    }

    private fun assertClick(click: Click) {
        assertTrue("real semantic click accepted", click.accepted)
        assertEquals("semantic click does not advance clock", click.before, click.after)
    }

    private fun assertRestored(source: Rect, last: Frame) {
        assertTrue("full close removes overlay", last.panel == null)
        compose.onNodeWithTag(PANEL).assertDoesNotExist()
        compose.onNodeWithTag(SOURCE).assertIsDisplayed().assertHasClickAction()
        assertRect("original source restored", source, requireNotNull(last.source), 1f)
    }

    private fun assertRect(label: String, expected: Rect, actual: Rect, tolerance: Float) {
        assertEquals("$label left", expected.left, actual.left, tolerance)
        assertEquals("$label top", expected.top, actual.top, tolerance)
        assertEquals("$label right", expected.right, actual.right, tolerance)
        assertEquals("$label bottom", expected.bottom, actual.bottom, tolerance)
    }

    private fun bounds(tag: String): Rect? {
        compose.waitForIdle()
        return compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
    }

    private fun outputDirectory(name: String): File = File("build/outputs/settings-panel-edge-motion/$name").also { dir ->
        check(dir.mkdirs() || dir.isDirectory)
        dir.listFiles { f -> f.extension == "png" || f.extension == "csv" }?.forEach { check(it.delete()) }
        dir.resolve("README.txt").writeText(
            "Robolectric Native Graphics / actual Compose animation-clock PixelCopy PNGs; NOT device video.\n" +
                "Real SettingsScreen PANEL_SPEED; 400x800 mdpi; UNIFIED glass, cream palette, mesh time 24s/seed 734921.\n" +
                "Source bottom is dock.top-12px. Bounds are Compose-root pixels; clocks are read at capture time.\n" +
                "close-trigger is the same instant as opened; close-NNN frames are exactly 16ms apart.\n"
        )
    }

    private fun capture(directory: File, phase: String, index: Int, frames: MutableList<Frame>): Frame {
        compose.waitForIdle()
        val panel = bounds(PANEL)
        val source = bounds(SOURCE)
        val dock = bounds(DOCK)
        val frame = Frame(phase, index, compose.mainClock.currentTime, panel, source, dock)
        frames += frame
        fun Rect?.csv() = if (this == null) ",,," else "$left,$top,$right,$bottom"
        directory.resolve("timeline.csv").writeText(
            "phase,index,clock_ms,relative_ms,panel_present,panel_left,panel_top,panel_right,panel_bottom," +
                "source_left,source_top,source_right,source_bottom,dock_left,dock_top,dock_right,dock_bottom\n" +
                frames.joinToString("\n") {
                    "${it.phase},${it.index},${it.clock},${it.clock - frames.first().clock},${it.panel != null},${it.panel.csv()},${it.source.csv()},${it.dock.csv()}"
                } + "\n"
        )
        val window = compose.activity.window
        val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        try {
            val result = AtomicInteger(-1)
            compose.runOnIdle { PixelCopy.request(window, bitmap, { result.set(it) }, Handler(Looper.getMainLooper())) }
            compose.waitUntil(5000) { result.get() != -1 }
            check(result.get() == PixelCopy.SUCCESS) { "PixelCopy failed: ${result.get()}" }
            directory.resolve("%03d-%s-%03d.png".format(Locale.ROOT, frames.lastIndex, phase, index)).outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            check(compose.mainClock.currentTime == frame.clock) { "PixelCopy advanced clock; screenshot would not match CSV" }
        } finally {
            bitmap.recycle()
        }
        return frame
    }

    private companion object {
        const val SOURCE = "settings-panel-speed-entry"
        const val PANEL = "settings-detail-page"
        const val CLOSE = "settings-detail-back"
        const val DOCK = "navigation-dock"
    }
}
