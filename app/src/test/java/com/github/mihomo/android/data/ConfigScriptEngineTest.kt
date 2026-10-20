package com.github.mihomo.android.data

import com.google.gson.GsonBuilder
import org.junit.Assert.*
import org.junit.Test

class ConfigScriptEngineTest {

    private val gson = GsonBuilder()
        .setObjectToNumberStrategy(com.google.gson.ToNumberPolicy.LONG_OR_DOUBLE)
        .create()

    @Test
    fun `normalizeYamlData converts whole numbers and expected-status correctly`() {
        val input = mapOf(
            "port" to 7890.0,
            "socks-port" to 7891.0f,
            "ratio" to 1.5,
            "expected-status" to 204.0,
            "list" to listOf(1.0, 2.5, 3.0),
            "nested" to mapOf(
                "expected-status" to 302,
                "timeout" to 5000.0
            )
        )

        @Suppress("UNCHECKED_CAST")
        val normalized = ConfigScriptEngine.normalizeYamlData(input) as Map<String, Any?>

        assertEquals(7890L, normalized["port"])
        assertEquals(7891L, normalized["socks-port"])
        assertEquals(1.5, normalized["ratio"])
        assertEquals("204", normalized["expected-status"])
        assertEquals(listOf(1L, 2.5, 3L), normalized["list"])

        @Suppress("UNCHECKED_CAST")
        val nested = normalized["nested"] as Map<String, Any?>
        assertEquals("302", nested["expected-status"])
        assertEquals(5000L, nested["timeout"])
    }

    @Test
    fun `executeWithRhino runs main function and transforms configuration`() {
        val jsCode = """
            function main(config) {
                config.mode = "global";
                config.proxies.push({ name: "Custom-Node", type: "ss" });
                return config;
            }
        """.trimIndent()

        val jsonInput = """
            {
                "mode": "rule",
                "proxies": [{ "name": "Node-1", "type": "vmess" }],
                "proxy-groups": [{ "name": "PROXY", "type": "select" }]
            }
        """.trimIndent()

        val resultJson = ConfigScriptEngine.executeWithRhino(jsCode, jsonInput, "test.js", gson)
        assertNotNull(resultJson)
        assertTrue(resultJson!!.contains("\"mode\":\"global\"") || resultJson.contains("\"mode\": \"global\""))
        assertTrue(resultJson.contains("Custom-Node"))
        assertTrue(resultJson.contains("Node-1"))
    }

    @Test
    fun `executeWithRhino merge defense prevents dropping proxies`() {
        val jsCode = """
            function main(config) {
                // Malicious or buggy script attempting to wipe out proxies
                config.proxies = [];
                return config;
            }
        """.trimIndent()

        val jsonInput = """
            {
                "proxies": [{ "name": "Important-Node", "type": "ss" }],
                "proxy-groups": [{ "name": "PROXY", "type": "select" }]
            }
        """.trimIndent()

        val resultJson = ConfigScriptEngine.executeWithRhino(jsCode, jsonInput, "wipe.js", gson)
        assertNotNull(resultJson)
        // Merge defense in runnerCode restores the input proxies!
        assertTrue(resultJson!!.contains("Important-Node"))
    }

    @Test
    fun `executeWithRhino blocks access to java reflection and dangerous classes`() {
        val maliciousJs = """
            function main(config) {
                var file = java.lang.System.getProperty("user.home");
                config.secret = file;
                return config;
            }
        """.trimIndent()

        val jsonInput = """{"proxies":[],"proxy-groups":[]}"""

        try {
            ConfigScriptEngine.executeWithRhino(maliciousJs, jsonInput, "exploit.js", gson)
            fail("Expected security exception when accessing Java classes")
        } catch (e: Throwable) {
            // ClassShutter denies access to Java classes
            assertTrue(e is org.mozilla.javascript.EcmaError || e is org.mozilla.javascript.EvaluatorException)
        }
    }
    @Test(timeout = 15_000)
    fun `Rhino fallback cannot swallow the host deadline inside script try catch`() {
        val start = System.nanoTime()
        try {
            ConfigScriptEngine.executeWithRhino(
                "function main(c) { while(true) { try { while(true) {} } catch(e) {} } }",
                "{}", "catch-loop.js", gson
            )
            fail("Expected host deadline")
        } catch (e: Error) {
            assertTrue(e.message.orEmpty().contains("超时"))
            assertTrue(System.nanoTime() - start < 10_000_000_000L)
        }
    }

}
