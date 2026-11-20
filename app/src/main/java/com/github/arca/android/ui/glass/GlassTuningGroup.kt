package com.github.arca.android.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.github.arca.android.ui.theme.LoonCard
import com.github.arca.android.ui.theme.LocalLoonColors
import com.github.arca.android.ui.components.cardDepth
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
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.AppStrings
import com.github.arca.android.ui.components.rememberHapticAction

/** A capsule header that morphs downward into a settings panel using the existing glass renderer. */
@Composable
internal fun GlassTuningGroup(
    title: String,
    subtitle: String,
    tag: String,
    lens: NavigationLensState? = null,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
    headerTag: String = tag,
    leading: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, bottom = 14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(tag) { mutableStateOf(initiallyExpanded) }
    val toggleExpanded = rememberHapticAction { expanded = !expanded }
    val shape = RoundedCornerShape(GlassRadius.card)
    val language = LocalAppLanguage.current
    val groupContent: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                    .glassTouchRipple()
                    .clickable(role = Role.Button, onClick = toggleExpanded)
                    .semantics { stateDescription = AppStrings.get(if (expanded) "settings_state_expanded" else "settings_state_collapsed", language) }
                    .testTag(headerTag)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                leading?.invoke()
                Column(Modifier.weight(1f)) {
                    Text(title, color = LoonTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(subtitle, color = LoonTextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
                }
                Text(AppStrings.get(if (expanded) "settings_collapse" else "settings_expand", language), color = LoonTextSecondary, fontSize = 12.sp)
            }
            AnimatedVisibility(
                visible = expanded,
                // The size animation lives here, in layout, so the parent scroll extent always
                // follows the real height. A separate animateContentSize left an initially
                // expanded group with a stale height and hid every group below it.
                enter = fadeIn(tween(160)) + expandVertically(
                    spring(dampingRatio = 0.9f, stiffness = 320f)
                ),
                exit = fadeOut(tween(100)) + shrinkVertically(
                    spring(dampingRatio = 0.9f, stiffness = 560f)
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(contentPadding),
                    content = content
                )
            }
        }
    }
    // Quiet pages use the same raised card as the main Settings groups. The content and
    // disclosure animation are shared with legacy glass tuning on older devices.
    if (GlassDefaults.quietPages || !GlassDefaults.enabled) {
        Surface(
            modifier = modifier.fillMaxWidth()
                .cardDepth(shape, LocalLoonColors.current.isDark, 2.dp).testTag("$tag-panel"),
            shape = shape,
            color = LoonCard,
            content = groupContent
        )
    } else {
        NavigationGlassSurface(
            modifier = modifier.fillMaxWidth().testTag("$tag-panel"),
            lens = lens,
            shape = shape,
            cornerRadius = GlassRadius.card,
            strength = LensStrength.STANDARD,
            surfaceCategory = GlassSurfaceCategory.LARGE_PANELS,
            frosted = true
        ) { groupContent() }
    }
}
