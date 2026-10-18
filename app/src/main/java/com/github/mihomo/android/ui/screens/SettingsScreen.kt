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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mihomo.android.core.ClashCore
import com.github.mihomo.android.data.ScriptItem
import com.github.mihomo.android.data.ScriptManager
import com.github.mihomo.android.data.SettingsManager
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.TunTuning
import com.github.mihomo.android.ui.components.bounceOverscroll
import com.github.mihomo.android.ui.components.*
import com.github.mihomo.android.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.mihomo.android.ui.components.rememberHapticAction
import com.github.mihomo.android.ui.components.rememberHapticValueAction
import com.github.mihomo.android.ui.components.hapticClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    scrollState: ScrollState,
    tunStack: String,
    bootOnStartup: Boolean,
    allowLan: Boolean,
    fakeIpEnabled: Boolean,
    hapticEnabled: Boolean,
    ipv6Enabled: Boolean = false,
    tunMtu: Int = TunTuning.DEFAULT_MTU,
    mipsCongestionController: String = TunTuning.DEFAULT_CONGESTION_CONTROLLER,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onTunStackChanged: (String) -> Unit,
    onBootOnStartupChanged: (Boolean) -> Unit,
    onAllowLanChanged: (Boolean) -> Unit,
    onFakeIpEnabledChanged: (Boolean) -> Unit,
    onHapticEnabledChanged: (Boolean) -> Unit,
    onIpv6EnabledChanged: (Boolean) -> Unit = {},
    onTunMtuChanged: (Int) -> Unit = {},
    onMipsCongestionControllerChanged: (String) -> Unit = {},
    onScriptsChanged: () -> Unit = {},
    onNavigateToScripts: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToAppRouting: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToDns: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val appVersion = remember(context) { getInstalledAppVersion(context) }

    var showTunDialog by remember { mutableStateOf(false) }
    var showMtuDialog by remember { mutableStateOf(false) }
    var showCongestionDialog by remember { mutableStateOf(false) }
    var mtuDraft by remember(tunMtu) { mutableStateOf(tunMtu.toString()) }
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
        CupertinoNavBar(title = AppStrings.get("settings_title", lang))

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
        CupertinoGroupedSection(horizontalPadding = 0.dp) {
            LoonSettingsItem(
                icon = Icons.Default.VpnKey,
                iconBg = Color(0xFF3B82F6),
                title = AppStrings.get("settings_tun_stack", lang),
                value = when (tunStack) {
                    "system" -> com.github.mihomo.android.data.TunStack.SYSTEM.displayName
                    "mixed" -> com.github.mihomo.android.data.TunStack.MIXED.displayName
                    "gvisor" -> com.github.mihomo.android.data.TunStack.GVISOR.displayName
                    "mips" -> com.github.mihomo.android.data.TunStack.MIPS.displayName
                    else -> tunStack
                },
                showChevron = true,
                onClick = { showTunDialog = true }
            )
            LoonDivider()
            LoonSettingsSwitchItem(
                icon = Icons.Default.Language,
                iconBg = Color(0xFF06B6D4),
                title = AppStrings.get("settings_ipv6", lang),
                subtitle = AppStrings.get("settings_ipv6_desc", lang),
                checked = ipv6Enabled,
                onCheckedChange = onIpv6EnabledChanged
            )
            LoonDivider()
            LoonSettingsItem(
                icon = Icons.Default.Speed,
                iconBg = Color(0xFFF59E0B),
                title = AppStrings.get("settings_tun_mtu", lang),
                value = tunMtu.toString(),
                showChevron = true,
                onClick = {
                    mtuDraft = tunMtu.toString()
                    showMtuDialog = true
                }
            )
            if (tunStack == com.github.mihomo.android.data.TunStack.MIPS.key) {
                LoonDivider()
                LoonSettingsItem(
                    icon = Icons.Default.Tune,
                    iconBg = Color(0xFF8B5CF6),
                    title = AppStrings.get("settings_mips_cc", lang),
                    value = mipsCongestionController,
                    showChevron = true,
                    onClick = { showCongestionDialog = true }
                )
            }
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
        CupertinoGroupedSection(horizontalPadding = 0.dp) {
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
        CupertinoGroupedSection(horizontalPadding = 0.dp) {
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
        CupertinoGroupedSection(horizontalPadding = 0.dp) {
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
        CupertinoGroupedSection(horizontalPadding = 0.dp) {
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
        CupertinoGroupedSection(horizontalPadding = 0.dp) {
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
                value = coreVersion.ifBlank { "—" },
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
        CupertinoGroupedSection(horizontalPadding = 0.dp) {
            LoonSettingsItem(
                icon = Icons.Default.Info,
                iconBg = LoonBlue,
                title = "应用版本",
                value = appVersion,
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
                TextButton(onClick = rememberHapticAction({
                    showResetDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) { ClashCore.reset() }
                    }
                })) {
                    Text(AppStrings.get("confirm", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = rememberHapticAction({ showResetDialog = false })) {
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
                                .hapticClickable {
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
                TextButton(onClick = rememberHapticAction({ showLogLevelDialog = false })) {
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
                    Text(AppStrings.get("settings_tun_stack_fallback", lang), fontSize = 13.sp, color = LoonTextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    com.github.mihomo.android.data.TunStack.entries.forEach { stack ->
                        val key = stack.key
                        val desc = AppStrings.get("tun_stack_${key}_desc", lang)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .hapticClickable {
                                    onTunStackChanged(key)
                                    showTunDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = tunStack == key, onClick = rememberHapticAction({
                                onTunStackChanged(key)
                                showTunDialog = false
                            }))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(desc, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = rememberHapticAction({ showTunDialog = false })) {
                    Text(AppStrings.get("confirm", lang))
                }
            },
            containerColor = LoonCard,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // TUN MTU Dialog
    if (showMtuDialog) {
        AlertDialog(
            onDismissRequest = { showMtuDialog = false },
            title = { Text(AppStrings.get("settings_tun_mtu", lang), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(AppStrings.get("settings_tun_mtu_desc", lang), fontSize = 13.sp, color = LoonTextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = mtuDraft,
                        onValueChange = { input ->
                            if (input.length <= 5 && input.all { it.isDigit() }) mtuDraft = input
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = rememberHapticAction({
                    mtuDraft.toIntOrNull()?.let { onTunMtuChanged(TunTuning.normalizeMtu(it)) }
                    showMtuDialog = false
                })) {
                    Text(AppStrings.get("confirm", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = rememberHapticAction({ showMtuDialog = false })) {
                    Text(AppStrings.get("cancel", lang), color = LoonTextSecondary)
                }
            },
            containerColor = LoonCard,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // MIPS congestion controller Dialog
    if (showCongestionDialog) {
        AlertDialog(
            onDismissRequest = { showCongestionDialog = false },
            title = { Text(AppStrings.get("settings_mips_cc", lang), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(AppStrings.get("settings_mips_cc_desc", lang), fontSize = 13.sp, color = LoonTextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    TunTuning.CONGESTION_CONTROLLERS.forEach { controller ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .hapticClickable {
                                    onMipsCongestionControllerChanged(controller)
                                    showCongestionDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = mipsCongestionController == controller,
                                onClick = rememberHapticAction({
                                    onMipsCongestionControllerChanged(controller)
                                    showCongestionDialog = false
                                })
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(controller, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = rememberHapticAction({ showCongestionDialog = false })) {
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
private fun LoonSettingsItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    value: String = "",
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    CupertinoCell(
        title = title, icon = icon, iconBg = iconBg,
        value = value, showChevron = showChevron, onClick = onClick
    )
}

@Composable
private fun LoonSettingsSwitchItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    CupertinoCell(
        title = title, subtitle = subtitle, icon = icon, iconBg = iconBg,
        trailing = { CupertinoSwitch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}

@Composable
private fun LoonDivider() {
    CupertinoIndentedDivider()
}
