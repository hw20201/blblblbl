package com.github.mihomo.android.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

/**
 * Lightweight haptic helpers. All feedback goes through the framework's
 * haptic-feedback channel, so no VIBRATE permission is required.
 */

/** Fires a VIRTUAL_KEY tick unless the user disabled haptic feedback. */
fun View.hapticTick(enabled: Boolean) {
    if (enabled) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

/** Returns a tick lambda bound to the current view, for use in composables. */
@Composable
fun rememberHapticTick(enabled: Boolean): () -> Unit {
    val view = LocalView.current
    return { view.hapticTick(enabled) }
}
