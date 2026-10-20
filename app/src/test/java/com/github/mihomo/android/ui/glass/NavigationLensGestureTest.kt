package com.github.mihomo.android.ui.glass

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.components.LoonFloatingBottomBar
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class NavigationLensGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val selected = mutableIntStateOf(0)
    private val selections = mutableListOf<Int>()

    @After fun reset() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    private fun showBar() {
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides "en") {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        LoonFloatingBottomBar(selected.intValue, {
                            selected.intValue = it
                            selections += it
                        }, lens = rememberNavigationLens())
                    }
                }
            }
            }
        }
    }

    @Test fun reversingDragBackToSameTabSettlesWithoutExtraClicks() {
        showBar()
        val dock = compose.onNodeWithTag("navigation-dock")
        dock.performTouchInput {
            down(Offset(width / 6f, height / 2f))
            moveTo(Offset(width * 0.8f, height / 2f), 200)
            moveTo(Offset(width * 0.4f, height / 2f), 200)
            moveTo(Offset(width / 6f, height / 2f), 200)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(0), selections)
        val bounds = dock.getUnclippedBoundsInRoot()
        val lens = compose.onNodeWithTag("navigation-liquid-lens").getUnclippedBoundsInRoot()
        assertEquals(bounds.left.value + 6f, lens.left.value, 1f)
        assertEquals(52f, (lens.bottom - lens.top).value, 1f)
    }

    @Test fun fractionalPositionClampsAtBothEnds() {
        assertEquals(0f, lensDragPosition(-100f, 300f, 3), 0f)
        assertEquals(1f, lensDragPosition(150f, 300f, 3), 0f)
        assertEquals(1.25f, lensDragPosition(175f, 300f, 3), 0f)
        assertEquals(2f, lensDragPosition(700f, 300f, 3), 0f)
        assertEquals(0f, lensDragPosition(10f, 0f, 3), 0f)
    }

    @Test fun dragFollowsFingerAndNavigatesOnlyOnceOnRelease() {
        showBar()
        val dock = compose.onNodeWithTag("navigation-dock")
        val bounds = dock.getUnclippedBoundsInRoot()
        dock.performTouchInput {
            down(Offset(width / 6f, height / 2f))
            moveTo(Offset(width * 0.65f, height / 2f), 300)
        }
        compose.waitForIdle()
        assertEquals(0, selected.intValue)
        assertTrue(selections.isEmpty())
        val moving = compose.onNodeWithTag("navigation-liquid-lens").getUnclippedBoundsInRoot()
        assertTrue("lens follows fractional slot, not selected tab", moving.left + moving.right > bounds.left + bounds.right)
        dock.performTouchInput {
            moveTo(Offset(width * 5f / 6f, height / 2f), 200)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(2), selections)
        assertEquals(2, selected.intValue)
        dock.performTouchInput {
            down(Offset(width * 5f / 6f, height / 2f))
            moveTo(Offset(width / 6f, height / 2f), 300)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(2, 0), selections)
    }

    @Test fun cancellationReturnsToSelectedTabWithoutNavigation() {
        showBar()
        val dock = compose.onNodeWithTag("navigation-dock")
        dock.performTouchInput {
            down(Offset(width / 6f, height / 2f))
            moveTo(Offset(width * 5f / 6f, height / 2f), 300)
            cancel()
        }
        compose.waitForIdle()
        assertTrue(selections.isEmpty())
        assertEquals(0, selected.intValue)
        val bounds = dock.getUnclippedBoundsInRoot()
        val lens = compose.onNodeWithTag("navigation-liquid-lens").getUnclippedBoundsInRoot()
        assertEquals(bounds.left.value + (bounds.right.value - bounds.left.value) / 6f, (lens.left.value + lens.right.value) / 2f, 1f)
    }

    @Test fun tapsAndAccessibilityClicksStillUseExistingTabs() {
        showBar()
        val dock = compose.onNodeWithTag("navigation-dock")
        dock.performTouchInput {
            down(Offset(width / 2f, height / 2f))
            moveTo(Offset(width / 2f + 1f, height / 2f), 50)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(1), selections)
        compose.onNodeWithText("Settings").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        assertEquals(listOf(1, 2), selections)
    }
}
