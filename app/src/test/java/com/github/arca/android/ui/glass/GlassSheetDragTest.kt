package com.github.arca.android.ui.glass

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Checks every drag frame; an endpoint-only swipe misses anchor/height feedback. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-mdpi")
class GlassSheetDragTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var state: SheetState
    private var height = 0
    private var windowHeight = 0
    private var headingTop = 0f
    private var dismissals = 0

    @After fun reset() { GlassDefaults.enabled = true; GlassDefaults.quietPages = true }

    @Test fun glassImportHeightAndAnchorStayFixedDuringListDrag() = checkDrag(true, 0.92f)
    @Test fun opaqueImportHeightAndAnchorStayFixedDuringListDrag() = checkDrag(false, 0.92f)
    @Test fun sourcesHeightAndAnchorStayFixedDuringListDrag() = checkDrag(true, 0.90f)

    @Test fun importSheetStillClosesOnDownwardDrag() {
        show(glass = true, fraction = 0.92f)
        compose.onNodeWithText("Add configuration").performTouchInput {
            down(center)
            moveBy(Offset(0f, 520f), delayMillis = 180)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    private fun show(glass: Boolean, fraction: Float) {
        GlassDefaults.enabled = glass
        GlassDefaults.quietPages = true
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    GlassModalBottomSheet(
                        onDismissRequest = { dismissals++ },
                        sheetState = state,
                        heightFraction = fraction,
                        // Large status/cutout inset forces the offset-dependent top padding path.
                        contentWindowInsets = { WindowInsets(top = 120.dp, bottom = 24.dp) },
                        modifier = Modifier.onGloballyPositioned {
                            height = it.size.height
                            windowHeight = it.findRootCoordinates().size.height
                        }
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            Text("Add configuration", Modifier.onGloballyPositioned { headingTop = it.positionInWindow().y })
                            LazyColumn(Modifier.weight(1f).testTag("drag-list")) {
                                item {
                                    var text by remember { mutableStateOf("") }
                                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().testTag("input"))
                                }
                                items(30) { index -> Text("Source $index", Modifier.fillMaxWidth().height(64.dp)) }
                            }
                            Text("Inspect and import", Modifier.testTag("submit"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun checkDrag(glass: Boolean, fraction: Float) {
        show(glass, fraction)
        val expanded = compose.runOnIdle { state.requireOffset() }
        val initialHeight = height
        assertEquals("whole modal uses its intended fraction", windowHeight * fraction, height.toFloat(), 1f)
        assertEquals("modal remains bottom aligned", windowHeight.toFloat(), expanded + height, 1f)
        assertTrue("top safe inset protects the heading", headingTop >= 119f)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("drag-list").performTouchInput {
            down(Offset(centerX, 180f))
            moveBy(Offset(0f, 60f))
        }
        compose.mainClock.advanceTimeByFrame()
        var previous = compose.runOnIdle { state.requireOffset() }
        repeat(30) { frame ->
            compose.onNodeWithTag("drag-list").performTouchInput { moveBy(Offset(0f, -4f), delayMillis = 16) }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertEquals("height must not change at drag frame $frame", initialHeight, height)
                val offset = state.requireOffset()
                assertTrue("sheet cannot jump backwards at drag frame $frame: $previous -> $offset", offset <= previous + 1f)
                assertTrue("sheet cannot move above its expanded anchor", offset >= expanded - 1f)
                previous = offset
            }
        }
        compose.onNodeWithTag("drag-list").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("drag settles at the same anchor", expanded, state.requireOffset(), 1f)
            assertEquals(initialHeight, height)
        }
        // The form still scrolls and text survives focus/keyboard layout changes.
        compose.onNodeWithTag("input").performTextInput("kept")
        compose.onNodeWithTag("input").assertTextContains("kept")
        compose.onNodeWithTag("submit").assertIsDisplayed()
        compose.onNodeWithTag("drag-list").performTouchInput { swipeUp() }
        compose.onNodeWithText("Source 0").assertIsNotDisplayed()
        compose.runOnIdle { assertEquals(0, dismissals) }
    }
}
