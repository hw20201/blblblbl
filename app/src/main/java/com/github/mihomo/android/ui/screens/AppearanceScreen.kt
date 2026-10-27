package com.github.mihomo.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mihomo.android.data.SettingsManager
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.components.*
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.glass.GlassWallpaperSettings
import com.github.mihomo.android.ui.glass.GlassWallpaperPicker
import com.github.mihomo.android.ui.glass.FROSTED_PAGE_TITLE_FILL_ALPHA
import com.github.mihomo.android.ui.glass.recordNavigationScene
import com.github.mihomo.android.ui.glass.rememberNavigationLens
import com.github.mihomo.android.ui.theme.*

@Composable
fun AppearanceScreen(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onLanguageChanged: (String) -> Unit,
    showGroupIcons: Boolean,
    onShowGroupIconsChanged: (Boolean) -> Unit,
    nodeColumns: Int,
    onNodeColumnsChanged: (Int) -> Unit,
    onBack: () -> Unit,
    wallpaperSettings: GlassWallpaperSettings = GlassDefaults.wallpaper,
    onWallpaperSettingsChanged: (GlassWallpaperSettings) -> Unit = { GlassDefaults.wallpaper = it },
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val lang = LocalAppLanguage.current
    var currentLanguage by remember { mutableStateOf(settingsManager.appLanguage) }

    val titleScene = rememberNavigationLens()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerInset = with(LocalDensity.current) { headerHeightPx.toDp() }

    val themes = listOf(
        ThemeMode.SYSTEM to AppStrings.get("theme_system", lang),
        ThemeMode.LIGHT to AppStrings.get("theme_light", lang),
        ThemeMode.DARK to AppStrings.get("theme_dark", lang)
    )
    val langs = listOf(
        "SYSTEM" to AppStrings.get("theme_system", lang),
        "zh" to "简体中文",
        "en" to "English",
        "ru" to "Русский"
    )

    // The old verticalScroll eagerly composed every section, including the wallpaper editor's
    // animated canvas and two 108-cell color grids. Keep the same one-surface scroll behavior, but
    // compose lower settings only when approached; this bounds entry work to the visible sections.
    Box(
        Modifier.fillMaxSize()
            .background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)
            .testTag("appearance-edge-to-edge-root")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .then(if (GlassDefaults.enabled) Modifier.recordNavigationScene(titleScene) else Modifier)
                .testTag("appearance-scroll-content")
                .bounceOverscroll(),
            state = listState,
            contentPadding = PaddingValues(
                top = headerInset + 24.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 132.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "appearance-theme-mode") {
                CupertinoGroupedSection(title = AppStrings.get("settings_theme", lang)) {
                    themes.forEachIndexed { index, (mode, label) ->
                        CupertinoCell(
                            title = label,
                            trailing = {
                                if (themeMode == mode) Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = IosBlue,
                                    modifier = Modifier.size(19.dp)
                                )
                            },
                            onClick = { onThemeModeChanged(mode) }
                        )
                        if (index < themes.lastIndex) CupertinoIndentedDivider(startIndent = 16.dp)
                    }
                }
            }

            if (GlassDefaults.enabled) item(key = "appearance-wallpaper") {
                CupertinoGroupedSection(
                    title = if (lang == "zh") "液态玻璃壁纸" else "Liquid-glass wallpaper",
                    footer = if (lang == "zh") "只保留直纹褶皱；三种主色柔和混合，色彩波动可关闭。" else "Straight fluted ribs only, blend three colors, and turn slow color flow off anytime."
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp).testTag("appearance-wallpaper-controls")) {
                        GlassWallpaperPicker(wallpaperSettings, onWallpaperSettingsChanged)
                    }
                }
            }

            item(key = "appearance-language") {
                CupertinoGroupedSection(title = AppStrings.get("settings_language", lang)) {
                    langs.forEachIndexed { index, (code, label) ->
                        CupertinoCell(
                            title = label,
                            trailing = {
                                if (currentLanguage == code) Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = IosBlue,
                                    modifier = Modifier.size(19.dp)
                                )
                            },
                            onClick = {
                                currentLanguage = code
                                settingsManager.appLanguage = code
                                onLanguageChanged(code)
                                Toast.makeText(
                                    context,
                                    "${AppStrings.get("language_updated", code)}: $label",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                        if (index < langs.lastIndex) CupertinoIndentedDivider(startIndent = 16.dp)
                    }
                }
            }

            item(key = "appearance-node-layout") {
                CupertinoGroupedSection(title = "策略与节点排版") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("默认每行节点数", fontSize = 15.5.sp, color = LoonTextPrimary)
                        Spacer(Modifier.height(10.dp))
                        CupertinoSegmentedControl(
                            items = listOf(1, 2, 3),
                            selectedItem = nodeColumns,
                            onItemSelected = onNodeColumnsChanged,
                            labelProvider = { col -> when (col) { 1 -> "单列"; 2 -> "双列"; else -> "三列" } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    CupertinoIndentedDivider(startIndent = 16.dp)
                    CupertinoCell(
                        title = "显示策略组远程图标",
                        subtitle = "在节点列表中展示代理协议或服务商的专用图标",
                        trailing = {
                            CupertinoSwitch(checked = showGroupIcons, onCheckedChange = onShowGroupIconsChanged)
                        }
                    )
                }
            }
        }

        Box(
            Modifier.fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .onSizeChanged { headerHeightPx = it.height }
                .zIndex(1f)
                .testTag("appearance-pinned-header")
        ) {
            CupertinoNavBar(
                title = AppStrings.get("settings_appearance", lang),
                showBackButton = true,
                onBack = onBack,
                frosted = true,
                titleFrostedFillAlpha = FROSTED_PAGE_TITLE_FILL_ALPHA,
                titleLens = titleScene
            )
        }
    }

 }
