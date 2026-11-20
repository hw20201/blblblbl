package com.github.arca.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.ui.glass.GlassSurface
import com.github.arca.android.ui.glass.GlassSurfaceCategory
import com.github.arca.android.ui.glass.NavigationLens
import com.github.arca.android.ui.glass.NavigationLensContent
import com.github.arca.android.ui.glass.NavigationLensOpticsState
import com.github.arca.android.ui.glass.ProvideArcaGlasmioBackdrop
import com.github.arca.android.ui.glass.navigationLensDrag
import com.github.arca.android.ui.glass.recordArcaGlasmioBackdropSource
import com.github.arca.android.ui.glass.recordNavigationScene
import com.github.arca.android.ui.glass.rememberArcaGlasmioBackdrop
import com.github.arca.android.ui.glass.rememberNavigationLens
import com.github.arca.android.ui.theme.IosBlue
import com.github.arca.android.ui.theme.LocalLoonColors
import com.github.arca.android.ui.theme.LoonTextSecondary

/** Glasmio tray plus the dock's clear lens and spatially refracted foreground.
 *
 * Only the local tray is recorded for the shell. Labels have their own single foreground input,
 * so the ordinary glyph is replaced, rather than doubled, wherever the lens passes over it.
 * The semantic labels and hit targets remain stationary while both optical layers share one clock.
 */
@Composable
internal fun RefractingSegmentedControl(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    testTag: String
) {
    if (options.isEmpty()) return
    val selectedIndex = options.indexOfFirst { it.first == selectedKey }.coerceAtLeast(0)
    val optics = remember(options.size) { NavigationLensOpticsState(selectedIndex.toFloat()) }
    val trayScene = rememberNavigationLens()
    val trayBackdrop = rememberArcaGlasmioBackdrop()
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var selectionPulse by remember { mutableIntStateOf(0) }
    val choose = rememberHapticValueAction<String> { key ->
        selectionPulse++
        onSelect(key)
    }
    val select: (Int) -> Unit = { index -> choose(options[index.coerceIn(options.indices)].first) }
    val dark = LocalLoonColors.current.isDark
    val trayFill = Brush.verticalGradient(
        if (dark) listOf(Color(0xFF343944), Color(0xFF262A33))
        else listOf(Color(0xFFEEF1F6), Color(0xFFE6E9F0))
    )
    val trackHeight = maxOf(44.dp, with(LocalDensity.current) { 20.sp.toDp() } + 24.dp)
    Box(
        Modifier.fillMaxWidth().height(trackHeight)
            .testTag("$testTag-glasmio-control")
            .selectableGroup()
            .navigationLensDrag(options.size, { dragPosition = it }, select)
    ) {
        Box(Modifier.matchParentSize().recordNavigationScene(trayScene)) {
            Box(Modifier.matchParentSize()
                .recordArcaGlasmioBackdropSource(trayBackdrop)
                .background(trayFill, CircleShape))
            ProvideArcaGlasmioBackdrop(trayBackdrop) {
                GlassSurface(
                    modifier = Modifier.matchParentSize(),
                    shape = CircleShape,
                    surfaceCategory = GlassSurfaceCategory.BOTTOM_NAVIGATION,
                    surfaceAlphaScale = if (dark) 1f else 0.45f,
                    honorShape = true
                ) {}
            }
        }
        NavigationLens(
            selectedSlot = selectedIndex,
            slotCount = options.size,
            lens = trayScene,
            modifier = Modifier.matchParentSize(),
            selectionPulse = selectionPulse,
            dragPosition = dragPosition,
            optics = optics,
            lensTestTag = "$testTag-selection-lens"
        )
        NavigationLensContent(
            modifier = Modifier.matchParentSize().testTag("$testTag-optical-labels"),
            optics = optics,
            slotCount = options.size,
            selectedTint = IosBlue
        ) {
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { index, (key, label) ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .testTag("$testTag-$key")
                            .selectable(
                                selected = key == selectedKey,
                                role = Role.Tab,
                                interactionSource = null,
                                indication = null,
                                onClick = { select(index) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = LoonTextSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
