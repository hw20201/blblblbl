package com.github.arca.android.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.util.Log
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.core.bridge.TunInterface
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.Traffic
import com.github.kr328.clash.core.model.TunnelState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

object ClashCore {
    private const val TAG = "ClashCore"
    private const val LOAD_TIMEOUT_MS = 20_000L

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Volatile
    private var initialized = false

    @Volatile
    var isCoreLoaded = false
        private set

    private val dnsLock = Any()
    private val dnsState = DnsStatePolicy()
    private val dnsNotificationCount = java.util.concurrent.atomic.AtomicLong()

    /** All DNS entry points share one deduplication state; never read the VPN's virtual DNS. */
    fun notifyNetworkChanged(context: Context, force: Boolean = false) {
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val active = cm?.activeNetwork
            val capabilities = active?.let { cm?.getNetworkCapabilities(it) }
            val physical = active?.takeIf {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            }
            val servers = physical?.let { cm?.getLinkProperties(it)?.dnsServers }
                ?.mapNotNull { it.hostAddress }.orEmpty()
            applyPhysicalDns(DnsUpdate(physical?.networkHandle?.toString(), servers), force)
            Bridge.nativeNotifyTimeZoneChanged(
                java.util.TimeZone.getDefault().id,
                java.util.TimeZone.getDefault().rawOffset / 1000
            )
        }.onFailure { Log.w(TAG, "Failed to notify network context to native core", it) }
    }

    fun notifyPhysicalDns(update: DnsUpdate) = applyPhysicalDns(update, force = false)

    private fun applyPhysicalDns(update: DnsUpdate, force: Boolean) = synchronized(dnsLock) {
        val previous = dnsState.lastAccepted
        val changed = dnsState.update(update.networkIdentity, update.dnsServers)
        val accepted = changed ?: dnsState.lastAccepted
        if (accepted != null && (changed != null || force)) {
            try {
                Bridge.nativeNotifyDnsChanged(accepted.orderedDnsKey)
                dnsNotificationCount.incrementAndGet()
                Log.i(TAG, "Updated physical DNS for network=${accepted.networkIdentity}: ${accepted.orderedDnsKey}")
            } catch (failure: Throwable) {
                // Do not cache a state the core rejected; a later event must be able to retry.
                dnsState.reset()
                previous?.let { dnsState.update(it.networkIdentity, it.dnsServers) }
                throw failure
            }
        }
    }

    /** Process-lifetime diagnostics; no polling is attached to this counter. */
    fun dnsUpdateCount(): Long = dnsNotificationCount.get()

    /** Idempotent; safe to call from any thread. Native loading and asset extraction are heavy. */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        Bridge.init(context)
        initialized = true
        notifyNetworkChanged(context)
        Log.i(TAG, "Mihomo Core initialized. Version: ${getCoreVersion()}")
    }

    suspend fun ensureInitialized(context: Context) = withContext(Dispatchers.IO) {
        init(context)
    }

    suspend fun ensureCoreLoaded(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isCoreLoaded) {
            notifyNetworkChanged(context)
            return@withContext true
        }
        runCatching {
            init(context)
            val settings = com.github.arca.android.data.SettingsManager(context)
            val profiles = com.github.arca.android.data.ConfigManager.getProfiles(context)
            val activeProfile = com.github.arca.android.data.DashboardData.activeProfile(profiles, settings.selectedProfileId)
            if (activeProfile != null && activeProfile.file.exists()) {
                val configFile = com.github.arca.android.data.ConfigManager.prepareConfig(context, activeProfile.file)
                val res = load(configFile)
                if (res.isSuccess) {
                    isCoreLoaded = true
                    runCatching {
                        val allSaved = settings.getAllSelectedNodes()
                        allSaved.forEach { (gName, sNode) ->
                            patchSelector(gName, sNode)
                        }
                    }
                    notifyNetworkChanged(context)
                    return@withContext true
                }
            }
            false
        }.getOrDefault(false)
    }

    fun getCoreVersion(): String {
        return try {
            Bridge.nativeCoreVersion()
        } catch (e: Throwable) {
            "Unknown (${e.message})"
        }
    }

    suspend fun load(configFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val deferred = CompletableDeferred<Unit>()
            val configDir = if (configFile.isDirectory) configFile.absolutePath else (configFile.parentFile?.absolutePath ?: configFile.absolutePath)
            Log.i(TAG, "Loading core config from directory: $configDir (target file: ${configFile.name})")
            Bridge.nativeLoad(deferred, configDir)
            withTimeout(LOAD_TIMEOUT_MS) { deferred.await() }
            isCoreLoaded = true
            Unit
        }
    }

    fun startTun(
        fd: Int,
        stack: String = com.github.arca.android.data.TunStack.DEFAULT.key,
        gateway: String = "172.19.0.1/30,fdfe:dcba:9876::1/126",
        portal: String = "172.19.0.2,fdfe:dcba:9876::2",
        dns: String = "172.19.0.2,fdfe:dcba:9876::2",
        mtu: Int = com.github.arca.android.data.TunTuning.DEFAULT_MTU,
        congestionController: String = "",
        vpnService: VpnService
    ) {
        Log.i(TAG, "Starting TUN with fd=$fd, stack=$stack, mtu=$mtu, cc=$congestionController")
        val cm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vpnService.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        } else null

        val callback = object : TunInterface {
            override fun markSocket(fd: Int) {
                vpnService.protect(fd)
            }

            override fun querySocketUid(protocol: Int, source: String, target: String): Int {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cm != null) {
                    try {
                        val srcAddr = parseInetSocketAddress(source) ?: return 0
                        val dstAddr = parseInetSocketAddress(target) ?: return 0
                        return cm.getConnectionOwnerUid(protocol, srcAddr, dstAddr)
                    } catch (_: Throwable) {
                    }
                }
                return 0
            }
        }
        val options = org.json.JSONObject()
            .put("mtu", com.github.arca.android.data.TunTuning.normalizeMtu(mtu))
            .put("congestion-controller", congestionController)
            .toString()
        val started = TunStartup.start(stack, Bridge::nativeTunStackContract) { selected, contract ->
            if (contract == TunStartup.CONTRACT_TUN_OPTIONS) {
                Bridge.nativeStartTunV2(fd, selected.key, gateway, portal, dns, options, callback)
            } else {
                Bridge.nativeStartTun(fd, selected.key, gateway, portal, dns, callback)
            }
        }
        Log.i(TAG, "Native TUN listener started: ${started.displayName}")
    }

    /**
     * Parses Go net.Addr.String() endpoints: host:port for IPv4 and [host]:port for IPv6,
     * including an optional %zone scope identifier. Invalid endpoints preserve UID 0 fallback.
     */
    internal fun parseInetSocketAddress(address: String): InetSocketAddress? {
        try {
            val host: String
            val portText: String
            val isBracketedIpv6 = address.startsWith("[")
            if (isBracketedIpv6) {
                val closingBracket = address.indexOf(']')
                if (closingBracket <= 1 || closingBracket + 1 >= address.length || address[closingBracket + 1] != ':') {
                    return null
                }
                host = address.substring(1, closingBracket)
                portText = address.substring(closingBracket + 2)
                if (!host.contains(':') || host.contains('[') || host.contains(']')) return null
            } else {
                val separator = address.indexOf(':')
                if (separator <= 0 || separator != address.lastIndexOf(':')) return null
                host = address.substring(0, separator)
                portText = address.substring(separator + 1)
                if (host.contains('[') || host.contains(']')) return null
            }

            if (host.isBlank() || portText.isEmpty() || !portText.all { it in '0'..'9' }) return null
            val port = portText.toIntOrNull()?.takeIf { it in 0..65535 } ?: return null
            val inetAddress = java.net.InetAddress.getByName(host)
            if (isBracketedIpv6 && inetAddress !is java.net.Inet6Address) return null
            return InetSocketAddress(inetAddress, port)
        } catch (_: Exception) {
            return null
        }
    }

    /** True only when the installed native pair understands MTU/congestion-controller options. */
    fun supportsTunOptions(): Boolean = runCatching {
        Bridge.nativeTunStackContract() == TunStartup.CONTRACT_TUN_OPTIONS
    }.getOrDefault(false)

    fun stopTun() {
        Log.i(TAG, "Stopping TUN")
        runCatching { Bridge.nativeStopTun() }
    }

    fun queryTrafficNow(): Traffic {
        return runCatching { Bridge.nativeQueryTrafficNow() }.getOrDefault(0L)
    }

    fun queryTrafficTotal(): Traffic {
        return runCatching { Bridge.nativeQueryTrafficTotal() }.getOrDefault(0L)
    }

    fun queryProxyTrafficTotal(): Traffic {
        return runCatching { Bridge.nativeQueryProxyTrafficTotal() }.getOrDefault(0L)
    }

    fun queryTunnelState(): TunnelState {
        return runCatching {
            val res = Bridge.nativeQueryTunnelState()
            json.decodeFromString<TunnelState>(res)
        }.getOrDefault(TunnelState("rule"))
    }

    fun queryGroupNames(excludeNotSelectable: Boolean = false): List<String> {
        return runCatching {
            val raw = Bridge.nativeQueryGroupNames(excludeNotSelectable)
            val array = json.decodeFromString<JsonArray>(raw)
            array.mapNotNull { it.jsonPrimitive.content }
        }.getOrDefault(emptyList())
    }

    fun queryGroup(name: String, sort: ProxySort = ProxySort.Default): ProxyGroup? {
        return runCatching {
            val raw = Bridge.nativeQueryGroup(name, sort.name)
            if (!raw.isNullOrEmpty()) {
                json.decodeFromString<ProxyGroup>(raw)
            } else null
        }.getOrNull()
    }

    fun patchSelector(selector: String, name: String): Boolean {
        return runCatching {
            Bridge.nativePatchSelector(selector, name)
        }.getOrDefault(false)
    }

    /**
     * Per-proxy delay test (FlClash/BettBox style): measures one proxy by
     * name through the core's URL test. Returns the delay in ms, or -2 when
     * the proxy is missing, the test times out, or it fails. Every call
     * returns a result; nothing is silently dropped. The old group
     * HealthCheck path is gone: it only accepted groups, so a single-node
     * tap was a no-op and a group the core could not return produced no
     * results at all.
     */
    suspend fun testProxyDelay(name: String, testUrl: String = "", timeoutMs: Long = 5_000L): Int = withContext(Dispatchers.IO) {
        if (name.equals("DIRECT", ignoreCase = true)) return@withContext 0
        if (name.equals("REJECT", ignoreCase = true)) return@withContext -2
        if (name.equals("COMPATIBLE", ignoreCase = true)) return@withContext -2
        runCatching {
            Bridge.nativeTestProxyDelay(name, testUrl, timeoutMs)
        }.onFailure {
            Log.w(TAG, "Delay test failed for $name", it)
        }.getOrDefault(-2)
    }

    /**
     * Tests a list of proxies, each with its own timeout, reporting each
     * result through onProgress as it arrives. Every name gets exactly one
     * result (ms or -2); the returned map covers all names. testOne is
     * injectable so unit tests can run the batch without the native core.
     * The Kotlin semaphore only bounds how many coroutines are in flight;
     * the real global cap lives in the native layer (a shared capacity-10
     * channel), so no amount of concurrent app-triggered tests can push
     * more than 10 URL tests into the core at once.
     */
    suspend fun testProxyDelays(
        names: List<String>,
        testUrl: String = "",
        timeoutMs: Long = 5_000L,
        concurrency: Int = 10,
        testOne: suspend (String) -> Int = { testProxyDelay(it, testUrl, timeoutMs) },
        onProgress: ((String, Int) -> Unit)? = null
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        if (names.isEmpty()) return@withContext emptyMap()
        val semaphore = Semaphore(concurrency.coerceAtLeast(1))
        val results = ConcurrentHashMap<String, Int>()
        coroutineScope {
            names.map { name ->
                async {
                    semaphore.withPermit {
                        val delay = testOne(name)
                        results[name] = delay
                        onProgress?.invoke(name, delay)
                    }
                }
            }.awaitAll()
        }
        results.toMap()
    }

    /**
     * The delay to show for a group: the lowest positive member delay, or
     * -2 (timeout) when no member reported one.
     */
    internal fun groupDelayFromMembers(memberDelays: Collection<Int>): Int {
        return memberDelays.filter { it in 1..65534 }.minOrNull() ?: -2
    }

    /** null = native library predates the connection contract (rebuild native to enable). */
    @Volatile
    private var connectionsSupported: Boolean? = null

    fun supportsConnections(): Boolean = connectionsSupported ?: runCatching {
        Bridge.nativeQueryConnections(); true
    }.getOrElse { it !is UnsatisfiedLinkError }.also { connectionsSupported = it }

    fun queryConnections(): com.github.arca.android.core.model.ConnectionSnapshot? {
        if (connectionsSupported == false) return null
        return try {
            val raw = Bridge.nativeQueryConnections()
            connectionsSupported = true
            json.decodeFromString<com.github.arca.android.core.model.ConnectionSnapshot>(raw)
        } catch (e: UnsatisfiedLinkError) {
            connectionsSupported = false
            null
        } catch (e: Throwable) {
            Log.w(TAG, "queryConnections failed: ${e.message}")
            com.github.arca.android.core.model.ConnectionSnapshot()
        }
    }

    fun closeConnection(id: String): Boolean = runCatching { Bridge.nativeCloseConnection(id) }.getOrDefault(false)

    fun closeAllConnections() {
        runCatching { Bridge.nativeCloseAllConnections() }
    }

    fun reset() {
        isCoreLoaded = false
        runCatching { Bridge.nativeReset() }
    }
}



