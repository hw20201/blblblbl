package com.github.mihomo.android.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.zIndex
import com.github.kr328.clash.core.model.Proxy
import com.github.mihomo.android.data.DashboardData
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.glass.PageTitleGlass
import com.github.mihomo.android.ui.glass.NavigationGlassSurface
import com.github.mihomo.android.ui.glass.LensStrength
import com.github.mihomo.android.ui.glass.rememberNavigationLens
import com.github.mihomo.android.ui.glass.recordNavigationScene
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.glass.NavigationLensState
import com.github.mihomo.android.ui.components.*
import com.github.mihomo.android.ui.theme.*
import kotlin.math.roundToInt

data class ShortcutDef(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    vpnState: VpnState,
    requestMonitoringEnabled: Boolean = false,
    currentMode: TunnelMode,
    activeProfileName: String,
    mainGroupName: String,
    mainGroupProxies: List<Proxy>,
    activeNodeName: String,
    totalNodes: Int,
    totalRules: Int,
    visibleShortcuts: Set<String>,
    shortcutOrder: List<String>,
    onToggleVpn: () -> Unit,
    onModeSelected: (TunnelMode) -> Unit,
    onSelectProxy: (group: String, proxy: Proxy) -> Unit,
    onHealthCheck: (group: String) -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToAppRouting: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToRules: () -> Unit = {},
    onNavigateToDns: () -> Unit = {},
    onNavigateToRequests: () -> Unit = {},
    onNavigateToNetworkShare: () -> Unit = {},
    onUpdateVisibleShortcuts: (Set<String>) -> Unit,
    onUpdateShortcutOrder: (List<String>) -> Unit,
    glassScene: NavigationLensState? = null
) {
    val isRunning = vpnState.status == VpnState.Status.RUNNING
    val isStarting = vpnState.status == VpnState.Status.STARTING
    val coroutineScope = rememberCoroutineScope()
    var modePickerOpen by remember { mutableStateOf(false) }
    val modePickerProgress = remember { Animatable(0f) }
    val pickerMorph = modePickerProgress.value.coerceIn(0f, 1f)
    // A fixed, slightly enlarged capsule is measured in both states. The same glass shell owns
    // the collapsed capsule and grows only downward when opened.
    val collapsedPickerWidth = 136.dp
    val capsuleHeight = 48.dp
    val optionHeight = 40.dp
    val pickerHeight = capsuleHeight + 96.dp * pickerMorph
    val closeModePicker: () -> Unit = {
        modePickerOpen = false
        coroutineScope.launch { modePickerProgress.animateTo(0f, tween(180, easing = FastOutSlowInEasing)) }
    }
    val toggleModePicker: () -> Unit = {
        if (modePickerOpen) {
            closeModePicker()
        } else {
            modePickerOpen = true
            coroutineScope.launch {
                modePickerProgress.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
            }
        }
    }
    BackHandler(enabled = modePickerOpen, onBack = closeModePicker)

    // State for Quick Node Selector Modal
    var showQuickNodeSelector by remember { mutableStateOf(false) }
    var quickNodeSearchQuery by remember { mutableStateOf("") }
    var isTestingQuickSheet by remember { mutableStateOf(false) }
    val quickNodeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Automatically fully expand sheet when opened
    LaunchedEffect(showQuickNodeSelector) {
        if (showQuickNodeSelector) {
            runCatching { quickNodeSheetState.expand() }
        }
    }

    // Visual In-Place Shortcuts Edit State
    var isEditingShortcuts by remember { mutableStateOf(false) }
    var showAddShortcutSheet by remember { mutableStateOf(false) }
    val addShortcutSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var draggedId by remember { mutableStateOf<String?>(null) }
    var pickedUpShortcutId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pickedUpShortcutId) {
        // Also stop a fling already in progress; consuming future moves alone cannot do that.
        if (pickedUpShortcutId != null) scrollState.stopScroll()
    }
    var dragOffsetState by remember { mutableStateOf(Offset.Zero) }
    var initialSlotIndex by remember { mutableIntStateOf(-1) }
    var hoverSlotIndex by remember { mutableIntStateOf(-1) }
    var dragStartSlotX by remember { mutableFloatStateOf(0f) }
    var dragStartSlotY by remember { mutableFloatStateOf(0f) }
    var localOrder by remember(shortcutOrder) { mutableStateOf(shortcutOrder) }

    // State for general Informational Dialog
    var activeDialogTitle by remember { mutableStateOf<String?>(null) }
    var activeDialogContent by remember { mutableStateOf<String?>(null) }
    var showOutboundIp by remember { mutableStateOf(false) }
    val latestToggleVpn by rememberUpdatedState(onToggleVpn)

    val lang = LocalAppLanguage.current

    // All shortcut item definitions available across entire DashboardScreen (cached to eliminate recomposition lag)
    val allShortcutMap = remember(
        lang, activeProfileName, activeNodeName, totalNodes, totalRules, requestMonitoringEnabled,
        vpnState.upSpeed, vpnState.downSpeed, vpnState.totalUp, vpnState.totalDown,
        vpnState.coreVersion, isRunning, isStarting, vpnState.status
    ) {
        mapOf(
            "vpn" to ShortcutDef("vpn", if (vpnState.status == VpnState.Status.STOPPING) "正在停止" else if (isRunning || isStarting) "停止 VPN" else "启动 VPN",
                when (vpnState.status) {
                    VpnState.Status.RUNNING -> "已连接 · 点击停止"
                    VpnState.Status.STARTING -> "连接中 · 点击取消"
                    VpnState.Status.STOPPING -> "请稍候"
                    else -> "点击连接"
                }, { Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = LoonOrange, modifier = Modifier.size(38.dp)) }) {
                if (vpnState.status != VpnState.Status.STOPPING) latestToggleVpn()
            },
            "ip_test" to ShortcutDef("ip_test", "IP 测试", "出站 IP · 点击检测", {
                Icon(Icons.Default.Public, contentDescription = null, tint = IosBlue, modifier = Modifier.size(38.dp))
            }) { showOutboundIp = true },
            "nodes" to ShortcutDef("nodes", AppStrings.get("shortcut_nodes", lang), "$totalNodes", { NodesIllustration() }) {
                showQuickNodeSelector = true
            },
            "profiles" to ShortcutDef("profiles", AppStrings.get("shortcut_profiles", lang), activeProfileName, { ProfilesIllustration() }) {
                onNavigateToProfiles()
            },
            "core_logs" to ShortcutDef("core_logs", AppStrings.get("shortcut_logs", lang), if (isRunning) "实时监控" else "点击查看", { RequestLogsIllustration() }) {
                onNavigateToLogs()
            },
            "request_logs" to ShortcutDef("request_logs", AppStrings.get("shortcut_requests", lang), DashboardData.requestStatus(isRunning, requestMonitoringEnabled), { RequestLogsIllustration() }) {
                onNavigateToRequests()
            },
            "dns" to ShortcutDef("dns", AppStrings.get("shortcut_dns", lang), "Fake-IP", { DnsIllustration() }) {
                onNavigateToDns()
            },
            "rules" to ShortcutDef("rules", AppStrings.get("shortcut_rules", lang), "$totalRules", { RulesIllustration() }) {
                onNavigateToRules()
            },
            "routing" to ShortcutDef("routing", AppStrings.get("shortcut_routing", lang), "规则与黑白名单", { AppRoutingIllustration() }) {
                onNavigateToAppRouting()
            },
            "plugins" to ShortcutDef("plugins", AppStrings.get("shortcut_plugins", lang), if (vpnState.coreVersion.isNotBlank()) "v${vpnState.coreVersion.take(8)}" else "Nexus Core", { PluginsIllustration() }) {
                activeDialogTitle = "内核信息"
                activeDialogContent = "内核版本: ${vpnState.coreVersion.ifBlank { "v1.18.x" }}\n" +
                        "架构支持: ${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"}"
            },
            "network_share" to ShortcutDef("network_share", AppStrings.get("shortcut_network_share", lang), "127.0.0.1:7890", { NetworkShareIllustration() }) {
                onNavigateToNetworkShare()
            }
        )
    }

    val displayedShortcuts = remember(localOrder, visibleShortcuts, lang, allShortcutMap) {
        localOrder.mapNotNull { allShortcutMap[it] }.filter { visibleShortcuts.contains(it.id) }
    }

    if (showOutboundIp) {
        OutboundIpSheet(isVpnRunning = isRunning, onDismiss = { showOutboundIp = false })
    }

    val titleScene = rememberNavigationLens()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerInset = with(LocalDensity.current) { headerHeightPx.toDp() }
    val isDarkTheme = LocalLoonColors.current.isDark
    // With glass enabled the shared backdrop already provides the colour field, so the screen
    // itself must stay transparent or it would paint over it.
    val topBgBrush = if (GlassDefaults.enabled) {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    } else if (isDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(LoonBg, LoonBg)
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFF6EE),
                Color(0xFFF9F9FD),
                LoonBg
            ),
            startY = 0f,
            endY = 550f
        )
    }

    // Replace platform stretching with bounded content translation; header/dock remain siblings.
    CompositionLocalProvider(
        LocalOverscrollConfiguration provides null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (GlassDefaults.enabled) Modifier.recordNavigationScene(titleScene, glassScene) else Modifier)
                    .background(topBgBrush)
                    .testTag("dashboard-scroll")
                    .bounceOverscroll()
                    .verticalScroll(state = scrollState, enabled = draggedId == null && pickedUpShortcutId == null)
                    .padding(horizontal = 20.dp)
                    .padding(top = headerInset + 12.dp, bottom = 110.dp)
            ) {

                LoonTrafficSummaryCard(
                    uploadTitle = AppStrings.get("traffic_up", lang),
                    uploadSpeed = if (isRunning) vpnState.upSpeed else "--",
                    downloadTitle = AppStrings.get("traffic_down", lang),
                    downloadSpeed = if (isRunning) vpnState.downSpeed else "--",
                    uploadTotal = vpnState.totalUp,
                    downloadTotal = vpnState.totalDown,
                    totalLabel = if (lang == "zh") "累计" else "Total"
                )

                Spacer(modifier = Modifier.height(26.dp))

                // Section Header: 快捷方式 + 纯文字“编辑 / 完成”按钮 (无铅笔图标)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AppStrings.get("shortcuts", lang),
                        color = LoonTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val haptic = rememberIosHaptic()
                    Text(
                        text = if (isEditingShortcuts) AppStrings.get("shortcuts_done", lang) else AppStrings.get("shortcuts_edit", lang),
                        color = IosBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptic.lightTick()
                                isEditingShortcuts = !isEditingShortcuts
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Order according to user preference (Default: nodes, profiles, ...)
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val spacingDp = 12.dp
                    val itemWidthDp = (maxWidth - spacingDp) / 2
                    val itemHeightDp = 118.dp

                    val density = LocalDensity.current
                    val itemWidthPx = with(density) { itemWidthDp.toPx() }
                    val itemHeightPx = with(density) { itemHeightDp.toPx() }
                    val spacingPx = with(density) { spacingDp.toPx() }

                    val rowCount = (displayedShortcuts.size + 1) / 2
                    val rawGridHeight = if (rowCount > 0) (itemHeightDp * rowCount) + (spacingDp * (rowCount - 1)) else 0.dp
                    val gridHeight = if (rawGridHeight == 0.dp && isEditingShortcuts) 10.dp else rawGridHeight

                    // Keep current displayedShortcuts available in gesture callbacks
                    val currentDisplayedRef = rememberUpdatedState(displayedShortcuts)
                    val currentLocalOrderRef = rememberUpdatedState(localOrder)

                    val gridBoxModifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight)

                    Box(modifier = gridBoxModifier) {
                        displayedShortcuts.forEach { item ->
                            androidx.compose.runtime.key(item.id) {
                                val origSlotIndex = displayedShortcuts.indexOfFirst { it.id == item.id }
                                val isBeingDragged = draggedId == item.id

                                // Compute visual slot index
                                val visualSlotIndex: Int = if (draggedId == null || initialSlotIndex == -1 || hoverSlotIndex == -1) {
                                    origSlotIndex
                                } else if (isBeingDragged) {
                                    hoverSlotIndex
                                } else {
                                    if (hoverSlotIndex > initialSlotIndex) {
                                        if (origSlotIndex in (initialSlotIndex + 1)..hoverSlotIndex) {
                                            origSlotIndex - 1
                                        } else {
                                            origSlotIndex
                                        }
                                    } else if (hoverSlotIndex < initialSlotIndex) {
                                        if (origSlotIndex in hoverSlotIndex until initialSlotIndex) {
                                            origSlotIndex + 1
                                        } else {
                                            origSlotIndex
                                        }
                                    } else {
                                        origSlotIndex
                                    }
                                }

                                val col = visualSlotIndex % 2
                                val row = visualSlotIndex / 2
                                val targetSlotX = col * (itemWidthPx + spacingPx)
                                val targetSlotY = row * (itemHeightPx + spacingPx)

                                val animatedX by animateFloatAsState(
                                    targetValue = targetSlotX,
                                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                    label = "animX_${item.id}"
                                )
                                val animatedY by animateFloatAsState(
                                    targetValue = targetSlotY,
                                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                    label = "animY_${item.id}"
                                )

                                val currentPosX: Float
                                val currentPosY: Float
                                if (isBeingDragged) {
                                    currentPosX = dragStartSlotX + dragOffsetState.x
                                    currentPosY = dragStartSlotY + dragOffsetState.y
                                } else {
                                    currentPosX = animatedX
                                    currentPosY = animatedY
                                }

                                var itemModifier = Modifier
                                    .size(itemWidthDp, itemHeightDp)
                                    .offset {
                                        IntOffset(currentPosX.roundToInt(), currentPosY.roundToInt())
                                    }
                                    .zIndex(if (isBeingDragged) 100f else if (pickedUpShortcutId == item.id) 50f else 1f)
                                    .graphicsLayer {
                                        val scale = if (isBeingDragged) 1.06f else 1f
                                        scaleX = scale
                                        scaleY = scale
                                        shadowElevation = if (isBeingDragged) 24f else 0f
                                    }
                                    .testTag("shortcut-${item.id}")

                                if (isEditingShortcuts) {
                                    itemModifier = itemModifier.pointerInput(item.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                val list = currentDisplayedRef.value
                                                val curIdx = list.indexOfFirst { it.id == item.id }
                                                draggedId = item.id
                                                initialSlotIndex = curIdx
                                                hoverSlotIndex = curIdx
                                                val startCol = if (curIdx != -1) curIdx % 2 else 0
                                                val startRow = if (curIdx != -1) curIdx / 2 else 0
                                                dragStartSlotX = startCol * (itemWidthPx + spacingPx)
                                                dragStartSlotY = startRow * (itemHeightPx + spacingPx)
                                                dragOffsetState = Offset.Zero
                                            },
                                            onDragEnd = {
                                                val list = currentDisplayedRef.value
                                                val fromIdx = initialSlotIndex
                                                val toIdx = hoverSlotIndex
                                                val canSwap = fromIdx != toIdx && fromIdx in list.indices && toIdx in list.indices
                                                if (canSwap) {
                                                    val mutable = currentLocalOrderRef.value.toMutableList()
                                                    val fromPos = mutable.indexOf(item.id)
                                                    val targetItem = list[toIdx]
                                                    val toPos = mutable.indexOf(targetItem.id)
                                                    if (fromPos != -1 && toPos != -1) {
                                                        mutable.removeAt(fromPos)
                                                        mutable.add(toPos, item.id)
                                                        localOrder = mutable
                                                        onUpdateShortcutOrder(mutable)
                                                    }
                                                }
                                                draggedId = null
                                                dragOffsetState = Offset.Zero
                                                initialSlotIndex = -1
                                                hoverSlotIndex = -1
                                            },
                                            onDragCancel = {
                                                draggedId = null
                                                dragOffsetState = Offset.Zero
                                                initialSlotIndex = -1
                                                hoverSlotIndex = -1
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                if (draggedId != item.id) return@detectDragGesturesAfterLongPress

                                                val nextOffset = dragOffsetState + dragAmount
                                                dragOffsetState = nextOffset

                                                val list = currentDisplayedRef.value
                                                val curCenterX = dragStartSlotX + nextOffset.x + itemWidthPx / 2f
                                                val curCenterY = dragStartSlotY + nextOffset.y + itemHeightPx / 2f

                                                val targetCol = (curCenterX / (itemWidthPx + spacingPx)).toInt().coerceIn(0, 1)
                                                val targetRow = (curCenterY / (itemHeightPx + spacingPx)).toInt().coerceIn(0, (list.size - 1) / 2)
                                                val targetIndex = (targetRow * 2 + targetCol).coerceIn(0, list.size - 1)

                                                if (targetIndex != hoverSlotIndex) {
                                                    hoverSlotIndex = targetIndex
                                                }
                                            }
                                        )
                                    }
                                }

                            LoonShortcutCard(
                                modifier = itemModifier,
                                title = item.title,
                                subtitle = item.subtitle,
                                icon = item.icon,
                                isEditing = isEditingShortcuts,
                                lens = glassScene,
                                onPickupChanged = { picked ->
                                    if (picked) pickedUpShortcutId = item.id
                                    else if (pickedUpShortcutId == item.id) pickedUpShortcutId = null
                                },
                                onDelete = {
                                    if (draggedId == null) { // Prevent delete during drag
                                        onUpdateVisibleShortcuts(visibleShortcuts - item.id)
                                    }
                                },
                                onClick = item.onClick
                            )
                        }
                    }
                }
            } // Close BoxWithConstraints so add button is placed linearly below the grid

            // Visual Add Shortcut button at bottom when in editing mode
            if (isEditingShortcuts) {
                val hiddenShortcuts = shortcutOrder
                    .filter { !visibleShortcuts.contains(it) }
                    .mapNotNull { allShortcutMap[it] }

                // Ensure button is always at the bottom, below any grid content
                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, LoonCardBorder, RoundedCornerShape(16.dp))
                        .clickable { showAddShortcutSheet = true },
                    color = LoonCard
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = null,
                            tint = LoonTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hiddenShortcuts.isEmpty()) AppStrings.get("shortcuts_all_shown", lang) else "${AppStrings.get("shortcuts_add", lang)} (${hiddenShortcuts.size})",
                            color = LoonTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (vpnState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = RedDanger, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = vpnState.error, color = RedDanger, fontSize = 13.sp)
                    }
                }
            }

            } // Only scrolling content belongs to the recorded scene.

            // Header: NEXUS (LOON style) & Mode Selector + iOS Switch
            Column(
                modifier = Modifier
                    .onSizeChanged { headerHeightPx = it.height }
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 12.dp)

            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PageTitleGlass(titleScene, modifier = Modifier.testTag("dashboard-nexus-title")) { Text(
                        text = "NEXUS",
                        color = LoonOrange,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    ) }

                    // Top Right iOS Cupertino Switch (aligned directly with NEXUS title)
                    CupertinoSwitch(
                        checked = isRunning || isStarting,
                        onCheckedChange = { onToggleVpn() }
                    )
                }

                val modeOptions = remember(lang) {
                    TunnelMode.values().map { mode ->
                        val key = when (mode) {
                            TunnelMode.RULE -> "mode_rule"
                            TunnelMode.GLOBAL -> "mode_global"
                            TunnelMode.DIRECT -> "mode_direct"
                            else -> "mode_${mode.name.lowercase()}"
                        }
                        mode to AppStrings.get(key, lang)
                    }
                }
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    val pickerShape = RoundedCornerShape(20.dp)
                    val otherModes = modeOptions.filterNot { it.first == currentMode }.take(2)
                    val outerPickerModifier = Modifier
                        .requiredSize(collapsedPickerWidth, pickerHeight)
                        .testTag(if (modePickerOpen) "mode-picker-panel" else "mode-picker-trigger")
                        .semantics { selected = modePickerOpen }
                        .clip(pickerShape)
                        .clickable(interactionSource = null, indication = null, onClick = toggleModePicker)
                    Box(outerPickerModifier) {
                        NavigationGlassSurface(
                            modifier = Modifier.fillMaxSize(),
                            lens = titleScene,
                            shape = pickerShape,
                            cornerRadius = 24.dp,
                            strength = LensStrength.SUBTLE,
                            surfaceCategory = com.github.mihomo.android.ui.glass.GlassSurfaceCategory.TITLE_CAPSULES,
                            frosted = true,
                            showRim = true
                        ) {
                            if (modePickerOpen) {
                                Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    Spacer(Modifier.height(capsuleHeight - 8.dp))
                                    Spacer(Modifier.height(4.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        otherModes.forEach { (mode, title) ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(optionHeight)
                                                        .testTag("mode-option-${mode.name.lowercase()}")
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .semantics { selected = mode == currentMode }
                                                        .clickable {
                                                            onModeSelected(mode)
                                                            closeModePicker()
                                                        }
                                                        .padding(horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        title,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = LoonTextPrimary,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                    }
                                }
                            }
                        }
                        // Keep a legible, persistently visible glass face over the live lens.
                        // The lens remains underneath for refraction; this soft neutral veil and
                        // quiet rim keep the collapsed capsule from disappearing on pale scenes.
                        Box(
                            Modifier.matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.White.copy(alpha = 0.24f),
                                        0.55f to Color.White.copy(alpha = 0.16f),
                                        1f to Color.White.copy(alpha = 0.10f)
                                    ),
                                    pickerShape
                                )
                                .border(0.8.dp, Color.White.copy(alpha = 0.72f), pickerShape)
                        )
                        // Keep the capsule as a separate fixed-size layer above the expanding
                        // shell; it remains visible and stationary throughout the downward morph.
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth()
                                .height(capsuleHeight)
                                .testTag("mode-picker-capsule"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = when (currentMode) {
                                    TunnelMode.RULE -> AppStrings.get("mode_rule", lang)
                                    TunnelMode.GLOBAL -> AppStrings.get("mode_global", lang)
                                    TunnelMode.DIRECT -> AppStrings.get("mode_direct", lang)
                                    else -> AppStrings.get("mode_${currentMode.name.lowercase()}", lang)
                                },
                                color = LoonOrangeLight,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = LoonOrangeLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                    }
                }

            }

            // =========================================================================
            // In-Place Quick Node Selector BottomSheet (Full 92% height, smooth expansion)
            // =========================================================================
            if (showQuickNodeSelector) {
                ModalBottomSheet(
                    onDismissRequest = { showQuickNodeSelector = false },
                    sheetState = quickNodeSheetState,
                    containerColor = LoonCard,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    Column(
                        modifier = Modifier
                            .testTag("quick-node-selector-sheet")
                            .fillMaxWidth()
                            .fillMaxHeight(0.92f)
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = AppStrings.get("quick_node_title", lang),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LoonTextPrimary
                                )
                                Text(
                                    text = "策略组: ${mainGroupName.ifBlank { "默认主组" }}",
                                    fontSize = 12.sp,
                                    color = LoonTextSecondary
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (mainGroupName.isNotBlank() && !isTestingQuickSheet) {
                                        isTestingQuickSheet = true
                                        coroutineScope.launch {
                                            onHealthCheck(mainGroupName)
                                            kotlinx.coroutines.delay(1800)
                                            isTestingQuickSheet = false
                                        }
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                AnimatedSpeedTestIcon(
                                    isTesting = isTestingQuickSheet,
                                    tint = LoonTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Search bar
                        OutlinedTextField(
                            value = quickNodeSearchQuery,
                            onValueChange = { quickNodeSearchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            placeholder = { Text(AppStrings.get("search_nodes", lang), color = LoonTextMuted, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LoonTextSecondary, modifier = Modifier.size(17.dp)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = LoonBg,
                                unfocusedContainerColor = LoonBg,
                                focusedBorderColor = LoonBlue,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        val filteredNodes = remember(mainGroupProxies, quickNodeSearchQuery) {
                            if (quickNodeSearchQuery.isBlank()) mainGroupProxies
                            else mainGroupProxies.filter { it.name.contains(quickNodeSearchQuery, ignoreCase = true) }
                        }

                        if (filteredNodes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (mainGroupProxies.isEmpty()) AppStrings.get("no_nodes_available", lang) else AppStrings.get("no_nodes_matched", lang),
                                    color = LoonTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                itemsIndexed(
                                    items = filteredNodes,
                                    key = { index, proxy -> "${proxy.name}_$index" }
                                ) { index, proxy ->
                                    val isSelected = proxy.name == activeNodeName
                                    val nodeEmoji = extractLeadingEmoji(proxy.name)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                onSelectProxy(mainGroupName, proxy)
                                                showQuickNodeSelector = false
                                            }
                                            .padding(horizontal = 8.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            if (nodeEmoji != null) {
                                                Text(
                                                    text = nodeEmoji,
                                                    fontSize = 17.sp,
                                                    modifier = Modifier.padding(end = 10.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = proxy.name,
                                                    color = if (isSelected) LoonOrange else LoonTextPrimary,
                                                    fontSize = 14.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    maxLines = 1
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = proxy.type.uppercase(),
                                                    color = LoonTextMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isTestingQuickSheet) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(13.dp),
                                                    strokeWidth = 1.8.dp,
                                                    color = LoonOrange
                                                )
                                            } else {
                                                val delayColor = when {
                                                    proxy.delay == -2 -> RedDanger
                                                    proxy.delay in 1..250 -> LoonGreen
                                                    proxy.delay in 251..600 -> Color(0xFFF59E0B)
                                                    proxy.delay > 600 -> RedDanger
                                                    else -> LoonTextMuted
                                                }
                                                val delayText = when {
                                                    proxy.delay == -2 -> "超时"
                                                    proxy.delay > 0 -> "${proxy.delay} ms"
                                                    else -> "--"
                                                }
                                                Text(
                                                    text = delayText,
                                                    color = delayColor,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (proxy.delay > 0 || proxy.delay == -2) FontWeight.SemiBold else FontWeight.Normal,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = LoonOrange,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (index < filteredNodes.size - 1) {
                                        HorizontalDivider(
                                            color = LoonCardBorder,
                                            thickness = 0.5.dp,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // Add Hidden Shortcut BottomSheet
            // =========================================================================
            if (showAddShortcutSheet) {
                val hiddenShortcuts = shortcutOrder
                    .filter { !visibleShortcuts.contains(it) }
                    .mapNotNull { allShortcutMap[it] }

                ModalBottomSheet(
                    onDismissRequest = { showAddShortcutSheet = false },
                    sheetState = addShortcutSheetState,
                    containerColor = LoonCard,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = AppStrings.get("shortcuts_add", lang),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = LoonTextPrimary
                            )
                            IconButton(onClick = { showAddShortcutSheet = false }) {
                                Icon(Icons.Default.Close, contentDescription = "关闭", tint = LoonTextSecondary)
                            }
                        }

                        if (hiddenShortcuts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = AppStrings.get("shortcuts_all_added", lang),
                                    color = LoonTextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(hiddenShortcuts) { _, item ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, LoonCardBorder, RoundedCornerShape(12.dp))
                                            .clickable {
                                                onUpdateVisibleShortcuts(visibleShortcuts + item.id)
                                            },
                                        color = LoonCard
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                                    item.icon()
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LoonTextPrimary)
                                                    Text(item.subtitle, fontSize = 11.sp, color = LoonTextSecondary)
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = LoonEditBlueBg
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, tint = LoonBlue, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(AppStrings.get("shortcuts_add", lang), color = LoonBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Informational Dialog
            if (activeDialogTitle != null) {
                AlertDialog(
                    onDismissRequest = { activeDialogTitle = null },
                    title = {
                        Text(
                            text = activeDialogTitle ?: "",
                            fontWeight = FontWeight.Bold,
                            color = LoonTextPrimary
                        )
                    },
                    text = {
                        Text(
                            text = activeDialogContent ?: "",
                            color = LoonTextSecondary,
                            lineHeight = 20.sp,
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { activeDialogTitle = null }) {
                            Text("知道了", color = LoonBlue, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = LoonCard,
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }
    }
}
