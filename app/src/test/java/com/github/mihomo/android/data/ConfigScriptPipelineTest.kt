package com.github.mihomo.android.data

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConfigScriptPipelineTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val raw = "proxies: []\nrules: [MATCH,DIRECT]\n"

    @Before fun clearScripts() {
        ScriptManager.getScripts(context).forEach { ScriptManager.deleteScript(context, it.id) }
    }

    private fun save(name: String, code: String) =
        ScriptManager.saveLocalScript(context, name, "", "rule", code)

    @Test fun `no selected scripts leaves YAML byte for byte unchanged`() {
        assertEquals(raw, ConfigScriptEngine.executeScripts(context, raw, null, false))
    }

    @Test fun `UTF8 byte limit rejects multibyte source before parsing`() {
        val source = "//" + "中".repeat(ConfigScriptEngine.MAX_SCRIPT_BYTES / 3 + 1) + "\nfunction main(c) { return c; }"
        val script = save("large", source)
        assertEquals(raw, ConfigScriptEngine.executeScripts(context, raw, listOf(script.id), false))
    }

    @Test fun `existing module exports scripts still execute through the unchanged engine pipeline`() {
        val good = save("good", "module.exports = function(c) { c.marker = 'recovered'; return c; };")
        val output = ConfigScriptEngine.executeScripts(context, raw, listOf(good.id), false)
        assertTrue(output.contains("recovered"))
        assertTrue(output.contains("proxies"))
    }
}
