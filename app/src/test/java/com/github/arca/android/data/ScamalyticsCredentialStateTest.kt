package com.github.arca.android.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

/** Tri-state decision (Absent / Present / Unreadable) with injected decode and decrypt; no Keystore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScamalyticsCredentialStateTest {
    private val ivBytes = 12
    private val xor: (ByteArray, ByteArray) -> ByteArray = { iv, data -> ByteArray(data.size) { (data[it].toInt() xor iv[0].toInt()).toByte() } }
    private val decode: (String) -> ByteArray = { Base64.getDecoder().decode(it) }

    private fun payload(plain: String): String {
        val iv = ByteArray(ivBytes) { 7 }
        return Base64.getEncoder().encodeToString(iv + xor(iv, plain.toByteArray()))
    }

    private fun state(encoded: String?, decrypt: (ByteArray, ByteArray) -> ByteArray = xor) =
        credentialStateFromPayload(encoded, ivBytes, decode, decrypt)

    @Test fun noPayloadIsAbsent() {
        assertSame(CredentialLoadState.Absent, state(null))
    }

    @Test fun validPayloadIsPresentWithEndpoint() {
        val result = state(payload("""{"username":"fixture","api_key":"fake-key","endpoint":"api12"}"""))
        assertTrue(result is CredentialLoadState.Present)
        val credentials = (result as CredentialLoadState.Present).credentials
        assertEquals("fixture", credentials.username)
        assertEquals("fake-key", credentials.apiKey)
        assertEquals(ScamalyticsEndpoint.EU, credentials.endpoint)
    }

    @Test fun legacyPayloadWithoutEndpointDefaultsToApi11() {
        val result = state(payload("""{"username":"fixture","api_key":"fake-key"}""")) as CredentialLoadState.Present
        assertEquals(ScamalyticsEndpoint.US, result.credentials.endpoint)
    }

    @Test fun payloadThatExistsButCannotBeReadIsUnreadableNeverAbsent() {
        listOf(
            "" to xor,                                              // empty string stored
            "!!!not-base64!!!" to xor,                              // undecodable
            Base64.getEncoder().encodeToString(ByteArray(ivBytes)) to xor,   // IV only, no ciphertext
            Base64.getEncoder().encodeToString(ByteArray(5)) to xor,         // shorter than the IV
            payload("""{"username":"u","api_key":"k"}""") to { _: ByteArray, _: ByteArray -> throw java.security.GeneralSecurityException("tag mismatch") },
            payload("""{"username":"u","api_key":"k"}""") to { _: ByteArray, _: ByteArray -> throw IllegalStateException("key invalidated") },
            payload("not json") to xor,
            payload("""{"username":"u"}""") to xor,
            payload("""{"username":"u","api_key":"k","endpoint":"https://evil.example/"}""") to xor,
            payload("""{"username":"..","api_key":"k"}""") to xor
        ).forEachIndexed { index, (encoded, decrypt) ->
            assertSame("case $index", CredentialLoadState.Unreadable, state(encoded, decrypt))
        }
    }

    @Test fun stringFormsNeverLeakTheKey() {
        val result = state(payload("""{"username":"account-name","api_key":"not-for-logs"}"""))
        assertFalse(result.toString().contains("not-for-logs"))
        assertFalse(result.toString().contains("account-name"))
        assertTrue(result.toString().contains("REDACTED"))
    }

    @Test fun interfaceDefaultMapsLoadToPresentOrAbsent() {
        val credentials = ScamalyticsCredentials("fixture", "fake-key")
        fun storage(value: ScamalyticsCredentials?) = object : ScamalyticsCredentialsStorage {
            override fun load() = value
            override fun save(credentials: ScamalyticsCredentials) = Unit
            override fun clear() = Unit
        }
        assertEquals(CredentialLoadState.Present(credentials), storage(credentials).loadState())
        assertSame(CredentialLoadState.Absent, storage(null).loadState())
    }
}
