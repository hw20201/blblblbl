package com.github.arca.android.data

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ProfileParserProviderCacheTest {

    @Test
    fun `loads provider nodes from the native filesDir providers cache`() {
        val context = RuntimeEnvironment.getApplication()
        val url = "https://example.invalid/providers/${System.nanoTime()}.yaml"
        val hash = MessageDigest.getInstance("MD5").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val providerFile = context.filesDir.resolve("providers/proxies/$hash")
        val profileFile = File(context.cacheDir, "provider-profile-${System.nanoTime()}.yaml")
        providerFile.parentFile?.mkdirs()
        providerFile.writeText(
            """
                proxies:
                  - name: Cached-HK
                    type: ss
                  - name: Cached-JP
                    type: vmess
            """.trimIndent()
        )
        profileFile.writeText(
            """
                proxies: []
                proxy-providers:
                  remote:
                    type: http
                    url: $url
                proxy-groups:
                  - name: PROXY
                    type: select
                    use: [remote]
            """.trimIndent()
        )

        try {
            ProfileParser.clearCache()
            val parsed = ProfileParser.parse(profileFile, context = context)
            assertEquals(2, parsed.totalNodes)
            assertTrue(parsed.groups["PROXY"]!!.proxies.map { it.name }.containsAll(listOf("Cached-HK", "Cached-JP")))
        } finally {
            ProfileParser.clearCache()
            providerFile.delete()
            providerFile.parentFile?.delete()
            providerFile.parentFile?.parentFile?.delete()
            profileFile.delete()
        }
    }
}
