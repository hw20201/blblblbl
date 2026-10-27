package com.github.mihomo.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Keeps the dock's existing route visibility in sync with the nested-page transition, while
 * letting it ease out/in instead of disappearing or popping back in on a single frame.
 */
@Composable
internal fun NestedPageDockVisibility(
    nestedPageTransition: MutableTransitionState<Boolean>,
    nestedPageVisible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shouldShow = !nestedPageVisible && !nestedPageTransition.targetState
    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
            slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ),
        exit = fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
            slideOutVertically(
                targetOffsetY = { it / 4 },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ),
        modifier = modifier
    ) {
        Box { content() }
    }
}
