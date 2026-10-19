package com.github.mihomo.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.sp
import com.github.mihomo.android.data.GlassEffect
import com.github.mihomo.android.ui.components.CupertinoGroupedSection
import com.github.mihomo.android.ui.components.GlassNavigationSurface
import com.github.mihomo.android.ui.theme.LocalGlassIntensity
import com.github.mihomo.android.ui.theme.LoonBg
import com.github.mihomo.android.ui.theme.LoonTextPrimary
import com.github.mihomo.android.ui.theme.LoonTextSecondary

@Composable
fun GlassThemeSection(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val normalizedValue = GlassEffect.normalize(value)
    CupertinoGroupedSection(title = "玻璃主题", modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "仅改变底栏与首页玻璃表面；文字和设置列表保持清晰。",
                color = LoonTextSecondary,
                fontSize = 13.sp
            )
            CompositionLocalProvider(LocalGlassIntensity provides normalizedValue) {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(LoonBg).padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassNavigationSurface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("首页", color = LoonTextPrimary, fontSize = 14.sp)
                            Text("代理　设置", color = LoonTextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("清淡", color = LoonTextSecondary, fontSize = 12.sp)
                Text("${(normalizedValue * 100).toInt()}%", color = LoonTextPrimary, fontSize = 13.sp)
                Text("浓郁", color = LoonTextSecondary, fontSize = 12.sp)
            }
            Slider(
                value = normalizedValue,
                onValueChange = { onValueChange(GlassEffect.normalize(it)) },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "玻璃强度" }
            )
        }
    }
}
