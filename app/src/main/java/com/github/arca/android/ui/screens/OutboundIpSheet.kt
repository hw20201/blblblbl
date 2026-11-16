package com.github.arca.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.arca.android.data.OutboundIpInfo
import com.github.arca.android.ui.components.CupertinoGroupedSection
import com.github.arca.android.ui.components.CupertinoIndentedDivider
import com.github.arca.android.ui.glass.GlassModalBottomSheet
import com.github.arca.android.ui.theme.LoonBg
import com.github.arca.android.ui.theme.LoonTextPrimary
import com.github.arca.android.ui.theme.LoonTextSecondary

/** IP checks are intentionally disabled until account credentials arrive through the secure form. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun OutboundIpSheet(isVpnRunning: Boolean, onDismiss: () -> Unit = {}) {
    OutboundIpMaintenanceSheet(onDismiss)
}

/** Kept for call-site/test compatibility; the callback is deliberately never invoked in maintenance mode. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun OutboundIpSheet(onRefresh: suspend () -> OutboundIpInfo, onDismiss: () -> Unit = {}) {
    OutboundIpMaintenanceSheet(onDismiss)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutboundIpMaintenanceSheet(onDismiss: () -> Unit) {
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LoonBg
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("出站 IP · 维护中", color = LoonTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(
                        enabled = false,
                        onClick = {},
                        modifier = Modifier.testTag("outbound-ip-maintenance-action")
                    ) { Text("维护中") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
            CupertinoGroupedSection(title = "功能状态") {
                IpField("状态", "维护中")
                IpField("说明", "账号凭据通过安全表单提交前，IP 检测暂时关闭。不会请求 ident.me、Scamalytics 或其他 IP 查询服务。", last = true)
            }
            CupertinoGroupedSection(title = "检测详情") {
                IpField("Hosting / 代理", "维护中")
                IpField("黑名单标记", "维护中")
                IpField("代理类型", "维护中", last = true)
            }
            Text(
                "维护期间不进行自动检测、重新检测或后台轮询。",
                color = LoonTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun IpField(label: String, value: String, last: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = LoonTextSecondary, style = MaterialTheme.typography.labelMedium)
        Text(value, color = LoonTextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
    }
    if (!last) CupertinoIndentedDivider(startIndent = 16.dp)
}
