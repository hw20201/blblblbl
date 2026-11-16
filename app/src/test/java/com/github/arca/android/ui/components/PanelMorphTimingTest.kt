package com.github.arca.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.PanelAnimationSpeed
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.glass.NexusGlassBackground
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelMorphTimingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun fastPanelsShareOpeningAndClosingTimeline() = verifyTiming(PanelAnimationSpeed.FAST)
    @Test fun mediumPanelsShareOpeningAndClosingTimeline() = verifyTiming(PanelAnimationSpeed.MEDIUM)
    @Test fun slowPanelsShareOpeningAndClosingTimeline() = verifyTiming(PanelAnimationSpeed.SLOW)

    private data class MotionCase(
        val tag: String,
        val profile: ShortcutMorphMotionProfile,
        val linearEdgeMotion: Boolean = false
    )

    private val cases = listOf(
        MotionCase("dashboard-standard", ShortcutMorphMotionProfile.STANDARD),
        MotionCase("policy-menu", ShortcutMorphMotionProfile.QUVEN_MENU),
        MotionCase("settings-linear", ShortcutMorphMotionProfile.QUVEN_MENU, linearEdgeMotion = true)
    )

    private fun verifyTiming(speed: PanelAnimationSpeed) {
        val visible = mutableStateOf(false)
        val dismissedAt = mutableMapOf<String, Long>()
        cases.forEach { motion ->
            assertEquals("${motion.tag} full opening duration", speed.openDurationMillis,
                panelMorphAnimationSpec(speed, opening = true, from = 0f,
                    linearEdgeMotion = motion.linearEdgeMotion).durationMillis)
            assertEquals("${motion.tag} full closing duration", speed.closeDurationMillis,
                panelMorphAnimationSpec(speed, opening = false, from = 1f,
                    linearEdgeMotion = motion.linearEdgeMotion).durationMillis)
        }
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    NexusGlassBackground {
                        CompositionLocalProvider(
                            LocalPanelAnimationSpeed provides speed,
                            LocalShortcutMotionEnabled provides true
                        ) {
                            cases.forEach { motion ->
                                key(motion.tag) {
                                    ShortcutMorphOverlay(
                                        visible = visible.value,
                                        sourceBounds = Rect(24f, 100f, 124f, 160f),
                                        sourceTitle = "面板", sourceSubtitle = "", sourceIcon = {}, lens = null,
                                        onDismissRequest = { visible.value = false },
                                        onDismissed = { dismissedAt[motion.tag] = compose.mainClock.currentTime },
                                        panelTestTag = motion.tag, motionProfile = motion.profile,
                                        linearEdgeMotion = motion.linearEdgeMotion,
                                        panelHeight = 480.dp, reserveDockSpace = false
                                    ) { Box(Modifier.fillMaxSize()) }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread {
                visible.value = true
                Snapshot.sendApplyNotifications()
            }
            // Mounting is a separate composition pass from the visible state change. Advance one
            // frame at a time until all tagged slabs exist, then measure from that mounted frame.
            var mounted = false
            repeat(12) {
                if (!mounted) {
                    advanceFrame()
                    mounted = cases.all { motion ->
                        compose.onAllNodesWithTag(motion.tag)
                            .fetchSemanticsNodes().isNotEmpty()
                    }
                }
            }
            assertTrue("all morph overlays mount after the visible state notification", mounted)
            val openingStartedAt = compose.mainClock.currentTime
            // Advance one frame at a time so the overlay's mount, layout, and animator all run.
            advanceUntil(openingStartedAt + speed.openDurationMillis / 2)
            // Timing is shared; settings intentionally uses linear edges while the other
            // profiles keep their lift/easing. Intermediate rectangles need not match.
            cases.forEach { motion ->
                val rect = bounds(motion)
                assertTrue("${motion.tag} opening width is in flight: $rect", rect.width > 100f && rect.width < 368f)
                assertTrue("${motion.tag} opening height is in flight: $rect", rect.height > 60f && rect.height < 480f)
            }
            val openingDeadline = openingStartedAt + 64 + speed.openDurationMillis
            advanceUntil(openingDeadline)
            cases.forEach { motion ->
                val rect = bounds(motion)
                assertEquals("${motion.tag} reaches full width by opening deadline", 368f, rect.width, 1f)
                assertEquals("${motion.tag} reaches full height by opening deadline", 480f, rect.height, 1f)
            }

            val closingStartedAt = compose.mainClock.currentTime
            compose.runOnUiThread {
                visible.value = false
                Snapshot.sendApplyNotifications()
            }
            // Closing has a composition/start frame but no opening pre-layout phase.
            advanceFrame()
            advanceUntil(compose.mainClock.currentTime + speed.closeDurationMillis / 2)
            assertTrue("no profile dismisses halfway through closing", dismissedAt.isEmpty())
            cases.forEach { motion ->
                val rect = bounds(motion)
                assertTrue("${motion.tag} closing width is in flight: $rect", rect.width > 100f && rect.width < 368f)
                assertTrue("${motion.tag} closing height is in flight: $rect", rect.height > 60f && rect.height < 480f)
            }
            val closingDeadline = closingStartedAt + speed.closeDurationMillis + 48
            advanceUntil(closingDeadline)
            cases.forEach { motion ->
                compose.onNodeWithTag(motion.tag).assertDoesNotExist()
            }
            assertEquals("every profile delivers its completion callback", cases.map { it.tag }.toSet(), dismissedAt.keys)
            dismissedAt.forEach { (tag, time) ->
                val elapsed = time - closingStartedAt
                assertTrue("$tag full close respects duration plus at most three startup/rounding frames: $elapsed",
                    elapsed in speed.closeDurationMillis.toLong()..(speed.closeDurationMillis + 48).toLong())
            }
            assertEquals("all three trajectories complete on the same animation frame", 1, dismissedAt.values.toSet().size)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun advanceFrame() {
        compose.runOnUiThread { Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun advanceUntil(deadline: Long) {
        while (compose.mainClock.currentTime < deadline) advanceFrame()
    }

    private fun bounds(motion: MotionCase) =
        compose.onNodeWithTag(motion.tag).fetchSemanticsNode().boundsInRoot

    @Test fun speedOrderingAndInterruptedReverseHaveBoundedDurations() {
        val speeds = PanelAnimationSpeed.entries
        assertTrue(speeds.zipWithNext().all { (a, b) -> a.openDurationMillis < b.openDurationMillis })
        assertTrue(speeds.zipWithNext().all { (a, b) -> a.closeDurationMillis < b.closeDurationMillis })
        speeds.forEach { speed ->
            listOf(false, true).forEach { linearEdges ->
                val reverse = panelMorphAnimationSpec(speed, opening = false, from = 0.25f,
                    linearEdgeMotion = linearEdges)
                val reopen = panelMorphAnimationSpec(speed, opening = true, from = 0.75f,
                    linearEdgeMotion = linearEdges)
                assertEquals("reverse duration scales with remaining distance", (speed.closeDurationMillis * 0.25f).roundToInt(), reverse.durationMillis)
                assertEquals("reopen duration scales with remaining distance", (speed.openDurationMillis * 0.25f).roundToInt(), reopen.durationMillis)
                assertEquals(1, panelMorphAnimationSpec(speed, opening = false, from = 0f,
                    linearEdgeMotion = linearEdges).durationMillis)
                assertEquals(1, panelMorphAnimationSpec(speed, opening = true, from = 1f,
                    linearEdgeMotion = linearEdges).durationMillis)
            }
        }
    }
}
