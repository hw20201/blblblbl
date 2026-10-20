package com.github.mihomo.android.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.github.mihomo.android.ui.theme.LoonBg
import kotlin.random.Random

/** A reproducible seed may be supplied to previews; app sessions start a fresh route. */
internal val LocalSoftMeshSeed = staticCompositionLocalOf<Long?> { null }

/** The approved pale ice-blue/lavender field, independent of legacy preset accent names. */
internal val IceLavenderMesh = listOf(
    GlassBlob(Color(0xA38AB9F5), 0.12f, 0.18f, 0.34f),
    GlassBlob(Color(0x859E91F0), 0.80f, 0.64f, 0.32f),
    GlassBlob(Color(0x99AFD5FF), 0.63f, 0.20f, 0.38f),
    GlassBlob(Color(0x70DBB8F2), 0.20f, 0.76f, 0.32f),
    GlassBlob(Color(0x70B6E0ED), 0.82f, 0.82f, 0.34f),
    GlassBlob(Color(0x73B0A9F5), 0.40f, 0.50f, 0.30f)
)

/**
 * Background-only soft mesh: cached radial brushes slowly translate, stretch and rotate.
 * No full-page blur, bitmap, stripe contours, waves or shader noise. Foreground content is a
 * separate sibling, outside both the background recorder and all mesh transforms.
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
    val seconds = rememberSoftMeshTime()
    val seed = LocalSoftMeshSeed.current
    val motion = remember(seed) { SoftMeshMotion(seed ?: Random.nextLong()) }
    val structured = LocalStructuredMeshEnabled.current
    val baseBlobs = if (palette.isDark || palette.key.startsWith("custom:")) palette.blobs else IceLavenderMesh
    val blobs = remember(palette, structured) {
        if (structured) structuredMeshCells(palette, baseBlobs) else baseBlobs
    }
    val base = if (palette.isDark || palette.key.startsWith("custom:")) palette.base else Color(0xFFECF3FF)
    val veil = if (palette.isDark) palette.veil.copy(alpha = if (structured) .08f else .16f)
        else Color.White.copy(alpha = if (structured) .035f else .10f)
    // Unit-space brushes stay cached across frames. Transform the canvas, not the foreground.
    val brushes = remember(blobs, structured) {
        blobs.map { blob ->
            if (structured) Brush.radialGradient(
                0f to blob.color,
                .44f to blob.color.copy(alpha = blob.color.alpha * .90f),
                .64f to blob.color.copy(alpha = blob.color.alpha * .48f),
                .80f to blob.color.copy(alpha = blob.color.alpha * .08f),
                1f to Color.Transparent,
                center = Offset.Zero, radius = 1f
            ) else Brush.radialGradient(
                0f to blob.color,
                0.38f to blob.color.copy(alpha = blob.color.alpha * 0.66f),
                0.72f to blob.color.copy(alpha = blob.color.alpha * 0.20f),
                1f to blob.color.copy(alpha = 0f),
                center = Offset.Zero,
                radius = 1f
            )
        }
    }
    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        Box(
            modifier = Modifier.fillMaxSize().then(backdropRecorder)
                .background(base)
                .drawBehind {
                    val time = seconds.value // draw-phase read; no foreground recomposition/layout.
                    val diag = kotlin.math.hypot(size.width, size.height)
                    blobs.forEachIndexed { index, blob ->
                        val pose = motion.sample(index, time)
                        val radius = (if (structured) size.minDimension else diag) * blob.radius
                        val x = if (structured) blob.x + (pose.x - .5f) * .16f else pose.x
                        val y = if (structured) blob.y + (pose.y - .5f) * .08f else pose.y
                        translate(size.width * x, size.height * y) {
                            rotate(pose.rotation, Offset.Zero) {
                                // Broad oblique colour ribbons, not tiny diagnostic grids.
                                val stretchX = if (structured) 1.22f else 1f
                                val stretchY = if (structured) .82f else 1f
                                scale(radius * pose.scaleX * stretchX, radius * pose.scaleY * stretchY, Offset.Zero) {
                                    drawCircle(brushes[index], radius = 1f, center = Offset.Zero)
                                }
                            }
                        }
                    }
                    // Uniform low-strength veil: no moving rings or flashing highlights.
                    drawRect(veil)
                }
        )
        content()
    }
}
