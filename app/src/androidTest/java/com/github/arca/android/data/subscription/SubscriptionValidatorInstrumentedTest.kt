package com.github.arca.android.data.subscription

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.arca.android.data.ConfigManager
import kotlinx.coroutines.*
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the isolated parser with the same APK assets used by import preview. */
@RunWith(AndroidJUnit4::class)
class SubscriptionValidatorInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bundledGeoRulesValidateOfflineInIsolatedProcess() = runBlocking {
        val config = """
            mixed-port: 0
            allow-lan: false
            mode: rule
            log-level: silent
            ipv6: false
            geodata-mode: false
            dns:
              enable: false
            proxies:
              - name: OfflineNode
                type: ss
                server: 127.0.0.1
                port: 443
                cipher: aes-128-gcm
                password: fixture-password
            proxy-groups:
              - name: PROXY
                type: select
                proxies: [OfflineNode]
            rules:
              - GEOSITE,cn,PROXY
              - GEOIP,CN,PROXY
              - MATCH,DIRECT
        """.trimIndent()
        val result = SubscriptionValidator.inspect(context, config, "yaml")
        assertTrue("offline config should be usable: ${result.diagnostics}", result.usable)
        assertTrue("complete config should pass: ${result.diagnostics}", result.completeValid)
        assertTrue(result.format == "mihomo")
    }

    @Test
    fun cancelledInspectionDoesNotBlockTheNextRequest() = runBlocking {
        val pending = launch(Dispatchers.IO) {
            SubscriptionValidator.inspect(context, "proxies:\n" + (1..8_000).joinToString("") {
                "  - {name: N$it, type: ss, server: 127.0.0.1, port: 443, cipher: aes-128-gcm, password: fixture}\n"
            })
        }
        delay(30)
        withTimeout(5_000) { pending.cancelAndJoin() }
        val result = withTimeout(10_000) { SubscriptionValidator.inspect(context,
            "ss://YWVzLTEyOC1nY206Zml4dHVyZQ@127.0.0.1:443#AfterCancel", "uri") }
        assertTrue(result.usable)
        assertEquals(1, result.nodes.size)
    }

    @Test
    fun nowhereShareSurvivesPreviewAggregationAndSave(): Unit = runBlocking {
        check(context.packageName.endsWith(".coretest.v98"))
        val uri = "nowhere://11111111111111111111111111111111@test.example.com:12345?net=udp&morph=1#Test%20Nowhere"
        val inputs = listOf(uri, Base64.encodeToString(uri.toByteArray(), Base64.NO_WRAP))
        for (input in inputs) {
            val preview = SubscriptionManager.preview(context, listOf(SourceDraft("Nowhere fixture", input)))
            assertEquals(0, preview.rejected)
            assertEquals(1, preview.nodes)
            val node = preview.sources.single().inspection.nodes.single()
            assertEquals("nowhere", node.type)
            assertEquals("Test Nowhere", node.name)
            assertEquals("test.example.com", node.data["server"]?.jsonPrimitive?.contentOrNull)
            assertEquals(12345, node.data["port"]?.jsonPrimitive?.int)
            assertEquals("11111111111111111111111111111111", node.data["password"]?.jsonPrimitive?.contentOrNull)
            assertEquals("udp", node.data["network"]?.jsonPrimitive?.contentOrNull)
            assertEquals(true, node.data["morph"]?.jsonPrimitive?.boolean)
            val profile = SubscriptionManager.create(context, "Nowhere fixture", preview, false, false)
            try {
                assertNull(profile.url)
                assertEquals(1, SubscriptionManager.state(context, profile.id)?.current?.nodeCount)
                val saved = ConfigManager.getProfiles(context, forceReload = true).single { it.id == profile.id }
                val checked = SubscriptionValidator.inspect(context, saved.file.readText(), "yaml")
                assertTrue(checked.completeValid)
                assertTrue(checked.diagnostics.isEmpty())
                assertEquals(node.data - "name", checked.nodes.single().data - "name")
            } finally {
                SubscriptionManager.delete(context, profile.id)
            }
        }
    }

    @Test
    fun malformedInputIsRejectedBeforeBinderAndNulNeverCrossesJni() = runBlocking {
        assertFalse(SubscriptionValidator.inspect(context, "not: [a", "yaml").usable)
        try {
            SubscriptionValidator.inspect(context, "name\u0000value", "auto")
            error("NUL input unexpectedly accepted")
        } catch (failure: IllegalArgumentException) {
            assertEquals("内容含有 NUL 字符，无法作为文本导入", failure.message)
        }
    }
}
