package com.github.arca.android.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.ui.theme.LoonTextPrimary
import com.github.arca.android.ui.theme.LoonTextSecondary
import com.github.arca.android.ui.components.rememberHapticAction

/** A capsule header that morphs downward into a settings panel using the existing glass renderer. */
@Composable
internal fun GlassTuningGroup(
    title: String,
    subtitle: String,
    tag: String,
    lens: NavigationLensState? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(tag) { mutableStateOf(initiallyExpanded) }
    val toggleExpanded = rememberHapticAction { expanded = !expanded }
    val sizeSpec = androidx.compose.runtime.remember(expanded) {
        spring<IntSize>(dampingRatio = 0.9f, stiffness = if (expanded) 320f else 560f)
    }
    NavigationGlassSurface(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).animateContentSize(sizeSpec).testTag("$tag-panel"),
        lens = lens,
        shape = RoundedCornerShape(24.dp),
        cornerRadius = 24.dp,
        strength = LensStrength.STANDARD,
        surfaceCategory = GlassSurfaceCategory.LARGE_PANELS,
        frosted = true
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                    .glassTouchRipple()
                    .clickable { toggleExpanded() }
                    .testTag(tag)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = LoonTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(subtitle, color = LoonTextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
                }
                Text(if (expanded) "收起" else "展开", color = LoonTextSecondary, fontSize = 12.sp)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(100))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp),
                    content = content
                )
            }
        }
    }
}
