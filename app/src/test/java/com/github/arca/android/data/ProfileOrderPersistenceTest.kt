package com.github.arca.android.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The shortcut panel drag and the profiles page drag both end in
 * ConfigManager.saveProfileOrder; this test proves the saved order is what
 * getProfiles returns afterwards, including across a cache clear, so a
 * drag that looks right on screen cannot silently revert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileOrderPersistenceTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        ConfigManager.getProfilesDir(context).listFiles()?.forEach { it.delete() }
        context.getSharedPreferences("profiles_meta", Context.MODE_PRIVATE).edit().clear().commit()
        ConfigManager.invalidateProfilesCache()
    }

    private fun writeProfile(id: String, updated: Long) {
        val file = ConfigManager.getProfilesDir(context).resolve("$id.yaml")
        file.writeText("proxies: []\nrules: []\n")
        file.setLastModified(updated)
    }

    @Test fun `saved order is returned on reload and survives cache clear`() {
        writeProfile("alpha", 3000L)
        writeProfile("beta", 2000L)
        writeProfile("gamma", 1000L)
        // Without a saved order, newest first.
        val initial = ConfigManager.getProfiles(context, forceReload = true)
            .map { it.id }.filter { it != ConfigManager.DEFAULT_PROFILE_ID }
        assertEquals(listOf("alpha", "beta", "gamma"), initial)

        // Simulate a drag that moves gamma to the front.
        ConfigManager.saveProfileOrder(context, listOf("gamma", "alpha", "beta"))
        val reloaded = ConfigManager.getProfiles(context, forceReload = true)
            .map { it.id }.filter { it != ConfigManager.DEFAULT_PROFILE_ID }
        assertEquals(listOf("gamma", "alpha", "beta"), reloaded)

        // A fresh read (cache cleared, as after an app restart) keeps it.
        ConfigManager.invalidateProfilesCache()
        val afterRestart = ConfigManager.getProfiles(context, forceReload = true)
            .map { it.id }.filter { it != ConfigManager.DEFAULT_PROFILE_ID }
        assertEquals(listOf("gamma", "alpha", "beta"), afterRestart)
    }

    @Test fun `unknown ids in saved order are ignored and new profiles append`() {
        writeProfile("alpha", 3000L)
        writeProfile("beta", 2000L)
        ConfigManager.saveProfileOrder(context, listOf("ghost", "beta", "alpha"))
        val ordered = ConfigManager.getProfiles(context, forceReload = true)
            .map { it.id }.filter { it != ConfigManager.DEFAULT_PROFILE_ID }
        assertEquals(listOf("beta", "alpha"), ordered)

        writeProfile("gamma", 4000L)
        val withNew = ConfigManager.getProfiles(context, forceReload = true)
            .map { it.id }.filter { it != ConfigManager.DEFAULT_PROFILE_ID }
        assertEquals(listOf("beta", "alpha", "gamma"), withNew)
    }
}
