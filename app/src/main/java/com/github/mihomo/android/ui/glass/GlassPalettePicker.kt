package com.github.mihomo.android.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Only the solid-color grid: grayscale row, full hue range, dark-to-pastel value rows. */
@Composable
fun GlassPalettePicker(
    selected: GlassPaletteSpec,
    onSelect: (GlassPaletteSpec) -> Unit,
    labelProvider: (GlassPaletteSpec) -> String = { it.labelZh },
    modifier: Modifier = Modifier
) {
    val selectedKey = GlassPalettes.keyForColor(selected.accent)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .semantics { contentDescription = labelProvider(selected) }) {
        // Eager rows avoid a nested lazy viewport cropping swatches on small or large screens.
        ThemeColorGrid.chunked(12).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { color ->
                    val key = GlassPalettes.keyForColor(color)
                    val isSelected = key == selectedKey
                    val contrast = if (color.luminance() < .45f) Color.White else Color.Black
                    Box(
                        Modifier.weight(1f).heightIn(min = 28.dp).aspectRatio(1f)
                            .background(color)
                            .border(if (isSelected) 2.dp else .35.dp,
                                if (isSelected) contrast else contrast.copy(alpha = .10f))
                            .semantics {
                                contentDescription = "#${key.substringAfter(':')}"
                                this.selected = isSelected
                            }
                            .clickable(role = Role.RadioButton) {
                                onSelect(GlassPalettes.byKey(key))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) Text("✓", color = contrast, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

internal val ThemeColorGrid: List<Color> = buildList {
    repeat(12) { index ->
        val value = (255f * (11 - index) / 11).roundToInt()
        add(Color(value, value, value))
    }
    // Twelve distinct hue columns, including cyan/blue/violet/magenta, like the reference grid.
    val hues = listOf(190f, 210f, 240f, 270f, 300f, 345f, 20f, 38f, 48f, 60f, 85f, 125f)
    repeat(8) { row ->
        val lightness = .23f + row * .085f
        hues.forEach { hue -> add(Color.hsl(hue, .80f, lightness)) }
    }
}
