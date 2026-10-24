package com.github.mihomo.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mihomo.android.data.InstalledApp
import com.github.mihomo.android.data.PerAppProxyMode
import com.github.mihomo.android.ui.glass.FROSTED_PAGE_TITLE_FILL_ALPHA
import com.github.mihomo.android.ui.components.*
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.theme.*

private val iconBitmapCache = androidx.collection.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(300)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoutingScreen(
    currentMode: PerAppProxyMode,
    selectedPackages: Set<String>,
    apps: List<InstalledApp>,
    isLoading: Boolean,
    onBack: (() -> Unit)? = null,
    onModeChanged: (PerAppProxyMode) -> Unit,
    onTogglePackage: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    val lang = LocalAppLanguage.current
    val haptic = rememberIosHaptic()
    val modes = listOf(PerAppProxyMode.DISABLED, PerAppProxyMode.WHITELIST, PerAppProxyMode.BLACKLIST)
    val visibleApps = remember(apps, showSystemApps) {
        if (showSystemApps) apps else apps.filter { !it.isSystem }
    }
    val filtered = remember(visibleApps, searchQuery) {
        if (searchQuery.isBlank()) visibleApps
        else visibleApps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }
    val (checkedApps, unselectedApps) = remember(filtered, selectedPackages) {
        filtered.partition { selectedPackages.contains(it.packageName) }
    }

    // One transparent edge-to-edge list owns the controls and every app row. The title/back
    // row floats above it, so searching, filters and results all move as one continuous page.
    Box(Modifier.fillMaxSize().background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("app-routing-list").bounceOverscroll(),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "routing-mode") {
                CupertinoSegmentedControl(
                    items = modes,
                    selectedItem = currentMode,
                    onItemSelected = onModeChanged,
                    labelProvider = { mode ->
                        when (mode) {
                            PerAppProxyMode.DISABLED -> AppStrings.get("per_app_disabled", lang)
                            PerAppProxyMode.WHITELIST -> AppStrings.get("per_app_whitelist", lang)
                            PerAppProxyMode.BLACKLIST -> AppStrings.get("per_app_blacklist", lang)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            if (currentMode != PerAppProxyMode.DISABLED) {
                item(key = "routing-search") {
                    ShortcutSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = AppStrings.get("app_search_placeholder", lang),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                item(key = "routing-summary") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("已选中 ${selectedPackages.size} 个应用", color = LoonTextSecondary,
                            fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(AppStrings.get("app_show_system", lang), color = LoonTextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            CupertinoSwitch(checked = showSystemApps, onCheckedChange = { showSystemApps = it })
                        }
                    }
                }
                if (isLoading) {
                    item(key = "routing-loading") {
                        Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = IosBlue)
                        }
                    }
                } else {
                    item(key = "routing-batch-actions") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp), color = LoonTertiaryBg,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                    haptic.selection()
                                    unselectedApps.forEach { onTogglePackage(it.packageName) }
                                }
                            ) {
                                Text("全选当前 (${filtered.size})", fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold, color = IosBlue,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                            if (checkedApps.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp), color = LoonTertiaryBg,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                        haptic.selection()
                                        checkedApps.forEach { onTogglePackage(it.packageName) }
                                    }
                                ) {
                                    Text("全部取消", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                                        color = IosRed, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                }
                            }
                        }
                    }
                    if (checkedApps.isNotEmpty()) {
                        item(key = "hdr_checked") {
                            Text(
                                text = if (currentMode == PerAppProxyMode.WHITELIST) "已启用代理的应用 (${checkedApps.size})"
                                else "直接连接/绕过的应用 (${checkedApps.size})",
                                color = IosBlue, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp, start = 20.dp)
                            )
                        }
                        items(checkedApps, key = { "chk_${it.packageName}" }) { app ->
                            Box(Modifier.padding(horizontal = 16.dp)) {
                                AppItemRow(app, true) { haptic.selection(); onTogglePackage(app.packageName) }
                            }
                        }
                        item(key = "sp_checked") { Spacer(Modifier.height(4.dp)) }
                    }
                    if (unselectedApps.isNotEmpty()) {
                        if (checkedApps.isNotEmpty()) {
                            item(key = "hdr_other") {
                                Text("其他未选应用 (${unselectedApps.size})", color = LoonTextSecondary,
                                    fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp, start = 20.dp))
                            }
                        }
                        items(unselectedApps, key = { "other_${it.packageName}" }) { app ->
                            Box(Modifier.padding(horizontal = 16.dp)) {
                                AppItemRow(app, false) { haptic.selection(); onTogglePackage(app.packageName) }
                            }
                        }
                    } else if (checkedApps.isEmpty()) {
                        item(key = "routing-no-match") {
                            Box(Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(horizontal = 32.dp),
                                contentAlignment = Alignment.Center) {
                                Text(AppStrings.get("app_no_match", lang), color = LoonTextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                item(key = "routing-disabled-empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp).padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(LoonTertiaryBg),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Android, contentDescription = null, tint = IosOrange, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("未启用分应用代理", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = LoonTextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text("请在上方选择「白名单模式」（仅代理选中应用）或「黑名单模式」（仅绕过选中应用）以开启针对特定 App 的网络分流控制",
                            color = LoonTextSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().statusBarsPadding().zIndex(1f)) {
            CupertinoNavBar(
                title = AppStrings.get("app_routing_title", lang),
                showBackButton = onBack != null,
                onBack = onBack,
                frosted = true,
                titleFrostedFillAlpha = FROSTED_PAGE_TITLE_FILL_ALPHA
            )
        }
    }
}

@Composable
private fun AppItemRow(
    app: InstalledApp,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    val cachedBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = iconBitmapCache.get(app.packageName),
        key1 = app.packageName,
        key2 = app.icon
    ) {
        if (value == null) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                iconBitmapCache.get(app.packageName) ?: app.icon?.let { d ->
                    val w = d.intrinsicWidth.coerceAtLeast(48)
                    val h = d.intrinsicHeight.coerceAtLeast(48)
                    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    d.setBounds(0, 0, canvas.width, canvas.height)
                    d.draw(canvas)
                    bmp.asImageBitmap().also { iconBitmapCache.put(app.packageName, it) }
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isChecked) 1.dp else 0.5.dp,
                color = if (isChecked) IosBlue else LoonCardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .iosPressable { onToggle() },
        color = LoonCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // App Icon
                val iconBmp = cachedBitmap
                if (iconBmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = iconBmp,
                        contentDescription = null,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(9.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(LoonTertiaryBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = LoonTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        color = if (isChecked) IosBlue else LoonTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = app.packageName,
                        color = LoonTextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // iOS Checkbox
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isChecked) IosBlue else Color.Transparent)
                    .border(
                        width = if (isChecked) 0.dp else 1.5.dp,
                        color = if (isChecked) Color.Transparent else LoonTextMuted,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isChecked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
