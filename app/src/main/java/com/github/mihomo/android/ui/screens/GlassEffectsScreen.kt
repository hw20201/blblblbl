package com.github.mihomo.android.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mihomo.android.ui.glass.FROSTED_PAGE_TITLE_FILL_ALPHA
import com.github.mihomo.android.ui.glass.GlassMotionTuning
import com.github.mihomo.android.ui.glass.GlassOptics
import com.github.mihomo.android.ui.glass.GlassSettings
import com.github.mihomo.android.ui.glass.GlassSurfaceCategory
import com.github.mihomo.android.ui.glass.LensStrength
import com.github.mihomo.android.ui.glass.NavigationGlassSurface
import com.github.mihomo.android.ui.glass.PageTitleGlass
import com.github.mihomo.android.ui.glass.LocalGlassSettings
import com.github.mihomo.android.ui.glass.glassPalette
import com.github.mihomo.android.ui.glass.recordNavigationScene
import com.github.mihomo.android.ui.glass.rememberNavigationLens
import com.github.mihomo.android.ui.theme.LoonTextMuted
import com.github.mihomo.android.ui.theme.LoonTextPrimary
import com.github.mihomo.android.ui.theme.LoonTextSecondary
import com.github.mihomo.android.ui.theme.IosBlue
import androidx.compose.ui.geometry.Offset

/** Dedicated, live glass editor. Surface-family overrides inherit until explicitly enabled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassEffectsScreen(
    settings: GlassSettings,
    onSettingsChanged: (GlassSettings) -> Unit,
    onBack: () -> Unit,
    scrollState: ScrollState = rememberScrollState()
) {
    val safe = settings.clamped()
    var selected by remember { mutableStateOf(GlassSurfaceCategory.SHORTCUT_CARDS) }
    val hasOverride = selected in safe.surfaceOverrides
    val optics = safe.opticsFor(selected)
    val palette = glassPalette
    val lens = rememberNavigationLens()
    val titleScene = rememberNavigationLens()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerInset = with(LocalDensity.current) { headerHeightPx.toDp() }
    val categories = listOf(
        GlassSurfaceCategory.SHORTCUT_CARDS to "快捷卡片",
        GlassSurfaceCategory.TITLE_CAPSULES to "标题胶囊",
        GlassSurfaceCategory.BOTTOM_NAVIGATION to "底部导航",
        GlassSurfaceCategory.LARGE_PANELS to "大面板"
    )
    // Keep the live sample driven by this editor's exact state, independent of its caller.
    CompositionLocalProvider(LocalGlassSettings provides safe) {
    Box(Modifier.fillMaxSize().background(Color.Transparent).testTag("glass-effects-root")) {
        Column(
            Modifier.fillMaxSize()
                .recordNavigationScene(titleScene)
                .testTag("glass-effects-scroll")
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(
                    top = headerInset + 20.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                )
        ) {
            Text("实时预览", color = LoonTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 6.dp))
            Box(Modifier.fillMaxWidth().height(142.dp).clip(RoundedCornerShape(26.dp)).testTag("glass-effects-preview")) {
                Canvas(Modifier.fillMaxSize().recordNavigationScene(lens)) {
                    drawRect(if (palette.isDark) Color(0xFF101A2A) else Color(0xFFE5EAF2))
                    val spacing = 20.dp.toPx()
                    val colors = listOf(Color(0xFF3878E8), Color(0xFFF28155), Color(0xFF61A879), Color(0xFF9B6AC7))
                    var x = -size.height
                    var index = 0
                    while (x < size.width + size.height) {
                        drawLine(colors[index % colors.size].copy(alpha = .9f), Offset(x, 0f), Offset(x + size.height, size.height), 3.dp.toPx())
                        x += spacing
                        index++
                    }
                    drawCircle(Color.White.copy(alpha = .65f), radius = 19.dp.toPx(), center = Offset(size.width * .78f, size.height * .35f))
                    drawCircle(Color(0xFF5656C8).copy(alpha = .75f), radius = 25.dp.toPx(), center = Offset(size.width * .2f, size.height * .72f))
                }
                NavigationGlassSurface(
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(.86f).height(90.dp).semantics { contentDescription = "玻璃预览：折射 ${optics.refractionDp} dp，色散 ${optics.dispersionDp} dp" }
                        .testTag("glass-effects-live-lens"),
                    lens = lens,
                    shape = RoundedCornerShape(24.dp),
                    cornerRadius = 24.dp,
                    strength = LensStrength.STRONG,
                    surfaceCategory = selected,
                    // Feed the editor's effective value directly to the same renderer used
                    // by the app surfaces. A fixed 12dp frost blur masked dispersion changes.
                    opticsOverride = optics,
                    frosted = false
                ) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween) {
                        Text(categories.first { it.first == selected }.second, color = LoonTextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("折射与色散会即时显示", color = LoonTextSecondary, fontSize = 12.sp)
                    }
                }
            }
            Text("选择玻璃部位", color = LoonTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp))
            // Keep all four surface families visible and directly selectable; a clipped horizontal
            // chip row made the first/last categories easy to miss on narrow screens.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.chunked(2).forEach { rowCategories ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowCategories.forEach { (category, label) ->
                            FilterChip(
                                selected = selected == category,
                                onClick = { selected = category },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f).testTag("glass-surface-${category.name.lowercase()}")
                            )
                        }
                        if (rowCategories.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            GlassEditorCard(title = "通用材质", subtitle = "作为所有部位的默认值；单独覆盖的部位不会受影响。") {
                GlassSlider("折射强度", safe.globalOptics.refractionDp, GlassOptics.MIN_REFRACTION_DP..GlassOptics.MAX_REFRACTION_DP,
                    "${"%.1f".format(java.util.Locale.ROOT, safe.globalOptics.refractionDp)} dp", "glass-global-refraction") {
                    onSettingsChanged(safe.copy(globalOptics = safe.globalOptics.copy(refractionDp = it)).clamped())
                }
                GlassSlider("色散", safe.globalOptics.dispersionDp, GlassOptics.MIN_DISPERSION_DP..GlassOptics.MAX_DISPERSION_DP,
                    "${"%.1f".format(java.util.Locale.ROOT, safe.globalOptics.dispersionDp)} dp", "glass-global-dispersion") {
                    onSettingsChanged(safe.copy(globalOptics = safe.globalOptics.copy(dispersionDp = it)).clamped())
                }
                GlassSlider("边缘带宽", safe.globalOptics.edgeWidthDp, GlassOptics.MIN_EDGE_WIDTH_DP..GlassOptics.MAX_EDGE_WIDTH_DP,
                    "${safe.globalOptics.edgeWidthDp.toInt()} dp", "glass-global-edge-width") {
                    onSettingsChanged(safe.copy(globalOptics = safe.globalOptics.copy(edgeWidthDp = it)).clamped())
                }
                GlassSlider("边缘高光", safe.globalOptics.edgeLightMultiplier, GlassOptics.MIN_EDGE_LIGHT_MULTIPLIER..GlassOptics.MAX_EDGE_LIGHT_MULTIPLIER,
                    "${"%.2f".format(java.util.Locale.ROOT, safe.globalOptics.edgeLightMultiplier)}×", "glass-global-edge-light") {
                    onSettingsChanged(safe.copy(globalOptics = safe.globalOptics.copy(edgeLightMultiplier = it)).clamped())
                }
            }
            GlassEditorCard(title = "部位微调 · ${categories.first { it.first == selected }.second}",
                subtitle = "关闭时继承通用材质；打开后只影响这一类玻璃。") {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("使用独立参数", color = LoonTextPrimary, fontSize = 14.sp)
                    Switch(checked = hasOverride, onCheckedChange = { enabled ->
                        onSettingsChanged(safe.withOptics(selected, if (enabled) optics else null))
                    }, modifier = Modifier.testTag("glass-surface-override-switch"))
                }
                if (hasOverride) {
                    GlassSlider("折射强度", optics.refractionDp, GlassOptics.MIN_REFRACTION_DP..GlassOptics.MAX_REFRACTION_DP,
                        "${"%.1f".format(java.util.Locale.ROOT, optics.refractionDp)} dp", "glass-surface-refraction") {
                        onSettingsChanged(safe.withOptics(selected, optics.copy(refractionDp = it)))
                    }
                    GlassSlider("色散", optics.dispersionDp, GlassOptics.MIN_DISPERSION_DP..GlassOptics.MAX_DISPERSION_DP,
                        "${"%.1f".format(java.util.Locale.ROOT, optics.dispersionDp)} dp", "glass-surface-dispersion") {
                        onSettingsChanged(safe.withOptics(selected, optics.copy(dispersionDp = it)))
                    }
                    GlassSlider("边缘带宽", optics.edgeWidthDp, GlassOptics.MIN_EDGE_WIDTH_DP..GlassOptics.MAX_EDGE_WIDTH_DP,
                        "${optics.edgeWidthDp.toInt()} dp", "glass-surface-edge-width") {
                        onSettingsChanged(safe.withOptics(selected, optics.copy(edgeWidthDp = it)))
                    }
                    GlassSlider("边缘高光", optics.edgeLightMultiplier, GlassOptics.MIN_EDGE_LIGHT_MULTIPLIER..GlassOptics.MAX_EDGE_LIGHT_MULTIPLIER,
                        "${"%.2f".format(java.util.Locale.ROOT, optics.edgeLightMultiplier)}×", "glass-surface-edge-light") {
                        onSettingsChanged(safe.withOptics(selected, optics.copy(edgeLightMultiplier = it)))
                    }
                }
                TextButton(onClick = { onSettingsChanged(safe.withOptics(selected, null)) }, modifier = Modifier.testTag("glass-reset-surface")) {
                    Text("重置此部位")
                }
            }
            GlassEditorCard(title = "跟手动态", subtitle = "仅影响快捷卡片的跟手倾斜；幅度有上限，避免晃动过头。") {
                GlassSlider("倾斜幅度", safe.motion.tiltGain, GlassMotionTuning.MIN_TILT_GAIN..GlassMotionTuning.MAX_TILT_GAIN,
                    "${"%.2f".format(java.util.Locale.ROOT, safe.motion.tiltGain)}×", "glass-motion-gain") {
                    onSettingsChanged(safe.copy(motion = safe.motion.copy(tiltGain = it)).clamped())
                }
                GlassSlider("最大倾角", safe.motion.maxTiltDegrees, GlassMotionTuning.MIN_MAX_TILT_DEGREES..GlassMotionTuning.MAX_MAX_TILT_DEGREES,
                    "${"%.1f".format(java.util.Locale.ROOT, safe.motion.maxTiltDegrees)}°", "glass-motion-max-tilt") {
                    onSettingsChanged(safe.copy(motion = safe.motion.copy(maxTiltDegrees = it)).clamped())
                }
                GlassSlider("回正平滑", safe.motion.smoothing, GlassMotionTuning.MIN_SMOOTHING..GlassMotionTuning.MAX_SMOOTHING,
                    "${"%.2f".format(java.util.Locale.ROOT, safe.motion.smoothing)}", "glass-motion-smoothing") {
                    onSettingsChanged(safe.copy(motion = safe.motion.copy(smoothing = it)).clamped())
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onSettingsChanged(GlassSettings.DEFAULT) }, modifier = Modifier.testTag("glass-effects-reset-all")) {
                    Text("恢复全部默认")
                }
            }
        }
        Box(
            Modifier.fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .onSizeChanged { headerHeightPx = it.height }
                .zIndex(1f)
                .testTag("glass-effects-pinned-header")
        ) {
            PageTitleGlass(
                lens = titleScene,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("glass-effects-title-capsule"),
                frostedFillAlpha = FROSTED_PAGE_TITLE_FILL_ALPHA
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("glass-settings-back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = LoonTextPrimary)
                    }
                    Text("玻璃效果", color = LoonTextPrimary, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                }
            }
        }
    }
    }
}

@Composable
private fun GlassEditorCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(22.dp),
        color = if (glassPalette.isDark) Color(0xFF202938).copy(alpha = .86f) else Color.White.copy(alpha = .76f),
        tonalElevation = 0.dp, shadowElevation = 3.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, color = LoonTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(subtitle, color = LoonTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun GlassSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    valueText: String, tag: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = LoonTextPrimary, fontSize = 13.sp)
            Text(valueText, color = LoonTextSecondary, fontSize = 12.sp, modifier = Modifier.testTag("$tag-value"))
        }
        Slider(value = value, onValueChange = onChange, valueRange = range,
            modifier = Modifier.fillMaxWidth().testTag(tag),
            colors = SliderDefaults.colors(thumbColor = IosBlue, activeTrackColor = IosBlue))
    }
}
