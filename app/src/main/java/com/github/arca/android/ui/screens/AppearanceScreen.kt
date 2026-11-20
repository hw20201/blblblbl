package com.github.arca.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.github.arca.android.data.SettingsManager
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.components.*
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.GlassSettings
import com.github.arca.android.ui.glass.LocalGlassSettings
import com.github.arca.android.ui.glass.GlassTuningGroup
import com.github.arca.android.ui.glass.FROSTED_PAGE_TITLE_FILL_ALPHA
import com.github.arca.android.ui.glass.recordNavigationScene
import com.github.arca.android.ui.glass.rememberNavigationLens
import com.github.arca.android.ui.theme.*

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
    listState: LazyListState = rememberLazyListState(),
    embeddedPanel: Boolean = false,
    glassSettings: GlassSettings = LocalGlassSettings.current,
    onGlassSettingsChanged: (GlassSettings) -> Unit = {}
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

    val edgeInsets = LocalSettingsDetailEdgeInsets.current
    ReportSettingsDetailScrollOffset {
        if (listState.firstVisibleItemIndex > 0) Int.MAX_VALUE / 2 else listState.firstVisibleItemScrollOffset
    }
    // Compose lower settings only as they approach the viewport.
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
                top = if (embeddedPanel) (edgeInsets?.top ?: 8.dp) else headerInset + 24.dp,
                bottom = if (embeddedPanel) (edgeInsets?.bottom ?: 24.dp) else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "appearance-theme-mode") {
                AppearanceSettingsGroup("appearance-theme-group", AppStrings.get("settings_theme", lang), AppStrings.get("settings_theme_group_subtitle", lang)) {
                    themes.forEachIndexed { index, (mode, label) ->
                        CupertinoCell(
                            modifier = Modifier.testTag("appearance-theme-${mode.name.lowercase()}"),
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

            item(key = "appearance-language") {
                AppearanceSettingsGroup("appearance-language-group", AppStrings.get("settings_language", lang), AppStrings.get("settings_language_group_subtitle", lang)) {
                    langs.forEachIndexed { index, (code, label) ->
                        CupertinoCell(
                            modifier = Modifier.testTag("appearance-language-${code.lowercase()}"),
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
                                AppNotice.show("${AppStrings.get("language_updated", code)}: $label")
                            }
                        )
                        if (index < langs.lastIndex) CupertinoIndentedDivider(startIndent = 16.dp)
                    }
                }
            }

            item(key = "appearance-follow-motion") {
                AppearanceSettingsGroup(
                    tag = "appearance-motion-group",
                    title = AppStrings.get("appearance_motion_title", lang),
                    subtitle = AppStrings.get("appearance_motion_subtitle", lang),
                    initiallyExpanded = false
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        FollowMotionControls(glassSettings, onGlassSettingsChanged)
                    }
                }
            }
            item(key = "appearance-node-layout") {
                AppearanceSettingsGroup("appearance-node-layout-group", AppStrings.get("appearance_node_layout_title", lang), AppStrings.get("appearance_node_layout_subtitle", lang)) {
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
                            ArcaGlasmioSwitch(checked = showGroupIcons, onCheckedChange = onShowGroupIconsChanged)
                        }
                    )
                }
            }
        }

        if (!embeddedPanel) Box(
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

/** The same disclosure surface and motion as the main settings groups. */
@Composable
private fun AppearanceSettingsGroup(
    tag: String,
    title: String,
    subtitle: String,
    footer: String? = null,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassTuningGroup(
        title = title,
        subtitle = subtitle,
        tag = tag,
        initiallyExpanded = initiallyExpanded,
        modifier = Modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        content()
        if (footer != null) Text(
            footer,
            color = LoonTextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
