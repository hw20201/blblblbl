package com.github.mihomo.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import com.github.mihomo.android.ui.glass.FROSTED_PAGE_TITLE_FILL_ALPHA
import com.github.mihomo.android.ui.glass.FROSTED_SELECTED_GLASS_TINT
import com.github.mihomo.android.ui.glass.PageTitleGlass
import com.github.mihomo.android.ui.glass.rememberNavigationLens
import com.github.mihomo.android.ui.glass.recordNavigationScene
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.mihomo.android.ui.components.*
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.glass.GlassSurface
import com.github.mihomo.android.ui.glass.LocalGlassTokens
import com.github.mihomo.android.ui.glass.NavigationLensState
import com.github.mihomo.android.ui.glass.ProxyGroupGlass
import com.github.mihomo.android.ui.glass.useProxyGroupGlass
import com.github.mihomo.android.ui.glass.proxyCardNeedsOuterBorder
import com.github.mihomo.android.ui.theme.*

@Composable
fun AnimatedSpeedTestIcon(
    isTesting: Boolean,
    tint: Color = LoonTextPrimary,
    modifier: Modifier = Modifier
) {
    val alpha by if (isTesting) {
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
        tint = tint.copy(alpha = alpha),
        modifier = modifier
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
    glassScene: NavigationLensState? = null
) {
    var showOptionsMenu by remember { mutableStateOf(false) }
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
    val titleScene = rememberNavigationLens()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerInset = with(LocalDensity.current) { headerHeightPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .then(if (GlassDefaults.enabled) Modifier.recordNavigationScene(titleScene, glassScene) else Modifier)
        ) {
        // Settings BottomSheet (Smooth, modern, non-cluttered bottom sheet)
        if (showOptionsMenu) {
            ModalBottomSheet(
                onDismissRequest = { showOptionsMenu = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = LoonCard,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(LoonTextMuted.copy(alpha = 0.3f))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "策略设置",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = LoonTextPrimary
                    )

                    // 1. 显示模式 (标签 / 列表)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "显示模式",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = LoonTextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val modes = listOf(
                                "TABS" to AppStrings.get("proxies_view_tabs", lang),
                                "LIST" to AppStrings.get("proxies_view_list", lang)
                            )
                            modes.forEach { (mode, label) ->
                                val isSel = currentViewMode == mode
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            0.75.dp,
                                            LoonCardBorder.copy(alpha = if (isSel) .95f else .65f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onViewModeChanged(mode) },
                                    color = if (isSel) LoonTextPrimary.copy(alpha = if (LocalLoonColors.current.isDark) .22f else .09f) else LoonCard.copy(alpha = .72f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 11.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 13.5.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = LoonTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. 节点排版 (单列 / 双列 / 三列)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "节点排版",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = LoonTextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val cols = listOf(1 to "单列", 2 to "双列", 3 to "三列")
                            cols.forEach { (colCount, label) ->
                                val isSel = nodeColumns == colCount
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            0.75.dp,
                                            LoonCardBorder.copy(alpha = if (isSel) .95f else .65f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onNodeColumnsChanged(colCount) },
                                    color = if (isSel) LoonTextPrimary.copy(alpha = if (LocalLoonColors.current.isDark) .22f else .09f) else LoonCard.copy(alpha = .72f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = LoonTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. 策略组远程图标开关
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "显示策略组远程图标",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = LoonTextPrimary
                        )
                        CupertinoSwitch(
                            checked = showGroupIcons,
                            onCheckedChange = { onShowGroupIconsChanged(it) }
                        )
                    }
                }
            }
        }

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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    LazyColumn(
                        state = activeTabState,
                        modifier = Modifier.testTag("policy-node-list")
                            .fillMaxSize()
                            .bounceOverscroll(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = headerInset + 8.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = chunkedRows,
                            key = { row -> row.first().name }
                        ) { rowProxies ->
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
                                            onTestSingleNode = { onTestSingleNode(selectedGroupName, proxy.name) }
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
                val listModeRows = remember(visibleGroups, groupMap, nodeColumns) {
                    visibleGroups.associateWith { groupName ->
                        (groupMap[groupName]?.proxies ?: emptyList()).chunked(nodeColumns.coerceIn(1, 3))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    LazyColumn(
                        state = listModeState,
                        modifier = Modifier
                            .testTag("policy-list-mode")
                            .fillMaxSize()
                            .bounceOverscroll(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = headerInset + 8.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        visibleGroups.forEach { groupName ->
                            val group = groupMap[groupName]
                            val isExpanded = expandedGroups[groupName] ?: false
                            val proxies = group?.proxies ?: emptyList()
                            val iconUrl = groupIcons[groupName]

                            item(key = "grp_item_$groupName", contentType = "group_container") {
                                val isGroupTesting = testingNodes.contains(groupName) || proxies.any { testingNodes.contains("$groupName:${it.name}") || (it.isGroup && testingNodes.contains(it.name)) }
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

                            if (isExpanded) {
                                val groupNow = group?.now
                                items(
                                    items = listModeRows[groupName].orEmpty(),
                                    key = { row -> "proxy_row_${groupName}_${row.first().name}" },
                                    contentType = { "proxy_row" }
                                ) { rowProxies ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (proxy in rowProxies) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                val isItemTesting = testingNodes.contains(proxy.name) || testingNodes.contains("$groupName:${proxy.name}")
                                                LoonProxyItemCard(
                                                    proxy = proxy,
                                                    glassScene = glassScene,
                                                    isSelected = proxy.name == groupNow,
                                                    columns = nodeColumns,
                                                    isTesting = isItemTesting,
                                                    onClick = { onSelectProxy(groupName, proxy) },
                                                    onTestSingleNode = { onTestSingleNode(groupName, proxy.name) }
                                                )
                                            }
                                        }
                                        val remainder = nodeColumns - rowProxies.size
                                        if (remainder > 0) {
                                            repeat(remainder) { Spacer(modifier = Modifier.weight(1f)) }
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

        // No screen-width scrim: the small title capsule samples the live scrolling list.
        Column(Modifier.onSizeChanged { headerHeightPx = it.height }.fillMaxWidth().statusBarsPadding()
            .padding(horizontal = 20.dp).padding(top = 12.dp)
            ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                PageTitleGlass(titleScene, frostedFillAlpha = FROSTED_PAGE_TITLE_FILL_ALPHA) { Text(
                    text = AppStrings.get("proxies_title", lang),
                    color = LoonTextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                ) }
            }

            PageTitleGlass(titleScene, contentPadding = PaddingValues(horizontal = 2.dp),
                frostedFillAlpha = FROSTED_PAGE_TITLE_FILL_ALPHA) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Health check button (Pure Lightning Bolt with Animation, no text, no circle background)
                IconButton(
                    onClick = {
                        if (selectedGroupName.isNotBlank()) {
                            onHealthCheck(selectedGroupName)
                        } else if (visibleGroups.isNotEmpty()) {
                            onHealthCheck(visibleGroups.first())
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    AnimatedSpeedTestIcon(
                        isTesting = isAnyTesting,
                        tint = LoonTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 3-Dots Settings Dialog Trigger (•••)
                IconButton(
                    onClick = { showOptionsMenu = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多设置",
                        tint = LoonTextPrimary
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
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .iosPressable { onSelectGroup(groupName) }
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
                                        contentDescription = groupName
                                    )
                                }
                                Text(
                                    text = groupName,
                                    color = LoonTextPrimary,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
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
    ProxyGlassCard(
        modifier = Modifier
            .testTag("list-group-header-$groupName")
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, LoonCardBorder, RoundedCornerShape(20.dp))
            .clickable { onToggleExpand() },
        color = LoonCard,
        shape = RoundedCornerShape(20.dp),
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
                            contentDescription = groupName
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 1.dp)
                ) {
                    Text(
                        text = groupName,
                        color = LoonTextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
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
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
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

// Keep the selected node perceptibly pressed without changing its measured layout size.
internal fun selectedProxyCardScale(isSelected: Boolean): Float = if (isSelected) 0.97f else 1f

// Match the neutral gray used by the selected strategy capsule in the reference UI.
internal val SelectedProxyCardFill = Color(0xFFABB1BC)
internal val SelectedProxyCardText = Color(0xFF1A1D23)
private val SelectedProxyCardMutedText = Color(0xB31A1D23)

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
    onTestSingleNode: () -> Unit = {}
) {
    val nodeEmoji = extractLeadingEmoji(proxy.name)
    val displayName = if (nodeEmoji != null && proxy.name.startsWith(nodeEmoji)) {
        proxy.name.removePrefix(nodeEmoji).trim()
    } else {
        proxy.name
    }

    val isTimeout = proxy.delay == -2 || proxy.delay >= 65535
    val isValidDelay = proxy.delay in 1..65534
    val delayColor = when {
        isTimeout -> IosRed
        isValidDelay && proxy.delay <= 250 -> IosGreen
        isValidDelay && proxy.delay <= 600 -> IosOrange
        isValidDelay && proxy.delay > 600 -> IosRed
        else -> LoonTextMuted
    }
    val delayText = when {
        isTimeout -> "超时"
        isValidDelay -> "${proxy.delay} ms"
        else -> "--"
    }

    val cardBg = if (isSelected) SelectedProxyCardFill else LoonCard

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
                        val scale = selectedProxyCardScale(isSelected)
                        scaleX = scale
                        scaleY = scale
                    }
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(if (isSelected) Modifier else Modifier.border(.5.dp, LoonCardBorder, RoundedCornerShape(14.dp)))
                    .iosPressable { onClick() },
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

                        Column {
                            Text(
                                text = displayName,
                                color = if (isSelected) SelectedProxyCardText else LoonTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = proxy.type.uppercase(),
                                color = if (isSelected) SelectedProxyCardMutedText else LoonTextMuted,
                                fontSize = 9.5.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 50.dp, minHeight = 24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onTestSingleNode() },
                            contentAlignment = Alignment.Center
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
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isValidDelay || isTimeout) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }

                        if (isSelected) {
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
                        val scale = selectedProxyCardScale(isSelected)
                        scaleX = scale
                        scaleY = scale
                    }
                    .height(62.dp)
                    .clip(cardShape)
                    .then(
                        if (proxyCardNeedsOuterBorder(groupGlass, isSelected)) {
                            Modifier.border(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) IosBlue else LoonCardBorder,
                                shape = cardShape
                            )
                        } else Modifier
                    )
                    .iosPressable { onClick() },
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
                            color = if (isSelected) SelectedProxyCardText else LoonTextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = proxy.type.uppercase(),
                            color = if (isSelected) SelectedProxyCardMutedText else LoonTextMuted,
                            fontSize = 8.5.sp,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 44.dp, minHeight = 20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onTestSingleNode() },
                            contentAlignment = Alignment.Center
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
                        val scale = selectedProxyCardScale(isSelected)
                        scaleX = scale
                        scaleY = scale
                    }
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (isSelected) Modifier else Modifier.border(.5.dp, LoonCardBorder, RoundedCornerShape(12.dp)))
                    .iosPressable { onClick() },
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
                            color = if (isSelected) SelectedProxyCardText else LoonTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = proxy.type.uppercase(),
                            color = if (isSelected) SelectedProxyCardMutedText else LoonTextMuted,
                            fontSize = 8.sp,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 36.dp, minHeight = 18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onTestSingleNode() },
                            contentAlignment = Alignment.Center
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
}


/** Replays the recorded page backdrop for proxy tiles; selected tiles retain a neutral-gray frost. */
@Composable
private fun ProxyGlassCard(
    modifier: Modifier,
    color: Color,
    shape: RoundedCornerShape,
    accent: Color? = null,
    groupScene: NavigationLensState? = null,
    selected: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    if (GlassDefaults.enabled && groupScene != null) {
        ProxyGroupGlass(
            modifier = modifier,
            shape = shape,
            scene = groupScene,
            accent = accent,
            selectedFill = if (selected) SelectedProxyCardFill else null,
            content = content
        )
    } else if (GlassDefaults.enabled && selected) {
        val tokens = LocalGlassTokens.current
        val frost = Brush.linearGradient(listOf(
            SelectedProxyCardFill.copy(alpha = .92f), SelectedProxyCardFill.copy(alpha = .84f)
        ))
        Box(modifier.clip(shape).background(frost)
            .border(0.8.dp, tokens.edge.copy(alpha = .8f), shape)
            .testTag("proxy-selected-glass-material"), content = content)
    } else if (selected) {
        Surface(modifier = modifier, color = SelectedProxyCardFill, shape = shape) { Box(content = content) }
    } else if (GlassDefaults.enabled) {
        val tokens = LocalGlassTokens.current
        val tint = if (accent == null) tokens.tint else androidx.compose.ui.graphics.lerp(tokens.tintAccent, accent, 0.12f)
        val fill = Brush.linearGradient(
            listOf(tint.copy(alpha = tint.alpha * 0.42f), tokens.tintEnd.copy(alpha = tokens.tintEnd.alpha * 0.42f))
        )
        Box(
            modifier = modifier
                .clip(shape)
                .background(fill)
                .border(0.55.dp, tokens.edge.copy(alpha = tokens.edge.alpha * 0.72f), shape)
                .drawWithCache {
                    val sheen = Brush.verticalGradient(listOf(
                        Color.White.copy(alpha = if (tokens.isDark) .025f else .045f),
                        Color.Transparent
                    ))
                    // Cached paint only, behind content: glyphs are never filtered or washed out.
                    onDrawBehind { drawRect(sheen) }
                },
            content = content
        )
    } else {
        Surface(modifier = modifier, color = color, shape = shape) { Box(content = content) }
    }
}
