package com.github.mihomo.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mihomo.android.data.LogEntry
import com.github.mihomo.android.data.LogRepository
import com.github.mihomo.android.data.RequestLogRepository
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.components.bounceOverscroll
import com.github.mihomo.android.ui.theme.*
import com.github.mihomo.android.ui.components.CupertinoNavBar
import java.text.SimpleDateFormat
import java.util.Locale
import com.github.mihomo.android.ui.components.rememberHapticAction
import com.github.mihomo.android.ui.components.hapticClickable

data class StructuredRequest(
    val id: Long,
    val timestamp: Long,
    val protocol: String,
    val source: String,
    val destination: String,
    val rule: String,
    val outbound: String,
    val rawMessage: String
)

internal fun parseRequestLog(entry: LogEntry): StructuredRequest {
    val msg = entry.message.trim()
    var protocol = "NET"
    var source = ""
    var dest = ""
    var rule = ""
    var outbound = ""

    if (msg.startsWith("[")) {
        protocol = msg.substringAfter("[").substringBefore("]").uppercase()
    } else if (msg.contains("TCP", ignoreCase = true)) {
        protocol = "TCP"
    } else if (msg.contains("UDP", ignoreCase = true)) {
        protocol = "UDP"
    } else if (msg.contains("DNS", ignoreCase = true)) {
        protocol = "DNS"
    }

    if (msg.contains("-->")) {
        source = msg.substringBefore("-->").substringAfterLast("]").trim()
        val afterArrow = msg.substringAfter("-->").trim()
        dest = afterArrow.substringBefore(" ").substringBefore("match").trim()
        if (afterArrow.contains("match", ignoreCase = true)) {
            val afterMatch = afterArrow.substringAfter("match").trim()
            rule = afterMatch.substringBefore("using").trim()
            if (afterMatch.contains("using", ignoreCase = true)) {
                outbound = afterMatch.substringAfter("using").trim()
            }
        }
    } else {
        dest = msg
    }

    return StructuredRequest(
        id = entry.id,
        timestamp = entry.timestamp,
        protocol = protocol,
        source = source,
        destination = dest,
        rule = rule,
        outbound = outbound,
        rawMessage = msg
    )
}

@Composable
fun RequestLogsScreen(
    vpnState: VpnState,
    onBack: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val rawLogs by RequestLogRepository.requestsFlow.collectAsState()
    val monitoringEnabled by RequestLogRepository.monitoringEnabled.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedProtocol by remember { mutableStateOf("ALL") }

    val requests = remember(rawLogs) {
        rawLogs.map { parseRequestLog(it) }.reversed()
    }

    val filteredRequests = remember(requests, searchQuery, selectedProtocol) {
        requests.filter { req ->
            val matchProto = selectedProtocol == "ALL" || req.protocol.equals(selectedProtocol, ignoreCase = true)
            val matchQuery = searchQuery.isBlank() ||
                req.destination.contains(searchQuery, ignoreCase = true) ||
                req.rule.contains(searchQuery, ignoreCase = true) ||
                req.outbound.contains(searchQuery, ignoreCase = true) ||
                req.rawMessage.contains(searchQuery, ignoreCase = true)
            matchProto && matchQuery
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val isRunning = vpnState.status == VpnState.Status.RUNNING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LoonBg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp)
            .navigationBarsPadding()
    ) {
        // Cupertino navigation bar; pause still controls the actual request monitor.
        CupertinoNavBar(
            title = AppStrings.get("requests_screen_title", lang),
            showBackButton = true,
            onBack = onBack,
            actions = {
                IconButton(onClick = rememberHapticAction { RequestLogRepository.setMonitoringEnabled(!monitoringEnabled) }, modifier = Modifier.size(36.dp)) {
                    Icon(if (monitoringEnabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (monitoringEnabled) "暂停" else "继续",
                        tint = if (monitoringEnabled) LoonTextSecondary else LoonGreen)
                }
                IconButton(onClick = rememberHapticAction { RequestLogRepository.clear() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "清空", tint = LoonTextSecondary)
                }
            }
        )

        // Live Traffic Metrics Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = LoonCard,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, LoonCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("实时速率 (↑ / ↓)", fontSize = 11.sp, color = LoonTextMuted)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${vpnState.upSpeed.ifBlank { "0 B/s" }} / ${vpnState.downSpeed.ifBlank { "0 B/s" }}",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = LoonBlue
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("流量累计 (↑ / ↓)", fontSize = 11.sp, color = LoonTextMuted)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${vpnState.totalUp.ifBlank { "0 B" }} / ${vpnState.totalDown.ifBlank { "0 B" }}",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = LoonTextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索请求目标、域名、规则、节点...", fontSize = 12.5.sp, color = LoonTextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = LoonTextMuted, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = rememberHapticAction({ searchQuery = "" }), modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "清除", tint = LoonTextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = LoonCard,
                unfocusedContainerColor = LoonCard,
                focusedBorderColor = LoonBlue,
                unfocusedBorderColor = LoonCardBorder,
                focusedTextColor = LoonTextPrimary,
                unfocusedTextColor = LoonTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Protocol Filter Chips
        val protocols = listOf("ALL", "TCP", "UDP", "DNS")
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(protocols) { proto ->
                val isSel = selectedProtocol == proto
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) LoonEditBlueBg else LoonCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) LoonBlue else LoonCardBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .hapticClickable { selectedProtocol = proto }
                ) {
                    Text(
                        text = if (proto == "ALL") "全部协议 (${requests.size})" else proto,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) LoonBlue else LoonTextSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Requests List
        if (filteredRequests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (!isRunning) "代理服务未开启\n连接建立后，应用请求记录将在此实时滚动"
                        else if (searchQuery.isNotBlank()) "未找到包含「$searchQuery」的请求记录"
                        else "暂无请求记录",
                        color = LoonTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .bounceOverscroll(allowTop = false, allowBottom = true),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredRequests, key = { it.id }) { req ->
                    val protoColor = when (req.protocol) {
                        "TCP" -> LoonBlue
                        "UDP" -> LoonGreen
                        "DNS" -> Color(0xFFF59E0B)
                        else -> Color(0xFF8B5CF6)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = LoonCard,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, LoonCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = req.protocol,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = protoColor,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )

                                    Text(
                                        text = req.destination,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LoonTextPrimary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = timeFormat.format(req.timestamp),
                                    fontSize = 10.5.sp,
                                    color = LoonTextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (req.rule.isNotBlank() || req.outbound.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (req.rule.isNotBlank()) {
                                        Text(
                                            text = "规则: ${req.rule}",
                                            fontSize = 11.sp,
                                            color = LoonTextSecondary,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }

                                    if (req.outbound.isNotBlank()) {
                                        Text(
                                            text = req.outbound,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                req.outbound.contains("DIRECT", ignoreCase = true) -> Color(0xFF10B981)
                                                req.outbound.contains("REJECT", ignoreCase = true) -> RedDanger
                                                else -> LoonBlue
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
