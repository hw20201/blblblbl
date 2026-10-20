package com.github.mihomo.android.ui.glass

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Local comparison switch. No saved preferences or production settings are migrated. */
internal val LocalShortcutTunedMaterialEnabled = staticCompositionLocalOf { true }

/** Same rounded-rectangle superellipse as the native optics, not a circular second clip. */
internal object ShortcutTunedCardShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(ShortcutTunedParameters.lengthDp(60f) * density.density, min(size.width, size.height) * .5f)
        val path = Path()
        val exponent = 2f / ShortcutTunedParameters.squircle
        path.moveTo(r, 0f)
        path.lineTo(size.width - r, 0f)
        fun corner(cx: Float, cy: Float, start: Double) {
            // Static geometry: a dense contour without an idle path-rebuild animation.
            for (i in 1..64) {
                val angle = start + (PI / 2) * i / 64
                val x = cos(angle).toFloat()
                val y = sin(angle).toFloat()
                path.lineTo(cx + r * sign(x) * abs(x).pow(exponent), cy + r * sign(y) * abs(y).pow(exponent))
            }
        }
        corner(size.width - r, r, -PI / 2)
        path.lineTo(size.width, size.height - r)
        corner(size.width - r, size.height - r, 0.0)
        path.lineTo(r, size.height)
        corner(r, size.height - r, PI / 2)
        path.lineTo(0f, r)
        corner(r, r, PI)
        path.close()
        return Outline.Generic(path)
    }
}
