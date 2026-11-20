package com.github.arca.android.ui.screens

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import com.github.arca.android.ui.glass.FROSTED_PAGE_TITLE_FILL_ALPHA
import com.github.arca.android.ui.glass.FROSTED_SELECTED_GLASS_TINT
import com.github.arca.android.ui.glass.rememberArcaGlasmioBackdrop
import com.github.arca.android.ui.glass.recordArcaGlasmioBackdropSource
import com.github.arca.android.ui.glass.PageTitleGlass
import com.github.arca.android.ui.glass.rememberNavigationLens
import com.github.arca.android.ui.glass.recordNavigationScene
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.arca.android.ui.components.*
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.GlassSurface
import com.github.arca.android.ui.glass.GlassSurfaceCategory
import com.github.arca.android.ui.glass.LocalGlassTokens
import com.github.arca.android.ui.glass.NavigationLensState
import com.github.arca.android.ui.glass.LightGlassSelectedTint
import com.github.arca.android.ui.glass.useProxyGroupGlass
import com.github.arca.android.ui.glass.proxyCardNeedsOuterBorder
import android.os.Build
import com.github.arca.android.ui.glass.LocalArcaGlasmioBackdrop
import com.github.arca.android.ui.glass.LocalGlassSettings
import com.github.arca.android.ui.glass.usesGlasmioFrosted
import com.github.arca.android.ui.theme.*

@Composable
fun AnimatedSpeedTestIcon(
    isTesting: Boolean,
    tint: Color = LoonTextPrimary,
    modifier: Modifier = Modifier
) {
    val pulseAlpha = if (isTesting) {
        val infiniteTransition = rememberInfiniteTransition(label = "speed_test_anim")
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "speed_test_alpha"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Icon(
        imageVector = Icons.Default.Bolt,
        contentDescription = "测速",
        tint = tint,
        modifier = modifier.graphicsLayer { alpha = pulseAlpha.value }
    )
}

// Helper to extract emoji/flag from text if present
fun extractLeadingEmoji(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val firstCodePoint = trimmed.codePointAt(0)
    val charCount = Character.charCount(firstCodePoint)
    val isEmoji = firstCodePoint in 0x1F1E6..0x1F1FF || // Regional indicator flags
            firstCodePoint in 0x1F300..0x1FAFF ||       // Misc symbols & pictographs
            firstCodePoint in 0x2600..0x27BF ||         // Misc symbols
            firstCodePoint in 0x2300..0x23FF            // Technical symbols

    if (isEmoji) {
        if (firstCodePoint in 0x1F1E6..0x1F1FF && trimmed.length >= charCount + 2) {
            val secondCodePoint = trimmed.codePointAt(charCount)
            if (secondCodePoint in 0x1F1E6..0x1F1FF) {
                return trimmed.substring(0, charCount + Character.charCount(secondCodePoint))
            }
        }
        return trimmed.substring(0, charCount)
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxiesScreen(
    groups: List<String>,
    groupMap: Map<String, ProxyGroup>,
    groupIcons: Map<String, String> = emptyMap(),
    currentGroup: ProxyGroup?,
    selectedGroupName: String,
    currentViewMode: String,
    showGroupIcons: Boolean = true,
    nodeColumns: Int = 1,
    onViewModeChanged: (String) -> Unit,
    onShowGroupIconsChanged: (Boolean) -> Unit,
    onNodeColumnsChanged: (Int) -> Unit = {},
    onSelectGroup: (String) -> Unit,
    onSelectProxy: (group: String, proxy: Proxy) -> Unit,
    onHealthCheck: (group: String) -> Unit,
    testingNodes: Set<String> = emptySet(),
    onTestSingleNode: (group: String, node: String) -> Unit = { _, _ -> },
    tabsListState: LazyListState = rememberLazyListState(),
    listModeState: LazyListState = rememberLazyListState(),
    groupRowState: LazyListState = rememberLazyListState(),
    expandedGroups: SnapshotStateMap<String, Boolean> = remember { mutableStateMapOf() },
    perGroupTabStates: SnapshotStateMap<String, LazyListState> = remember { mutableStateMapOf() },
    glassScene: NavigationLensState? = null,
    onAddNode: (() -> Unit)? = null,
    onEditNode: ((String) -> Unit)? = null,
    editableNodeNames: Set<String>? = null
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var policyMoreButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var hidePolicyMoreSource by remember { mutableStateOf(false) }
    val activeTabState = if (selectedGroupName.isNotBlank()) {
        perGroupTabStates.getOrPut(selectedGroupName) { LazyListState() }
    } else tabsListState

    val isListMode = currentViewMode == "LIST"
    val visibleGroups = remember(groups, groupMap) {
        groups.filter { gName -> groupMap[gName]?.hidden != true }
    }
    val isAnyTesting = if (isListMode) {
        testingNodes.isNotEmpty()
    } else {
        testingNodes.contains(selectedGroupName) || currentGroup?.proxies?.any { testingNodes.contains("$selectedGroupName:${it.name}") || testingNodes.contains(it.name) } == true
    }

    val lang = LocalAppLanguage.current
    val colors = LocalLoonColors.current
    val quietPages = GlassDefaults.quietPages
    val pullState = rememberPullToRefreshState()
    val titleScene = rememberNavigationLens()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerInset = with(LocalDensity.current) { headerHeightPx.toDp() }
    val pageBackdrop = rememberArcaGlasmioBackdrop(enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    val activeScrollState = if (isListMode) listModeState else activeTabState

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (quietPages || !GlassDefaults.enabled) colors.bg else Color.Transparent)
    ) {
        Box(
            // Give scrolling content its own viewport so neither cards nor their shadows
            // can scroll into the pinned title and group capsules.
            modifier = Modifier.fillMaxSize().padding(top = headerInset).clipToBounds()
                .then(if (GlassDefaults.enabled) Modifier.recordNavigationScene(titleScene, glassScene) else Modifier)
                .recordArcaGlasmioBackdropSource(pageBackdrop)
                .testTag("policy-content-viewport")
        ) {
        // =====================================================================
        // MODE 1: TABS VIEW (High-Performance, Butter Smooth)
        // =====================================================================
        if (!isListMode) {
            val proxies = currentGroup?.proxies ?: emptyList()

            if (proxies.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    GlassSurface(
                        modifier = Modifier.padding(horizontal = 28.dp),
                        shape = RoundedCornerShape(18.dp),
                        blurEnabled = false
                    ) {
                        Text(
                            text = if (visibleGroups.isEmpty()) "暂无节点数据\n请在[配置]中添加订阅" else "当前策略组无可用节点",
                            color = LoonTextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )
                    }
                }
            } else {
                val chunkedRows = remember(proxies, nodeColumns) {
                    proxies.chunked(nodeColumns.coerceIn(1, 3))
                }
                val currentNow = currentGroup?.now

                PullToRefreshBox(
                    isRefreshing = isAnyTesting,
                    onRefresh = {
                        if (selectedGroupName.isNotBlank()) onHealthCheck(selectedGroupName)
                        else if (visibleGroups.isNotEmpty()) onHealthCheck(visibleGroups.first())
                    },
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = { ProxiesPullIndicator(pullState, isAnyTesting, 0.dp) }
                ) {
                    LazyColumn(
                        state = activeTabState,
                        modifier = Modifier.testTag("policy-node-list")
                            .fillMaxSize()
                            .bounceOverscroll(allowTop = false, allowBottom = false),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = mainDockContentInset(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tabRowKeys = proxyRowKeysOf(chunkedRows)
                        itemsIndexed(
                            items = chunkedRows,
                            key = { index, _ -> tabRowKeys[index] }
                        ) { _, rowProxies ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (proxy in rowProxies) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        val isItemTesting = testingNodes.contains(proxy.name) || testingNodes.contains("$selectedGroupName:${proxy.name}")
                                        LoonProxyItemCard(
                                            proxy = proxy,
                                            glassScene = glassScene,
                                            isSelected = proxy.name == currentNow,
                                            columns = nodeColumns,
                                            isTesting = isItemTesting,
                                            onClick = { onSelectProxy(selectedGroupName, proxy) },
                                            onTestSingleNode = { onTestSingleNode(selectedGroupName, proxy.name) },
                                            onEditNode = onEditNode?.takeIf { canEditProxyNode(proxy, editableNodeNames) }
                                        )
                                    }
                                }
                                // Fill remaining slots in last row if not full
                                val remainder = nodeColumns - rowProxies.size
                                if (remainder > 0) {
                                    repeat(remainder) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // =====================================================================
        // MODE 2: FLATTENED 120 FPS LIST VIEW (Zero Frame Drops, Default Collapsed)
        // =====================================================================
        else {
            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无策略分组数据",
                        color = LoonTextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isAnyTesting,
                    // Blank group = test every visible group.
                    onRefresh = { onHealthCheck("") },
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = { ProxiesPullIndicator(pullState, isAnyTesting, 0.dp) }
                ) {
                    LazyColumn(
                        state = listModeState,
                        modifier = Modifier
                            .testTag("policy-group-list")
                            .fillMaxSize()
                            .bounceOverscroll(allowTop = false, allowBottom = false),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = mainDockContentInset(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
                    ) {
                        visibleGroups.forEachIndexed { groupIndex, groupName ->
                            val group = groupMap[groupName]
                            val isExpanded = expandedGroups[groupName] ?: false
                            val proxies = group?.proxies ?: emptyList()
                            val iconUrl = groupIcons[groupName]

                            item(key = "grp_item_$groupName", contentType = "group_header") {
                                val isGroupTesting = testingNodes.contains(groupName) || proxies.any { testingNodes.contains("$groupName:${it.name}") || (it.isGroup && testingNodes.contains(it.name)) }
                                Box(Modifier.fillMaxWidth().animateItem()
                                    .padding(top = if (groupIndex == 0) 0.dp else 8.dp)) {
                                    LoonGroupHeaderCard(
                                        groupName = groupName,
                                        groupType = group?.type ?: "select",
                                        activeNode = group?.now ?: "",
                                        iconUrl = iconUrl,
                                        showGroupIcons = showGroupIcons,
                                        isExpanded = isExpanded,
                                        isTesting = isGroupTesting,
                                        onHealthCheck = { onHealthCheck(groupName) },
                                        onToggleExpand = { expandedGroups[groupName] = !isExpanded }
                                    )
                                }
                            }
                            // Each row is a lazy item: expanding a large subscription must not
                            // compose its off-screen cards or start hundreds of progress indicators.
                            // Item placement/fades retain motion without measuring a full-group expansion.
                            if (isExpanded) {
                                val columns = nodeColumns.coerceIn(1, 3)
                                val rowCount = (proxies.size + columns - 1) / columns
                                // A lazy list crashes on duplicate keys, and subscriptions can
                                // contain repeated node names; make every row key unique.
                                val rowKeys = proxyGroupRowKeys(groupName, proxies, columns)
                                items(
                                    count = rowCount,
                                    key = { rowIndex -> rowKeys[rowIndex] },
                                    contentType = { "group_node_row_$columns" }
                                ) { rowIndex ->
                                    val firstIndex = rowIndex * columns
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .animateItem(
                                                fadeInSpec = tween(150, delayMillis = 30),
                                                placementSpec = spring(
                                                    stiffness = Spring.StiffnessMediumLow,
                                                    dampingRatio = Spring.DampingRatioNoBouncy
                                                ),
                                                fadeOutSpec = tween(120)
                                            )
                                            .padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        repeat(columns) { column ->
                                            val proxy = proxies.getOrNull(firstIndex + column)
                                            if (proxy == null) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            } else {
                                                key(proxy.name) {
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        val isItemTesting = testingNodes.contains(proxy.name) || testingNodes.contains("$groupName:${proxy.name}")
                                                        LoonProxyItemCard(
                                                            proxy = proxy,
                                                            glassScene = glassScene,
                                                            isSelected = proxy.name == group?.now,
                                                            columns = columns,
                                                            isTesting = isItemTesting,
                                                            onClick = { onSelectProxy(groupName, proxy) },
                                                            onTestSingleNode = { onTestSingleNode(groupName, proxy.name) },
                                                            onEditNode = onEditNode?.takeIf { canEditProxyNode(proxy, editableNodeNames) }
                                                        )
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
            }
        }
    }

        MainPageScrollEdge(
            backdrop = pageBackdrop,
            headerHeight = headerInset,
            background = colors.bg,
            tag = "policy-top-glasmio-blur",
            modifier = Modifier.align(Alignment.TopCenter),
            scrollOffsetPx = {
                if (activeScrollState.firstVisibleItemIndex > 0) Int.MAX_VALUE
                else activeScrollState.firstVisibleItemScrollOffset
            }
        )
        // Titles and actions stay sharp above the shared scroll-edge blur.
        Column(Modifier.onSizeChanged { headerHeightPx = it.height }.fillMaxWidth()
            .testTag("policy-header")
            .then(if (quietPages) Modifier.background(colors.bg) else Modifier)
            .statusBarsPadding()
            .padding(top = 12.dp)
            ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                PageTitleGlass(titleScene, plainTitle = true) { Text(
                    text = AppStrings.get("proxies_title", lang),
                    color = LoonTextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                ) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp)
                        .clip(CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        if (selectedGroupName.isNotBlank()) {
                            onHealthCheck(selectedGroupName)
                        } else if (visibleGroups.isNotEmpty()) {
                            onHealthCheck(visibleGroups.first())
                        }
                    }.testTag("policy-health-check-button"),
                    contentAlignment = Alignment.Center
                ) {
                    GlassSurface(
                        Modifier.matchParentSize(), shape = CircleShape,
                        surfaceCategory = GlassSurfaceCategory.TITLE_CAPSULES,
                        surfaceAlphaScale = 0.9f, honorShape = true
                    ) {}
                    AnimatedSpeedTestIcon(
                        isTesting = isAnyTesting,
                        tint = LoonTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier.size(48.dp)
                        .clip(CircleShape)
                        .onGloballyPositioned { policyMoreButtonBounds = it.boundsInWindow() }
                        .iosPressable(pressedScale = 0.96f, role = Role.Button) {
                            showMoreMenu = true
                        }
                        .graphicsLayer { alpha = if (hidePolicyMoreSource) 0f else 1f }
                        .testTag("policy-more-button"),
                    contentAlignment = Alignment.Center
                ) {
                    GlassSurface(
                        Modifier.matchParentSize(), shape = CircleShape,
                        surfaceCategory = GlassSurfaceCategory.TITLE_CAPSULES,
                        surfaceAlphaScale = 0.9f, honorShape = true
                    ) {}
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多设置",
                        tint = LoonTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        shape = RoundedCornerShape(18.dp),
                        containerColor = colors.card,
                        modifier = Modifier.widthIn(min = 192.dp).testTag("policy-more-menu")
                    ) {
                        if (onAddNode != null) {
                            DropdownMenuItem(
                                text = { Text("添加节点") },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = { showMoreMenu = false; onAddNode() },
                                modifier = Modifier.testTag("policy-add-node")
                            )
                            HorizontalDivider(color = LoonCardBorder.copy(alpha = 0.35f))
                        }
                        DropdownMenuItem(
                            text = { Text("显示设置") },
                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                hidePolicyMoreSource = true
                                showOptionsMenu = true
                            },
                            modifier = Modifier.testTag("policy-display-settings")
                        )
                    }
                }
            }
        }

            // Group Selector Tabs (Scrollable)
            if (visibleGroups.isNotEmpty()) {
                LazyRow(
                    state = groupRowState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .testTag("policy-group-tabs"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visibleGroups.size, key = { visibleGroups[it] }) { index ->
                        val groupName = visibleGroups[index]
                        val isSelected = groupName == selectedGroupName
                        val groupIconUrl = groupIcons[groupName]

                        PageTitleGlass(
                            lens = titleScene,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                            showRim = false,
                            frostedFillAlpha = FROSTED_PAGE_TITLE_FILL_ALPHA,
                            frostedTint = if (isSelected) FROSTED_SELECTED_GLASS_TINT else null,
                            shape = RoundedCornerShape(12.dp),
                            cornerRadius = 12.dp,
                            modifier = Modifier.testTag("policy-group-tab-$groupName")
                                .then(if (quietPages) {
                                    val shape = RoundedCornerShape(12.dp)
                                    Modifier
                                        .cardDepth(shape, colors.isDark, 1.dp)
                                        .background(
                                            if (isSelected) lerp(colors.card, LoonBlue, if (colors.isDark) 0.18f else 0.07f) else colors.card,
                                            shape
                                        )
                                        .then(if (isSelected) Modifier.border(1.dp, LoonBlue.copy(alpha = 0.55f), shape) else Modifier)
                                } else Modifier)
                                .clip(RoundedCornerShape(12.dp))
                                .iosPressable { onSelectGroup(groupName) }
                                .then(if (quietPages) Modifier.padding(horizontal = 10.dp) else Modifier)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (showGroupIcons && !groupIconUrl.isNullOrBlank()) {
                                    RemoteIcon(
                                        url = groupIconUrl,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .padding(end = 6.dp),
                                        contentDescription = proxyGroupDisplayName(groupName)
                                    )
                                }
                                Text(
                                    text = proxyGroupDisplayName(groupName),
                                    color = if (quietPages && isSelected) LoonBlue else LoonTextPrimary,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

        }

        ShortcutMorphOverlay(
            visible = showOptionsMenu,
            sourceBounds = policyMoreButtonBounds,
            liveSourceBounds = { policyMoreButtonBounds },
            sourceTitle = "策略设置",
            sourceSubtitle = "分组显示与节点排版",
            sourceIcon = { Icon(Icons.Default.MoreVert, contentDescription = null, tint = LoonTextPrimary) },
            lens = titleScene,
            onDismissRequest = { showOptionsMenu = false },
            onDismissed = { hidePolicyMoreSource = false },
            panelTestTag = "policy-options-panel",
            contentTestTag = "policy-options-content",
            panelHeightFraction = 0.62f,
            raisedPanel = true,
            motionProfile = ShortcutMorphMotionProfile.QUVEN_MENU,
            sourceCornerRadius = 24.dp,
            sourceContent = {
                Box(
                    Modifier.fillMaxSize().testTag("policy-more-source-face"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = LoonTextPrimary, modifier = Modifier.size(22.dp))
                }
            }
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("策略设置", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = LoonTextPrimary)
                    Text("调整当前分组的显示和排版", fontSize = 12.sp, color = LoonTextSecondary)
                }
                IconButton(onClick = { showOptionsMenu = false }, modifier = Modifier.size(36.dp).testTag("policy-options-close")) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = LoonTextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("显示方式", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = LoonTextSecondary)
                    AnimatedGlassSegmentGroup(
                        options = listOf(
                            "TABS" to AppStrings.get("proxies_view_tabs", lang),
                            "LIST" to AppStrings.get("proxies_view_list", lang)
                        ),
                        selectedKey = currentViewMode,
                        onSelect = onViewModeChanged,
                        testTag = "proxies-view-mode"
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("节点排版", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = LoonTextSecondary)
                    AnimatedGlassSegmentGroup(
                        options = listOf("1" to "单列", "2" to "双列", "3" to "三列"),
                        selectedKey = nodeColumns.toString(),
                        onSelect = { onNodeColumnsChanged(it.toInt()) },
                        testTag = "proxies-node-columns"
                    )
                }
                HorizontalDivider(color = LoonCardBorder.copy(alpha = 0.35f), thickness = 0.7.dp)
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("显示策略组远程图标", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = LoonTextPrimary)
                        Text("在组标题旁展示远程图标", fontSize = 11.sp, color = LoonTextMuted)
                    }
                    ArcaGlasmioSwitch(checked = showGroupIcons, onCheckedChange = onShowGroupIconsChanged,
                        modifier = Modifier.testTag("policy-group-icons-switch"))
                }
            }
        }

    }
}

// -----------------------------------------------------------------------------
// Header Card for List Mode (120 FPS Flat & Fast)
// -----------------------------------------------------------------------------
@Composable
private fun LoonGroupHeaderCard(
    groupName: String,
    groupType: String,
    activeNode: String,
    iconUrl: String?,
    showGroupIcons: Boolean,
    isExpanded: Boolean,
    isTesting: Boolean = false,
    onHealthCheck: () -> Unit,
    onToggleExpand: () -> Unit
) {
    val quietPages = GlassDefaults.quietPages
    val shape = RoundedCornerShape(if (quietPages) 16.dp else 20.dp)
    ProxyGlassCard(
        modifier = Modifier
            .testTag("policy-group-header-$groupName")
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (quietPages) 62.dp else 56.dp)
            .then(if (quietPages) Modifier else Modifier.clip(shape))
            .then(if (GlassDefaults.enabled || quietPages) Modifier else Modifier.border(1.dp, LoonCardBorder, shape))
            .clickable { onToggleExpand() },
        color = LoonCard,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (showGroupIcons && !iconUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        RemoteIcon(
                            url = iconUrl,
                            modifier = Modifier.size(30.dp),
                            contentDescription = proxyGroupDisplayName(groupName)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 1.dp)
                ) {
                    Text(
                        text = proxyGroupDisplayName(groupName),
                        color = LoonTextPrimary,
                        fontSize = if (quietPages) 15.sp else 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (activeNode.isNotBlank()) activeNode else "未选择节点",
                        color = if (activeNode.isNotBlank()) LoonTextSecondary else LoonTextMuted.copy(alpha = 0.6f),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = if (activeNode.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = groupType.uppercase(),
                    color = LoonTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 6.dp)
                )

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn(animationSpec = tween(150)) + expandHorizontally(expandFrom = Alignment.End),
                    exit = fadeOut(animationSpec = tween(120)) + shrinkHorizontally(shrinkTowards = Alignment.End)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (!isTesting) onHealthCheck() },
                            enabled = !isTesting,
                            modifier = Modifier.size(30.dp)
                        ) {
                            AnimatedSpeedTestIcon(
                                isTesting = isTesting,
                                tint = LoonTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                }

                val arrowRotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                    label = "arrow_rot"
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = LoonTextSecondary,
                    modifier = Modifier
                        .size(19.dp)
                        .graphicsLayer { rotationZ = arrowRotation }
                )
            }
        }
    }
}

// Runtime group names can include a profile UUID; hide that implementation detail
// in labels only. Lazy keys, selection and callbacks always retain the original name.
private val ProxyGroupUuid = Regex(
    """(?i)[(\[（【]?\s*(?<![0-9a-f])[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?![0-9a-f])\s*[)\]）】]?"""
)

internal fun proxyGroupDisplayName(name: String): String {
    if (!ProxyGroupUuid.containsMatchIn(name)) return name
    return ProxyGroupUuid.replace(name, "")
        .trim(' ', '-', '_', '·', '|', ':', '/')
        .ifBlank { "策略组" }
}

/** A missing profile allowlist must never expose provider or runtime-only nodes for editing. */
internal fun canEditProxyNode(proxy: Proxy, editableNodeNames: Set<String>?): Boolean =
    !proxy.isGroup && editableNodeNames?.contains(proxy.name) == true

// Keep the selected node perceptibly pressed without changing its measured layout size.
internal fun selectedProxyCardScale(isSelected: Boolean): Float = if (isSelected) 0.97f else 1f

// Match the neutral gray used by the selected strategy capsule in the reference UI.
internal val SelectedProxyCardFill = LightGlassSelectedTint
private val SelectedProxyCardText = AppPalette.selectedOnFrost
private val SelectedProxyCardMutedText = AppPalette.selectedOnFrostMuted

// -----------------------------------------------------------------------------
// Multi-Column Responsive Proxy Node Card (Supports 1, 2, or 3 columns)
// -----------------------------------------------------------------------------
@Composable
private fun LoonProxyItemCard(
    proxy: Proxy,
    glassScene: NavigationLensState? = null,
    isSelected: Boolean,
    columns: Int = 1,
    isTesting: Boolean = false,
    onClick: () -> Unit,
    onTestSingleNode: () -> Unit = {},
    onEditNode: ((String) -> Unit)? = null
) {
    var showRenameMenu by remember(proxy.name) { mutableStateOf(false) }
    LaunchedEffect(proxy.name, onEditNode != null) {
        if (onEditNode == null) showRenameMenu = false
    }
    val nodeInteraction = if (onEditNode == null) {
        Modifier.iosPressable(onClick = onClick)
    } else {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
            onLongClickLabel = "重命名",
            onLongClick = { showRenameMenu = true }
        )
    }
    val latencyInteraction = if (onEditNode == null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onTestSingleNode
        )
    } else {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onTestSingleNode,
            onLongClickLabel = "重命名",
            onLongClick = { showRenameMenu = true }
        )
    }
    val nodeEmoji = extractLeadingEmoji(proxy.name)
    val displayName = if (nodeEmoji != null && proxy.name.startsWith(nodeEmoji)) {
        proxy.name.removePrefix(nodeEmoji).trim()
    } else {
        proxy.name
    }

    val isTimeout = proxy.delay == -2 || proxy.delay >= 65535
    val isValidDelay = proxy.delay in 1..65534
    val delayColor = AppPalette.delayColor(proxy.delay, LoonTextMuted)
    val delayText = when {
        isTimeout -> "超时"
        isValidDelay -> "${proxy.delay} ms"
        else -> "--"
    }

    val quietPages = GlassDefaults.quietPages
    val colors = LocalLoonColors.current
    val cardBg = when {
        quietPages && isSelected -> lerp(colors.card, LoonBlue, if (colors.isDark) 0.18f else 0.07f)
        quietPages -> colors.card
        isSelected -> SelectedProxyCardFill
        else -> LoonCard
    }
    val nameColor = if (quietPages) colors.textPrimary else if (isSelected) SelectedProxyCardText else LoonTextPrimary
    val typeColor = if (quietPages) colors.textSecondary else if (isSelected) SelectedProxyCardMutedText else LoonTextMuted

    Box {
        when (columns) {
        // ---------------------------------------------------------------------
        // 1 Column: Standard Horizontal Card (Fixed Height 54dp)
        // ---------------------------------------------------------------------
        1 -> {
            ProxyGlassCard(
                modifier = Modifier
                    .testTag("proxy-card-${proxy.name}")
                    .fillMaxWidth()
                    .graphicsLayer {
                        val scale = if (quietPages) 1f else selectedProxyCardScale(isSelected)
                        scaleX = scale
                        scaleY = scale
                    }
                    .height(54.dp)
                    .then(if (isSelected || GlassDefaults.enabled) Modifier else Modifier.border(.5.dp, LoonCardBorder, RoundedCornerShape(14.dp)))
                    .then(nodeInteraction),

                color = cardBg,
                shape = RoundedCornerShape(if (columns == 3) 12.dp else 14.dp),
                accent = null,
                groupScene = glassScene,
                selected = isSelected
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (nodeEmoji != null) {
                            Text(
                                text = nodeEmoji,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(end = 9.dp)
                            )
                        }

                        Column(if (quietPages) Modifier.weight(1f).padding(end = 8.dp) else Modifier) {
                            Text(
                                text = displayName,
                                color = nameColor,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = proxy.type.uppercase(),
                                color = typeColor,
                                fontSize = 9.5.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .then(if (quietPages) Modifier.width(64.dp).heightIn(min = 24.dp)
                                    else Modifier.defaultMinSize(minWidth = 50.dp, minHeight = 24.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .then(latencyInteraction),
                            contentAlignment = if (quietPages) Alignment.CenterEnd else Alignment.Center
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = IosBlue
                                )
                            } else {
                                Text(
                                    text = delayText,
                                    color = delayColor,
                                    style = if (quietPages) LocalTextStyle.current.copy(fontFeatureSettings = "tnum") else LocalTextStyle.current,
                                    maxLines = 1,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isValidDelay || isTimeout) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }

                        if (quietPages) {
                            Spacer(modifier = Modifier.width(8.dp))
                            ProxySelectionMark(isSelected, Modifier.size(16.dp))
                        } else if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = SelectedProxyCardText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // 2 Columns: Balanced Grid Card (Fixed Height 62dp)
        // ---------------------------------------------------------------------
        2 -> {
            val groupGlass = GlassDefaults.enabled && useProxyGroupGlass(
                proxy.isGroup, columns, glassScene != null
            )
            val cardShape = RoundedCornerShape(14.dp)
            ProxyGlassCard(
                modifier = Modifier
                    .testTag("proxy-card-${proxy.name}")
                    .fillMaxWidth()
                    .graphicsLayer {
                        val scale = if (quietPages) 1f else selectedProxyCardScale(isSelected)
                        scaleX = scale
                        scaleY = scale
                    }
                    .height(62.dp)
                    .then(
                        if (!GlassDefaults.enabled && proxyCardNeedsOuterBorder(groupGlass, isSelected)) {
                            Modifier.border(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) IosBlue else LoonCardBorder,
                                shape = cardShape
                            )
                        } else Modifier
                    )
                    .then(nodeInteraction),

                color = cardBg,
                shape = cardShape,
                groupScene = glassScene,
                accent = null,
                selected = isSelected
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (nodeEmoji != null) {
                            Text(
                                text = nodeEmoji,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = displayName,
                            color = nameColor,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (quietPages) {
                            Spacer(Modifier.width(5.dp))
                            ProxySelectionMark(isSelected, Modifier.size(14.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = proxy.type.uppercase(),
                            color = typeColor,
                            fontSize = 8.5.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = if (quietPages) Modifier.weight(1f).padding(end = 4.dp) else Modifier
                        )
                        Box(
                            modifier = Modifier
                                .then(if (quietPages) Modifier.width(56.dp).heightIn(min = 20.dp)
                                    else Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 20.dp))
                                .clip(RoundedCornerShape(5.dp))
                                .then(latencyInteraction),
                            contentAlignment = if (quietPages) Alignment.CenterEnd else Alignment.Center
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.3.dp,
                                    color = IosBlue
                                )
                            } else {
                                Text(
                                    text = delayText,
                                    color = delayColor,
                                    style = if (quietPages) LocalTextStyle.current.copy(fontFeatureSettings = "tnum") else LocalTextStyle.current,
                                    maxLines = 1,
                                    fontSize = 10.sp,
                                    fontWeight = if (isValidDelay || isTimeout) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // 3 Columns: High-Density Mini Grid Card (Fixed Height 56dp)
        // ---------------------------------------------------------------------
        else -> {
            ProxyGlassCard(
                modifier = Modifier
                    .testTag("proxy-card-${proxy.name}")
                    .fillMaxWidth()
                    .graphicsLayer {
                        val scale = if (quietPages) 1f else selectedProxyCardScale(isSelected)
                        scaleX = scale
                        scaleY = scale
                    }
                    .height(56.dp)
                    .then(if (isSelected || GlassDefaults.enabled) Modifier else Modifier.border(.5.dp, LoonCardBorder, RoundedCornerShape(12.dp)))
                    .then(nodeInteraction),

                color = cardBg,
                shape = RoundedCornerShape(if (columns == 3) 12.dp else 14.dp),
                accent = null,
                groupScene = glassScene,
                selected = isSelected
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (nodeEmoji != null) {
                            Text(
                                text = nodeEmoji,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                        }
                        Text(
                            text = displayName,
                            color = nameColor,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (quietPages) {
                            Spacer(Modifier.width(3.dp))
                            ProxySelectionMark(isSelected, Modifier.size(12.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = proxy.type.uppercase(),
                            color = typeColor,
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = if (quietPages) Modifier.weight(1f).padding(end = 3.dp) else Modifier
                        )
                        Box(
                            modifier = Modifier
                                .then(if (quietPages) Modifier.width(44.dp).heightIn(min = 18.dp)
                                    else Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 18.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .then(latencyInteraction),
                            contentAlignment = if (quietPages) Alignment.CenterEnd else Alignment.Center
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(9.dp),
                                    strokeWidth = 1.2.dp,
                                    color = IosBlue
                                )
                            } else {
                                Text(
                                    text = delayText,
                                    color = delayColor,
                                    style = if (quietPages) LocalTextStyle.current.copy(fontFeatureSettings = "tnum") else LocalTextStyle.current,
                                    maxLines = 1,
                                    fontSize = 9.sp,
                                    fontWeight = if (isValidDelay || isTimeout) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
        DropdownMenu(
            expanded = showRenameMenu && onEditNode != null,
            onDismissRequest = { showRenameMenu = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = colors.card,
            modifier = Modifier.widthIn(min = 180.dp).testTag("proxy-rename-menu-${proxy.name}")
        ) {
            DropdownMenuItem(
                text = { Text("重命名") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { showRenameMenu = false; onEditNode?.invoke(proxy.name) },
                modifier = Modifier.testTag("proxy-rename-action-${proxy.name}")
            )
        }
    }
}


/** A reserved slot keeps labels and latency aligned as selection moves between nodes. */
@Composable
private fun ProxySelectionMark(selected: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (selected) {
            Box(Modifier.matchParentSize().background(LoonBlue, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White,
                    modifier = Modifier.fillMaxSize(0.72f))
            }
        }
    }
}

/** Solid, shallow cards for quiet pages; the legacy glass branch remains available. */
@Composable
private fun ProxyGlassCard(
    modifier: Modifier,
    color: Color,
    shape: RoundedCornerShape,
    accent: Color? = null,
    @Suppress("UNUSED_PARAMETER") groupScene: NavigationLensState? = null,
    selected: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    if (GlassDefaults.quietPages) {
        // One surface per header or node, with a uniform shallow lift and no inset panels.
        Surface(
            modifier = modifier.cardDepth(shape, LocalLoonColors.current.isDark, 1.dp),
            color = color,
            shape = shape
        ) {
            Box(content = content)
        }
    } else if (GlassDefaults.enabled) {
        // The rounded shadow must sit outside the clip; clipping the caller's
        // modifier first cuts off the lift and can leave a rectangular edge.
        val surfaceModifier = modifier
            .shadow(if (selected) 3.dp else 0.dp, shape, clip = false)
            .clip(shape)
        // Keep the glass-material tag on its own inner node, as ProxyGroupGlass did:
        // the caller's modifier already carries the proxy-card tag, and a single node
        // can only hold one testTag, so merging the two tags loses one of them.
        Box(surfaceModifier) {
            Box(Modifier.matchParentSize()
                .testTag(if (selected) "proxy-selected-glass-material" else "proxy-group-glass-material"))
            Box(Modifier.matchParentSize().testTag("proxy-glasmio-main-material"))
            GlassSurface(
                modifier = Modifier.matchParentSize(),
                shape = shape,
                accent = if (selected) SelectedProxyCardFill else accent,
                surfaceCategory = GlassSurfaceCategory.LARGE_PANELS,
                surfaceAlphaScale = if (selected) 1f else 0.82f,
                content = content
            )
        }
    } else if (selected) {
        Surface(modifier = modifier.shadow(3.dp, shape, clip = false), color = SelectedProxyCardFill, shape = shape) { Box(content = content) }
    } else {
        Surface(modifier = modifier, color = color, shape = shape) { Box(content = content) }
    }
}

/** Pull indicator placed below the pinned glass header instead of under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ProxiesPullIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    headerInset: androidx.compose.ui.unit.Dp
) {
    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = isRefreshing,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = headerInset),
        containerColor = LoonCard,
        color = IosBlue
    )
}

@Composable
private fun AnimatedGlassSegmentGroup(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    testTag: String
) {
    if (options.isEmpty()) return
    val isDark = LocalLoonColors.current.isDark
    val backdrop = LocalArcaGlasmioBackdrop.current
    val useRefractingControl = GlassDefaults.enabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        LocalGlassSettings.current.glassPreset.usesGlasmioFrosted() && backdrop != null
    if (useRefractingControl) {
        Box(Modifier.fillMaxWidth().testTag("$testTag-segment-group")) {
            RefractingSegmentedControl(options, selectedKey, onSelect, testTag)
        }
        return
    }
    val selectedIndex = options.indexOfFirst { it.first == selectedKey }.coerceAtLeast(0)
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(durationMillis = 430, easing = FastOutSlowInEasing),
        label = "$testTag-selection"
    )
    val shape = RoundedCornerShape(14.dp)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(if (isDark) Color.White.copy(alpha = 0.065f) else Color.Black.copy(alpha = 0.035f))
            .border(0.7.dp, LoonCardBorder.copy(alpha = if (isDark) 0.38f else 0.32f), shape)
            .testTag("$testTag-segment-group")
    ) {
        val segmentWidth = maxWidth / options.size
        Box(
            modifier = Modifier
                .offset(x = segmentWidth * animatedIndex)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (isDark) LoonBlue.copy(alpha = 0.25f) else LoonBlue.copy(alpha = 0.12f))
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { (key, label) ->
                val isSelected = key == selectedKey
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(key) }
                        .testTag("$testTag-$key"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) LoonTextPrimary else LoonTextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}


/**
 * Unique, order-stable lazy keys for the node rows of one group. A row is keyed by its first
 * node's name (so animations follow the node when the list reorders); when the same first name
 * appears again, later rows get a "#n" occurrence suffix.
 */
internal fun proxyGroupRowKeys(groupName: String, proxies: List<Proxy>, columns: Int): List<String> {
    val perRow = columns.coerceIn(1, 3)
    val rowCount = (proxies.size + perRow - 1) / perRow
    val seen = HashMap<String, Int>()
    return List(rowCount) { rowIndex ->
        val base = "grp_node_${groupName.length}:$groupName:${proxies[rowIndex * perRow].name}"
        val occurrence = seen.merge(base, 1, Int::plus) ?: 1
        if (occurrence == 1) base else "$base#$occurrence"
    }
}

/** Same idea for pre-chunked rows: first node name, suffixed on repeats so the lazy keys stay unique. */
internal fun proxyRowKeysOf(rows: List<List<Proxy>>): List<String> {
    val seen = HashMap<String, Int>()
    return rows.map { row ->
        val base = row.first().name
        val occurrence = seen.merge(base, 1, Int::plus) ?: 1
        if (occurrence == 1) base else "$base#$occurrence"
    }
}
