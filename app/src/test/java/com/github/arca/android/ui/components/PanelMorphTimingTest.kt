package com.github.arca.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelMorphTimingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun fastPanelsShareOpeningAndClosingTimeline() = verifyTiming(PanelAnimationSpeed.FAST)
    @Test fun mediumPanelsShareOpeningAndClosingTimeline() = verifyTiming(PanelAnimationSpeed.MEDIUM)
    @Test fun slowPanelsShareOpeningAndClosingTimeline() = verifyTiming(PanelAnimationSpeed.SLOW)

    private fun verifyTiming(speed: PanelAnimationSpeed) {
        val visible = mutableStateOf(false)
        val dismissed = mutableStateListOf<String>()
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    NexusGlassBackground {
                        CompositionLocalProvider(
                            LocalPanelAnimationSpeed provides speed,
                            LocalShortcutMotionEnabled provides true
                        ) {
                            ShortcutMorphMotionProfile.entries.forEach { profile ->
                                ShortcutMorphOverlay(
                                    visible = visible.value,
                                    sourceBounds = Rect(24f, 100f, 124f, 160f),
                                    sourceTitle = "面板", sourceSubtitle = "", sourceIcon = {}, lens = null,
                                    onDismissRequest = { visible.value = false },
                                    onDismissed = { dismissed.add(profile.name) },
                                    panelTestTag = profile.name, motionProfile = profile,
                                    panelHeight = 480.dp, reserveDockSpace = false
                                ) { Box(Modifier.fillMaxSize()) }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { visible.value = true }
        // Compose + two pre-layout frames + the animator's starting frame.
        compose.mainClock.advanceTimeBy(80)
        compose.mainClock.advanceTimeBy((speed.openDurationMillis / 2).toLong())
        val card = bounds(ShortcutMorphMotionProfile.STANDARD)
        val row = bounds(ShortcutMorphMotionProfile.QUVEN_MENU)
        assertEquals("same normalized width", card.width, row.width, 1f)
        assertEquals("same normalized height", card.height, row.height, 1f)
        assertTrue("opening must be visible", card.width > 100f)
        assertTrue("opening must still be in flight", card.width < 368f)
        compose.mainClock.advanceTimeBy(speed.openDurationMillis.toLong())
        assertEquals(368f, bounds(ShortcutMorphMotionProfile.STANDARD).width, 1f)
        compose.runOnUiThread { visible.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy((speed.closeDurationMillis / 2).toLong())
        assertEquals(bounds(ShortcutMorphMotionProfile.STANDARD).width,
            bounds(ShortcutMorphMotionProfile.QUVEN_MENU).width, 1f)
        compose.mainClock.advanceTimeBy(speed.closeDurationMillis.toLong() + 32)
        ShortcutMorphMotionProfile.entries.forEach { compose.onNodeWithTag(it.name).assertDoesNotExist() }
        assertEquals(2, dismissed.size)
        compose.mainClock.autoAdvance = true
    }

    private fun bounds(profile: ShortcutMorphMotionProfile) =
        compose.onNodeWithTag(profile.name).fetchSemanticsNode().boundsInRoot

    @Test fun speedOrderingAndInterruptedReverseHaveBoundedDurations() {
        val speeds = PanelAnimationSpeed.entries
        assertTrue(speeds.zipWithNext().all { (a, b) -> a.openDurationMillis < b.openDurationMillis })
        assertTrue(speeds.zipWithNext().all { (a, b) -> a.closeDurationMillis < b.closeDurationMillis })
        speeds.forEach { speed ->
            val reverse = panelMorphAnimationSpec(speed, opening = false, from = 0.25f)
            assertTrue(reverse.durationMillis < speed.closeDurationMillis / 2)
            assertEquals(1, panelMorphAnimationSpec(speed, opening = false, from = 0f).durationMillis)
        }
    }
}
