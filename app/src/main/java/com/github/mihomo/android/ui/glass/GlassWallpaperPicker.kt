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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
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
import com.github.mihomo.android.data.SavedWallpaperPalette
import com.github.mihomo.android.data.SettingsManager
import com.github.mihomo.android.ui.glass.NavigationGlassSurface
import com.github.mihomo.android.ui.glass.LensStrength

/** Compact wallpaper editor. Straight fluted ribs only; the curved-refraction style was removed. */
@Composable
fun GlassWallpaperPicker(
    settings: GlassWallpaperSettings,
    onSettingsChanged: (GlassWallpaperSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager(context) }
    var selectedColor by remember { mutableIntStateOf(0) }
    var paletteEditorExpanded by remember { mutableStateOf(false) }
    var savedPalettesExpanded by remember { mutableStateOf(false) }
    var savedPalettes by remember { mutableStateOf(settingsManager.savedWallpaperPalettes) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var paletteName by remember { mutableStateOf("我的壁纸配色") }
    val transition = rememberInfiniteTransition(label = "wallpaper-preview-flow")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(54_000, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "wallpaper-preview-phase"
    )
    val lens = rememberNavigationLens()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("自定义褶皱壁纸已直接用于应用背景", color = LoonTextSecondary, fontSize = 12.sp)

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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("自定义色板", Modifier.weight(1f), color = LoonTextSecondary, fontSize = 13.sp)
            TextButton(
                onClick = { paletteEditorExpanded = !paletteEditorExpanded },
                modifier = Modifier.testTag("wallpaper-toggle-color-grid")
            ) { Text(if (paletteEditorExpanded) "收起色板" else "展开色板") }
            TextButton(
                onClick = { paletteName = "我的壁纸配色"; showSaveDialog = true },
                modifier = Modifier.testTag("wallpaper-save-current-palette")
            ) { Text("收纳配色") }
        }
        if (paletteEditorExpanded) {
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
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("已收纳配色（${savedPalettes.size}）", Modifier.weight(1f), color = LoonTextPrimary, fontSize = 13.sp)
            TextButton(
                onClick = { savedPalettesExpanded = !savedPalettesExpanded },
                modifier = Modifier.testTag("wallpaper-toggle-saved-palettes")
            ) { Text(if (savedPalettesExpanded) "收起" else "展开") }
        }
        if (savedPalettesExpanded) {
            if (savedPalettes.isEmpty()) {
                Text("收纳后的三色配色会显示在这里。", color = LoonTextMuted, fontSize = 11.sp)
            } else {
                savedPalettes.forEachIndexed { index, entry ->
                    SavedWallpaperPaletteRow(
                        entry = entry,
                        index = index,
                        onApply = { onSettingsChanged(entry.applyTo(settings)) },
                        onDelete = { savedPalettes = settingsManager.deleteWallpaperPalette(entry.name) }
                    )
                }
            }
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
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("收纳壁纸配色") },
            text = {
                OutlinedTextField(
                    value = paletteName,
                    onValueChange = { paletteName = it.take(32) },
                    singleLine = true,
                    label = { Text("名称") },
                    modifier = Modifier.testTag("wallpaper-palette-name")
                )
            },
            confirmButton = {
                TextButton(
                    enabled = paletteName.isNotBlank(),
                    onClick = {
                        savedPalettes = settingsManager.saveWallpaperPalette(paletteName, settings)
                        savedPalettesExpanded = true
                        showSaveDialog = false
                    },
                    modifier = Modifier.testTag("wallpaper-confirm-save-palette")
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SavedWallpaperPaletteRow(
    entry: SavedWallpaperPalette,
    index: Int,
    onApply: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).testTag("saved-wallpaper-palette-$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(entry.colorA, entry.colorB, entry.colorC).forEach { argb ->
            Box(Modifier.size(18.dp).clip(CircleShape).background(Color(argb)).border(.6.dp, LoonTextMuted.copy(alpha = .35f), CircleShape))
        }
        Text(entry.name, Modifier.weight(1f).padding(start = 4.dp), color = LoonTextPrimary, maxLines = 1)
        TextButton(onClick = onApply, modifier = Modifier.testTag("apply-wallpaper-palette-$index")) { Text("应用") }
        IconButton(onClick = onDelete, modifier = Modifier.testTag("delete-wallpaper-palette-$index")) {
            Icon(Icons.Default.Delete, contentDescription = "删除 ${entry.name}", tint = LoonTextSecondary)
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
