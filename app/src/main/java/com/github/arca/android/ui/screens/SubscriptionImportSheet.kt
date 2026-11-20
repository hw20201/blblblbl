package com.github.arca.android.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.data.ConfigManager
import com.github.arca.android.data.ProfileItem
import com.github.arca.android.data.subscription.*
import com.github.arca.android.ui.components.AppNotice
import com.github.arca.android.ui.glass.GlassModalBottomSheet
import com.github.arca.android.ui.glass.GlassSurface
import com.github.arca.android.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionImportSheet(
    onDismiss: () -> Unit,
    onImported: (ProfileItem) -> Unit,
    initial: SourceDraft? = null,
    addingSources: Boolean = false,
    onCommit: (suspend (String, ImportPreview, Boolean, Boolean) -> ProfileItem)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var label by remember { mutableStateOf(initial?.name ?: "") }
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var token by remember { mutableStateOf(initial?.token ?: "") }
    var mode by remember { mutableStateOf(initial?.mode ?: "auto") }
    var remote by remember { mutableStateOf(initial?.remote) }
    var queue by remember { mutableStateOf(emptyList<SourceDraft>()) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var aggregate by remember { mutableStateOf<AggregationOutput?>(null) }
    var generationError by remember { mutableStateOf<String?>(null) }
    var standalone by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var work by remember { mutableStateOf<Job?>(null) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    fun draft() = SourceDraft(label.ifBlank { "来源 ${queue.size + 1}" }, text, mode, token, remote)
    fun close() { work?.cancel(); onDismiss() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) work = scope.launch {
            try {
                val selected = withContext(Dispatchers.IO) { ConfigManager.readProfileDraft(context, uri) }
                text = selected.text; label = selected.name; remote = false
                if (name.isBlank()) name = selected.name
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure = "文件读取失败，请检查大小与 UTF-8 编码" }
        }
    }
    GlassModalBottomSheet(onDismissRequest = ::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = LoonBg,
        heightFraction = 0.92f, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            SheetHeading(if (addingSources) "添加来源" else "添加配置",
                if (preview == null) "选择来源  ·  01 / 02" else "检查结果  ·  02 / 02", ::close)
            LazyColumn(Modifier.weight(1f).testTag("subscription-import-content"),
                verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                if (preview == null) {
                    item {
                        SubscriptionCard {
                            Text("订阅、节点，一处导入", fontWeight = FontWeight.SemiBold, color = LoonTextPrimary)
                            Text("支持 URL、Mihomo 配置、节点 YAML、分享链接和 Base64。先检查，确认后保存。",
                                color = LoonTextSecondary, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { picker.launch("*/*") }, modifier = Modifier.weight(1f).testTag("profile-add-local-file")) {
                                    Icon(Icons.Default.UploadFile, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("本地文件")
                                }
                                OutlinedButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    text = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                    remote = null
                                }, modifier = Modifier.weight(1f).testTag("profile-add-clipboard")) {
                                    Icon(Icons.Default.ContentPaste, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("粘贴")
                                }
                            }
                        }
                    }
                    item { SubscriptionField(name, { name = it.take(100) }, "配置名称", "profile-add-name") }
                    item { SubscriptionField(label, { label = it.take(100) }, "当前来源别名（选填）") }
                    item { SubscriptionField(text, { text = it; remote = null }, "订阅 URL 或节点内容", "profile-add-url", singleLine = false) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(listOf("auto" to "自动识别", "yaml" to "YAML", "uri" to "节点链接", "base64" to "Base64", "proxy" to "HTTP / SOCKS 代理")) { (value, title) ->
                                FilterChip(selected = mode == value, onClick = { mode = value; if (value == "proxy") remote = false }, label = { Text(title) })
                            }
                        }
                    }
                    item {
                        SubscriptionField(token, { token = it }, "自有云端设备令牌（选填）", password = true)
                        Text("令牌仅通过 HTTPS 请求头发送。已有 Sub-Store 等服务的 Mihomo 输出可直接作为来源。",
                            color = LoonTextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    items(queue.indices.toList()) { index ->
                        SubscriptionCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Layers, null, tint = IosBlue, modifier = Modifier.size(18.dp))
                                Text(queue[index].name, Modifier.weight(1f).padding(start = 8.dp), color = LoonTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(enabled = !busy, onClick = {
                                    val selected = queue[index]
                                    val remaining = queue.filterIndexed { i, _ -> i != index }
                                    queue = if (text.isNotBlank()) remaining + draft() else remaining
                                    label = selected.name; text = selected.text; token = selected.token
                                    mode = selected.mode; remote = selected.remote; failure = null
                                }) { Icon(Icons.Default.Edit, "编辑来源", tint = IosBlue) }
                                IconButton(enabled = !busy, onClick = { queue = queue.filterIndexed { i, _ -> i != index } }) { Icon(Icons.Default.Close, "移除来源", tint = LoonTextSecondary) }
                            }
                        }
                    }
                    item {
                        TextButton(enabled = text.isNotBlank() && queue.size < SUBSCRIPTION_MAX_SOURCES - 1, onClick = {
                            queue = queue + draft(); text = ""; label = ""; token = ""; mode = "auto"; remote = null
                        }) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text("再加一个来源  ·  ${queue.size + if (text.isBlank()) 0 else 1} / 20") }
                    }
                } else {
                    val result = requireNotNull(preview)
                    item {
                        SubscriptionCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                CountLabel("可用节点", result.nodes.toString(), IosBlue)
                                CountLabel("完全重复", aggregate?.duplicates?.toString() ?: "—", IosGreen)
                                CountLabel("未通过", result.rejected.toString(), if (result.rejected > 0) IosRed else LoonTextSecondary)
                            }
                            Text("已完成格式解析和同版本核心校验；连通性尚未测试。", fontSize = 11.sp, color = LoonTextSecondary)
                        }
                    }
                    if (!addingSources && result.sources.size == 1 && result.sources[0].inspection.completeValid) item {
                        SubscriptionCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(standalone, { standalone = true }); Text("保留完整配置", color = LoonTextPrimary)
                                RadioButton(!standalone, { standalone = false }); Text("提取节点", color = LoonTextPrimary)
                            }
                            Text(if (standalone) "保留原有分组、规则和 provider。" else "使用聚合的基础分组与规则；不合并来源的 DNS 或脚本。", fontSize = 12.sp, color = LoonTextSecondary)
                        }
                    }
                    if (!standalone) item {
                        Text(aggregate?.let { "生成 ${it.nodes} 个唯一节点 · 全部节点 + 按来源分组" } ?: generationError.orEmpty(),
                            color = if (aggregate == null) IosRed else LoonTextSecondary, fontSize = 12.sp)
                    }
                    items(result.failures) { DiagnosticLine(it) }
                    result.sources.forEach { source ->
                        item(key = source.source.id) {
                            SubscriptionCard {
                                Text(source.source.name, color = LoonTextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("${source.inspection.formatLabel}  ·  ${source.inspection.nodes.size} 个节点", color = IosBlue, fontSize = 12.sp)
                                Text(source.inspection.nodes.groupingBy { it.type }.eachCount().entries.joinToString("   ") { "${it.key} ${it.value}" }, color = LoonTextSecondary, fontSize = 11.sp)
                                source.inspection.warnings.distinct().forEach { Text(it, color = LoonTextSecondary, fontSize = 12.sp) }
                                TextButton(onClick = { expanded = if (source.source.id in expanded) expanded - source.source.id else expanded + source.source.id }) {
                                    Text(if (source.source.id in expanded) "收起节点" else "查看节点和协议")
                                }
                            }
                        }
                        items(source.inspection.diagnostics) { DiagnosticLine(it) }
                        if (source.source.id in expanded) items(source.inspection.nodes) { node ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = IosGreen, modifier = Modifier.size(16.dp))
                                Text(node.name, Modifier.weight(1f).padding(horizontal = 8.dp), color = LoonTextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(node.type, color = LoonTextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                    if (result.rejected > 0) item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(partial, { partial = it }); Text("我已检查原因，仅导入通过校验的项目", color = LoonTextPrimary, fontSize = 13.sp)
                        }
                    }
                    item { TextButton(onClick = { preview = null; partial = false }) { Icon(Icons.Default.ArrowBack, null, Modifier.size(16.dp)); Text("返回修改来源") } }
                }
                failure?.let { message -> item { Text(message, color = IosRed, fontSize = 12.sp, modifier = Modifier.testTag("subscription-import-error")) } }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp), color = IosBlue)
            Button(enabled = !busy && (if (preview == null) text.isNotBlank() || queue.isNotEmpty() else
                (standalone || aggregate != null) && (preview!!.rejected == 0 || partial)),
                onClick = {
                    work = scope.launch {
                        busy = true; failure = null
                        try {
                            if (preview == null) {
                                val all = queue + if (text.isNotBlank()) listOf(draft()) else emptyList()
                                val inspected = SubscriptionManager.preview(context, all)
                                val generated = withContext(Dispatchers.Default) { runCatching { SubscriptionAggregator.generate(inspected.sources) } }
                                aggregate = generated.getOrNull()
                                generationError = generated.exceptionOrNull()?.let { error ->
                                    SubscriptionManager.safeFailure(error as? Exception ?: IllegalStateException("聚合配置生成失败"))
                                }
                                queue = all; text = ""; label = ""; token = ""; remote = null
                                standalone = !addingSources && inspected.sources.singleOrNull()?.inspection?.completeValid == true
                                preview = inspected
                            } else {
                                val title = name.ifBlank { preview!!.sources.firstOrNull()?.source?.name ?: "聚合配置" }
                                val imported = onCommit?.invoke(title, preview!!, standalone, partial)
                                    ?: SubscriptionManager.create(context, title, preview!!, standalone, partial)
                                onImported(imported); onDismiss()
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { failure = SubscriptionManager.safeFailure(error) }
                        finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth().height(50.dp).testTag("profile-add-submit"),
                shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = IosBlue)) {
                Text(if (busy) "正在检查…" else if (preview == null) "检查并预览" else if (addingSources) "确认添加来源" else "确认保存配置", fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { if (busy) { work?.cancel(); busy = false } else close() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (busy) "取消本次操作" else "取消", color = LoonTextSecondary)
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun DiagnosticLine(diagnostic: ImportDiagnostic) {
    SubscriptionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.ErrorOutline, null, tint = IosRed, modifier = Modifier.size(18.dp))
            Column {
                Text(if (diagnostic.line > 0) "第 ${diagnostic.line} 行" else if (diagnostic.index > 0) "第 ${diagnostic.index} 项" else "配置检查", color = IosRed, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                Text(diagnostic.message, color = LoonTextPrimary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun SubscriptionCard(content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun SubscriptionField(value: String, onValue: (String) -> Unit, label: String,
    tag: String = "", password: Boolean = false, singleLine: Boolean = true) {
    TextField(value, onValue, label = { Text(label, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth().testTag(tag), singleLine = singleLine,
        minLines = if (singleLine) 1 else 3, maxLines = if (singleLine) 1 else 6,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(14.dp), colors = TextFieldDefaults.colors(
            focusedContainerColor = LoonTextPrimary.copy(alpha = 0.07f), unfocusedContainerColor = LoonTextPrimary.copy(alpha = 0.05f),
            focusedIndicatorColor = IosBlue, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = LoonTextPrimary, unfocusedTextColor = LoonTextPrimary, cursorColor = IosBlue))
}

@Composable
internal fun SheetHeading(title: String, subtitle: String, close: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = LoonTextPrimary)
            Text(subtitle, fontSize = 12.sp, color = LoonTextSecondary, modifier = Modifier.padding(top = 3.dp))
        }
        IconButton(onClick = close) { Icon(Icons.Default.Close, "关闭", tint = LoonTextSecondary) }
    }
}

@Composable
private fun CountLabel(title: String, value: String, color: Color) {
    Column { Text(value, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = color); Text(title, fontSize = 11.sp, color = LoonTextSecondary) }
}

internal fun subscriptionTime(value: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
