package com.github.arca.android.ui

import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup

/**
 * Build the proxy-page display model without touching Compose state or the core.
 * Callers may run this on Dispatchers.Default because all inputs are immutable snapshots.
 */
internal fun buildProxyDisplayGroups(
    baseGroups: Map<String, ProxyGroup>,
    liveGroups: Map<String, ProxyGroup>,
    selectedNodes: Map<String, String?>,
    delayCache: Map<String, Int>
): Map<String, ProxyGroup> = baseGroups.mapValues { (groupName, baseGroup) ->
    val live = liveGroups[groupName]
    val liveProxyMap = live?.proxies?.associateBy { it.name } ?: emptyMap()
    val savedNode = selectedNodes[groupName]
    val resolvedNow = if (!savedNode.isNullOrBlank() && baseGroup.proxies.any { it.name == savedNode }) {
        savedNode
    } else {
        live?.now?.ifBlank { baseGroup.now } ?: baseGroup.now
    }

    baseGroup.copy(
        now = resolvedNow,
        proxies = baseGroup.proxies.map { baseProxy ->
            val proxy = liveProxyMap[baseProxy.name] ?: baseProxy
            val scopedKey = "$groupName:${baseProxy.name}"
            val cachedDelay = delayCache[baseProxy.name] ?: delayCache[scopedKey]
            if (cachedDelay != null) proxy.copy(delay = cachedDelay) else proxy
        }
    )
}

/** Build the dashboard's active-group list from the same delay cache as the proxy page. */
internal fun buildDashboardProxyList(
    proxies: List<Proxy>,
    groupName: String,
    delayCache: Map<String, Int>
): List<Proxy> = proxies.map { proxy ->
    val scopedKey = "$groupName:${proxy.name}"
    val cachedDelay = delayCache[proxy.name] ?: delayCache[scopedKey]
    if (cachedDelay != null) proxy.copy(delay = cachedDelay) else proxy
}
