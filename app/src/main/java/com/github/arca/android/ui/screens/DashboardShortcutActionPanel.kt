package com.github.arca.android.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.data.InstalledApp
import com.github.arca.android.data.LogRepository
import com.github.arca.android.data.ParsedRule
import com.github.arca.android.data.PerAppProxyMode
import com.github.arca.android.data.ProfileItem
import com.github.arca.android.data.RequestLogRepository
import com.github.arca.android.service.TrafficMetrics
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.glass.glassPalette
import com.github.arca.android.ui.theme.IosBlue
import com.github.arca.android.ui.theme.LoonBlue
import com.github.arca.android.ui.theme.LoonTextMuted
import com.github.arca.android.ui.theme.LoonTextPrimary
import com.github.arca.android.ui.theme.LoonTextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

internal fun outboundIpFlagsSummary(flags: List<String>?): String = when {
    flags == null -> "来源未返回"
    flags.isEmpty() -> "本次未命中已知标记"
    else -> flags.joinToString(" · ") { flag ->
        when (flag) {
            "Proxy" -> "代理"
            "VPN" -> "VPN"
            "Tor" -> "Tor"
            "Abuser" -> "滥用标记"
            else -> flag
        }
    }
}

internal fun outboundIpClassification(level: String?, type: String?): String {
    val localizedType = type?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
        when (value) {
            "Hosting" -> "机房"
            "Residential" -> "住宅"
            "Mobile" -> "移动网络"
            "Business" -> "商用"
            else -> value
        }
    }
    return listOfNotNull(level?.trim()?.takeIf { it.isNotEmpty() }, localizedType)
        .joinToString(" · ")
        .ifBlank { "来源未提供" }
}

@Composable
internal fun DashboardShortcutActionPanel(
    shortcut: ShortcutDef,
    vpnStatus: VpnState.Status,
    activeProfileName: String,
    profiles: List<ProfileItem>,
    selectedProfileId: String?,
    onSelectProfile: (ProfileItem) -> Unit,
    rules: List<ParsedRule>,
    requestMonitoringEnabled: Boolean,
    fakeIpEnabled: Boolean,
    onFakeIpEnabledChanged: (Boolean) -> Unit,
    perAppProxyMode: PerAppProxyMode,
    selectedPackages: Set<String>,
    apps: List<InstalledApp>,
    isAppsLoading: Boolean,
    onEnsureAppsLoaded: () -> Unit,
    onPerAppProxyModeChanged: (PerAppProxyMode) -> Unit,
    onToggleAppPackage: (String) -> Unit,
    allowLan: Boolean,
    onAllowLanChanged: (Boolean) -> Unit,
    trafficMetrics: StateFlow<TrafficMetrics>,
    coreVersion: String,
    onClose: () -> Unit,
    onImportLocalFile: (() -> Unit)? = null,
    onAddSubscription: (String, String) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    val logEntries by LogRepository.logsFlow.collectAsState()
    val requestEntries by RequestLogRepository.requestsFlow.collectAsState()
    val traffic by trafficMetrics.collectAsState()
    var filter by remember(shortcut.id) { mutableStateOf("") }
    var showAddProfileSheet by remember { mutableStateOf(false) }
    var dnsQuery by remember { mutableStateOf("") }
    var dnsLoading by remember { mutableStateOf(false) }
    var dnsAnswer by remember { mutableStateOf<String?>(null) }
    var dnsError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(shortcut.id) {
        if (shortcut.id == "routing") onEnsureAppsLoaded()
    }

    Column(Modifier.fillMaxSize().testTag("shortcut-panel-${shortcut.id}-content")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(shortcut.title, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    color = LoonTextPrimary, maxLines = 1)
                Text(shortcut.subtitle, fontSize = 12.sp, color = LoonTextSecondary, maxLines = 1)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp).testTag("shortcut-panel-close")) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = LoonTextSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = LoonTextSecondary.copy(alpha = 0.16f), thickness = 0.6.dp)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .testTag("shortcut-panel-quick-actions"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(2.dp))
            when (shortcut.id) {
                "vpn" -> {
                    Text(when (vpnStatus) {
                        VpnState.Status.RUNNING -> "VPN 正在运行。点击首页快捷卡片即可停止连接。"
                        VpnState.Status.STARTING -> "VPN 正在连接。点击首页快捷卡片可按原有流程取消或切换连接。"
                        VpnState.Status.STOPPING -> "VPN 正在停止，请稍候。"
                        else -> "VPN 尚未连接。点击首页快捷卡片即可直接启动，不会打开额外窗口。"
                    }, color = LoonTextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                }
                "ip_test" -> {
                    Text("IP 测试功能目前维护中。维护期间不会发起 IP 查询或后台请求。", color = LoonTextSecondary, fontSize = 13.sp)
                    Column(Modifier.fillMaxWidth().testTag("quick-ip-current-window")) {
                        QuickInfoCard {
                            Text("IP 测试 · 维护中", color = LoonTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("等待账号凭据通过安全表单提交后再恢复检测。", color = LoonTextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth().testTag("quick-ip-refresh"),
                                colors = ButtonDefaults.buttonColors(containerColor = LoonBlue)
                            ) { Text("维护中") }
                        }
                    }
                    Column(Modifier.fillMaxWidth().testTag("quick-ip-details-window")) {
                        QuickInfoCard {
                            Text("代理 / Hosting", color = LoonTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            QuickHint("维护中，暂无本次检测数据。恢复后只显示来源实际返回的字段，不推测托管或代理状态。")
                            HorizontalDivider(color = LoonTextSecondary.copy(alpha = 0.16f), thickness = 0.6.dp)
                            Text("黑名单标记", color = LoonTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            QuickHint("维护中；当前没有黑名单查询或缓存结果。")
                            HorizontalDivider(color = LoonTextSecondary.copy(alpha = 0.16f), thickness = 0.6.dp)
                            Text("代理类型", color = LoonTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            QuickHint("维护中；凭据提交后再恢复相关检测。")
                        }
                    }
                }
                "profiles" -> {
                    Text("当前配置：$activeProfileName。选择一项即可在此切换，不会跳到配置管理页。", color = LoonTextSecondary, fontSize = 13.sp)
                    Button(
                        onClick = { showAddProfileSheet = true },
                        modifier = Modifier.fillMaxWidth().testTag("quick-profile-add"),
                        colors = ButtonDefaults.buttonColors(containerColor = LoonBlue)
                    ) { Text("添加配置：本地文件 / 订阅") }
                    if (profiles.isEmpty()) QuickHint("暂无可用配置；可通过上方入口导入本地文件或订阅")
                    profiles.forEach { profile ->
                        val isSelected = profile.id == selectedProfileId
                        QuickRow(
                            title = profile.name,
                            subtitle = if (isSelected) "当前使用" else profile.url ?: "本地配置",
                            trailing = if (isSelected) "当前" else "切换",
                            selected = isSelected,
                            onClick = { if (!isSelected) { onSelectProfile(profile); onClose() } }
                        )
                    }
                }
                "request_logs" -> {
                    val requests = remember(requestEntries) { requestEntries.asReversed().take(12).map(::parseRequestLog) }
                    val filteredRequests = remember(requests, filter) {
                        if (filter.isBlank()) requests else requests.filter {
                            it.destination.contains(filter, true) || it.rule.contains(filter, true) || it.outbound.contains(filter, true)
                        }
                    }
                    OutlinedTextField(value = filter, onValueChange = { filter = it }, modifier = Modifier.fillMaxWidth().testTag("quick-requests-search"), singleLine = true, label = { Text("搜索请求") })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (requestMonitoringEnabled) "请求监控已开启" else "请求监控已暂停", color = LoonTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Switch(checked = requestMonitoringEnabled, onCheckedChange = RequestLogRepository::setMonitoringEnabled,
                            modifier = Modifier.testTag("quick-request-monitor-toggle"))
                    }
                    Text("最近 ${filteredRequests.size} 条请求", color = LoonTextMuted, fontSize = 12.sp)
                    if (filteredRequests.isEmpty()) QuickHint("暂无匹配的请求记录")
                    filteredRequests.forEach { request ->
                        QuickRow(
                            title = request.destination.ifBlank { request.rawMessage },
                            subtitle = listOf(request.protocol, request.rule, request.outbound).filter { it.isNotBlank() }.joinToString(" · "),
                            trailing = request.source.takeIf { it.isNotBlank() } ?: "",
                            selected = false,
                            onClick = { }
                        )
                    }
                    TextButtonLike("清空请求记录", onClick = RequestLogRepository::clear)
                }
                "dns" -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Fake-IP 模式", color = LoonTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (fakeIpEnabled) "已启用" else "已关闭", color = LoonTextSecondary, fontSize = 11.sp)
                        }
                        Switch(checked = fakeIpEnabled, onCheckedChange = onFakeIpEnabledChanged,
                            modifier = Modifier.testTag("quick-dns-fake-ip-toggle"))
                    }
                    OutlinedTextField(
                        value = dnsQuery,
                        onValueChange = { dnsQuery = it; dnsError = null },
                        modifier = Modifier.fillMaxWidth().testTag("quick-dns-query"),
                        singleLine = true,
                        label = { Text("域名") },
                        placeholder = { Text("例如 example.com") }
                    )
                    Button(
                        onClick = {
                            val host = dnsQuery.trim().removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore(':')
                            if (host.isNotBlank()) scope.launch {
                                dnsLoading = true
                                dnsError = null
                                dnsAnswer = null
                                try {
                                    dnsAnswer = withContext(Dispatchers.IO) {
                                        InetAddress.getAllByName(host).joinToString("\n") { it.hostAddress ?: it.hostName }
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    dnsError = error.message ?: "解析失败"
                                } finally {
                                    dnsLoading = false
                                }
                            }
                        },
                        enabled = dnsQuery.isNotBlank() && !dnsLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = LoonBlue)
                    ) { Text(if (dnsLoading) "查询中…" else "查询 DNS") }
                    dnsError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    dnsAnswer?.let { QuickInfoCard { Text(it, color = LoonTextPrimary, fontSize = 12.sp, lineHeight = 18.sp) } }
                    val dnsLogs = remember(logEntries) {
                        logEntries.filter { it.message.contains("dns", true) || it.message.contains("fake-ip", true) || it.message.contains("resolve", true) }
                            .takeLast(8).asReversed()
                    }
                    Text("最近 DNS 记录 (${dnsLogs.size})", color = LoonTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (dnsLogs.isEmpty()) QuickHint("暂无捕获的 DNS 查询记录")
                    dnsLogs.forEach { QuickRow(it.message, it.level, "", false, {}) }
                }
                "rules" -> {
                    Text("当前配置包含 ${rules.size} 条规则。可在面板内搜索规则与目标策略。", color = LoonTextSecondary, fontSize = 13.sp)
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        modifier = Modifier.fillMaxWidth().testTag("quick-rules-search"),
                        singleLine = true,
                        label = { Text("搜索规则") }
                    )
                    val filteredRules = remember(rules, filter) {
                        if (filter.isBlank()) rules else rules.filter {
                            it.type.contains(filter, true) || it.payload.contains(filter, true) || it.target.contains(filter, true)
                        }
                    }
                    if (filteredRules.isEmpty()) QuickHint("没有匹配的规则")
                    filteredRules.take(20).forEach { rule ->
                        QuickRow("${rule.type}: ${rule.payload}", "目标：${rule.target}", if (rule.noResolve) "no-resolve" else "", false, {})
                    }
                }
                "routing" -> {
                    Text("按应用设置代理策略；开关与应用列表都可直接在此修改。", color = LoonTextSecondary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(PerAppProxyMode.DISABLED, PerAppProxyMode.WHITELIST, PerAppProxyMode.BLACKLIST).forEach { mode ->
                            FilterChip(
                                selected = perAppProxyMode == mode,
                                onClick = { onPerAppProxyModeChanged(mode) },
                                label = { Text(when (mode) {
                                    PerAppProxyMode.DISABLED -> "关闭"
                                    PerAppProxyMode.WHITELIST -> "仅代理所选"
                                    PerAppProxyMode.BLACKLIST -> "排除所选"
                                }, fontSize = 11.sp) }
                            )
                        }
                    }
                    if (perAppProxyMode != PerAppProxyMode.DISABLED) {
                        OutlinedTextField(value = filter, onValueChange = { filter = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("搜索应用") })
                        if (isAppsLoading) QuickHint("正在读取应用列表…")
                        val visibleApps = remember(apps, filter) {
                            apps.asSequence().filter { !it.isSystem }
                                .filter { filter.isBlank() || it.label.contains(filter, true) || it.packageName.contains(filter, true) }
                                .take(12).toList()
                        }
                        if (!isAppsLoading && visibleApps.isEmpty()) QuickHint("没有匹配的应用")
                        visibleApps.forEach { app ->
                            QuickAppRow(app, selectedPackages.contains(app.packageName)) { onToggleAppPackage(app.packageName) }
                        }
                    }
                }
                "core_logs" -> {
                    Text(if (vpnStatus == VpnState.Status.RUNNING) "核心运行中 · 最近日志" else "最近核心日志", color = LoonTextSecondary, fontSize = 13.sp)
                    val latest = remember(logEntries) { logEntries.takeLast(12).asReversed() }
                    if (latest.isEmpty()) QuickHint("暂无核心日志")
                    latest.forEach { QuickRow(it.message, it.level, "", false, {}) }
                    TextButtonLike("清空日志", onClick = LogRepository::clear)
                }
                "network_share" -> {
                    QuickInfoCard {
                        Text("局域网共享", color = LoonTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("代理端口：127.0.0.1:7890", color = LoonTextSecondary, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (allowLan) "允许局域网设备连接" else "仅本机可连接", color = LoonTextPrimary, fontSize = 12.sp)
                            Switch(checked = allowLan, onCheckedChange = onAllowLanChanged, modifier = Modifier.testTag("quick-network-share-toggle"))
                        }
                    }
                }
                "traffic" -> {
                    Text("实时速率", color = LoonTextSecondary, fontSize = 13.sp)
                    QuickInfoCard {
                        QuickInfoRow("上传", if (vpnStatus == VpnState.Status.RUNNING) traffic.upSpeed else "--")
                        QuickInfoRow("下载", if (vpnStatus == VpnState.Status.RUNNING) traffic.downSpeed else "--")
                    }
                }
                "plugins" -> {
                    QuickInfoCard {
                        QuickInfoRow("内核版本", coreVersion.ifBlank { "v1.18.x" })
                        QuickInfoRow("设备架构", Build.SUPPORTED_ABIS.firstOrNull() ?: "未知")
                        Text("插件与核心组件状态在这里查看，无需跳转页面。", color = LoonTextSecondary, fontSize = 12.sp)
                    }
                }
                else -> Text("${shortcut.title} 的快捷操作已在此面板中提供。", color = LoonTextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))
        }
    }
    if (showAddProfileSheet) {
        ProfileAddSheet(
            onDismissRequest = { showAddProfileSheet = false },
            onImportLocalFile = onImportLocalFile,
            onAddSubscription = onAddSubscription
        )
    }
}

@Composable
private fun QuickInfoCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(glassPalette.panelStart.copy(alpha = 0.48f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        content = { content() }
    )
}

@Composable
private fun QuickInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LoonTextSecondary, fontSize = 12.sp)
        Text(value, color = LoonTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QuickHint(text: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(glassPalette.panelStart.copy(alpha = 0.36f)).padding(12.dp)) {
        Text(text, color = LoonTextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun QuickRow(
    title: String,
    subtitle: String,
    trailing: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) IosBlue.copy(alpha = 0.14f) else glassPalette.panelStart.copy(alpha = 0.34f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = LoonTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            if (subtitle.isNotBlank()) Text(subtitle, color = LoonTextSecondary, fontSize = 10.5.sp, maxLines = 2)
        }
        if (trailing.isNotBlank()) Text(trailing, color = if (selected) IosBlue else LoonTextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun QuickAppRow(app: InstalledApp, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(glassPalette.panelStart.copy(alpha = 0.34f)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(app.label, color = LoonTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(app.packageName, color = LoonTextMuted, fontSize = 9.5.sp, maxLines = 1)
        }
        Switch(checked = selected, onCheckedChange = { onClick() })
    }
}

@Composable
private fun TextButtonLike(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = IosBlue,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp)
    )
}
