package com.github.arca.android.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.github.arca.android.data.NowhereNodeParser
import com.github.arca.android.data.NowhereNodeStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Hosts the official Rust Vector runtime in a separate Android app process.
 * bindProcessToNetwork() applies only to this PID; the JNI runtime therefore
 * runs here instead of being spawned as a child executable.
 */
class NowhereSidecarService : Service() {
    companion object {
        private const val TAG = "ArCaNowhere"
        private const val ACTION_START = "com.github.arca.android.nowhere.START"
        private const val EXTRA_PROFILE_ID = "profile_id"
        private const val EXTRA_NETWORK = "physical_network"

        fun start(context: Context, profileId: String, physicalNetwork: Network) {
            val intent = Intent(context, NowhereSidecarService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROFILE_ID, profileId)
                putExtra(EXTRA_NETWORK, physicalNetwork)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, NowhereSidecarService::class.java)) }
                .onFailure { Log.w(TAG, "Unable to stop the isolated Vector service", it) }
        }

        suspend fun awaitSocksReady(port: Int, timeoutMs: Long = 12_000L) {
            withContext(Dispatchers.IO) {
                val deadline = SystemClock.elapsedRealtime() + timeoutMs
                var lastFailure: Exception? = null
                while (SystemClock.elapsedRealtime() < deadline) {
                    currentCoroutineContext().ensureActive()
                    try {
                        if (socksHandshake(port)) return@withContext
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        lastFailure = error
                    }
                    delay(120)
                }
                throw IllegalStateException(
                    "Nowhere 本地 SOCKS5 服务未就绪，已阻止 VPN 启动。请检查节点、证书指纹和网络后重试。",
                    lastFailure
                )
            }
        }

        private fun socksHandshake(port: Int): Boolean = Socket().use { socket ->
            socket.soTimeout = 500
            socket.connect(InetSocketAddress("127.0.0.1", port), 500)
            socket.getOutputStream().write(byteArrayOf(0x05, 0x01, 0x00))
            val response = ByteArray(2)
            var read = 0
            while (read < response.size) {
                val count = socket.getInputStream().read(response, read, response.size - read)
                if (count < 0) return@use false
                read += count
            }
            response[0] == 0x05.toByte() && response[1] == 0x00.toByte()
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transitionMutex = Mutex()
    private var vectorJob: Job? = null
    private var boundNetwork: Network? = null
    private var boundProfileId: String? = null
    private var boundPort: Int? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        val network = networkExtra(intent)
        if (profileId.isNullOrBlank() || network == null) {
            Log.e(TAG, "Missing Nowhere profile or physical Network")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        serviceScope.launch {
            try {
                transitionMutex.withLock {
                    val node = withContext(Dispatchers.IO) {
                        NowhereNodeStore.load(this@NowhereSidecarService, profileId)
                            ?: error("Nowhere 节点元数据不存在")
                    }
                    val validated = NowhereNodeParser.parse(node.vectorUrl, node.socksPort)
                    check(validated.vectorUrl == node.vectorUrl) { "Nowhere runtime URL is not canonical" }
                    val sameBinding = boundNetwork == network &&
                        boundProfileId == profileId && boundPort == node.socksPort
                    if (vectorJob?.isActive == true && sameBinding) {
                        return@withLock
                    }

                    stopVectorGracefully()
                    val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    check(connectivity.bindProcessToNetwork(network)) {
                        "无法将 Nowhere 服务绑定到所选物理网络"
                    }
                    boundNetwork = network
                    boundProfileId = profileId
                    boundPort = node.socksPort
                    vectorJob = serviceScope.launch(Dispatchers.IO) {
                        try {
                            NowhereNative.run(node.vectorUrl)
                            if (isActive) Log.e(TAG, "Vector runtime exited unexpectedly")
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            // Avoid logging the exception message: third-party errors can include URLs.
                            Log.e(TAG, "Vector runtime failed (${error::class.java.simpleName})")
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                runCatching {
                    transitionMutex.withLock {
                        stopVectorGracefully()
                        (getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                            ?.bindProcessToNetwork(null)
                    }
                }
                Log.e(TAG, "Unable to start Vector runtime (${error::class.java.simpleName})")
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun networkExtra(intent: Intent): Network? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_NETWORK, Network::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_NETWORK)
        }

    private suspend fun stopVectorGracefully() {
        val job = vectorJob ?: return
        if (!job.isActive) {
            clearBoundNetwork()
            return
        }
        runCatching { Os.kill(Process.myPid(), OsConstants.SIGTERM) }
            .onFailure { Log.w(TAG, "SIGTERM delivery to Vector service failed", it) }
        val stopped = withTimeoutOrNull(5_500L) {
            job.join()
            true
        } ?: false
        if (!stopped) {
            Log.e(TAG, "Vector did not stop cleanly; terminating isolated service process")
            Process.killProcess(Process.myPid())
            return
        }
        clearBoundNetwork()
    }

    private fun clearBoundNetwork() {
        vectorJob = null
        boundNetwork = null
        boundProfileId = null
        boundPort = null
    }

    override fun onDestroy() {
        if (vectorJob?.isActive == true) {
            // This service owns an isolated PID; killing it cannot terminate the main ArCa process.
            runCatching { Process.killProcess(Process.myPid()) }
        }
        runCatching {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                ?.bindProcessToNetwork(null)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
