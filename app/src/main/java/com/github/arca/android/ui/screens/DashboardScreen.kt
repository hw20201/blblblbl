package com.github.arca.android.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
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
import com.github.arca.android.data.DashboardData
import com.github.arca.android.data.ProfileItem
import com.github.arca.android.data.ParsedRule
import com.github.arca.android.data.PerAppProxyMode
import com.github.arca.android.data.InstalledApp
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.service.VpnState
import com.github.arca.android.service.TrafficMetrics
import com.github.arca.android.service.MihomoVpnService
import kotlinx.coroutines.flow.StateFlow
import com.github.arca.android.ui.glass.PageScrollEdge
import com.github.arca.android.ui.glass.PageTitleGlass
import com.github.arca.android.ui.glass.GlassAlertDialog
import com.github.arca.android.ui.glass.GlassModalBottomSheet
import com.github.arca.android.ui.glass.GlassSurface
import com.github.arca.android.ui.glass.GlassSurfaceCategory
import com.github.arca.android.ui.glass.rememberNavigationLens
import com.github.arca.android.ui.glass.recordNavigationScene
import com.github.arca.android.ui.glass.rememberArcaGlasmioBackdrop
import com.github.arca.android.ui.glass.recordArcaGlasmioBackdropSource
import android.os.Build
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.NavigationLensState
import com.github.arca.android.ui.glass.NavigationLensGlobalBounds
import com.github.arca.android.ui.glass.glassPalette
import com.github.arca.android.ui.components.*
import com.github.arca.android.ui.theme.*
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicReference

data class ShortcutDef(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit
)

internal val ShortcutDef.panelAccent: Color
    get() = when (id) {
        "vpn" -> Color(0xFFFF822E)
        "ip_test" -> Color(0xFF378CFF)
        "dns" -> Color(0xFF1BA889)
        else -> Color(0xFF6A82BA)
    }

@Composable
private fun DashboardTrafficSummary(
    status: VpnState.Status,
    activeProfileName: String,
    activeNodeName: String,
    lang: String,
    trafficMetrics: StateFlow<TrafficMetrics>,
    onToggleVpn: () -> Unit,
    modeControl: @Composable () -> Unit
) {
    val metrics by trafficMetrics.collectAsState()
    DashboardConnectionCard(
        status = status,
        activeProfileName = activeProfileName,
        activeNodeName = activeNodeName,
        lang = lang,
        uploadSpeed = if (status == VpnState.Status.RUNNING) metrics.upSpeed else "--",
        downloadSpeed = if (status == VpnState.Status.RUNNING) metrics.downSpeed else "--",
        onToggleVpn = onToggleVpn,
        modeControl = modeControl
    )
}

/** Read the moving dock bounds only in the DNS card's restart scope. */
@Composable
private fun DashboardShortcutCard(
    modifier: Modifier,
    item: ShortcutDef,
    isEditing: Boolean,
    lens: NavigationLensState?,
    navigationLensBoundsState: State<NavigationLensGlobalBounds?>?,
    onPickupChanged: (Boolean) -> Unit,
    onRestingBoundsChanged: (LayoutCoordinates, Rect) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    LoonShortcutCard(
        modifier = modifier,
        title = item.title,
        subtitle = item.subtitle,
        icon = item.icon,
        isEditing = isEditing,
        lens = lens,
        lensBoundsState = navigationLensBoundsState,
        lensTintTarget = if (item.id == "dns") Color(0xFF22C55E) else null,
        editorialStyle = true,
        onPickupChanged = onPickupChanged,
        onRestingBoundsChanged = onRestingBoundsChanged,
        onDelete = onDelete,
        onClick = onClick
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    vpnState: VpnState,
    trafficMetrics: StateFlow<TrafficMetrics> = MihomoVpnService.trafficMetrics,
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
    onNavigateToTraffic: () -> Unit = {},
    onUpdateVisibleShortcuts: (Set<String>) -> Unit,
    onUpdateShortcutOrder: (List<String>) -> Unit,
    profiles: List<ProfileItem> = emptyList(),
    selectedProfileId: String? = null,
    onQuickProfileSelect: (ProfileItem) -> Unit = {},
    onQuickProfileOrderChanged: (List<ProfileItem>) -> Unit = {},
    onQuickProfileImported: (ProfileItem) -> Unit = {},
    rules: List<ParsedRule> = emptyList(),
    fakeIpEnabled: Boolean = false,
    onFakeIpEnabledChanged: (Boolean) -> Unit = {},
    perAppProxyMode: PerAppProxyMode = PerAppProxyMode.DISABLED,
    selectedPackages: Set<String> = emptySet(),
    apps: List<InstalledApp> = emptyList(),
    isAppsLoading: Boolean = false,
    onEnsureAppsLoaded: () -> Unit = {},
    onPerAppProxyModeChanged: (PerAppProxyMode) -> Unit = {},
    onToggleAppPackage: (String) -> Unit = {},
    allowLan: Boolean = false,
    onAllowLanChanged: (Boolean) -> Unit = {},
    glassScene: NavigationLensState? = null,
    navigationLensBoundsState: State<NavigationLensGlobalBounds?>? = null,
    onAddNode: () -> Unit = {},
    onRenameNode: (String) -> Unit = {},
    onDeleteNode: (String) -> Unit = {},
    editableNodeNames: Set<String>? = null
) {
    val isRunning = vpnState.status == VpnState.Status.RUNNING
    val isStarting = vpnState.status == VpnState.Status.STARTING
    val coroutineScope = rememberCoroutineScope()

    // Every shortcut can morph from its own card into the shared glass panel.
    // Keep geometry outside Compose state so scrolling only updates the tiny bounds map.
    var activeShortcutPanelId by remember { mutableStateOf<String?>(null) }
    val ipConfigEntry = remember { IpConfigEntryState() }
    var shortcutPanelDataId by remember { mutableStateOf<String?>(null) }
    var hiddenShortcutId by remember { mutableStateOf<String?>(null) }
    var shortcutPanelMorphBounds by remember { mutableStateOf<Rect?>(null) }
    val shortcutBounds = remember { AtomicReference<Map<String, Rect>>(emptyMap()) }
    var quickNodeSearchQuery by remember { mutableStateOf("") }
    var isTestingQuickSheet by remember { mutableStateOf(false) }
    var nodeActionMenuName by remember(mainGroupName, selectedProfileId) { mutableStateOf<String?>(null) }
    // Structural node updates invalidate the menu, but speed-test delay updates do not.
    LaunchedEffect(activeNodeName, mainGroupProxies.map { Triple(it.name, it.type, it.isGroup) }) {
        nodeActionMenuName = null
    }
    LaunchedEffect(activeShortcutPanelId) {
        if (activeShortcutPanelId != "nodes") nodeActionMenuName = null
    }
    val dashboardRootCoordinates = remember { AtomicReference<LayoutCoordinates?>(null) }
    val openShortcutPanel = remember {
        {
            id: String ->
            shortcutPanelDataId = id
            shortcutPanelMorphBounds = shortcutBounds.get()[id]
            hiddenShortcutId = id
            activeShortcutPanelId = id
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

    // Live traffic samples are rendered by a leaf card below. They must not invalidate this
    // shortcut model (or its grid) on every one-second telemetry update.
    val allShortcutMap = remember(
        lang, activeProfileName, activeNodeName, totalNodes, totalRules, requestMonitoringEnabled,
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
                openShortcutPanel("nodes")
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
            "plugins" to ShortcutDef("plugins", AppStrings.get("shortcut_plugins", lang), if (vpnState.coreVersion.isNotBlank()) "v${vpnState.coreVersion.take(8)}" else "Arca Core", { PluginsIllustration() }) {
                activeDialogTitle = "内核信息"
                activeDialogContent = "内核版本: ${vpnState.coreVersion.ifBlank { "v1.18.x" }}\n" +
                        "架构支持: ${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"}"
            },
            "network_share" to ShortcutDef("network_share", AppStrings.get("shortcut_network_share", lang), "127.0.0.1:7890", { NetworkShareIllustration() }) {
                onNavigateToNetworkShare()
            },
            "traffic" to ShortcutDef("traffic", AppStrings.get("shortcut_traffic", lang), AppStrings.get("traffic_records_subtitle", lang), {
                Icon(Icons.Default.DataUsage, contentDescription = null, tint = IosBlue, modifier = Modifier.size(38.dp))
            }) {
                onNavigateToTraffic()
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
    val quietPageColor = if (isDarkTheme) Color(0xFF101114) else Color(0xFFF2F2F5)
    val pageBackdrop = rememberArcaGlasmioBackdrop(enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
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
                AppPalette.dashboardWashTop,
                AppPalette.dashboardWashMid,
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
                .onGloballyPositioned { dashboardRootCoordinates.set(it) }
                .background(quietPageColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (GlassDefaults.enabled) Modifier.recordNavigationScene(titleScene, glassScene) else Modifier)
                    .background(topBgBrush)
                    .recordArcaGlasmioBackdropSource(pageBackdrop)
                    .testTag("dashboard-scroll")
                    .bounceOverscroll(allowBottom = false)
                    // Keep the page viewport full-height behind the translucent dock; reserve clearance in scroll content.
                    .verticalScroll(state = scrollState, enabled = draggedId == null && pickedUpShortcutId == null)
                    .padding(horizontal = 20.dp)
                    .padding(
                        top = headerInset + 12.dp,
                        bottom = mainDockContentInset(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                    )
            ) {

                DashboardTrafficSummary(
                    status = vpnState.status,
                    activeProfileName = activeProfileName,
                    activeNodeName = activeNodeName,
                    lang = lang,
                    trafficMetrics = trafficMetrics,
                    onToggleVpn = onToggleVpn,
                    modeControl = {
                        DashboardModeControl(
                            currentMode = currentMode,
                            onModeSelected = onModeSelected
                        )
                    }
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
                            .iosPressable {
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
                    val displayedShortcutIds = displayedShortcuts.map { it.id }
                    val currentDisplayedRef = rememberUpdatedState(displayedShortcuts)
                    val currentLocalOrderRef = rememberUpdatedState(localOrder)
                    val currentUpdateOrder = rememberUpdatedState(onUpdateShortcutOrder)

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

                                val animatedX = animateFloatAsState(
                                    targetValue = targetSlotX,
                                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                    label = "animX_${item.id}"
                                )
                                val animatedY = animateFloatAsState(
                                    targetValue = targetSlotY,
                                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                    label = "animY_${item.id}"
                                )

                                var itemModifier = Modifier
                                    .size(itemWidthDp, itemHeightDp)
                                    .offset {
                                        // Position changes invalidate placement, not the grid composition.
                                        val x = if (isBeingDragged) dragStartSlotX + dragOffsetState.x else animatedX.value
                                        val y = if (isBeingDragged) dragStartSlotY + dragOffsetState.y else animatedY.value
                                        IntOffset(x.roundToInt(), y.roundToInt())
                                    }
                                    .zIndex(if (isBeingDragged) 100f else if (pickedUpShortcutId == item.id) 50f else 1f)
                                    .then(if (isEditingShortcuts) Modifier.testTag("shortcut-${item.id}") else Modifier)

                                if (isEditingShortcuts) {
                                    // Editing already signals reordering: take the drag at touch slop,
                                    // before the page scrolls, and keep input outside the scaled face.
                                    itemModifier = itemModifier.pointerInput(
                                        item.id, itemWidthPx, itemHeightPx, spacingPx, localOrder, displayedShortcutIds
                                    ) {
                                        fun clearDrag() {
                                            if (draggedId != item.id) return
                                            draggedId = null
                                            dragOffsetState = Offset.Zero
                                            initialSlotIndex = -1
                                            hoverSlotIndex = -1
                                        }
                                        try {
                                            detectDragGestures(
                                                onDragStart = {
                                                    val list = currentDisplayedRef.value
                                                    val curIdx = list.indexOfFirst { it.id == item.id }
                                                    if (curIdx == -1) return@detectDragGestures
                                                    draggedId = item.id
                                                    initialSlotIndex = curIdx
                                                    hoverSlotIndex = curIdx
                                                    dragStartSlotX = (curIdx % 2) * (itemWidthPx + spacingPx)
                                                    dragStartSlotY = (curIdx / 2) * (itemHeightPx + spacingPx)
                                                    dragOffsetState = Offset.Zero
                                                },
                                                onDragEnd = {
                                                    val order = currentLocalOrderRef.value
                                                    val reordered = reorderDashboardShortcut(
                                                        order = order,
                                                        displayedIds = currentDisplayedRef.value.map { it.id },
                                                        draggedId = item.id,
                                                        targetSlot = hoverSlotIndex
                                                    )
                                                    if (reordered != order) {
                                                        localOrder = reordered
                                                        currentUpdateOrder.value(reordered)
                                                    }
                                                    clearDrag()
                                                },
                                                onDragCancel = { clearDrag() },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    if (draggedId != item.id) return@detectDragGestures

                                                    val nextOffset = dragOffsetState + dragAmount
                                                    dragOffsetState = nextOffset
                                                    hoverSlotIndex = shortcutDragTargetSlot(
                                                        startIndex = initialSlotIndex,
                                                        dragX = nextOffset.x,
                                                        dragY = nextOffset.y,
                                                        columnStride = itemWidthPx + spacingPx,
                                                        rowStride = itemHeightPx + spacingPx,
                                                        itemCount = currentDisplayedRef.value.size
                                                    )
                                                }
                                            )
                                        } finally {
                                            // Removing edit mode, a card, or stale grid/order geometry
                                            // cancels pointerInput without necessarily calling onDragCancel.
                                            clearDrag()
                                        }
                                    }
                                }

                                // zIndex must belong to the grid's direct child; the card has its own
                                // wrapper. Scale only the face so pointer deltas remain in grid pixels.
                                Box(modifier = itemModifier) {
                                    DashboardShortcutCard(
                                        modifier = Modifier
                                            // Outside editing retain the original clickable slot semantics.
                                            .then(if (!isEditingShortcuts) Modifier.testTag("shortcut-${item.id}") else Modifier)
                                            .graphicsLayer {
                                                val scale = if (isBeingDragged) 1.06f else 1f
                                                scaleX = scale
                                                scaleY = scale
                                                shadowElevation = if (isBeingDragged) 24f else 0f
                                                alpha = if (item.id == hiddenShortcutId) 0f else 1f
                                            },
                                        item = item,
                                        isEditing = isEditingShortcuts,
                                        lens = glassScene,
                                        navigationLensBoundsState = if (item.id == "dns") navigationLensBoundsState else null,
                                        onPickupChanged = { picked ->
                                            if (picked) pickedUpShortcutId = item.id
                                            else if (pickedUpShortcutId == item.id) pickedUpShortcutId = null
                                        },
                                        onRestingBoundsChanged = { coordinates, restingRect ->
                                            val root = dashboardRootCoordinates.get()
                                            if (root != null && root.isAttached && coordinates.isAttached) {
                                                // Overlay is a sibling in this root. Keep scroll/edit placement,
                                                // but anchor to the settled plane (B rests 6dp above its hit slot),
                                                // not the transient press, tilt or pickup transform.
                                                val topLeft = root.localPositionOf(coordinates, restingRect.topLeft)
                                                val bottomRight = root.localPositionOf(coordinates, restingRect.bottomRight)
                                                shortcutBounds.set(shortcutBounds.get() + (item.id to Rect(topLeft, bottomRight)))
                                            }
                                        },
                                        onDelete = {
                                            if (draggedId == null) { // Prevent delete during drag
                                                onUpdateVisibleShortcuts(visibleShortcuts - item.id)
                                            }
                                        },
                                        onClick = {
                                            if (item.id == "vpn") item.onClick() else openShortcutPanel(item.id)
                                        }
                                    )
                                }
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


            } // Only scrolling content belongs to the recorded scene.

            MainPageScrollEdge(
                backdrop = pageBackdrop,
                headerHeight = headerInset,
                background = quietPageColor,
                tag = "dashboard-top-glasmio-blur",
                modifier = Modifier.align(Alignment.TopCenter),
                scrollOffsetPx = { scrollState.value }
            )
            // Header: unboxed title & mode selector, with restrained scroll blur.
            Column(
                modifier = Modifier
                    .onSizeChanged { headerHeightPx = it.height }
                    .fillMaxWidth()
                    // The fading backdrop behind this column supplies contrast. An opaque
                    // fill here would hide the sampled blur and leave a hard lower edge.
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 12.dp)

            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PageTitleGlass(titleScene, plainTitle = true,
                        modifier = Modifier.testTag("dashboard-nexus-title")) { Text(
                        text = "ARCA",
                        color = LoonTextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp
                    ) }

                }
            }
            // One shared morph hosts the node selector and every other shortcut action.
            val activePanelShortcut = shortcutPanelDataId?.let { allShortcutMap[it] }
                ?: allShortcutMap.getValue("nodes")
            ShortcutMorphOverlay(
                visible = activeShortcutPanelId != null,
                sourceBounds = shortcutPanelMorphBounds,
                liveSourceBounds = { shortcutBounds.get()[activePanelShortcut.id] },
                sourceTitle = activePanelShortcut.title,
                sourceSubtitle = activePanelShortcut.subtitle,
                sourceIcon = activePanelShortcut.icon,
                lens = glassScene,
                raisedPanel = true,
                panelHeight = if (activePanelShortcut.id == "nodes") {
                    (280 + 52 * mainGroupProxies.size).coerceIn(356, 620).dp
                } else null,
                panelTestTag = if (activePanelShortcut.id == "nodes") "quick-node-selector-sheet" else "shortcut-panel-${activePanelShortcut.id}-sheet",
                contentTestTag = if (activePanelShortcut.id == "nodes") "quick-node-selector-content" else "shortcut-panel-${activePanelShortcut.id}-content",
                panelSurfaceCategory = if (activePanelShortcut.id == "nodes")
                    com.github.arca.android.ui.glass.GlassSurfaceCategory.LARGE_PANELS
                else com.github.arca.android.ui.glass.GlassSurfaceCategory.SHORTCUT_CARDS,
                onDismissRequest = {
                    activeShortcutPanelId = null
                },
                onDismissed = {
                    hiddenShortcutId = null
                    shortcutPanelMorphBounds = null
                    shortcutPanelDataId = null
                    ipConfigEntry.reset()
                },
                pinnedOverlay = if (activePanelShortcut.id == "ip_test") ({
                    // Fixed in the panel's bottom-right corner, outside the scrolling body and its
                    // enter/exit animation. The body keeps 54dp of clear space under its last row.
                    ScamalyticsConfigLink(
                        onClick = { ipConfigEntry.settingsOpen.value = true },
                        enabled = ipConfigEntry.linkEnabled,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 8.dp)
                    )
                }) else null
            ) {
                if (activePanelShortcut.id == "nodes") {
                    val filteredNodes = remember(mainGroupProxies, quickNodeSearchQuery) {
                        if (quickNodeSearchQuery.isBlank()) mainGroupProxies
                        else mainGroupProxies.filter { it.name.contains(quickNodeSearchQuery, ignoreCase = true) }
                    }
                    val nodeListState = rememberLazyListState()
                    // Same scroll-edge container as Settings > Glass effects; the search field is
                    // part of the pinned header, the node list stays a lazy list underneath.
                    SettingsDetailEdgeViewport(
                        modifier = Modifier.fillMaxSize().testTag("quick-node-edge-viewport"),
                        header = {
                            Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(activePanelShortcut.panelAccent.copy(alpha = 0.11f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .graphicsLayer(scaleX = 0.60f, scaleY = 0.60f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        activePanelShortcut.icon()
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = AppStrings.get("quick_node_title", lang),
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LoonTextPrimary
                                    )
                                    Text(
                                        text = "${AppStrings.get("dashboard_node_group", lang)}: ${mainGroupName.ifBlank { "--" }}",
                                        fontSize = 12.sp,
                                        color = LoonTextSecondary
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (mainGroupName.isNotBlank() && !isTestingQuickSheet) {
                                            isTestingQuickSheet = true
                                            coroutineScope.launch {
                                                onHealthCheck(mainGroupName)
                                                delay(1800)
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
                                IconButton(
                                    onClick = { activeShortcutPanelId = null },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = LoonTextSecondary)
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                nodeActionMenuName = null
                                activeShortcutPanelId = null
                                onAddNode()
                            },
                            modifier = Modifier.testTag("quick-node-add")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("添加独立节点")
                        }
                        OutlinedTextField(
                            value = quickNodeSearchQuery,
                            onValueChange = { quickNodeSearchQuery = it },
                            modifier = Modifier.fillMaxWidth().testTag("quick-node-search"),
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
                            }
                        }
                    ) {
                        val nodeEdgeInsets = LocalSettingsDetailEdgeInsets.current
                        ReportSettingsDetailScrollOffset {
                            if (nodeListState.firstVisibleItemIndex > 0) Int.MAX_VALUE / 2
                            else nodeListState.firstVisibleItemScrollOffset
                        }
                        if (filteredNodes.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize()
                                    .padding(
                                        top = nodeEdgeInsets?.top ?: 0.dp,
                                        bottom = nodeEdgeInsets?.bottom ?: 0.dp
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (mainGroupProxies.isEmpty()) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    ) {
                                        Text(
                                            text = AppStrings.get("dashboard_nodes_empty_hint", lang),
                                            color = LoonTextSecondary,
                                            fontSize = 13.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Button(
                                            onClick = {
                                                activeShortcutPanelId = null
                                                onNavigateToProfiles()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = activePanelShortcut.panelAccent
                                            )
                                        ) {
                                            Text(AppStrings.get("dashboard_open_profiles", lang))
                                        }
                                    }
                                } else {
                                    Text(
                                        text = AppStrings.get("dashboard_no_matches_hint", lang),
                                        color = LoonTextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = nodeListState,
                                modifier = Modifier.fillMaxSize().testTag("quick-node-list"),
                                contentPadding = PaddingValues(
                                    top = nodeEdgeInsets?.top ?: 0.dp,
                                    bottom = nodeEdgeInsets?.bottom ?: 0.dp
                                )
                            ) {
                                itemsIndexed(
                                    items = filteredNodes,
                                    key = { index, proxy -> "${proxy.name}_$index" }
                                ) { index, proxy ->
                                    val isSelected = proxy.name == activeNodeName
                                    val nodeEmoji = extractLeadingEmoji(proxy.name)
                                    val canManage = canManageProxyNode(proxy, editableNodeNames)
                                    Box {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("quick-node-${proxy.name}")
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    if (isSelected) {
                                                        nodeActionMenuName = proxy.name
                                                    } else {
                                                        nodeActionMenuName = null
                                                        onSelectProxy(mainGroupName, proxy)
                                                        activeShortcutPanelId = null
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                if (nodeEmoji != null) {
                                                    Text(nodeEmoji, fontSize = 17.sp, modifier = Modifier.padding(end = 10.dp))
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
                                                    Text(proxy.type.uppercase(), color = LoonTextMuted, fontSize = 11.sp)
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
                                                    val delayColor = AppPalette.delayColor(proxy.delay, LoonTextMuted)
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
                                        DropdownMenu(
                                            expanded = nodeActionMenuName == proxy.name && activeShortcutPanelId == "nodes",
                                            onDismissRequest = { nodeActionMenuName = null },
                                            modifier = Modifier.testTag("quick-node-action-menu")
                                        ) {
                                            if (!canManage) {
                                                DropdownMenuItem(
                                                    text = { Text("仅默认配置中的独立节点可编辑") },
                                                    enabled = false,
                                                    onClick = {}
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text("重命名") },
                                                modifier = Modifier.testTag("quick-node-rename"),
                                                enabled = canManage,
                                                onClick = {
                                                    nodeActionMenuName = null
                                                    activeShortcutPanelId = null
                                                    onRenameNode(proxy.name)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("删除") },
                                                modifier = Modifier.testTag("quick-node-delete"),
                                                enabled = canManage,
                                                onClick = {
                                                    nodeActionMenuName = null
                                                    activeShortcutPanelId = null
                                                    onDeleteNode(proxy.name)
                                                }
                                            )
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
                } else {
                    DashboardShortcutActionPanel(
                        shortcut = activePanelShortcut,
                        vpnStatus = vpnState.status,
                        activeProfileName = activeProfileName,
                        profiles = profiles,
                        selectedProfileId = selectedProfileId,
                        onSelectProfile = { profile -> onQuickProfileSelect(profile) },
                        onReorderProfiles = onQuickProfileOrderChanged,
                        onProfileImported = onQuickProfileImported,
                        rules = rules,
                        requestMonitoringEnabled = requestMonitoringEnabled,
                        fakeIpEnabled = fakeIpEnabled,
                        onFakeIpEnabledChanged = onFakeIpEnabledChanged,
                        perAppProxyMode = perAppProxyMode,
                        selectedPackages = selectedPackages,
                        apps = apps,
                        isAppsLoading = isAppsLoading,
                        onEnsureAppsLoaded = onEnsureAppsLoaded,
                        onPerAppProxyModeChanged = onPerAppProxyModeChanged,
                        onToggleAppPackage = onToggleAppPackage,
                        allowLan = allowLan,
                        onAllowLanChanged = onAllowLanChanged,
                        trafficMetrics = trafficMetrics,
                        coreVersion = vpnState.coreVersion,
                        isPanelActive = activeShortcutPanelId != null,
                        ipConfigEntry = ipConfigEntry,
                        onClose = { activeShortcutPanelId = null }
                    )
                }
            }
            // =========================================================================
            // Add Hidden Shortcut BottomSheet
            // =========================================================================
            if (showAddShortcutSheet) {
                val hiddenShortcuts = shortcutOrder
                    .filter { !visibleShortcuts.contains(it) }
                    .mapNotNull { allShortcutMap[it] }

                GlassModalBottomSheet(
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
                                    GlassSurface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, LoonCardBorder, RoundedCornerShape(12.dp))
                                            .clickable {
                                                onUpdateVisibleShortcuts(visibleShortcuts + item.id)
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        surfaceCategory = GlassSurfaceCategory.SHORTCUT_CARDS
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
                GlassAlertDialog(
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
