package com.github.arca.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.Trace
import android.system.OsConstants
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import com.github.kr328.clash.core.util.*
import com.github.arca.android.R
import com.github.arca.android.core.ClashCore
import com.github.arca.android.data.ConfigManager
import com.github.arca.android.data.PerAppProxyMode
import com.github.arca.android.data.SettingsManager
import com.github.arca.android.data.TunStack
import com.github.arca.android.data.TunTuning
import com.github.arca.android.ui.MainActivity
import com.github.arca.android.service.network.PhysicalNetworkObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class TrafficMetrics(
    val upSpeed: String = "0 B/s",
    val downSpeed: String = "0 B/s"
)

data class VpnState(
    val status: Status = Status.STOPPED,
    val upSpeed: String = "0 B/s",
    val downSpeed: String = "0 B/s",
    val totalUp: String = "0 B",
    val totalDown: String = "0 B",
    val coreVersion: String = "",
    val error: String? = null
) {
    enum class Status {
        STOPPED, STARTING, RUNNING, STOPPING
    }
}

class MihomoVpnService : VpnService() {

    companion object {
        private const val TAG = "MihomoVpnService"
        const val ACTION_START = "com.github.arca.android.service.START"
        const val ACTION_STOP = "com.github.arca.android.service.STOP"
        const val ACTION_RESTART = "com.github.arca.android.service.RESTART"
        private const val NOTIFICATION_CHANNEL_ID = "mihomo_vpn_service"
        private const val NOTIFICATION_ID = 1001
        private const val SCREEN_OFF_SAMPLE_MS = 30_000L

        private val _vpnState = MutableStateFlow(VpnState())
        val vpnState = _vpnState.asStateFlow()

        // Keep once-per-second traffic telemetry separate from connection state so screens
        // that only care whether the tunnel is running do not invalidate on every sample.
        private val _trafficMetrics = MutableStateFlow(TrafficMetrics())
        val trafficMetrics = _trafficMetrics.asStateFlow()

        // Native log subscription lives for the process, not one VPN service instance.
        private val coreLogcatSubscription = ProcessLogcatSubscription()
        // Native TUN state is process-wide, including cleanup after an old Service is destroyed.
        private val coreLifecycleMutex = Mutex()
        private var coreOwner: Any? = null // Access only while holding coreLifecycleMutex.
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun start(context: Context) {
            val intent = Intent(context, MihomoVpnService::class.java).apply {
                action = ACTION_START
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.e(TAG, "Failed to start VPN service", it) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MihomoVpnService::class.java).apply {
                action = ACTION_STOP
            }
            // Background-start restrictions can reject this; the tunnel cleanup also runs from
            // onDestroy, so a rejected command is not fatal.
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "Failed to deliver stop command to VPN service", it) }
        }

        /** Rebuilds the TUN with the current settings without leaving the service foreground gap. */
        fun restart(context: Context) {
            val intent = Intent(context, MihomoVpnService::class.java).apply {
                action = ACTION_RESTART
            }
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "Failed to deliver restart command to VPN service", it) }
        }
    }

    // Serialize service control (including restart continuations) on Android's main thread.
    // Blocking native/configuration work stays on Dispatchers.Default below.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lifecycleMutex get() = coreLifecycleMutex
    private val ownerToken = Any()
    private var trafficMonitorJob: Job? = null
    private var startJob: Job? = null
    private var stopJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var networkObserver: PhysicalNetworkObserver? = null
    private var foregroundStarted = false // Main-thread service command lifecycle.
    private var latestCommandStartId = 0

    /** A queued restart must not resurrect a tunnel after the user asked to stop. */
    @Volatile
    private var stopRequested = false
    @Volatile
    private var destroyed = false
    // Preserve the existing notification N artwork independently of the launcher icon.
    private val notificationLargeIcon by lazy {
        requireNotNull(getDrawable(R.drawable.ic_notification_brand)).toBitmap(
            width = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width),
            height = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)
        )
    }

    override fun onCreate() {
        super.onCreate()
        com.github.arca.android.data.TrafficRecordRepository.initialize(this)
        createNotificationChannel()
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestCommandStartId = startId
        // Even an idle STOP/RESTART can recreate a Service while Android still expects its
        // pending foreground acknowledgement. Satisfy it before any no-op/stop branch.
        if (!foregroundStarted) startForegroundServiceNotification("准备 Nexus 核心")
        when (intent?.action) {
            ACTION_START -> {
                stopRequested = false
                startVpn()
            }
            ACTION_STOP -> stopVpn()
            ACTION_RESTART -> restartVpn()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startVpn() {
        if (destroyed || stopRequested) return
        val previousStart = startJob
        if (previousStart?.isActive == true && !previousStart.isCancelled) return
        val previousStop = stopJob
        val commandStartId = latestCommandStartId
        startForegroundServiceNotification("启动 Nexus 核心")

        startJob = serviceScope.launch(Dispatchers.Default) {
            previousStart?.join()
            previousStop?.join()
            lifecycleMutex.withLock {
            currentCoroutineContext().ensureActive()
            if (destroyed || stopRequested) return@withLock
            if (coreOwner === ownerToken && _vpnState.value.status == VpnState.Status.RUNNING) return@withLock
            coreOwner = ownerToken
            _vpnState.value = VpnState(status = VpnState.Status.STARTING)
            try {
                val traceStartup = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                val startupStartedAtNanos = if (traceStartup) SystemClock.elapsedRealtimeNanos() else 0L
                currentCoroutineContext().ensureActive()
                // 1. Initialize native core
                measureStartupStage("core_init", traceStartup) { ClashCore.init(this@MihomoVpnService) }
                val coreVer = measureStartupStage("core_version", traceStartup) { ClashCore.getCoreVersion() }

                // 2. Select and prepare configuration
                val settings = SettingsManager(this@MihomoVpnService)
                val ipv6 = settings.ipv6Enabled
                val nativeTunOptions = ClashCore.supportsTunOptions()
                val mtu = if (nativeTunOptions) settings.tunMtu else TunTuning.DEFAULT_MTU
                val congestion = if (nativeTunOptions && settings.tunStack == TunStack.MIPS) {
                    settings.mipsCongestionController
                } else {
                    ""
                }
                if (!nativeTunOptions) {
                    Log.w(TAG, "Native bridge has no TUN options contract; MTU/pacing settings stay at defaults")
                }
                val profiles = measureStartupStage("profile_lookup", traceStartup) {
                    ConfigManager.getProfiles(this@MihomoVpnService)
                }
                val activeProfile = profiles.firstOrNull { it.id == settings.selectedProfileId } ?: profiles.firstOrNull()

                val configFile = measureStartupStage("prepare_config", traceStartup) {
                    ConfigManager.prepareConfig(
                        this@MihomoVpnService, activeProfile?.file, ipv6Enabled = ipv6
                    )
                }

                // 3. Load config into core
                val loadRes = measureStartupStage("load_config", traceStartup) { ClashCore.load(configFile) }
                if (loadRes.isFailure) {
                    error("加载配置文件失败: ${loadRes.exceptionOrNull()?.message}")
                }

                // Restore user's saved proxy selections into core
                runCatching {
                    val allSaved = settings.getAllSelectedNodes()
                    allSaved.forEach { (gName, savedNode) ->
                        ClashCore.patchSelector(gName, savedNode)
                        Log.i(TAG, "Restored group selector: $gName -> $savedNode")
                    }
                }

                // Shared DNS deduplication excludes the VPN's own virtual LinkProperties.
                ClashCore.notifyNetworkChanged(this@MihomoVpnService)
                if (networkObserver == null) {
                    networkObserver = PhysicalNetworkObserver(
                        this@MihomoVpnService,
                        onDnsChanged = ClashCore::notifyPhysicalDns
                    ).also { it.start() }
                }

                // Reuse one native subscriber across VPN restarts to avoid duplicate fan-out.
                runCatching {
                    coreLogcatSubscription.subscribeIfNeeded {
                        com.github.kr328.clash.core.bridge.Bridge.nativeSubscribeLogcat(object : com.github.kr328.clash.core.bridge.LogcatInterface {
                            override fun received(jsonPayload: String) {
                                // Native already emits Android logcat; keep this callback short.
                                com.github.arca.android.data.LogRepository.enqueueRawLogcat(jsonPayload)
                            }
                        })
                    }
                }

                currentCoroutineContext().ensureActive()
                // 4. Establish Android VpnService TUN
                val builder = Builder().apply {
                    setSession("Nexus")
                    setMtu(mtu)
                    addAddress("172.19.0.1", 30)
                    addRoute("0.0.0.0", 0)
                    addRoute("198.18.0.0", 16) // Explicit route for Fake-IP range!
                    addDnsServer("172.19.0.2")

                    // IPv6 capture follows one switch so the routes and the core config cannot drift.
                    if (ipv6) {
                        // Fail the whole startup if any IPv6 operation fails: never report a
                        // partially configured IPv4-only tunnel while the core has IPv6 enabled.
                        addAddress("fdfe:dcba:9876::1", 126)
                        addRoute("::", 0)
                        addDnsServer("fdfe:dcba:9876::2")
                    } else {
                        // Android blocks an unconfigured family by default. Explicitly allow it
                        // outside the VPN to match the switch's documented bypass semantics.
                        allowFamily(OsConstants.AF_INET6)
                    }

                    // Per-App routing
                    val packages = settings.selectedPackages
                    when (settings.perAppProxyMode) {
                        PerAppProxyMode.WHITELIST -> {
                            packages.forEach { pkg ->
                                runCatching { addAllowedApplication(pkg) }
                            }
                        }
                        PerAppProxyMode.BLACKLIST -> {
                            packages.forEach { pkg ->
                                runCatching { addDisallowedApplication(pkg) }
                            }
                            runCatching { addDisallowedApplication(packageName) }
                        }
                        PerAppProxyMode.DISABLED -> {
                            runCatching { addDisallowedApplication(packageName) }
                        }
                    }
                }

                val pfd = measureStartupStage("establish_tun", traceStartup) { builder.establish() }
                    ?: error("无法建立 VPN TUN 接口 (Builder.establish() 返回 null)")
                vpnInterface = pfd

                // 5. Borrow the app-owned FD; the native listener owns a separate duplicate.
                // Do not adopt/close a descriptor that may already have been reused by native code.
                val selectedStack = settings.tunStack.key
                val gateway = if (ipv6) "172.19.0.1/30,fdfe:dcba:9876::1/126" else "172.19.0.1/30"
                val portal = if (ipv6) "172.19.0.2,fdfe:dcba:9876::2" else "172.19.0.2"
                measureStartupStage("native_start_tun", traceStartup) {
                    ClashCore.startTun(
                        fd = pfd.fd,
                        stack = selectedStack,
                        gateway = gateway,
                        portal = portal,
                        dns = portal,
                        mtu = mtu,
                        congestionController = congestion,
                        vpnService = this@MihomoVpnService
                    )
                }
                currentCoroutineContext().ensureActive()

                // 6. Report running only after the native listener has started successfully.
                _vpnState.value = _vpnState.value.copy(
                    status = VpnState.Status.RUNNING,
                    coreVersion = coreVer,
                    error = null
                )

                startTrafficMonitor()
                if (traceStartup) {
                    val totalMs = (SystemClock.elapsedRealtimeNanos() - startupStartedAtNanos) / 1_000_000L
                    Log.d(TAG, "VPN startup total=${totalMs}ms")
                }
                Log.i(TAG, "Nexus VPN successfully started! Core: $coreVer, ipv6=$ipv6, mtu=$mtu, cc=$congestion")
                com.github.arca.android.data.LogRepository.addLog(
                    "info",
                    "Nexus VPN 已建立；TUN 栈: $selectedStack；IPv6: $ipv6；MTU: $mtu；核心版本: $coreVer"
                )

            } catch (e: kotlinx.coroutines.CancellationException) {
                closeTunnelLocked()
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start Nexus VPN", e)
                com.github.arca.android.data.LogRepository.addLog("error", "启动失败: ${e.message}")
                closeTunnelLocked()
                _vpnState.value = VpnState(
                    status = VpnState.Status.STOPPED,
                    error = e.message ?: "未知启动错误"
                )
                if (!destroyed && startJob === currentCoroutineContext()[Job]) stopSelfResult(commandStartId)
            }
            }
        }
    }

    private inline fun <T> measureStartupStage(name: String, traceEnabled: Boolean, block: () -> T): T {
        if (!traceEnabled) return block()
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        Trace.beginSection("ArcaVPN/$name")
        return try {
            block()
        } finally {
            val durationMicros = (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000L
            Trace.endSection()
            Log.d(TAG, "VPN startup stage $name=${durationMicros}us")
        }
    }

    /**
     * Light power saving: while the screen is off the core keeps routing normally (native suspend
     * is deliberately not used, it would stall new connections on a locked device), but the
     * once-per-second JNI sampling, speed StateFlow and notification rebuild pause. Proxy traffic
     * accounting continues at a slow cadence so daily records stay accurate.
     */
    @Volatile
    private var screenInteractive = true
    private var screenReceiverRegistered = false
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractive = false
                    // Return idle heap to the OS once instead of keeping it during sleep.
                    if (_vpnState.value.status == VpnState.Status.RUNNING) {
                        runCatching { com.github.kr328.clash.core.bridge.Bridge.nativeForceGc() }
                    }
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    if (!screenInteractive) {
                        screenInteractive = true
                        // Wake the sampler immediately so the notification is fresh on unlock.
                        if (_vpnState.value.status == VpnState.Status.RUNNING) startTrafficMonitor()
                    }
                }
            }
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                this, screenReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            screenReceiverRegistered = true
        }.onFailure { Log.w(TAG, "Screen receiver unavailable; power saving disabled", it) }
        screenInteractive = (getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.isInteractive ?: true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }

    private fun startTrafficMonitor() {
        trafficMonitorJob?.cancel()
        trafficMonitorJob = serviceScope.launch(Dispatchers.Default) {
            var lastNotified = ""
            while (isActive && _vpnState.value.status == VpnState.Status.RUNNING) {
                if (!screenInteractive) {
                    delay(SCREEN_OFF_SAMPLE_MS)
                    val proxyTotal = ClashCore.queryProxyTrafficTotal()
                    currentCoroutineContext().ensureActive()
                    com.github.arca.android.data.TrafficRecordRepository.recordProxy(proxyTotal)
                    continue
                }
                delay(1000)
                val now = ClashCore.queryTrafficNow()
                val proxyTotal = ClashCore.queryProxyTrafficTotal()
                currentCoroutineContext().ensureActive()
                com.github.arca.android.data.TrafficRecordRepository.recordProxy(proxyTotal)

                val upStr = now.trafficSpeedUpload()
                val downStr = now.trafficSpeedDownload()

                _trafficMetrics.value = TrafficMetrics(upSpeed = upStr, downSpeed = downStr)
                val text = "\u2191 $upStr   \u2193 $downStr"
                // Skip identical rebuilds (idle link) - NotificationManager IPC is not free.
                if (text != lastNotified) {
                    updateNotification(text)
                    lastNotified = text
                }
            }
        }
    }

    private fun stopVpn() {
        stopRequested = true
        if (stopJob?.isActive == true) return
        val starting = startJob
        val commandStartId = latestCommandStartId
        starting?.cancel()
        trafficMonitorJob?.cancel()
        stopJob = serviceScope.launch {
            // Waiting while holding the native lifecycle mutex would deadlock start cleanup.
            starting?.join()
            closeTunnel(commandStartId)
        }
    }

    /**
     * Rebuilds the tunnel with the current settings. The start job holds the lifecycle lock, so it
     * is cancelled and joined before waiting for that lock; otherwise this would deadlock.
     */
    private fun restartVpn() {
        // A settings restart is not an explicit START request. Never revive a stopped tunnel.
        if (destroyed || stopRequested || _vpnState.value.status == VpnState.Status.STOPPED ||
            _vpnState.value.status == VpnState.Status.STOPPING) {
            if (_vpnState.value.status == VpnState.Status.STOPPED) stopSelfResult(latestCommandStartId)
            return
        }
        serviceScope.launch {
            startJob?.cancelAndJoin()
            lifecycleMutex.withLock {
                if (stopRequested) return@withLock
                // A queued start may have been launched while this coroutine waited for the lock.
                if (destroyed || (coreOwner != null && coreOwner !== ownerToken)) return@withLock
                startJob?.cancel()
                startJob = null
                trafficMonitorJob?.cancel()
                closeTunnelLocked()
                startVpn()
            }
        }
    }

    private suspend fun closeTunnelLocked() {
        networkObserver?.close()
        networkObserver = null
        if (coreOwner !== ownerToken) {
            val oldDescriptor = vpnInterface
            vpnInterface = null
            kotlinx.coroutines.withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                runCatching { oldDescriptor?.close() }
            }
            return
        }
        _vpnState.value = _vpnState.value.copy(status = VpnState.Status.STOPPING)
        // Keep the lifecycle lock while the blocking native close runs away from Main.
        // The app's borrowed descriptor remains alive until the native listener stops.
        val descriptor = vpnInterface
        try {
            kotlinx.coroutines.withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                try {
                    ClashCore.stopTun()
                } finally {
                    descriptor?.close()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing VPN", e)
        } finally {
            coreOwner = null
            vpnInterface = null
            _vpnState.value = VpnState(status = VpnState.Status.STOPPED)
            _trafficMetrics.value = TrafficMetrics()
        }
    }

    private suspend fun closeTunnel(commandStartId: Int) = lifecycleMutex.withLock {
        closeTunnelLocked()
        // A newer explicit START may be waiting for this stop job to finish.
        if (stopRequested && !destroyed) {
            // Android may already have accepted a newer START not delivered to Main yet.
            // A stale stop ID must not destroy that pending foreground-service request.
            if (stopSelfResult(commandStartId)) {
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                foregroundStarted = false
            }
        }
        Log.i(TAG, "Nexus VPN stopped.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification(content: String) {
        val notification = buildNotification(content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun buildNotification(content: String): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MihomoVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Use a dedicated small-icon identity and explicit N artwork for this comparison.
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_zz_notification_nexus_v107)
            .setLargeIcon(notificationLargeIcon)
            .setColor(getColor(R.color.nexus_brand_accent))
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setContentIntent(launchIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyed = true
        stopRequested = true
        unregisterScreenReceiver()
        networkObserver?.close()
        networkObserver = null
        trafficMonitorJob?.cancel()
        val starting = startJob
        starting?.cancel()
        serviceScope.cancel()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        // Never block Main on rTunLock/listener.Close. Old cleanup must not stop a newer owner.
        cleanupScope.launch {
            starting?.join()
            lifecycleMutex.withLock { closeTunnelLocked() }
        }
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }
}


/** Guards a process-lifetime native log subscription against repeated VPN starts. */
internal class ProcessLogcatSubscription {
    private var subscribed = false

    @Synchronized
    fun subscribeIfNeeded(subscribe: () -> Unit) {
        if (subscribed) return
        subscribe()
        // Mark only after registration returns so transient failures can be retried.
        subscribed = true
    }
}
