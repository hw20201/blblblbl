package com.github.mihomo.android.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ProfileParserTest {

    private lateinit var tempFile: File

    @Before
    fun setUp() {
        ProfileParser.clearCache()
        tempFile = File.createTempFile("test_profile", ".yaml")
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
        ProfileParser.clearCache()
    }

    @Test
    fun `parses standard clash yaml correctly`() {
        tempFile.writeText(
            """
            proxies:
              - name: "HK-01"
                type: vmess
                server: 1.2.3.4
                port: 443
              - name: "JP-01"
                type: shadowsocks
                server: 5.6.7.8
                port: 8388
              - name: "US-01"
                type: hysteria2
                server: 9.10.11.12
                port: 443

            proxy-groups:
              - name: "PROXY"
                type: select
                proxies:
                  - "HK-01"
                  - "JP-01"
                  - "DIRECT"
              - name: "AUTO"
                type: url-test
                filter: "^HK"
                url: http://www.gstatic.com/generate_204
                interval: 300

            rules:
              - DOMAIN-SUFFIX,google.com,PROXY
              - DOMAIN-KEYWORD,github,PROXY
              - MATCH,DIRECT
            """.trimIndent()
        )

        val parsed = ProfileParser.parse(tempFile)
        assertEquals(3, parsed.totalNodes)
        assertEquals(3, parsed.totalRules)
        assertEquals(2, parsed.groupNames.size)
        assertTrue(parsed.groupNames.contains("PROXY"))
        assertTrue(parsed.groupNames.contains("AUTO"))

        // PROXY group check
        val proxyGroup = parsed.groups["PROXY"]
        assertNotNull(proxyGroup)
        assertEquals("select", proxyGroup!!.type)
        assertEquals(3, proxyGroup.proxies.size)
        assertEquals("HK-01", proxyGroup.now)

        // AUTO group check with filter ^HK
        val autoGroup = parsed.groups["AUTO"]
        assertNotNull(autoGroup)
        assertEquals(1, autoGroup!!.proxies.size)
        assertEquals("HK-01", autoGroup.proxies[0].name)
    }

    @Test
    fun `applies savedNodes selection to active node`() {
        tempFile.writeText(
            """
            proxies:
              - name: "HK-01"
                type: vmess
              - name: "JP-01"
                type: ss
            proxy-groups:
              - name: "节点选择"
                type: select
                proxies:
                  - "HK-01"
                  - "JP-01"
            rules: []
            """.trimIndent()
        )

        val savedNodes = mapOf("节点选择" to "JP-01")
        val parsed = ProfileParser.parse(tempFile, savedNodes)

        val group = parsed.groups["节点选择"]
        assertNotNull(group)
        assertEquals("JP-01", group!!.now)
        assertEquals("JP-01", parsed.activeNode)
    }

    @Test
    fun `supports include-all and exclude-filter in proxy-groups`() {
        tempFile.writeText(
            """
            proxies:
              - name: "HK-Premium-01"
                type: ss
              - name: "HK-Expire-02"
                type: ss
              - name: "SG-01"
                type: ss
            proxy-groups:
              - name: "ALL-HK"
                type: select
                include-all: true
                exclude-filter: "Expire"
            rules: []
            """.trimIndent()
        )

        val parsed = ProfileParser.parse(tempFile)
        val group = parsed.groups["ALL-HK"]
        assertNotNull(group)
        val proxyNames = group!!.proxies.map { it.name }
        assertTrue(proxyNames.contains("HK-Premium-01"))
        assertTrue(proxyNames.contains("SG-01"))
        assertFalse(proxyNames.contains("HK-Expire-02"))
    }

    @Test
    fun `loads cached proxy providers and resolves group use and include-all`() {
        val providerFile = File(tempFile.parentFile, "cached-provider-${System.nanoTime()}.yaml")
        try {
            providerFile.writeText("""
                proxies:
                  - name: Provider-HK
                    type: ss
                  - name: Provider-JP
                    type: vmess
            """.trimIndent())
            tempFile.writeText("""
                proxies:
                  - name: Inline
                    type: ss
                proxy-providers:
                  cached:
                    type: http
                    url: https://example.invalid/proxies.yaml
                    path: ${providerFile.absolutePath}
                    interval: 3600
                proxy-groups:
                  - name: Via provider
                    type: select
                    use: [cached]
                    proxies: [Inline, DIRECT, REJECT]
                  - name: Everyone
                    type: select
                    include-all: true
                rules:
                  - MATCH,DIRECT
                  - DOMAIN-SUFFIX,example.com,Via provider,no-resolve
                """.trimIndent())
            val parsed = ProfileParser.parse(tempFile)
            assertEquals(3, parsed.totalNodes)
            assertEquals(2, parsed.totalRules)
            assertEquals(setOf("Inline", "Provider-HK", "Provider-JP", "DIRECT", "REJECT"), parsed.groups["Via provider"]!!.proxies.map { it.name }.toSet())
            assertEquals(setOf("Inline", "Provider-HK", "Provider-JP"), parsed.groups["Everyone"]!!.proxies.map { it.name }.toSet())
            assertEquals(3, parsed.totalNodes)
            providerFile.writeText("""
                proxies:
                  - name: Provider-SG
                    type: trojan
            """.trimIndent())
            val refreshed = ProfileParser.parse(tempFile)
            assertEquals(2, refreshed.totalNodes)
            assertTrue(refreshed.groups["Via provider"]!!.proxies.any { it.name == "Provider-SG" })
            assertFalse(refreshed.groups["Via provider"]!!.proxies.any { it.name == "Provider-HK" })
        } finally {
            providerFile.delete()
        }
    }


    @Test
    fun `keeps subgroups and special policies as group members while excluding policies from node count`() {
        tempFile.writeText("""
            proxies:
              - name: Node
                type: ss
            proxy-groups:
              - name: Child
                type: select
                proxies: [Node]
              - name: Parent
                type: select
                proxies: [Child, DIRECT, REJECT]
            rules: []
        """.trimIndent())
        val parsed = ProfileParser.parse(tempFile)
        assertEquals(1, parsed.totalNodes)
        assertEquals(setOf("Child", "DIRECT", "REJECT"), parsed.groups["Parent"]!!.proxies.map { it.name }.toSet())
    }

    @Test
    fun `auto-generates fallback group if no proxy-groups specified`() {
        tempFile.writeText(
            """
            proxies:
              - name: "Standalone-1"
                type: ss
              - name: "Standalone-2"
                type: vmess
            rules: []
            """.trimIndent()
        )

        val parsed = ProfileParser.parse(tempFile)
        assertEquals(2, parsed.totalNodes)
        assertEquals(1, parsed.groupNames.size)
        assertEquals("节点选择", parsed.groupNames[0])
        val group = parsed.groups["节点选择"]
        assertNotNull(group)
        assertEquals(2, group!!.proxies.size)
    }

    @Test
    fun `falls back to robust line parser on corrupted yaml`() {
        // Malformed YAML with syntax error indentation that SnakeYAML cannot parse
        tempFile.writeText(
            """
            proxies:
              - name: "Corrupted-Node"
              type: ss
               bad indentation : :
            proxy-groups:
              - name: "Fallback-Group"
                type: select
                - Corrupted-Node
            rules:
              - MATCH,DIRECT
            """.trimIndent()
        )

        val parsed = ProfileParser.parse(tempFile)
        assertTrue(parsed.totalNodes >= 1)
        assertTrue(parsed.groups.containsKey("Fallback-Group"))
    }

    @Test
    fun `returns empty ParsedProfile for missing or null file`() {
        val nonExistent = File("non_existent_file.yaml")
        val parsedNull = ProfileParser.parse(null)
        val parsedMissing = ProfileParser.parse(nonExistent)

        assertEquals(0, parsedNull.totalNodes)
        assertEquals(0, parsedMissing.totalNodes)
    }
}
