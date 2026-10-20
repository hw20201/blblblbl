package com.github.mihomo.android.ui

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The dock exists only when the shared nested-page transition is fully back at its hidden state. */
@Composable
internal fun NestedPageDockVisibility(
    nestedPageTransition: MutableTransitionState<Boolean>,
    nestedPageVisible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (nestedPageVisible) return
    if (nestedPageTransition.isIdle &&
        !nestedPageTransition.currentState && !nestedPageTransition.targetState
    ) {
        Box(modifier = modifier) { content() }
    }
}
