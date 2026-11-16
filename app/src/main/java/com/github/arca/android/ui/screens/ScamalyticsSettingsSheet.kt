package com.github.arca.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.data.ScamalyticsCredentials
import com.github.arca.android.data.ScamalyticsEndpoint
import com.github.arca.android.data.readScamalyticsCredentials
import com.github.arca.android.ui.glass.GlassModalBottomSheet
import com.github.arca.android.ui.theme.LoonBg
import com.github.arca.android.ui.theme.LoonTextMuted
import com.github.arca.android.ui.theme.LoonTextPrimary
import com.github.arca.android.ui.theme.LoonTextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScamalyticsSettingsSheet(
    initialUsername: String,
    hasSavedCredentials: Boolean,
    saving: Boolean,
    errorMessage: String?,
    onSave: (ScamalyticsCredentials) -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
    initialEndpoint: ScamalyticsEndpoint = ScamalyticsEndpoint.US
) {
    var username by remember(initialUsername) { mutableStateOf(initialUsername) }
    var apiKey by remember { mutableStateOf("") }
    var endpoint by remember(initialEndpoint) { mutableStateOf(initialEndpoint) }
    var endpointMenu by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy = saving || importing
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            importMessage = null
            try {
                val imported = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(::readScamalyticsCredentials)
                        ?: error("Unavailable credential file")
                }
                username = imported.username
                apiKey = imported.apiKey
                endpoint = imported.endpoint
                importMessage = "已读取凭据，请点击加密保存。"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                importMessage = "无法导入：请选择不超过 16 KiB 的 JSON，包含 username、api_key 与 api11/api12 endpoint。"
            } finally {
                importing = false
            }
        }
    }

    GlassModalBottomSheet(
        onDismissRequest = { if (!busy) onDismissRequest() },
        containerColor = LoonBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .imePadding().navigationBarsPadding().padding(horizontal = 20.dp)
                .padding(bottom = 24.dp).testTag("scamalytics-settings-sheet")
        ) {
            Text("Scamalytics API", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LoonTextPrimary)
            Text(
                "凭据用 Android Keystore 在本机加密保存。保存不会查询；仅在手动检测时，通过 HTTPS 向所选节点发送当前 IP 与凭据。每次实时查询使用账户额度。",
                fontSize = 12.sp, lineHeight = 18.sp, color = LoonTextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            TextButton(
                onClick = { importer.launch("application/json") }, enabled = !busy,
                modifier = Modifier.testTag("scamalytics-import-credentials")
            ) { Text(if (importing) "读取中…" else "导入凭据 JSON（≤16 KiB）") }
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth().testTag("scamalytics-username"),
                enabled = !busy, singleLine = true, label = { Text("API 账号 Username") }
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth().testTag("scamalytics-api-key"),
                enabled = !busy, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), label = { Text("API Key") },
                supportingText = {
                    Text(if (hasSavedCredentials) "已保存的密钥不会显示；替换时请重新输入或导入。" else "密钥不会预览或写入应用日志。", color = LoonTextMuted)
                }
            )
            Box {
                TextButton(
                    onClick = { endpointMenu = true }, enabled = !busy,
                    modifier = Modifier.testTag("scamalytics-endpoint")
                ) { Text("API 节点：${endpoint.label} ▾") }
                DropdownMenu(expanded = endpointMenu, onDismissRequest = { endpointMenu = false }) {
                    ScamalyticsEndpoint.entries.forEach { item ->
                        DropdownMenuItem(text = { Text(item.label) }, onClick = { endpoint = item; endpointMenu = false })
                    }
                }
            }
            Text("节点必须与账号注册时一致；默认 api11（US）。", color = LoonTextMuted, fontSize = 12.sp)
            (errorMessage ?: importMessage)?.let {
                Text(it, color = LoonTextPrimary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasSavedCredentials) {
                    TextButton(onClick = onDelete, enabled = !busy,
                        modifier = Modifier.testTag("scamalytics-delete-credentials")) { Text("删除") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismissRequest, enabled = !busy) { Text("取消") }
                Button(
                    onClick = { onSave(ScamalyticsCredentials(username, apiKey, endpoint)) },
                    enabled = !busy && username.isNotBlank() && apiKey.isNotBlank(),
                    modifier = Modifier.testTag("scamalytics-save-credentials")
                ) { Text(if (saving) "保存中…" else "加密保存") }
            }
        }
    }
}
