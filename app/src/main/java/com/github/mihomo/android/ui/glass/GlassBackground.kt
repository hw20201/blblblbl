package com.github.mihomo.android.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The colour field that glass panels refract.
 *
 * Placement matters more than intensity: a panel only reads as glass when there is something
 * coloured *behind that specific panel*. The stops below are keyed to the restored dashboard layout
 * (traffic cards in the first row, 2-column shortcut grid underneath), so each card picks up a
 * distinct hue instead of every card looking the same shade of grey.
 *
 * Everything is a gradient drawn in `drawBehind` — no bitmaps, no `filter: blur`. Positions are
 * fractions of the canvas so the composition survives tablets and foldables.
 */
@Composable
fun NexusGlassBackground(
    dark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val palette = if (dark) DarkField else LightField
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(palette.base)
            .drawBehind {
                val w = size.width
                val h = size.height
                val diag = kotlin.math.hypot(w, h)
                palette.blobs.forEach { blob ->
                    val center = Offset(w * blob.x, h * blob.y)
                    val r = diag * blob.radius
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to blob.color,
                            0.26f to blob.color.copy(alpha = blob.color.alpha * 0.52f),
                            0.50f to blob.color.copy(alpha = blob.color.alpha * 0.16f),
                            1f to Color.Transparent,
                            center = center,
                            radius = r
                        ),
                        radius = r,
                        center = center
                    )
                }
                // Soft veil: keeps type contrast without flattening the colour field to black.
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
            },
        contentAlignment = Alignment.TopStart,
        content = content
    )
}

private data class FieldBlob(val color: Color, val x: Float, val y: Float, val radius: Float)

private data class FieldPalette(
    val base: Color,
    val veil: Color,
    val blobs: List<FieldBlob>
)

private val DarkField = FieldPalette(
    base = Color(0xFF05060A),
    veil = Color(0x4D020307),
    blobs = listOf(
        // Brand orange sits behind the NEXUS header.
        FieldBlob(Color(0xD9FF6B00), 0.16f, -0.06f, 0.42f),
        // Upload card, first row left.
        FieldBlob(Color(0x7500C853), 0.06f, 0.30f, 0.34f),
        // Download card, first row right.
        FieldBlob(Color(0x804A69FF), 0.92f, 0.30f, 0.36f),
        // Shortcut grid: violet on the left, teal on the right.
        FieldBlob(Color(0x4D9650FF), 0.02f, 0.66f, 0.40f),
        FieldBlob(Color(0x4700BEAA), 0.98f, 0.70f, 0.38f),
        // Bottom ambient, so the empty lower half isn't dead black.
        FieldBlob(Color(0x334664DC), 0.50f, 1.04f, 0.42f)
    )
)

private val LightField = FieldPalette(
    base = Color(0xFFF2F2F7),
    veil = Color(0x4DFFFFFF),
    blobs = listOf(
        FieldBlob(Color(0x9EFF9240), 0.16f, -0.06f, 0.42f),
        FieldBlob(Color(0x5934DC82), 0.06f, 0.30f, 0.34f),
        FieldBlob(Color(0x596084FF), 0.92f, 0.30f, 0.36f),
        FieldBlob(Color(0x3DB284FF), 0.02f, 0.66f, 0.40f),
        FieldBlob(Color(0x3350D6C8), 0.98f, 0.70f, 0.38f),
        FieldBlob(Color(0x2E7896FF), 0.50f, 1.04f, 0.42f)
    )
)

/** Corner radii, aligned with the Cupertino surface family the restored UI uses. */
object GlassRadius {
    val section = 14.dp   // CupertinoGroupedSection
    val card = 18.dp      // LoonShortcutCard / LoonTrafficCard
    val bar = 32.dp       // LoonFloatingBottomBar
    val sheet = 28.dp
}
