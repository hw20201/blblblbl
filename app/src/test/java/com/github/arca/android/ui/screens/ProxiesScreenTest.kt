package com.github.arca.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.github.kr328.clash.core.model.Proxy
import org.junit.Test

class ProxiesScreenTest {
    @Test
    fun extractLeadingEmojiHandlesFlagsSymbolsAndPlainNames() {
        assertEquals("🇯🇵", extractLeadingEmoji("🇯🇵 日本节点"))
        assertEquals("⚡", extractLeadingEmoji("⚡ Fast"))
        assertNull(extractLeadingEmoji("Tokyo 🇯🇵"))
        assertNull(extractLeadingEmoji("   "))
    }

    @Test
    fun extractLeadingEmojiDoesNotConsumeFollowingText() {
        assertEquals("🌐", extractLeadingEmoji("🌐-Edge"))
    }

    @Test
    fun groupDisplayHidesOnlyUuidDecoration() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        assertEquals("自动选择", proxyGroupDisplayName("自动选择 ($uuid)"))
        assertEquals("Auto", proxyGroupDisplayName("${uuid.uppercase()}_Auto"))
        assertEquals("来源 · 机场 A", proxyGroupDisplayName("来源 · 机场 A · $uuid"))
        assertEquals("策略组", proxyGroupDisplayName(uuid))
        assertEquals("🇭🇰 香港 - 01", proxyGroupDisplayName("🇭🇰 香港 - 01"))
        assertEquals("Auto (primary)", proxyGroupDisplayName("Auto (primary)"))
    }

    @Test
    fun managementRequiresAnAllowlistedOrdinaryNode() {
        val local = Proxy("🇯🇵 Local", type = "Shadowsocks")
        val remote = Proxy("Remote", type = "Trojan")
        val excluded = listOf(
            Proxy("Nested", type = "Selector", isGroup = true),
            Proxy("Group placeholder", type = "Group"),
            Proxy("direct"), Proxy("REJECT"), Proxy("REJECT-DROP"),
            Proxy("PASS"), Proxy("COMPATIBLE"), Proxy("GLOBAL"),
            Proxy("Built-in alias", type = "Reject-Drop")
        )
        val allowed = (listOf(local) + excluded).map { it.name }.toSet()

        assertTrue(canManageProxyNode(local, allowed))
        assertFalse(canManageProxyNode(remote, allowed))
        excluded.forEach { assertFalse(it.name, canManageProxyNode(it, allowed)) }
        assertFalse(canManageProxyNode(local, setOf("Local")))
        assertFalse(canManageProxyNode(local, emptySet()))
        assertFalse(canManageProxyNode(local, null))
    }
}
