package com.github.mihomo.android.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Swatch picker for the glass palettes.
 *
 * Each chip previews the actual backdrop gradient of its palette rather than a flat colour, because
 * what the user is choosing is the *field* the glass refracts — a row of solid dots would not tell
 * them apart. Six palettes fit in one scrolling row without wrapping.
 */
@Composable
fun GlassPalettePicker(
    selected: GlassPaletteSpec,
    onSelect: (GlassPaletteSpec) -> Unit,
    labelProvider: (GlassPaletteSpec) -> String = { it.labelZh },
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .selectableGroup()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GlassPalettes.all.forEach { palette ->
            GlassPaletteChip(
                palette = palette,
                label = labelProvider(palette),
                isSelected = palette.key == selected.key,
                onClick = { onSelect(palette) }
            )
        }
    }
}

@Composable
private fun GlassPaletteChip(
    palette: GlassPaletteSpec,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val ringWidth by animateDpAsState(
        targetValue = if (isSelected) 2.5.dp else 0.dp,
        animationSpec = tween(180),
        label = "ringWidth"
    )
    val ringColor by animateColorAsState(
        targetValue = if (isSelected) palette.accent else Color.Transparent,
        animationSpec = tween(180),
        label = "ringColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    // Preview the palette's own field, not a flat swatch.
                    Brush.linearGradient(
                        listOf(
                            palette.swatch.getOrElse(0) { palette.base },
                            palette.swatch.getOrElse(1) { palette.accent },
                            palette.swatch.getOrElse(2) { palette.accent }
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
                .border(ringWidth, ringColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // A tiny glass sliver so the chip hints at the material it selects.
            Box(
                Modifier
                    .size(width = 30.dp, height = 14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                lerp(palette.panelStart, Color.White, 0.25f),
                                palette.panelEnd
                            )
                        )
                    )
                    .border(0.5.dp, palette.edge, RoundedCornerShape(7.dp))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) palette.accent else glassTextMuted
        )
    }
}
