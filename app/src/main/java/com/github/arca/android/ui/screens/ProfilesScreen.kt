package com.github.arca.android.ui.screens

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.github.arca.android.data.ConfigManager
import com.github.arca.android.data.ProfileItem
import com.github.arca.android.data.subscription.SourceDraft
import com.github.arca.android.data.subscription.SubscriptionManager
import com.github.arca.android.data.subscription.SubscriptionAggregator
import com.github.arca.android.data.subscription.readBounded
import kotlinx.coroutines.CancellationException
import com.github.arca.android.ui.components.*
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.GlassModalBottomSheet
import com.github.arca.android.ui.glass.GlassSurface
import com.github.arca.android.ui.glass.recordArcaGlasmioBackdropSource
import com.github.arca.android.ui.glass.rememberArcaGlasmioBackdrop
import androidx.compose.ui.graphics.Shape
import com.github.arca.android.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 0) return "刚刚"
    val seconds = diff / 1000
    if (seconds < 60) return "刚刚"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes} 分钟前"
    val hours = minutes / 60
    if (hours < 24) return "${hours} 小时前"
    val days = hours / 24
    if (days < 30) return "${days} 天前"
    val months = days / 30
    if (months < 12) return "${months} 个月前"
    val years = months / 12
    return "${years} 年前"
}

private fun formatTraffic(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    val tb = gb / 1024.0
    return when {
        tb >= 1.0 -> String.format(Locale.US, "%.2f TB", tb)
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UnusedParameter")
@Composable
fun ProfilesScreen(
    profiles: List<ProfileItem>,
    selectedProfileId: String?,
    onBack: (() -> Unit)? = null,
    onSelectProfile: (ProfileItem) -> Unit,
    onUpdateProfile: (ProfileItem) -> Unit,
    onDeleteProfile: (ProfileItem) -> Unit,
    onRefreshSubscription: suspend (ProfileItem) -> Unit = { onUpdateProfile(it) },
    onReorderProfiles: ((List<ProfileItem>) -> Unit)? = null,
    onNavigateToOverride: ((ProfileItem) -> Unit)? = null,
    /** Pull-to-refresh: updates every remote subscription; suspends until all finish. */
    onRefreshAllSubscriptions: (suspend () -> Unit)? = null,
    embeddedPanel: Boolean = false,
    onProfileImported: (ProfileItem) -> Unit = onUpdateProfile
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberIosHaptic()
    val lang = LocalAppLanguage.current

    // Sheets & Dialog States
    var showAddSheet by remember { mutableStateOf(false) }
    var actionSheetProfile by remember { mutableStateOf<ProfileItem?>(null) }
    var editSheetProfile by remember { mutableStateOf<ProfileItem?>(null) }
    var yamlEditorProfile by remember { mutableStateOf<ProfileItem?>(null) }
    var deleteConfirmProfile by remember { mutableStateOf<ProfileItem?>(null) }
    var refreshingProfileId by remember { mutableStateOf<String?>(null) }
    var localProfiles by remember(profiles) { mutableStateOf(profiles) }
    var profileDrag by remember { mutableStateOf<ProfileDragPosition?>(null) }
    // Slot top captured at pickup. translationY is drag.top minus THIS fixed
    // anchor, i.e. pure finger travel, so a reorder mid-drag cannot move the
    // card under the finger.
    var dragStartTop by remember { mutableStateOf<Float?>(null) }
    var retainedDragItem by remember { mutableStateOf<ProfileReorderItem?>(null) }
    val dragContainers = remember { mutableMapOf<String, PinnableContainer>() }
    var dragPin by remember { mutableStateOf<PinnableContainer.PinnedHandle?>(null) }
    DisposableEffect(Unit) { onDispose { dragPin?.release() } }
    val draggedProfileId by remember { derivedStateOf { profileDrag?.id } }
    val currentOnReorderProfiles by rememberUpdatedState(onReorderProfiles)
    val lazyListState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    val pageBackdrop = rememberArcaGlasmioBackdrop()
    var pullRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(profiles) {
        // Pointer events and remeasurement need not alternate. Keep the latest finger position
        // while waiting for a layout matching our order, including a release in that interval.
        snapshotFlow {
            profileDrag?.let { drag ->
                drag to profileReorderResult(
                    localProfiles.map { it.id },
                    lazyListState.layoutInfo.visibleItemsInfo.map {
                        ProfileReorderItem(it.key as String, it.index, it.offset, it.size)
                    },
                    drag,
                    retainedDragItem
                )
            }
        }.collect { frame ->
            val (drag, result) = frame ?: return@collect
            // The finger is already up: commit whatever order we have. Waiting
            // for a Settled frame is not safe here, because snapshotFlow only
            // re-emits when profileDrag changes, and a release that lands on
            // Move/AwaitLayout never produces another emission. The order then
            // stays unpersisted until profiles is re-emitted, and
            // remember(profiles) resets localProfiles to the old order: the
            // drag visibly snaps back.
            fun commitReleasedOrder(order: List<ProfileItem>) {
                profileDrag = null
                dragStartTop = null
                retainedDragItem = null
                dragPin?.release()
                dragPin = null
                currentOnReorderProfiles?.invoke(order)
            }
            when (result) {
                ProfileReorderResult.AwaitLayout -> {
                    if (drag.released) commitReleasedOrder(localProfiles)
                }
                ProfileReorderResult.Cancelled -> {
                    profileDrag = null
                    dragStartTop = null
                    retainedDragItem = null
                    dragPin?.release()
                    dragPin = null
                }
                ProfileReorderResult.Settled -> if (drag.released) {
                    commitReleasedOrder(localProfiles)
                }
                is ProfileReorderResult.Move -> {
                    val firstIndex = lazyListState.firstVisibleItemIndex
                    if (result.affectsFirstVisibleItem(firstIndex)) {
                        // Override keyed first-item retention in the SAME remeasure as the move.
                        lazyListState.requestScrollToItem(firstIndex, lazyListState.firstVisibleItemScrollOffset)
                    }
                    val reordered = localProfiles.toMutableList().apply {
                        add(result.to, removeAt(result.from))
                    }
                    localProfiles = reordered
                    if (drag.released) commitReleasedOrder(reordered)
                }
            }
        }
    }

    // File Picker for replacing profile
    var targetReplacingProfile by remember { mutableStateOf<ProfileItem?>(null) }
    var replacement by remember { mutableStateOf<Pair<ProfileItem, SourceDraft>?>(null) }
    var sourcesProfile by remember { mutableStateOf<ProfileItem?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val target = targetReplacingProfile
        if (uri != null && target != null) scope.launch {
            try {
                val draft = withContext(Dispatchers.IO) { ConfigManager.readProfileDraft(context, uri) }
                replacement = target to draft
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { AppNotice.error("文件读取失败，请检查大小和 UTF-8 编码") }
        }
        targetReplacingProfile = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)
            .then(if (embeddedPanel) Modifier else Modifier.statusBarsPadding())
            .testTag("profiles-screen-root")
    ) {
        // Cupertino Navigation Bar
        CupertinoNavBar(
            title = AppStrings.get("profiles_title", lang),
            showBackButton = onBack != null,
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = {
                        haptic.selection()
                        showAddSheet = true
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = AppStrings.get("profiles_add", lang),
                        tint = IosBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            frosted = true
        )

        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = if (embeddedPanel) 24.dp else 0.dp)
                    .bounceOverscroll(),
                contentAlignment = Alignment.Center
            ) {
                ProfilesPanelSurface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(LoonTertiaryBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = IosBlue,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "暂无配置文件",
                            color = LoonTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "点击右上角 + 按钮添加远程订阅链接或导入本地 YAML 配置文件",
                            color = LoonTextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .border(0.5.dp, LoonCardBorder, RoundedCornerShape(12.dp))
                                .iosPressable { showAddSheet = true },
                            color = LoonCard
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = IosBlue, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("添加首个配置", color = IosBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = pullRefreshing,
                onRefresh = {
                    val refreshAll = onRefreshAllSubscriptions
                    if (refreshAll != null && !pullRefreshing) {
                        scope.launch {
                            pullRefreshing = true
                            try { refreshAll() } finally { pullRefreshing = false }
                        }
                    }
                },
                state = pullState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .recordArcaGlasmioBackdropSource(pageBackdrop)
                    .testTag("profiles-list-viewport"),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = pullRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = LoonCard,
                        color = IosBlue
                    )
                }
            ) {
            LazyColumn(
                state = lazyListState,
                // The viewport detector is outside LazyColumn's internal scrollable node.
                // Only an accepted card pickup may lock that competing drag recognizer.
                userScrollEnabled = profileDrag == null,
                modifier = Modifier
                    .fillMaxSize()
                    .bounceOverscroll(allowTop = false)
                    .padding(horizontal = 16.dp)
                    .testTag("profiles-list")
                    // Observe unscaled viewport coordinates, outside the moving card's layer.
                    .pointerInput(profiles, localProfiles.size) {
                        fun cancelProfileDrag() {
                            profileDrag = null
                            dragStartTop = null
                            retainedDragItem = null
                            dragPin?.release()
                            dragPin = null
                            localProfiles = profiles
                        }
                        fun dragAt(pointerY: Float): ProfileDragPosition? {
                            val layout = lazyListState.layoutInfo
                            return profileDragStart(layout.visibleItemsInfo.map {
                                ProfileReorderItem(it.key as String, it.index, it.offset, it.size)
                            }, layout.viewportStartOffset, pointerY)
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // Reject non-card starts BEFORE recognizing a long press. Merely
                            // returning null from onDragStart cannot veto the stock detector's
                            // automatic consumption of movement/up in gaps or content padding.
                            if (dragAt(down.position.y) == null) return@awaitEachGesture
                            val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                            val start = dragAt(longPress.position.y) ?: return@awaitEachGesture
                            dragPin?.release()
                            dragPin = dragContainers[start.id]?.pin()
                            retainedDragItem = lazyListState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == start.id }?.let {
                                    ProfileReorderItem(start.id, it.index, it.offset, it.size)
                                }
                            profileDrag = start
                            dragStartTop = start.top
                            try {
                                // Own accepted drags before the inner scrollable's Main pass,
                                // even when the scroll lock has not recomposed yet.
                                var pointerId = longPress.id
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                    if (change == null || change.isConsumed) {
                                        cancelProfileDrag()
                                        break
                                    }
                                    if (change.changedToUp()) {
                                        change.consume()
                                        val next = event.changes.firstOrNull { it.pressed }
                                        if (next == null) {
                                            profileDrag = profileDrag?.copy(released = true)
                                            break
                                        }
                                        pointerId = next.id
                                    } else {
                                        val deltaY = change.positionChange().y
                                        profileDrag?.let { profileDrag = it.copy(top = it.top + deltaY) }
                                        change.consume()
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                cancelProfileDrag()
                                throw cancelled
                            }
                        }
                    },
                contentPadding = PaddingValues(top = 10.dp, bottom = if (embeddedPanel) 24.dp else 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(localProfiles, key = { _, item -> item.id }) { index, item ->
                    val isBeingDragged = draggedProfileId == item.id
                    val isSelected = item.id == selectedProfileId
                    val isRefreshing = refreshingProfileId == item.id
                    val cardShape = RoundedCornerShape(18.dp)
                    val pinnableContainer = LocalPinnableContainer.current
                    DisposableEffect(item.id, pinnableContainer) {
                        if (pinnableContainer != null) dragContainers[item.id] = pinnableContainer
                        onDispose {
                            if (dragContainers[item.id] === pinnableContainer) dragContainers.remove(item.id)
                        }
                    }

                    ProfilesPanelSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isBeingDragged) 10f else 1f)
                            .animateItem(
                                placementSpec = if (isBeingDragged) null else spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioNoBouncy
                                )
                            )
                            .onPlaced { coordinates ->
                                if (profileDrag?.id == item.id) {
                                    // This modifier precedes the moving layer, so placement cannot
                                    // feed translation back into its own offset. Include pinned slots
                                    // which LazyColumn omits from visibleItemsInfo after normalization.
                                    retainedDragItem = ProfileReorderItem(
                                        item.id, index,
                                        coordinates.positionInParent().y.toInt() - lazyListState.layoutInfo.beforeContentPadding,
                                        coordinates.size.height
                                    )
                                }
                            }
                            .graphicsLayer {
                                // Match the lift shadow to the panel outline; the default layer
                                // shape is rectangular and leaves a square gray shadow under the card.
                                shape = cardShape
                                clip = false
                                val drag = profileDrag
                                val anchorTop = dragStartTop
                                translationY = if (drag?.id == item.id && anchorTop != null) drag.top - anchorTop else 0f
                                scaleX = 1f
                                scaleY = 1f
                                shadowElevation = 0f
                            }
                            .then(if (isSelected) Modifier.border(1.dp, IosBlue.copy(alpha = 0.16f), cardShape) else Modifier)
                            .testTag("profile-card-${item.id}"),
                        shape = cardShape,
                        quietPanelElevation = profileCardQuietElevation(isSelected)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .iosPressable(pressedScale = 1f) {
                                    haptic.selection()
                                    onSelectProfile(item)
                                }
                                .padding(horizontal = 15.dp, vertical = 14.dp)
                        ) {
                            // Top Row: Active Radio, Title, Tag & Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Checkmark / Select Circle
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) LoonTextPrimary else Color.Transparent)
                                        .border(
                                            width = if (isSelected) 0.dp else 1.5.dp,
                                            color = if (isSelected) Color.Transparent else LoonTextMuted,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = LoonTextPrimary,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (item.canRefresh) "订阅" else if (item.managed) "托管" else "本地",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = LoonTextSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "更新于 ${formatRelativeTime(item.lastUpdated)}" +
                                                if (item.autoUpdate) " · 自动更新开启" else "",
                                        fontSize = 11.5.sp,
                                        color = LoonTextSecondary
                                    )
                                }

                                // Quick Refresh Button (for remote subscriptions)
                                if (item.canRefresh) {
                                    val rotation by rememberInfiniteTransition().animateFloat(
                                        initialValue = 0f,
                                        targetValue = 360f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1000, easing = LinearEasing),
                                            repeatMode = RepeatMode.Restart
                                        )
                                    )
                                    IconButton(
                                        onClick = {
                                            if (refreshingProfileId == null) {
                                                haptic.impact()
                                                refreshingProfileId = item.id
                                                scope.launch {
                                                    try { onRefreshSubscription(item) }
                                                    finally { refreshingProfileId = null }
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "刷新",
                                            tint = if (isRefreshing) IosBlue else LoonTextSecondary,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .rotate(if (isRefreshing) rotation else 0f)
                                        )
                                    }
                                }

                                // More Actions Button
                                IconButton(
                                    onClick = {
                                        haptic.selection()
                                        actionSheetProfile = item
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreHoriz,
                                        contentDescription = "更多",
                                        tint = LoonTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Subscription Traffic & Expiration Pill (If available)
                            if (item.totalBytes > 0) {
                                Spacer(modifier = Modifier.height(14.dp))
                                val usedBytes = item.uploadBytes + item.downloadBytes
                                val fraction = (usedBytes.toDouble() / item.totalBytes).coerceIn(0.0, 1.0).toFloat()
                                val progressColor = when {
                                    fraction >= 0.9f -> IosRed
                                    fraction >= 0.75f -> IosOrange
                                    else -> IosBlue
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(LoonTertiaryBg)
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${formatTraffic(usedBytes)} / ${formatTraffic(item.totalBytes)}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = LoonTextPrimary
                                        )
                                        if (item.expireTimestamp > 0) {
                                            val expireDateStr = remember(item.expireTimestamp) {
                                                val sdf = SimpleDateFormat("yyyy-MM-dd 到期", Locale.getDefault())
                                                sdf.format(Date(item.expireTimestamp * 1000))
                                            }
                                            Text(
                                                text = expireDateStr,
                                                fontSize = 11.sp,
                                                color = LoonTextSecondary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    // Smooth Progress Track
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(LoonCardBorder)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(fraction)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(progressColor)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } // pull-to-refresh
        }
    }

    // =========================================================================
    // 1. Add Subscription Sheet (Cupertino Style)
    // =========================================================================
    if (showAddSheet) {
        SubscriptionImportSheet(onDismiss = { showAddSheet = false }, onImported = onProfileImported)
    }

    // =========================================================================
    // 2. Action Sheet (Profile Operations)
    // =========================================================================
    if (actionSheetProfile != null) {
        val target = actionSheetProfile!!
        val isSelected = target.id == selectedProfileId

        GlassModalBottomSheet(
            onDismissRequest = { actionSheetProfile = null },
            containerColor = LoonBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = target.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LoonTextPrimary,
                    modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 14.dp)
                )

                CupertinoGroupedSection {
                    if (!isSelected) {
                        CupertinoCell(
                            title = "设为活跃配置",
                            icon = Icons.Default.CheckCircle,
                            iconBg = IosBlue,
                            onClick = {
                                actionSheetProfile = null
                                onSelectProfile(target)
                            }
                        )
                        CupertinoDivider()
                    }

                    if (target.managed) {
                        CupertinoCell(title = "来源与版本", icon = Icons.Default.Layers, iconBg = IosBlue,
                            onClick = { actionSheetProfile = null; sourcesProfile = target })
                        CupertinoDivider()
                    }
                    if (target.canRefresh) {
                        CupertinoCell(
                            title = "立即更新订阅",
                            icon = Icons.Default.CloudSync,
                            iconBg = IosGreen,
                            onClick = {
                                actionSheetProfile = null
                                scope.launch { onRefreshSubscription(target) }
                            }
                        )
                        CupertinoDivider()
                    }

                    CupertinoCell(
                        title = if (target.id == ConfigManager.DEFAULT_PROFILE_ID) "重命名" else "编辑配置参数",
                        icon = Icons.Default.Tune,
                        iconBg = AppPalette.iconOrange,
                        onClick = {
                            actionSheetProfile = null
                            editSheetProfile = target
                        }
                    )
                    CupertinoDivider()

                    CupertinoCell(
                        title = "在线查看 / 编辑 YAML",
                        icon = Icons.Default.Code,
                        iconBg = AppPalette.iconIndigo,
                        onClick = {
                            actionSheetProfile = null
                            yamlEditorProfile = target
                        }
                    )
                    CupertinoDivider()

                    CupertinoCell(
                        title = "JS 脚本覆写",
                        icon = Icons.Default.Javascript,
                        iconBg = AppPalette.iconPurple,
                        onClick = {
                            actionSheetProfile = null
                            onNavigateToOverride?.invoke(target)
                        }
                    )
                    CupertinoDivider()

                    CupertinoCell(
                        title = "替换为本地文件",
                        icon = Icons.Default.FileUpload,
                        iconBg = AppPalette.iconSky,
                        onClick = {
                            actionSheetProfile = null
                            targetReplacingProfile = target
                            filePickerLauncher.launch("*/*")
                        }
                    )
                    if (target.id != ConfigManager.DEFAULT_PROFILE_ID) {
                        CupertinoDivider()
                        CupertinoCell(
                            title = "删除配置",
                            icon = Icons.Default.DeleteOutline,
                            iconBg = IosRed,
                            onClick = {
                                actionSheetProfile = null
                                deleteConfirmProfile = target
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(0.5.dp, LoonCardBorder, RoundedCornerShape(14.dp))
                        .iosPressable { actionSheetProfile = null },
                    color = LoonCard
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "取消", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = IosBlue)
                    }
                }
            }
        }
    }

    sourcesProfile?.let { target ->
        SubscriptionSourcesSheet(target, onDismiss = { sourcesProfile = null }, onChanged = onUpdateProfile)
    }
    replacement?.let { (target, draft) ->
        SubscriptionImportSheet(
            onDismiss = { replacement = null }, initial = draft,
            onImported = { updated -> onUpdateProfile(updated); AppNotice.success("配置已保存，连接时使用新版本") },
            onCommit = { _, preview, standalone, _ ->
                val content = if (standalone) preview.sources.single().inspection.complete
                    else SubscriptionAggregator.generate(preview.sources).yaml
                SubscriptionManager.replace(context, target, content)
            }
        )
    }

    // =========================================================================
    // 3. Edit Profile Sheet
    // =========================================================================
    if (editSheetProfile != null) {
        val target = editSheetProfile!!
        var editName by remember(target) { mutableStateOf(target.name) }
        var editUrl by remember(target) { mutableStateOf(target.url ?: "") }
        var editAutoUpdate by remember(target) { mutableStateOf(target.autoUpdate) }
        var intervalHoursText by remember(target) {
            mutableStateOf(((target.autoUpdateInterval / 60).coerceAtLeast(1)).toString())
        }

        GlassModalBottomSheet(
            onDismissRequest = { editSheetProfile = null },
            containerColor = LoonBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = if (target.id == ConfigManager.DEFAULT_PROFILE_ID) "重命名默认配置" else "配置参数设置",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = LoonTextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                CupertinoGroupedSection {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (target.id == ConfigManager.DEFAULT_PROFILE_ID) {
                            Text("独立节点仍保存在此配置中。", fontSize = 12.sp, color = LoonTextSecondary)
                        }
                        Text("配置备注名称", fontSize = 12.sp, color = LoonTextSecondary)
                        TextField(
                            value = editName,
                            onValueChange = { editName = it },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = LoonBg,
                                unfocusedContainerColor = LoonBg,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = LoonTextPrimary,
                                unfocusedTextColor = LoonTextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (target.url != null) {
                            Text("订阅链接 URL", fontSize = 12.sp, color = LoonTextSecondary)
                            TextField(
                                value = editUrl,
                                onValueChange = { editUrl = it },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = LoonBg,
                                    unfocusedContainerColor = LoonBg,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = LoonTextPrimary,
                                    unfocusedTextColor = LoonTextPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (target.url != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    CupertinoGroupedSection {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("定时自动更新", fontSize = 15.sp, color = LoonTextPrimary)
                                Text("后台静默拉取最新节点与规则", fontSize = 11.5.sp, color = LoonTextSecondary)
                            }
                            ArcaGlasmioSwitch(
                                checked = editAutoUpdate,
                                onCheckedChange = { editAutoUpdate = it }
                            )
                        }

                        if (editAutoUpdate) {
                            CupertinoDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("更新间隔 (小时)", fontSize = 14.5.sp, color = LoonTextPrimary)
                                TextField(
                                    value = intervalHoursText,
                                    onValueChange = { if (it.all { ch -> ch.isDigit() }) intervalHoursText = it },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = LoonBg,
                                        unfocusedContainerColor = LoonBg,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = LoonTextPrimary,
                                        unfocusedTextColor = LoonTextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    modifier = Modifier.width(80.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(IosBlue)
                        .clickable {
                            val intervalHours = (intervalHoursText.toIntOrNull() ?: 24).coerceAtLeast(1)
                            val updated = target.copy(
                                name = editName.trim().ifBlank { target.name },
                                url = if (target.url != null) editUrl.ifBlank { target.url } else null,
                                autoUpdate = editAutoUpdate,
                                autoUpdateInterval = intervalHours * 60
                            )
                            ConfigManager.updateProfile(context, updated)
                            if (target.id == selectedProfileId) {
                                onSelectProfile(updated)
                            }
                            onUpdateProfile(updated)
                            editSheetProfile = null
                            AppNotice.show("配置修改已保存")
                        },
                    color = IosBlue
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("保存设置", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // =========================================================================
    // 4. Online YAML Editor Sheet
    // =========================================================================
    if (yamlEditorProfile != null) {
        val target = yamlEditorProfile!!
        var yamlText by remember(target) { mutableStateOf("") }

        LaunchedEffect(target) {
            yamlText = withContext(Dispatchers.IO) {
                runCatching { target.file.inputStream().use { readBounded(it) } }.getOrDefault("")
            }
        }

        GlassModalBottomSheet(
            onDismissRequest = { yamlEditorProfile = null },
            containerColor = LoonBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            modifier = Modifier.fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("编辑 YAML 配置", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LoonTextPrimary)
                        Text(target.name, fontSize = 12.sp, color = LoonTextSecondary)
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(IosBlue)
                            .clickable {
                                replacement = target to SourceDraft(target.name, yamlText, remote = false)
                                yamlEditorProfile = null
                            },
                        color = IosBlue
                    ) {
                        Text(
                            text = "检查并保存",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                TextField(
                    value = yamlText,
                    onValueChange = { yamlText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = LoonTextPrimary,
                        lineHeight = 17.sp
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = LoonCard,
                        unfocusedContainerColor = LoonCard,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

    // =========================================================================
    // 5. Delete Confirmation Sheet
    // =========================================================================
    if (deleteConfirmProfile != null) {
        val target = deleteConfirmProfile!!

        GlassModalBottomSheet(
            onDismissRequest = { deleteConfirmProfile = null },
            containerColor = LoonBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "删除配置文件",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LoonTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "确定要删除配置 [${target.name}] 吗？此操作将彻底移除该配置文件及其缓存。",
                    fontSize = 13.sp,
                    color = LoonTextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(IosRed)
                        .clickable {
                            haptic.impact()
                            onDeleteProfile(target)
                            deleteConfirmProfile = null
                        },
                    color = IosRed
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("确认删除", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(0.5.dp, LoonCardBorder, RoundedCornerShape(12.dp))
                        .iosPressable { deleteConfirmProfile = null },
                    color = LoonCard
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("取消", color = LoonTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilesPanelSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    fallbackColor: Color = LoonCard,
    quietPanelElevation: Dp = 2.dp,
    content: @Composable () -> Unit
) {
    if (GlassDefaults.enabled) {
        GlassSurface(modifier = modifier, shape = shape, quietPanelElevation = quietPanelElevation) { content() }
    } else {
        Surface(modifier = modifier, shape = shape, color = fallbackColor) { content() }
    }
}
