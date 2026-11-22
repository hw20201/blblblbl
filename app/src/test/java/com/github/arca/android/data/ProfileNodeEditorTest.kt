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
          - name: Select-Old
            type: select
            proxies: [Old]
        rules:
          - DOMAIN-SUFFIX,Old,Child
          - DOMAIN-SUFFIX,example.com,Old
          - AND,((DOMAIN,a.example),(NETWORK,TCP)),Old
          - SUB-RULE,(NETWORK,TCP),Old
          - MATCH,Old
          - IP-CIDR,192.0.2.0/24,Old,no-resolve
          - DOMAIN,selector.example,Select-Old
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
        rule-providers:
          routing:
            type: http
            behavior: classical
            url: https://example.com/rules/Old
            proxy: Old
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
        val groups = entries(out, "proxy-groups")
        assertEquals(listOf("Old", "Child", "DIRECT", "New"), groups[0]["proxies"])
        assertEquals(listOf("Old"), groups[1]["proxies"])
        assertEquals(parse(raw)["rules"], out["rules"])
        assertEquals(parse(raw)["dns"], out["dns"])
    }

    @Test fun appendAddsMembershipOnlyToTheExactNamedGroup() {
        val original = parse(raw)
        val out = parse(ProfileNodeEditor.append(raw, proxy, "New", groupName = "Select-Old"))
        val groups = entries(out, "proxy-groups")
        assertEquals(entries(original, "proxy-groups").first(), groups[0])
        assertEquals("Select-Old", groups[1]["name"])
        assertEquals(listOf("Old", "New"), groups[1]["proxies"])
        assertEquals("New", entries(out, "proxies").last()["name"])
        assertEquals(original["rules"], out["rules"])
    }

    @Test fun appendAllAddsEveryNodeOnceAndResolvesCollisionsAcrossTheBatch() {
        val second = JsonObject(mapOf("type" to JsonPrimitive("socks5"), "server" to JsonPrimitive("third.example"), "port" to JsonPrimitive(1080)))
        val out = parse(ProfileNodeEditor.appendAll(raw, listOf(proxy to "Old", second to "Old")))
        assertEquals(listOf("Old", "Child", "Old-2", "Old-3"), entries(out, "proxies").map { it["name"] })
        assertEquals(listOf("Old", "Child", "DIRECT", "Old-2", "Old-3"), entries(out, "proxy-groups")[0]["proxies"])
        assertEquals(parse(raw)["rules"], out["rules"])
    }

    @Test fun appendAllRejectsInvalidBatchWithoutChangingInput() {
        val before = raw
        assertThrows(IllegalStateException::class.java) {
            ProfileNodeEditor.appendAll(raw, listOf(proxy to "New"), groupName = "Missing")
        }
        assertEquals(before, raw)
    }

    @Test fun appendRejectsMissingOrInexactGroupWithoutFallingBack() {
        for (groupName in listOf("Missing", "Select-Ol", "select-old")) {
            val failure = assertThrows(IllegalStateException::class.java) {
                ProfileNodeEditor.append(raw, proxy, "New", groupName = groupName)
            }
            assertEquals("目标策略组已不存在，请重新选择", failure.message)
        }
    }

    @Test fun appendRejectsWhenTheTargetGroupExcludesTheNewNode() {
        val config = """
            proxies: []
            proxy-groups:
              - {name: Select, type: select, proxies: [DIRECT]}
              - {name: Filtered, type: select, proxies: [DIRECT], exclude-filter: '^new$'}
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            ProfileNodeEditor.append(config, proxy, "New", groupName = "Filtered")
        }
    }

    @Test fun collisionsWithGroupsNodesAndReservedNamesGetUniqueNames() {
        for (name in listOf("Old", "Select", "DIRECT", "direct", "ReJeCt", "reject-drop", "pass", "compatible", "global")) {
            val out = parse(ProfileNodeEditor.append(raw, proxy, name))
            assertEquals("$name-2", entries(out, "proxies").last()["name"])
        }
    }

    @Test fun renameUpdatesExactTargetsWithoutChangingPayloadsOrSubRuleNames() {
        val out = parse(ProfileNodeEditor.rename(raw, "Old", "Renamed"))
        assertEquals("Renamed", entries(out, "proxies").first()["name"])
        assertEquals("Renamed", entries(out, "proxies")[1]["dialer-proxy"])
        assertEquals(listOf("Renamed", "Child", "DIRECT"), entries(out, "proxy-groups")[0]["proxies"])
        assertEquals(listOf("Renamed"), entries(out, "proxy-groups")[1]["proxies"])
        val rules = out["rules"] as List<*>
        assertEquals("DOMAIN-SUFFIX,Old,Child", rules[0])
        assertEquals("DOMAIN-SUFFIX,example.com,Renamed", rules[1])
        assertEquals("AND,((DOMAIN,a.example),(NETWORK,TCP)),Renamed", rules[2])
        assertEquals("SUB-RULE,(NETWORK,TCP),Old", rules[3])
        assertEquals("MATCH,Renamed", rules[4])
        assertEquals("IP-CIDR,192.0.2.0/24,Renamed,no-resolve", rules[5])
        assertEquals("DOMAIN,selector.example,Select-Old", rules[6])
        assertEquals("Renamed", entries(out, "listeners").single()["proxy"])
        assertEquals(listOf("MATCH,Renamed"), (out["sub-rules"] as Map<*, *>)["Old"])
        assertEquals(parse(raw)["dns"], out["dns"])
        val provider = (out["proxy-providers"] as Map<*, *>)["remote"] as Map<*, *>
        assertEquals("https://example.com/Old", provider["url"])
        assertEquals("Renamed", provider["proxy"])
        assertEquals("Renamed", (provider["override"] as Map<*, *>)["dialer-proxy"])
    }

    @Test fun nodeReturnsYamlWithOnlyTheRequestedInlineProxy() {
        val child = entries(parse(raw), "proxies")[1]
        assertEquals(mapOf("proxies" to listOf(child)), parse(ProfileNodeEditor.node(raw, "Child")))
    }

    @Test fun replaceKeepsTheOriginalNameAndAllRoutingReferences() {
        val original = parse(raw)
        val replacement = JsonObject(proxy + ("name" to JsonPrimitive("Ignored")))
        val out = parse(ProfileNodeEditor.replace(raw, "Old", replacement))
        val nodes = entries(out, "proxies")
        assertEquals(mapOf("name" to "Old", "type" to "socks5", "server" to "new.example", "port" to 1080), nodes.first())
        assertEquals(entries(original, "proxies").drop(1), nodes.drop(1))
        assertEquals(original - "proxies", out - "proxies")
    }

    @Test fun deleteRemovesGroupMembershipAndRedirectsExactReferences() {
        val out = parse(ProfileNodeEditor.delete(raw, "Old"))
        assertEquals(
            mapOf("name" to "Child", "type" to "socks5", "server" to "two.example", "port" to 1080, "dialer-proxy" to "DIRECT"),
            entries(out, "proxies").single()
        )
        val groups = entries(out, "proxy-groups")
        assertEquals(listOf("Select", "Select-Old"), groups.map { it["name"] })
        assertEquals(listOf("Child", "DIRECT"), groups[0]["proxies"])
        assertEquals(listOf("DIRECT"), groups[1]["proxies"])
        assertEquals("DIRECT", entries(out, "listeners").single()["proxy"])

        val provider = (out["proxy-providers"] as Map<*, *>)["remote"] as Map<*, *>
        assertEquals("DIRECT", provider["proxy"])
        assertEquals("DIRECT", (provider["override"] as Map<*, *>)["dialer-proxy"])
        assertEquals("https://example.com/Old", provider["url"])
        val ruleProvider = (out["rule-providers"] as Map<*, *>)["routing"] as Map<*, *>
        assertEquals("DIRECT", ruleProvider["proxy"])
        assertEquals("https://example.com/rules/Old", ruleProvider["url"])

        assertEquals(listOf(
            "DOMAIN-SUFFIX,Old,Child",
            "DOMAIN-SUFFIX,example.com,DIRECT",
            "AND,((DOMAIN,a.example),(NETWORK,TCP)),DIRECT",
            "SUB-RULE,(NETWORK,TCP),Old",
            "MATCH,DIRECT",
            "IP-CIDR,192.0.2.0/24,DIRECT,no-resolve",
            "DOMAIN,selector.example,Select-Old"
        ), out["rules"])
        assertEquals(mapOf("Old" to listOf("MATCH,DIRECT")), out["sub-rules"])
        assertEquals(parse(raw)["dns"], out["dns"])
    }

    @Test fun nodeReplaceAndDeleteRejectMissingOrAmbiguousNodes() {
        val duplicate = """
            proxies:
              - {name: Old, type: socks5, server: one.example, port: 1080}
              - {name: Old, type: socks5, server: two.example, port: 1080}
        """.trimIndent()
        for ((config, name) in listOf(raw to "Absent", raw to "old", duplicate to "Old")) {
            assertThrows(IllegalStateException::class.java) { ProfileNodeEditor.node(config, name) }
            assertThrows(IllegalArgumentException::class.java) { ProfileNodeEditor.replace(config, name, proxy) }
            assertThrows(IllegalArgumentException::class.java) { ProfileNodeEditor.delete(config, name) }
        }
    }

    @Test fun allManualActionsRejectReservedNamesInAnyCaseEvenIfDeclaredInline() {
        for (name in listOf("DIRECT", "direct", "ReJeCt", "reject-drop", "pass", "compatible", "global")) {
            val config = "proxies: [{name: $name, type: socks5, server: one.example, port: 1080}]"
            assertThrows(IllegalArgumentException::class.java) { ProfileNodeEditor.node(config, name) }
            assertThrows(IllegalArgumentException::class.java) { ProfileNodeEditor.replace(config, name, proxy) }
            assertThrows(IllegalArgumentException::class.java) { ProfileNodeEditor.delete(config, name) }
            assertThrows(IllegalArgumentException::class.java) { ProfileNodeEditor.rename(config, name, "Ordinary") }
        }
    }

    @Test fun rawHelpersKeepYamlOrderAndExcludeNonWritableOrHiddenGroups() {
        val config = """
            proxies:
              - {name: Second, type: socks5}
              - {name: First, type: socks5}
            proxy-providers:
              Remote: {type: file, path: providers.yaml}
            proxy-groups:
              - {name: Writable, type: select, use: [Remote]}
              - {name: Hidden, type: select, hidden: true, proxies: [DIRECT]}
              - {name: HiddenText, type: select, hidden: 'TRUE', proxies: [DIRECT]}
              - {name: Broken, type: select, proxies: invalid}
              - {name: Duplicate, type: select, proxies: [DIRECT]}
              - {name: Duplicate, type: select, proxies: [DIRECT]}
              - {name: Unknown, type: unsupported, proxies: [DIRECT]}
              - {name: Automatic, type: url-test, proxies: [First]}
        """.trimIndent()
        assertEquals(listOf("Second", "First"), ProfileNodeEditor.proxyNames(config))
        assertEquals(listOf("Writable", "Automatic"), ProfileNodeEditor.writableGroupNames(config))
        for (name in listOf("Hidden", "HiddenText", "Broken", "Unknown")) {
            assertThrows(IllegalArgumentException::class.java) { ProfileNodeEditor.append(config, proxy, "New", name) }
        }
        assertTrue(ProfileNodeEditor.writableGroupNames("proxies: [{name: Existing}]").isEmpty())
    }

    @Test fun rawNamesReportTheResolvedAddedIdentityRatherThanTheRequestedName() {
        val before = ProfileNodeEditor.proxyNames(raw).toSet()
        val appended = ProfileNodeEditor.append(raw, proxy, "Old")
        assertEquals(listOf("Old-2"), ProfileNodeEditor.proxyNames(appended).filterNot { it in before }.distinct())
    }

    @Test fun invalidRenamesRejectBeforeWriting() {
        for (name in listOf("Child", "Select", "DIRECT", "direct", "ReJeCt", "reject-drop", "pass", "compatible", "global", "", "a,b", "a\nb")) {
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
