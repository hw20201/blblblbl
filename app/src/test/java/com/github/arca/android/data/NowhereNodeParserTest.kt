package com.github.arca.android.data

import org.junit.Assert.*
import org.junit.Test
import org.yaml.snakeyaml.Yaml

class NowhereNodeParserTest {
    private val key = "0123456789abcdef0123456789abcdef"

    @Test fun detectsNodeSchemesForManualImportRouting() {
        assertTrue(NowhereNodeParser.usesNowhereScheme("  nowhere://malformed link "))
        assertTrue(NowhereNodeParser.usesNowhereScheme("VECTOR://$key@host.example:443"))
        assertFalse(NowhereNodeParser.usesNowhereScheme("https://subscription.example/config"))
        assertFalse(NowhereNodeParser.usesNowhereScheme("ordinary text"))
    }

    @Test fun parsesIpv4DomainAndIpv6() {
        assertEquals("1.2.3.4", NowhereNodeParser.parse("nowhere://$key@1.2.3.4:443", 1080).endpointHost)
        assertEquals("node.example.com", NowhereNodeParser.parse("vector://$key@node.example.com:443", 1081).endpointHost)
        val ipv6 = NowhereNodeParser.parse("vector://$key@[2001:db8::1]/udp:443", 1082)
        assertEquals("2001:db8::1", ipv6.endpointHost)
        assertTrue(ipv6.vectorUrl.startsWith("vector://$key@[2001:db8::1]/udp:443"))
    }

    @Test fun canonicalizesCarrierAndPreservesOfficialOptions() {
        val pin = "a".repeat(64)
        val p = NowhereNodeParser.parse("nowhere://$key@host.example:443?net=udp&pin=$pin&sni=edge.example&morph=0&rate=7&etar=9#label", 1400)
        assertEquals("udp", p.carrier)
        assertEquals("label", p.displayName)
        assertTrue(p.vectorUrl.startsWith("vector://$key@host.example/udp:443?"))
        listOf("pin=$pin", "sni=edge.example", "morph=0", "rate=7", "etar=9").forEach { assertTrue(p.vectorUrl, p.vectorUrl.contains(it)) }
        assertFalse(p.vectorUrl.contains("net="))
        assertTrue(p.vectorUrl.contains("in=127.0.0.1%3A1400"))
        assertFalse(p.vectorUrl.contains("#label"))
    }

    @Test fun defaultMorphIsAppliedAndInIsOverridden() {
        val p = NowhereNodeParser.parse("vector://$key@host.example:443?morph=1&in=0.0.0.0%3A99", 9898)
        assertTrue(p.vectorUrl.contains("morph=1"))
        assertTrue(p.vectorUrl.contains("in=127.0.0.1%3A9898"))
        assertFalse(p.vectorUrl.contains("0.0.0.0"))
        val default = NowhereNodeParser.parse("nowhere://$key@host.example:443", 1080)
        assertEquals("tcp", default.carrier)
        assertTrue(default.vectorUrl.contains("morph=1"))
    }

    @Test fun yamlHasOnlyLocalSocksAndMatchRouteAndEscapesNames() {
        val p = NowhereNodeParser.parse("nowhere://$key@host.example:443", 1080, "evil: [name]\nMATCH,DIRECT")
        val root = Yaml().load<Map<String, Any>>(p.profileYaml())
        val proxy = (root["proxies"] as List<*>).single() as Map<*, *>
        assertEquals("socks5", proxy["type"])
        assertEquals("127.0.0.1", proxy["server"])
        assertEquals(1080, proxy["port"])
        assertEquals(true, proxy["udp"])
        assertEquals("NW3-LOCAL", proxy["name"])
        val group = (root["proxy-groups"] as List<*>).single() as Map<*, *>
        assertEquals("NW3-OUT", group["name"])
        assertEquals(listOf("NW3-LOCAL"), group["proxies"])
        val dns = root["dns"] as Map<*, *>
        assertEquals(true, dns["respect-rules"])
        assertTrue(dns["proxy-server-nameserver"] is List<*>)
        assertEquals(listOf("MATCH,NW3-OUT"), root["rules"])
        assertEquals(1, (root["proxies"] as List<*>).size)

        val reservedName = NowhereNodeParser.parse("nowhere://$key@host.example:443", 1080, "DIRECT")
        val reservedRoot = Yaml().load<Map<String, Any>>(reservedName.profileYaml())
        val reservedProxy = (reservedRoot["proxies"] as List<*>).single() as Map<*, *>
        assertEquals("NW3-LOCAL", reservedProxy["name"])
        assertEquals(listOf("MATCH,NW3-OUT"), reservedRoot["rules"])
    }

    @Test fun rejectsMalformedOrUnsafeInput() {
        val bad = listOf(
            "nowhere://ABC@host.example:443", "nowhere://${key.uppercase()}@host.example:443",
            "nowhere://$key@host.example:0", "nowhere://$key@host.example:65536",
            "nowhere://$key@bad..host:443", "nowhere://$key@999.1.1.1:443",
            "nowhere://$key@host.example:443?pin=x&pin=y", "nowhere://$key@host.example:443?sni=x&sni=y",
            "nowhere://$key@host.example:443?morph=1&morph=2", "nowhere://$key@host.example:443?net=tcp&net=udp",
            "nowhere://$key@host.example:443?morph=2", "nowhere://$key@host.example:443?pin=x",
            "nowhere://$key@host.example:443?rate=-1", "nowhere://$key@host.example:443?sni=127.0.0.1",
            "nowhere://$key@host.example:443?rate=1&rate=2", "nowhere://$key@host.example:443?in=127.0.0.1%3A9&in=127.0.0.1%3A10",
            "vector://$key@host.example/tcp:443?net=tcp",
            "http://$key@host.example:443", "nowhere://user:password@host.example:443",
            "nowhere://$key@host.example:443?allowInsecure=1", "nowhere://$key@host.example:443?pin=%GG",
            "nowhere://$key@host.example:443?net=icmp", "vector://$key@host.example/nope:443"
        )
        bad.forEach { input -> assertThrows(input) { NowhereNodeParser.parse(input, 1080) } }
        assertThrows { NowhereNodeParser.parse("nowhere://$key@host.example:443", 0) }
    }

    private fun assertThrows(message: String = "expected invalid input", block: () -> Any?) {
        try { block(); fail(message) } catch (_: IllegalArgumentException) { }
    }
}
