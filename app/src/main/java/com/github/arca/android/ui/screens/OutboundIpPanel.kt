package com.github.arca.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.data.*
import com.github.arca.android.ui.glass.GlassSurface
import com.github.arca.android.ui.glass.GlassSurfaceCategory
import com.github.arca.android.ui.theme.LoonBlue
import com.github.arca.android.ui.theme.LoonTextMuted
import com.github.arca.android.ui.theme.LoonTextPrimary
import com.github.arca.android.ui.theme.LoonTextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explicit user-driven lifetime: constructing this state does not start any request. */
internal class OutboundIpLookupState {
    var result by mutableStateOf<OutboundIpResult?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null
    private var generation = 0

    fun refresh(scope: CoroutineScope, lookup: suspend ((OutboundIpInfo) -> Unit) -> OutboundIpResult) {
        if (loading) return
        val requestGeneration = ++generation
        result = null
        error = null
        loading = true
        job = scope.launch {
            try {
                val fresh = lookup { if (generation == requestGeneration) result = OutboundIpResult(it) }
                if (generation == requestGeneration) result = fresh
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generation == requestGeneration) error = safeIpLookupError(failure)
            } finally {
                if (generation == requestGeneration) loading = false
            }
        }
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        loading = false
    }

    fun reset() {
        cancel()
        result = null
        error = null
    }
}

/**
 * State of the fixed "Scamalytics API 配置" entry. It is hoisted out of the scrolling panel body so
 * the morph overlay can draw the link in its own layer, untouched by scroll or body animation.
 */
@Stable
internal class IpConfigEntryState {
    val settingsOpen = mutableStateOf(false)
    var linkEnabled by mutableStateOf(false)

    fun reset() {
        settingsOpen.value = false
        linkEnabled = false
    }
}

/** Shared body for the V93 morph column and the standalone glass sheet; owns no scroll container. */
@Composable
internal fun OutboundIpPanel(
    isVpnRunning: Boolean,
    modifier: Modifier = Modifier,
    state: OutboundIpLookupState = remember { OutboundIpLookupState() },
    repository: OutboundIpRepository = remember { OutboundIpRepository() },
    credentialStorage: ScamalyticsCredentialsStorage? = null,
    /**
     * Hoisted settings-sheet state. When a host supplies it, the host renders the
     * low-key [ScamalyticsConfigLink] fixed at the panel's bottom-right corner;
     * otherwise the panel keeps its own state and shows no entry.
     */
    settingsOpenState: MutableState<Boolean>? = null,
    /** Reports when the fixed config entry may be tapped (credentials loaded, idle). */
    onConfigLinkAvailabilityChanged: ((Boolean) -> Unit)? = null,
    onRefresh: (suspend () -> OutboundIpInfo)? = null
) {
    val context = LocalContext.current
    val storage = credentialStorage ?: remember(context) { ScamalyticsCredentialStore(context) }
    val scope = rememberCoroutineScope()
    var credentials by remember { mutableStateOf<ScamalyticsCredentials?>(null) }
    var credentialsReady by remember { mutableStateOf(false) }
    var credentialBusy by remember { mutableStateOf(false) }
    val settingsState = settingsOpenState ?: remember { mutableStateOf(false) }
    var settingsOpen by settingsState
    var credentialError by remember { mutableStateOf<String?>(null) }
    var credentialNotice by remember { mutableStateOf<String?>(null) }
    var testMode by remember { mutableStateOf(false) }
    var credentialsUnreadable by remember { mutableStateOf(false) }

    LaunchedEffect(storage) {
        try {
            when (val loaded = withContext(Dispatchers.IO) { storage.loadState() }) {
                is CredentialLoadState.Present -> credentials = loaded.credentials
                CredentialLoadState.Absent -> Unit
                CredentialLoadState.Unreadable -> {
                    // Never silently treated as "not configured": the lookup reports it instead.
                    credentialsUnreadable = true
                    credentialNotice = "本机保存的凭据无法读取，请重新配置。"
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            credentialNotice = "无法读取本机凭据，请重新配置。"
        } finally {
            credentialsReady = true
        }
    }
    // Changing route invalidates the displayed IP as well as any in-flight request.
    DisposableEffect(isVpnRunning, state) {
        state.reset()
        onDispose { state.reset() }
    }
    LaunchedEffect(credentialsReady, state.loading, credentialBusy) {
        onConfigLinkAvailabilityChanged?.invoke(credentialsReady && !state.loading && !credentialBusy)
    }
    // A previous save failure must not greet the user again on the next open.
    LaunchedEffect(settingsOpen) {
        if (settingsOpen) credentialError = null
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("手动检测当前出口；打开面板和保存凭据不会发起查询。", color = LoonTextSecondary, fontSize = 12.sp)
        IpPanelCard(Modifier.testTag("quick-ip-current-window")) {
            Text("IP 测试 · ${if (state.loading) "检测中" else "就绪"}", color = LoonTextPrimary,
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(if (isVpnRunning) "查询路径：VPN 代理" else "查询路径：当前网络",
                color = LoonTextMuted, fontSize = 11.sp)
            Text(when {
                !credentialsReady -> "正在读取本机配置…"
                credentials == null -> "内置检测：无需配置凭据，手动检测可查看风险详情。"
                else -> "Scamalytics API 已配置 · ${credentials!!.endpoint.label}（优先使用官方 API）"
            }, color = LoonTextMuted, fontSize = 11.sp)
            if (testMode && credentials != null) {
                Text("测试模式已开启 · 本次仅验证 API", color = LoonTextSecondary, fontSize = 11.sp)
            }
            Button(
                onClick = {
                    val selectedState: CredentialLoadState = credentials?.let { CredentialLoadState.Present(it) }
                        ?: if (credentialsUnreadable) CredentialLoadState.Unreadable else CredentialLoadState.Absent
                    val selectedTest = testMode
                    state.refresh(scope) { onIpResolved ->
                        if (onRefresh != null) OutboundIpResult(onRefresh())
                        else repository.lookupWithRisk(isVpnRunning, selectedState, selectedTest, onIpResolved)
                    }
                },
                enabled = credentialsReady && !state.loading && !credentialBusy,
                modifier = Modifier.fillMaxWidth().testTag("quick-ip-refresh"),
                colors = ButtonDefaults.buttonColors(containerColor = LoonBlue)
            ) { Text(if (state.loading) "检测中…" else if (state.result != null) "重新检测 IP" else "检测当前 IP") }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            credentialNotice?.let { Text(it, color = LoonTextSecondary, fontSize = 12.sp) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            state.result?.outbound?.let { ip ->
                Text(ip.ipAddress, color = LoonTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp))
                val place = listOfNotNull(ip.country, ip.city).joinToString(" · ")
                val network = listOfNotNull(ip.organization, ip.asn).joinToString(" · ")
                IpPanelLine(place.ifBlank { "位置未知（来源未返回）" })
                IpPanelLine(network.ifBlank { "运营商未知（来源未返回）" })
                val extra = listOfNotNull(
                    ip.level?.let { outboundIpClassification(it, ip.type) },
                    ip.flags?.let { outboundIpFlagsSummary(it) }
                ).joinToString(" · ")
                if (extra.isNotBlank()) IpPanelLine(extra)
                Text("出口来源：${ip.source}", color = LoonTextMuted, fontSize = 11.sp)
            }
        }
        IpPanelCard(Modifier.testTag("quick-ip-details-window")) {
            Text("检测详情", color = LoonTextPrimary, fontWeight = FontWeight.SemiBold)
            val result = state.result
            val risk = result?.scamalytics
            when {
                result?.riskError != null -> Text("已保留本次出口 IP。${result.riskError}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                risk != null -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IpStatTile("风险评分", displayFraudScore(risk.score)?.toString() ?: "未知", Modifier.weight(1f))
                        IpStatTile("风险等级", when (risk.risk) {
                            "low" -> "低"; "medium" -> "中"; "high" -> "高"; "very high" -> "很高"
                            else -> "未知"
                        }, Modifier.weight(1f))
                    }
                    if (risk.mode == "test") Text("测试响应用于验证凭据和接口，不作为实时风险结论。", color = LoonTextSecondary, fontSize = 12.sp)
                }
                state.loading && result != null -> Text("已获取出口 IP，正在查询风险详情…", color = LoonTextSecondary, fontSize = 12.sp)
                else -> Text("尚未检测。", color = LoonTextSecondary, fontSize = 12.sp)
            }
            IpIndicatorGroup("Hosting / 代理", risk?.hostingProxySignals)
            IpIndicatorGroup("黑名单标记", risk?.blacklistMarkers)
            val proxyType = when {
                risk?.proxyType != null -> "${risk.proxyType} · ${risk.proxyTypeSource}"
                risk?.proxyTypeRequiresPremium == true -> "需 Premium（来源未提供实际值）"
                risk != null && risk.mode == "web" -> null
                else -> "未知（来源未返回）"
            }
            if (proxyType != null || risk == null) IpPanelField("代理类型", proxyType ?: "未知（尚无本次数据）")
            if (risk != null) {
                val detailLines = buildList {
                    if (risk.mode != "web") {
                        add("IPsum 黑名单数量：${risk.blacklistCount ?: "未知"}")
                        risk.ip2proxyBlacklistType?.let { add("IP2Proxy Lite 黑名单类型：$it") }
                        risk.usageType?.let { add("用途类型：$it") }
                        risk.creditsRemaining?.let { add("剩余额度（约）：$it") }
                    }
                    val outboundOrg = result?.outbound?.organization
                    val isp = premiumText(risk.isp, risk.ispRequiresPremium)
                    val org = premiumText(risk.organization, risk.organizationRequiresPremium)
                    if (risk.isp == null || risk.isp != outboundOrg) add("ISP：$isp")
                    if (risk.organization != null && risk.organization != risk.isp && risk.organization != outboundOrg) add("组织：$org")
                }
                detailLines.forEach { IpPanelLine(it) }
            }
            Text(
                "数据来源：Scamalytics" + when (risk?.mode) {
                    "web" -> " · 公开网页"
                    "live" -> " · 官方 API"
                    "test" -> " · 官方 API（测试）"
                    else -> ""
                },
                color = LoonTextMuted, fontSize = 11.sp
            )
        }
        // Advanced API option follows the results, beside the low-priority configuration entry.
        // It shares the same state used at request start; moving it must never trigger a query.
        if (credentials != null) {
            Row(
                Modifier.fillMaxWidth().testTag("scamalytics-test-options"),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = testMode,
                    onCheckedChange = { testMode = it },
                    enabled = !state.loading && !credentialBusy,
                    modifier = Modifier.testTag("scamalytics-test-mode")
                )
                Column {
                    Text("测试模式", color = LoonTextSecondary, fontSize = 12.sp)
                    Text("验证 API · 不消耗实时额度", color = LoonTextMuted, fontSize = 11.sp)
                }
            }
        }
        // Room under the last row for the fixed bottom-right API entry.
        Spacer(Modifier.height(18.dp))
    }

    if (settingsOpen) {
        ScamalyticsSettingsSheet(
            initialUsername = credentials?.username.orEmpty(),
            initialEndpoint = credentials?.endpoint ?: ScamalyticsEndpoint.US,
            hasSavedCredentials = credentials != null,
            saving = credentialBusy,
            errorMessage = credentialError,
            onSave = { submitted ->
                if (!credentialBusy) scope.launch {
                    credentialBusy = true
                    credentialError = null
                    try {
                        val saved = withContext(Dispatchers.IO) {
                            submitted.validated().also { storage.save(it) }
                        }
                        credentials = saved
                        credentialsUnreadable = false
                        state.reset()
                        credentialNotice = "凭据已加密保存。点击检测才会查询。"
                        settingsOpen = false
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        credentialError = "无法保存凭据，请检查输入与本机安全存储后重试。"
                    } finally { credentialBusy = false }
                }
            },
            onDelete = {
                if (!credentialBusy) scope.launch {
                    credentialBusy = true
                    credentialError = null
                    try {
                        withContext(Dispatchers.IO) { storage.clear() }
                        credentials = null
                        credentialsUnreadable = false
                        state.reset()
                        credentialNotice = "本机凭据已删除。"
                        settingsOpen = false
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        credentialError = "无法删除本机凭据，请重试。"
                    } finally { credentialBusy = false }
                }
            },
            onDismissRequest = { settingsOpen = false }
        )
    }
}

internal fun List<ScamalyticsIndicator>.indicatorSummary(): String = joinToString("\n") {
    "${it.source} · ${it.label}：${when (it.detected) { true -> "命中"; false -> "未命中"; null -> "未知（来源未返回）" }}"
}

private fun premiumText(value: String?, premium: Boolean): String =
    value ?: if (premium) "需 Premium（来源未提供实际值）" else "未知（来源未返回）"

/**
 * Low-key entry to the optional Scamalytics API credentials. Small muted text, no
 * button chrome: the built-in check needs no credentials, so this stays out of the way.
 * Hosts render it fixed at the panel's bottom-right corner, outside any scroll container.
 */
@Composable
internal fun ScamalyticsConfigLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Text(
        text = "Scamalytics API 配置",
        color = LoonTextMuted,
        fontSize = 10.5.sp,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp).testTag("scamalytics-config-link")
    )
}

@Composable
private fun IpPanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        surfaceCategory = GlassSurfaceCategory.SHORTCUT_CARDS
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            content = content
        )
    }
}

@Composable
private fun IpPanelLine(text: String) {
    Text(text, color = LoonTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
private fun IpStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(LoonTextPrimary.copy(alpha = 0.07f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = LoonTextSecondary, fontSize = 11.sp)
        Text(value, color = LoonTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

private val IpHitColor = Color(0xFFFF7A59)

/**
 * One heading, then only what matters: hits as highlighted chips, everything clean folded into a
 * single muted line, unknowns folded into another. Missing data is never counted as a clean result.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IpIndicatorGroup(title: String, items: List<ScamalyticsIndicator>?) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = LoonTextSecondary, fontSize = 12.sp)
        if (items == null) {
            Text("未知（尚无本次数据）", color = LoonTextPrimary, fontSize = 13.sp)
            return@Column
        }
        val hits = items.filter { it.detected == true }
        val clean = items.filter { it.detected == false }
        val unknown = items.filter { it.detected == null }
        if (hits.isNotEmpty()) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            hits.forEach {
                Text(indicatorName(it) + " · 命中", color = LoonTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(IpHitColor.copy(alpha = 0.22f))
                        .padding(horizontal = 9.dp, vertical = 4.dp))
            }
        }
        if (clean.isNotEmpty()) Text(
            "未命中 ${clean.size} 项：" + clean.joinToString("、") { indicatorName(it) },
            color = LoonTextSecondary, fontSize = 12.sp, lineHeight = 17.sp
        )
        if (unknown.isNotEmpty()) Text(
            "未知：" + unknown.joinToString("、") { indicatorName(it) },
            color = LoonTextMuted, fontSize = 12.sp, lineHeight = 17.sp
        )
    }
}

/** Source prefix is dropped when it is just "Scamalytics" (the footer already names it). */
private fun indicatorName(i: ScamalyticsIndicator): String =
    if (i.source == "Scamalytics") i.label else "${i.source}${if (i.label == "黑名单") "" else " ${i.label}"}"

@Composable
private fun IpPanelField(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = LoonTextSecondary, fontSize = 12.sp)
        Text(value, color = LoonTextPrimary, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
