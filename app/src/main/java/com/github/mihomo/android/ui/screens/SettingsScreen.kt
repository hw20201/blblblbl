package com.github.mihomo.android.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import com.github.mihomo.android.ui.glass.NavigationLensState
import com.github.mihomo.android.ui.glass.rememberNavigationLens
import com.github.mihomo.android.ui.glass.recordNavigationScene
import com.github.mihomo.android.ui.glass.PageTitleGlass
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mihomo.android.core.ClashCore
import com.github.mihomo.android.data.SettingsManager
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.TunTuning
import com.github.mihomo.android.data.TunStack
import com.github.mihomo.android.ui.components.ShortcutTouchVariant
import com.github.mihomo.android.ui.glass.ShortcutGlassLevel
import com.github.mihomo.android.ui.components.*
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    scrollState: ScrollState,
    tunStack: TunStack,
    bootOnStartup: Boolean,
    allowLan: Boolean,
    fakeIpEnabled: Boolean,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    ipv6Enabled: Boolean = false,
    tunMtu: Int = 9000,
    mipsCongestionController: String = "cubic",
    hapticEnabled: Boolean = true,
    shortcutTouchVariant: ShortcutTouchVariant = ShortcutTouchVariant.GentleFloat,
    onShortcutTouchVariantChanged: (ShortcutTouchVariant) -> Unit = {},
    shortcutGlassLevel: ShortcutGlassLevel = ShortcutGlassLevel.Normal,
    onShortcutGlassLevelChanged: (ShortcutGlassLevel) -> Unit = {},
    onIpv6EnabledChanged: (Boolean) -> Unit = {},
    onTunMtuChanged: (Int) -> Unit = {},
    onMipsCongestionControllerChanged: (String) -> Unit = {},
    onHapticEnabledChanged: (Boolean) -> Unit = {},
    onTunStackChanged: (TunStack) -> Unit,
    onBootOnStartupChanged: (Boolean) -> Unit,
    onAllowLanChanged: (Boolean) -> Unit,
    onNavigateToConfigurationCenter: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToDns: () -> Unit = {},
    glassScene: NavigationLensState? = null
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    var showShortcutTouchDialog by remember { mutableStateOf(false) }
    var showShortcutGlassDialog by remember { mutableStateOf(false) }
    var showMtuDialog by remember { mutableStateOf(false) }
    var showCongestionDialog by remember { mutableStateOf(false) }
    var mtuDraft by remember { mutableStateOf(tunMtu.toString()) }

    var showTunDialog by remember { mutableStateOf(false) }
    var showLogLevelDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var currentLogLevel by remember { mutableStateOf(settingsManager.logLevel) }

    var coreVersion by remember { mutableStateOf("") }

    val installedVersion = remember(context) { getInstalledAppVersion(context) }

    LaunchedEffect(Unit) {
        coreVersion = withContext(Dispatchers.IO) { ClashCore.getCoreVersion() }
    }

    val titleScene = rememberNavigationLens()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerInset = with(LocalDensity.current) { headerHeightPx.toDp() }
    Box(Modifier.fillMaxSize().background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)) {
        Column(
            modifier = Modifier.fillMaxSize()
                .then(if (GlassDefaults.enabled) Modifier.recordNavigationScene(titleScene, glassScene) else Modifier)
                .bounceOverscroll()
                .verticalScroll(scrollState)
                .padding(top = headerInset + 12.dp, bottom = 120.dp)
        ) {
            // Section 1: 网络与核心 (Network & Core)
            CupertinoGroupedSection(
                header = "网络与核心"
            ) {
                CupertinoCell(
                    title = "TUN 堆栈",
                    value = when (tunStack.key) {
                        "mixed" -> "Mixed"
                        "gvisor" -> "gVisor"
                        "mips" -> "MIPS"
                        "system" -> "System"
                        else -> "Mixed"
                    },
                    icon = Icons.Default.VpnKey,
                    iconBg = IosBlue,
                    showChevron = true,
                    onClick = { showTunDialog = true }
                )
                CupertinoDivider()
                CupertinoCell(
                    title = "开机自启动",
                    icon = Icons.Default.PowerSettingsNew,
                    iconBg = Color(0xFF34C759),
                    trailing = {
                        CupertinoSwitch(
                            checked = bootOnStartup,
                            onCheckedChange = onBootOnStartupChanged
                        )
                    },
                    onClick = { onBootOnStartupChanged(!bootOnStartup) }
                )
                CupertinoDivider()
                CupertinoCell(
                    title = "局域网共享",
                    icon = Icons.Default.WifiTethering,
                    iconBg = Color(0xFF32ADE6),
                    trailing = {
                        CupertinoSwitch(
                            checked = allowLan,
                            onCheckedChange = onAllowLanChanged
                        )
                    },
                    onClick = { onAllowLanChanged(!allowLan) }
                )
                CupertinoDivider()
                CupertinoCell(
                    title = "系统 VPN",
                    value = "始终开启",
                    icon = Icons.Default.Security,
                    iconBg = Color(0xFFFF9500),
                    showChevron = true,
                    onClick = {
                        runCatching {
                            val intent = Intent(Settings.ACTION_VPN_SETTINGS)
                            context.startActivity(intent)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: 分流与服务 (Routing & Services)
            CupertinoGroupedSection(
                header = "网络与 TUN"
            ) {
                CupertinoCell(title = "IPv6", subtitle = AppStrings.get("settings_ipv6_desc", lang), trailing = {
                    CupertinoSwitch(checked = ipv6Enabled, onCheckedChange = { enabled ->
                        onIpv6EnabledChanged(enabled)
                    })
                })
                CupertinoDivider()
                CupertinoCell(title = "TUN MTU", value = tunMtu.toString(), showChevron = true,
                    onClick = { mtuDraft = tunMtu.toString(); showMtuDialog = true })
                if (tunStack == TunStack.MIPS) {
                    CupertinoDivider()
                    CupertinoCell(title = "MIPS 拥塞控制", value = mipsCongestionController, showChevron = true,
                        onClick = { showCongestionDialog = true })
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            CupertinoGroupedSection(
                header = "分流与服务"
            ) {
                CupertinoCell(
                    title = "配置中心",
                    subtitle = "配置文件、订阅、脚本与路由资源",
                    icon = Icons.Default.FolderZip,
                    iconBg = Color(0xFFAF52DE),
                    showChevron = true,
                    onClick = onNavigateToConfigurationCenter
                )
                CupertinoDivider()
                CupertinoCell(
                    title = "DNS 设置",
                    value = if (fakeIpEnabled) "Fake-IP" else "Redir-Host",
                    icon = Icons.Default.Public,
                    iconBg = Color(0xFF30B0C7),
                    showChevron = true,
                    onClick = onNavigateToDns
                )

            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: 个性化与调试 (Preferences & Debugging)
            CupertinoGroupedSection(
                header = "个性化与调试"
            ) {
                CupertinoCell(
                    title = "外观与显示",
                    value = when (themeMode) {
                        ThemeMode.SYSTEM -> "跟随系统"
                        ThemeMode.LIGHT -> "浅色"
                        ThemeMode.DARK -> "深色"
                    },
                    icon = Icons.Default.Palette,
                    iconBg = Color(0xFFFF2D55),
                    showChevron = true,
                    onClick = onNavigateToAppearance
                )
                CupertinoDivider()
                CupertinoCell(
                    title = "快捷卡片触感",
                    subtitle = "A 轻按浮起｜B 跟手倾斜｜C 浮力回弹",
                    value = when (shortcutTouchVariant) {
                        ShortcutTouchVariant.GentleFloat -> "A"
                        ShortcutTouchVariant.FingerTilt -> "B"
                        ShortcutTouchVariant.BuoyantSpring -> "C"
                    },
                    icon = Icons.Default.TouchApp,
                    iconBg = Color(0xFF007AFF),
                    showChevron = true,
                    onClick = { showShortcutTouchDialog = true }
                )
                CupertinoDivider()
                CupertinoCell(
                    title = if (lang == "en") "Shortcut glass intensity" else "快捷卡片玻璃明显度",
                    subtitle = if (lang == "en") "Affects shortcut cards only" else "仅影响快捷卡片，不改变底部滑钮",
                    value = when (shortcutGlassLevel) {
                        ShortcutGlassLevel.Normal -> if (lang == "en") "Normal" else "普通"
                        ShortcutGlassLevel.Obvious -> if (lang == "en") "Obvious" else "明显"
                        ShortcutGlassLevel.Extreme -> if (lang == "en") "Extreme" else "极致"
                    },
                    icon = Icons.Default.Layers,
                    iconBg = Color(0xFF5E5CE6),
                    showChevron = true,
                    onClick = { showShortcutGlassDialog = true }
                )
                CupertinoDivider()
                CupertinoCell(title = "触感反馈", subtitle = "立即应用于界面交互", trailing = {
                    CupertinoSwitch(checked = hapticEnabled, onCheckedChange = { enabled ->
                        onHapticEnabledChanged(enabled)
                    })
                })
                CupertinoDivider()
                CupertinoCell(
                    title = "核心日志",
                    icon = Icons.Default.Terminal,
                    iconBg = Color(0xFF8E8E93),
                    showChevron = true,
                    onClick = onNavigateToLogs
                )
                CupertinoDivider()
                CupertinoCell(
                    title = "日志级别",
                    value = currentLogLevel.uppercase(),
                    icon = Icons.Default.Tune,
                    iconBg = Color(0xFF5856D6),
                    showChevron = true,
                    onClick = { showLogLevelDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 4: 关于与维护 (About & Maintenance)
            CupertinoGroupedSection(
                header = "维护与关于"
            ) {
                CupertinoCell(
                    title = "应用版本",
                    value = "v$installedVersion",
                    icon = Icons.Default.SystemUpdate,
                    iconBg = Color(0xFF34C759)
                )
                CupertinoDivider()
                CupertinoCell(
                    title = "核心版本",
                    value = coreVersion.ifBlank { "v1.18.x" },
                    icon = Icons.Default.Memory,
                    iconBg = Color(0xFF8E8E93),
                    showChevron = false
                )
                CupertinoDivider()
                CupertinoCell(
                    title = "重置核心状态",
                    icon = Icons.Default.RestartAlt,
                    iconBg = Color(0xFFFF3B30),
                    showChevron = true,
                    onClick = { showResetDialog = true }
                )
            }
        }
        Box(Modifier.onSizeChanged { headerHeightPx = it.height }.statusBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 12.dp)
            ) {
            PageTitleGlass(titleScene) {
                Text(AppStrings.get("settings_title", lang), color = LoonTextPrimary,
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
            }
        }
    }

    // TUN Stack Selection BottomSheet
    if (showTunDialog) {
        val stacks = listOf(
            Triple("mixed", "Mixed", "兼顾性能与稳定性的混合模式"),
            Triple("gvisor", "gVisor", "用户态网络协议栈隔离"),
            Triple("system", "System", "操作系统原生网络栈"),
            Triple("mips", "MIPS", AppStrings.get("tun_stack_mips_desc", lang))
        )
        ModalBottomSheet(
            onDismissRequest = { showTunDialog = false },
            containerColor = LoonCard,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "TUN 协议栈",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LoonTextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                stacks.forEachIndexed { index, (stackKey, label, desc) ->
                    val isSelected = tunStack.key.equals(stackKey, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onTunStackChanged(TunStack.fromKey(stackKey) ?: TunStack.DEFAULT)
                                showTunDialog = false
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) IosBlue else LoonTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = desc,
                                fontSize = 12.sp,
                                color = LoonTextSecondary
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = IosBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (index < stacks.size - 1) {
                        HorizontalDivider(
                            color = LoonCardBorder,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }

    // Log Level Selection BottomSheet
    if (showLogLevelDialog) {
        val levels = listOf(
            Triple("info", "INFO", "记录标准网络调度与运行输出"),
            Triple("warning", "WARN", "仅记录警告告警与异常"),
            Triple("error", "ERROR", "仅记录严重崩溃与错误"),
            Triple("debug", "DEBUG", "输出底层全部调试报文")
        )
        ModalBottomSheet(
            onDismissRequest = { showLogLevelDialog = false },
            containerColor = LoonCard,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "日志详细程度",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LoonTextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                levels.forEachIndexed { index, (lvlKey, label, desc) ->
                    val isSelected = currentLogLevel.equals(lvlKey, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                currentLogLevel = lvlKey
                                settingsManager.logLevel = lvlKey
                                showLogLevelDialog = false
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) IosBlue else LoonTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = desc,
                                fontSize = 12.sp,
                                color = LoonTextSecondary
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = IosBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (index < levels.size - 1) {
                        HorizontalDivider(
                            color = LoonCardBorder,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }

    // Core Reset Confirmation
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置核心状态", fontWeight = FontWeight.Bold, color = LoonTextPrimary) },
            text = { Text("确定要重置底层核心状态吗？当前活动的网络连接将被断开并重新初始化。", color = LoonTextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) { ClashCore.reset() }
                        Toast.makeText(context, "核心已重置", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("重置", color = IosRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("取消", color = LoonTextSecondary)
                }
            },
            containerColor = LoonCard,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showShortcutGlassDialog) {
        AlertDialog(
            onDismissRequest = { showShortcutGlassDialog = false },
            title = { Text(if (lang == "en") "Shortcut glass intensity" else "快捷卡片玻璃明显度") },
            text = {
                Column {
                    val choices = listOf(
                        Triple(ShortcutGlassLevel.Normal, if (lang == "en") "Normal" else "普通", if (lang == "en") "Lighter resource use" else "较轻开销"),
                        Triple(ShortcutGlassLevel.Obvious, if (lang == "en") "Obvious" else "明显", if (lang == "en") "Balanced" else "平衡"),
                        Triple(ShortcutGlassLevel.Extreme, if (lang == "en") "Extreme" else "极致", if (lang == "en") "Stronger optics; GPU cost above Normal" else "强折射；GPU 开销高于普通")
                    )
                    choices.forEach { (level, label, description) ->
                        Row(Modifier.fillMaxWidth().clickable {
                            onShortcutGlassLevelChanged(level)
                            showShortcutGlassDialog = false
                        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = shortcutGlassLevel == level, onClick = {
                                onShortcutGlassLevelChanged(level)
                                showShortcutGlassDialog = false
                            })
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(label, fontWeight = FontWeight.Medium)
                                Text(description, color = LoonTextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showShortcutGlassDialog = false }) { Text(if (lang == "en") "Done" else "完成") } }
        )
    }
    if (showShortcutTouchDialog) {
        AlertDialog(
            onDismissRequest = { showShortcutTouchDialog = false },
            title = { Text("快捷卡片触感") },
            text = {
                Column {
                    listOf(
                        ShortcutTouchVariant.GentleFloat to ("A 轻按浮起" to "轻柔抬升，动作克制"),
                        ShortcutTouchVariant.FingerTilt to ("B 跟手倾斜" to "随手指方向产生倾斜"),
                        ShortcutTouchVariant.BuoyantSpring to ("C 浮力回弹" to "更明显的浮起与弹性回落")
                    ).forEach { (variant, labels) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onShortcutTouchVariantChanged(variant)
                                showShortcutTouchDialog = false
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = shortcutTouchVariant == variant, onClick = {
                                onShortcutTouchVariantChanged(variant)
                                showShortcutTouchDialog = false
                            })
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(labels.first, fontWeight = FontWeight.Medium)
                                Text(labels.second, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showShortcutTouchDialog = false }) { Text("完成") } }
        )
    }
    if (showMtuDialog) {
        AlertDialog(onDismissRequest = { showMtuDialog = false }, title = { Text("TUN MTU") },
            text = { OutlinedTextField(value = mtuDraft, onValueChange = { v -> if (v.length <= 5 && v.all(Char::isDigit)) mtuDraft = v }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                mtuDraft.toIntOrNull()?.let { value ->
                    onTunMtuChanged(TunTuning.normalizeMtu(value))
                }
                showMtuDialog = false
            }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { showMtuDialog = false }) { Text("取消") } })
    }
    if (showCongestionDialog) {
        AlertDialog(onDismissRequest = { showCongestionDialog = false }, title = { Text("MIPS 拥塞控制") },
            text = { Column { TunTuning.CONGESTION_CONTROLLERS.forEach { option ->
                Row(Modifier.fillMaxWidth().clickable {
                    onMipsCongestionControllerChanged(option)
                    showCongestionDialog = false
                }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = option == mipsCongestionController, onClick = {
                        onMipsCongestionControllerChanged(option)
                        showCongestionDialog = false
                    })
                    Text(option)
                }
            } } }, confirmButton = { TextButton(onClick = { showCongestionDialog = false }) { Text("关闭") } })
    }

}
