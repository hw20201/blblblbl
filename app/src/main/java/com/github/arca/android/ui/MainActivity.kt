package com.github.arca.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.github.arca.android.ui.theme.LocalAppLanguage
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.arca.android.core.ClashCore
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.data.*
import com.github.arca.android.service.MihomoVpnService
import com.github.arca.android.service.VpnState
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.github.arca.android.ui.glass.GlassSettings
import com.github.arca.android.ui.glass.GlassSurfaceCategory
import com.github.arca.android.ui.glass.LocalGlassSettings
import com.github.arca.android.ui.glass.GlassPalettes
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.GlassWallpaperSettings
import com.github.arca.android.ui.glass.glassPalette
import com.github.arca.android.ui.glass.NexusGlassBackground
import com.github.arca.android.ui.glass.ProvideArcaGlasmioBackdrop
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.glass.ProvideSoftMeshMotion
import com.github.arca.android.ui.glass.rememberArcaGlasmioBackdrop
import com.github.arca.android.ui.glass.recordArcaGlasmioBackdropSource
import com.github.arca.android.ui.glass.rememberNavigationLens
import com.github.arca.android.ui.glass.NavigationLensGlobalBounds
import com.github.arca.android.ui.glass.recordNavigationScene
import com.github.arca.android.ui.components.LoonFloatingBottomBar
import com.github.arca.android.ui.components.BOTTOM_BAR_BOTTOM_MARGIN
import com.github.arca.android.ui.components.MainDockContentViewport
import com.github.arca.android.ui.components.LocalHapticFeedbackEnabled
import com.github.arca.android.ui.components.LocalShortcutTouchVariant
import com.github.arca.android.ui.components.ShortcutTouchVariant
import com.github.arca.android.ui.components.LocalShortcutMotionEnabled
import com.github.arca.android.ui.components.rememberShortcutAnimationsEnabled
import io.github.neoruaa.glasmio.glass.rememberSharedGlassBackdrop
import com.github.arca.android.ui.screens.*
import com.github.arca.android.ui.theme.LoonBg
import com.github.arca.android.ui.theme.MihomoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.github.arca.android.ui.components.AppNotice
import com.github.arca.android.ui.components.AppNoticeHost

enum class SubScreen {
    TRAFFIC,
    CONFIGURATION_CENTER,
    PROFILES,
    APP_ROUTING,
    LOGS,
    SCRIPTS,
    PROFILE_OVERRIDE,
    RULES,
    DNS,
    REQUESTS,
    NETWORK_SHARE,
    APPEARANCE,
    GLASS_EFFECTS,
    CONNECTIONS
}

internal data class SubScreenBackdropPolicy(
    val useRootWallpaperBackdrop: Boolean,
    val drawWallpaperBackground: Boolean
)

@Suppress("UNUSED_PARAMETER")
internal fun subScreenBackdropPolicy(
    screen: SubScreen,
    apiLevel: Int,
    pageBackdropAvailable: Boolean
): SubScreenBackdropPolicy {
    return SubScreenBackdropPolicy(
        useRootWallpaperBackdrop = true,
        drawWallpaperBackground = true
    )
}

internal fun shouldHideSettingsParentContent(
    subScreenActive: Boolean,
    currentPage: Int,
    targetPage: Int
): Boolean = subScreenActive && (currentPage == 2 || targetPage == 2)

/** Persists the current sub-screen across configuration changes. */
private val subScreenSaver: Saver<SubScreen?, String> = Saver(
    save = { it?.name },
    restore = { name -> runCatching { SubScreen.valueOf(name) }.getOrNull() }
)

@Suppress("LargeClass")
class MainActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            MihomoVpnService.start(this)
        } else {
            AppNotice.show("需要授予 VPN 权限才能建立代理")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional handler */ }

    /** Link handed over by clash://install-config; confirmed by the user before downloading. */
    private val pendingSubscriptionLink = kotlinx.coroutines.flow.MutableStateFlow<SubscriptionLink?>(null)

    private fun handleIncomingIntent(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme?.lowercase() ?: return
        if (scheme !in SubscriptionLinkParser.SCHEMES) return
        val link = SubscriptionLinkParser.parse(data)
        if (link == null) {
            AppNotice.error("无法识别订阅链接：仅支持 HTTPS 订阅地址")
        } else {
            pendingSubscriptionLink.value = link
        }
        // Do not re-trigger the import on rotation / recreation.
        intent?.data = null
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIncomingIntent(intent)
        // Pages draw into the system-bar areas; each screen owns its own inset handling.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        settingsManager = SettingsManager(this)
        // Use the fixed brand glass surface palette; the app backdrop is the saved custom wallpaper.
        GlassDefaults.palette = GlassPalettes.CREAM
        GlassDefaults.wallpaper = settingsManager.glassWallpaperSettings

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            var themeMode by remember { mutableStateOf(settingsManager.themeMode) }
            MihomoTheme(themeMode = themeMode) {
                MainAppScreen(
                    currentThemeMode = themeMode,
                    onThemeModeChanged = { newMode ->
                        settingsManager.themeMode = newMode
                        themeMode = newMode
                    }
                )
            }
        }
    }

    private fun handleToggleVpn() {
        val currentStatus = MihomoVpnService.vpnState.value.status
        if (currentStatus == VpnState.Status.STOPPING) return
        if (currentStatus == VpnState.Status.RUNNING || currentStatus == VpnState.Status.STARTING) {
            MihomoVpnService.stop(this)
        } else {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                vpnPermissionLauncher.launch(vpnIntent)
            } else {
                MihomoVpnService.start(this)
            }
        }
    }

    @Composable
    private fun MainAppScreen(
        currentThemeMode: ThemeMode,
        onThemeModeChanged: (ThemeMode) -> Unit
    ) {
        // Primary Tabs: 0: 仪表, 1: 策略, 2: 配置
        var currentTab by rememberSaveable { mutableStateOf(0) }
        var currentSubScreen by rememberSaveable(stateSaver = subScreenSaver) {
            mutableStateOf<SubScreen?>(null)
        }
        var fromConfigurationCenter by rememberSaveable { mutableStateOf(false) }
        var overrideTargetProfileId by rememberSaveable { mutableStateOf<String?>(null) }
        var overrideTargetProfile by remember { mutableStateOf<ProfileItem?>(null) }

        // Connection state changes only on lifecycle transitions; traffic samples use a
        // separate flow so they cannot invalidate the app shell or navigation tree.
        val vpnState by MihomoVpnService.vpnState.collectAsState()
        LaunchedEffect(vpnState.error) {
            val error = vpnState.error?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
            AppNotice.error(error)
        }
        val requestMonitoringEnabled by RequestLogRepository.monitoringEnabled.collectAsState()

        var profiles by remember { mutableStateOf(emptyList<ProfileItem>()) }
        var selectedProfileId by remember { mutableStateOf(settingsManager.selectedProfileId) }
        var tunnelMode by remember { mutableStateOf(TunnelMode.fromString(settingsManager.tunnelMode)) }
        var appLanguage by remember { mutableStateOf(settingsManager.appLanguage) }

        // Listing the profile directory touches the disk, so it is not composition work.
        LaunchedEffect(Unit) {
            profiles = withContext(Dispatchers.IO) { ConfigManager.getProfiles(this@MainActivity) }
        }

        // Re-resolve the override target after rotation, when only its id could be restored.
        LaunchedEffect(overrideTargetProfileId, profiles) {
            if (overrideTargetProfileId == null) {
                overrideTargetProfile = null
            } else if (overrideTargetProfile?.id != overrideTargetProfileId) {
                overrideTargetProfile = profiles.find { it.id == overrideTargetProfileId }
            }
        }

        var parsedProfile by remember { mutableStateOf(ParsedProfile()) }
        var proxyGroups by remember { mutableStateOf(listOf<String>()) }
        var liveGroupMap by remember { mutableStateOf<Map<String, ProxyGroup>>(emptyMap()) }
        var selectedGroupName by rememberSaveable { mutableStateOf("") }
        var currentGroupData by remember { mutableStateOf<ProxyGroup?>(null) }
        var activeNodeName by remember { mutableStateOf("") }

        val mainProxyGroupName = remember(proxyGroups, parsedProfile.groups) {
            proxyGroups.firstOrNull { it.equals("节点选择", ignoreCase = true) }
                ?: proxyGroups.firstOrNull { it.equals("PROXY", ignoreCase = true) }
                ?: proxyGroups.firstOrNull { it.equals("Proxies", ignoreCase = true) }
                ?: proxyGroups.firstOrNull { it.equals("Proxy", ignoreCase = true) }
                ?: proxyGroups.firstOrNull { parsedProfile.groups[it]?.hidden != true }
                ?: proxyGroups.firstOrNull() ?: ""
        }

        var visibleShortcuts by remember { mutableStateOf(settingsManager.visibleShortcuts) }
        var shortcutOrder by remember { mutableStateOf(settingsManager.shortcutOrder) }
        var proxiesViewMode by remember { mutableStateOf(settingsManager.proxiesViewMode) }
        var showGroupIcons by remember { mutableStateOf(settingsManager.showGroupIcons) }
        var nodeColumns by remember { mutableIntStateOf(settingsManager.nodeColumns) }
        var scriptingEnabled by remember { mutableStateOf(settingsManager.scriptingEnabled) }
        var rewriteEnabled by remember { mutableStateOf(settingsManager.rewriteEnabled) }

        var perAppMode by remember { mutableStateOf(settingsManager.perAppProxyMode) }
        var selectedPackages by remember { mutableStateOf(settingsManager.selectedPackages) }
        var installedApps by remember { mutableStateOf(listOf<InstalledApp>()) }
        var isAppsLoading by remember { mutableStateOf(false) }

        var tunStack by remember { mutableStateOf(settingsManager.tunStack) }
        var bootOnStartup by remember { mutableStateOf(settingsManager.bootOnStartup) }
        var allowLan by remember { mutableStateOf(settingsManager.allowLan) }
        var lanCredentials by remember { mutableStateOf(settingsManager.lanCredentials()) }
        var fakeIpEnabled by remember { mutableStateOf(settingsManager.fakeIpEnabled) }
        var hapticEnabled by remember { mutableStateOf(settingsManager.hapticEnabled) }
        var shortcutTouchVariant by remember { mutableStateOf(settingsManager.shortcutTouchVariant) }
        var glassSettings by remember { mutableStateOf(settingsManager.glassSettings) }
        var ipv6Enabled by remember { mutableStateOf(settingsManager.ipv6Enabled) }
        var unifiedDelayEnabled by remember { mutableStateOf(settingsManager.unifiedDelayEnabled) }
        var profileHotSwitchEnabled by remember { mutableStateOf(settingsManager.profileHotSwitchEnabled) }
        var tunMtu by remember { mutableIntStateOf(settingsManager.tunMtu) }
        var mipsCongestionController by remember { mutableStateOf(settingsManager.mipsCongestionController) }
        val settingsScrollState = rememberScrollState()
        val dashboardScrollState = rememberScrollState()
        val configurationCenterScrollState = rememberScrollState()
        val appearanceListState = rememberLazyListState()
        val glassEffectsScrollState = rememberScrollState()
        val proxiesTabsListState = rememberLazyListState()
        val proxiesListModeState = rememberLazyListState()
        val proxiesGroupRowState = rememberLazyListState()
        val proxiesExpandedGroups = rememberSaveable(
            saver = Saver<SnapshotStateMap<String, Boolean>, HashMap<String, Boolean>>(
                save = { HashMap(it) },
                restore = { saved ->
                    mutableStateMapOf<String, Boolean>().apply {
                        putAll(saved)
                    }
                }
            )
        ) { mutableStateMapOf<String, Boolean>() }
        val proxiesPerGroupTabStates = remember { mutableStateMapOf<String, LazyListState>() }
        var testingNodes by remember { mutableStateOf(setOf<String>()) }
        val pagerState = rememberPagerState(initialPage = currentTab, pageCount = { 3 })
        val coroutineScope = rememberCoroutineScope()
        LaunchedEffect(pagerState.currentPage) {
            if (currentTab != pagerState.currentPage) {
                currentTab = pagerState.currentPage
            }
        }

        // Persistent node selection handler (Instant 0-delay optimistic update)
        fun handleSelectProxy(group: String, proxy: Proxy) {
            settingsManager.setSelectedNode(group, proxy.name)
            if (group == mainProxyGroupName) {
                activeNodeName = proxy.name
            }
            if (group == selectedGroupName) {
                currentGroupData = currentGroupData?.copy(now = proxy.name)
            }

            // 1. Update in parsedProfile map
            val existingGroup = parsedProfile.groups[group]
            if (existingGroup != null) {
                val updatedGroup = existingGroup.copy(now = proxy.name)
                val newMap = parsedProfile.groups.toMutableMap()
                newMap[group] = updatedGroup
                val updatedActiveNode = if (group == mainProxyGroupName) proxy.name else parsedProfile.activeNode
                parsedProfile = parsedProfile.copy(groups = newMap, activeNode = updatedActiveNode)
            }

            // 2. Immediately update liveGroupMap on main thread
            val currentLive = liveGroupMap[group] ?: existingGroup
            if (currentLive != null) {
                val updatedMap = liveGroupMap.toMutableMap()
                updatedMap[group] = currentLive.copy(now = proxy.name)
                liveGroupMap = updatedMap
            }

            // 3. Asynchronously dispatch to core without blocking UI
            if (vpnState.status == VpnState.Status.RUNNING) {
                lifecycleScope.launch(Dispatchers.IO) {
                    ClashCore.patchSelector(group, proxy.name)
                }
            }
        }

        var parseJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        // Settings affecting the active TUN take effect by rebuilding its service.
        fun restartTunnelForSettings() {
            when (MihomoVpnService.vpnState.value.status) {
                VpnState.Status.RUNNING, VpnState.Status.STARTING -> MihomoVpnService.restart(this@MainActivity)
                else -> Unit
            }
        }

        // Parse currently active profile offline to immediately show nodes & groups
        fun parseActiveProfile() {
            val active = DashboardData.activeProfile(profiles, selectedProfileId)
            parseJob?.cancel()
            parseJob = lifecycleScope.launch(Dispatchers.IO) {
                val savedMap = settingsManager.getAllSelectedNodes()
                val parsed = ProfileParser.parse(active?.file, savedMap, this@MainActivity)
                withContext(Dispatchers.Main) {
                    parsedProfile = parsed
                    proxyGroups = parsed.groupNames
                    if (selectedGroupName.isBlank() || !parsed.groupNames.contains(selectedGroupName)) {
                        selectedGroupName = parsed.groupNames.firstOrNull() ?: ""
                    }
                    if (selectedGroupName.isNotBlank()) {
                        val grp = parsed.groups[selectedGroupName]
                        val savedNode = settingsManager.getSelectedNode(selectedGroupName)
                        val effectiveNow = if (savedNode != null && grp?.proxies?.any { it.name == savedNode } == true) {
                            savedNode
                        } else {
                            grp?.now ?: ""
                        }
                        currentGroupData = grp?.copy(now = effectiveNow)
                    }
                    val effectiveMainGroup = parsed.groupNames.firstOrNull { it.equals("节点选择", ignoreCase = true) }
                        ?: parsed.groupNames.firstOrNull { it.equals("PROXY", ignoreCase = true) }
                        ?: parsed.groupNames.firstOrNull { it.equals("Proxies", ignoreCase = true) }
                        ?: parsed.groupNames.firstOrNull { it.equals("Proxy", ignoreCase = true) }
                        ?: parsed.groupNames.firstOrNull { parsed.groups[it]?.hidden != true }
                        ?: parsed.groupNames.firstOrNull() ?: ""
                    val mainGrp = parsed.groups[effectiveMainGroup]
                    val savedMainNode = settingsManager.getSelectedNode(effectiveMainGroup)
                    val effectiveMainNow = if (savedMainNode != null && mainGrp?.proxies?.any { it.name == savedMainNode } == true) {
                        savedMainNode
                    } else {
                        mainGrp?.now ?: parsed.activeNode
                    }
                    activeNodeName = effectiveMainNow
                }
            }
        }

        // Reload profiles from storage
        fun reloadProfiles() {
            profiles = ConfigManager.getProfiles(this@MainActivity)
            selectedProfileId = DashboardData.activeProfile(profiles, selectedProfileId)?.id
            settingsManager.selectedProfileId = selectedProfileId
            liveGroupMap = emptyMap()
            parseActiveProfile()
        }

        // Refresh proxy groups dynamically when core is running
        val delayCache = remember { mutableStateMapOf<String, Int>() }
        val delaySnapshot by remember { derivedStateOf { delayCache.toMap() } }
        val nodeLastTestedTimes = remember { mutableMapOf<String, Long>() }
        var computedGroupMap by remember { mutableStateOf<Map<String, ProxyGroup>>(emptyMap()) }

        // A large subscription can contain thousands of proxy objects. Coalesce bursts of
        // per-node delay updates, then build the display-only copies off the UI thread.
        LaunchedEffect(parsedProfile.groups, liveGroupMap) {
            val baseGroups = parsedProfile.groups
            val liveGroups = liveGroupMap
            val selectedNodes = withContext(Dispatchers.IO) {
                baseGroups.keys.associateWith { settingsManager.getSelectedNode(it) }
            }
            snapshotFlow { delayCache.toMap() }.collectLatest { delays ->
                delay(48L)
                val displayGroups = withContext(Dispatchers.Default) {
                    buildProxyDisplayGroups(baseGroups, liveGroups, selectedNodes, delays)
                }
                computedGroupMap = displayGroups
            }
        }

        fun refreshProxiesOnline() {
            // Skip refresh while speed tests are running to avoid clobbering results
            if (testingNodes.isNotEmpty()) return

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val allGroups = ClashCore.queryGroupNames()
                    val groups = allGroups.filter { gName ->
                        val grp = ClashCore.queryGroup(gName)
                        grp?.hidden != true
                    }
                    if (groups.isNotEmpty()) {
                        val groupDataMap = mutableMapOf<String, ProxyGroup>()
                        for (gName in groups) {
                            val gData = ClashCore.queryGroup(gName)
                            if (gData != null) {
                                val mergedProxies = gData.proxies.map { p ->
                                    val cachedDelay = delayCache[p.name] ?: delayCache["$gName:${p.name}"]
                                    if (cachedDelay != null) {
                                        p.copy(delay = cachedDelay)
                                    } else if (p.delay >= 65535 || p.delay <= 0) {
                                        p.copy(delay = -1) // not tested -> show "--"
                                    } else p
                                }
                                groupDataMap[gName] = gData.copy(proxies = mergedProxies)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            proxyGroups = groups
                            liveGroupMap = groupDataMap
                            if (selectedGroupName.isBlank() || !groups.contains(selectedGroupName)) {
                                selectedGroupName = groups.firstOrNull() ?: ""
                            }
                            if (selectedGroupName.isNotBlank()) {
                                val cur = groupDataMap[selectedGroupName]
                                if (cur != null) {
                                    currentGroupData = cur
                                }
                            }
                            val mainCur = groupDataMap[mainProxyGroupName]
                            if (mainCur != null && mainCur.now.isNotBlank()) {
                                activeNodeName = mainCur.now
                            }
                        }
                    }
                } catch (_: Throwable) {
                }
            }
        }

        fun runSpeedTestForGroup(groupName: String, isAutoTest: Boolean = false): Job {
            if (groupName.isNotBlank() && testingNodes.contains(groupName)) {
                return Job().apply { complete() }
            }
            return lifecycleScope.launch(Dispatchers.IO) {
                try {
                    if (!ClashCore.isCoreLoaded) {
                        ClashCore.ensureCoreLoaded(this@MainActivity)
                    }
                    val targetGroups = if (groupName.isNotBlank()) listOf(groupName) else proxyGroups
                    val semaphore = Semaphore(3)
                    coroutineScope {
                        targetGroups.map { g ->
                            async(Dispatchers.IO) {
                                semaphore.withPermit {
                                    val grp = liveGroupMap[g] ?: parsedProfile.groups[g] ?: (if (selectedGroupName == g) currentGroupData else null)
                                    val normType = (grp?.type ?: "").lowercase().replace("-", "").replace("_", "").trim()
                                    val isAutoGroup = normType == "urltest" || normType == "fallback" || normType == "loadbalance"

                                    if (isAutoTest && !isAutoGroup) return@withPermit

                                    val filterVirtual = grp?.proxies?.filter { p ->
                                        !p.name.equals("DIRECT", ignoreCase = true) &&
                                        !p.name.equals("REJECT", ignoreCase = true) &&
                                        !p.name.equals("COMPATIBLE", ignoreCase = true)
                                    } ?: emptyList()

                                    val allNodeNames = filterVirtual.map { it.name }
                                    if (allNodeNames.isEmpty()) return@withPermit

                                    val memberSubGroups = allNodeNames.filter { parsedProfile.groups.containsKey(it) }

                                    withContext(Dispatchers.Main) {
                                        testingNodes = if (isAutoTest) testingNodes + g else testingNodes + allNodeNames + g
                                    }

                                    try {
                                        val curTime = System.currentTimeMillis()

                                        // Concurrently run parent group health check and member sub-groups!
                                        val parentJob = async(Dispatchers.IO) {
                                            ClashCore.healthCheckGroupNative(g) { pName, delay ->
                                                lifecycleScope.launch(Dispatchers.Main) {
                                                    if (delay > 0 || delay == -2) {
                                                        delayCache[pName] = delay
                                                        delayCache["$g:$pName"] = delay
                                                        if (delay > 0) {
                                                            nodeLastTestedTimes[pName] = System.currentTimeMillis()
                                                        }
                                                    }
                                                    testingNodes = testingNodes - pName
                                                }
                                            }
                                        }

                                        val subJobs = memberSubGroups.map { subG ->
                                            async(Dispatchers.IO) {
                                                val subDelays = ClashCore.healthCheckGroupNative(subG) { subNodeName, subNodeDelay ->
                                                    lifecycleScope.launch(Dispatchers.Main) {
                                                        if (subNodeDelay > 0 || subNodeDelay == -2) {
                                                            delayCache[subNodeName] = subNodeDelay
                                                            delayCache["$subG:$subNodeName"] = subNodeDelay
                                                        }
                                                        testingNodes = testingNodes - subNodeName
                                                    }
                                                }
                                                val subGroupData = ClashCore.queryGroup(subG)
                                                val subBestName = subGroupData?.now ?: ""
                                                val subBestDelay = subDelays[subBestName] ?: subGroupData?.proxies?.find { it.name == subBestName }?.delay ?: -1
                                                val finalSubDelay = if (subBestDelay in 1..65534) subBestDelay else if (subBestDelay >= 65535) -2 else -1
                                                withContext(Dispatchers.Main) {
                                                    if (finalSubDelay > 0 || finalSubDelay == -2) {
                                                        delayCache[subG] = finalSubDelay
                                                    }
                                                    subDelays.forEach { (subNodeName, subNodeDelay) ->
                                                        if (subNodeDelay > 0 || subNodeDelay == -2) {
                                                            delayCache[subNodeName] = subNodeDelay
                                                            delayCache["$subG:$subNodeName"] = subNodeDelay
                                                        }
                                                    }
                                                    if (subGroupData != null && subGroupData.now.isNotBlank()) {
                                                        val existing = liveGroupMap[subG] ?: parsedProfile.groups[subG]
                                                        if (existing != null) {
                                                            liveGroupMap = liveGroupMap + (subG to existing.copy(now = subGroupData.now))
                                                        }
                                                    }
                                                    testingNodes = testingNodes - subG
                                                }
                                            }
                                        }

                                        val groupDelays = parentJob.await()
                                        subJobs.awaitAll()

                                        withContext(Dispatchers.Main) {
                                            groupDelays.forEach { (name, delay) ->
                                                if (delay > 0 || delay == -2) {
                                                    delayCache[name] = delay
                                                    delayCache["$g:$name"] = delay
                                                    if (delay > 0) {
                                                        nodeLastTestedTimes[name] = curTime
                                                    }
                                                }
                                            }
                                        }

                                        val finalGroup = ClashCore.queryGroup(g)
                                        withContext(Dispatchers.Main) {
                                            val bestNodeName = finalGroup?.now ?: ""
                                            val bestDelay = delayCache[bestNodeName] ?: finalGroup?.proxies?.find { it.name == bestNodeName }?.delay ?: -1
                                            val finalGroupDelay = if (bestDelay in 1..65534) bestDelay else if (bestDelay >= 65535) -2 else -1
                                            if (finalGroupDelay > 0 || finalGroupDelay == -2) {
                                                delayCache[g] = finalGroupDelay
                                            }

                                            if (finalGroup != null && finalGroup.now.isNotBlank()) {
                                                val existing = liveGroupMap[g] ?: parsedProfile.groups[g]
                                                if (existing != null) {
                                                    liveGroupMap = liveGroupMap + (g to existing.copy(now = finalGroup.now))
                                                }
                                                if (selectedGroupName == g && currentGroupData != null) {
                                                    currentGroupData = currentGroupData?.copy(now = finalGroup.now)
                                                }
                                            }
                                        }
                                    } finally {
                                        withContext(Dispatchers.Main) {
                                            testingNodes = if (isAutoTest) testingNodes - g else testingNodes - allNodeNames.toSet() - g
                                        }
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                } catch (_: Throwable) {
                    withContext(Dispatchers.Main) {
                        if (groupName.isNotBlank()) {
                            testingNodes = testingNodes - groupName
                        }
                    }
                }
            }
        }

        fun runSpeedTestForSingleNode(groupName: String?, nodeName: String) {
            if (testingNodes.contains(nodeName)) return

            if (parsedProfile.groups.containsKey(nodeName)) {
                runSpeedTestForGroup(nodeName, isAutoTest = false)
                return
            }

            testingNodes = testingNodes + nodeName

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    if (!ClashCore.isCoreLoaded) {
                        ClashCore.ensureCoreLoaded(this@MainActivity)
                    }
                    val delay = ClashCore.testProxyDelayNative(nodeName, groupName)
                    val curTime = System.currentTimeMillis()
                    withContext(Dispatchers.Main) {
                        if (delay > 0 || delay == -2) {
                            delayCache[nodeName] = delay
                            if (delay > 0) {
                                nodeLastTestedTimes[nodeName] = curTime
                            }
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    withContext(Dispatchers.Main) {
                        testingNodes = testingNodes - nodeName
                    }
                }
            }
        }

        val lastAutoTestTimes = remember { mutableMapOf<String, Long>() }
        var autoTestJob by remember { mutableStateOf<Job?>(null) }

        fun isAutoStrategyGroupType(type: String): Boolean {
            val norm = type.lowercase().replace("-", "").replace("_", "").trim()
            return norm == "urltest" || norm == "fallback" || norm == "loadbalance"
        }

        fun triggerAutoSpeedTest() {
            // Checked and set on the main thread; every caller (LaunchedEffect, ON_RESUME) runs
            // there, so two triggers cannot both start a sweep.
            if (autoTestJob?.isActive == true) return
            autoTestJob = lifecycleScope.launch(Dispatchers.IO) {
                try {
                    if (!ClashCore.isCoreLoaded) {
                        ClashCore.ensureCoreLoaded(this@MainActivity)
                    }
                    val now = System.currentTimeMillis()

                    // Strictly find the 3 auto-testable group types (url-test, fallback, load-balance)
                    val autoGroups = parsedProfile.groups.filter { (gName, grp) ->
                        val isAutoType = isAutoStrategyGroupType(grp.type)
                        val isRootGroup = gName.equals("GLOBAL", ignoreCase = true) ||
                                gName.equals("节点选择", ignoreCase = true) ||
                                gName.equals("PROXIES", ignoreCase = true) ||
                                gName.equals("Proxy", ignoreCase = true) ||
                                gName.equals("全部节点", ignoreCase = true)
                        isAutoType && !isRootGroup
                    }.keys.toList()

                    // Filter out groups that have already been tested within cooldown (120 seconds)
                    val groupsToTest = autoGroups.filter { g ->
                        val lastTime = lastAutoTestTimes[g] ?: 0L
                        now - lastTime > 120_000L
                    }

                    for (g in groupsToTest) {
                        lastAutoTestTimes[g] = System.currentTimeMillis()
                        // Await each group so the sweep cannot overlap itself or finish early.
                        runSpeedTestForGroup(g, isAutoTest = true).join()
                        delay(200L)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    android.util.Log.w("MainActivity", "Auto speed test failed", e)
                }
            }
        }

        // Re-parse once the profile list finishes loading, otherwise the first run (when the list
        // starts empty) would parse nothing and show no nodes.
        LaunchedEffect(selectedProfileId, profiles) {
            if (profiles.isNotEmpty()) {
                val effectiveId = DashboardData.activeProfile(profiles, selectedProfileId)?.id
                if (selectedProfileId != effectiveId) {
                    selectedProfileId = effectiveId
                    settingsManager.selectedProfileId = effectiveId
                }
            }
            parseActiveProfile()
        }

        // Dynamic refresh when VPN is connected
        LaunchedEffect(vpnState.status) {
            if (vpnState.status == VpnState.Status.RUNNING) {
                // Only poll while the UI is visible: a backgrounded / screen-off Activity keeps its
                // composition alive, and this loop used to keep crossing JNI every 3 s all night.
                lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                    while (isActive) {
                        refreshProxiesOnline()
                        delay(3000)
                    }
                }
            } else if (vpnState.status == VpnState.Status.STOPPED) {
                liveGroupMap = emptyMap()
                parseActiveProfile()
            }
        }

        // Load apps when entering App Routing screen (delayed so the transition completes smoothly)
        LaunchedEffect(currentSubScreen) {
            if (currentSubScreen == SubScreen.APP_ROUTING && installedApps.isEmpty()) {
                isAppsLoading = true
                try {
                    delay(300L)
                    // Child of the effect, so leaving the screen cancels the load instead of leaking it.
                    installedApps = withContext(Dispatchers.IO) {
                        AppInfoManager.getInstalledApps(this@MainActivity)
                    }
                } finally {
                    isAppsLoading = false
                }
            }
        }

        val profileImportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val res = ConfigManager.importProfileFromUri(this@MainActivity, uri)
                    withContext(Dispatchers.Main) {
                        if (res.isSuccess) {
                            val imported = res.getOrNull()
                            reloadProfiles()
                            AppNotice.show("成功导入配置: ${imported?.name}")
                        } else {
                            AppNotice.show("导入失败: ${res.exceptionOrNull()?.message}", long = true)
                        }
                    }
                }
            }
        }

        val launchProfileImport: () -> Unit = {
            profileImportLauncher.launch("*/*")
        }
        val addProfileSubscription: (String, String) -> Unit = { name, url ->
            lifecycleScope.launch {
                val res = ConfigManager.downloadSubscription(this@MainActivity, url, name)
                if (res.isSuccess) {
                    reloadProfiles()
                    AppNotice.show("订阅添加成功")
                } else {
                    AppNotice.show("下载失败: ${res.exceptionOrNull()?.message}", long = true)
                }
            }
        }

        val handleSubScreenBack: () -> Unit = {
            currentSubScreen = configurationParent(currentSubScreen, fromConfigurationCenter, overrideTargetProfileId != null)
            if (currentSubScreen == null || currentSubScreen == SubScreen.CONFIGURATION_CENTER) {
                fromConfigurationCenter = false
            }
        }

        // Handle Android system back button
        BackHandler(enabled = currentSubScreen != null) {
            handleSubScreenBack()
        }

        CompositionLocalProvider(
            LocalAppLanguage provides appLanguage,
            // Read the persisted preference at action time so changes apply immediately.
            LocalHapticFeedbackEnabled provides { settingsManager.hapticEnabled },
            LocalShortcutTouchVariant provides shortcutTouchVariant,
            LocalGlassSettings provides glassSettings,
            LocalShortcutMotionEnabled provides rememberShortcutAnimationsEnabled()
        ) {
        ProvideGlassTheme {
        ProvideSoftMeshMotion(paused = {
            pagerState.isScrollInProgress || dashboardScrollState.isScrollInProgress ||
                proxiesTabsListState.isScrollInProgress || proxiesListModeState.isScrollInProgress ||
                proxiesGroupRowState.isScrollInProgress || proxiesPerGroupTabStates.values.any { it.isScrollInProgress } ||
                currentSubScreen != null
        }) {
        val cardScene = rememberNavigationLens()
        val navigationLens = rememberNavigationLens()
        val navigationLensBoundsState = remember { mutableStateOf<NavigationLensGlobalBounds?>(null) }
        val overlayWallpaperScene = rememberNavigationLens()
        val rootGlasmioBackdrop = rememberArcaGlasmioBackdrop()
        val subScreenTransitionState = remember {
            androidx.compose.animation.core.MutableTransitionState(currentSubScreen != null)
        }
        subScreenTransitionState.targetState = currentSubScreen != null
        val subScreenBackdropActive = subScreenTransitionState.currentState || subScreenTransitionState.targetState
        val hideSettingsParentContent = shouldHideSettingsParentContent(
            subScreenActive = subScreenBackdropActive,
            currentPage = pagerState.currentPage,
            targetPage = pagerState.targetPage
        )
        // Record the page independently from the selected lens, and share it with the base dock for real content refraction.
        val subScreenPageSource = rememberArcaGlasmioBackdrop()
        val subScreenGlasmioBackdrop = if (rootGlasmioBackdrop != null && subScreenPageSource != null)
            rememberSharedGlassBackdrop(rootGlasmioBackdrop, subScreenPageSource)
        else rootGlasmioBackdrop
        Box(Modifier.fillMaxSize()) {
        // The dock base samples wallpaper plus the recorded page; the selected Arca lens keeps its independent scene.
        NexusGlassBackground(
            modifier = if (GlassDefaults.enabled) Modifier.recordNavigationScene(navigationLens) else Modifier,
            palette = glassPalette,
            backdropRecorder = if (GlassDefaults.enabled) Modifier.recordNavigationScene(cardScene) else Modifier,
            sharedGlasmioBackdrop = rootGlasmioBackdrop,
            recordGlasmioBackdropSource = true
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)
        ) {
            // Main tabs end at the dock top; reserve that area as a sibling slot, not pager padding.
            MainDockContentViewport(
                navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                beyondViewportPageCount = 0,
                modifier = Modifier.fillMaxSize()
                    // The floating dock overlays the page. Do not hard-clip the scene at
                    // the dock's top edge: content must remain visible through its glass.
                    .recordArcaGlasmioBackdropSource(subScreenPageSource)
                    .graphicsLayer { alpha = if (hideSettingsParentContent) 0f else 1f }
            ) { page ->
                // Keep page drawing unclipped so the floating glass dock can reveal the
                // live page beneath it. Individual cards/surfaces retain their own shapes.
                Box(Modifier.fillMaxSize().graphicsLayer { clip = false }) {
                    when (page) {
                        0 -> {
                            val mainGroupProxies = computedGroupMap[mainProxyGroupName]?.proxies
                                ?: buildDashboardProxyList(
                                    liveGroupMap[mainProxyGroupName]?.proxies
                                        ?: parsedProfile.groups[mainProxyGroupName]?.proxies.orEmpty(),
                                    mainProxyGroupName,
                                    delaySnapshot
                                )
                            val currentMainActiveNode = (liveGroupMap[mainProxyGroupName]?.now
                                ?: parsedProfile.groups[mainProxyGroupName]?.now
                                ?: settingsManager.getSelectedNode(mainProxyGroupName)
                                ?: activeNodeName).ifBlank { parsedProfile.activeNode }
                            DashboardScreen(
                                scrollState = dashboardScrollState,
                                vpnState = vpnState,
                                requestMonitoringEnabled = requestMonitoringEnabled,
                                profiles = profiles,
                                selectedProfileId = selectedProfileId,
                                onQuickProfileSelect = { profile ->
                                    lifecycleScope.launch {
                                        val shouldHotSwitch = vpnState.status == VpnState.Status.RUNNING && settingsManager.profileHotSwitchEnabled
                                        val loaded = if (shouldHotSwitch) {
                                            runCatching {
                                                withContext(Dispatchers.IO) {
                                                    val conf = ConfigManager.prepareConfig(this@MainActivity, profile.file)
                                                    ClashCore.load(conf)
                                                }
                                            }
                                        } else Result.success(Unit)
                                        if (loaded.isSuccess) {
                                            selectedProfileId = profile.id
                                            settingsManager.selectedProfileId = profile.id
                                            parseActiveProfile()
                                            liveGroupMap = emptyMap()
                                            if (shouldHotSwitch) lifecycleScope.launch(Dispatchers.IO) { refreshProxiesOnline() }
                                            AppNotice.show(if (shouldHotSwitch) "已热切换配置: ${profile.name}" else "已选择配置: ${profile.name}")
                                        } else {
                                            AppNotice.show("配置切换失败，仍保留当前配置：${loaded.exceptionOrNull()?.message}", long = true)
                                        }
                                    }
                                },
                                onQuickImportLocalFile = launchProfileImport,
                                onQuickAddSubscription = addProfileSubscription,
                                rules = parsedProfile.rules,
                                fakeIpEnabled = fakeIpEnabled,
                                onFakeIpEnabledChanged = { enabled ->
                                    fakeIpEnabled = enabled
                                    settingsManager.fakeIpEnabled = enabled
                                    if (vpnState.status == VpnState.Status.RUNNING) {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            val active = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
                                            val conf = ConfigManager.prepareConfig(this@MainActivity, active?.file)
                                            ClashCore.load(conf)
                                        }
                                    }
                                },
                                perAppProxyMode = perAppMode,
                                selectedPackages = selectedPackages,
                                apps = installedApps,
                                isAppsLoading = isAppsLoading,
                                onEnsureAppsLoaded = {
                                    if (installedApps.isEmpty() && !isAppsLoading) {
                                        isAppsLoading = true
                                        lifecycleScope.launch {
                                            try {
                                                installedApps = withContext(Dispatchers.IO) {
                                                    AppInfoManager.getInstalledApps(this@MainActivity)
                                                }
                                            } finally {
                                                isAppsLoading = false
                                            }
                                        }
                                    }
                                },
                                onPerAppProxyModeChanged = { mode ->
                                    perAppMode = mode
                                    settingsManager.perAppProxyMode = mode
                                },
                                onToggleAppPackage = { pkg ->
                                    val updated = if (selectedPackages.contains(pkg)) selectedPackages - pkg else selectedPackages + pkg
                                    selectedPackages = updated
                                    settingsManager.selectedPackages = updated
                                },
                                allowLan = allowLan,
                                onAllowLanChanged = { enabled ->
                                    allowLan = enabled
                                    settingsManager.allowLan = enabled
                                    if (vpnState.status == VpnState.Status.RUNNING) {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            val active = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
                                            val conf = ConfigManager.prepareConfig(this@MainActivity, active?.file)
                                            ClashCore.load(conf)
                                        }
                                    }
                                },
                                currentMode = tunnelMode,
                                activeProfileName = DashboardData.activeProfile(profiles, selectedProfileId)?.name ?: "未选择配置",
                                mainGroupName = mainProxyGroupName,
                                mainGroupProxies = mainGroupProxies,
                                activeNodeName = currentMainActiveNode,
                                totalNodes = parsedProfile.totalNodes,
                                totalRules = parsedProfile.totalRules,
                                visibleShortcuts = visibleShortcuts,
                                shortcutOrder = shortcutOrder,
                                onToggleVpn = { handleToggleVpn() },
                                onModeSelected = { newMode ->
                                    tunnelMode = newMode
                                    settingsManager.tunnelMode = newMode.value
                                },
                                onSelectProxy = { group, proxy -> handleSelectProxy(mainProxyGroupName, proxy) },
                                onHealthCheck = { group -> runSpeedTestForGroup(group) },
                                onNavigateToProfiles = { currentSubScreen = SubScreen.PROFILES },
                                onNavigateToAppRouting = { currentSubScreen = SubScreen.APP_ROUTING },
                                onNavigateToLogs = { currentSubScreen = SubScreen.LOGS },
                                onNavigateToRules = { currentSubScreen = SubScreen.RULES },
                                onNavigateToDns = { currentSubScreen = SubScreen.DNS },
                                onNavigateToRequests = { currentSubScreen = SubScreen.REQUESTS },
                                onNavigateToNetworkShare = { currentSubScreen = SubScreen.NETWORK_SHARE },
                                onNavigateToTraffic = { currentSubScreen = SubScreen.TRAFFIC },
                                onUpdateVisibleShortcuts = { updated ->
                                    visibleShortcuts = updated
                                    settingsManager.visibleShortcuts = updated
                                },
                                onUpdateShortcutOrder = { updated ->
                                    shortcutOrder = updated
                                    settingsManager.shortcutOrder = updated
                                },
                                glassScene = cardScene,
                                navigationLensBoundsState = navigationLensBoundsState,
                            )
                        }
                        1 -> {
                            val effectiveCurrentGroup = computedGroupMap[selectedGroupName] ?: currentGroupData

                            ProxiesScreen(
                                glassScene = cardScene,
                                groups = proxyGroups,
                                groupMap = computedGroupMap,
                                groupIcons = parsedProfile.groupIcons,
                                currentGroup = effectiveCurrentGroup,
                                selectedGroupName = selectedGroupName,
                                currentViewMode = proxiesViewMode,
                                showGroupIcons = showGroupIcons,
                                nodeColumns = nodeColumns,
                                tabsListState = proxiesTabsListState,
                                listModeState = proxiesListModeState,
                                groupRowState = proxiesGroupRowState,
                                expandedGroups = proxiesExpandedGroups,
                                perGroupTabStates = proxiesPerGroupTabStates,
                                onViewModeChanged = { mode ->
                                    proxiesViewMode = mode
                                    settingsManager.proxiesViewMode = mode
                                },
                                onShowGroupIconsChanged = { show ->
                                    showGroupIcons = show
                                    settingsManager.showGroupIcons = show
                                },
                                onNodeColumnsChanged = { cols ->
                                    nodeColumns = cols
                                    settingsManager.nodeColumns = cols
                                },
                                onSelectGroup = { gName ->
                                    selectedGroupName = gName
                                    val savedNode = settingsManager.getSelectedNode(gName)
                                    if (vpnState.status == VpnState.Status.RUNNING) {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            val gData = ClashCore.queryGroup(gName)
                                            withContext(Dispatchers.Main) {
                                                currentGroupData = gData
                                            }
                                        }
                                    } else {
                                        val gData = parsedProfile.groups[gName]
                                        val effectiveNow = if (savedNode != null && gData?.proxies?.any { it.name == savedNode } == true) savedNode else (gData?.now ?: "")
                                        currentGroupData = gData?.copy(now = effectiveNow)
                                    }
                                },
                                onSelectProxy = { group, proxy -> handleSelectProxy(group, proxy) },
                                onHealthCheck = { group -> runSpeedTestForGroup(group) },
                                testingNodes = testingNodes,
                                onTestSingleNode = { group, nodeName -> runSpeedTestForSingleNode(group, nodeName) }
                            )
                        }
                        2 -> SettingsScreen(
                            glassScene = cardScene,
                            scrollState = settingsScrollState,
                            tunStack = tunStack,
                            bootOnStartup = bootOnStartup,
                            allowLan = allowLan,
                            fakeIpEnabled = fakeIpEnabled,
                            hapticEnabled = hapticEnabled,
                            shortcutTouchVariant = shortcutTouchVariant,
                            onShortcutTouchVariantChanged = { variant ->
                                shortcutTouchVariant = variant
                                settingsManager.shortcutTouchVariant = variant
                            },
                            ipv6Enabled = ipv6Enabled,
                            unifiedDelayEnabled = unifiedDelayEnabled,
                            profileHotSwitchEnabled = profileHotSwitchEnabled,
                            tunMtu = tunMtu,
                            mipsCongestionController = mipsCongestionController,
                            themeMode = currentThemeMode,
                            onTunStackChanged = { stack ->
                                tunStack = stack
                                settingsManager.tunStack = stack
                                restartTunnelForSettings()
                            },
                            onBootOnStartupChanged = { enabled ->
                                bootOnStartup = enabled
                                settingsManager.bootOnStartup = enabled
                            },
                            onAllowLanChanged = { enabled ->
                                allowLan = enabled
                                settingsManager.allowLan = enabled
                                if (vpnState.status == VpnState.Status.RUNNING) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val active = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
                                        val conf = ConfigManager.prepareConfig(this@MainActivity, active?.file)
                                        ClashCore.load(conf)
                                    }
                                }
                            },
                            onHapticEnabledChanged = { enabled ->
                                hapticEnabled = enabled
                                settingsManager.hapticEnabled = enabled
                            },
                            onIpv6EnabledChanged = { enabled ->
                                ipv6Enabled = enabled
                                settingsManager.ipv6Enabled = enabled
                                restartTunnelForSettings()
                            },
                            onUnifiedDelayChanged = { enabled ->
                                val previous = settingsManager.unifiedDelayEnabled
                                unifiedDelayEnabled = enabled
                                settingsManager.unifiedDelayEnabled = enabled
                                if (vpnState.status == VpnState.Status.RUNNING) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        runCatching {
                                            val active = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
                                            val config = ConfigManager.prepareConfig(this@MainActivity, active?.file)
                                            ClashCore.load(config).getOrThrow()
                                        }.onFailure { error ->
                                            withContext(Dispatchers.Main) {
                                                unifiedDelayEnabled = previous
                                                settingsManager.unifiedDelayEnabled = previous
                                                AppNotice.show("统一延迟配置未能应用：${error.message}", long = true)
                                            }
                                        }
                                    }
                                }
                            },
                            onProfileHotSwitchChanged = { enabled ->
                                profileHotSwitchEnabled = enabled
                                settingsManager.profileHotSwitchEnabled = enabled
                            },
                            onTunMtuChanged = { value ->
                                tunMtu = TunTuning.normalizeMtu(value)
                                settingsManager.tunMtu = tunMtu
                                restartTunnelForSettings()
                            },
                            onMipsCongestionControllerChanged = { value ->
                                mipsCongestionController = TunTuning.normalizeCongestionController(value)
                                settingsManager.mipsCongestionController = mipsCongestionController
                                restartTunnelForSettings()
                            },
                            onNavigateToConfigurationCenter = {
                                overrideTargetProfile = null
                                overrideTargetProfileId = null
                                fromConfigurationCenter = false
                                currentSubScreen = SubScreen.CONFIGURATION_CENTER
                            },
                            onNavigateToLogs = { currentSubScreen = SubScreen.LOGS },
                            onNavigateToConnections = { currentSubScreen = SubScreen.CONNECTIONS },
                            onNavigateToAppearance = { currentSubScreen = SubScreen.APPEARANCE },
                            onNavigateToDns = { currentSubScreen = SubScreen.DNS },
                            onNavigateToGlassEffects = { currentSubScreen = SubScreen.GLASS_EFFECTS }
                        )
                    }
                }

            } // bounded pager page
            } // MainDockContentViewport

            var lastActiveSubScreen by remember { mutableStateOf<SubScreen?>(null) }
            LaunchedEffect(currentSubScreen) {
                if (currentSubScreen != null) {
                    lastActiveSubScreen = currentSubScreen
                }
            }
            val displayedSubScreen = currentSubScreen ?: lastActiveSubScreen

            val edgeSwipeOffsetX = remember { androidx.compose.animation.core.Animatable(0f) }
            var isEdgeDragging by remember { mutableStateOf(false) }
            val density = LocalDensity.current
            val edgeZonePx = with(density) { 32.dp.toPx() }

            LaunchedEffect(currentSubScreen) {
                edgeSwipeOffsetX.snapTo(0f)
                isEdgeDragging = false
            }

            // Share the actual overlay transition state so the dock returns only after its last exit frame.
            // 2. Sub-screens overlay (smooth slide in / out over the main tabs with iOS edge swipe back)
            AnimatedVisibility(
                visibleState = subScreenTransitionState,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(220)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(200)),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(20f)
                    .pointerInput(currentSubScreen) {
                        if (currentSubScreen == null) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { startOffset ->
                                isEdgeDragging = startOffset.x <= edgeZonePx
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (isEdgeDragging && (dragAmount > 0f || edgeSwipeOffsetX.value > 0f)) {
                                    change.consume()
                                    val next = (edgeSwipeOffsetX.value + dragAmount).coerceAtLeast(0f)
                                    coroutineScope.launch { edgeSwipeOffsetX.snapTo(next) }
                                }
                            },
                            onDragEnd = {
                                if (isEdgeDragging) {
                                    isEdgeDragging = false
                                    val screenWidth = size.width.toFloat()
                                    if (edgeSwipeOffsetX.value > screenWidth * 0.30f) {
                                        coroutineScope.launch {
                                            edgeSwipeOffsetX.animateTo(
                                                targetValue = screenWidth,
                                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                            )
                                            handleSubScreenBack()
                                            edgeSwipeOffsetX.snapTo(0f)
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            edgeSwipeOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = androidx.compose.animation.core.spring(
                                                    dampingRatio = 0.82f,
                                                    stiffness = 500f
                                                )
                                            )
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                if (isEdgeDragging) {
                                    isEdgeDragging = false
                                    coroutineScope.launch { edgeSwipeOffsetX.animateTo(0f) }
                                }
                            }
                        )
                    }
                    .graphicsLayer {
                        translationX = edgeSwipeOffsetX.value
                    }
            ) {
                AnimatedContent(
                    targetState = displayedSubScreen,
                    transitionSpec = {
                        fun getDepth(s: SubScreen?): Int = configurationDepth(
                            s, fromConfigurationCenter, overrideTargetProfileId != null
                        )
                        val initialDepth = getDepth(initialState)
                        val targetDepth = getDepth(targetState)

                        if (targetDepth > initialDepth) {
                            (slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                                    fadeIn(animationSpec = tween(220)))
                                .togetherWith(
                                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                                            fadeOut(animationSpec = tween(200))
                                ).using(SizeTransform(clip = false) { _, _ -> tween(0) })
                        } else {
                            (slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                                    fadeIn(animationSpec = tween(220)))
                                .togetherWith(
                                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                                            fadeOut(animationSpec = tween(200))
                                ).using(SizeTransform(clip = false) { _, _ -> tween(0) })
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "SubScreenOverlayTransition"
                ) { subScreen ->
                    if (subScreen != null) {
                    val backdropPolicy = subScreenBackdropPolicy(
                        screen = subScreen,
                        apiLevel = Build.VERSION.SDK_INT,
                        pageBackdropAvailable = subScreenGlasmioBackdrop != null
                    )
                    val overlayGlasmioBackdrop = if (backdropPolicy.useRootWallpaperBackdrop)
                        rootGlasmioBackdrop else subScreenGlasmioBackdrop
                    NexusGlassBackground(palette = glassPalette, backdropRecorder = if (GlassDefaults.enabled && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU)
                        Modifier.recordNavigationScene(overlayWallpaperScene) else Modifier,
                        sharedGlasmioBackdrop = overlayGlasmioBackdrop,
                        recordGlasmioBackdropSource = false,
                        drawBackground = backdropPolicy.drawWallpaperBackground) {
                        when (subScreen) {
                            SubScreen.CONFIGURATION_CENTER -> ConfigurationCenterScreen(
                                onNavigateToProfiles = {
                                    fromConfigurationCenter = true
                                    currentSubScreen = SubScreen.PROFILES
                                },
                                onNavigateToScripts = {
                                    overrideTargetProfile = null
                                    overrideTargetProfileId = null
                                    fromConfigurationCenter = true
                                    currentSubScreen = SubScreen.SCRIPTS
                                },
                                onNavigateToRules = {
                                    fromConfigurationCenter = true
                                    currentSubScreen = SubScreen.RULES
                                },
                                onNavigateToAppRouting = {
                                    fromConfigurationCenter = true
                                    currentSubScreen = SubScreen.APP_ROUTING
                                },
                                onBack = handleSubScreenBack,
                                scrollState = configurationCenterScrollState
                            )
                            SubScreen.PROFILES -> {
                                ProfilesScreen(
                                    profiles = profiles,
                                    selectedProfileId = selectedProfileId,
                                    onBack = handleSubScreenBack,
                                    onSelectProfile = { profile ->
                                        lifecycleScope.launch {
                                            val shouldHotSwitch = vpnState.status == VpnState.Status.RUNNING && settingsManager.profileHotSwitchEnabled
                                            val loaded = if (shouldHotSwitch) withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val config = ConfigManager.prepareConfig(this@MainActivity, profile.file)
                                                    ClashCore.load(config).getOrThrow()
                                                    // Reapply remembered selectors where the new profile exposes the same groups/nodes.
                                                    settingsManager.getAllSelectedNodes().forEach { (group, node) ->
                                                        runCatching { ClashCore.patchSelector(group, node) }
                                                    }
                                                }
                                            } else Result.success(Unit)
                                            if (loaded.isSuccess) {
                                                selectedProfileId = profile.id
                                                settingsManager.selectedProfileId = profile.id
                                                parseActiveProfile()
                                                liveGroupMap = emptyMap()
                                                if (shouldHotSwitch) lifecycleScope.launch(Dispatchers.IO) { refreshProxiesOnline() }
                                                AppNotice.show(if (shouldHotSwitch) "已热切换配置: ${profile.name}" else "已选择配置: ${profile.name}")
                                            } else {
                                                AppNotice.show("配置切换失败，仍保留当前配置：${loaded.exceptionOrNull()?.message}", long = true)
                                            }
                                        }
                                    },
                                    onAddSubscription = addProfileSubscription,
                                    onUpdateProfile = { profile ->
                                        reloadProfiles()
                                        if (selectedProfileId == profile.id) {
                                            parseActiveProfile()
                                        }
                                    },
                                    onRefreshSubscription = { profile ->
                                        if (profile.url != null) {
                                            lifecycleScope.launch {
                                                val res = ConfigManager.updateSubscription(this@MainActivity, profile.id, profile.name, profile.url)
                                                if (res.isSuccess) {
                                                    reloadProfiles()
                                                    AppNotice.show("订阅更新成功")
                                                } else {
                                                    AppNotice.show("更新失败: ${res.exceptionOrNull()?.message}", long = true)
                                                }
                                            }
                                        }
                                    },
                                    onRefreshAllSubscriptions = {
                                        val remote = profiles.filter { !it.url.isNullOrBlank() }
                                        if (remote.isEmpty()) {
                                            AppNotice.info("没有可更新的远程订阅")
                                        } else {
                                            var failed = 0
                                            for (p in remote) {
                                                val res = ConfigManager.updateSubscription(this@MainActivity, p.id, p.name, p.url!!)
                                                if (res.isFailure) failed++
                                            }
                                            reloadProfiles()
                                            if (failed == 0) AppNotice.success("已更新 ${remote.size} 个订阅")
                                            else AppNotice.error("${remote.size - failed} 个成功，$failed 个更新失败")
                                        }
                                    },
                                    onDeleteProfile = { profile ->
                                        ConfigManager.deleteProfile(this@MainActivity, profile.id)
                                        reloadProfiles()
                                    },
                                    onImportLocalFile = launchProfileImport,
                                    onReorderProfiles = { newOrderList ->
                                        profiles = newOrderList
                                        ConfigManager.saveProfileOrder(this@MainActivity, newOrderList.map { it.id })
                                    },
                                    onNavigateToOverride = { profile ->
                                        overrideTargetProfile = profile
                                        overrideTargetProfileId = profile.id
                                        currentSubScreen = SubScreen.PROFILE_OVERRIDE
                                    }
                                )
                            }
                            SubScreen.PROFILE_OVERRIDE -> {
                                val currentTarget = overrideTargetProfile ?: profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
                                if (currentTarget != null) {
                                    ProfileOverrideScreen(
                                        profile = currentTarget,
                                        onBack = { currentSubScreen = SubScreen.PROFILES },
                                        onNavigateToScripts = { currentSubScreen = SubScreen.SCRIPTS },
                                        onProfileUpdated = { updated ->
                                            overrideTargetProfile = updated
                                            overrideTargetProfileId = updated.id
                                            reloadProfiles()
                                            parseActiveProfile()
                                        }
                                    )
                                } else {
                                    currentSubScreen = SubScreen.PROFILES
                                }
                            }
                            SubScreen.SCRIPTS -> {
                                ScriptsScreen(
                                    onBack = handleSubScreenBack,
                                    onScriptsChanged = {
                                        parseActiveProfile()
                                    }
                                )
                            }
                            SubScreen.APP_ROUTING -> {
                                AppRoutingScreen(
                                    currentMode = perAppMode,
                                    selectedPackages = selectedPackages,
                                    apps = installedApps,
                                    isLoading = isAppsLoading,
                                    onBack = handleSubScreenBack,
                                    onModeChanged = { mode ->
                                        perAppMode = mode
                                        settingsManager.perAppProxyMode = mode
                                    },
                                    onTogglePackage = { pkg ->
                                        val updated = if (selectedPackages.contains(pkg)) {
                                            selectedPackages - pkg
                                        } else {
                                            selectedPackages + pkg
                                        }
                                        selectedPackages = updated
                                        settingsManager.selectedPackages = updated
                                    }
                                )
                            }
                            SubScreen.LOGS -> {
                                LogsScreen(
                                    onBack = { currentSubScreen = null }
                                )
                            }
                            SubScreen.RULES -> {
                                RulesScreen(
                                    rules = parsedProfile.rules,
                                    totalRules = parsedProfile.totalRules,
                                    tunnelMode = tunnelMode,
                                    onBack = handleSubScreenBack
                                )
                            }
                            SubScreen.DNS -> {
                                DnsScreen(
                                    fakeIpEnabled = fakeIpEnabled,
                                    onFakeIpEnabledChanged = { enabled ->
                                        fakeIpEnabled = enabled
                                        settingsManager.fakeIpEnabled = enabled
                                        if (vpnState.status == VpnState.Status.RUNNING) {
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                val active = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
                                                val conf = ConfigManager.prepareConfig(this@MainActivity, active?.file)
                                                ClashCore.load(conf)
                                            }
                                        }
                                    },
                                    onBack = { currentSubScreen = null }
                                )
                            }
                            SubScreen.TRAFFIC -> TrafficRecordsScreen(
                                onBack = { currentSubScreen = null }
                            )
                            SubScreen.CONNECTIONS -> ConnectionsScreen(
                                vpnState = vpnState,
                                onBack = { currentSubScreen = null }
                            )
                            SubScreen.REQUESTS -> {
                                RequestLogsScreen(
                                    vpnState = vpnState,
                                    onBack = { currentSubScreen = null }
                                )
                            }
                            SubScreen.NETWORK_SHARE -> {
                                NetworkShareScreen(
                                    allowLan = allowLan,
                                    credentials = lanCredentials,
                                    onAllowLanChanged = { enabled ->
                                        allowLan = enabled
                                        settingsManager.allowLan = enabled
                                        if (vpnState.status == VpnState.Status.RUNNING) {
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                val active = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
                                                val conf = ConfigManager.prepareConfig(this@MainActivity, active?.file)
                                                ClashCore.load(conf)
                                            }
                                        }
                                    },
                                    onRegenerateCredentials = {
                                        lanCredentials = settingsManager.regenerateLanCredentials()
                                        if (vpnState.status == VpnState.Status.RUNNING) {
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                val active = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
                                                val conf = ConfigManager.prepareConfig(this@MainActivity, active?.file)
                                                ClashCore.load(conf)
                                            }
                                        }
                                    },
                                    vpnState = vpnState,
                                    onBack = { currentSubScreen = null }
                                )
                            }
                            SubScreen.GLASS_EFFECTS -> GlassEffectsScreen(
                                settings = glassSettings,
                                onSettingsChanged = { updated ->
                                    glassSettings = updated.clamped()
                                    settingsManager.glassSettings = glassSettings
                                },
                                wallpaperSettings = GlassDefaults.wallpaper,
                                onWallpaperSettingsChanged = { value ->
                                    GlassDefaults.wallpaper = value
                                    settingsManager.glassWallpaperSettings = value
                                },
                                wallpaperScene = overlayWallpaperScene,
                                onBack = { currentSubScreen = null },
                                scrollState = glassEffectsScrollState
                            )
                            SubScreen.APPEARANCE -> {
                                AppearanceScreen(
                                    themeMode = currentThemeMode,
                                    onThemeModeChanged = onThemeModeChanged,
                                    wallpaperSettings = GlassDefaults.wallpaper,
                                    onWallpaperSettingsChanged = { value ->
                                        GlassDefaults.wallpaper = value
                                        settingsManager.glassWallpaperSettings = value
                                    },
                                    onLanguageChanged = { code -> appLanguage = code },
                                    nodeColumns = nodeColumns,
                                    onNodeColumnsChanged = { cols ->
                                        nodeColumns = cols
                                        settingsManager.nodeColumns = cols
                                    },
                                    showGroupIcons = showGroupIcons,
                                    onShowGroupIconsChanged = { show ->
                                        showGroupIcons = show
                                        settingsManager.showGroupIcons = show
                                    },
                                    onBack = { currentSubScreen = null },
                                    listState = appearanceListState
                                )
                            }
                        }
                    } // overlay Glasmio scene
                }
            }
        }

        } // scene content
        } // recorded scene; the dock below must never enter its own input

            // The recorded scene is rendered after its children; keep nested pages' dock
            // completely absent (including semantics/input) until the return transition ends.
            NestedPageDockVisibility(
                nestedPageTransition = subScreenTransitionState,
                nestedPageVisible = currentSubScreen != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .wrapContentSize(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = BOTTOM_BAR_BOTTOM_MARGIN)
            ) {
                ProvideArcaGlasmioBackdrop(subScreenGlasmioBackdrop) {
                LoonFloatingBottomBar(
                    selectedTab = pagerState.targetPage,
                    onTabSelected = { tabIndex ->
                        currentTab = tabIndex
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                page = tabIndex,
                                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                            )
                        }
                        if (tabIndex == 1) {
                            if (vpnState.status == VpnState.Status.RUNNING) {
                                refreshProxiesOnline()
                            } else if (parsedProfile.groups.isEmpty()) {
                                parseActiveProfile()
                            }
                        }
                    },
                    lens = navigationLens,
                    onLensBoundsChanged = { navigationLensBoundsState.value = it }
                )
                }
            }
            // One-click import confirmation (clash://install-config).
            val incomingLink by pendingSubscriptionLink.collectAsState()
            incomingLink?.let { link ->
                SubscriptionImportDialog(
                    link = link,
                    onDismiss = { pendingSubscriptionLink.value = null },
                    onConfirm = { name ->
                        pendingSubscriptionLink.value = null
                        lifecycleScope.launch {
                            AppNotice.info("正在下载订阅…")
                            val res = ConfigManager.downloadSubscription(this@MainActivity, link.url, name)
                            if (res.isSuccess) {
                                reloadProfiles()
                                AppNotice.success("订阅已导入：${res.getOrNull()?.name}")
                            } else {
                                AppNotice.error("导入失败：${res.exceptionOrNull()?.message}")
                            }
                        }
                    }
                )
            }
            // Unified glass notices (replaces Toast); drawn above pages and the dock.
            AppNoticeHost()
        } // scene + dock overlay
        } // end shared background motion
        } // end ProvideGlassTheme
        } // end CompositionLocalProvider
    }
}
