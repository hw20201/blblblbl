package com.github.arca.android.data

import android.content.Context
import java.io.File
import java.util.UUID
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.yaml.snakeyaml.Yaml

@RunWith(RobolectricTestRunner::class)
class NowhereNodeStoreTest {
    private lateinit var context: Context
    private val key = "0123456789abcdef0123456789abcdef"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun metadataIsPrivateAndProfileMutationFailsClosed() {
        val profileId = UUID.randomUUID().toString()
        val port = 23456
        val node = NowhereNodeParser.parse("nowhere://$key@node.example:443", port)
        val yaml = node.profileYaml()
        val profileFile = File(context.filesDir, "$profileId.yaml").apply { writeText(yaml) }
        val metadataFile = File(context.filesDir, "nowhere_nodes/$profileId.node")

        try {
            NowhereNodeStore.save(context, profileId, node.vectorUrl, port, yaml)
            assertTrue(NowhereNodeStore.hasNode(context, profileId))
            val stored = NowhereNodeStore.load(context, profileId, profileFile)
            assertNotNull(stored)
            assertEquals(port, stored!!.socksPort)
            assertEquals(node.vectorUrl, stored.vectorUrl)
            assertFalse(metadataFile.readText().contains(key))

            profileFile.appendText("\n# changed after import\n")
            assertThrows(IllegalArgumentException::class.java) {
                NowhereNodeStore.load(context, profileId, profileFile)
            }
        } finally {
            NowhereNodeStore.delete(context, profileId)
            profileFile.delete()
        }
        assertFalse(NowhereNodeStore.hasNode(context, profileId))
    }

    @Test
    fun runtimeConfigPreservesNowhereProxyRouteAndDnsRespectRules() {
        val node = NowhereNodeParser.parse("nowhere://$key@node.example:443", 23457)
        val profile = ConfigManager.saveLocalProfile(context, "Nowhere test", node.profileYaml())
        try {
            val generated = ConfigManager.prepareConfig(context, profile.file).readText()
            val root = Yaml().load<Map<String, Any>>(generated)
            val proxies = root["proxies"] as List<*>
            assertEquals(1, proxies.size)
            val proxy = proxies.single() as Map<*, *>
            assertEquals("socks5", proxy["type"])
            assertEquals("NW3-LOCAL", proxy["name"])
            assertEquals("127.0.0.1", proxy["server"])
            assertEquals(23457, proxy["port"])

            val dns = root["dns"] as Map<*, *>
            assertEquals(true, dns["respect-rules"])
            assertTrue(dns["proxy-server-nameserver"] is List<*>)
            assertEquals(listOf("MATCH,NW3-OUT"), root["rules"])
            assertFalse(generated.contains("MATCH,DIRECT"))
        } finally {
            ConfigManager.deleteProfile(context, profile.id)
        }
    }
}
