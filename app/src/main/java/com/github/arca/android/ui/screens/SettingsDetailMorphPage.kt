package com.github.arca.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.data.PanelAnimationSpeed
import com.github.arca.android.data.TunStack
import com.github.arca.android.data.TunTuning
import com.github.arca.android.ui.components.ShortcutTouchVariant
import com.github.arca.android.ui.glass.GlassSurface
import com.github.arca.android.ui.glass.GlassSurfaceCategory
import com.github.arca.android.ui.theme.IosBlue
import com.github.arca.android.ui.theme.LoonTextMuted
import com.github.arca.android.ui.theme.LoonTextPrimary
import com.github.arca.android.ui.theme.LoonTextSecondary

internal enum class SettingsDetailPage(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    TUN_STACK("TUN 协议栈", "为当前连接选择网络实现", Icons.Default.VpnKey),
    LOG_LEVEL("日志详细程度", "选择核心日志记录级别", Icons.Default.Terminal),
    SHORTCUT_TOUCH("快捷卡片触感", "选择按压与回弹的动态反馈", Icons.Default.TouchApp),
    PANEL_SPEED("面板动画速度", "首页快捷方式与设置面板", Icons.Default.Speed),
    TUN_MTU("TUN MTU", "调整隧道传输单元大小", Icons.Default.Tune),
    MIPS_CONGESTION("MIPS 拥塞控制", "选择连接拥塞控制算法", Icons.Default.Memory),
    APPEARANCE("外观与显示", "主题、壁纸与策略节点排版", Icons.Default.Palette),
    GLASS_EFFECTS("玻璃效果", "主材质、光学与跟手动态", Icons.Default.AutoAwesome),
    DNS("DNS 设置", "Fake-IP、解析诊断与 DNS 记录", Icons.Default.Public),
    CONNECTIONS("活动连接", "查看实时连接并管理断开操作", Icons.Default.SwapHoriz),
    CORE_LOGS("核心日志", "查看、筛选与导出核心运行记录", Icons.Default.Terminal),
    CONFIGURATION_CENTER("配置中心", "配置文件、订阅、脚本与路由资源", Icons.Default.FolderZip)
}

/** A short panel fits its controls; option-heavy pages grow until the overlay's safe-height cap. */
internal val SettingsDetailPage.preferredPanelHeight: androidx.compose.ui.unit.Dp
    get() {
        val optionCount = when (this) {
            SettingsDetailPage.TUN_STACK, SettingsDetailPage.LOG_LEVEL -> 4
            SettingsDetailPage.SHORTCUT_TOUCH, SettingsDetailPage.PANEL_SPEED -> 3
            SettingsDetailPage.MIPS_CONGESTION -> TunTuning.CONGESTION_CONTROLLERS.size
            else -> 0
        }
        return when (this) {
            SettingsDetailPage.TUN_MTU -> 280.dp
            SettingsDetailPage.APPEARANCE -> 620.dp
            SettingsDetailPage.GLASS_EFFECTS -> 680.dp
            SettingsDetailPage.DNS -> 620.dp
            SettingsDetailPage.CONNECTIONS -> 660.dp
            SettingsDetailPage.CORE_LOGS -> 660.dp
            SettingsDetailPage.CONFIGURATION_CENTER -> 620.dp
            else -> {
                // Includes the 72dp minimum option card, inter-card gaps, header, panel
                // insets, and a visible scroll-content tail instead of ending on the last row.
                120.dp + 72.dp * optionCount.toFloat() + 12.dp * (optionCount - 1).coerceAtLeast(0).toFloat()
            }
        }
    }

@Composable
internal fun SettingsDetailMorphPage(
    page: SettingsDetailPage,
    tunStack: TunStack,
    onTunStackChanged: (TunStack) -> Unit,
    logLevel: String,
    onLogLevelChanged: (String) -> Unit,
    shortcutTouchVariant: ShortcutTouchVariant,
    onShortcutTouchVariantChanged: (ShortcutTouchVariant) -> Unit,
    tunMtuDraft: String,
    onTunMtuDraftChanged: (String) -> Unit,
    onTunMtuSaved: (Int) -> Unit,
    mipsCongestionController: String,
    onMipsCongestionControllerChanged: (String) -> Unit,
    onClose: () -> Unit,
    appearanceContent: (@Composable () -> Unit)? = null,
    glassEffectsContent: (@Composable () -> Unit)? = null,
    dnsContent: (@Composable () -> Unit)? = null,
    connectionsContent: (@Composable () -> Unit)? = null,
    logsContent: (@Composable () -> Unit)? = null,
    configurationCenterContent: (@Composable (onClose: () -> Unit) -> Unit)? = null,
    panelAnimationSpeed: PanelAnimationSpeed = PanelAnimationSpeed.MEDIUM,
    onPanelAnimationSpeedChanged: (PanelAnimationSpeed) -> Unit = {}
) {
    val header: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().testTag("settings-detail-fixed-header"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(page.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = LoonTextPrimary)
                Text(page.subtitle, fontSize = 12.sp, color = LoonTextSecondary)
            }
            IconButton(onClick = onClose, modifier = Modifier.testTag("settings-detail-back")) {
                Icon(Icons.Default.Close, contentDescription = "返回设置", tint = LoonTextSecondary)
            }
        }
    }
    if (page == SettingsDetailPage.APPEARANCE || page == SettingsDetailPage.GLASS_EFFECTS) {
        // Scrolling bodies run under the pinned title and dissolve into a progressive blur.
        Box(Modifier.fillMaxSize().testTag("settings-detail-content-body")) {
            SettingsDetailEdgeViewport(header = header, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().testTag("settings-detail-scroll-content")) {
                    if (page == SettingsDetailPage.APPEARANCE) appearanceContent?.invoke()
                    else glassEffectsContent?.invoke()
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().testTag("settings-detail-content-body")) {
        header()
        Spacer(Modifier.height(12.dp))
        when (page) {
            SettingsDetailPage.APPEARANCE -> Box(
                Modifier.weight(1f).fillMaxWidth().testTag("settings-detail-scroll-content")
            ) { appearanceContent?.invoke() }
            SettingsDetailPage.GLASS_EFFECTS -> Box(
                Modifier.weight(1f).fillMaxWidth().testTag("settings-detail-scroll-content")
            ) { glassEffectsContent?.invoke() }
            SettingsDetailPage.DNS -> Box(
                Modifier.weight(1f).fillMaxWidth().testTag("settings-detail-scroll-content")
            ) { dnsContent?.invoke() }
            SettingsDetailPage.CONNECTIONS -> Box(
                Modifier.weight(1f).fillMaxWidth().testTag("settings-detail-scroll-content")
            ) { connectionsContent?.invoke() }
            SettingsDetailPage.CORE_LOGS -> Box(
                Modifier.weight(1f).fillMaxWidth().testTag("settings-detail-scroll-content")
            ) { logsContent?.invoke() }
            SettingsDetailPage.CONFIGURATION_CENTER -> Box(
                Modifier.weight(1f).fillMaxWidth().testTag("settings-detail-scroll-content")
            ) { configurationCenterContent?.invoke(onClose) }
            else -> Column(
                Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
                    .testTag("settings-detail-scroll-content"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (page) {
                SettingsDetailPage.TUN_STACK -> {
                    val stacks = listOf(
                        Triple("mixed", TunStack.MIXED, "兼顾性能与稳定性的混合模式"),
                        Triple("gvisor", TunStack.GVISOR, "用户态网络协议栈隔离"),
                        Triple("system", TunStack.SYSTEM, "操作系统原生网络栈"),
                        Triple("mips", TunStack.MIPS, "多接口代理协议栈")
                    )
                    stacks.forEachIndexed { index, (key, stack, description) ->
                        val label = when (key) {
                            "mixed" -> "Mixed"
                            "gvisor" -> "gVisor"
                            "system" -> "System"
                            else -> "MIPS"
                        }
                        SettingsDetailOption(
                            title = label,
                            subtitle = description,
                            selected = tunStack.key.equals(key, ignoreCase = true),
                            tag = "settings-detail-tun-$key"
                        ) {
                            onTunStackChanged(stack)
                            onClose()
                        }
                    }
                }
                SettingsDetailPage.LOG_LEVEL -> {
                    listOf(
                        Triple("info", "INFO", "记录标准网络调度与运行输出"),
                        Triple("warning", "WARN", "仅记录警告告警与异常"),
                        Triple("error", "ERROR", "仅记录严重崩溃与错误"),
                        Triple("debug", "DEBUG", "输出底层全部调试报文")
                    ).forEach { (key, label, description) ->
                        SettingsDetailOption(
                            title = label,
                            subtitle = description,
                            selected = logLevel.equals(key, ignoreCase = true),
                            tag = "settings-detail-log-$key"
                        ) {
                            onLogLevelChanged(key)
                            onClose()
                        }
                    }
                }
                SettingsDetailPage.SHORTCUT_TOUCH -> {
                    listOf(
                        ShortcutTouchVariant.GentleFloat to ("A 轻按浮起" to "轻柔抬升，动作克制"),
                        ShortcutTouchVariant.FingerTilt to ("B 跟手倾斜" to "随手指方向产生倾斜"),
                        ShortcutTouchVariant.BuoyantSpring to ("C 浮力回弹" to "更明显的浮起与弹性回落")
                    ).forEachIndexed { index, (variant, labels) ->
                        SettingsDetailOption(
                            title = labels.first,
                            subtitle = labels.second,
                            selected = shortcutTouchVariant == variant,
                            tag = "settings-detail-touch-$index"
                        ) {
                            onShortcutTouchVariantChanged(variant)
                            onClose()
                        }
                    }
                }
                SettingsDetailPage.PANEL_SPEED -> {
                    PanelAnimationSpeed.entries.forEach { speed ->
                        SettingsDetailOption(
                            title = speed.label,
                            subtitle = when (speed) {
                                PanelAnimationSpeed.FAST -> "快速展开与收起"
                                PanelAnimationSpeed.MEDIUM -> "适中的展开与收起速度"
                                PanelAnimationSpeed.SLOW -> "舒缓的展开与收起"
                            },
                            selected = panelAnimationSpeed == speed,
                            tag = "settings-detail-panel-speed-${speed.name.lowercase(java.util.Locale.ROOT)}"
                        ) {
                            onPanelAnimationSpeedChanged(speed)
                            onClose()
                        }
                    }
                }
                SettingsDetailPage.TUN_MTU -> {
                    Text(
                        "请输入 576–9000 之间的数值；保存时仍会经过原有 MTU 规范化逻辑。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = LoonTextSecondary
                    )
                    OutlinedTextField(
                        value = tunMtuDraft,
                        onValueChange = { value ->
                            if (value.length <= 5 && value.all(Char::isDigit)) onTunMtuDraftChanged(value)
                        },
                        singleLine = true,
                        label = { Text("MTU") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("settings-detail-mtu-input")
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onClose,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.14f), contentColor = LoonTextPrimary)
                        ) { Text("取消") }
                        Button(
                            onClick = {
                                tunMtuDraft.toIntOrNull()?.let { onTunMtuSaved(TunTuning.normalizeMtu(it)) }
                                onClose()
                            },
                            modifier = Modifier.weight(1f).testTag("settings-detail-mtu-save"),
                            colors = ButtonDefaults.buttonColors(containerColor = IosBlue.copy(alpha = 0.86f))
                        ) { Text("保存") }
                    }
                }
                SettingsDetailPage.MIPS_CONGESTION -> {
                    TunTuning.CONGESTION_CONTROLLERS.forEachIndexed { index, option ->
                        SettingsDetailOption(
                            title = option,
                            subtitle = when (option) {
                                "bbr" -> "面向高带宽与长延迟链路"
                                "cubic" -> "通用拥塞控制，兼容性较好"
                                else -> "使用内核提供的 $option 控制策略"
                            },
                            selected = option == mipsCongestionController,
                            tag = "settings-detail-congestion-$index"
                        ) {
                            onMipsCongestionControllerChanged(option)
                            onClose()
                        }
                    }
                }
                SettingsDetailPage.APPEARANCE,
                SettingsDetailPage.GLASS_EFFECTS,
                SettingsDetailPage.DNS,
                SettingsDetailPage.CONNECTIONS,
                SettingsDetailPage.CORE_LOGS,
                SettingsDetailPage.CONFIGURATION_CENTER -> Unit
                }
            }
        }
    }
}

@Composable
private fun SettingsDetailOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    GlassSurface(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = shape,
        accent = if (selected) IosBlue else null,
        surfaceCategory = GlassSurfaceCategory.SHORTCUT_CARDS,
        surfaceAlphaScale = if (selected) 1f else 0.78f
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = LoonTextPrimary)
                Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = LoonTextSecondary)
            }
            if (selected) Text("已选", color = IosBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            else Text("选择", color = LoonTextMuted, fontSize = 11.sp)
        }
    }
}
