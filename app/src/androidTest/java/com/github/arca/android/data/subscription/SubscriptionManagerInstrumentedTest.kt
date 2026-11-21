package com.github.arca.android.data.subscription

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.arca.android.data.ConfigManager
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Loopback-only fixtures exercise download -> isolated validation -> atomic publication. */
@RunWith(AndroidJUnit4::class)
class SubscriptionManagerInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun nodes(vararg labels: String) = "proxies:\n" + labels.mapIndexed { index, label ->
        "  - {name: $label, type: ss, server: 127.0.0.1, port: ${8000 + index}, cipher: aes-128-gcm, password: fixture-$label}\n"
    }.joinToString("")

    @Test
    fun failedSourcesAndLargeDropsRetainSnapshotsAndRollbackRestoresVersion(): Unit = runBlocking {
        check(context.packageName.endsWith(".coretest.v98"))
        FixtureServer().use { server ->
            server.responses["/a"] = Reply(nodes("A1", "A2", "A3"))
            server.responses["/b"] = Reply(nodes("B1"))
            val preview = SubscriptionManager.preview(context, listOf(
                SourceDraft("来源 A", server.url("/a")), SourceDraft("来源 B", server.url("/b"))))
            assertEquals(0, preview.rejected)
            val profile = SubscriptionManager.create(context, "聚合测试", preview, false, false)
            try {
                val original = profile.file.readText()
                assertEquals(4, SubscriptionManager.state(context, profile.id)!!.current.nodeCount)
                server.responses["/a"] = Reply(nodes("A1"))
                server.responses["/b"] = Reply("<html>fixture error</html>")
                val failed = SubscriptionManager.update(context, profile)
                assertFalse(failed.changed)
                assertEquals(2, failed.sources.count { it.retained })
                assertEquals(original, selected(profile.id).file.readText())

                server.responses["/a"] = Reply(nodes("A1", "A2", "A3", "A4"))
                val partial = SubscriptionManager.update(context, selected(profile.id))
                assertTrue(partial.changed)
                assertEquals(1, partial.sources.count { it.retained })
                val current = SubscriptionManager.state(context, profile.id)!!.current
                assertEquals(5, current.nodeCount)
                assertEquals(1, current.sources.count { it.retained })
                assertEquals(original, SubscriptionManager.state(context, profile.id)!!.previous!!.content)
                assertNotEquals(original, selected(profile.id).file.readText())

                val beforeInvalid = selected(profile.id).file.readText()
                try {
                    SubscriptionManager.replace(context, selected(profile.id), "proxies: [invalid]")
                    fail("Invalid replacement unexpectedly committed")
                } catch (_: IllegalArgumentException) { /* existing head must remain */ }
                assertEquals(beforeInvalid, selected(profile.id).file.readText())

                val restored = SubscriptionManager.rollback(context, selected(profile.id))
                assertEquals(original, restored.file.readText())
                assertEquals(original, selected(profile.id).file.readText())
                assertTrue(restored.managed)
                assertNull(restored.url)
            } finally {
                SubscriptionManager.delete(context, profile.id)
            }
        }
    }

    @Test
    fun conditionalRefreshKeepsQuotaAndEncryptedStateContainsPrivateSourceMaterial(): Unit = runBlocking {
        check(context.packageName.endsWith(".coretest.v98"))
        FixtureServer().use { server ->
            server.responses["/cached"] = Reply(nodes("Cached"), headers = mapOf(
                "ETag" to "\"fixture-v1\"", "subscription-userinfo" to "upload=10; download=20; total=1000; expire=2000000000"))
            val preview = SubscriptionManager.preview(context, listOf(SourceDraft("缓存", server.url("/cached"))))
            val token = "fixture-private-device-token"
            val privatePreview = ImportPreview(preview.sources.map {
                it.copy(source = it.source.copy(token = token))
            })
            val profile = SubscriptionManager.create(context, "缓存测试", privatePreview, false, false)
            try {
                val store = SubscriptionStore(context)
                val head = File(context.filesDir, "profiles/${profile.id}.subscription").readText()
                assertFalse(head.contains(token))
                assertFalse(head.contains(server.url("/cached")))
                val pointer = requireNotNull(store.head(profile.id))
                val encrypted = File(context.noBackupFilesDir, "subscriptions/${profile.id}/${pointer.stateId}.bin").readBytes()
                assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains(token))
                assertEquals(token, store.read(profile.id)!!.current.sources.single().source.token)
                val source = store.read(profile.id)!!.current.sources.single().source
                // Remove the fixture token before loopback refresh; real device tokens require HTTPS.
                val unlocked = SubscriptionManager.reconfigure(context, profile, listOf(source.copy(token = "")))
                server.responses["/cached"] = Reply("", 304)
                val update = SubscriptionManager.update(context, unlocked)
                assertFalse(update.changed)
                assertFalse(update.sources.single().retained)
                assertEquals("\"fixture-v1\"", server.requests["/cached"]?.get("if-none-match"))
                val cached = store.read(profile.id)!!.current.sources.single()
                assertEquals(1000L, ConfigManager.parseSubscriptionUserInfo(cached.userInfo)!!.total)
                assertEquals(1, cached.inspection.nodes.size)
            } finally {
                SubscriptionManager.delete(context, profile.id)
            }
        }
    }

    @Test
    fun defaultEditorAndLocalFileReplacementsKeepManualNodeManagementAvailable(): Unit = runBlocking {
        check(context.packageName.endsWith(".coretest.v98"))
        val original = ConfigManager.getOrCreateDefaultProfile(context)
        val originalText = original.file.readText()
        try {
            ConfigManager.updateProfile(context, original.copy(name = "我的独立节点"))
            for (name in listOf("Edited", "FileReplacement")) {
                val replacement = nodes(name) + "proxy-groups: [{name: Select, type: select, proxies: [$name, DIRECT]}]\nrules: [\"MATCH,Select\"]\n"
                val saved = SubscriptionManager.replace(context, ConfigManager.getOrCreateDefaultProfile(context), replacement)
                assertEquals(ConfigManager.DEFAULT_PROFILE_ID, saved.id)
                assertEquals(original.file, saved.file)
                assertEquals("我的独立节点", saved.name)
                assertFalse(saved.managed)
                assertFalse(saved.canRefresh)
                assertNull(SubscriptionManager.state(context, saved.id))
                ConfigManager.readProfileNode(context, saved.id, name).getOrThrow()
                ConfigManager.renameProfileNode(context, saved.id, name, "$name-Renamed").getOrThrow()
                ConfigManager.appendNodeToProfile(context, saved.id, nodes(name), "Select").getOrThrow()
                ConfigManager.deleteProfileNode(context, saved.id, "$name-Renamed").getOrThrow()
                assertEquals(listOf(name), ConfigManager.readProfileProxyNames(context, saved.id))
                SubscriptionManager.delete(context, saved.id)
                assertTrue(saved.file.isFile)
            }
        } finally {
            assertTrue(ConfigManager.saveProfileContent(context, original.id, originalText))
            ConfigManager.updateProfile(context, original)
        }
    }

    @Test
    fun partialPreviewRequiresExplicitAcceptanceAndDoesNotImportBadLines(): Unit = runBlocking {
        check(context.packageName.endsWith(".coretest.v98"))
        val good = "ss://YWVzLTEyOC1nY206Zml4dHVyZQ@127.0.0.1:443#Local"
        val preview = SubscriptionManager.preview(context, listOf(SourceDraft("链接", "$good\nunknown://fixture-secret", "uri")))
        assertEquals(1, preview.nodes)
        assertEquals(1, preview.rejected)
        try {
            SubscriptionManager.create(context, "部分导入", preview, false, false)
            fail("Partial import unexpectedly committed without confirmation")
        } catch (_: IllegalArgumentException) { /* confirmation required */ }
        val profile = SubscriptionManager.create(context, "部分导入", preview, false, true)
        try {
            assertEquals(1, SubscriptionManager.state(context, profile.id)!!.current.nodeCount)
            assertFalse(profile.file.readText().contains("fixture-secret"))
        } finally { SubscriptionManager.delete(context, profile.id) }
    }

    @Test
    fun cancelledRefreshNeverPublishesCandidate(): Unit = runBlocking {
        check(context.packageName.endsWith(".coretest.v98"))
        FixtureServer().use { server ->
            server.responses["/cancel"] = Reply(nodes("Original"))
            val preview = SubscriptionManager.preview(context, listOf(SourceDraft("取消测试", server.url("/cancel"))))
            val profile = SubscriptionManager.create(context, "取消测试", preview, false, false)
            val gate = CountDownLatch(1)
            try {
                val before = SubscriptionStore(context).head(profile.id)
                server.requests.remove("/cancel")
                server.responses["/cancel"] = Reply(nodes("Changed"), gate = gate)
                val update = launch(Dispatchers.IO) { SubscriptionManager.update(context, profile) }
                withTimeout(5_000) { while (!server.bodyWaiting.contains("/cancel")) delay(10) }
                delay(100) // The response callback has time to enter the deliberately blocked body read.
                withTimeout(5_000) { update.cancelAndJoin() }
                assertEquals(before, SubscriptionStore(context).head(profile.id))
            } finally {
                gate.countDown()
                SubscriptionManager.delete(context, profile.id)
            }
        }
    }

    @Test
    fun interruptedAtomicHeadIsDiscoveredAndRecovered(): Unit = runBlocking {
        check(context.packageName.endsWith(".coretest.v98"))
        val preview = SubscriptionManager.preview(context, listOf(SourceDraft("恢复", nodes("Recovery"), remote = false)))
        val profile = SubscriptionManager.create(context, "恢复测试", preview, false, false)
        try {
            val head = File(context.filesDir, "profiles/${profile.id}.subscription")
            val backup = File("${head.path}.bak")
            assertTrue(head.renameTo(backup))
            assertEquals(profile.file.readText(), selected(profile.id).file.readText())
            assertTrue(head.isFile)
            assertFalse(backup.exists())
            assertNotNull(SubscriptionManager.state(context, profile.id))
        } finally { SubscriptionManager.delete(context, profile.id) }
    }

    @Test
    fun unnamedRemoteImportsResolveUrlHeadersBodyAndHostThroughTheDownloadEntry(): Unit = runBlocking {
        check(context.packageName.endsWith(".coretest.v98"))
        FixtureServer().use { server ->
            val body = "# title: Body Name\n" + nodes("Imported")
            val titled = Reply(body, headers = mapOf(
                "profile-title" to "Header%20Name", "Content-Disposition" to "attachment; filename=Ignored.yaml"))
            val cases = listOf(
                Triple("/fragment?name=Query%20Name#Fragment%20Name", titled, "Fragment Name"),
                Triple("/query?name=Query%20Name", titled, "Query Name"),
                Triple("/query-title?title=Query%20Title", titled, "Query Title"),
                Triple("/header", titled, "Header Name"),
                Triple("/filename", Reply(body, headers = mapOf("Content-Disposition" to "attachment; filename=Filename.yaml")), "Filename"),
                Triple("/encoded-filename", Reply(body, headers = mapOf("Content-Disposition" to "attachment; filename*=UTF-8''Encoded%20Filename.yaml")), "Encoded Filename"),
                Triple("/comment", Reply(body), "Body Name"),
                Triple("/plain", Reply(nodes("Imported")), "127.0.0.1")
            )
            for ((path, reply, expected) in cases) {
                server.responses[path.substringBefore('#')] = reply
                val profile = ConfigManager.downloadSubscription(context, server.url(path), "  ").getOrThrow()
                try {
                    assertEquals(path, expected, profile.name)
                    val current = SubscriptionManager.state(context, profile.id)!!.current
                    val snapshot = current.sources.single()
                    assertEquals(expected, snapshot.suggestedName)
                    assertEquals(expected, snapshot.source.name)
                    assertEquals(expected, ImportPreview(current.sources).suggestedName)
                    assertEquals(server.url(path), snapshot.source.url)
                    assertEquals(expected, selected(profile.id).name)
                    assertTrue(profile.canRefresh)
                } finally { SubscriptionManager.delete(context, profile.id) }
            }
        }
    }

    @Test
    fun explicitProfileAndSourceNamesTakePrecedenceOverRemoteMetadata(): Unit = runBlocking {
        check(context.packageName.endsWith(".coretest.v98"))
        FixtureServer().use { server ->
            server.responses["/explicit"] = Reply("# title: Body Name\n" + nodes("Explicit"),
                headers = mapOf("profile-title" to "Header%20Name"))
            val url = server.url("/explicit#Fragment%20Name")
            val automatic = SubscriptionManager.preview(context, listOf(SourceDraft("", url)))
            assertEquals(0, automatic.rejected)
            assertEquals("Fragment Name", automatic.sources.single().suggestedName)
            assertEquals("Fragment Name", automatic.sources.single().source.name)
            val inferred = SubscriptionManager.create(context, "", automatic, false, false)
            try { assertEquals("Fragment Name", inferred.name) }
            finally { SubscriptionManager.delete(context, inferred.id) }

            val aliased = SubscriptionManager.preview(context, listOf(SourceDraft("  Source Alias  ", url)))
            assertEquals(0, aliased.rejected)
            assertEquals("Source Alias", aliased.sources.single().source.name)
            assertEquals("Source Alias", aliased.suggestedName)
            val named = SubscriptionManager.create(context, "  My Configuration  ", aliased, false, false)
            try {
                assertEquals("My Configuration", named.name)
                assertEquals("Source Alias", SubscriptionManager.state(context, named.id)!!.current.sources.single().source.name)
            } finally { SubscriptionManager.delete(context, named.id) }

            val legacyEntry = ConfigManager.downloadSubscription(context, url, "  Explicit Download  ").getOrThrow()
            try { assertEquals("Explicit Download", legacyEntry.name) }
            finally { SubscriptionManager.delete(context, legacyEntry.id) }
        }
    }

    @Test
    fun remoteCompleteConfigurationKeepsItsPolicyProvidersAndRefreshesAsAStandaloneProfile(): Unit = runBlocking {
        check(context.packageName.endsWith(".coretest.v98"))
        FixtureServer().use { server ->
            val complete = nodes("Original") + """
                proxy-providers:
                  FixtureProvider:
                    type: http
                    url: ${server.url("/provider.yaml")}
                    path: ./providers/import-fixture.yaml
                    interval: 3600
                rule-providers:
                  FixtureRules:
                    type: http
                    behavior: domain
                    format: text
                    url: ${server.url("/rules.txt")}
                    path: ./rules/import-fixture.txt
                    interval: 3600
                proxy-groups:
                  - name: OriginalGroup
                    type: select
                    proxies: [Original, DIRECT]
                    use: [FixtureProvider]
                rules:
                  - RULE-SET,FixtureRules,OriginalGroup
                  - DOMAIN-SUFFIX,example.invalid,OriginalGroup
                  - MATCH,DIRECT
            """.trimIndent()
            server.responses["/complete.yaml"] = Reply(complete, headers = mapOf("profile-title" to "Full%20Remote"))
            val profile = ConfigManager.downloadSubscription(context, server.url("/complete.yaml"), "").getOrThrow()
            try {
                assertEquals("Full Remote", profile.name)
                assertEquals(complete, profile.file.readText())
                val original = SubscriptionManager.state(context, profile.id)!!.current
                assertTrue(original.standalone)
                assertTrue(ImportPreview(original.sources).supportsStandalone)
                assertTrue(profile.managed)
                assertTrue(profile.canRefresh)
                assertNull(profile.url)
                assertFalse(server.requests.containsKey("/provider.yaml"))
                assertFalse(server.requests.containsKey("/rules.txt"))

                val changed = complete.replace("example.invalid", "updated.invalid")
                server.responses["/complete.yaml"] = Reply(changed, headers = mapOf("profile-title" to "Changed%20Server%20Title"))
                val update = SubscriptionManager.update(context, profile)
                assertTrue(update.changed)
                assertFalse(update.sources.single().retained)
                assertEquals(changed, selected(profile.id).file.readText())
                assertEquals("Full Remote", selected(profile.id).name)
                val current = SubscriptionManager.state(context, profile.id)!!.current
                assertTrue(current.standalone)
                assertEquals("Full Remote", current.sources.single().source.name)
                assertFalse(server.requests.containsKey("/provider.yaml"))
                assertFalse(server.requests.containsKey("/rules.txt"))
            } finally { SubscriptionManager.delete(context, profile.id) }
        }
    }

    private fun selected(id: String) = ConfigManager.getProfiles(context, forceReload = true).single { it.id == id }

    private data class Reply(val body: String, val status: Int = 200, val headers: Map<String, String> = emptyMap(), val gate: CountDownLatch? = null)

    private class FixtureServer : Closeable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val running = AtomicBoolean(true)
        val responses = ConcurrentHashMap<String, Reply>()
        val requests = ConcurrentHashMap<String, Map<String, String>>()
        val bodyWaiting = ConcurrentHashMap.newKeySet<String>()
        private val worker = thread(name = "subscription-loopback-fixture", isDaemon = true) {
            while (running.get()) try {
                socket.accept().use { client ->
                    client.soTimeout = 5_000
                    val input = client.getInputStream().bufferedReader()
                    val path = input.readLine().split(" ")[1]
                    val headers = linkedMapOf<String, String>()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                    }
                    requests[path] = headers
                    val reply = responses[path] ?: Reply("", 404)
                    val body = reply.body.toByteArray(Charsets.UTF_8)
                    val head = buildString {
                        append("HTTP/1.1 ${reply.status} Fixture\r\nContent-Length: ${body.size}\r\nConnection: close\r\n")
                        reply.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                        append("\r\n")
                    }
                    client.getOutputStream().apply {
                        write(head.toByteArray())
                        flush()
                        if (reply.gate != null) {
                            bodyWaiting.add(path)
                            reply.gate.await(10, TimeUnit.SECONDS)
                        }
                        write(body)
                        flush()
                    }
                }
            } catch (_: Exception) { if (!running.get()) break }
        }
        fun url(path: String) = "http://127.0.0.1:${socket.localPort}$path"
        override fun close() { running.set(false); socket.close(); worker.join(5_000) }
    }
}
