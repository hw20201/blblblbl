package com.github.arca.android.ui.components

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A shallow cast shadow and a lit upper rim, without sampling the backdrop. */
internal fun Modifier.cardDepth(shape: Shape, dark: Boolean, elevation: Dp): Modifier {
    val shadowInk = if (dark) Color.Black else Color(0xFF485870)
    return shadow(elevation, shape, clip = false, ambientColor = shadowInk, spotColor = shadowInk)
        .border(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (dark) .15f else .80f),
                    shadowInk.copy(alpha = if (dark) .24f else .13f)
                )
            ),
            shape
        )
}
