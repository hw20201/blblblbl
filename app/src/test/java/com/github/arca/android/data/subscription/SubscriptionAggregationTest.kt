package com.github.arca.android.data.subscription

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayInputStream

class SubscriptionAggregationTest {
    private fun node(name: String, password: String = "fixture", extra: String = ""): ImportNode {
        val data = Json.parseToJsonElement("""{"name":"$name","type":"ss","server":"node.invalid","port":443,"cipher":"aes-128-gcm","password":"$password"$extra}""").jsonObject
        return ImportNode(1, name = name, type = "ss", data = data)
    }
    private fun source(id: String, vararg nodes: ImportNode, enabled: Boolean = true) = SourceSnapshot(
        SubscriptionSource(id, "来源 $id", enabled = enabled), "fixture", ImportInspection(nodes = nodes.toList()))

    @Test fun stableIdentityIgnoresLabelsButKeepsSecurityAndTransportFields() {
        assertEquals(SubscriptionAggregator.fingerprint(node("one").data), SubscriptionAggregator.fingerprint(node("renamed").data))
        assertNotEquals(SubscriptionAggregator.fingerprint(node("one").data), SubscriptionAggregator.fingerprint(node("one", "other").data))
        assertNotEquals(SubscriptionAggregator.fingerprint(node("one").data), SubscriptionAggregator.fingerprint(node("one", extra = ",\"udp\":true").data))
    }

    @Test fun deduplicatedNodesRemainInEverySourceGroupAndKeepStableNames() {
        val a = source("a", node("DIRECT"), node("second", "different"))
        val b = source("b", node("renamed"))
        val first = SubscriptionAggregator.generate(listOf(a, b))
        assertEquals(2, first.nodes)
        assertEquals(1, first.duplicates)
        val document = Yaml().load<Map<String, Any>>(first.yaml)
        val groups = document["proxy-groups"] as List<Map<String, Any>>
        val bGroup = groups.single { it["name"].toString().endsWith(" · b") }
        assertTrue((bGroup["proxies"] as List<String>).single() in first.names.values)
        val changedLabels = b.copy(source = b.source.copy(prefix = "new prefix"))
        val next = SubscriptionAggregator.generate(listOf(changedLabels, a), first.names)
        assertEquals(first.names, next.names)
    }

    @Test fun filteringDisableAndNativeFieldsAreAppliedWithoutImportingSourcePolicy() {
        val secure = node("HK secure", extra = ",\"udp\":true,\"plugin\":\"obfs\",\"plugin-opts\":{\"mode\":\"http\",\"host\":\"front.invalid\"}")
        val a = source("a", secure, node("US", "other")).let { it.copy(source = it.source.copy(include = "hk")) }
        val b = source("b", node("disabled"), enabled = false)
        val generated = SubscriptionAggregator.generate(listOf(a, b))
        assertEquals(1, generated.nodes)
        val doc = Yaml().load<Map<String, Any>>(generated.yaml)
        val proxy = (doc["proxies"] as List<Map<String, Any>>).single()
        assertEquals(true, proxy["udp"])
        assertEquals("front.invalid", (proxy["plugin-opts"] as Map<*, *>)["host"])
        assertEquals(listOf("MATCH,节点选择"), doc["rules"])
        assertFalse(doc.containsKey("dns"))
    }

    @Test fun chainAndEmptySelectionsFailBeforePublication() {
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionAggregator.generate(listOf(source("a", node("chain", extra = ",\"dialer-proxy\":\"other\""))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionAggregator.generate(listOf(source("a", node("off"), enabled = false)))
        }
    }

    @Test fun dropGuardRedirectOriginAndRedactionHaveExplicitBoundaries() {
        assertFalse(SubscriptionManager.largeDrop(100, 50))
        assertTrue(SubscriptionManager.largeDrop(100, 49))
        assertFalse(SubscriptionManager.largeDrop(0, 0))
        assertTrue(SubscriptionManager.sameOrigin("https://a.invalid/sub".toHttpUrl(), "https://a.invalid/new".toHttpUrl()))
        assertFalse(SubscriptionManager.sameOrigin("https://a.invalid/sub".toHttpUrl(), "https://b.invalid/new".toHttpUrl()))
        assertFalse(SubscriptionManager.sameOrigin("https://a.invalid/sub".toHttpUrl(), "https://a.invalid:444/new".toHttpUrl()))
        assertFalse(SubscriptionManager.safeFailure(Exception("https://private.invalid/?token=fixture-secret")).contains("fixture-secret"))
    }

    @Test fun textReaderRejectsOverflowAndMalformedUtf8() {
        assertEquals("你好", readBounded(ByteArrayInputStream("你好".toByteArray()), 6))
        assertThrows(IllegalArgumentException::class.java) { readBounded(ByteArrayInputStream(ByteArray(7)), 6) }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { readBounded(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28))) }
    }
}
