package com.github.mihomo.android.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.github.mihomo.android.ui.theme.LoonBg

/**
 * The colour field that glass panels refract.
 *
 * Placement matters more than intensity: a panel only reads as glass when there is something
 * coloured *behind that specific panel*. The blob positions in each palette are tuned against the
 * dashboard layout (traffic cards in the first row, 2-column shortcut grid underneath), so each
 * card picks up a distinct hue instead of every card looking the same shade.
 *
 * Everything is a gradient drawn in `drawBehind` — no bitmaps, no blur filter. Positions are
 * fractions of the canvas so the composition survives tablets and foldables.
 */
@Composable
fun NexusGlassBackground(
    modifier: Modifier = Modifier,
    palette: GlassPaletteSpec = LocalGlassPalette.current,
    backdropRecorder: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    if (!GlassDefaults.enabled) {
        Box(modifier = modifier.fillMaxSize().background(LoonBg), content = content)
        return
    }
    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
      Box(
        modifier = Modifier.fillMaxSize().then(backdropRecorder)
            .background(palette.base)
            .drawBehind {
                val w = size.width
                val h = size.height
                val diag = kotlin.math.hypot(w, h)

                palette.blobs.forEach { blob ->
                    val center = Offset(w * blob.x, h * blob.y)
                    val radius = diag * blob.radius
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to blob.color,
                            0.26f to blob.color.copy(alpha = blob.color.alpha * 0.52f),
                            0.50f to blob.color.copy(alpha = blob.color.alpha * 0.16f),
                            1f to Color.Transparent,
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }

                // Soft veil: keeps type contrast without flattening the field.
                // Light palettes use white (lift), the dark palette uses near-black (settle).
                drawRect(
                    Brush.radialGradient(
                        0f to palette.veil,
                        0.46f to palette.veil.copy(alpha = palette.veil.alpha * 0.60f),
                        0.78f to palette.veil.copy(alpha = palette.veil.alpha * 0.16f),
                        1f to Color.Transparent,
                        center = Offset(w * 0.5f, h * 0.42f),
                        radius = diag * 0.70f
                    )
                )
            }
      )
      content()
    }
}
