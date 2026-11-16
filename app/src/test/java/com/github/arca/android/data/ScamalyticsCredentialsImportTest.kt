package com.github.arca.android.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScamalyticsCredentialsImportTest {
    @Test fun legacyCredentialsWithoutRegionDefaultToApi11() {
        val result = parseScamalyticsCredentials(JSONObject("""{"username":"fixture", "api_key":"fake-key"}"""))
        assertEquals(ScamalyticsEndpoint.US, result.endpoint)
        assertEquals("fake-key", result.apiKey)
    }

    @Test fun importAcceptsOnlyEnumeratedNodesAndHttpsApiRoots() {
        listOf("api11", "https://api11.scamalytics.com/v3/").forEach {
            assertEquals(ScamalyticsEndpoint.US, parseScamalyticsCredentials(fixture(it)).endpoint)
        }
        listOf("api12", "https://api12.scamalytics.com/").forEach {
            assertEquals(ScamalyticsEndpoint.EU, parseScamalyticsCredentials(fixture(it)).endpoint)
        }
        listOf("api13", "http://api11.scamalytics.com/v3/", "https://evil.example/v3/",
            "https://api11.scamalytics.com.evil.example/", "https://api11.scamalytics.com@evil.example/",
            "https://user@api11.scamalytics.com/", "https://api11.scamalytics.com:8443/",
            "https://api11.scamalytics.com/v3/other", "https://api11.scamalytics.com/?key=other",
            "https://api11.scamalytics.com/#fragment").forEach { endpoint ->
            assertThrows(IllegalArgumentException::class.java) { parseScamalyticsCredentials(fixture(endpoint)) }
        }
    }

    @Test fun importEnforcesTypesAndSixteenKiBLimit() {
        val valid = fixture("api11").toString()
        assertEquals("fixture", readScamalyticsCredentials(ByteArrayInputStream(valid.toByteArray())).username)
        val exact = valid.padEnd(SCAMALYTICS_IMPORT_MAX_BYTES, ' ').toByteArray()
        assertEquals("fixture", readScamalyticsCredentials(ByteArrayInputStream(exact)).username)
        assertThrows(IllegalArgumentException::class.java) {
            readScamalyticsCredentials(ByteArrayInputStream(exact + byteArrayOf(32)))
        }
        listOf("""{"username":1,"api_key":"fake"}""", """{"username":"ok","api_key":null}""",
            """{"username":"ok","api_key":"fake","endpoint":null}""").forEach {
            assertThrows(IllegalArgumentException::class.java) { parseScamalyticsCredentials(JSONObject(it)) }
        }
    }

    @Test fun invalidPathSegmentsAndControlCharactersAreRejected() {
        listOf("..", ".", "account/path", "account\\path", "account\nname").forEach {
            assertThrows(IllegalArgumentException::class.java) { ScamalyticsCredentials(it, "fake").validated() }
        }
        assertThrows(IllegalArgumentException::class.java) { ScamalyticsCredentials("fixture", "fake\nkey").validated() }
    }

    private fun fixture(endpoint: String) = JSONObject().put("username", "fixture")
        .put("api_key", "fake-key").put("endpoint", endpoint)
}
