package com.github.mihomo.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mihomo.android.data.LogEntry
import com.github.mihomo.android.data.RequestLogRepository
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.components.*
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.glass.GlassSurface
import com.github.mihomo.android.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestLogsScreen(
    vpnState: VpnState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lang = LocalAppLanguage.current
    val haptic = rememberIosHaptic()

    val rawLogs by RequestLogRepository.requestsFlow.collectAsState()
    val monitoringEnabled by RequestLogRepository.monitoringEnabled.collectAsState()
    val isPaused = !monitoringEnabled
    var searchQuery by remember { mutableStateOf("") }
    var selectedProtocol by remember { mutableStateOf("ALL") }
    var inspectedRequest by remember { mutableStateOf<StructuredRequest?>(null) }

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
            .background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)
            .statusBarsPadding()
    ) {
        // Cupertino Navigation Bar
        CupertinoNavBar(
            title = AppStrings.get("requests_screen_title", lang),
            showBackButton = true,
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = {
                        haptic.selection()
                        RequestLogRepository.setMonitoringEnabled(!monitoringEnabled)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "继续" else "暂停",
                        tint = if (isPaused) IosGreen else IosBlue
                    )
                }
                IconButton(
                    onClick = {
                        haptic.selection()
                        RequestLogRepository.clear()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "清空",
                        tint = LoonTextSecondary
                    )
                }
            },
            frosted = true
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Live traffic is now the page's primary visual: a glass instrument panel with a
            // clear state line and two separated metrics, without changing their data source.
            GlassSurface(
                modifier = Modifier.fillMaxWidth().height(104.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (isRunning) IosGreen else LoonTextMuted))
                        Spacer(Modifier.width(8.dp))
                        Text("实时流量", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LoonTextPrimary)
                        Spacer(Modifier.weight(1f))
                        Text(if (isRunning) "LIVE" else "待连接", fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, color = if (isRunning) IosGreen else LoonTextMuted)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("↑ 上传  /  ↓ 下载", fontSize = 10.sp, color = LoonTextMuted)
                            Text(
                                text = "${vpnState.upSpeed.ifBlank { "0 B/s" }}  /  ${vpnState.downSpeed.ifBlank { "0 B/s" }}",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IosBlue, maxLines = 1
                            )
                        }
                        Box(Modifier.width(1.dp).height(34.dp).background(LoonCardBorder.copy(alpha = .7f)))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("累计流量", fontSize = 10.sp, color = LoonTextMuted)
                            Text(
                                text = "${vpnState.totalUp.ifBlank { "0 B" }}  /  ${vpnState.totalDown.ifBlank { "0 B" }}",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LoonTextPrimary, maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cupertino Search Bar
            ShortcutSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "搜索请求域名、IP、规则、出站节点..."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Protocol Filter Segmented Control
            val protocols = listOf("ALL", "TCP", "UDP", "DNS")
            CupertinoSegmentedControl(
                items = protocols,
                selectedItem = selectedProtocol,
                onItemSelected = { selectedProtocol = it },
                labelProvider = { proto ->
                    if (proto == "ALL") "全部 (${requests.size})" else proto
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            // Requests Stream List
            if (filteredRequests.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .bounceOverscroll(),
                    contentAlignment = Alignment.Center
                ) {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.History, contentDescription = null,
                                tint = IosBlue.copy(alpha = .82f), modifier = Modifier.size(38.dp))
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = when {
                                    !isRunning -> "代理服务未开启"
                                    searchQuery.isNotBlank() -> "没有匹配的请求"
                                    else -> "暂无实时请求记录"
                                },
                                color = LoonTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = when {
                                    !isRunning -> "连接建立后，请求明细会在这里实时滚动"
                                    searchQuery.isNotBlank() -> "试试缩短关键词，或切换协议筛选"
                                    else -> "新建连接后，域名、规则和出站节点会汇集在这里"
                                },
                                color = LoonTextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .bounceOverscroll(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredRequests, key = { it.id }) { req ->
                        val protoColor = when (req.protocol) {
                            "TCP" -> IosBlue
                            "UDP" -> IosGreen
                            "DNS" -> IosOrange
                            else -> Color(0xFFAF52DE)
                        }

                        // A quiet rail ties adjacent records into a timeline without adding
                        // another boxed card or changing the row's existing inspect action.
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.width(14.dp).fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(Modifier.padding(top = 15.dp).size(7.dp).clip(CircleShape).background(protoColor))
                                Box(Modifier.width(1.dp).weight(1f).background(protoColor.copy(alpha = 0.22f)))
                            }
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = LoonCard.copy(alpha = .72f),
                                border = androidx.compose.foundation.BorderStroke(0.75.dp, Color.White.copy(alpha = .68f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .iosPressable {
                                        haptic.selection()
                                        inspectedRequest = req
                                    }
                            ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Protocol label: clean colored text typography - NO square box!
                                        Text(
                                            text = req.protocol,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = protoColor
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Text(
                                            text = req.destination,
                                            fontSize = 13.5.sp,
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
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }

                                if (req.source.isNotBlank() || req.rule.isNotBlank() || req.outbound.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            if (req.source.isNotBlank()) {
                                                Text(
                                                    text = "${req.source}  →",
                                                    fontSize = 10.5.sp,
                                                    color = LoonTextMuted,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = if (req.rule.isNotBlank()) "匹配: ${req.rule}" else "默认路由",
                                                fontSize = 11.sp,
                                                color = LoonTextSecondary,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }

                                        if (req.outbound.isNotBlank()) {
                                            val isDirect = req.outbound.contains("DIRECT", ignoreCase = true)
                                            val isReject = req.outbound.contains("REJECT", ignoreCase = true)
                                            val outboundColor = when {
                                                isDirect -> IosGreen
                                                isReject -> IosRed
                                                else -> IosBlue
                                            }

                                            // Rounded capsule pill strictly for essential highlight
                                            Surface(
                                                shape = CircleShape,
                                                color = outboundColor.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = req.outbound,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = outboundColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
            }
        }

    // =========================================================================
    // iOS Inspection Bottom Sheet (Flat layout, NO box-in-a-box)
    // =========================================================================
    if (inspectedRequest != null) {
        val req = inspectedRequest!!

        ModalBottomSheet(
            onDismissRequest = { inspectedRequest = null },
            containerColor = LoonCard,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "请求明细",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = LoonTextPrimary
                        )
                        Text(
                            text = timeFormat.format(req.timestamp),
                            fontSize = 12.sp,
                            color = LoonTextSecondary
                        )
                    }

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(req.destination))
                            Toast.makeText(context, "已复制请求目标", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", tint = IosBlue, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Flat detail rows with dividers
                val details = mutableListOf(
                    "请求协议" to req.protocol,
                    "目标主机" to req.destination
                )
                if (req.source.isNotBlank()) details.add("来源地址" to req.source)
                if (req.rule.isNotBlank()) details.add("匹配规则" to req.rule)
                if (req.outbound.isNotBlank()) details.add("最终出站" to req.outbound)

                details.forEachIndexed { i, (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 14.sp, color = LoonTextSecondary)
                        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = LoonTextPrimary)
                    }
                    if (i < details.size - 1) {
                        HorizontalDivider(color = LoonCardBorder, thickness = 0.5.dp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("原始链路日志", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LoonTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = req.rawMessage,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = LoonTextPrimary,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(LoonBg)
                        .padding(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(req.rawMessage))
                            Toast.makeText(context, "已复制全量日志", Toast.LENGTH_SHORT).show()
                            inspectedRequest = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("复制日志", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { inspectedRequest = null },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, LoonCardBorder),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("关闭", color = LoonTextPrimary)
                    }
                }
            }
        }
    }
}
