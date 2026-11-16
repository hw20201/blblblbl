package com.github.arca.android.data

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Plain JVM: reads the shipped XML so a cleartext regression is caught without a device. */
class NetworkSecurityConfigTest {
    private fun configFile(): File = listOf(
        "src/main/res/xml/network_security_config.xml",
        "app/src/main/res/xml/network_security_config.xml"
    ).map(::File).first { it.isFile }

    private val root: Element by lazy {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(configFile()).documentElement
    }

    private fun children(parent: Element, name: String) =
        (0 until parent.childNodes.length).map { parent.childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == name }

    @Test fun baseConfigStillForbidsCleartext() {
        val base = children(root, "base-config").single()
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
    }

    @Test fun cleartextIsPermittedForExactlyLoopbackAndIpApi() {
        val permitted = children(root, "domain-config").filter { it.getAttribute("cleartextTrafficPermitted") == "true" }
        val domains = permitted.flatMap { children(it, "domain") }
        assertEquals(setOf("127.0.0.1", "localhost", "ip-api.com"), domains.map { it.textContent.trim() }.toSet())
        assertEquals(3, domains.size)
        domains.forEach { assertEquals(it.textContent, "false", it.getAttribute("includeSubdomains")) }
    }

    @Test fun noDomainConfigAllowsAnythingElseOrOverridesWithNestedRules() {
        val configs = children(root, "domain-config")
        assertTrue(configs.all { it.getAttribute("cleartextTrafficPermitted") == "true" })
        assertTrue(configs.all { children(it, "domain-config").isEmpty() })
        assertTrue(children(root, "debug-overrides").isEmpty())
    }
}
