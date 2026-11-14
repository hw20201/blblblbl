package com.github.arca.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.core.ClashCore
import com.github.arca.android.core.model.ConnectionInfo
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.components.AppNotice
import com.github.arca.android.ui.components.CupertinoNavBar
import com.github.arca.android.ui.components.ShortcutSearchField
import com.github.arca.android.ui.components.bounceOverscroll
import com.github.arca.android.ui.glass.GlassAlertDialog
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.LightGlassSurface
import com.github.arca.android.ui.theme.AppPalette
import com.github.arca.android.ui.theme.IosBlue
import com.github.arca.android.ui.theme.LoonBg
import com.github.arca.android.ui.theme.LoonTextMuted
import com.github.arca.android.ui.theme.LoonTextPrimary
import com.github.arca.android.ui.theme.LoonTextSecondary
import com.github.kr328.clash.core.util.trafficString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Live connection list with per-connection and close-all actions. Polls every second while shown. */
@Composable
fun ConnectionsScreen(
    vpnState: VpnState,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var connections by remember { mutableStateOf<List<ConnectionInfo>>(emptyList()) }
    var supported by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var confirmCloseAll by remember { mutableStateOf(false) }
    val running = vpnState.status == VpnState.Status.RUNNING

    LaunchedEffect(running, paused) {
        if (!running) { connections = emptyList(); return@LaunchedEffect }
        while (isActive && !paused) {
            val snapshot = withContext(Dispatchers.IO) { ClashCore.queryConnections() }
            if (snapshot == null) { supported = false; break }
            connections = snapshot.connections.orEmpty().sortedByDescending { it.start }
            delay(1000)
        }
    }

    val filtered = remember(connections, query) {
        if (query.isBlank()) connections else connections.filter { c ->
            c.target.contains(query, true) || c.outbound.contains(query, true) ||
                c.rule.contains(query, true) || c.metadata.process.contains(query, true)
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)
            .statusBarsPadding()
            .testTag("connections-root")
    ) {
        CupertinoNavBar(
            title = "活动连接",
            subtitle = if (running && supported) "${connections.size} 个连接" else null,
            showBackButton = true,
            onBack = onBack,
            frosted = true,
            actions = {
                if (running && supported) {
                    IconButton(onClick = { paused = !paused }, modifier = Modifier.size(36.dp)) {
                        Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (paused) "继续刷新" else "暂停刷新", tint = IosBlue)
                    }
                    IconButton(onClick = { if (connections.isNotEmpty()) confirmCloseAll = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "断开全部连接", tint = AppPalette.danger)
                    }
                }
            }
        )
        when {
            !running -> EmptyHint("代理未运行\n启动 VPN 后可查看实时连接")
            !supported -> EmptyHint("当前原生核心版本不支持连接列表\n请重新编译 native 库（build-native.sh）")
            else -> {
                ShortcutSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索域名 / 节点 / 规则 / 应用",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                if (filtered.isEmpty()) {
                    EmptyHint(if (query.isBlank()) "暂无活动连接" else "没有匹配的连接")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().bounceOverscroll(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { c ->
                            ConnectionRow(c) {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { ClashCore.closeConnection(c.id) }
                                    connections = connections.filterNot { it.id == c.id }
                                    if (!ok) AppNotice.info("连接已结束")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmCloseAll) {
        GlassAlertDialog(
            onDismissRequest = { confirmCloseAll = false },
            title = { Text("断开全部连接", fontWeight = FontWeight.Bold, color = LoonTextPrimary) },
            text = { Text("将立即断开 ${connections.size} 个活动连接，应用会自动重连。", color = LoonTextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCloseAll = false
                    scope.launch {
                        withContext(Dispatchers.IO) { ClashCore.closeAllConnections() }
                        connections = emptyList()
                        AppNotice.success("已断开全部连接")
                    }
                }) { Text("断开", color = AppPalette.danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { confirmCloseAll = false }) { Text("取消", color = LoonTextSecondary) } }
        )
    }
}

@Composable
private fun ConnectionRow(c: ConnectionInfo, onClose: () -> Unit) {
    LightGlassSurface(Modifier.fillMaxWidth().testTag("connection-${c.id}"), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.target.ifBlank { "未知目标" }, color = LoonTextPrimary, fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                val route = buildString {
                    append(c.metadata.network.uppercase())
                    if (c.outbound.isNotBlank()) append(" · ").append(c.outbound)
                    if (c.rule.isNotBlank()) {
                        append(" · ").append(c.rule)
                        if (c.rulePayload.isNotBlank()) append("(").append(c.rulePayload).append(")")
                    }
                }
                Text(route, color = LoonTextSecondary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row {
                    Text("↑ ${trafficString(c.upload)}  ↓ ${trafficString(c.download)}",
                        color = LoonTextMuted, fontSize = 11.sp)
                    if (c.metadata.process.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(c.metadata.process, color = LoonTextMuted, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "断开此连接", tint = LoonTextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = LoonTextSecondary, fontSize = 14.sp, lineHeight = 21.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
