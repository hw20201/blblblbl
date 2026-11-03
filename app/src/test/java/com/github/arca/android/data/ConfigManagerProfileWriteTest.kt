package com.github.arca.android.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ConfigManagerProfileWriteTest {

    @Test
    fun `invalid subscription content never replaces the existing profile`() {
        val context = RuntimeEnvironment.getApplication()
        val file = File.createTempFile("arca-profile-", ".yaml", context.cacheDir)
        val previous = """
            proxies:
              - name: working-node
                type: ss
        """.trimIndent()
        file.writeText(previous)

        try {
            assertThrows(IllegalArgumentException::class.java) {
                ConfigManager.writeValidatedProfile(file, "<html>subscription error</html>")
            }
            assertEquals(previous, file.readText())
        } finally {
            file.delete()
            File(file.path + ".bak").delete()
        }
    }

    @Test
    fun `valid replacement is atomically written in normalized form`() {
        val context = RuntimeEnvironment.getApplication()
        val file = File.createTempFile("arca-profile-", ".yaml", context.cacheDir)
        val yaml = "proxies:\n  - name: new-node\n    type: ss"
        val encoded = java.util.Base64.getEncoder().encodeToString(yaml.toByteArray(Charsets.UTF_8))

        try {
            ConfigManager.writeValidatedProfile(file, encoded)
            assertEquals(yaml, file.readText())
        } finally {
            file.delete()
            File(file.path + ".bak").delete()
        }
    }
}
