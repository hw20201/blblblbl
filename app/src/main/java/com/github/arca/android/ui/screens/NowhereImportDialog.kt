package com.github.arca.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.data.NowhereNodeProfile
import com.github.arca.android.ui.glass.GlassAlertDialog
import com.github.arca.android.ui.theme.IosBlue
import com.github.arca.android.ui.theme.LoonTextPrimary
import com.github.arca.android.ui.theme.LoonTextSecondary

/** Explicitly confirms an NW3 node import without displaying its credential-bearing URL. */
@Composable
fun NowhereImportDialog(
    node: NowhereNodeProfile,
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit
) {
    var name by remember(node) { mutableStateOf(node.displayName) }
    val transport = if (node.carrier == "udp") "QUIC / UDP" else "TLS / TCP"
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("nowhere-import-dialog"),
        title = { Text("导入 Nowhere 节点", fontWeight = FontWeight.Bold, color = LoonTextPrimary) },
        text = {
            Column {
                Text(
                    "服务器：${node.endpointHost}",
                    color = LoonTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text("传输：$transport", color = LoonTextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "连接由官方 Vector runtime 建立；证书校验失败时不会跳过校验。",
                    color = LoonTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.filterNot(Char::isISOControl).take(64) },
                    singleLine = true,
                    label = { Text("节点名称") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }) {
                Text("导入", color = IosBlue, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = LoonTextSecondary) }
        }
    )
}
