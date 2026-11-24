package com.github.arca.android.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.github.arca.android.ui.components.*
import com.github.arca.android.ui.glass.FROSTED_PAGE_TITLE_FILL_ALPHA
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.recordNavigationScene
import com.github.arca.android.ui.glass.rememberNavigationLens
import com.github.arca.android.ui.theme.LoonBg
import com.github.arca.android.ui.theme.LoonTextSecondary
import com.github.arca.android.ui.theme.AppPalette

/** Navigation hub; existing managers and screens retain ownership of their resources. */
internal enum class ConfigurationCenterDestination { PROFILES, SCRIPTS, RULES, APP_ROUTING }

@Composable
fun ConfigurationCenterScreen(
    onNavigateToProfiles: () -> Unit,
    onNavigateToScripts: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToAppRouting: () -> Unit,
    onBack: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
    embeddedPanel: Boolean = false
) {
    val titleScene = rememberNavigationLens()
    // Inside the settings morph page the EdgeViewport pins its own header above
    // this hub; clear it with the published edge insets so the first section
    // sits below the morph title instead of overlapping it.
    val edgeInsets = LocalSettingsDetailEdgeInsets.current
    // Entrance: the four hub entries fade + slide up, staggered. Flipped once
    // the screen first composes so the animation runs on entry.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerInset = with(LocalDensity.current) { headerHeightPx.toDp() }

    Box(
        Modifier.fillMaxSize()
            .background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)
            .testTag("configuration-center-root")
    ) {
        Column(
            Modifier.fillMaxSize()
                .then(if (GlassDefaults.enabled && !embeddedPanel) Modifier.recordNavigationScene(titleScene) else Modifier)
                .testTag("configuration-center-scroll")
                .verticalScroll(scrollState)
                .padding(
                    top = when {
                        edgeInsets != null -> edgeInsets.top
                        embeddedPanel -> 4.dp
                        else -> headerInset + 32.dp
                    },
                    bottom = if (embeddedPanel) 24.dp else 136.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConfigurationCenterSection("配置 / 订阅", "configuration-center-section-header-0") {
                CupertinoCell(
                    modifier = Modifier.testTag("configuration-center-profiles-entry")
                        .then(configCenterEntrance(index = 0, entered = entered)),
                    title = "配置文件与订阅", subtitle = "导入本地配置、管理配置及刷新订阅",
                    icon = Icons.Default.FolderOpen, iconBg = AppPalette.iconBrightBlue,
                    showChevron = true, onClick = onNavigateToProfiles
                )
            }
            ConfigurationCenterSection("脚本", "configuration-center-section-header-1") {
                CupertinoCell(
                    modifier = Modifier.testTag("configuration-center-scripts-entry")
                        .then(configCenterEntrance(index = 1, entered = entered)),
                    title = "脚本管理", subtitle = "管理全局脚本；单配置覆写仍在配置详情中设置",
                    icon = Icons.Default.Code, iconBg = AppPalette.iconBrightBlue,
                    showChevron = true, onClick = onNavigateToScripts
                )
            }
            ConfigurationCenterSection(
                title = "规则 / 路由资源",
                tag = "configuration-center-section-header-2",
                footer = "保留当前配置中的规则与资源；更新和重载继续使用原有配置流程。"
            ) {
                CupertinoCell(
                    modifier = Modifier.testTag("configuration-center-rules-entry")
                        .then(configCenterEntrance(index = 2, entered = entered)),
                    title = "规则资源", subtitle = "查看与筛选当前配置中的规则",
                    icon = Icons.Default.Rule, iconBg = AppPalette.iconYellowAmber,
                    showChevron = true, onClick = onNavigateToRules
                )
                CupertinoIndentedDivider(startIndent = 56.dp)
                CupertinoCell(
                    modifier = Modifier.testTag("configuration-center-app-routing-entry")
                        .then(configCenterEntrance(index = 3, entered = entered)),
                    title = "应用分流", subtitle = "管理按应用代理设置",
                    icon = Icons.Default.Apps, iconBg = AppPalette.iconViolet,
                    showChevron = true, onClick = onNavigateToAppRouting
                )
            }
        }

        if (!embeddedPanel) Box(
            Modifier.fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .onSizeChanged { headerHeightPx = it.height }
                .zIndex(1f)
                .testTag("configuration-center-pinned-header")
        ) {
            CupertinoNavBar(
                title = "配置中心",
                showBackButton = true,
                onBack = onBack,
                frosted = true,
                titleFrostedFillAlpha = FROSTED_PAGE_TITLE_FILL_ALPHA,
                titleLens = titleScene
            )
        }
    }
}


/**
 * Entrance for the four hub entries: a gentle fade + short slide-up, staggered
 * one after another. It deliberately does NOT start from fully transparent:
 * the section labels sit right above the cards, so a fully-transparent long
 * slide would read as the cards flying in through the text. Starting at a
 * partial alpha with a short travel keeps the text above undisturbed.
 */
@Composable
private fun configCenterEntrance(index: Int, entered: Boolean): Modifier {
    val spec = tween<Float>(durationMillis = 320, delayMillis = index * 70, easing = FastOutSlowInEasing)
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0.35f,
        animationSpec = spec,
        label = "config-center-entrance-alpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (entered) 0.dp else 12.dp,
        animationSpec = tween(durationMillis = 320, delayMillis = index * 70, easing = FastOutSlowInEasing),
        label = "config-center-entrance-offset"
    )
    return Modifier.graphicsLayer { this.alpha = alpha }.offset(y = offsetY)
}

@Composable
private fun ConfigurationCenterSection(
    title: String,
    tag: String,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        // Keep the section label but not its former translucent capsule backdrop. The page title
        // remains the single frosted capsule above the grouped management cards.
        Text(
            text = title,
            color = LoonTextSecondary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            modifier = Modifier.padding(start = 30.dp, bottom = 6.dp).testTag(tag)
        )
        CupertinoGroupedSection(footer = footer, content = content)
    }
}
