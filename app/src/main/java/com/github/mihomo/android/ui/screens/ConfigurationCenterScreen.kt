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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.ui.components.*
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.theme.LoonBg

/** Navigation hub; existing managers and screens retain ownership of their resources. */
@Composable
fun ConfigurationCenterScreen(
    onNavigateToProfiles: () -> Unit,
    onNavigateToScripts: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToAppRouting: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)
            .statusBarsPadding()
    ) {
        CupertinoNavBar(title = "配置中心", showBackButton = true, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp).padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            CupertinoGroupedSection(header = "配置 / 订阅") {
                CupertinoCell(
                    title = "配置文件与订阅", subtitle = "导入本地配置、管理配置及刷新订阅",
                    icon = Icons.Default.FolderOpen, iconBg = Color(0xFF3B82F6),
                    showChevron = true, onClick = onNavigateToProfiles
                )
            }
            CupertinoGroupedSection(header = "脚本") {
                CupertinoCell(
                    title = "脚本管理", subtitle = "管理全局脚本；单配置覆写仍在配置详情中设置",
                    icon = Icons.Default.Code, iconBg = Color(0xFF3B82F6),
                    showChevron = true, onClick = onNavigateToScripts
                )
            }
            CupertinoGroupedSection(header = "规则 / 路由资源", footer = "保留当前配置中的规则与资源；更新和重载继续使用原有配置流程。") {
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
    }
}
