package com.github.arca.android.ui

import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyDisplayGroupsTest {
    @Test
    fun displayGroupsPreserveSelectionLiveDataAndDelayPrecedence() {
        val baseGroups = mapOf(
            "Main" to ProxyGroup(
                type = "Selector",
                now = "base-node",
                proxies = listOf(Proxy(name = "node-a", title = "Base title", delay = -1))
            ),
            "Other" to ProxyGroup(
                type = "Selector",
                now = "other-base",
                proxies = listOf(Proxy(name = "node-b", delay = -1))
            )
        )
        val liveGroups = mapOf(
            "Main" to ProxyGroup(
                type = "Selector",
                now = "live-node",
                proxies = listOf(Proxy(name = "node-a", title = "Live title", delay = 180))
            ),
            "Other" to ProxyGroup(type = "Selector", now = "", proxies = emptyList())
        )

        val result = buildProxyDisplayGroups(
            baseGroups = baseGroups,
            liveGroups = liveGroups,
            selectedNodes = mapOf("Main" to "node-a", "Other" to "missing-node"),
            delayCache = mapOf("node-a" to 125, "Main:node-a" to 210, "Other:node-b" to 95)
        )

        assertEquals("node-a", result.getValue("Main").now)
        assertEquals("Live title", result.getValue("Main").proxies.single().title)
        assertEquals(125, result.getValue("Main").proxies.single().delay)
        assertEquals("other-base", result.getValue("Other").now)
        assertEquals(95, result.getValue("Other").proxies.single().delay)
    }

    @Test
    fun dashboardListUsesUnscopedDelayBeforeGroupScopedDelay() {
        val proxies = listOf(Proxy(name = "node-a", delay = -1), Proxy(name = "node-b", delay = -1))

        val result = buildDashboardProxyList(
            proxies = proxies,
            groupName = "Main",
            delayCache = mapOf("node-a" to 125, "Main:node-a" to 210, "Main:node-b" to 95)
        )

        assertEquals(listOf(125, 95), result.map { it.delay })
    }
}
