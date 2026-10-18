package com.github.mihomo.android.data

import com.github.kr328.clash.core.model.ProxyGroup

/** The same fallback is used by the UI and the VPN service when a saved id is absent. */
object DashboardData {
    fun activeProfile(profiles: List<ProfileItem>, selectedId: String?): ProfileItem? =
        profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()

    fun nodeCount(offlineCount: Int, groups: Map<String, ProxyGroup>): Int {
        val builtIns = setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE", "GLOBAL")
        val liveCount = groups.values.flatMap { it.proxies }
            .filter { !it.isGroup && it.name !in groups && it.name.uppercase() !in builtIns &&
                it.type.lowercase().replace("-", "") !in setOf("direct", "reject", "rejectdrop", "pass", "compatible") }
            .map { it.name }.distinct().size
        return maxOf(offlineCount, liveCount)
    }

    fun requestStatus(isRunning: Boolean, monitoringEnabled: Boolean): String = when {
        !monitoringEnabled -> "已暂停"
        isRunning -> "实时监控"
        else -> "未开启"
    }
}
