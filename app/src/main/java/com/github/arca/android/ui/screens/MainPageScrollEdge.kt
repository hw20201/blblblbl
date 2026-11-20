package com.github.arca.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.arca.android.ui.components.settingsMorphTopBackdropBlur
import com.github.arca.android.ui.glass.GlassDefaults
import io.github.neoruaa.glasmio.glass.render.Backdrop

/** Shared pinned edge for all three main pages. Only the scroll body records into [backdrop]. */
@Composable
internal fun MainPageScrollEdge(
    backdrop: Backdrop?,
    headerHeight: Dp,
    background: Color,
    tag: String,
    modifier: Modifier = Modifier,
    scrollOffsetPx: () -> Int
) {
    val latestOffset by rememberUpdatedState(scrollOffsetPx)
    val glasmioBlurAvailable = GlassDefaults.enabled
    val edge = modifier.fillMaxWidth().height(headerHeight + 28.dp)
    if (glasmioBlurAvailable && backdrop != null) {
        Box(edge.graphicsLayer {
            // Read scroll in the layer, so each pixel of scrolling does not recompose the page.
            alpha = (latestOffset() / (96.dp.toPx())).coerceIn(0f, 1f)
        }.settingsMorphTopBackdropBlur(backdrop).testTag(tag))
    }
    Box(edge.drawWithCache {
        val headerEnd = (headerHeight.toPx() / size.height).coerceIn(0f, 0.99f)
        val resting = Brush.verticalGradient(
            0f to background, headerEnd to background, 1f to Color.Transparent
        )
        val scrolled = Brush.verticalGradient(
            0f to background.copy(alpha = 0.24f),
            0.65f to background.copy(alpha = 0.12f),
            1f to Color.Transparent
        )
        val revealDistance = 96.dp.toPx()
        onDrawBehind {
            val reveal = if (glasmioBlurAvailable && backdrop != null) {
                (latestOffset() / revealDistance).coerceIn(0f, 1f)
            } else 0f
            drawRect(resting, alpha = 1f - reveal)
            drawRect(scrolled, alpha = reveal)
        }
    })
}
