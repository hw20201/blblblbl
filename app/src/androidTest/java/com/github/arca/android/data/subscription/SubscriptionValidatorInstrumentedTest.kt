package com.github.arca.android.data.subscription

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
