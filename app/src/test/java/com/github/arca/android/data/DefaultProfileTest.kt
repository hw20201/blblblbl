package com.github.arca.android.data

import android.content.Context
import com.github.arca.android.data.subscription.ImportInspection
import com.github.arca.android.data.subscription.ImportNode
import com.github.arca.android.data.subscription.ImportPreview
import com.github.arca.android.data.subscription.SourceSnapshot
import com.github.arca.android.data.subscription.SubscriptionHead
import com.github.arca.android.data.subscription.SubscriptionSource
import com.github.arca.android.data.subscription.SubscriptionStore
import com.github.arca.android.data.subscription.subscriptionJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultProfileTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        ConfigManager.getProfilesDir(context).listFiles()?.filter { it.name.startsWith(ConfigManager.DEFAULT_PROFILE_ID) }?.forEach { it.delete() }
        context.getSharedPreferences("profiles_meta", Context.MODE_PRIVATE).edit().clear().commit()
        ConfigManager.invalidateProfilesCache()
    }

    @Test fun freshDefaultShipsWithFullSplitRules() {
        val initial = ConfigManager.getOrCreateDefaultProfile(context)
        val content = initial.file.readText()
        assertTrue(content.contains("GEOSITE,CN,DIRECT"))
        assertTrue(content.contains("GEOIP,CN,DIRECT"))
        assertTrue(content.contains("IP-CIDR,192.168.0.0/16,DIRECT,no-resolve"))
        assertTrue(content.contains("MATCH,GLOBAL"))
        assertFalse(content.contains("MATCH,DIRECT"))
    }

    @Test fun pristineLegacyDefaultIsUpgradedOnLoad() {
        val dir = ConfigManager.getProfilesDir(context)
        dir.resolve("${ConfigManager.DEFAULT_PROFILE_ID}.yaml").writeText(
            "proxies: []\nproxy-groups:\n  - name: GLOBAL\n    type: select\n    proxies:\n      - DIRECT\nrules:\n  - MATCH,DIRECT\n"
        )
        ConfigManager.invalidateProfilesCache()
        val loaded = ConfigManager.getOrCreateDefaultProfile(context)
        val content = loaded.file.readText()
        assertTrue(content.contains("GEOSITE,CN,DIRECT"))
        assertTrue(content.contains("MATCH,GLOBAL"))
        assertFalse(content.contains("MATCH,DIRECT"))
    }

    @Test fun legacyDefaultWithNodesKeepsNodesAndGainsSplitRules() {
        val default = ConfigManager.getOrCreateDefaultProfile(context)
        val legacy = """
            proxies:
              - {name: Old, type: socks5, server: example.com, port: 1080}
            proxy-groups:
              - {name: GLOBAL, type: select, proxies: [Old, DIRECT]}
            rules: ["MATCH,DIRECT"]
        """.trimIndent()
        default.file.writeText(legacy)
        ConfigManager.invalidateProfilesCache()
        val loaded = ConfigManager.getOrCreateDefaultProfile(context)
        val content = loaded.file.readText()
        assertTrue(content.contains("Old"))
        assertTrue(content.contains("GEOSITE,CN,DIRECT"))
        assertTrue(content.contains("MATCH,GLOBAL"))
        assertFalse(content.contains("MATCH,DIRECT"))
    }

    @Test fun defaultHasStableIdentityAndKeepsUserContentAndScriptOverrides() {
        val initial = ConfigManager.getOrCreateDefaultProfile(context)
        assertEquals(ConfigManager.DEFAULT_PROFILE_ID, initial.id)
        assertEquals("默认配置", initial.name)
        assertFalse(initial.managed)
        assertFalse(initial.canRefresh)
        val custom = "proxies: []\nmarker: persisted\nrules: [\"MATCH,DIRECT\"]\n"
        assertTrue(ConfigManager.saveProfileContent(context, initial.id, custom))
        ConfigManager.updateProfile(context, initial.copy(scriptEnabled = true, scriptIds = listOf("script-1")))
        val loaded = ConfigManager.getOrCreateDefaultProfile(context)
        assertEquals(initial.file, loaded.file)
        assertEquals(custom, loaded.file.readText())
        assertTrue(loaded.scriptEnabled)
        assertEquals(listOf("script-1"), loaded.scriptIds)
        val prepared = ConfigManager.prepareConfig(context, null).readText()
        assertTrue(prepared.contains("marker: persisted"))
    }

    @Test fun localImportOnlyAppendsToExistingDefaultAndCreatesNoProfileOrFile() = runBlocking {
        val default = ConfigManager.getOrCreateDefaultProfile(context)
        val beforeIds = ConfigManager.getProfiles(context, forceReload = true).map { it.id }.toSet()
        val beforeFiles = ConfigManager.getProfilesDir(context).listFiles().orEmpty().map { it.canonicalPath }.toSet()
        val node = ImportNode(1, name = "Imported local", type = "socks5", data = buildJsonObject {
            put("type", JsonPrimitive("socks5"))
            put("server", JsonPrimitive("local.example"))
            put("port", JsonPrimitive(1080))
        })
        val preview = ImportPreview(listOf(SourceSnapshot(
            source = SubscriptionSource("local", "", url = null),
            text = "local fixture",
            inspection = ImportInspection(format = "yaml", nodes = listOf(node))
        )))

        val imported = ConfigManager.appendImportedNodesToDefault(context, preview, allowPartial = false).getOrThrow()

        assertEquals(ConfigManager.DEFAULT_PROFILE_ID, imported.id)
        assertEquals(default.file, imported.file)
        assertEquals(beforeIds, ConfigManager.getProfiles(context, forceReload = true).map { it.id }.toSet())
        assertEquals(beforeFiles, ConfigManager.getProfilesDir(context).listFiles().orEmpty().map { it.canonicalPath }.toSet())
        val content = imported.file.readText()
        assertTrue(content.contains("name: Imported local"))
        assertTrue(content.contains("server: local.example"))
        assertNull(SubscriptionStore(context).head(imported.id))
    }

    @Test fun remoteAndMixedImportsCannotModifyTheDefaultProfile() = runBlocking {
        val default = ConfigManager.getOrCreateDefaultProfile(context)
        val original = default.file.readBytes()
        val localNode = ImportNode(1, name = "Local", type = "socks5", data = buildJsonObject {
            put("type", JsonPrimitive("socks5"))
            put("server", JsonPrimitive("local.example"))
            put("port", JsonPrimitive(1080))
        })
        val snapshot = SourceSnapshot(
            source = SubscriptionSource("source", "", url = null),
            text = "fixture",
            inspection = ImportInspection(format = "yaml", nodes = listOf(localNode))
        )
        val remoteSnapshot = snapshot.copy(source = SubscriptionSource(
            "remote", "", url = "https://example.invalid/config.yaml"
        ))

        for (sources in listOf(listOf(remoteSnapshot), listOf(snapshot, remoteSnapshot))) {
            val result = ConfigManager.appendImportedNodesToDefault(
                context, ImportPreview(sources), allowPartial = false
            )
            assertTrue(result.isFailure)
            assertArrayEquals(original, default.file.readBytes())
        }
        assertNull(SubscriptionStore(context).head(default.id))
    }

    @Test fun defaultDisplayNamePersistsWithoutChangingIdentityOrYaml() {
        val profile = ConfigManager.getOrCreateDefaultProfile(context)
        val custom = "# Keep this comment and formatting\nproxies: []\nrules: [\"MATCH,DIRECT\"]\n"
        assertTrue(ConfigManager.saveProfileContent(context, profile.id, custom))
        val saved = ConfigManager.getOrCreateDefaultProfile(context)
        val originalBytes = saved.file.readBytes()
        val displayName = "日常节点"

        ConfigManager.updateProfile(context, saved.copy(name = displayName))
        val renamed = ConfigManager.getOrCreateDefaultProfile(context)
        assertEquals(displayName, renamed.name)
        assertEquals("builtin-default", renamed.id)
        assertEquals(saved.file, renamed.file)
        assertArrayEquals(originalBytes, renamed.file.readBytes())

        ConfigManager.deleteProfile(context, renamed.id)
        assertTrue(renamed.file.isFile)
        assertArrayEquals(originalBytes, renamed.file.readBytes())
        assertEquals(renamed, ConfigManager.getOrCreateDefaultProfile(context))
    }

    @Test fun defaultNodeRenamePreservesProfileMetadata() = runBlocking {
        val profile = ConfigManager.getOrCreateDefaultProfile(context)
        assertTrue(ConfigManager.saveProfileContent(context, profile.id, editableConfig))
        val saved = ConfigManager.getOrCreateDefaultProfile(context)
        ConfigManager.updateProfile(context, saved.copy(name = "我的默认配置", scriptEnabled = true, scriptIds = listOf("s")))

        val edited = ConfigManager.renameProfileNode(context, profile.id, "Old", "New").getOrThrow()
        assertEquals(profile.id, edited.id)
        assertEquals(profile.file, edited.file)
        assertEquals("我的默认配置", edited.name)
        assertTrue(edited.scriptEnabled)
        assertEquals(listOf("s"), edited.scriptIds)
        assertTrue(edited.file.readText().contains("MATCH,New"))
        val before = edited.file.readBytes()
        assertTrue(ConfigManager.renameProfileNode(context, profile.id, "New", "DIRECT").isFailure)
        assertArrayEquals(before, edited.file.readBytes())
    }

    @Test fun legacyManagedDefaultIsRecoveredAsLocalWithoutLosingTheCurrentYamlOrName() = runBlocking {
        val original = ConfigManager.getOrCreateDefaultProfile(context)
        ConfigManager.updateProfile(context, original.copy(name = "我的节点", scriptEnabled = true, scriptIds = listOf("s")))
        val store = SubscriptionStore(context)
        val version = store.versionFile(original.id, "legacy-version")
        version.parentFile!!.mkdirs()
        version.writeText(editableConfig)
        val pointer = ConfigManager.getProfilesDir(context).resolve("${original.id}.subscription")
        pointer.writeText(subscriptionJson.encodeToString(SubscriptionHead.serializer(),
            SubscriptionHead("legacy-state", "legacy-version", "旧版本名称", 100L, false)))
        ConfigManager.invalidateProfilesCache()

        val recovered = ConfigManager.getOrCreateDefaultProfile(context)
        assertEquals(original.id, recovered.id)
        assertEquals(original.file, recovered.file)
        assertEquals(editableConfig, recovered.file.readText())
        assertEquals("我的节点", recovered.name)
        assertTrue(recovered.scriptEnabled)
        assertEquals(listOf("s"), recovered.scriptIds)
        assertFalse(recovered.managed)
        assertFalse(recovered.canRefresh)
        assertNull(store.head(recovered.id))
        assertTrue(ConfigManager.readProfileNode(context, recovered.id, "Old").isSuccess)
        assertTrue(ConfigManager.renameProfileNode(context, recovered.id, "Old", "New").isSuccess)
        assertTrue(ConfigManager.deleteProfileNode(context, recovered.id, "New").isSuccess)
    }

    @Test fun defaultCannotAcquireRemoteMetadataThroughProfileUpdates() {
        val original = ConfigManager.getOrCreateDefaultProfile(context)
        ConfigManager.updateProfile(context, original.copy(name = "Local", url = "https://example.invalid/remote", autoUpdate = true))
        val reloaded = ConfigManager.getOrCreateDefaultProfile(context)
        assertEquals("Local", reloaded.name)
        assertNull(reloaded.url)
        assertFalse(reloaded.canRefresh)
        assertFalse(reloaded.autoUpdate)
        assertFalse(reloaded.managed)
    }

    @Test fun nonDefaultLocalProfilesRejectManualNodeActions() = runBlocking {
        val profile = ConfigManager.saveLocalProfile(context, "Local", editableConfig)
        assertFalse(profile.canRefresh)
        assertManualNodeActionsRejected(profile)
    }

    @Test fun legacyRemoteProfilesRejectManualNodeActions() = runBlocking {
        val local = ConfigManager.saveLocalProfile(context, "Remote", editableConfig)
        ConfigManager.updateProfile(context, local.copy(url = "https://example.com/sub"))
        val remote = ConfigManager.getProfiles(context, forceReload = true).single { it.id == local.id }
        assertTrue(remote.canRefresh)
        assertManualNodeActionsRejected(remote)
    }

    private suspend fun assertManualNodeActionsRejected(profile: ProfileItem) {
        val before = profile.file.readBytes()
        fun assertRejected(result: Result<*>, message: String) {
            assertEquals(message, result.exceptionOrNull()?.message)
            assertArrayEquals(before, profile.file.readBytes())
        }

        assertRejected(
            ConfigManager.readProfileNode(context, profile.id, "Old"),
            "请在远程配置中管理此节点；独立节点仅在默认配置中管理"
        )
        assertRejected(
            ConfigManager.renameProfileNode(context, profile.id, "Old", "New"),
            "独立节点仅在默认配置中管理"
        )
        assertRejected(
            ConfigManager.deleteProfileNode(context, profile.id, "Old"),
            "独立节点仅在默认配置中管理"
        )
        assertRejected(
            ConfigManager.replaceProfileNode(context, profile.id, "Old", "\u0000"),
            "独立节点仅在默认配置中管理"
        )
        // Validation rejects NUL before binding the isolated service. The profile guard must win.
        assertRejected(
            ConfigManager.appendNodeToProfile(context, profile.id, "\u0000"),
            "独立节点只能添加到默认配置"
        )
    }

    private val editableConfig = """
        proxies:
          - {name: Old, type: socks5, server: example.com, port: 1080}
        proxy-groups:
          - {name: Select, type: select, proxies: [Old, DIRECT]}
        rules: ["MATCH,Old"]
    """.trimIndent()
}
