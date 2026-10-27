package com.github.mihomo.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import com.github.kr328.clash.core.util.*
import com.github.mihomo.android.R
import com.github.mihomo.android.core.ClashCore
import com.github.mihomo.android.data.ConfigManager
import com.github.mihomo.android.data.PerAppProxyMode
import com.github.mihomo.android.data.SettingsManager
import com.github.mihomo.android.data.TunStack
import com.github.mihomo.android.data.TunTuning
import com.github.mihomo.android.ui.MainActivity
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
        const val ACTION_START = "com.github.mihomo.android.service.START"
        const val ACTION_STOP = "com.github.mihomo.android.service.STOP"
        const val ACTION_RESTART = "com.github.mihomo.android.service.RESTART"
        private const val NOTIFICATION_CHANNEL_ID = "mihomo_vpn_service"
        private const val NOTIFICATION_ID = 1001

        private val _vpnState = MutableStateFlow(VpnState())
        val vpnState = _vpnState.asStateFlow()

        // Keep once-per-second traffic telemetry separate from connection state so screens
        // that only care whether the tunnel is running do not invalidate on every sample.
        private val _trafficMetrics = MutableStateFlow(TrafficMetrics())
        val trafficMetrics = _trafficMetrics.asStateFlow()

        // Native log subscription lives for the process, not one VPN service instance.
        private val coreLogcatSubscription = ProcessLogcatSubscription()

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
    private val lifecycleMutex = Mutex()
    private var trafficMonitorJob: Job? = null
    private var startJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null

    /** A queued restart must not resurrect a tunnel after the user asked to stop. */
    @Volatile
    private var stopRequested = false
    // Preserve the existing notification N artwork independently of the launcher icon.
    private val notificationLargeIcon by lazy {
        requireNotNull(getDrawable(R.drawable.ic_notification_brand)).toBitmap(
            width = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width),
            height = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)
        )
    }

    override fun onCreate() {
        super.onCreate()
        com.github.mihomo.android.data.TrafficRecordRepository.initialize(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                stopRequested = false
                startVpn()
            }
            ACTION_STOP -> stopVpn()
            ACTION_RESTART -> restartVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn() {
        val status = _vpnState.value.status
        if (stopRequested || startJob?.isActive == true || status != VpnState.Status.STOPPED) {
            return
        }

        _vpnState.value = _vpnState.value.copy(status = VpnState.Status.STARTING, error = null)
        startForegroundServiceNotification("启动 Nexus 核心")

        startJob = serviceScope.launch(Dispatchers.Default) {
            lifecycleMutex.withLock {
            try {
                currentCoroutineContext().ensureActive()
                // 1. Initialize native core
                ClashCore.init(this@MihomoVpnService)
                val coreVer = ClashCore.getCoreVersion()

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
                val profiles = ConfigManager.getProfiles(this@MihomoVpnService)
                val activeProfile = profiles.firstOrNull { it.id == settings.selectedProfileId } ?: profiles.firstOrNull()

                val configFile = ConfigManager.prepareConfig(
                    this@MihomoVpnService, activeProfile?.file, ipv6Enabled = ipv6
                )

                // 3. Load config into core
                val loadRes = ClashCore.load(configFile)
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

                // Notify physical network DNS and Timezone to core
                runCatching {
                    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val activeNet = cm?.activeNetwork
                    val linkProps = cm?.getLinkProperties(activeNet)
                    val dnsServers = linkProps?.dnsServers?.mapNotNull { it.hostAddress }?.filter { !it.contains(":") } ?: emptyList()
                    val dnsStr = if (dnsServers.isNotEmpty()) dnsServers.joinToString(",") else "223.5.5.5,119.29.29.29"
                    com.github.kr328.clash.core.bridge.Bridge.nativeNotifyDnsChanged(dnsStr)
                    com.github.kr328.clash.core.bridge.Bridge.nativeNotifyTimeZoneChanged(
                        java.util.TimeZone.getDefault().id,
                        java.util.TimeZone.getDefault().rawOffset / 1000
                    )
                }

                // Reuse one native subscriber across VPN restarts to avoid duplicate fan-out.
                runCatching {
                    coreLogcatSubscription.subscribeIfNeeded {
                        com.github.kr328.clash.core.bridge.Bridge.nativeSubscribeLogcat(object : com.github.kr328.clash.core.bridge.LogcatInterface {
                            override fun received(jsonPayload: String) {
                                Log.i("NexusCore", jsonPayload)
                                com.github.mihomo.android.data.LogRepository.addRawLogcat(jsonPayload)
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

                val pfd = builder.establish() ?: error("无法建立 VPN TUN 接口 (Builder.establish() 返回 null)")
                vpnInterface = pfd

                // 5. Borrow the app-owned FD; the native listener owns a separate duplicate.
                // Do not adopt/close a descriptor that may already have been reused by native code.
                val selectedStack = settings.tunStack.key
                val gateway = if (ipv6) "172.19.0.1/30,fdfe:dcba:9876::1/126" else "172.19.0.1/30"
                val portal = if (ipv6) "172.19.0.2,fdfe:dcba:9876::2" else "172.19.0.2"
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
                currentCoroutineContext().ensureActive()

                // 6. Report running only after the native listener has started successfully.
                _vpnState.value = _vpnState.value.copy(
                    status = VpnState.Status.RUNNING,
                    coreVersion = coreVer,
                    error = null
                )

                startTrafficMonitor()
                Log.i(TAG, "Nexus VPN successfully started! Core: $coreVer, ipv6=$ipv6, mtu=$mtu, cc=$congestion")
                com.github.mihomo.android.data.LogRepository.addLog(
                    "info",
                    "Nexus VPN 已建立；TUN 栈: $selectedStack；IPv6: $ipv6；MTU: $mtu；核心版本: $coreVer"
                )

            } catch (e: kotlinx.coroutines.CancellationException) {
                ClashCore.stopTun()
                runCatching { vpnInterface?.close() }
                vpnInterface = null
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start Nexus VPN", e)
                com.github.mihomo.android.data.LogRepository.addLog("error", "启动失败: ${e.message}")
                ClashCore.stopTun()
                runCatching { vpnInterface?.close() }
                vpnInterface = null
                _vpnState.value = _vpnState.value.copy(
                    status = VpnState.Status.STOPPED,
                    error = e.message ?: "未知启动错误"
                )
                stopSelf()
            }
            }
        }
    }

    private fun startTrafficMonitor() {
        trafficMonitorJob?.cancel()
        trafficMonitorJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive && _vpnState.value.status == VpnState.Status.RUNNING) {
                delay(1000)
                val now = ClashCore.queryTrafficNow()
                val proxyTotal = ClashCore.queryProxyTrafficTotal()
                com.github.mihomo.android.data.TrafficRecordRepository.recordProxy(proxyTotal)

                val upStr = now.trafficSpeedUpload()
                val downStr = now.trafficSpeedDownload()

                _trafficMetrics.value = TrafficMetrics(upSpeed = upStr, downSpeed = downStr)
                updateNotification("\u2191 $upStr   \u2193 $downStr")
            }
        }
    }

    private fun stopVpn() {
        stopRequested = true
        if (_vpnState.value.status == VpnState.Status.STOPPED) {
            stopSelf()
            return
        }
        _vpnState.value = _vpnState.value.copy(status = VpnState.Status.STOPPING)
        // Cancel an in-flight start first: otherwise it can establish the TUN and flip the state
        // back to RUNNING after we have already torn down and reported STOPPED.
        startJob?.cancel()
        startJob = null
        trafficMonitorJob?.cancel()

        serviceScope.launch { closeTunnel() }
    }

    /**
     * Rebuilds the tunnel with the current settings. The start job holds the lifecycle lock, so it
     * is cancelled and joined before waiting for that lock; otherwise this would deadlock.
     */
    private fun restartVpn() {
        // A settings restart is not an explicit START request. Never revive a stopped tunnel.
        if (stopRequested || _vpnState.value.status == VpnState.Status.STOPPED ||
            _vpnState.value.status == VpnState.Status.STOPPING) return
        serviceScope.launch {
            startJob?.cancelAndJoin()
            lifecycleMutex.withLock {
                if (stopRequested) return@withLock
                // A queued start may have been launched while this coroutine waited for the lock.
                startJob?.cancel()
                startJob = null
                trafficMonitorJob?.cancel()
                closeTunnelLocked()
                startVpn()
            }
        }
    }

    private fun closeTunnelLocked() {
        try {
            ClashCore.stopTun()
            vpnInterface?.close()
        } catch (e: Throwable) {
            Log.w(TAG, "Error closing VPN", e)
        } finally {
            vpnInterface = null
            _vpnState.value = VpnState(status = VpnState.Status.STOPPED)
            _trafficMetrics.value = TrafficMetrics()
        }
    }

    private suspend fun closeTunnel() = lifecycleMutex.withLock {
        startJob?.cancel()
        closeTunnelLocked()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
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
        stopRequested = true
        // The service can be destroyed without ACTION_STOP (process death, low memory), so the
        // native tunnel and the shared state are cleaned up here as well.
        trafficMonitorJob?.cancel()
        startJob?.cancel()
        serviceScope.cancel()
        runCatching { ClashCore.stopTun() }
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        if (_vpnState.value.status != VpnState.Status.STOPPED) {
            _vpnState.value = VpnState(status = VpnState.Status.STOPPED)
            _trafficMetrics.value = TrafficMetrics()
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
