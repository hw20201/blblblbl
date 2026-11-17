package com.github.arca.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class ScamalyticsEndpoint(val id: String, val host: String, val label: String) {
    US("api11", "api11.scamalytics.com", "api11 · US"),
    EU("api12", "api12.scamalytics.com", "api12 · EU");

    companion object {
        /** URL imports accept only an HTTPS API root, never an arbitrary credential destination. */
        fun parse(value: String): ScamalyticsEndpoint? {
            entries.firstOrNull { it.id == value }?.let { return it }
            val url = value.toHttpUrlOrNull() ?: return null
            if (url.scheme != "https" || url.port != 443 || url.username.isNotEmpty() ||
                url.password.isNotEmpty() || url.query != null || url.fragment != null ||
                url.encodedPath !in listOf("/", "/v3", "/v3/")) return null
            return entries.firstOrNull { it.host == url.host }
        }
    }
}

data class ScamalyticsCredentials(
    val username: String,
    val apiKey: String,
    val endpoint: ScamalyticsEndpoint = ScamalyticsEndpoint.US
) {
    override fun toString(): String = "ScamalyticsCredentials(username=[REDACTED], apiKey=[REDACTED], endpoint=${endpoint.id})"

    fun validated(): ScamalyticsCredentials {
        val name = username.trim()
        require(name.isNotBlank() && name.length <= 128 && name != "." && name != ".." &&
            name.none { it.isISOControl() || it == '/' || it == '\\' }) { "Invalid Scamalytics username" }
        require(apiKey.isNotBlank() && apiKey != "null" && apiKey.length <= 4096 &&
            apiKey.none { it.isISOControl() }) { "Invalid Scamalytics API key" }
        return copy(username = name)
    }
}

/**
 * Outcome of reading stored credentials. [Unreadable] (a payload exists but cannot be decrypted or
 * parsed) is deliberately distinct from [Absent]: it must never be treated as "not configured".
 */
sealed interface CredentialLoadState {
    object Absent : CredentialLoadState {
        override fun toString(): String = "CredentialLoadState.Absent"
    }

    data class Present(val credentials: ScamalyticsCredentials) : CredentialLoadState {
        // ScamalyticsCredentials.toString() is already redacted; keep it that way here.
        override fun toString(): String = "CredentialLoadState.Present(credentials=$credentials)"
    }

    object Unreadable : CredentialLoadState {
        override fun toString(): String = "CredentialLoadState.Unreadable"
    }
}

/** Synchronous operations must be called on Dispatchers.IO. */
interface ScamalyticsCredentialsStorage {
    fun load(): ScamalyticsCredentials?

    /** Like [load] but a stored-yet-unreadable payload is [CredentialLoadState.Unreadable], not absent. */
    fun loadState(): CredentialLoadState = load()?.let { CredentialLoadState.Present(it) } ?: CredentialLoadState.Absent

    fun save(credentials: ScamalyticsCredentials)
    fun clear()
}

internal const val SCAMALYTICS_IMPORT_MAX_BYTES = 16 * 1024

internal fun readScamalyticsCredentials(input: InputStream): ScamalyticsCredentials {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(1024)
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size, SCAMALYTICS_IMPORT_MAX_BYTES + 1 - output.size()))
        if (count == -1) break
        output.write(buffer, 0, count)
        require(output.size() <= SCAMALYTICS_IMPORT_MAX_BYTES) { "Credential file exceeds 16 KiB" }
    }
    return parseScamalyticsCredentials(JSONObject(output.toString(Charsets.UTF_8.name())))
}

internal fun parseScamalyticsCredentials(json: JSONObject): ScamalyticsCredentials {
    val username = json.opt("username") as? String ?: throw IllegalArgumentException("Missing username")
    val apiKey = json.opt("api_key") as? String ?: throw IllegalArgumentException("Missing API key")
    val endpoint = if (!json.has("endpoint")) ScamalyticsEndpoint.US else {
        val value = json.opt("endpoint") as? String ?: throw IllegalArgumentException("Invalid endpoint")
        ScamalyticsEndpoint.parse(value) ?: throw IllegalArgumentException("Invalid endpoint")
    }
    return ScamalyticsCredentials(username, apiKey, endpoint).validated()
}

/**
 * Pure payload -> state decision, with the Base64 decoder and AES-GCM decryptor injected so it can be
 * tested on a plain JVM. No payload is [CredentialLoadState.Absent]; a payload that fails to decode,
 * decrypt or validate is [CredentialLoadState.Unreadable]. Failure details are never kept or logged.
 */
internal fun credentialStateFromPayload(
    encoded: String?,
    ivBytes: Int,
    decode: (String) -> ByteArray,
    decrypt: (iv: ByteArray, ciphertext: ByteArray) -> ByteArray
): CredentialLoadState {
    if (encoded == null) return CredentialLoadState.Absent
    return try {
        val payload = decode(encoded)
        if (payload.size <= ivBytes) return CredentialLoadState.Unreadable
        val plain = decrypt(payload.copyOfRange(0, ivBytes), payload.copyOfRange(ivBytes, payload.size))
        // Existing v1 ciphertext has no endpoint; those accounts default to api11.
        CredentialLoadState.Present(parseScamalyticsCredentials(JSONObject(String(plain, Charsets.UTF_8))))
    } catch (_: Exception) {
        CredentialLoadState.Unreadable
    }
}

/** Stores Scamalytics credentials as AES-GCM ciphertext with a non-exportable Android Keystore key. */
class ScamalyticsCredentialStore(context: Context) : ScamalyticsCredentialsStorage {
    private val applicationContext = context.applicationContext
    private val preferences by lazy {
        applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    override fun load(): ScamalyticsCredentials? = (loadState() as? CredentialLoadState.Present)?.credentials

    @Synchronized
    override fun loadState(): CredentialLoadState = credentialStateFromPayload(
        encoded = preferences.getString(PAYLOAD_KEY, null),
        ivBytes = GCM_IV_BYTES,
        decode = { Base64.decode(it, Base64.NO_WRAP) },
        decrypt = { iv, ciphertext ->
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getKey(createIfMissing = false), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        }
    )

    @Synchronized
    override fun save(credentials: ScamalyticsCredentials) {
        val validated = credentials.validated()
        val json = JSONObject()
            .put(USERNAME_FIELD, validated.username)
            .put(API_KEY_FIELD, validated.apiKey)
            .put("endpoint", validated.endpoint.id)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey(createIfMissing = true))
        val iv = cipher.iv ?: error("Credential encryption failed")
        val ciphertext = cipher.doFinal(json)
        val envelope = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, envelope, 0, iv.size)
        System.arraycopy(ciphertext, 0, envelope, iv.size, ciphertext.size)
        val encoded = Base64.encodeToString(envelope, Base64.NO_WRAP)
        check(preferences.edit().putString(PAYLOAD_KEY, encoded).commit()) {
            "Secure credential save failed"
        }
    }

    @Synchronized
    override fun clear() {
        check(preferences.edit().remove(PAYLOAD_KEY).commit()) { "Secure credential deletion failed" }
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getKey(createIfMissing: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(createIfMissing) { "Credential encryption key is unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "scamalytics_credentials_ciphertext"
        const val PAYLOAD_KEY = "encrypted_payload_v1"
        const val USERNAME_FIELD = "username"
        const val API_KEY_FIELD = "api_key"
        const val KEY_ALIAS = "arca.scamalytics.credentials.aesgcm.v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
