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
import com.github.mihomo.android.data.SavedGlassPalette
import com.github.mihomo.android.data.SettingsManager
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.components.*
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.glass.GlassPalettePicker
import com.github.mihomo.android.ui.glass.GlassPaletteSpec
import com.github.mihomo.android.ui.glass.GlassPalettes
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
    palette: GlassPaletteSpec = GlassDefaults.palette,
    onPaletteChanged: (GlassPaletteSpec) -> Unit = { GlassDefaults.palette = it },
    wallpaperSettings: GlassWallpaperSettings = GlassDefaults.wallpaper,
    onWallpaperSettingsChanged: (GlassWallpaperSettings) -> Unit = { GlassDefaults.wallpaper = it },
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val lang = LocalAppLanguage.current
    var currentLanguage by remember { mutableStateOf(settingsManager.appLanguage) }
    var savedPalettes by remember { mutableStateOf(settingsManager.savedGlassPalettes) }
    var showSavePaletteDialog by remember { mutableStateOf(false) }
    var paletteName by remember { mutableStateOf(if (lang == "zh") "我的调色盘" else "My palette") }

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

            if (GlassDefaults.enabled) item(key = "appearance-theme-colors") {
                CupertinoGroupedSection(
                    title = when (lang) { "zh" -> "主题色"; "ru" -> "Цвет темы"; else -> "Theme color" },
                    footer = when (lang) {
                        "zh" -> "选择网格中的任意纯色，即时应用到强调色与玻璃背景。"
                        "ru" -> "Выберите цвет сетки для акцентов и стеклянного фона."
                        else -> "Choose a grid color to apply it to accents and the glass background."
                    }
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        GlassPalettePicker(
                            selected = palette,
                            labelProvider = { spec ->
                                if (spec.key.startsWith("custom:")) {
                                    if (lang == "zh") spec.labelZh else spec.labelEn
                                } else AppStrings.get("palette_" + spec.key, lang)
                            },
                            onSelect = onPaletteChanged,
                            modifier = Modifier.testTag("appearance-theme-color-grid")
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (lang == "zh") "已收纳的调色盘" else "Saved palettes",
                                color = LoonTextPrimary,
                                fontSize = 14.sp
                            )
                            TextButton(
                                onClick = {
                                    paletteName = if (lang == "zh") "我的调色盘" else "My palette"
                                    showSavePaletteDialog = true
                                },
                                modifier = Modifier.testTag("save-current-palette")
                            ) {
                                Text(if (lang == "zh") "收纳当前配色" else "Save current")
                            }
                        }
                        savedPalettes.forEachIndexed { index, entry ->
                            SavedPaletteRow(
                                entry = entry,
                                index = index,
                                lang = lang,
                                onApply = { onPaletteChanged(GlassPalettes.byKey(entry.key)) },
                                onDelete = {
                                    savedPalettes = settingsManager.deleteSavedGlassPalette(entry.name)
                                }
                            )
                        }
                        if (savedPalettes.isEmpty()) {
                            Text(
                                text = if (lang == "zh") "保存后会显示在这里，可随时应用或删除。" else "Saved palettes appear here for reuse or removal.",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = LoonTextMuted,
                                fontSize = 12.sp
                            )
                        }
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

    if (showSavePaletteDialog) {
        AlertDialog(
            onDismissRequest = { showSavePaletteDialog = false },
            title = { Text(if (lang == "zh") "收纳调色盘" else "Save palette") },
            text = {
                OutlinedTextField(
                    value = paletteName,
                    onValueChange = { paletteName = it.take(32) },
                    singleLine = true,
                    label = { Text(if (lang == "zh") "名称" else "Name") },
                    modifier = Modifier.testTag("saved-palette-name")
                )
            },
            confirmButton = {
                TextButton(
                    enabled = paletteName.isNotBlank(),
                    onClick = {
                        savedPalettes = settingsManager.saveGlassPalette(paletteName, palette.key)
                        showSavePaletteDialog = false
                    },
                    modifier = Modifier.testTag("confirm-save-palette")
                ) { Text(if (lang == "zh") "保存" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePaletteDialog = false }) {
                    Text(if (lang == "zh") "取消" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun SavedPaletteRow(
    entry: SavedGlassPalette,
    index: Int,
    lang: String,
    onApply: () -> Unit,
    onDelete: () -> Unit
) {
    val color = GlassPalettes.byKey(entry.key).accent
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag("saved-palette-$index"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = MaterialTheme.shapes.small,
            color = color,
            border = androidx.compose.foundation.BorderStroke(.5.dp, LoonTextMuted.copy(alpha = .35f))
        ) {}
        Text(entry.name, Modifier.weight(1f).padding(start = 10.dp), color = LoonTextPrimary, maxLines = 1)
        TextButton(onClick = onApply, modifier = Modifier.testTag("apply-saved-palette-$index")) {
            Text(when (lang) { "zh" -> "应用"; "ru" -> "Применить"; else -> "Apply" })
        }
        IconButton(onClick = onDelete, modifier = Modifier.testTag("delete-saved-palette-$index")) {
            val deleteLabel = when (lang) { "zh" -> "删除"; "ru" -> "Удалить"; else -> "Delete" }
            Icon(Icons.Default.Delete, contentDescription = "$deleteLabel ${entry.name}", tint = LoonTextSecondary)
        }
    }
}
