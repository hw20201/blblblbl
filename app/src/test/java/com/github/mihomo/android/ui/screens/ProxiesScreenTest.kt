package com.github.mihomo.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
