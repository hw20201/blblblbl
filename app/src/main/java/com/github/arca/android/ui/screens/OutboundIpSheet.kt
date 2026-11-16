package com.github.arca.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.arca.android.data.OutboundIpInfo
import com.github.arca.android.ui.glass.GlassModalBottomSheet
import com.github.arca.android.ui.theme.LoonBg
import com.github.arca.android.ui.theme.LoonTextPrimary

@Composable
fun OutboundIpSheet(isVpnRunning: Boolean, onDismiss: () -> Unit = {}) {
    OutboundIpGlassSheet(isVpnRunning = isVpnRunning, onDismiss = onDismiss)
}

/** Injectable manual lookup for previews/tests; never called simply by opening the sheet. */
@Composable
fun OutboundIpSheet(onRefresh: suspend () -> OutboundIpInfo, onDismiss: () -> Unit = {}) {
    OutboundIpGlassSheet(isVpnRunning = false, onDismiss = onDismiss, onRefresh = onRefresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutboundIpGlassSheet(
    isVpnRunning: Boolean,
    onDismiss: () -> Unit,
    onRefresh: (suspend () -> OutboundIpInfo)? = null
) {
    val state = remember { OutboundIpLookupState() }
    val close = { state.cancel(); onDismiss() }
    // The Scamalytics API entry lives outside the scrolling column, pinned to the
    // sheet's bottom-right corner; the scroll content keeps extra bottom padding
    // so the link never covers the last rows.
    val settingsOpen = remember { mutableStateOf(false) }
    var configLinkEnabled by remember { mutableStateOf(false) }
    GlassModalBottomSheet(
        onDismissRequest = close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LoonBg
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("出站 IP", color = LoonTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = close) { Text("关闭") }
                }
                OutboundIpPanel(
                    isVpnRunning = isVpnRunning,
                    state = state,
                    onRefresh = onRefresh,
                    settingsOpenState = settingsOpen,
                    onConfigLinkAvailabilityChanged = { configLinkEnabled = it }
                )
            }
            ScamalyticsConfigLink(
                onClick = { settingsOpen.value = true },
                enabled = configLinkEnabled,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 6.dp)
            )
        }
    }
}
