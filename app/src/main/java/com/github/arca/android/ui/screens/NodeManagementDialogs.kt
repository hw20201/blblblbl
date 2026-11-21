package com.github.arca.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.arca.android.data.ConfigManager
import com.github.arca.android.data.ProfileItem
import com.github.arca.android.ui.glass.GlassAlertDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class NodeManagementActions(
    val add: () -> Unit,
    val edit: (String) -> Unit,
    val rename: (String) -> Unit,
    val delete: (String) -> Unit
)

private enum class NodeAction(val title: String, val success: String) {
    ADD("添加独立节点", "节点已添加"), EDIT("编辑节点", "节点已保存"),
    RENAME("重命名节点", "节点已重命名"), DELETE("删除节点", "节点已删除")
}

/** Manual nodes always belong to the stable default-profile id, regardless of its display name. */
@Composable
internal fun NodeManagementDialogs(
    activeProfile: ProfileItem?,
    selectedGroupName: String,
    onSaved: (profile: ProfileItem, group: String?, oldName: String?, newName: String?, message: String, addedNodeNames: List<String>) -> Unit
): NodeManagementActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var action by rememberSaveable { mutableStateOf<NodeAction?>(null) }
    var nodeName by rememberSaveable { mutableStateOf("") }
    var input by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }
    var groups by remember { mutableStateOf(emptyList<String>()) }
    var profile by remember { mutableStateOf<ProfileItem?>(null) }
    var busy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var groupMenu by remember { mutableStateOf(false) }

    fun open(next: NodeAction, name: String = "") {
        if (busy || (next != NodeAction.ADD && activeProfile?.id != ConfigManager.DEFAULT_PROFILE_ID)) return
        nodeName = name
        input = if (next == NodeAction.RENAME) name else ""
        ready = false
        error = null
        action = next
    }

    LaunchedEffect(action, nodeName) {
        val request = action ?: return@LaunchedEffect
        ready = false
        try {
            val target = withContext(Dispatchers.IO) { ConfigManager.getOrCreateDefaultProfile(context) }
            profile = target
            if (request == NodeAction.ADD) {
                groups = ConfigManager.writableNodeGroupNames(context, target)
                group = selectedGroupName.takeIf { activeProfile?.id == target.id && it in groups }
                    ?: groups.firstOrNull().orEmpty()
            } else if (request == NodeAction.EDIT) {
                input = ConfigManager.readProfileNode(context, target.id, nodeName).getOrThrow()
            }
            ready = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "无法读取节点" }
    }

    action?.let { request ->
        GlassAlertDialog(
            onDismissRequest = { if (!busy) { action = null; groupMenu = false } },
            title = { Text(request.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("独立节点保存在「${profile?.name ?: "默认配置"}」，与远程配置分开管理。")
                    when (request) {
                        NodeAction.ADD -> {
                            Text("添加后会显示此配置中的节点列表。")
                            if (groups.size > 1) {
                                TextButton(enabled = ready && !busy, onClick = { groupMenu = true }) { Text("策略组：$group") }
                                DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                                    groups.forEach { name ->
                                        DropdownMenuItem(text = { Text(name) }, onClick = { group = name; groupMenu = false })
                                    }
                                }
                            }
                            OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth().testTag("node-add-input"),
                                enabled = ready && !busy, label = { Text("节点分享链接或 YAML") }, minLines = 4, maxLines = 8)
                        }
                        NodeAction.EDIT -> {
                            Text("修改连接参数；节点名称保持不变，可通过“重命名”单独修改。")
                            OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth().testTag("node-edit-input"),
                                enabled = ready && !busy, label = { Text("节点参数 YAML") }, minLines = 6, maxLines = 12)
                        }
                        NodeAction.RENAME -> OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth().testTag("node-rename-input"),
                            enabled = ready && !busy, singleLine = true, label = { Text("新名称") })
                        NodeAction.DELETE -> Text("删除“$nodeName”？引用此节点的规则将改用 DIRECT；此操作保存后立即生效。")
                    }
                    error?.let { Text(it, modifier = Modifier.testTag("node-operation-error")) }
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("node-operation-confirm"),
                    enabled = ready && !busy && (request == NodeAction.DELETE || input.isNotBlank()) &&
                        (request != NodeAction.RENAME || input.trim() != nodeName),
                    onClick = {
                        val target = profile ?: return@TextButton
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                val beforeNames = if (request == NodeAction.ADD) {
                                    ConfigManager.readProfileProxyNames(context, target.id).toSet()
                                } else emptySet()
                                val result = when (request) {
                                    NodeAction.ADD -> ConfigManager.appendNodeToProfile(context, target.id, input, group.ifBlank { null })
                                    NodeAction.EDIT -> ConfigManager.replaceProfileNode(context, target.id, nodeName, input)
                                    NodeAction.RENAME -> ConfigManager.renameProfileNode(context, target.id, nodeName, input.trim())
                                    NodeAction.DELETE -> ConfigManager.deleteProfileNode(context, target.id, nodeName)
                                }
                                result.fold(onSuccess = { updated ->
                                    val addedNodeNames = if (request == NodeAction.ADD) {
                                        ConfigManager.readProfileProxyNames(context, updated.id)
                                            .filterNot { it in beforeNames }.distinct()
                                    } else emptyList()
                                    action = null
                                    groupMenu = false
                                    onSaved(updated, group.takeIf { request == NodeAction.ADD },
                                        nodeName.takeIf { request == NodeAction.RENAME || request == NodeAction.DELETE },
                                        when (request) { NodeAction.RENAME -> input.trim(); NodeAction.DELETE -> "DIRECT"; else -> null },
                                        "${request.success}，已显示「${updated.name}」", addedNodeNames)
                                }, onFailure = { error = it.message ?: "节点操作失败" })
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.message ?: "节点操作失败" }
                            finally { busy = false }
                        }
                    }
                ) { Text(if (busy) "保存中…" else if (request == NodeAction.DELETE) "删除" else "保存") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { action = null; groupMenu = false }) { Text("取消") } }
        )
    }
    return NodeManagementActions({ open(NodeAction.ADD) }, { open(NodeAction.EDIT, it) },
        { open(NodeAction.RENAME, it) }, { open(NodeAction.DELETE, it) })
}
