package com.github.mihomo.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.data.OutboundIpInfo
import com.github.mihomo.android.data.OutboundIpRepository
import com.github.mihomo.android.ui.components.*
import com.github.mihomo.android.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Only composed after the user taps IP test. No polling, background probes or VPN state effect. */
@Composable
fun OutboundIpSheet(isVpnRunning: Boolean, onDismiss: () -> Unit = {}) {
    val repository = remember { OutboundIpRepository() }
    OutboundIpSheet(onRefresh = { repository.lookup(isVpnRunning) }, onDismiss = onDismiss)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboundIpSheet(onRefresh: suspend () -> OutboundIpInfo, onDismiss: () -> Unit = {}) {
    var result by remember { mutableStateOf<OutboundIpInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var hideIp by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val refresh by rememberUpdatedState(onRefresh)
    suspend fun lookup() {
        loading = true
        error = null
        result = null // Never present the previous exit as a fresh measurement after failure.
        try {
            result = refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "检测失败：${e.message ?: "请检查网络后重试"}"
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { lookup() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                Text("出站 IP", color = LoonTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(enabled = !loading, onClick = {
                        if (!loading) { loading = true; scope.launch { lookup() } }
                    }) {
                        Text(if (loading) "检测中…" else "重新检测")
                    }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
            }
            CupertinoGroupedSection(title = "基础信息") {
                IpField("IP 地址", result?.ipAddress?.let { if (hideIp) "••••••••" else it } ?: "未知")
                IpField("等级", result?.level ?: "未知")
                IpField("类型", when (result?.type) {
                    "Hosting" -> "机房"
                    "Residential" -> "住宅"
                    "Mobile" -> "移动网络"
                    "Business" -> "商用"
                    else -> "未知"
                })
                IpField("命中标记", result?.flags?.let { if (it.isEmpty()) "无" else it.joinToString("、") } ?: "未知", last = true)
            }
            CupertinoGroupedSection(title = "网络") {
                IpField("组织", result?.organization ?: "未知")
                IpField("ASN", result?.asn ?: "未知")
                IpField("采用来源", result?.source ?: "未知", last = true)
            }
            CupertinoGroupedSection(title = "其他", footer = "仅在打开本面板或点击重新检测时请求第三方服务。VPN 运行时通过本地代理检测当前出站；未连接时检测本机网络。等级按已知类型和风险标记分类，并非安全保证；未提供的信息显示为未知。") {
                CupertinoCell(title = "隐藏 IP", trailing = {
                    CupertinoSwitch(checked = hideIp, onCheckedChange = { hideIp = it })
                })
            }
        }
    }
}

@Composable
private fun IpField(label: String, value: String, last: Boolean = false) {
    // Stacked values remain readable on narrow screens, large fonts and long organization names.
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = LoonTextSecondary, style = MaterialTheme.typography.labelMedium)
        Text(value, color = LoonTextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
    }
    if (!last) CupertinoIndentedDivider(startIndent = 16.dp)
}
