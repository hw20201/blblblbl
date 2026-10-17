package com.github.mihomo.android.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView

/** Preference is a function so retained callbacks can observe its current value. */
val LocalHapticFeedbackEnabled = compositionLocalOf<() -> Boolean> { { false } }

/** Optional feedback override (primarily useful for observing feedback in UI tests). */
val LocalHapticFeedbackPerformer = compositionLocalOf<(() -> Unit)?> { null }

private fun View.performTick(performer: (() -> Unit)?) {
    if (performer != null) performer() else hapticTick(true)
}

/** Runs the action first, then consults the current preference and requests one tick if enabled. */
@Composable
fun rememberHapticAction(onAction: (() -> Unit)?): () -> Unit {
    val view = LocalView.current
    val currentPreference = rememberUpdatedState(LocalHapticFeedbackEnabled.current)
    val currentPerformer = rememberUpdatedState(LocalHapticFeedbackPerformer.current)
    val currentAction = rememberUpdatedState(onAction)
    return remember(view) {
        {
            val action = currentAction.value
            if (action != null) {
                action()
                if (currentPreference.value()) view.performTick(currentPerformer.value)
            }
        }
    }
}

/** Wraps value-bearing controls without capturing an obsolete callback or preference. */
@Composable
fun <T> rememberHapticValueAction(onAction: ((T) -> Unit)?): (T) -> Unit {
    val view = LocalView.current
    val currentPreference = rememberUpdatedState(LocalHapticFeedbackEnabled.current)
    val currentPerformer = rememberUpdatedState(LocalHapticFeedbackPerformer.current)
    val currentAction = rememberUpdatedState(onAction)
    return remember(view) {
        { value: T ->
            val action = currentAction.value
            if (action != null) {
                action(value)
                if (currentPreference.value()) view.performTick(currentPerformer.value)
            }
        }
    }
}

/** A native clickable whose successful action also works for keyboard/accessibility semantics. */
fun Modifier.hapticClickable(onClick: (() -> Unit)?): Modifier = composed {
    if (onClick == null) this else {
        val hapticAction = rememberHapticAction(onClick)
        clickable(onClick = hapticAction)
    }
}

/** Only requests feedback when the app preference permits it. */
internal fun requestHapticFeedback(enabled: Boolean, performFeedback: () -> Unit) {
    if (enabled) performFeedback()
}

/** Uses the framework channel and honors system haptic settings without VIBRATE permission. */
fun View.hapticTick(enabled: Boolean) {
    requestHapticFeedback(enabled) { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
}

/** Compatibility helper for existing explicit tick users. */
@Composable
fun rememberHapticTick(enabled: Boolean): () -> Unit {
    val view = LocalView.current
    val currentEnabled = rememberUpdatedState(enabled)
    return remember(view) { { view.hapticTick(currentEnabled.value) } }
}
