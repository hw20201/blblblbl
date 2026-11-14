package com.github.arca.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ScamalyticsCredentials(
    val username: String,
    val apiKey: String
) {
    override fun toString(): String = "ScamalyticsCredentials(username=$username, apiKey=[REDACTED])"
}

/** Stores Scamalytics credentials as AES-GCM ciphertext with a non-exportable Android Keystore key. */
class ScamalyticsCredentialStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun load(): ScamalyticsCredentials? {
        val encoded = preferences.getString(PAYLOAD_KEY, null) ?: return null
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size <= GCM_IV_BYTES) return null
            val iv = payload.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = payload.copyOfRange(GCM_IV_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getKey(createIfMissing = false), GCMParameterSpec(GCM_TAG_BITS, iv))
            val json = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
            val username = json.optString(USERNAME_FIELD).trim()
            val apiKey = json.optString(API_KEY_FIELD)
            if (username.isBlank() || apiKey.isBlank() || apiKey == "null") null
            else ScamalyticsCredentials(username, apiKey)
        } catch (_: Exception) {
            // Corrupt/invalidated ciphertext is treated as not configured; never log its contents.
            null
        }
    }

    @Synchronized
    fun save(credentials: ScamalyticsCredentials) {
        val username = credentials.username.trim()
        require(username.isNotBlank() && username.length <= MAX_USERNAME_LENGTH) { "Invalid Scamalytics username" }
        require(username.none { it.isISOControl() || it == '/' || it == '\\' }) { "Invalid Scamalytics username" }
        require(credentials.apiKey.isNotBlank()) { "Scamalytics API key is required" }

        val json = JSONObject()
            .put(USERNAME_FIELD, username)
            .put(API_KEY_FIELD, credentials.apiKey)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey(createIfMissing = true))
        val iv = cipher.iv ?: throw IllegalStateException("Credential encryption failed")
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
    fun clear() {
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
        const val MAX_USERNAME_LENGTH = 128
    }
}
