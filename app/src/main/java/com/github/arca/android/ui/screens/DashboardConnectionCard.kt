package com.github.arca.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.components.ArcaGlasmioSwitch
import com.github.arca.android.ui.components.cardDepth
import com.github.arca.android.ui.theme.AppStrings
import com.github.arca.android.ui.theme.LocalLoonColors

/** Connection and telemetry share a card; telemetry remains a leaf of the dashboard. */
@Composable
internal fun DashboardConnectionCard(
    status: VpnState.Status,
    activeProfileName: String,
    activeNodeName: String,
    lang: String,
    uploadSpeed: String,
    downloadSpeed: String,
    onToggleVpn: () -> Unit,
    modeControl: @Composable () -> Unit
) {
    val dark = LocalLoonColors.current.isDark
    val shape = RoundedCornerShape(24.dp)
    val primary = if (dark) Color(0xFFF0F5FF) else Color(0xFF182D48)
    val secondary = if (dark) Color(0xFFB0BFD4) else Color(0xFF60738D)
    val statusKey = when (status) {
        VpnState.Status.RUNNING -> "tile_connected"
        VpnState.Status.STARTING -> "tile_connecting"
        VpnState.Status.STOPPING -> "tile_disconnecting"
        VpnState.Status.STOPPED -> "tile_disconnected"
    }
    val statusColor = when (status) {
        VpnState.Status.RUNNING -> Color(0xFF20B98D)
        VpnState.Status.STARTING, VpnState.Status.STOPPING -> Color(0xFFDD9950)
        VpnState.Status.STOPPED -> if (dark) Color(0xFF91A4C0) else Color(0xFF7188A9)
    }
    Box(
        Modifier.fillMaxWidth()
            .cardDepth(shape, dark, 6.dp)
            .clip(shape)
            .background(Brush.linearGradient(if (dark) {
                listOf(Color(0xFF1D2E46), Color(0xFF182235))
            } else {
                listOf(Color(0xFFF1F6FF), Color(0xFFE2ECFC))
            }))
            .testTag("dashboard-traffic-card")
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(AppStrings.get("dashboard_connection", lang), color = secondary,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(statusColor, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(AppStrings.get(statusKey, lang), color = primary, fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("dashboard-connection-status"))
                    }
                }
                ArcaGlasmioSwitch(
                    checked = status == VpnState.Status.RUNNING || status == VpnState.Status.STARTING,
                    onCheckedChange = { if (status != VpnState.Status.STOPPING) onToggleVpn() },
                    enabled = status != VpnState.Status.STOPPING,
                    modifier = Modifier.testTag("dashboard-vpn-switch")
                        .semantics { contentDescription = "VPN" }
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ConnectionRate(Modifier.weight(1f), AppStrings.get("traffic_up", lang), uploadSpeed,
                    true, primary, secondary)
                Box(Modifier.width(1.dp).height(36.dp).background(secondary.copy(alpha = .19f)))
                ConnectionRate(Modifier.weight(1f).padding(start = 20.dp), AppStrings.get("traffic_down", lang),
                    downloadSpeed, false, primary, secondary)
            }
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = secondary.copy(alpha = .15f), thickness = .5.dp)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Layers, contentDescription = null, tint = secondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("${AppStrings.get("shortcut_profiles", lang)} · " +
                    listOf(activeProfileName.ifBlank { AppStrings.get("dashboard_no_profile", lang) },
                        activeNodeName).filter { it.isNotBlank() }.joinToString(" · "),
                    color = secondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("dashboard-connection-route"))
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                modeControl()
            }
        }
    }
}

@Composable
private fun ConnectionRate(
    modifier: Modifier,
    label: String,
    speed: String,
    upload: Boolean,
    primary: Color,
    secondary: Color
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (upload) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null, tint = if (upload) Color(0xFF25B993) else Color(0xFF448FFF),
                modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(5.dp))
        Text(speed.ifBlank { "--" }, color = primary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(if (upload) "traffic-upload-rate" else "traffic-download-rate"))
    }
}
