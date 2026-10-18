package com.github.mihomo.android.data

import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardDataTest {
    private fun profile(id: String, name: String) = ProfileItem(id, name, null, File("$id.yaml"), 0L)

    @Test fun selectedProfileAndFallbackUseTheSameName() {
        val profiles = listOf(profile("a", "我的订阅"), profile("b", "备用订阅"))
        assertEquals("备用订阅", DashboardData.activeProfile(profiles, "b")?.name)
        assertEquals("我的订阅", DashboardData.activeProfile(profiles, null)?.name)
        assertEquals("我的订阅", DashboardData.activeProfile(profiles, "deleted")?.name)
        assertNull(DashboardData.activeProfile(emptyList(), "deleted"))
    }

    @Test fun liveNodesAreDeduplicatedAndExcludeBuiltinsAndGroups() {
        val groups = mapOf(
            "select" to ProxyGroup("select", listOf(Proxy("HK"), Proxy("auto", isGroup = true), Proxy("DIRECT", type = "Direct"))),
            "auto" to ProxyGroup("url-test", listOf(Proxy("HK"), Proxy("JP"), Proxy("REJECT", type = "Reject")))
        )
        assertEquals(2, DashboardData.nodeCount(0, groups))
        assertEquals(4, DashboardData.nodeCount(4, groups))
        assertEquals(0, DashboardData.nodeCount(0, emptyMap()))
    }

    @Test fun monitoringStatusTracksBothVpnAndPause() {
        assertEquals("实时监控", DashboardData.requestStatus(true, true))
        assertEquals("未开启", DashboardData.requestStatus(false, true))
        assertEquals("已暂停", DashboardData.requestStatus(true, false))
        assertEquals("已暂停", DashboardData.requestStatus(false, false))
    }
}
