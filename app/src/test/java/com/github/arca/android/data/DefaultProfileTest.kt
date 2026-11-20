package com.github.arca.android.data

import android.content.Context
import kotlinx.coroutines.runBlocking
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

    @Test fun renameUsesAtomicBackendAndPreservesSelectedProfileMetadata() = runBlocking {
        val profile = ConfigManager.saveLocalProfile(context, "Local", """
            proxies:
              - {name: Old, type: socks5, server: example.com, port: 1080}
            proxy-groups:
              - {name: Select, type: select, proxies: [Old, DIRECT]}
            rules: ["MATCH,Old"]
        """.trimIndent())
        ConfigManager.updateProfile(context, profile.copy(scriptEnabled = true, scriptIds = listOf("s")))
        val edited = ConfigManager.renameProfileNode(context, profile.id, "Old", "New").getOrThrow()
        assertEquals(profile.id, edited.id)
        assertEquals(profile.name, edited.name)
        assertTrue(edited.scriptEnabled)
        assertEquals(listOf("s"), edited.scriptIds)
        assertTrue(edited.file.readText().contains("MATCH,New"))
        val before = edited.file.readText()
        assertTrue(ConfigManager.renameProfileNode(context, profile.id, "New", "DIRECT").isFailure)
        assertEquals(before, edited.file.readText())
    }

    @Test fun legacyRemoteProfileRejectsEditsThatRefreshWouldLose() = runBlocking {
        val profile = ConfigManager.saveLocalProfile(context, "Remote", "proxies: [{name: Old, type: socks5, server: example.com, port: 1080}]")
        ConfigManager.updateProfile(context, profile.copy(url = "https://example.com/sub"))
        val before = profile.file.readText()
        assertTrue(ConfigManager.renameProfileNode(context, profile.id, "Old", "New").isFailure)
        assertEquals(before, profile.file.readText())
    }
}
