package com.github.arca.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.ui.glass.GlassModalBottomSheet
import com.github.arca.android.ui.theme.LoonBg
import com.github.arca.android.ui.theme.LoonTextMuted
import com.github.arca.android.ui.theme.LoonTextPrimary
import com.github.arca.android.ui.theme.LoonTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScamalyticsSettingsSheet(
    initialUsername: String,
    hasSavedCredentials: Boolean,
    saving: Boolean,
    errorMessage: String?,
    onSave: (username: String, apiKey: String) -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var username by remember(initialUsername) { mutableStateOf(initialUsername) }
    var apiKey by remember { mutableStateOf("") }

    GlassModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = LoonBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag("scamalytics-settings-sheet")
        ) {
            Text(
                text = "Scamalytics API",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = LoonTextPrimary
            )
            Text(
                text = "凭据会用 Android Keystore 在本机加密保存。保存不会发起查询；只有手动运行检测时，当前 IP 与凭据才会通过 HTTPS 发给 Scamalytics（api12）。每次查询会使用账户额度。",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = LoonTextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth().testTag("scamalytics-username"),
                enabled = !saving,
                singleLine = true,
                label = { Text("API 账号 Username") }
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth().testTag("scamalytics-api-key"),
                enabled = !saving,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("API Key") },
                supportingText = {
                    Text(
                        if (hasSavedCredentials) "已保存的密钥不会显示；替换凭据时请重新输入密钥。" else "密钥不会预览或写入应用日志。",
                        color = LoonTextMuted
                    )
                }
            )
            errorMessage?.let {
                Text(it, color = LoonTextPrimary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasSavedCredentials) {
                    TextButton(
                        onClick = onDelete,
                        enabled = !saving,
                        modifier = Modifier.testTag("scamalytics-delete-credentials")
                    ) { Text("删除") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismissRequest, enabled = !saving) {
                    Text("取消")
                }
                Button(
                    onClick = { onSave(username, apiKey) },
                    enabled = !saving && username.isNotBlank() && apiKey.isNotBlank(),
                    modifier = Modifier.testTag("scamalytics-save-credentials")
                ) {
                    Text(if (saving) "保存中…" else "加密保存")
                }
            }
        }
    }
}
