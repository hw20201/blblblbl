package com.github.arca.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.arca.android.ui.glass.GlassRadius
import com.github.arca.android.ui.glass.GlassSurface
import com.github.arca.android.ui.glass.GlassSurfaceCategory
import com.github.arca.android.ui.glass.LocalShortcutBlurEnabled
import com.github.arca.android.ui.glass.NavigationLensState
import kotlin.math.roundToInt

/** Geometry interpolation kept separate so its endpoint behavior can be unit-tested. */
internal fun interpolateShortcutMorphRect(start: Rect, end: Rect, progress: Float): Rect {
    val fraction = progress.coerceIn(0f, 1f)
    fun mix(from: Float, to: Float) = from + (to - from) * fraction
    return Rect(mix(start.left, end.left), mix(start.top, end.top), mix(start.right, end.right), mix(start.bottom, end.bottom))
}

internal fun shortcutMorphContentAlphas(progress: Float): Pair<Float, Float> {
    val fraction = progress.coerceIn(0f, 1f)
    val oldContent = (1f - fraction / 0.52f).coerceIn(0f, 1f)
    val newContent = ((fraction - 0.30f) / 0.70f).coerceIn(0f, 1f)
    return oldContent to newContent
}

/**
 * A native Compose shortcut-to-floating-panel morph. The source corner stays near
 * the same screen edge; the glass grows in place and its content cross-fades.
 * The existing scene lens is sampled by GlassSurface; this does not capture the
 * whole screen per animation frame. No physical haptics are added here.
 */
@Composable
internal fun ShortcutMorphOverlay(
    visible: Boolean,
    sourceBounds: Rect?,
    sourceTitle: String,
    sourceSubtitle: String,
    sourceIcon: @Composable () -> Unit,
    lens: NavigationLensState?,
    onDismissRequest: () -> Unit,
    onDismissed: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val progress = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }
    val animationsEnabled = LocalShortcutMotionEnabled.current ?: true
    val latestOnDismissed by rememberUpdatedState(onDismissed)

    LaunchedEffect(visible, animationsEnabled) {
        if (visible) {
            mounted = true
            if (animationsEnabled) {
                progress.animateTo(
                    1f,
                    spring(dampingRatio = 0.88f, stiffness = 370f)
                )
            } else {
                progress.snapTo(1f)
            }
        } else if (mounted) {
            if (animationsEnabled) {
                progress.animateTo(
                    0f,
                    spring(dampingRatio = 0.94f, stiffness = 440f)
                )
            } else {
                progress.snapTo(0f)
            }
            mounted = false
            latestOnDismissed()
        }
    }
    BackHandler(enabled = visible, onBack = onDismissRequest)

    if (visible || mounted) {
        BoxWithConstraints(Modifier.fillMaxSize().zIndex(100f)) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val rootWidthPx = with(density) { maxWidth.toPx() }
            val rootHeightPx = with(density) { maxHeight.toPx() }
            val fallbackAnchor = Rect(
                rootWidthPx * 0.5f - with(density) { 24.dp.toPx() },
                rootHeightPx * 0.5f - with(density) { 24.dp.toPx() },
                rootWidthPx * 0.5f + with(density) { 24.dp.toPx() },
                rootHeightPx * 0.5f + with(density) { 24.dp.toPx() }
            )
            val start = sourceBounds ?: fallbackAnchor
            val targetWidth = (maxWidth - 32.dp).coerceAtLeast(0.dp).coerceAtMost(420.dp)
            val targetHeight = (maxHeight * 0.72f).coerceAtMost((maxHeight - 32.dp).coerceAtLeast(0.dp))
            val targetWidthPx = with(density) { targetWidth.toPx() }
            val targetHeightPx = with(density) { targetHeight.toPx() }
            val leftAnchor = (start.left + start.right) * 0.5f <= rootWidthPx * 0.5f
            val targetLeft = if (leftAnchor) {
                with(density) { 16.dp.toPx() }
            } else {
                (rootWidthPx - targetWidthPx - with(density) { 16.dp.toPx() }).coerceAtLeast(0f)
            }
            val bottomInsetPx = with(density) { 12.dp.toPx() }
            val targetTop = start.top.coerceIn(
                0f,
                (rootHeightPx - targetHeightPx - bottomInsetPx).coerceAtLeast(0f)
            )
            val target = Rect(
                targetLeft,
                targetTop,
                targetLeft + targetWidthPx,
                targetTop + targetHeightPx
            )
            val fraction = progress.value.coerceIn(0f, 1f)
            val current = interpolateShortcutMorphRect(start, target, fraction)
            val currentWidth = with(density) { current.width.toDp() }.coerceAtLeast(1.dp)
            val currentHeight = with(density) { current.height.toDp() }.coerceAtLeast(1.dp)
            val currentRadius = GlassRadius.card + (28.dp - GlassRadius.card) * fraction
            val (collapsedAlpha, expandedAlpha) = shortcutMorphContentAlphas(fraction)

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.045f * fraction))
                    .clickable(interactionSource = null, indication = null, onClick = onDismissRequest)
            )
            GlassSurface(
                modifier = Modifier
                    .offset { IntOffset(current.left.roundToInt(), current.top.roundToInt()) }
                    .size(currentWidth, currentHeight)
                    .zIndex(1f)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        }
                    }
                    .testTag("quick-node-selector-sheet"),
                shape = RoundedCornerShape(currentRadius),
                lens = lens,
                blurEnabled = LocalShortcutBlurEnabled.current,
                shortcutContactFrost = true,
                shortcutDockLens = true,
                surfaceCategory = GlassSurfaceCategory.SHORTCUT_CARDS
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (fraction < 0.60f) {
                        Box(Modifier.matchParentSize().graphicsLayer { alpha = collapsedAlpha }) {
                            ShortcutCardContent(
                                title = sourceTitle,
                                subtitle = sourceSubtitle,
                                icon = sourceIcon,
                                isEditing = false,
                                onDelete = null
                            )
                        }
                    }
                    if (fraction > 0.30f) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = expandedAlpha }
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                                .testTag("quick-node-selector-content")
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}
