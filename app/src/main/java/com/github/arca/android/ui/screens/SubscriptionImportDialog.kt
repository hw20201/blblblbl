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
import com.github.arca.android.data.SubscriptionLink
import com.github.arca.android.ui.glass.GlassAlertDialog
import com.github.arca.android.ui.theme.IosBlue
import com.github.arca.android.ui.theme.LoonTextPrimary
import com.github.arca.android.ui.theme.LoonTextSecondary

/** Confirms a one-click import so a web page cannot silently add a subscription. */
@Composable
fun SubscriptionImportDialog(
    link: SubscriptionLink,
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit
) {
    var name by remember(link) { mutableStateOf(link.name) }
    val host = remember(link) { runCatching { java.net.URI(link.url).host }.getOrNull() ?: link.url }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("subscription-import-dialog"),
        title = { Text("导入订阅", fontWeight = FontWeight.Bold, color = LoonTextPrimary) },
        text = {
            Column {
                Text("来源：$host", color = LoonTextSecondary, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(link.url, color = LoonTextSecondary, fontSize = 11.5.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(64) },
                    singleLine = true,
                    label = { Text("名称（可留空自动识别）") },
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
