package com.github.mihomo.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CupertinoComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchExposesStateAndHonorsLiveHapticPreference() {
        val checked = mutableStateOf(false)
        val enabled = mutableStateOf(false)
        var ticks = 0
        var calls = 0
        compose.setContent {
            CompositionLocalProvider(
                LocalHapticFeedbackEnabled provides { enabled.value },
                LocalHapticFeedbackPerformer provides { ticks++ },
            ) {
                CupertinoSwitch(checked.value, { checked.value = it; calls++ }, Modifier.testTag("switch"))
            }
        }
        compose.onNodeWithTag("switch").assertIsOff().performClick()
        compose.onNodeWithTag("switch").assertIsOn()
        compose.runOnIdle { assertEquals(1, calls); assertEquals(0, ticks); enabled.value = true }
        compose.onNodeWithTag("switch").performClick().assertIsOff()
        compose.runOnIdle { assertEquals(2, calls); assertEquals(1, ticks) }
    }

    @Test fun disabledSwitchCannotTriggerCallbackOrHaptics() {
        var calls = 0
        var ticks = 0
        compose.setContent {
            CompositionLocalProvider(
                LocalHapticFeedbackEnabled provides { true },
                LocalHapticFeedbackPerformer provides { ticks++ },
            ) {
                CupertinoSwitch(false, { calls++ }, Modifier.testTag("disabled"), enabled = false)
            }
        }
        compose.onNodeWithTag("disabled").assertIsNotEnabled().assertIsOff().performClick()
        compose.runOnIdle { assertEquals(0, calls); assertEquals(0, ticks) }
    }

    @Test fun cellsAndSegmentsRespectHapticPreferenceAndOnlyChangeSelectionOnce() {
        val feedback = mutableStateOf(false)
        val selected = mutableStateOf("All")
        var ticks = 0
        var selections = 0
        var cellCalls = 0
        compose.setContent {
            CompositionLocalProvider(
                LocalHapticFeedbackEnabled provides { feedback.value },
                LocalHapticFeedbackPerformer provides { ticks++ },
            ) {
                Column {
                    CupertinoCell(title = "Open", onClick = { cellCalls++ })
                    CupertinoSegmentedControl(
                        items = listOf("All", "Proxy"), selectedItem = selected.value,
                        onItemSelected = { selected.value = it; selections++ }, labelProvider = { it },
                    )
                }
            }
        }
        compose.onNodeWithText("Open").performClick()
        compose.runOnIdle { assertEquals(1, cellCalls); assertEquals(0, ticks); feedback.value = true }
        compose.onNodeWithText("Open").performClick()
        compose.onNodeWithText("Proxy").performClick().assertIsSelected()
        compose.onNodeWithText("Proxy").performClick()
        compose.runOnIdle { assertEquals(2, cellCalls); assertEquals(1, selections); assertEquals(2, ticks) }
    }
}
