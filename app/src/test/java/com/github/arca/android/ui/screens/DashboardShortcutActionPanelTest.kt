package com.github.arca.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardShortcutActionPanelTest {
    @Test
    fun ipFlagsDistinguishUnavailableNoHitsAndPositiveTags() {
        assertEquals("来源未返回", outboundIpFlagsSummary(null))
        assertEquals("本次未命中已知标记", outboundIpFlagsSummary(emptyList()))
        assertEquals("代理 · VPN", outboundIpFlagsSummary(listOf("Proxy", "VPN")))
    }

    @Test
    fun ipClassificationUsesOnlyReturnedValues() {
        assertEquals("来源未提供", outboundIpClassification(null, null))
        assertEquals("机房", outboundIpClassification(null, "Hosting"))
        assertEquals("住宅", outboundIpClassification(null, "Residential"))
        assertEquals("Elevated · 机房", outboundIpClassification("Elevated", "Hosting"))
    }
}
