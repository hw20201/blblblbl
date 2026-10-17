package com.github.mihomo.android.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** Real Compose input tests; the injected performer counts requests, not physical motor output. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HapticsComposeTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun providers(enabled: () -> Boolean, ticks: MutableList<Int>, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalHapticFeedbackEnabled provides enabled,
            LocalHapticFeedbackPerformer provides { ticks += 1 },
            content = content,
        )
    }

    @Test fun disabledAndEnabledActionsProduceExactlyZeroAndOneTick() {
        val enabled = mutableStateOf(false)
        val ticks = mutableListOf<Int>()
        var calls = 0
        compose.setContent {
            providers({ enabled.value }, ticks) {
                Button(onClick = rememberHapticAction { calls++ }, modifier = Modifier.testTag("action")) { Text("run") }
            }
        }
        compose.onNodeWithTag("action").performClick()
        compose.runOnIdle { assertEquals(1, calls); assertEquals(0, ticks.size); enabled.value = true }
        compose.onNodeWithTag("action").performClick()
        compose.runOnIdle { assertEquals(2, calls); assertEquals(1, ticks.size) }
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("action").performClick()
        compose.runOnIdle { assertEquals(3, calls); assertEquals(1, ticks.size) }
    }

    @Test fun callbackCanTurnFeedbackOffOrOnBeforeRecomposition() {
        var enabled = true
        val ticks = mutableListOf<Int>()
        lateinit var retained: () -> Unit
        compose.setContent {
            providers({ enabled }, ticks) {
                val action = rememberHapticAction { enabled = !enabled }
                androidx.compose.runtime.SideEffect { retained = action }
                Button(onClick = action, modifier = Modifier.testTag("toggle")) { Text("toggle") }
            }
        }
        // All invocations happen in one UI task, without recomposition between transitions.
        compose.runOnIdle {
            retained(); assertEquals(false, enabled); assertEquals(0, ticks.size)
            retained(); assertEquals(true, enabled); assertEquals(1, ticks.size)
            retained(); assertEquals(false, enabled); assertEquals(1, ticks.size)
            retained(); assertEquals(true, enabled); assertEquals(2, ticks.size)
        }
    }

    @Test fun valueControlReadsPostActionPreferenceAndRetainedActionUpdates() {
        val enabled = mutableStateOf(true)
        val revision = mutableStateOf(1)
        val ticks = mutableListOf<Int>()
        val values = mutableListOf<Int>()
        lateinit var retained: (Boolean) -> Unit
        compose.setContent {
            providers({ enabled.value }, ticks) {
                val capturedRevision = revision.value
                val action = rememberHapticValueAction<Boolean> {
                    values += capturedRevision
                    enabled.value = it
                }
                androidx.compose.runtime.SideEffect { retained = action }
                androidx.compose.material3.Switch(
                    checked = enabled.value,
                    onCheckedChange = action,
                    modifier = Modifier.testTag("switch"),
                )
            }
        }
        compose.onNodeWithTag("switch").performClick()
        compose.runOnIdle { assertEquals(false, enabled.value); assertEquals(0, ticks.size) }
        compose.onNodeWithTag("switch").performClick()
        compose.runOnIdle { assertEquals(true, enabled.value); assertEquals(1, ticks.size); revision.value = 2 }
        compose.waitForIdle()
        compose.runOnIdle {
            retained(false)
            retained(true)
            assertEquals(listOf(1, 1, 2, 2), values)
            assertEquals(2, ticks.size)
        }
    }

    @Test fun nullActionDoesNothingAndPlaceholderHasNoClickableSemantics() {
        val ticks = mutableListOf<Int>()
        lateinit var emptyAction: () -> Unit
        compose.setContent {
            providers({ true }, ticks) {
                val action = rememberHapticAction(null)
                androidx.compose.runtime.SideEffect { emptyAction = action }
                Text("blank", Modifier.hapticClickable(null).testTag("blank"))
            }
        }
        compose.onNodeWithTag("blank").assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        compose.onNodeWithTag("blank").performTouchInput { down(center); up() }
        compose.runOnIdle { emptyAction(); assertEquals(0, ticks.size) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun semanticAccessibilityAndNativeEnterKeyEachTickOnce() {
        val ticks = mutableListOf<Int>()
        var actions = 0
        compose.setContent {
            providers({ true }, ticks) {
                Text("key target", Modifier.hapticClickable { actions++ }.testTag("key"))
            }
        }
        // performClick uses OnClick semantics, the same route used by accessibility services.
        compose.onNodeWithTag("key").performClick()
        compose.runOnIdle { assertEquals(1, actions); assertEquals(1, ticks.size) }
        compose.onNodeWithTag("key").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("key").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(2, actions); assertEquals(2, ticks.size) }
    }

    @Test fun cancelledPressAndConsumedScrollProduceNoClickOrFeedback() {
        val ticks = mutableListOf<Int>()
        var clicks = 0
        compose.setContent {
            providers({ true }, ticks) {
                val scroll = androidx.compose.runtime.remember { androidx.compose.foundation.ScrollState(0) }
                Column(Modifier.height(150.dp).scrollable(scroll, Orientation.Vertical)) {
                    Text("target", Modifier.hapticClickable { clicks++ }.testTag("target"))
                    Text("content")
                }
            }
        }
        compose.onNodeWithTag("target").performTouchInput { down(center); cancel() }
        compose.runOnIdle { assertEquals(0, clicks); assertEquals(0, ticks.size) }
        compose.onNodeWithTag("target").performTouchInput { swipe(center, center.copy(y = center.y - 100f)) }
        compose.runOnIdle { assertEquals(0, clicks); assertEquals(0, ticks.size) }
    }

    @Test fun longPressAndMultitouchFollowActualNativeClickWithoutExtraTicks() {
        val ticks = mutableListOf<Int>()
        var clicks = 0
        compose.setContent {
            providers({ true }, ticks) { Text("target", Modifier.hapticClickable { clicks++ }.testTag("target")) }
        }
        compose.onNodeWithTag("target").performTouchInput { longClick() }
        // Native clickable has no long-click handler: release invokes its real onClick once.
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(1, ticks.size) }
        compose.onNodeWithTag("target").performTouchInput {
            down(0, center); down(1, center.copy(x = center.x + 2f)); up(1); up(0)
        }
        // This native two-pointer sequence also resolves to one real business click.
        compose.runOnIdle { assertEquals(2, clicks); assertEquals(2, ticks.size) }
    }

    @Test fun dialogAndDropdownEffectiveClicksTickOnceAndDialogBackDoesNot() {
        val ticks = mutableListOf<Int>()
        val showDialog = mutableStateOf(true)
        val showMenu = mutableStateOf(false)
        var actions = 0
        compose.setContent {
            providers({ true }, ticks) {
                Text("anchor")
                if (showDialog.value) AlertDialog(
                    onDismissRequest = { showDialog.value = false },
                    confirmButton = {
                        Button(onClick = rememberHapticAction { actions++ }, modifier = Modifier.testTag("confirm")) {
                            Text("Confirm")
                        }
                    },
                    title = { Text("Dialog") },
                )
                DropdownMenu(expanded = showMenu.value, onDismissRequest = { showMenu.value = false }) {
                    DropdownMenuItem(
                        text = { Text("Choose") },
                        onClick = rememberHapticAction { actions++; showMenu.value = false },
                        modifier = Modifier.testTag("choose"),
                    )
                }
            }
        }
        compose.onNodeWithTag("confirm").performClick()
        compose.runOnIdle { assertEquals(1, actions); assertEquals(1, ticks.size) }
        // Dispatch Android dialog back, not a key event to the inner Compose button.
        compose.runOnIdle { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.runOnIdle {
            assertEquals(false, showDialog.value)
            assertEquals(1, actions); assertEquals(1, ticks.size)
            showMenu.value = true
        }
        compose.onNodeWithTag("choose").performClick()
        compose.runOnIdle { assertEquals(2, actions); assertEquals(2, ticks.size); assertEquals(false, showMenu.value) }
    }
}
