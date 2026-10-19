package com.github.mihomo.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.github.mihomo.android.ui.components.rememberGlassBackdrop
import com.github.mihomo.android.ui.components.LocalGlassBackdrop
import com.github.mihomo.android.ui.components.glassBackdropSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.data.GlassEffect
import com.github.mihomo.android.ui.components.CupertinoGroupedSection
import com.github.mihomo.android.ui.components.GlassNavigationSurface
import com.github.mihomo.android.ui.components.LoonBottomNavigationTabs
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.github.mihomo.android.ui.theme.LocalGlassIntensity
import com.github.mihomo.android.ui.theme.LocalGlassEnabled
import com.github.mihomo.android.ui.theme.SettingsTypography
import com.github.mihomo.android.ui.theme.LoonBg
import com.github.mihomo.android.ui.theme.LoonTextPrimary
import com.github.mihomo.android.ui.theme.LoonTextSecondary

@Composable
fun GlassThemeSection(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onEnabledChange: (Boolean) -> Unit = {}
) {
    val normalizedValue = GlassEffect.normalize(value)
    CupertinoGroupedSection(title = "玻璃主题", modifier = modifier) {
        com.github.mihomo.android.ui.components.CupertinoCell(
            title = "玻璃效果",
            trailing = {
                com.github.mihomo.android.ui.components.CupertinoSwitch(
                    checked = enabled,
                    onCheckedChange = { onEnabledChange(it) },
                    modifier = Modifier.semantics { contentDescription = "玻璃效果" }
                )
            }
        )
        com.github.mihomo.android.ui.components.CupertinoIndentedDivider(startIndent = 16.dp)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "作用于底栏、首页主卡与快捷方式、策略选区和设置控件；文字和密集列表保持清晰。",
                color = LoonTextSecondary,
                style = SettingsTypography.Description
            )
            val previewBackdrop = rememberGlassBackdrop()
            CompositionLocalProvider(
                LocalGlassIntensity provides normalizedValue,
                LocalGlassEnabled provides enabled,
                LocalGlassBackdrop provides previewBackdrop
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(LoonBg).padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Separate sibling source: colours visibly diffuse through the glass,
                    // while navigation content is excluded from backdrop recording.
                    Box(Modifier.matchParentSize().glassBackdropSource(previewBackdrop, enabled).drawBehind {
                        drawRect(LoonPreviewBase)
                        drawCircle(Color(0xFF57B8F2).copy(alpha = 0.62f), size.width * 0.23f,
                            Offset(size.width * 0.20f, size.height * 0.70f))
                        drawCircle(Color(0xFFB08AF4).copy(alpha = 0.55f), size.width * 0.20f,
                            Offset(size.width * 0.73f, size.height * 0.20f))
                    })
                    GlassNavigationSurface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        var previewTab by remember { mutableIntStateOf(0) }
                        LoonBottomNavigationTabs(previewTab, { previewTab = it })
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("清淡", color = LoonTextSecondary, style = SettingsTypography.Description)
                Text("${(normalizedValue * 100).toInt()}%", color = LoonTextPrimary, style = SettingsTypography.Description)
                Text("浓郁", color = LoonTextSecondary, style = SettingsTypography.Description)
            }
            Slider(
                value = normalizedValue,
                onValueChange = { if (enabled) onValueChange(GlassEffect.normalize(it)) },
                enabled = enabled,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "玻璃强度" }
            )
        }
    }
}

private val LoonPreviewBase = Color(0xFFDFE8F2)
