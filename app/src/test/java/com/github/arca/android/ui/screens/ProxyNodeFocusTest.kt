package com.github.arca.android.ui.screens

import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import org.junit.Assert.*
import org.junit.Test

class ProxyNodeFocusTest {
    private val groups = listOf("Hidden", "Collapsed", "Expanded", "Target", "Following")
    private fun group(size: Int, prefix: String) = ProxyGroup("select", List(size) { Proxy("$prefix $it") })
    private val targetNodes = List(43) { Proxy("Old $it") } + Proxy("Added (2)") + List(90) { Proxy("Provider $it") }
    private val map = mapOf(
        "Hidden" to group(100, "Hidden").copy(hidden = true),
        "Collapsed" to group(80, "Collapsed"),
        "Expanded" to group(11, "Expanded"),
        "Target" to ProxyGroup("select", targetNodes),
        "Following" to group(1, "Following")
    )

    @Test fun actualSavedNameWinsOverTheProviderTailAndAnIncorrectPreferredGroup() {
        val request = ProxyNodeFocusRequest("builtin-default", "Collapsed", listOf("Added (2)"))
        val target = findProxyNodeFocusTarget(request, groups, map)!!
        assertEquals("Target", target.groupName)
        assertEquals("Added (2)", target.nodeName)
        assertEquals(43, target.nodeIndex)
        for (columns in 1..3) {
            val expanded = mapOf("Hidden" to true, "Collapsed" to false, "Expanded" to true, "Target" to true)
            assertEquals(1 + 1 + (11 + columns - 1) / columns + 1 + 43 / columns,
                proxyNodeFocusListIndex(target, groups, map, expanded, columns))
        }
    }

    @Test fun delayedOrHiddenDisplayDoesNotConsumeThePendingNode() {
        val request = ProxyNodeFocusRequest("builtin-default", "Target", listOf("Added (2)"))
        val before = map + ("Target" to ProxyGroup("select", targetNodes.filterNot { it.name == "Added (2)" }))
        assertNull(findProxyNodeFocusTarget(request, groups, before))
        assertNull(findProxyNodeFocusTarget(request, groups, map + ("Target" to map.getValue("Target").copy(hidden = true))))
        assertNotNull(findProxyNodeFocusTarget(request, groups, map))
    }
}
