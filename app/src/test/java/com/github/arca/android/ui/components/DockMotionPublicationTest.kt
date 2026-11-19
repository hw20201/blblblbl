package com.github.arca.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.NavigationLensGlobalBounds
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.glass.rememberNavigationLens
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockMotionPublicationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var previousGlassEnabled = true

    @Before fun enableGlass() {
        previousGlassEnabled = GlassDefaults.enabled
        GlassDefaults.enabled = true
    }

    @After fun restoreGlass() { GlassDefaults.enabled = previousGlassEnabled }

    @Test fun motionPublishesChangingBoundsThenSettles() {
        val selected = mutableIntStateOf(0)
        val bounds = mutableListOf<NavigationLensGlobalBounds?>()
        var composedSelection = -1
        val onTabSelected: (Int) -> Unit = { selected.intValue = it }
        val onBounds: (NavigationLensGlobalBounds?) -> Unit = { bounds.add(it) }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val selection = selected.intValue
            SideEffect { composedSelection = selection }
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoonFloatingBottomBar(selection, onTabSelected,
                            lens = rememberNavigationLens(), onLensBoundsChanged = onBounds)
                    }
                }
            }
        }
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.runOnIdle {
            assertTrue("The real lens must publish window bounds", bounds.any { it != null })
            selected.intValue = 2
            Snapshot.sendApplyNotifications()
        }
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.runOnIdle {
            assertEquals("Positive control: the selected input reached composition", 2, composedSelection)
            bounds.clear()
            // DOCK_PROBE_RESET
        }
        repeat(36) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.runOnIdle {
            val distinctBounds = bounds.filterNotNull().distinct().size
            println("dock-motion frames=36 callbacks=${bounds.size} distinctBounds=$distinctBounds")
            assertTrue("Spring motion must keep publishing changing geometry", distinctBounds >= 4)
            // DOCK_PROBE_RESULT
        }
        compose.mainClock.advanceTimeBy(4000L)
        compose.waitForIdle()
        compose.runOnIdle { bounds.clear() }
        repeat(12) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.runOnIdle {
            assertTrue("Settled springs must stop publishing motion", bounds.filterNotNull().distinct().size <= 1)
        }
    }
}
