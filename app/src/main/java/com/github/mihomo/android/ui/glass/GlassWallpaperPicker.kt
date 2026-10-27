package com.github.mihomo.android.ui.glass

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.github.mihomo.android.ui.theme.LoonTextMuted
import com.github.mihomo.android.ui.theme.LoonTextPrimary
import com.github.mihomo.android.ui.theme.LoonTextSecondary
import com.github.mihomo.android.ui.glass.NavigationGlassSurface
import com.github.mihomo.android.ui.glass.LensStrength

/** Compact wallpaper editor. Straight fluted ribs only; the curved-refraction style was removed. */
@Composable
fun GlassWallpaperPicker(
    settings: GlassWallpaperSettings,
    onSettingsChanged: (GlassWallpaperSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedColor by remember { mutableIntStateOf(0) }
    val transition = rememberInfiniteTransition(label = "wallpaper-preview-flow")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(54_000, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "wallpaper-preview-phase"
    )
    val lens = rememberNavigationLens()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("使用自定义壁纸", color = LoonTextPrimary, fontSize = 13.sp)
                Text("关闭后恢复原有柔和背景", color = LoonTextMuted, fontSize = 11.sp)
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = { onSettingsChanged(settings.copy(enabled = it)) },
                modifier = Modifier.testTag("wallpaper-enabled-toggle")
            )
        }

        Text("纹理样式", color = LoonTextSecondary, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.mode == GlassWallpaperMode.FLUTED,
                onClick = { onSettingsChanged(settings.copy(enabled = true, mode = GlassWallpaperMode.FLUTED)) },
                label = { Text("直纹褶皱") },
                modifier = Modifier.testTag("wallpaper-mode-fluted")
            )
        }

        Text("混合主色", color = LoonTextSecondary, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (index in 0..2) {
                val selected = index == selectedColor
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Color.White.copy(alpha = .52f) else Color.Transparent)
                        .border(if (selected) 1.dp else .5.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .6f) else LoonTextMuted.copy(alpha = .22f), RoundedCornerShape(14.dp))
                        .clickable(role = Role.RadioButton) { selectedColor = index }
                        .semantics { contentDescription = "主色 ${index + 1}"; this.selected = selected }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag("wallpaper-color-slot-$index"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(settings.colorAt(index)).border(.7.dp, LoonTextMuted.copy(alpha = .35f), CircleShape))
                    Text("色${index + 1}", color = LoonTextPrimary, fontSize = 12.sp, maxLines = 1)
                    if (selected) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text("选中一个色位，再用色板或 RGB 滑杆调色", color = LoonTextMuted, fontSize = 11.sp)
        GlassPalettePicker(
            selected = GlassPalettes.byKey(GlassPalettes.keyForColor(settings.colorAt(selectedColor))),
            labelProvider = { "混合主色 ${selectedColor + 1}" },
            onSelect = { spec -> onSettingsChanged(settings.withColor(selectedColor, spec.accent)) },
            modifier = Modifier.testTag("wallpaper-color-grid")
        )
        val activeColor = settings.colorAt(selectedColor)
        RgbChannelSlider("R", activeColor.red, Modifier.testTag("wallpaper-rgb-red")) { channel ->
            onSettingsChanged(settings.withColor(selectedColor, Color(channel / 255f, activeColor.green, activeColor.blue)))
        }
        RgbChannelSlider("G", activeColor.green, Modifier.testTag("wallpaper-rgb-green")) { channel ->
            onSettingsChanged(settings.withColor(selectedColor, Color(activeColor.red, channel / 255f, activeColor.blue)))
        }
        RgbChannelSlider("B", activeColor.blue, Modifier.testTag("wallpaper-rgb-blue")) { channel ->
            onSettingsChanged(settings.withColor(selectedColor, Color(activeColor.red, activeColor.green, channel / 255f)))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("缓慢色彩波动", color = LoonTextPrimary, fontSize = 13.sp)
                Text("轻微移动混色边界；关闭后保持静态", color = LoonTextMuted, fontSize = 11.sp)
            }
            Switch(
                checked = settings.colorFlow,
                onCheckedChange = { onSettingsChanged(settings.copy(enabled = true, colorFlow = it)) },
                modifier = Modifier.testTag("wallpaper-color-flow-toggle")
            )
        }

        Box(Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(22.dp)).testTag("wallpaper-live-preview")) {
            Canvas(Modifier.fillMaxSize().recordNavigationScene(lens)) {
                val seconds = if (settings.colorFlow) progress / (2.0 * Math.PI).toFloat() * 54f else 0f
                drawGlassWallpaper(settings, seconds)
            }
            NavigationGlassSurface(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(.72f).height(78.dp)
                    .testTag("wallpaper-glass-sample"),
                lens = lens,
                shape = RoundedCornerShape(20.dp),
                cornerRadius = 20.dp,
                strength = LensStrength.STANDARD,
                surfaceCategory = GlassSurfaceCategory.SHORTCUT_CARDS,
                opticsOverride = LocalGlassSettings.current.opticsFor(GlassSurfaceCategory.SHORTCUT_CARDS),
                frosted = false
            ) {
                Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text("NEXUS", color = if (settings.colorAt(0).luminance() < .3f) Color.White else Color(0xFF273041), fontSize = 18.sp)
                    Text("直纹 · 边缘高光", color = LoonTextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}


@Composable
private fun RgbChannelSlider(label: String, channel: Float, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val value = (channel * 255f).roundToInt()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LoonTextSecondary, fontSize = 12.sp, modifier = Modifier.width(20.dp))
        Slider(
            value = channel.coerceIn(0f, 1f) * 255f,
            onValueChange = { onChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            steps = 254,
            modifier = Modifier.weight(1f).testTag("wallpaper-rgb-slider-$label")
        )
        Text(value.toString(), color = LoonTextMuted, fontSize = 11.sp, modifier = Modifier.width(32.dp))
    }
}
