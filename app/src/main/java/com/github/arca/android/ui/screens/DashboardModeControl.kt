package com.github.arca.android.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.ui.components.rememberHapticValueAction
import com.github.arca.android.ui.glass.*
import com.github.arca.android.ui.theme.*

/** Fixed-size choice of tunnel mode. The lens moves; header and hit targets stay put.
 *
 * The shared foreground renderer follows Quven's spatial selection mask and Glasmio's
 * separate optical label plane. GlassSurface reuses the installed Glasmio material; the
 * selected lens shares the dock's motion and local refraction instead of creating a menu.
 */
@Composable
internal fun DashboardModeControl(
    currentMode: TunnelMode,
    onModeSelected: (TunnelMode) -> Unit,
    lens: NavigationLensState,
    modifier: Modifier = Modifier
) {
    val modes = remember { listOf(TunnelMode.RULE, TunnelMode.GLOBAL, TunnelMode.DIRECT) }
    val selectedIndex = modes.indexOf(currentMode).coerceAtLeast(0)
    val lang = LocalAppLanguage.current
    val optics = remember { NavigationLensOpticsState(selectedIndex.toFloat()) }
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var selectionPulse by remember { mutableIntStateOf(0) }
    val chooseWithHaptic = rememberHapticValueAction<TunnelMode> { mode ->
        selectionPulse++
        onModeSelected(mode)
    }
    val select: (Int) -> Unit = { index ->
        val mode = modes[index.coerceIn(modes.indices)]
        if (mode != currentMode) chooseWithHaptic(mode)
    }
    val useGlass = GlassDefaults.enabled
    BoxWithConstraints(
        modifier = modifier.widthIn(max = 264.dp).fillMaxWidth().height(48.dp)
            .testTag("mode-segment-control")
            .selectableGroup()
            .then(if (useGlass) Modifier.navigationLensDrag(modes.size, { dragPosition = it }, select) else Modifier)
    ) {
        Box(
            Modifier.matchParentSize()
                .background(LoonCard, CircleShape)
                .border(1.dp, LoonCardBorder, CircleShape)
        )
        if (useGlass) {
            NavigationLens(
                selectedSlot = selectedIndex,
                slotCount = modes.size,
                lens = lens,
                modifier = Modifier.matchParentSize(),
                selectionPulse = selectionPulse,
                dragPosition = dragPosition,
                optics = optics,
                lensTestTag = "mode-selection-lens",
                surfaceCategory = GlassSurfaceCategory.SHORTCUT_CARDS,
                solidFill = if (LocalLoonColors.current.isDark) {
                    androidx.compose.ui.graphics.Color(0xFF263B55)
                } else {
                    androidx.compose.ui.graphics.Color(0xFFE6F0FF)
                }
            )
            NavigationLensContent(
                modifier = Modifier.matchParentSize(),
                optics = optics,
                slotCount = modes.size,
                selectedTint = IosBlue
            ) {
                ModeLabels(modes, currentMode, lang, select)
            }
        } else {
            val position by animateFloatAsState(
                selectedIndex.toFloat(), spring(dampingRatio = 1f, stiffness = 300f), label = "mode-position"
            )
            val segmentWidth = maxWidth / modes.size
            Box(Modifier.offset(x = segmentWidth * position).width(segmentWidth).fillMaxHeight()
                .padding(4.dp).background(LoonBlue.copy(alpha = 0.14f), CircleShape))
            ModeLabels(modes, currentMode, lang, select, highlightSelected = true)
        }
    }
}

@Composable
private fun ModeLabels(
    modes: List<TunnelMode>,
    selected: TunnelMode,
    lang: String,
    onSelected: (Int) -> Unit,
    highlightSelected: Boolean = false
) {
    Row(Modifier.fillMaxSize()) {
        modes.forEachIndexed { index, mode ->
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .testTag("mode-option-${mode.name.lowercase()}")
                    .selectable(
                        selected = mode == selected,
                        role = Role.RadioButton,
                        interactionSource = null,
                        indication = null,
                        onClick = { onSelected(index) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = AppStrings.get("mode_${mode.name.lowercase()}_short", lang),
                    color = if (highlightSelected && mode == selected) LoonBlue else LoonTextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
