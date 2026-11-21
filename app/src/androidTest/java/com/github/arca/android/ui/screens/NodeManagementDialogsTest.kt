package com.github.arca.android.ui.screens

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.ConfigManager
import com.github.arca.android.data.ProfileItem
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.yaml.snakeyaml.Yaml

@RunWith(AndroidJUnit4::class)
class NodeManagementDialogsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Context get() = compose.activity
    private var currentName = "Old"
    private var saved: ProfileItem? = null
    private var addedNodeNames: List<String>? = null

    @Before fun reset() {
        ConfigManager.invalidateProfilesCache()
        val profile = ConfigManager.getOrCreateDefaultProfile(context)
        ConfigManager.saveProfileContent(context, profile.id, """
            proxies:
              - {name: Old, type: socks5, server: example.invalid, port: 1080}
            proxy-groups:
              - {name: Select, type: select, proxies: [Old]}
            rules: ["MATCH,Old"]
        """.trimIndent())
        ConfigManager.updateProfile(context, profile.copy(name = "我的节点"))
    }

    private fun show(active: ProfileItem = ConfigManager.getOrCreateDefaultProfile(context)) {
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    val actions = NodeManagementDialogs(active, "Select") { profile, _, old, new, _, added ->
                        saved = profile
                        addedNodeNames = added
                        if (old != null && new != null) currentName = new
                    }
                    Column {
                        TextButton(actions.add, Modifier.testTag("open-add")) { Text("Add") }
                        TextButton({ actions.edit(currentName) }, Modifier.testTag("open-edit")) { Text("Edit") }
                        TextButton({ actions.rename(currentName) }, Modifier.testTag("open-rename")) { Text("Rename") }
                        TextButton({ actions.delete(currentName) }, Modifier.testTag("open-delete")) { Text("Delete") }
                    }
                }
            }
        }
    }

    @Test fun renameAndDeletePersistDefaultNodeAndRepairItsRoutingReferences() {
        show()
        compose.onNodeWithTag("open-rename").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("node-rename-input") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("node-rename-input").performTextReplacement("New")
        compose.onNodeWithTag("node-operation-confirm").performClick()
        compose.waitUntil(5_000) { saved != null }
        compose.runOnIdle {
            assertEquals(ConfigManager.DEFAULT_PROFILE_ID, saved!!.id)
            assertEquals("我的节点", saved!!.name)
            assertEquals(emptyList<String>(), addedNodeNames)
            assertTrue(saved!!.file.readText().contains("MATCH,New"))
        }
        saved = null
        compose.onNodeWithTag("open-delete").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("node-operation-confirm") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("node-operation-confirm").performClick()
        compose.waitUntil(5_000) { saved != null }
        compose.runOnIdle {
            val config = Yaml().load<Map<String, Any>>(saved!!.file.readText())
            assertEquals(emptyList<Any>(), config["proxies"])
            assertEquals(emptyList<String>(), addedNodeNames)
            assertEquals(listOf("MATCH,DIRECT"), config["rules"])
            assertEquals(ConfigManager.DEFAULT_PROFILE_ID, saved!!.id)
        }
    }

    @Test fun editLoadsOnlyOneNodesParametersAndKeepsNameChangeSeparate() {
        val before = ConfigManager.getOrCreateDefaultProfile(context).file.readText()
        show()
        compose.onNodeWithTag("open-edit").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("node-edit-input") and hasText("example.invalid", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("修改连接参数；节点名称保持不变", substring = true).assertExists()
        compose.onNodeWithText("取消").performClick()
        assertNull(saved)
        assertEquals(before, ConfigManager.getOrCreateDefaultProfile(context).file.readText())
    }

    @Test fun addSocks5YamlFromRemoteAppendsResolvedNameToExistingDefaultOnly() {
        val default = ConfigManager.getOrCreateDefaultProfile(context)
        val beforeDefault = Yaml().load<Map<String, Any>>(default.file.readText())
        val remote = ConfigManager.saveLocalProfile(context, "远程配置", """
            proxies:
              - {name: Old, type: socks5, server: remote.example.invalid, port: 1080}
            proxy-groups:
              - {name: Remote select, type: select, proxies: [Old]}
            rules: ["MATCH,Old"]
        """.trimIndent())
        ConfigManager.updateProfile(context, remote.copy(url = "https://example.invalid/config.yaml"))
        val activeRemote = ConfigManager.getProfiles(context, forceReload = true).first { it.id == remote.id }
        val remoteBytes = activeRemote.file.readBytes()
        val profilePathsBefore = ConfigManager.getProfiles(context, forceReload = true)
            .associate { it.id to it.file.absolutePath }
        val yamlFilesBefore = ConfigManager.getProfilesDir(context).listFiles().orEmpty()
            .filter { it.extension == "yaml" }.map { it.absolutePath }.toSet()
        show(activeRemote)
        compose.onNodeWithTag("open-add").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("node-add-input") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("独立节点保存在「我的节点」", substring = true).assertExists()
        compose.onNodeWithTag("node-add-input").performTextReplacement("""
            proxies:
              - name: Old
                type: socks5
                server: 203.0.113.10
                port: 1081
                username: fixture-user
                password: fixture-password
        """.trimIndent())
        compose.onNodeWithTag("node-operation-confirm").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { saved != null }
        compose.onNodeWithTag("node-add-input").assertDoesNotExist()
        compose.runOnIdle {
            val updated = saved!!
            assertEquals(ConfigManager.DEFAULT_PROFILE_ID, updated.id)
            assertEquals(default.id, updated.id)
            assertEquals(default.file.absolutePath, updated.file.absolutePath)
            assertEquals("我的节点", updated.name)
            assertNull(updated.url)
            assertFalse(updated.managed)
            assertFalse(updated.canRefresh)
            assertEquals(listOf("Old-2"), addedNodeNames)
            val after = Yaml().load<Map<String, Any>>(updated.file.readText())
            val beforeNodes = beforeDefault["proxies"] as List<*>
            val afterNodes = after["proxies"] as List<*>
            assertEquals(2, afterNodes.size)
            assertEquals("the old connection must not be replaced by a colliding add", beforeNodes.single(), afterNodes.first())
            val added = afterNodes.last() as Map<*, *>
            assertEquals("Old-2", added["name"])
            assertEquals("socks5", added["type"])
            assertEquals("203.0.113.10", added["server"])
            assertEquals(1081, (added["port"] as Number).toInt())
            val group = (after["proxy-groups"] as List<*>).single() as Map<*, *>
            assertEquals("Select", group["name"])
            assertEquals(listOf("Old", "Old-2"), group["proxies"])
            assertEquals(beforeDefault["rules"], after["rules"])
            assertArrayEquals(remoteBytes, activeRemote.file.readBytes())
            val profilesAfter = ConfigManager.getProfiles(context, forceReload = true)
            assertEquals(profilePathsBefore, profilesAfter.associate { it.id to it.file.absolutePath })
            assertEquals(1, profilesAfter.count { it.id == ConfigManager.DEFAULT_PROFILE_ID })
            assertEquals(activeRemote.url, profilesAfter.first { it.id == remote.id }.url)
            assertEquals(yamlFilesBefore, ConfigManager.getProfilesDir(context).listFiles().orEmpty()
                .filter { it.extension == "yaml" }.map { it.absolutePath }.toSet())
        }
    }

    @Test fun cancelAddWithValidYamlDoesNotWriteOrPublishAddedNodes() {
        val default = ConfigManager.getOrCreateDefaultProfile(context)
        val defaultBytes = default.file.readBytes()
        val profilePathsBefore = ConfigManager.getProfiles(context, forceReload = true)
            .associate { it.id to it.file.absolutePath }
        val yamlFilesBefore = ConfigManager.getProfilesDir(context).listFiles().orEmpty()
            .filter { it.extension == "yaml" }.map { it.absolutePath }.toSet()
        show(default)
        compose.onNodeWithTag("open-add").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("node-add-input") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("node-add-input").performTextReplacement("""
            proxies:
              - {name: Cancelled, type: socks5, server: 203.0.113.11, port: 1081}
        """.trimIndent())
        compose.onNodeWithTag("node-operation-confirm").assertIsEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("node-add-input").assertDoesNotExist()
        compose.onNodeWithTag("node-operation-confirm").assertDoesNotExist()
        compose.runOnIdle {
            assertNull(saved)
            assertNull(addedNodeNames)
            assertEquals("Old", currentName)
            assertArrayEquals(defaultBytes, ConfigManager.getOrCreateDefaultProfile(context).file.readBytes())
            assertEquals(profilePathsBefore, ConfigManager.getProfiles(context, forceReload = true)
                .associate { it.id to it.file.absolutePath })
            assertEquals(yamlFilesBefore, ConfigManager.getProfilesDir(context).listFiles().orEmpty()
                .filter { it.extension == "yaml" }.map { it.absolutePath }.toSet())
        }
    }

    @Test fun remoteNodesCannotOpenActionsAndAddShowsRenamedDefaultDestination() {
        val remote = ConfigManager.saveLocalProfile(context, "远程配置", "proxies: []")
        ConfigManager.updateProfile(context, remote.copy(url = "https://example.invalid/config.yaml"))
        val before = remote.file.readText()
        show(remote)
        for (action in listOf("edit", "rename", "delete")) {
            compose.onNodeWithTag("open-$action").performClick()
            compose.onNodeWithTag("node-operation-confirm").assertDoesNotExist()
        }
        compose.onNodeWithTag("open-add").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("独立节点保存在「我的节点」", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("添加后会显示此配置中的节点列表。").assertExists()
        compose.onNodeWithTag("node-add-input").assertIsDisplayed()
        assertEquals(before, remote.file.readText())
        assertNull(saved)
    }
}
