package com.github.mihomo.android.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView

/** Only requests feedback when the app preference permits it. */
internal fun requestHapticFeedback(enabled: Boolean, performFeedback: () -> Unit) {
    if (enabled) performFeedback()
}

/**
 * Uses the framework channel, honoring system haptic settings without VIBRATE
 * permission or flags that bypass the user's system preference.
 */
fun View.hapticTick(enabled: Boolean) {
    requestHapticFeedback(enabled) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

/** Retained callbacks read the latest preference instead of a captured old value. */
@Composable
fun rememberHapticTick(enabled: Boolean): () -> Unit {
    val view = LocalView.current
    val currentEnabled = rememberUpdatedState(enabled)
    return remember(view) { { view.hapticTick(currentEnabled.value) } }
}
