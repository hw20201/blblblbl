package com.github.mihomo.android.ui.components

import android.os.Build
import androidx.compose.ui.composed
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.github.mihomo.android.ui.theme.LocalGlassEnabled
import com.github.mihomo.android.ui.theme.LocalLoonColors

/** Dedicated wallpaper display list; it never records a pane or any labels. */
internal val LocalGlassMaterialBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

internal fun Modifier.glassScreenBackground(): Modifier = composed {
    val backdrop = LocalGlassMaterialBackdrop.current
    val dark = LocalLoonColors.current.isDark
    val enabled = LocalGlassEnabled.current
    val base = if (dark) Color(0xFF101722) else Color(0xFFF0F5FC)
    val blue = if (dark) Color(0xFF19354D) else Color(0xFFD6ECFF)
    val violet = if (dark) Color(0xFF242B45) else Color(0xFFE5E5FA)
    onGloballyPositioned { if (backdrop != null) backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            fun DrawScope.wallpaper() {
                drawRect(base)
                if (enabled) {
                    drawRect(Brush.radialGradient(listOf(blue, Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.30f), radius = size.width * 0.95f))
                    drawRect(Brush.radialGradient(listOf(violet, Color.Transparent),
                        center = Offset(size.width * 0.90f, size.height * 0.64f), radius = size.width * 0.90f))
                    drawRect(Brush.radialGradient(listOf(blue.copy(alpha = 0.65f), Color.Transparent),
                        center = Offset(size.width * 0.38f, size.height * 0.98f), radius = size.width * 0.68f))
                }
            }
            val hardwareBlur = Build.VERSION.SDK_INT >= 31 && drawContext.canvas.nativeCanvas.isHardwareAccelerated
            if (enabled && backdrop != null && hardwareBlur) {
                backdrop.layer.record { wallpaper() }
                drawLayer(backdrop.layer)
            } else wallpaper()
            drawContent()
        }
}
