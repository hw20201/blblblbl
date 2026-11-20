package com.github.arca.android.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.yaml.snakeyaml.Yaml

class ProfileNodeEditorTest {
    private val raw = """
        proxies:
          - name: Old
            type: socks5
            server: one.example
            port: 1080
          - name: Child
            type: socks5
            server: two.example
            port: 1080
            dialer-proxy: Old
        proxy-groups:
          - name: Select
            type: select
            proxies: [Old, Child, DIRECT]
        rules:
          - DOMAIN-SUFFIX,Old,Child
          - DOMAIN-SUFFIX,example.com,Old
          - AND,((DOMAIN,a.example),(NETWORK,TCP)),Old
          - SUB-RULE,(NETWORK,TCP),Old
          - MATCH,Old
        sub-rules:
          Old:
            - MATCH,Old
        listeners:
          - name: socks-in
            type: socks
            port: 1081
            proxy: Old
        proxy-providers:
          remote:
            type: http
            url: https://example.com/Old
            proxy: Old
            override:
              dialer-proxy: Old
        dns:
          nameserver: [https://Old/dns-query]
    """.trimIndent()
    private val proxy = JsonObject(mapOf("type" to JsonPrimitive("socks5"), "server" to JsonPrimitive("new.example"), "port" to JsonPrimitive(1080)))
    private fun parse(text: String) = Yaml().load<Map<String, Any>>(text)
    private fun entries(config: Map<String, Any>, key: String) = (config[key] as List<*>).map { it as Map<*, *> }

    @Test fun appendAddsNodeAndPrimarySelectorMembershipAndPreservesRouting() {
        val out = parse(ProfileNodeEditor.append(raw, proxy, "New"))
        assertEquals(3, entries(out, "proxies").size)
        assertEquals("New", entries(out, "proxies").last()["name"])
        assertEquals(listOf("Old", "Child", "DIRECT", "New"), entries(out, "proxy-groups").single()["proxies"])
        assertEquals(parse(raw)["rules"], out["rules"])
        assertEquals(parse(raw)["dns"], out["dns"])
    }

    @Test fun collisionsWithGroupsNodesAndReservedNamesGetUniqueNames() {
        for (name in listOf("Old", "Select", "DIRECT")) {
            val out = parse(ProfileNodeEditor.append(raw, proxy, name))
            assertEquals("$name-2", entries(out, "proxies").last()["name"])
        }
    }

    @Test fun renameUpdatesExactTargetsWithoutChangingPayloadsOrSubRuleNames() {
        val out = parse(ProfileNodeEditor.rename(raw, "Old", "Renamed"))
        assertEquals("Renamed", entries(out, "proxies").first()["name"])
        assertEquals("Renamed", entries(out, "proxies")[1]["dialer-proxy"])
        assertEquals(listOf("Renamed", "Child", "DIRECT"), entries(out, "proxy-groups").single()["proxies"])
        val rules = out["rules"] as List<*>
        assertEquals("DOMAIN-SUFFIX,Old,Child", rules[0])
        assertEquals("DOMAIN-SUFFIX,example.com,Renamed", rules[1])
        assertEquals("AND,((DOMAIN,a.example),(NETWORK,TCP)),Renamed", rules[2])
        assertEquals("SUB-RULE,(NETWORK,TCP),Old", rules[3])
        assertEquals("MATCH,Renamed", rules[4])
        assertEquals("Renamed", entries(out, "listeners").single()["proxy"])
        assertEquals(listOf("MATCH,Renamed"), (out["sub-rules"] as Map<*, *>)["Old"])
        assertEquals(parse(raw)["dns"], out["dns"])
        val provider = (out["proxy-providers"] as Map<*, *>)["remote"] as Map<*, *>
        assertEquals("https://example.com/Old", provider["url"])
        assertEquals("Renamed", provider["proxy"])
        assertEquals("Renamed", (provider["override"] as Map<*, *>)["dialer-proxy"])
    }

    @Test fun invalidRenamesRejectBeforeWriting() {
        for (name in listOf("Child", "Select", "DIRECT", "", "a,b", "a\nb")) {
            assertTrue(runCatching { ProfileNodeEditor.rename(raw, "Old", name) }.isFailure)
        }
        assertTrue(runCatching { ProfileNodeEditor.rename(raw, "Absent", "New") }.isFailure)
    }

    @Test fun noOpRenamePreservesBytesAndInvalidYamlIsRejected() {
        assertEquals(raw, ProfileNodeEditor.rename(raw, "Old", "Old"))
        assertTrue(runCatching { ProfileNodeEditor.append("proxies: []\nproxies: []", proxy, "New") }.isFailure)
        assertTrue(runCatching { ProfileNodeEditor.append("proxies: 123", proxy, "New") }.isFailure)
    }
}
