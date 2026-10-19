package com.github.mihomo.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.github.mihomo.android.ui.theme.*
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import kotlinx.coroutines.launch
import kotlin.math.abs

// =============================================================================
// iOS Haptic Feedback Utilities
// =============================================================================

/** Cupertino visuals share the fork's live, opt-in haptic policy. */
@Composable
fun rememberIosHaptic(): IosHapticHelper {
    val tick = rememberHapticAction {}
    return remember(tick) { IosHapticHelper(tick) }
}

class IosHapticHelper(private val tick: () -> Unit) {
    fun lightTick() = tick()
    fun mediumImpact() = tick()
    fun impact() = tick()
    fun selection() = tick()
}

// =============================================================================
// iOS Pressable Modifier (Instant micro-scale & highlight, replacing ink ripples)
// =============================================================================

fun Modifier.iosPressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.982f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) pressedScale else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ios_press_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
            } else Modifier
        )
}

// =============================================================================
// iOS Inset Grouped Section & Cell
// =============================================================================

@Composable
fun CupertinoGroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    title: String? = null,
    footer: String? = null,
    horizontalPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val displayHeader = header ?: title
    val miuix = LocalUiStyle.current == com.github.mihomo.android.data.UiStyle.MIUIX
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
    ) {
        if (!displayHeader.isNullOrBlank()) {
            Text(
                text = displayHeader.uppercase(),
                color = LoonTextSecondary,
                style = SettingsTypography.SectionHeader,
                modifier = Modifier.padding(start = 14.dp, bottom = 6.dp)
            )
        }

        if (miuix) {
            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth(), content = content)
            }
        } else Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = 0.5.dp,
                    color = LoonCardBorder,
                    shape = RoundedCornerShape(14.dp)
                ),
            color = LoonCard,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }

        if (!footer.isNullOrBlank()) {
            Text(
                text = footer,
                color = LoonTextSecondary,
                style = SettingsTypography.Description,
                modifier = Modifier.padding(start = 14.dp, top = 6.dp, end = 14.dp)
            )
        }
    }
}

@Composable
fun CupertinoCell(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconBg: Color = IosBlue,
    customIcon: (@Composable () -> Unit)? = null,
    value: String? = null,
    showChevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val haptic = rememberIosHaptic()
    val miuix = LocalUiStyle.current == com.github.mihomo.android.data.UiStyle.MIUIX
    val textMeasurer = rememberTextMeasurer()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null && enabled) {
                    Modifier.iosPressable {
                        haptic.selection()
                        onClick()
                    }
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (customIcon != null || icon != null) {
            Box(
                modifier = Modifier.size(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (miuix || customIcon != null) Color.Transparent else iconBg),
                contentAlignment = Alignment.Center
            ) {
                if (customIcon != null) customIcon()
                else if (icon != null) Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (miuix) iconBg else Color.White,
                    modifier = Modifier.size(if (miuix) 24.dp else 17.dp)
                )
            }
            Spacer(Modifier.width(13.dp))
        }

        // Allocate only the natural trailing width. If title and value cannot both fit,
        // put the value on its own line rather than squeezing the title into a narrow column.
        BoxWithConstraints(Modifier.weight(1f)) {
            val density = LocalDensity.current
            val availableWidth = with(density) { maxWidth.toPx() }
            val titleWidth = textMeasurer.measure(title, SettingsTypography.RowTitle, softWrap = false).size.width
            val valueWidth = if (value.isNullOrBlank()) 0 else
                textMeasurer.measure(value, SettingsTypography.Value, softWrap = false).size.width
            val accessoryWidth = with(density) { (if (showChevron) 22.dp else 0.dp).toPx() }
            val gap = with(density) { 12.dp.toPx() }
            val stackedValue = trailing == null && !value.isNullOrBlank() &&
                titleWidth + valueWidth + accessoryWidth + gap > availableWidth

            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = if (enabled) LoonTextPrimary else LoonTextMuted,
                            style = SettingsTypography.RowTitle
                        )
                        if (stackedValue) {
                            Spacer(Modifier.height(6.dp))
                            Text(text = value.orEmpty(), color = LoonTextSecondary, style = SettingsTypography.Value)
                        }
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(12.dp))
                        trailing()
                    } else {
                        if (!stackedValue && !value.isNullOrBlank()) {
                            Spacer(Modifier.width(12.dp))
                            Text(text = value, color = LoonTextSecondary, style = SettingsTypography.Value)
                        }
                        if (showChevron) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.ChevronRight, null, tint = LoonChevronTint, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                // Descriptions get the whole text column, not the space left beside a switch.
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(text = subtitle, color = LoonTextSecondary, style = SettingsTypography.Description)
                }
            }
        }
    }
}

/**
 * Text-aligned indented hairline divider for iOS Inset Grouped lists.
 * Indents past the leading icon squircle so it starts aligned with the cell text.
 */
@Composable
fun CupertinoIndentedDivider(
    startIndent: Dp = 59.dp,
    endIndent: Dp = 0.dp
) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent, end = endIndent),
        color = LoonSeparator,
        thickness = 0.5.dp
    )
}

@Composable
fun CupertinoDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 59.dp,
    endIndent: Dp = 0.dp
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent, end = endIndent),
        color = LoonSeparator,
        thickness = 0.5.dp
    )
}

// =============================================================================
// Authentic Cupertino Switch (51dp x 31dp, 27dp circle thumb, spring damping)
// =============================================================================

@Composable
fun CupertinoSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (LocalUiStyle.current == com.github.mihomo.android.data.UiStyle.MIUIX) {
        val action = rememberHapticValueAction(onCheckedChange)
        val currentEnabled by rememberUpdatedState(enabled)
        val silentNativeHaptic = remember {
            object : androidx.compose.ui.hapticfeedback.HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) = Unit
            }
        }
        // Upstream has unconditional native feedback; the app owns the opt-in policy.
        CompositionLocalProvider(androidx.compose.ui.platform.LocalHapticFeedback provides silentNativeHaptic) {
            MiuixSwitch(
                checked = checked,
                onCheckedChange = { if (currentEnabled) action(it) },
                modifier = modifier,
                enabled = enabled
            )
        }
        return
    }
    val haptic = rememberIosHaptic()
    val checkedTrackColor = IosGreen
    val uncheckedTrackColor = LoonSwitchUncheckedTrack

    val trackColor by animateColorAsState(
        targetValue = if (checked) checkedTrackColor else uncheckedTrackColor,
        animationSpec = tween(durationMillis = 200),
        label = "switch_track_color"
    )

    // Thumb slide position: width is 51dp, thumb is 27dp, padding is 2dp on each side.
    // Unchecked offset: 2dp. Checked offset: 51 - 27 - 2 = 22dp.
    val targetOffset = if (checked) 22.dp else 2.dp
    val thumbOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 650f
        ),
        label = "switch_thumb_offset"
    )

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .size(width = 51.dp, height = 31.dp)
            .clip(RoundedCornerShape(15.5.dp))
            .background(trackColor)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = rememberHapticValueAction(onCheckedChange)
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Thumb circle with subtle iOS drop shadow
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(27.dp)
                .shadow(
                    elevation = 2.dp,
                    shape = CircleShape,
                    spotColor = Color(0x33000000)
                )
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

// =============================================================================
// Standard Cupertino Navigation Bar
// =============================================================================

@Composable
fun CupertinoNavBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showBackButton: Boolean = false,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val haptic = rememberIosHaptic()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = LoonBg,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Back button or empty placeholder
                Box(
                    modifier = Modifier.widthIn(min = 60.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (showBackButton && onBack != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.lightTick()
                                    onBack()
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                                contentDescription = AppStrings.get("back", LocalAppLanguage.current),
                                tint = IosBlue,
                                modifier = Modifier.size(17.dp)
                            )
                            if (!backLabel.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = backLabel,
                                    color = IosBlue,
                                    style = SettingsTypography.RowTitle,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Center: Title
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        color = LoonTextPrimary,
                        style = SettingsTypography.NavigationTitle
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = LoonTextSecondary,
                            style = SettingsTypography.Description
                        )
                    }
                }

                // Right: Actions
                Box(
                    modifier = Modifier.widthIn(min = 60.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (actions != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            content = actions
                        )
                    }
                }
            }

            // Hairline separator underneath
            HorizontalDivider(color = LoonSeparator, thickness = 0.5.dp)
        }
    }
}

// =============================================================================
// Cupertino Action Sheet & Single-Selection Dialog (Replacing Android RadioButtons)
// =============================================================================

@Composable
fun <T> CupertinoSelectDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = rememberIosHaptic()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = LoonCard,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, LoonCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp)
            ) {
                // Header
                Text(
                    text = title,
                    color = LoonTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = LoonSeparator, thickness = 0.5.dp)

                // Option rows
                options.forEachIndexed { index, (key, label) ->
                    val isSelected = key == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.selection()
                                onSelect(key)
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            color = if (isSelected) IosBlue else LoonTextPrimary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = IosBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (index < options.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 20.dp),
                            color = LoonSeparator,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// iOS Segmented Pill Control (For strategy groups in ProxiesScreen)
// =============================================================================

@Composable
fun <T> CupertinoSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier
) {
    val haptic = rememberIosHaptic()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = LoonTertiaryBg,
        modifier = modifier
            .height(34.dp)
            .padding(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isSelected) {
                                Modifier
                                    .shadow(2.dp, RoundedCornerShape(8.dp), spotColor = Color(0x1F000000))
                                    .background(LoonCard)
                            } else Modifier
                        )
                        .selectable(selected = isSelected, role = Role.Tab) {
                            if (!isSelected) {
                                haptic.selection()
                                onItemSelected(item)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        color = if (isSelected) LoonTextPrimary else LoonTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// =============================================================================
// Tuned iOS Rubber-Band Bounce Overscroll Modifier
// =============================================================================

@Composable
fun rememberCupertinoBounceConnection(
    allowTop: Boolean = true,
    allowBottom: Boolean = true
): Pair<NestedScrollConnection, State<Float>> {
    val coroutineScope = rememberCoroutineScope()
    val overscrollOffset = remember { Animatable(0f) }

    val connection = remember(allowTop, allowBottom) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val current = overscrollOffset.value
                if (current > 0f && available.y < 0f) {
                    val consumed = available.y.coerceAtLeast(-current)
                    coroutineScope.launch { overscrollOffset.snapTo(current + consumed) }
                    return Offset(0f, consumed)
                } else if (current < 0f && available.y > 0f) {
                    val consumed = available.y.coerceAtMost(-current)
                    coroutineScope.launch { overscrollOffset.snapTo(current + consumed) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    val isTop = available.y > 0f
                    val isBottom = available.y < 0f
                    if (isTop && !allowTop) return Offset.Zero
                    if (isBottom && !allowBottom) return Offset.Zero

                    val current = overscrollOffset.value
                    // Standard iOS square-root resistance curve
                    val resistance = (1f - (abs(current) / 450f).coerceIn(0f, 0.82f)) * 0.40f
                    val delta = available.y * resistance
                    coroutineScope.launch { overscrollOffset.snapTo(current + delta) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (overscrollOffset.value != 0f) {
                    overscrollOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (overscrollOffset.value != 0f) {
                    overscrollOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                return Velocity.Zero
            }
        }
    }
    return Pair(connection, overscrollOffset.asState())
}

@Composable
fun Modifier.cupertinoBounceOverscroll(
    allowTop: Boolean = true,
    allowBottom: Boolean = true
): Modifier {
    val (connection, offsetState) = rememberCupertinoBounceConnection(allowTop, allowBottom)
    return this
        .nestedScroll(connection)
        .graphicsLayer {
            translationY = offsetState.value
        }
}
