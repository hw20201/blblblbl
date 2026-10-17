package com.github.mihomo.android.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mihomo.android.core.ClashCore
import com.github.mihomo.android.data.ScriptItem
import com.github.mihomo.android.data.ScriptManager
import com.github.mihomo.android.data.SettingsManager
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.components.bounceOverscroll
import com.github.mihomo.android.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    scrollState: ScrollState,
    tunStack: String,
    bootOnStartup: Boolean,
    allowLan: Boolean,
    fakeIpEnabled: Boolean,
    hapticEnabled: Boolean,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onTunStackChanged: (String) -> Unit,
    onBootOnStartupChanged: (Boolean) -> Unit,
    onAllowLanChanged: (Boolean) -> Unit,
    onFakeIpEnabledChanged: (Boolean) -> Unit,
    onHapticEnabledChanged: (Boolean) -> Unit,
    onScriptsChanged: () -> Unit = {},
    onNavigateToScripts: () -> Unit = {},
    onNavigateToProfiles: () -> Unit,
    onNavigateToAppRouting: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToDns: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()

    var showTunDialog by remember { mutableStateOf(false) }
    var showLogLevelDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var currentLogLevel by remember { mutableStateOf(settingsManager.logLevel) }

    var scripts by remember { mutableStateOf(emptyList<ScriptItem>()) }
    var coreVersion by remember { mutableStateOf("") }

    // SharedPreferences + JSON decoding and the native version query are not composition work.
    LaunchedEffect(Unit) {
        scripts = withContext(Dispatchers.IO) { ScriptManager.getScripts(context) }
        coreVersion = withContext(Dispatchers.IO) { ClashCore.getCoreVersion() }
    }

    fun refreshScripts() {
        scripts = ScriptManager.getScripts(context)
        onScriptsChanged()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LoonBg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        // Top Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppStrings.get("settings_title", lang),
                color = LoonTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Scrollable content area below Top Title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .bounceOverscroll()
                .verticalScroll(scrollState)
                .padding(bottom = 110.dp)
        ) {
            // Section 1: 基础网络与 TUN
            LoonSectionHeader(title = AppStrings.get("settings_core_service", lang))
        LoonGroupCard {
            LoonSettingsItem(
                icon = Icons.Default.VpnKey,
                iconBg = Color(0xFF3B82F6),
                title = AppStrings.get("settings_tun_stack", lang),
                value = when (tunStack) {
                    "mips" -> "Mips"
                    "mixed" -> "Mixed"
                    "gvisor" -> "gVisor"
                    else -> "System"
                },
                showChevron = true,
                onClick = { showTunDialog = true }
            )
            LoonDivider()
            LoonSettingsSwitchItem(
                icon = Icons.Default.PowerSettingsNew,
                iconBg = Color(0xFF10B981),
                title = AppStrings.get("settings_boot_startup", lang),
                checked = bootOnStartup,
                onCheckedChange = onBootOnStartupChanged
            )
            LoonDivider()
            LoonSettingsItem(
                icon = Icons.Default.Security,
                iconBg = Color(0xFFF59E0B),
                title = AppStrings.get("settings_always_on_vpn", lang),
                value = "",
                showChevron = true,
                onClick = {
                    runCatching {
                        val intent = Intent(Settings.ACTION_VPN_SETTINGS)
                        context.startActivity(intent)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section: 外观与个性化
        LoonSectionHeader(title = AppStrings.get("settings_general", lang))
        LoonGroupCard {
            LoonSettingsItem(
                icon = Icons.Default.Palette,
                iconBg = Color(0xFF8B5CF6),
                title = AppStrings.get("settings_appearance", lang),
                value = when (themeMode) {
                    ThemeMode.SYSTEM -> AppStrings.get("theme_system", lang)
                    ThemeMode.LIGHT -> AppStrings.get("theme_light", lang)
                    ThemeMode.DARK -> AppStrings.get("theme_dark", lang)
                },
                showChevron = true,
                onClick = onNavigateToAppearance
            )
            LoonDivider()
            LoonSettingsSwitchItem(
                icon = Icons.Default.Vibration,
                iconBg = Color(0xFF14B8A6),
                title = AppStrings.get("settings_haptic", lang),
                checked = hapticEnabled,
                onCheckedChange = onHapticEnabledChanged
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 2: 脚本
        LoonSectionHeader(title = AppStrings.get("settings_scripts", lang))
        LoonGroupCard {
            LoonSettingsItem(
                icon = Icons.Default.Code,
                iconBg = Color(0xFF3B82F6),
                title = AppStrings.get("settings_scripts", lang),
                value = "${scripts.size}",
                showChevron = true,
                onClick = onNavigateToScripts
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 3: 分流与规则
        LoonSectionHeader(title = AppStrings.get("settings_routing_section", lang))
        LoonGroupCard {
            LoonSettingsItem(
                icon = Icons.Default.Apps,
                iconBg = Color(0xFF8B5CF6),
                title = AppStrings.get("settings_app_routing", lang),
                value = "",
                showChevron = true,
                onClick = onNavigateToAppRouting
            )
            LoonDivider()
            LoonSettingsSwitchItem(
                icon = Icons.Default.Share,
                iconBg = Color(0xFFEC4899),
                title = AppStrings.get("settings_lan_share", lang),
                checked = allowLan,
                onCheckedChange = onAllowLanChanged
            )
            LoonDivider()
            LoonSettingsItem(
                icon = Icons.Default.FolderOpen,
                iconBg = Color(0xFF3B82F6),
                title = AppStrings.get("settings_profiles_subs", lang),
                value = "",
                showChevron = true,
                onClick = onNavigateToProfiles
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 4: DNS 设置
        LoonSectionHeader(title = AppStrings.get("settings_dns_section", lang))
        LoonGroupCard {
            LoonSettingsSwitchItem(
                icon = Icons.Default.Dns,
                iconBg = Color(0xFF0284C7),
                title = AppStrings.get("settings_fakeip", lang),
                checked = fakeIpEnabled,
                onCheckedChange = onFakeIpEnabledChanged
            )
            LoonDivider()
            LoonSettingsItem(
                icon = Icons.Default.Public,
                iconBg = Color(0xFF0D9488),
                title = AppStrings.get("dns_screen_title", lang),
                value = "上游服务器与记录",
                showChevron = true,
                onClick = onNavigateToDns
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 5: 内核与日志
        LoonSectionHeader(title = AppStrings.get("settings_core_section", lang))
        LoonGroupCard {
            LoonSettingsItem(
                icon = Icons.Default.Terminal,
                iconBg = Color(0xFF6366F1),
                title = AppStrings.get("settings_realtime_log", lang),
                value = AppStrings.get("settings_view_stream", lang),
                showChevron = true,
                onClick = onNavigateToLogs
            )
            LoonDivider()
            LoonSettingsItem(
                icon = Icons.Default.Tune,
                iconBg = Color(0xFF8B5CF6),
                title = AppStrings.get("settings_log_level", lang),
                value = currentLogLevel.uppercase(),
                showChevron = true,
                onClick = { showLogLevelDialog = true }
            )
            LoonDivider()
            LoonSettingsItem(
                icon = Icons.Default.Info,
                iconBg = Color(0xFF4B5563),
                title = AppStrings.get("settings_core_version", lang),
                value = coreVersion.ifBlank { "v1.18.x" },
                showChevron = false
            )
            LoonDivider()
            LoonSettingsItem(
                icon = Icons.Default.Memory,
                iconBg = Color(0xFF6B7280),
                title = AppStrings.get("settings_arch", lang),
                value = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
                showChevron = false
            )
            LoonDivider()
            LoonSettingsItem(
                icon = Icons.Default.RestartAlt,
                iconBg = Color(0xFFEF4444),
                title = AppStrings.get("settings_reset_core", lang),
                value = "",
                showChevron = true,
                onClick = { showResetDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section: 关于
        LoonSectionHeader(title = AppStrings.get("settings_about", lang))
        LoonGroupCard {
            LoonSettingsItem(
                icon = Icons.Default.Info,
                iconBg = LoonBlue,
                title = "应用版本",
                value = "1.0.1-Beta.3",
                showChevron = false
            )
        }
    }
}

    // Core Reset Confirmation -- the native reset can block, so it runs off the main thread.
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(AppStrings.get("settings_reset_core", lang)) },
            text = { Text(AppStrings.get("settings_reset_core_confirm", lang)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) { ClashCore.reset() }
                    }
                }) {
                    Text(AppStrings.get("confirm", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(AppStrings.get("cancel", lang))
                }
            }
        )
    }
    // Log Level Selection Dialog
    if (showLogLevelDialog) {
        val levels = listOf(
            "debug" to "Debug (详细调试)",
            "info" to "Info (默认常规)",
            "warning" to "Warning (仅警告)",
            "error" to "Error (仅错误)",
            "silent" to "Silent (静默不输出)"
        )
        AlertDialog(
            onDismissRequest = { showLogLevelDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = LoonCard,
            title = {
                Text(AppStrings.get("settings_log_level", lang), fontWeight = FontWeight.Bold, color = LoonTextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    levels.forEach { (lvl, label) ->
                        val isSelected = currentLogLevel == lvl
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) LoonBlue else LoonCardBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    currentLogLevel = lvl
                                    settingsManager.logLevel = lvl
                                    showLogLevelDialog = false
                                },
                            color = LoonCard
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) LoonBlue else LoonTextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(LoonBlue)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLogLevelDialog = false }) {
                    Text(AppStrings.get("cancel", lang), color = LoonTextSecondary)
                }
            }
        )
    }

    // TUN Stack Selection Dialog
    if (showTunDialog) {
        AlertDialog(
            onDismissRequest = { showTunDialog = false },
            title = { Text(AppStrings.get("settings_tun_stack", lang), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("mips" to "Mips (MetaCubeX 自研栈，性能优先)", "mixed" to "Mixed (混合栈，兼顾兼容与性能)", "gvisor" to "gVisor (用户态栈，高隔离)").forEach { (key, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTunStackChanged(key)
                                    showTunDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = tunStack == key, onClick = {
                                onTunStackChanged(key)
                                showTunDialog = false
                            })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(desc, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTunDialog = false }) {
                    Text(AppStrings.get("confirm", lang))
                }
            },
            containerColor = LoonCard,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// -----------------------------------------------------------------------------
// Helper UI Components
// -----------------------------------------------------------------------------
@Composable
private fun LoonSectionHeader(title: String) {
    Text(
        text = title,
        color = LoonTextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun LoonGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(0.5.dp, LoonCardBorder.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
        color = LoonCard,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            content = content
        )
    }
}

@Composable
private fun LoonSettingsItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    value: String = "",
    showChevron: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = LoonTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    color = LoonTextSecondary,
                    fontSize = 13.sp
                )
            }
            if (showChevron) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFFBAC0CC),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun LoonSettingsSwitchItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = LoonTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = LoonGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE2E4EB),
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun LoonDivider() {
    val isDark = LocalLoonColors.current.isDark
    HorizontalDivider(
        color = if (isDark) Color(0xFF2B3244).copy(alpha = 0.35f) else Color(0xFFE5E7EB).copy(alpha = 0.4f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 44.dp)
    )
}
