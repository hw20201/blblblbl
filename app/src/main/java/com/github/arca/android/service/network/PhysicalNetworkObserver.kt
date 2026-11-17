package com.github.arca.android.service.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.github.arca.android.core.DnsUpdate
import java.util.concurrent.Executors

/**
 * API 26+ physical-network DNS observation, owned by one service lifecycle.
 *
 * Construct after the core is ready, start once, close during service teardown. Both methods return
 * without waiting for ConnectivityManager/JNI. [onDnsChanged] runs on a dedicated serial worker and
 * can forward [DnsUpdate.orderedDnsKey] to the core. The callback should not touch service/UI state
 * without its usual synchronization. A closed instance cannot restart; create a new one for a new
 * lifecycle. Queued old callbacks are discarded; an already-running consumer callback may finish.
 *
 * Only INTERNET + NOT_VPN candidates contribute DNS. The app default is used when demonstrably
 * physical. When a VPN hides the physical default, validated/unsuspended candidates are compared
 * with stable ties; this is not a claim to reproduce Android's private network scoring. No sockets,
 * underlying networks, TUN state or IPv6 settings are changed.
 */
class PhysicalNetworkObserver(
    context: Context,
    onDnsChanged: (DnsUpdate) -> Unit,
    onError: (Throwable) -> Unit = { Log.w("PhysicalNetworkObserver", "Network observation failed", it) }
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val observation = PhysicalNetworkObservation(
        createWorker = { ExecutorNetworkObservationWorker() },
        createAdapter = {
            val manager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: error("ConnectivityManager unavailable")
            AndroidPhysicalNetworkAdapter(manager)
        },
        onDnsChanged = onDnsChanged,
        onError = onError
    )

    fun start() = observation.start()

    override fun close() = observation.close()
}

private class ExecutorNetworkObservationWorker : NetworkObservationWorker {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "physical-network-dns").apply { isDaemon = true }
    }

    override fun execute(task: () -> Unit) {
        executor.execute { task() }
    }

    override fun close() {
        executor.shutdown()
    }
}

internal class AndroidPhysicalNetworkAdapter(
    private val manager: ConnectivityManager
) : PhysicalNetworkAdapter {
    override fun register(onChanged: () -> Unit): AutoCloseable {
        val physicalCallback = callback(onChanged)
        // Observe only default replacement/loss here. A VPN's capability/LinkProperties changes
        // cannot trigger refreshes, and its DNS is never read or forwarded.
        val defaultCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChanged()
            override fun onLost(network: Network) = onChanged()
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        return registerNetworkCallbacks(listOf(
            {
                manager.registerNetworkCallback(request, physicalCallback)
                AutoCloseable { manager.unregisterNetworkCallback(physicalCallback) }
            },
            {
                manager.registerDefaultNetworkCallback(defaultCallback)
                AutoCloseable { manager.unregisterNetworkCallback(defaultCallback) }
            }
        ))
    }

    override fun snapshot(): PhysicalNetworkSnapshot {
        val activeDefault = manager.activeNetwork
        val candidates = manager.allNetworks.mapNotNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) ||
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) return@mapNotNull null

            PhysicalNetworkCandidate(
                identity = network.networkHandle.toString(),
                dnsServers = manager.getLinkProperties(network)?.dnsServers
                    ?.mapNotNull { it.hostAddress }.orEmpty(),
                validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                notSuspended = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
            )
        }
        return PhysicalNetworkSnapshot(
            candidates = candidates,
            activeDefault = activeDefault?.networkHandle?.toString()
                ?.takeIf { identity -> candidates.any { it.identity == identity } }
        )
    }

    private fun callback(onChanged: () -> Unit) = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onChanged()
        override fun onLost(network: Network) = onChanged()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = onChanged()
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = onChanged()
    }
}
