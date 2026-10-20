package com.github.mihomo.android.ui.glass

import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.roundToInt

private class ContactFeather {
    private var geometry: Triple<Float, Float, Float>? = null
    private val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    fun paint(x: Float, y: Float, radius: Float, contact: Float): Paint {
        val next = Triple(x, y, radius)
        if (next != geometry) {
            mask.shader = RadialGradient(x, y, radius,
                intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
            geometry = next
        }
        mask.alpha = (contact.coerceIn(0f, 1f) * 255f).roundToInt()
        return mask
    }
}

/** API 31/32: replay only the shared background into a bounded feathered contact patch.
 * No sibling/foreground capture; the existing softened background remains underneath.
 * This is a clarity fallback, not the API 33+ refractive depression shader.
 */
@Composable
internal fun BoxScope.ShortcutContactFallback(lens: NavigationLensState, refraction: () -> ShortcutRefraction) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val feather = remember { ContactFeather() }
    Box(Modifier.matchParentSize()
        .onGloballyPositioned { origin = it.positionInRoot(); coordinates = it }
        .drawWithContent {
            val r = refraction()
            val canvas = drawContext.canvas.nativeCanvas
            val validContact = r.contactFollow && r.contact.isFinite() && r.contact > 0f
            if (!validContact || !canvas.isHardwareAccelerated) return@drawWithContent
            val x = ((r.x.takeIf { it.isFinite() } ?: 0f).coerceIn(-1f, 1f) * .5f + .5f) * size.width
            val y = ((r.y.takeIf { it.isFinite() } ?: 0f).coerceIn(-1f, 1f) * .5f + .5f) * size.height
            val radius = shortcutContactRadiusDp(size.width / density, size.height / density) * density
            val bounds = RectF((x - radius).coerceAtLeast(0f), (y - radius).coerceAtLeast(0f),
                (x + radius).coerceAtMost(size.width), (y + radius).coerceAtMost(size.height))
            if (bounds.isEmpty) return@drawWithContent
            clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
                val save = canvas.saveLayer(bounds, null)
                try {
                    replayCardScene(lens, coordinates, origin, anchored = true)
                    canvas.drawRect(bounds, feather.paint(x, y, radius, r.contact))
                } finally { canvas.restoreToCount(save) }
            }
        })
}
