package com.github.arca.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.data.ConfigManager
import com.github.arca.android.data.ProfileItem
import com.github.arca.android.data.subscription.*
import com.github.arca.android.ui.glass.GlassModalBottomSheet
import com.github.arca.android.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionSourcesSheet(profile: ProfileItem, onDismiss: () -> Unit, onChanged: (ProfileItem) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember(profile.id) { mutableStateOf(profile) }
    var state by remember(profile.id) { mutableStateOf<ManagedSubscription?>(null) }
    var staged by remember(profile.id) { mutableStateOf<List<SubscriptionSource>>(emptyList()) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmRollback by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var activity by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val dirty = state?.current?.sources?.map { it.source } != staged && state != null
    val busy = activity != null

    suspend fun reload() {
        val loaded = SubscriptionManager.state(context, profile.id) ?: error("该配置没有来源版本")
        state = loaded
        staged = loaded.current.sources.map { it.source }
    }
    fun run(label: String, action: suspend () -> Unit) {
        if (busy) return
        activity = label; message = null; failed = false
        operation = scope.launch {
            try { action() }
            catch (timeout: TimeoutCancellationException) {
                if (!currentCoroutineContext().isActive) throw timeout
                message = "操作超时，当前版本未替换"; failed = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = SubscriptionManager.safeFailure(failure); failed = true }
            finally { activity = null }
        }
    }
    fun updateSource(id: String, change: (SubscriptionSource) -> SubscriptionSource) {
        staged = staged.map { if (it.id == id) change(it) else it }
    }
    fun move(index: Int, offset: Int) {
        staged = staged.toMutableList().apply { add(index + offset, removeAt(index)) }
    }
    fun refresh(id: String? = null) = run("正在刷新来源…") {
        val result = SubscriptionManager.update(context, selected, id)
        reload()
        onChanged(selected)
        val retained = result.sources.count { it.retained }
        message = if (retained > 0) "$retained 个来源沿用旧版" else if (result.changed) "新版本已保存" else "节点内容未变化"
        failed = retained > 0
    }
    fun close() {
        if (dirty && !busy) confirmDiscard = true
        else { operation?.cancel(); onDismiss() }
    }
    LaunchedEffect(profile.id) { run("正在读取来源…") { reload() } }

    if (adding) {
        SubscriptionImportSheet(onDismiss = { adding = false }, addingSources = true,
            onImported = { updated ->
                selected = updated
                onChanged(updated)
                run("正在读取来源…") { reload() }
            }, onCommit = { _, preview, _, partial ->
                SubscriptionManager.reconfigure(context, selected, staged, preview, partial)
            })
        return
    }
    GlassModalBottomSheet(onDismissRequest = ::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = LoonBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.90f).imePadding().padding(horizontal = 20.dp)) {
            val version = state?.current
            val aggregate = version?.standalone == false
            val hasRemote = staged.any { it.enabled && it.url != null }
            SheetHeading("来源与版本", profile.name, ::close)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${staged.count { it.enabled }} / ${staged.size} 个启用  ·  ${version?.nodeCount ?: 0} 个节点", color = LoonTextSecondary, fontSize = 12.sp)
                Row {
                    IconButton(enabled = hasRemote && !busy && !dirty, onClick = { refresh() }, modifier = Modifier.testTag("subscription-refresh-all")) {
                        Icon(Icons.Default.Refresh, "刷新全部来源")
                    }
                    IconButton(enabled = aggregate && staged.size < SUBSCRIPTION_MAX_SOURCES && !busy && !dirty,
                        onClick = { adding = true }) { Icon(Icons.Default.Add, "添加来源") }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = IosBlue)
            LazyColumn(Modifier.weight(1f).testTag("subscription-source-list"),
                verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                activity?.let { item { Text(it, color = LoonTextSecondary, fontSize = 12.sp) } }
                message?.let { item { Text(it, color = if (failed) IosRed else IosGreen, fontSize = 13.sp) } }
                itemsIndexed(staged, key = { _, source -> source.id }) { index, source ->
                    val snapshot = version?.sources?.firstOrNull { it.source.id == source.id }
                    SubscriptionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (source.url == null) Icons.Default.Description else Icons.Default.Cloud,
                                null, tint = IosBlue, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(source.name, color = LoonTextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${snapshot?.inspection?.formatLabel.orEmpty()} · ${snapshot?.inspection?.nodes?.size ?: 0} 个节点",
                                    color = LoonTextSecondary, fontSize = 11.sp)
                            }
                            if (aggregate) Switch(source.enabled, { value -> updateSource(source.id) { it.copy(enabled = value) } }, enabled = !busy)
                            IconButton(enabled = !busy, onClick = { expanded = if (expanded == source.id) null else source.id }) {
                                Icon(if (expanded == source.id) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "来源详情")
                            }
                        }
                        snapshot?.let {
                            Text(it.status, color = if (it.retained) IosRed else LoonTextSecondary, fontSize = 12.sp)
                            Text("上次成功 ${displayTime(it.fetchedAt)}  ·  最近检查 ${displayTime(it.attemptedAt)}", color = LoonTextSecondary, fontSize = 11.sp)
                            it.userInfo?.let(ConfigManager::parseSubscriptionUserInfo)?.let { quota ->
                                if (quota.total > 0L) {
                                    val used = quota.upload.toDouble() + quota.download.toDouble()
                                    LinearProgressIndicator(progress = { (used / quota.total).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth(), color = IosBlue)
                                    Text("流量 ${gib(used)} / ${gib(quota.total.toDouble())}", fontSize = 11.sp, color = LoonTextSecondary)
                                }
                                if (quota.expire > 0L) Text("到期 ${displayTime(quota.expire * 1_000)}", fontSize = 11.sp, color = LoonTextSecondary)
                            }
                        }
                        if (expanded == source.id) {
                            SubscriptionField(source.name, { value -> updateSource(source.id) { it.copy(name = value.take(100)) } }, "来源名称")
                            if (aggregate) {
                                SubscriptionField(source.prefix, { value -> updateSource(source.id) { it.copy(prefix = value.take(80)) } }, "节点名称前缀")
                                SubscriptionField(source.include, { value -> updateSource(source.id) { it.copy(include = value.take(240)) } }, "包含关键字（逗号分隔）")
                                SubscriptionField(source.exclude, { value -> updateSource(source.id) { it.copy(exclude = value.take(240)) } }, "排除关键字（逗号分隔）")
                            }
                            if (source.url?.startsWith("https://", true) == true) {
                                var credential by remember(source.id) { mutableStateOf("") }
                                SubscriptionField(credential, { credential = it }, "新设备令牌", password = true)
                                Row {
                                    TextButton(enabled = credential.isNotBlank() && !busy, onClick = {
                                        updateSource(source.id) { it.copy(token = credential.trim()) }; credential = ""
                                    }) { Text("更换令牌") }
                                    TextButton(enabled = source.token.isNotBlank() && !busy,
                                        onClick = { updateSource(source.id) { it.copy(token = "") } }) { Text("移除令牌") }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (aggregate) {
                                    IconButton(enabled = !busy && index > 0, onClick = { move(index, -1) }) { Icon(Icons.Default.ArrowUpward, "上移来源") }
                                    IconButton(enabled = !busy && index < staged.lastIndex, onClick = { move(index, 1) }) { Icon(Icons.Default.ArrowDownward, "下移来源") }
                                    IconButton(enabled = !busy && staged.size > 1, onClick = { staged = staged.filterNot { it.id == source.id } }) {
                                        Icon(Icons.Default.DeleteOutline, "移除来源", tint = if (!busy && staged.size > 1) IosRed else LoonTextSecondary)
                                    }
                                }
                                if (source.url != null) TextButton(enabled = !busy && !dirty && source.enabled, onClick = { refresh(source.id) }) {
                                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp)); Text("刷新此来源")
                                }
                            }
                        }
                    }
                }
                state?.previous?.let { previous ->
                    item {
                        TextButton(enabled = !busy && !dirty, onClick = { confirmRollback = true }, modifier = Modifier.testTag("subscription-rollback")) {
                            Icon(Icons.Default.Restore, null, Modifier.size(18.dp)); Text("回滚至 ${displayTime(previous.generatedAt)}")
                        }
                    }
                }
            }
            if (dirty) Button(enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("subscription-save-sources"),
                onClick = { run("正在校验新版本…") {
                    selected = SubscriptionManager.reconfigure(context, selected, staged)
                    reload(); onChanged(selected); message = "已保存来源设置"
                } }) { Text("保存来源变更") }
            if (busy) TextButton(onClick = { operation?.cancel() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("取消本次操作") }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
    if (confirmRollback) AlertDialog(onDismissRequest = { confirmRollback = false }, title = { Text("回滚到上一版本？") },
        text = { Text("上一版配置及其来源设置会恢复，当前版本仍可恢复。正在运行的连接不会被切换。") },
        confirmButton = { TextButton(onClick = {
            confirmRollback = false
            run("正在校验旧版本…") {
                selected = SubscriptionManager.rollback(context, selected)
                reload(); onChanged(selected); message = "已恢复上一版本"
            }
        }) { Text("确认回滚") } }, dismissButton = { TextButton(onClick = { confirmRollback = false }) { Text("取消") } })
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("放弃未保存的更改？") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("放弃更改") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } })
}

private fun displayTime(time: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))
private fun gib(bytes: Double) = String.format(Locale.getDefault(), "%.2f GiB", bytes / (1L shl 30))
