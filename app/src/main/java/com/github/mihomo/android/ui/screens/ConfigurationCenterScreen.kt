package com.github.mihomo.android.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.github.mihomo.android.ui.components.*
import com.github.mihomo.android.ui.glass.FROSTED_PAGE_TITLE_FILL_ALPHA
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.glass.recordNavigationScene
import com.github.mihomo.android.ui.glass.rememberNavigationLens
import com.github.mihomo.android.ui.theme.LoonBg
import com.github.mihomo.android.ui.theme.LoonTextSecondary

/** Navigation hub; existing managers and screens retain ownership of their resources. */
@Composable
fun ConfigurationCenterScreen(
    onNavigateToProfiles: () -> Unit,
    onNavigateToScripts: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToAppRouting: () -> Unit,
    onBack: () -> Unit
) {
    val titleScene = rememberNavigationLens()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerInset = with(LocalDensity.current) { headerHeightPx.toDp() }

    Box(
        Modifier.fillMaxSize()
            .background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)
            .testTag("configuration-center-root")
    ) {
        Column(
            Modifier.fillMaxSize()
                .then(if (GlassDefaults.enabled) Modifier.recordNavigationScene(titleScene) else Modifier)
                .testTag("configuration-center-scroll")
                .verticalScroll(rememberScrollState())
                .padding(top = headerInset + 16.dp, bottom = 136.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConfigurationCenterSection("配置 / 订阅", "configuration-center-section-header-0") {
                CupertinoCell(
                    title = "配置文件与订阅", subtitle = "导入本地配置、管理配置及刷新订阅",
                    icon = Icons.Default.FolderOpen, iconBg = Color(0xFF3B82F6),
                    showChevron = true, onClick = onNavigateToProfiles
                )
            }
            ConfigurationCenterSection("脚本", "configuration-center-section-header-1") {
                CupertinoCell(
                    title = "脚本管理", subtitle = "管理全局脚本；单配置覆写仍在配置详情中设置",
                    icon = Icons.Default.Code, iconBg = Color(0xFF3B82F6),
                    showChevron = true, onClick = onNavigateToScripts
                )
            }
            ConfigurationCenterSection(
                title = "规则 / 路由资源",
                tag = "configuration-center-section-header-2",
                footer = "保留当前配置中的规则与资源；更新和重载继续使用原有配置流程。"
            ) {
                CupertinoCell(
                    title = "规则资源", subtitle = "查看与筛选当前配置中的规则",
                    icon = Icons.Default.Rule, iconBg = Color(0xFFF59E0B),
                    showChevron = true, onClick = onNavigateToRules
                )
                CupertinoIndentedDivider(startIndent = 56.dp)
                CupertinoCell(
                    title = "应用分流", subtitle = "管理按应用代理设置",
                    icon = Icons.Default.Apps, iconBg = Color(0xFF8B5CF6),
                    showChevron = true, onClick = onNavigateToAppRouting
                )
            }
        }

        Box(
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
