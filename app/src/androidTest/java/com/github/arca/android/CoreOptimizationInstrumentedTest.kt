package com.github.arca.android

import android.net.VpnService
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.arca.android.core.ClashCore
import com.github.arca.android.core.DnsUpdate
import com.github.arca.android.data.ConfigManager
import com.github.arca.android.data.LogRepository
import com.github.arca.android.data.RequestLogRepository
import com.github.arca.android.data.SettingsManager
import com.github.arca.android.service.MihomoVpnService
import com.github.kr328.clash.core.bridge.Bridge
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Go/JNI tests; only the isolated Debug target package may run these fixtures. */
@RunWith(AndroidJUnit4::class)
class CoreOptimizationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun requireDisposableTarget() {
        check(context.packageName.endsWith(".coretest.v98")) { "Use the isolated V98 debug package" }
    }

    private fun loadFixture() = runBlocking {
        requireDisposableTarget()
        ClashCore.init(context)
        val dir = File(context.cacheDir, "core-v98-test").apply { mkdirs() }
        File(dir, "config.yaml").writeText("""
            mixed-port: 0
            allow-lan: false
            mode: rule
            log-level: warning
            ipv6: false
            dns:
              enable: false
            proxies:
              - name: LocalUnavailable
                type: socks5
                server: 127.0.0.1
                port: 9
            proxy-groups:
              - name: LocalTest
                type: select
                proxies: [LocalUnavailable]
            rules:
              - MATCH,DIRECT
        """.trimIndent())
        val loaded = ClashCore.load(dir)
        assertTrue("Fixture load failed: ${loaded.exceptionOrNull()}", loaded.isSuccess)
    }

    private fun awaitCondition(timeout: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Condition timed out" }
            SystemClock.sleep(20)
        }
    }

    @Test
    fun healthCheckReferencesReturnToBaselineAfterOneThousandCalls(): Unit = runBlocking {
        loadFixture()
        awaitCondition { Bridge.nativePendingHealthChecks() == 0L }
        val baseline = Bridge.nativeGlobalReferenceCount()
        repeat(20) { batch ->
            val callbacks = List(50) { CompletableDeferred<Unit>() }
            callbacks.forEach { Bridge.nativeHealthCheck(it, "LocalTest") }
            withTimeout(20_000) { callbacks.forEach { it.await() } }
            awaitCondition { Bridge.nativePendingHealthChecks() == 0L }
            assertEquals("Batch $batch leaked JNI global refs", baseline, Bridge.nativeGlobalReferenceCount())
        }
        Log.i("ArcaV98Test", "healthchecks=1000 pending=0 globalRefs=$baseline baselineMatched=true")
    }

    @Test
    fun physicalDnsDeduplicatesButHonorsNetworkIdentityAndExplicitFlush() {
        loadFixture()
        val before = ClashCore.dnsUpdateCount()
        val update = DnsUpdate("instrumented-network-a", listOf("1.1.1.1", "8.8.8.8"))
        ClashCore.notifyPhysicalDns(update)
        repeat(100) { ClashCore.notifyPhysicalDns(update) }
        assertEquals(before + 1, ClashCore.dnsUpdateCount())
        ClashCore.notifyPhysicalDns(update.copy(networkIdentity = "instrumented-network-b"))
        assertEquals(before + 2, ClashCore.dnsUpdateCount())
        ClashCore.notifyNetworkChanged(context, force = true)
        assertEquals(before + 3, ClashCore.dnsUpdateCount())
        Log.i("ArcaV98Test", "dns repeated=100 nativeUpdates=1 networkChange=1 explicitFlush=1")
    }

    @Test
    fun localHttpRequestActuallyTraversesTheGoProxy() {
        loadFixture()
        ServerSocket(0, 16, InetAddress.getByName("127.0.0.1")).use { server ->
            val running = AtomicBoolean(true)
            val worker = thread(name = "v98-fixture-http", isDaemon = true) {
                while (running.get()) {
                    try {
                        server.accept().use { client ->
                            client.soTimeout = 5000
                            val input = client.getInputStream().bufferedReader()
                            while (!input.readLine().isNullOrEmpty()) { /* request headers */ }
                            client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 7\r\nConnection: close\r\n\r\nv98-ok!".toByteArray())
                        }
                    } catch (failure: Exception) {
                        if (running.get()) throw failure
                    }
                }
            }
            val address = Bridge.nativeStartHttp("127.0.0.1:0") ?: error("HTTP listener failed")
            val proxyPort = address.substringAfterLast(':').toInt()
            try {
                Socket("127.0.0.1", proxyPort).use { client ->
                    client.soTimeout = 5000
                    client.getOutputStream().write(("GET http://127.0.0.1:${server.localPort}/probe HTTP/1.1\r\n" +
                        "Host: 127.0.0.1:${server.localPort}\r\nConnection: close\r\n\r\n").toByteArray())
                    val response = client.getInputStream().bufferedReader().readText()
                    assertTrue(response, response.contains("200 OK"))
                    assertTrue(response, response.endsWith("v98-ok!"))
                }
                Log.i("ArcaV98Test", "realProxyHttp=200 localFixture=true")
            } finally {
                Bridge.nativeStopHttp()
                running.set(false)
                server.close()
                worker.join(5000)
            }
        }
    }

    @Test
    fun nativeLogQueueDrainsAndPreservesRecentRequests() {
        requireDisposableTarget()
        LogRepository.clear()
        RequestLogRepository.clear()
        RequestLogRepository.setMonitoringEnabled(true)
        repeat(200) { index ->
            assertTrue(LogRepository.enqueueRawLogcat("""{"level":"info","message":"[TCP] src:$index --> host$index.example:443 match Domain using DIRECT"}"""))
        }
        awaitCondition { LogRepository.logsFlow.value.size == 200 && RequestLogRepository.requestsFlow.value.size == 200 }
        assertTrue(LogRepository.logsFlow.value.last().message.contains("host199.example"))
        assertEquals(0, LogRepository.rawLogcatQueueStats.queuedEntries)
        Log.i("ArcaV98Test", "nativeLogQueue records=200 requests=200 drained=true")
    }

    @Test
    fun sustainedVpnForwardsTrafficFromAnotherUid() {
        requireDisposableTarget()
        val fixturePort = InstrumentationRegistry.getArguments().getString("tunFixturePort")?.toIntOrNull()
        org.junit.Assume.assumeTrue("Provide a loopback host fixture through emulator 10.0.2.2", fixturePort != null)
        check(VpnService.prepare(context) == null) { "Grant test VPN consent first" }
        val settings = SettingsManager(context)
        val previous = settings.selectedProfileId
        val profile = ConfigManager.saveLocalProfile(context, "V98 TUN traffic fixture", "proxies:\n  - {name: LocalUnavailable, type: socks5, server: 127.0.0.1, port: 9}\nproxy-groups: []\nrules:\n  - MATCH,DIRECT\n")
        ConfigManager.invalidateProfilesCache()
        settings.selectedProfileId = profile.id
        try {
            MihomoVpnService.start(context)
            awaitCondition(20_000) { MihomoVpnService.vpnState.value.status == com.github.arca.android.service.VpnState.Status.RUNNING }
            // Give Android netd time to finish installing the VPN route before testing actual data.
            SystemClock.sleep(1500)
            repeat(5) {
                val command = "sh /data/local/tmp/arca-v98-tun-probe.sh $fixturePort"
                val response = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText()
                }
                assertTrue("Shell UID traffic failed through VPN: $response", response.contains("arca-v98-tun-ok"))
            }
            awaitCondition { RequestLogRepository.requestsFlow.value.any { it.message.contains("10.0.2.2:$fixturePort") } }
            assertEquals(com.github.arca.android.service.VpnState.Status.RUNNING, MihomoVpnService.vpnState.value.status)
            Log.i("ArcaV98Test", "sustainedTun shellUidRequests=5 httpMarker=true coreRequestRecorded=true")
        } finally {
            MihomoVpnService.stop(context)
            awaitCondition(20_000) { MihomoVpnService.vpnState.value.status == com.github.arca.android.service.VpnState.Status.STOPPED }
            settings.selectedProfileId = previous
        }
    }

    @Test
    fun vpnLifecycleUsesRealTunAndNeverRevivesAfterStop() {
        requireDisposableTarget()
        check(VpnService.prepare(context) == null) { "Grant the test package VPN consent before this test" }
        val settings = SettingsManager(context)
        val oldProfile = settings.selectedProfileId
        val profile = ConfigManager.saveLocalProfile(context, "V98 local integration fixture", "proxies:\n  - {name: LocalUnavailable, type: socks5, server: 127.0.0.1, port: 9}\nproxy-groups: []\nrules:\n  - MATCH,DIRECT\n")
        ConfigManager.invalidateProfilesCache()
        settings.selectedProfileId = profile.id
        val fdBaseline = File("/proc/self/fd").list()?.size ?: error("FD count unavailable")
        try {
            repeat(10) { round ->
                MihomoVpnService.start(context)
                awaitCondition(20_000) { MihomoVpnService.vpnState.value.status == com.github.arca.android.service.VpnState.Status.RUNNING }
                MihomoVpnService.restart(context)
                // Let the restart command reach the Main serializer before the explicit STOP.
                instrumentation.waitForIdleSync()
                MihomoVpnService.stop(context)
                awaitCondition(20_000) { MihomoVpnService.vpnState.value.status == com.github.arca.android.service.VpnState.Status.STOPPED }
                MihomoVpnService.restart(context)
                instrumentation.waitForIdleSync()
                SystemClock.sleep(100)
                assertEquals("Late restart revived round $round", com.github.arca.android.service.VpnState.Status.STOPPED, MihomoVpnService.vpnState.value.status)
            }
            SystemClock.sleep(500)
            val finalFds = File("/proc/self/fd").list()?.size ?: error("FD count unavailable")
            assertTrue("FD growth baseline=$fdBaseline final=$finalFds", finalFds <= fdBaseline + 8)
            Log.i("ArcaV98Test", "vpnRounds=10 stopped=true fdBaseline=$fdBaseline finalFds=$finalFds")
        } finally {
            MihomoVpnService.stop(context)
            settings.selectedProfileId = oldProfile
            // Keep the isolated fixture on disk as evidence; no owner's profile is touched.
        }
    }
}
