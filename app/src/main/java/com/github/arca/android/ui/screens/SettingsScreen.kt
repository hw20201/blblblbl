package com.github.arca.android.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import com.github.arca.android.ui.glass.FROSTED_PAGE_TITLE_FILL_ALPHA
import com.github.arca.android.ui.glass.GlassAlertDialog
import com.github.arca.android.ui.glass.GlassModalBottomSheet
import com.github.arca.android.ui.glass.NavigationLensState
import com.github.arca.android.ui.glass.rememberNavigationLens
import com.github.arca.android.ui.glass.recordNavigationScene
import com.github.arca.android.ui.glass.PageScrollEdge
import com.github.arca.android.ui.glass.PageTitleGlass
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.core.ClashCore
import com.github.arca.android.data.PanelAnimationSpeed
import com.github.arca.android.data.SettingsManager
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.data.TunTuning
import com.github.arca.android.data.TunStack
import com.github.arca.android.ui.components.ShortcutTouchVariant
import com.github.arca.android.ui.glass.LocalGlassTokens
import com.github.arca.android.ui.glass.ShortcutDockGlassSurface
import com.github.arca.android.ui.glass.ShortcutFollowupCardShape
import com.github.arca.android.ui.glass.ShortcutRefraction
import com.github.arca.android.ui.components.*
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.GlassSurface
import com.github.arca.android.ui.glass.GlassRadius
import com.github.arca.android.ui.glass.rememberArcaGlasmioBackdrop
import com.github.arca.android.ui.glass.recordArcaGlasmioBackdropSource
import io.github.neoruaa.glasmio.glass.progressiveBlurBackdrop
import com.github.arca.android.ui.glass.glassTextPrimary
import com.github.arca.android.ui.glass.glassTextSecondary
import com.github.arca.android.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    scrollState: ScrollState,
    tunStack: TunStack,
    bootOnStartup: Boolean,
    allowLan: Boolean,
    fakeIpEnabled: Boolean,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    ipv6Enabled: Boolean = false,
    unifiedDelayEnabled: Boolean = false,
    profileHotSwitchEnabled: Boolean = true,
    tunMtu: Int = 9000,
    mipsCongestionController: String = "cubic",
    hapticEnabled: Boolean = true,
    shortcutTouchVariant: ShortcutTouchVariant = ShortcutTouchVariant.GentleFloat,
    onShortcutTouchVariantChanged: (ShortcutTouchVariant) -> Unit = {},
    onIpv6EnabledChanged: (Boolean) -> Unit = {},
    onUnifiedDelayChanged: (Boolean) -> Unit = {},
    onProfileHotSwitchChanged: (Boolean) -> Unit = {},
    onTunMtuChanged: (Int) -> Unit = {},
    onMipsCongestionControllerChanged: (String) -> Unit = {},
    onHapticEnabledChanged: (Boolean) -> Unit = {},
    onTunStackChanged: (TunStack) -> Unit,
    onBootOnStartupChanged: (Boolean) -> Unit,
    onAllowLanChanged: (Boolean) -> Unit,
    onNavigateToConfigurationCenter: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToConnections: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToDns: () -> Unit = {},
    onNavigateToGlassEffects: () -> Unit = {},
    glassScene: NavigationLensState? = null,
    appearanceDetailContent: (@Composable () -> Unit)? = null,
    glassEffectsDetailContent: (@Composable () -> Unit)? = null,
    dnsDetailContent: (@Composable () -> Unit)? = null,
    connectionsDetailContent: (@Composable () -> Unit)? = null,
    logsDetailContent: (@Composable () -> Unit)? = null,
    configurationCenterDetailContent: (@Composable (onClose: () -> Unit) -> Unit)? = null,
    panelAnimationSpeed: PanelAnimationSpeed = PanelAnimationSpeed.MEDIUM,
    onPanelAnimationSpeedChanged: (PanelAnimationSpeed) -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    var mtuDraft by remember { mutableStateOf(tunMtu.toString()) }

    var activeSettingsDetail by remember { mutableStateOf<SettingsDetailPage?>(null) }
    var hiddenSettingsDetailSource by remember { mutableStateOf<SettingsDetailPage?>(null) }
    val settingsDetailBounds = remember { mutableStateMapOf<SettingsDetailPage, Rect>() }
    // Which corners of its own card a row keeps large; the morph starts from the card, not a guess.
    val settingsDetailRadius = remember { mutableStateMapOf<SettingsDetailPage, SeparatedRowPosition>() }
    fun openSettingsDetail(page: SettingsDetailPage) {
        activeSettingsDetail = page
        hiddenSettingsDetailSource = page
    }
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
    val quietPageColor = if (LocalLoonColors.current.isDark) Color(0xFF101114) else Color(0xFFF2F2F5)
    val pageBackdrop = rememberArcaGlasmioBackdrop(enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    val topBlurReveal by remember(scrollState) {
        derivedStateOf { (scrollState.value / 96f).coerceIn(0f, 1f) }
    }
    Box(Modifier.fillMaxSize().background(quietPageColor)) {
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null,
            LocalQuietSeparatedRows provides true
        ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .then(if (GlassDefaults.enabled)
                    Modifier.recordNavigationScene(titleScene, glassScene) else Modifier)
                .background(quietPageColor)
                .recordArcaGlasmioBackdropSource(pageBackdrop)
                .testTag("settings-scroll")
                .bounceOverscroll(allowBottom = false)
                .verticalScroll(scrollState)
                .padding(top = headerInset + 20.dp, bottom = mainDockContentInset(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        ) {
            SettingsGroup("network-core", "网络与核心", "连接基础、启动与局域网共享", Icons.Default.VpnKey, IosBlue) {
                SeparatedRow(
                    SeparatedRowPosition.First,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (hiddenSettingsDetailSource == SettingsDetailPage.TUN_STACK) 0f else 1f }
                        .onGloballyPositioned { settingsDetailBounds[SettingsDetailPage.TUN_STACK] = it.boundsInWindow(); settingsDetailRadius[SettingsDetailPage.TUN_STACK] = SeparatedRowPosition.First }
                ) {
                    CupertinoCell(
                        modifier = Modifier.testTag("settings-tun-stack-entry"),
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
                        onClick = { openSettingsDetail(SettingsDetailPage.TUN_STACK) }
                    )
                }
                SeparatedRow(SeparatedRowPosition.Middle) {
                    CupertinoCell(
                        title = "开机自启动",
                        icon = Icons.Default.PowerSettingsNew,
                        iconBg = AppPalette.iconGreen,
                        trailing = {
                            ArcaGlasmioSwitch(
                                checked = bootOnStartup,
                                onCheckedChange = onBootOnStartupChanged
                            )
                        },
                        onClick = { onBootOnStartupChanged(!bootOnStartup) }
                    )
                }
                SeparatedRow(SeparatedRowPosition.Middle) {
                    CupertinoCell(
                        title = "局域网共享",
                        icon = Icons.Default.WifiTethering,
                        iconBg = AppPalette.iconSky,
                        trailing = {
                            ArcaGlasmioSwitch(
                                checked = allowLan,
                                onCheckedChange = onAllowLanChanged
                            )
                        },
                        onClick = { onAllowLanChanged(!allowLan) }
                    )
                }
                SeparatedRow(SeparatedRowPosition.Middle) {
                    CupertinoCell(
                        title = "系统 VPN",
                        value = "始终开启",
                        icon = Icons.Default.Security,
                        iconBg = AppPalette.iconOrange,
                        showChevron = true,
                        onClick = {
                            runCatching {
                                val intent = Intent(Settings.ACTION_VPN_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    )
                }
                SeparatedRow(SeparatedRowPosition.Last) {
                    CupertinoCell(
                        modifier = Modifier.testTag("settings-profile-hot-switch"),
                        title = AppStrings.get("settings_profile_hot_switch", lang),
                        subtitle = AppStrings.get("settings_profile_hot_switch_desc", lang),
                        icon = Icons.Default.FlashOn,
                        iconBg = AppPalette.iconAmber,
                        trailing = {
                            ArcaGlasmioSwitch(checked = profileHotSwitchEnabled, onCheckedChange = onProfileHotSwitchChanged)
                        },
                        onClick = { onProfileHotSwitchChanged(!profileHotSwitchEnabled) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            SettingsGroup("network-tun", "网络与 TUN", "隧道协议、地址族与网络参数", Icons.Default.Public, AppPalette.iconTeal) {
                SeparatedRow(SeparatedRowPosition.First) {
                    CupertinoCell(title = "IPv6", subtitle = AppStrings.get("settings_ipv6_desc", lang), trailing = {
                        ArcaGlasmioSwitch(checked = ipv6Enabled, onCheckedChange = { enabled ->
                            onIpv6EnabledChanged(enabled)
                        })
                    })
                }
                SeparatedRow(SeparatedRowPosition.Middle) {
                    CupertinoCell(modifier = Modifier.testTag("settings-unified-delay"), title = AppStrings.get("settings_unified_delay", lang), subtitle = AppStrings.get("settings_unified_delay_desc", lang), trailing = {
                        ArcaGlasmioSwitch(checked = unifiedDelayEnabled, onCheckedChange = onUnifiedDelayChanged)
                    }, onClick = { onUnifiedDelayChanged(!unifiedDelayEnabled) })
                }
                // TUN MTU closes the group unless the MIPS row follows it.
                val mtuPosition = if (tunStack == TunStack.MIPS) SeparatedRowPosition.Middle else SeparatedRowPosition.Last
                SeparatedRow(
                    mtuPosition,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (hiddenSettingsDetailSource == SettingsDetailPage.TUN_MTU) 0f else 1f }
                        .onGloballyPositioned {
                            settingsDetailBounds[SettingsDetailPage.TUN_MTU] = it.boundsInWindow()
                            settingsDetailRadius[SettingsDetailPage.TUN_MTU] = mtuPosition
                        }
                ) {
                    CupertinoCell(
                        modifier = Modifier.testTag("settings-mtu-entry"),
                        title = "TUN MTU", value = tunMtu.toString(), showChevron = true,
                        onClick = { mtuDraft = tunMtu.toString(); openSettingsDetail(SettingsDetailPage.TUN_MTU) }
                    )
                }
                if (tunStack == TunStack.MIPS) {
                    SeparatedRow(
                        SeparatedRowPosition.Last,
                        modifier = Modifier
                            .graphicsLayer { alpha = if (hiddenSettingsDetailSource == SettingsDetailPage.MIPS_CONGESTION) 0f else 1f }
                            .onGloballyPositioned {
                                settingsDetailBounds[SettingsDetailPage.MIPS_CONGESTION] = it.boundsInWindow()
                                settingsDetailRadius[SettingsDetailPage.MIPS_CONGESTION] = SeparatedRowPosition.Last
                            }
                    ) {
                        CupertinoCell(
                            modifier = Modifier.testTag("settings-mips-congestion-entry"),
                            title = "MIPS 拥塞控制", value = mipsCongestionController, showChevron = true,
                            onClick = { openSettingsDetail(SettingsDetailPage.MIPS_CONGESTION) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            SettingsGroup("routing-services", "分流与服务", "配置资源、DNS 与规则入口", Icons.Default.FolderZip, AppPalette.iconPurple) {
                SeparatedRow(
                    SeparatedRowPosition.First,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (hiddenSettingsDetailSource == SettingsDetailPage.CONFIGURATION_CENTER) 0f else 1f }
                        .onGloballyPositioned { settingsDetailBounds[SettingsDetailPage.CONFIGURATION_CENTER] = it.boundsInWindow(); settingsDetailRadius[SettingsDetailPage.CONFIGURATION_CENTER] = SeparatedRowPosition.First }
                ) {
                    CupertinoCell(
                        modifier = Modifier.testTag("settings-configuration-center-entry"),
                        title = "配置中心",
                        subtitle = "配置文件、订阅、脚本与路由资源",
                        icon = Icons.Default.FolderZip,
                        iconBg = AppPalette.iconPurple,
                        showChevron = true,
                        onClick = {
                            if (configurationCenterDetailContent != null) openSettingsDetail(SettingsDetailPage.CONFIGURATION_CENTER)
                            else onNavigateToConfigurationCenter()
                        }
                    )
                }
                SeparatedRow(
                    SeparatedRowPosition.Last,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (hiddenSettingsDetailSource == SettingsDetailPage.DNS) 0f else 1f }
                        .onGloballyPositioned { settingsDetailBounds[SettingsDetailPage.DNS] = it.boundsInWindow(); settingsDetailRadius[SettingsDetailPage.DNS] = SeparatedRowPosition.Last }
                ) {
                    CupertinoCell(
                        modifier = Modifier.testTag("settings-dns-entry"),
                        title = "DNS 设置",
                        value = if (fakeIpEnabled) "Fake-IP" else "Redir-Host",
                        icon = Icons.Default.Public,
                        iconBg = AppPalette.iconTeal,
                        showChevron = true,
                        onClick = {
                            if (dnsDetailContent != null) openSettingsDetail(SettingsDetailPage.DNS)
                            else onNavigateToDns()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Each group shares one raised outline; its controls stay flat and compact.
            SettingsGroup("preferences-debug", "个性化与调试", "外观、交互反馈与调试工具", Icons.Default.Palette, AppPalette.iconPink) {
                SeparatedRow(
                    SeparatedRowPosition.First,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (hiddenSettingsDetailSource == SettingsDetailPage.APPEARANCE) 0f else 1f }
                        .onGloballyPositioned { settingsDetailBounds[SettingsDetailPage.APPEARANCE] = it.boundsInWindow(); settingsDetailRadius[SettingsDetailPage.APPEARANCE] = SeparatedRowPosition.First }
                ) {
                    CupertinoCell(
                        modifier = Modifier.testTag("settings-appearance-entry"),
                        title = "外观与显示",
                        value = when (themeMode) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "浅色"
                            ThemeMode.DARK -> "深色"
                        },
                        icon = Icons.Default.Palette,
                        iconBg = AppPalette.iconPink,
                        showChevron = true,
                        onClick = {
                            if (appearanceDetailContent != null) openSettingsDetail(SettingsDetailPage.APPEARANCE)
                            else onNavigateToAppearance()
                        }
                    )
                }
                SeparatedRow(
                    SeparatedRowPosition.Middle,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (hiddenSettingsDetailSource == SettingsDetailPage.SHORTCUT_TOUCH) 0f else 1f }
                        .onGloballyPositioned { settingsDetailBounds[SettingsDetailPage.SHORTCUT_TOUCH] = it.boundsInWindow(); settingsDetailRadius[SettingsDetailPage.SHORTCUT_TOUCH] = SeparatedRowPosition.Middle }
                ) {
                    CupertinoCell(
                        modifier = Modifier.testTag("settings-shortcut-touch-entry"),
                        title = "快捷卡片触感",
                        subtitle = "A 轻按浮起｜B 跟手倾斜｜C 浮力回弹",
                        value = when (shortcutTouchVariant) {
                            ShortcutTouchVariant.GentleFloat -> "A"
                            ShortcutTouchVariant.FingerTilt -> "B"
                            ShortcutTouchVariant.BuoyantSpring -> "C"
                        },
                        icon = Icons.Default.TouchApp,
                        iconBg = AppPalette.iconBlue,
                        showChevron = true,
                        onClick = { openSettingsDetail(SettingsDetailPage.SHORTCUT_TOUCH) }
                    )
                }
                SeparatedRow(
                    SeparatedRowPosition.Middle,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (hiddenSettingsDetailSource == SettingsDetailPage.PANEL_SPEED) 0f else 1f }
                        .onGloballyPositioned {
                            settingsDetailBounds[SettingsDetailPage.PANEL_SPEED] = it.boundsInWindow()
                            settingsDetailRadius[SettingsDetailPage.PANEL_SPEED] = SeparatedRowPosition.Middle
                        }
                ) {
                    CupertinoCell(
                        modifier = Modifier.testTag("settings-panel-speed-entry"),
                        title = "面板动画速度",
                        subtitle = "首页快捷方式与设置面板",
                        value = panelAnimationSpeed.label,
                        icon = Icons.Default.Speed,
                        iconBg = AppPalette.iconBlue,
                        showChevron = true,
                        onClick = { openSettingsDetail(SettingsDetailPage.PANEL_SPEED) }
                    )
                }
                SeparatedRow(
                    SeparatedRowPosition.Middle,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (hiddenSettingsDetailSource == SettingsDetailPage.GLASS_EFFECTS) 0f else 1f }
                        .onGloballyPositioned { settingsDetailBounds[SettingsDetailPage.GLASS_EFFECTS] = it.boundsInWindow(); settingsDetailRadius[SettingsDetailPage.GLASS_EFFECTS] = SeparatedRowPosition.Middle }
                ) {
                    CupertinoCell(
                        modifier = Modifier.testTag("settings-glass-effects-entry"),
                        title = "玻璃效果",
                        subtitle = "折射、色散、部位覆盖与跟手倾斜",
                        icon = Icons.Default.AutoAwesome,
                        iconBg = AppPalette.iconLavender,
                        showChevron = true,
                        onClick = {
                            if (glassEffectsDetailContent != null) openSettingsDetail(SettingsDetailPage.GLASS_EFFECTS)
                            else onNavigateToGlassEffects()
                        }
                    )
                }
                SeparatedRow(SeparatedRowPosition.Middle) {
                    CupertinoCell(title = "触感反馈", subtitle = "立即应用于界面交互", trailing = {
                        ArcaGlasmioSwitch(checked = hapticEnabled, onCheckedChange = { enabled ->
                            onHapticEnabledChanged(enabled)
                        })
                    })
                }
                SeparatedRow(
                    SeparatedRowPosition.Middle,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (hiddenSettingsDetailSource == SettingsDetailPage.CONNECTIONS) 0f else 1f }
                        .onGloballyPositioned { settingsDetailBounds[SettingsDetailPage.CONNECTIONS] = it.boundsInWindow(); settingsDetailRadius[SettingsDetailPage.CONNECTIONS] = SeparatedRowPosition.Middle }
                ) {
                    CupertinoCell(
                        modifier = Modifier.testTag("settings-connections-entry"),
                        title = "活动连接",
                        subtitle = "查看实时连接，可单条或全部断开",
                        icon = Icons.Default.SwapHoriz,
                        iconBg = AppPalette.iconTeal,
                        showChevron = true,
                        onClick = {
                            if (connectionsDetailContent != null) openSettingsDetail(SettingsDetailPage.CONNECTIONS)
                            else onNavigateToConnections()
                        }
                    )
                }
                SeparatedRow(
                    SeparatedRowPosition.Middle,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (hiddenSettingsDetailSource == SettingsDetailPage.CORE_LOGS) 0f else 1f }
                        .onGloballyPositioned { settingsDetailBounds[SettingsDetailPage.CORE_LOGS] = it.boundsInWindow(); settingsDetailRadius[SettingsDetailPage.CORE_LOGS] = SeparatedRowPosition.Middle }
                ) {
                    CupertinoCell(
                        modifier = Modifier.testTag("settings-logs-entry"),
                        title = "核心日志",
                        icon = Icons.Default.Terminal,
                        iconBg = AppPalette.iconGray,
                        showChevron = true,
                        onClick = {
                            if (logsDetailContent != null) openSettingsDetail(SettingsDetailPage.CORE_LOGS)
                            else onNavigateToLogs()
                        }
                    )
                }
                SeparatedRow(
                    SeparatedRowPosition.Last,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (hiddenSettingsDetailSource == SettingsDetailPage.LOG_LEVEL) 0f else 1f }
                        .onGloballyPositioned { settingsDetailBounds[SettingsDetailPage.LOG_LEVEL] = it.boundsInWindow(); settingsDetailRadius[SettingsDetailPage.LOG_LEVEL] = SeparatedRowPosition.Last }
                ) {
                    CupertinoCell(
                        modifier = Modifier.testTag("settings-log-level-entry"),
                        title = "日志级别",
                        value = currentLogLevel.uppercase(),
                        icon = Icons.Default.Tune,
                        iconBg = AppPalette.iconIndigo,
                        showChevron = true,
                        onClick = { openSettingsDetail(SettingsDetailPage.LOG_LEVEL) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            SettingsGroup("maintenance-about", "维护与关于", "应用与核心版本、维护操作", Icons.Default.SystemUpdate, AppPalette.iconGreen) {
                SeparatedRow(SeparatedRowPosition.First) {
                    CupertinoCell(
                        title = "应用版本",
                        value = "v$installedVersion",
                        icon = Icons.Default.SystemUpdate,
                        iconBg = AppPalette.iconGreen
                    )
                }
                SeparatedRow(SeparatedRowPosition.Middle) {
                    CupertinoCell(
                        title = "核心版本",
                        value = coreVersion.ifBlank { "v1.18.x" },
                        icon = Icons.Default.Memory,
                        iconBg = AppPalette.iconGray,
                        showChevron = false
                    )
                }
                SeparatedRow(SeparatedRowPosition.Last) {
                    CupertinoCell(
                        title = "重置核心状态",
                        icon = Icons.Default.RestartAlt,
                        iconBg = AppPalette.iconRed,
                        showChevron = true,
                        onClick = { showResetDialog = true }
                    )
                }
            }
        }
        }
        // The live page is sampled only at this pinned top edge; the title stays plain text.
        // The blur fades in as rows pass beneath the header and dissolves into the calm page.
        if (pageBackdrop != null && topBlurReveal > 0f) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .height(headerInset + 24.dp)
                    .graphicsLayer { alpha = topBlurReveal }
                    .settingsMorphTopBackdropBlur(pageBackdrop)
                    .testTag("settings-top-glasmio-blur")
            )
        }
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(headerInset + 24.dp)
                .background(Brush.verticalGradient(listOf(
                    quietPageColor.copy(alpha = 1f - 0.32f * topBlurReveal),
                    quietPageColor.copy(alpha = 0.82f - 0.32f * topBlurReveal),
                    Color.Transparent
                )))
        )
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(quietPageColor.copy(alpha = if (topBlurReveal > 0f) 0.96f else 1f))
                .onSizeChanged { headerHeightPx = it.height }
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 12.dp)
                .zIndex(1f)
                .testTag("settings-title-header")
        ) {
            Text(
                AppStrings.get("settings_title", lang),
                modifier = Modifier.testTag("settings-title-capsule"),
                color = LoonTextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp
            )
        }
    }

    val settingsMorphPage = activeSettingsDetail ?: hiddenSettingsDetailSource
    ShortcutMorphOverlay(
        visible = activeSettingsDetail != null,
        sourceBounds = settingsMorphPage?.let { settingsDetailBounds[it] },
        liveSourceBounds = { settingsMorphPage?.let { settingsDetailBounds[it] } },
        sourceTitle = settingsMorphPage?.title ?: "设置",
        sourceSubtitle = settingsMorphPage?.subtitle.orEmpty(),
        sourceIcon = {
            Icon(settingsMorphPage?.icon ?: Icons.Default.Tune, contentDescription = null, tint = LoonTextPrimary)
        },
        lens = titleScene,
        onDismissRequest = { activeSettingsDetail = null },
        onDismissed = { hiddenSettingsDetailSource = null },
        panelTestTag = "settings-detail-page",
        contentTestTag = "settings-detail-content",
        panelHeightFraction = 0.92f,
        panelHeight = settingsMorphPage?.preferredPanelHeight,
        sideMargin = 8.dp,
        maxPanelWidth = 560.dp,
        reserveDockSpace = true,
        motionProfile = ShortcutMorphMotionProfile.QUVEN_MENU,
        linearEdgeMotion = true,
        settingsTopBackdropBlur = false,
        settingsBottomCloseFade = false,
        panelSurfaceCategory = com.github.arca.android.ui.glass.GlassSurfaceCategory.LARGE_PANELS,
        raisedPanel = true,
        sourceCornerRadius = 12.dp,
        sourceCorners = settingsMorphPage?.let { page ->
            settingsDetailRadius[page]?.let { pos ->
                val o = GlassRadius.section
                // A row of a line-divided group is a plain band; of a separated group, a card with small inner corners.
                val inner = SeparatedInnerRadius
                when (pos) {
                    SeparatedRowPosition.Only -> ShortcutMorphCorners(o, o, o, o)
                    SeparatedRowPosition.First -> ShortcutMorphCorners(o, o, inner, inner)
                    SeparatedRowPosition.Middle -> ShortcutMorphCorners(inner, inner, inner, inner)
                    SeparatedRowPosition.Last -> ShortcutMorphCorners(inner, inner, o, o)
                }
            }
        },
        sourceContent = {
            settingsMorphPage?.let { page ->
                SettingsDetailSourceFace(
                    page = page,
                    tunStack = tunStack,
                    logLevel = currentLogLevel,
                    shortcutTouchVariant = shortcutTouchVariant,
                    panelAnimationSpeed = panelAnimationSpeed,
                    tunMtu = tunMtu,
                    mipsCongestionController = mipsCongestionController,
                    themeMode = themeMode,
                    fakeIpEnabled = fakeIpEnabled,
                    modifier = Modifier.fillMaxSize().testTag("settings-detail-source-face")
                )
            }
        }
    ) {
        if (settingsMorphPage != null) {
            SettingsDetailMorphPage(
                page = settingsMorphPage,
                tunStack = tunStack,
                onTunStackChanged = onTunStackChanged,
                logLevel = currentLogLevel,
                onLogLevelChanged = { level ->
                    currentLogLevel = level
                    settingsManager.logLevel = level
                },
                shortcutTouchVariant = shortcutTouchVariant,
                onShortcutTouchVariantChanged = onShortcutTouchVariantChanged,
                panelAnimationSpeed = panelAnimationSpeed,
                onPanelAnimationSpeedChanged = onPanelAnimationSpeedChanged,
                tunMtuDraft = mtuDraft,
                onTunMtuDraftChanged = { mtuDraft = it },
                onTunMtuSaved = onTunMtuChanged,
                mipsCongestionController = mipsCongestionController,
                onMipsCongestionControllerChanged = onMipsCongestionControllerChanged,
                onClose = { activeSettingsDetail = null },
                appearanceContent = appearanceDetailContent,
                glassEffectsContent = glassEffectsDetailContent,
                dnsContent = dnsDetailContent,
                connectionsContent = connectionsDetailContent,
                logsContent = logsDetailContent,
                configurationCenterContent = configurationCenterDetailContent
            )
        }
    }

    // Core Reset Confirmation
    if (showResetDialog) {
        GlassAlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置核心状态", fontWeight = FontWeight.Bold, color = LoonTextPrimary) },
            text = { Text("确定要重置底层核心状态吗？当前活动的网络连接将被断开并重新初始化。", color = LoonTextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) { ClashCore.reset() }
                        AppNotice.show("核心已重置")
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



}


/** A descendant glass surface must never sample the scene currently recording that descendant. */
internal fun settingsSectionLens(
    recordedScene: NavigationLensState,
    externalBackdrop: NavigationLensState?
): NavigationLensState? = externalBackdrop?.takeUnless { it === recordedScene }


@Composable
private fun SettingsGroup(
    tag: String,
    title: String,
    description: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable SeparatedSectionScope.() -> Unit
) {
    val shape = RoundedCornerShape(GlassRadius.card)
    val dark = LocalLoonColors.current.isDark
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .cardDepth(shape, dark, if (dark) 3.dp else 4.dp)
            .testTag("settings-group-$tag"),
        shape = shape,
        color = LoonCard
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(64.dp).testTag("settings-group-header-$tag")) {
                SettingsGroupHeaderContent(title, description, icon, accent, false)
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                color = LoonSeparator,
                thickness = 0.5.dp
            )
            Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                SeparatedSectionScope.content()
            }
        }
    }
}

@Composable
private fun SettingsGroupHeaderContent(
    title: String,
    description: String,
    icon: ImageVector,
    accent: Color,
    glass: Boolean
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = if (glass) .16f else .12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Text(title, color = if (glass) glassTextPrimary else LoonTextPrimary,
                fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(1.dp))
            Text(description, color = if (glass) glassTextSecondary else LoonTextSecondary,
                fontSize = 11.sp, maxLines = 1)
        }
    }
}

/**
 * The row a panel grows from, drawn by the row's own component with the row's own arguments, so its icon, title,
 * subtitle, value and chevron stay exactly where they were while it lifts and grows.
 */
@Composable
private fun SettingsDetailSourceFace(
    page: SettingsDetailPage,
    tunStack: TunStack,
    logLevel: String,
    shortcutTouchVariant: ShortcutTouchVariant,
    panelAnimationSpeed: PanelAnimationSpeed,
    tunMtu: Int,
    mipsCongestionController: String,
    themeMode: ThemeMode,
    fakeIpEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (page) {
            SettingsDetailPage.TUN_STACK -> CupertinoCell(
                title = "TUN 堆栈",
                value = when (tunStack.key) {
                    "mixed" -> "Mixed"; "gvisor" -> "gVisor"; "mips" -> "MIPS"; "system" -> "System"; else -> "Mixed"
                },
                icon = Icons.Default.VpnKey, iconBg = IosBlue, showChevron = true
            )
            SettingsDetailPage.LOG_LEVEL -> CupertinoCell(
                title = "日志级别", value = logLevel.uppercase(),
                icon = Icons.Default.Tune, iconBg = AppPalette.iconIndigo, showChevron = true
            )
            SettingsDetailPage.SHORTCUT_TOUCH -> CupertinoCell(
                title = "快捷卡片触感", subtitle = "A 轻按浮起｜B 跟手倾斜｜C 浮力回弹",
                value = when (shortcutTouchVariant) {
                    ShortcutTouchVariant.GentleFloat -> "A"
                    ShortcutTouchVariant.FingerTilt -> "B"
                    ShortcutTouchVariant.BuoyantSpring -> "C"
                },
                icon = Icons.Default.TouchApp, iconBg = AppPalette.iconBlue, showChevron = true
            )
            SettingsDetailPage.PANEL_SPEED -> CupertinoCell(
                title = "面板动画速度", subtitle = "首页快捷方式与设置面板",
                value = panelAnimationSpeed.label,
                icon = Icons.Default.Speed, iconBg = AppPalette.iconBlue, showChevron = true
            )
            SettingsDetailPage.TUN_MTU -> CupertinoCell(title = "TUN MTU", value = tunMtu.toString(), showChevron = true)
            SettingsDetailPage.MIPS_CONGESTION -> CupertinoCell(title = "MIPS 拥塞控制", value = mipsCongestionController, showChevron = true)
            SettingsDetailPage.APPEARANCE -> CupertinoCell(
                title = "外观与显示",
                value = when (themeMode) {
                    ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色"
                },
                icon = Icons.Default.Palette, iconBg = AppPalette.iconPink, showChevron = true
            )
            SettingsDetailPage.GLASS_EFFECTS -> CupertinoCell(
                title = "玻璃效果", subtitle = "折射、色散、部位覆盖与跟手倾斜",
                icon = Icons.Default.AutoAwesome, iconBg = AppPalette.iconLavender, showChevron = true
            )
            SettingsDetailPage.DNS -> CupertinoCell(
                title = "DNS 设置", value = if (fakeIpEnabled) "Fake-IP" else "Redir-Host",
                icon = Icons.Default.Public, iconBg = AppPalette.iconTeal, showChevron = true
            )
            SettingsDetailPage.CONNECTIONS -> CupertinoCell(
                title = "活动连接", subtitle = "查看实时连接，可单条或全部断开",
                icon = Icons.Default.SwapHoriz, iconBg = AppPalette.iconTeal, showChevron = true
            )
            SettingsDetailPage.CORE_LOGS -> CupertinoCell(
                title = "核心日志", icon = Icons.Default.Terminal, iconBg = AppPalette.iconGray, showChevron = true
            )
            SettingsDetailPage.CONFIGURATION_CENTER -> CupertinoCell(
                title = "配置中心", subtitle = "配置文件、订阅、脚本与路由资源",
                icon = Icons.Default.FolderZip, iconBg = AppPalette.iconPurple, showChevron = true
            )
        }
    }
}
