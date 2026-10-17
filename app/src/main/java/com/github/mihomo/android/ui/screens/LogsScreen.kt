package com.github.mihomo.android.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.github.mihomo.android.data.LogRepository
import com.github.mihomo.android.ui.components.bounceOverscroll
import com.github.mihomo.android.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.github.mihomo.android.ui.components.rememberHapticAction
import com.github.mihomo.android.ui.components.hapticClickable

@Composable
fun LogsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val clipboardManager = LocalClipboardManager.current
    val logs by LogRepository.logsFlow.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedLevel by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var autoScrollEnabled by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val filteredLogs = remember(logs, selectedLevel, searchQuery) {
        logs.filter { entry ->
            val matchLevel = if (selectedLevel == "ALL") true else entry.level.equals(selectedLevel, ignoreCase = true)
            val matchSearch = if (searchQuery.isBlank()) true else entry.message.contains(searchQuery, ignoreCase = true)
            matchLevel && matchSearch
        }
    }

    // Auto-scroll to bottom only when autoScrollEnabled is true
    LaunchedEffect(filteredLogs.size, autoScrollEnabled) {
        if (autoScrollEnabled && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LoonBg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 20.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = rememberHapticAction(onBack), modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = AppStrings.get("back", lang),
                        tint = LoonTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = AppStrings.get("logs_title", lang),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = LoonTextPrimary
                    )
                    Text(
                        text = "${filteredLogs.size} 条记录",
                        fontSize = 11.5.sp,
                        color = LoonTextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Auto scroll pause/resume toggle
                IconButton(
                    onClick = rememberHapticAction({ autoScrollEnabled = !autoScrollEnabled }),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (autoScrollEnabled) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = if (autoScrollEnabled) "暂停自动滚动" else "恢复自动滚动",
                        tint = if (autoScrollEnabled) LoonBlue else LoonOrange
                    )
                }

                // Search toggle
                IconButton(
                    onClick = rememberHapticAction({
                        isSearchVisible = !isSearchVisible
                        if (!isSearchVisible) searchQuery = ""
                    }),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索日志",
                        tint = if (isSearchVisible || searchQuery.isNotBlank()) LoonBlue else LoonTextSecondary
                    )
                }

                // Copy all logs
                IconButton(
                    onClick = rememberHapticAction({
                        val text = filteredLogs.joinToString("\n") {
                            "[${timeFormat.format(Date(it.timestamp))}] [${it.level.uppercase()}] ${it.message}"
                        }
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(context, AppStrings.get("logs_copied", lang), Toast.LENGTH_SHORT).show()
                    }),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = AppStrings.get("logs_copy", lang),
                        tint = LoonTextSecondary
                    )
                }

                // Export/Share logs
                IconButton(
                    onClick = rememberHapticAction({
                        val text = filteredLogs.joinToString("\n") {
                            "[${timeFormat.format(Date(it.timestamp))}] [${it.level.uppercase()}] ${it.message}"
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, AppStrings.get("logs_export", lang)))
                    }),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = AppStrings.get("logs_export", lang),
                        tint = LoonTextSecondary
                    )
                }

                // Clear logs
                IconButton(
                    onClick = rememberHapticAction({
                        LogRepository.clear()
                        Toast.makeText(context, "日志已清空", Toast.LENGTH_SHORT).show()
                    }),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = AppStrings.get("logs_clear", lang),
                        tint = LoonTextSecondary
                    )
                }
            }
        }

        // Search Input Field (Collapsible)
        AnimatedVisibility(
            visible = isSearchVisible,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(150)) + fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索日志内容...", fontSize = 13.sp, color = LoonTextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LoonBlue,
                    unfocusedBorderColor = LoonCardBorder,
                    focusedContainerColor = LoonCard,
                    unfocusedContainerColor = LoonCard,
                    focusedTextColor = LoonTextPrimary,
                    unfocusedTextColor = LoonTextPrimary
                ),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = rememberHapticAction({ searchQuery = "" }), modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "清除", tint = LoonTextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )
        }

        // Level Filter Tabs (Clean, minimal, no bulky box chips)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val levels = listOf(
                "ALL" to AppStrings.get("log_level_all", lang),
                "INFO" to AppStrings.get("log_level_info", lang),
                "WARNING" to AppStrings.get("log_level_warning", lang),
                "ERROR" to AppStrings.get("log_level_error", lang),
                "DEBUG" to AppStrings.get("log_level_debug", lang)
            )
            levels.forEach { (lvl, title) ->
                val isSelected = selectedLevel == lvl
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) LoonEditBlueBg else LoonCard,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) LoonBlue else LoonCardBorder
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .hapticClickable { selectedLevel = lvl }
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) LoonBlue else LoonTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Logs Output Console Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                color = LoonCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, LoonCardBorder)
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = LoonTextMuted.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "未匹配到相关日志" else AppStrings.get("logs_empty", lang),
                                color = LoonTextMuted,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .bounceOverscroll()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { log ->
                            CleanLogItemRow(
                                log = log,
                                timeStr = timeFormat.format(Date(log.timestamp)),
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString("[${timeFormat.format(Date(log.timestamp))}] [${log.level.uppercase()}] ${log.message}"))
                                    Toast.makeText(context, "已复制单条日志", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            // Floating "Jump to Bottom" button when auto-scroll is disabled
            if (!autoScrollEnabled && filteredLogs.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = LoonCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LoonCardBorder),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .clip(CircleShape)
                        .hapticClickable {
                            autoScrollEnabled = true
                            scope.launch {
                                listState.animateScrollToItem(filteredLogs.size - 1)
                            }
                        }
                ) {
                    Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "滚到底部",
                            tint = LoonBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanLogItemRow(
    log: LogEntry,
    timeStr: String,
    onCopy: () -> Unit
) {
    val levelColor = when (log.level.lowercase()) {
        "error" -> RedDanger
        "warning", "warn" -> YellowWarning
        "debug" -> Color(0xFF8B5CF6)
        else -> LoonBlue
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .hapticClickable { onCopy() }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = timeStr,
            color = LoonTextMuted,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 1.5.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = log.level.uppercase(),
            color = levelColor,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 1.5.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = log.message,
            color = LoonTextPrimary,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
