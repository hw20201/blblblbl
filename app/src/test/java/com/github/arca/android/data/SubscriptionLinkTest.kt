package com.github.arca.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionLinkTest {
    @Test
    fun `accepts https subscription with name`() {
        val link = SubscriptionLinkParser.parseParts("https://example.com/sub?token=1", " 机场 ")
        assertNotNull(link)
        assertEquals("https://example.com/sub?token=1", link!!.url)
        assertEquals("机场", link.name)
    }

    @Test
    fun `rejects plain http, file and blank urls`() {
        assertNull(SubscriptionLinkParser.parseParts("http://example.com/sub", null))
        assertNull(SubscriptionLinkParser.parseParts("file:///sdcard/a.yaml", null))
        assertNull(SubscriptionLinkParser.parseParts("", "x"))
    }

    @Test
    fun `allows loopback http like manual add`() {
        assertNotNull(SubscriptionLinkParser.parseParts("http://127.0.0.1:8080/sub", null))
    }
}
