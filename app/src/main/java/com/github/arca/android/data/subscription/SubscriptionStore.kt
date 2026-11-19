package com.github.arca.android.data.subscription

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only this small manifest is read on the UI thread. It points at fully written immutable files. */
@Serializable
internal data class SubscriptionHead(
    val stateId: String,
    val versionId: String,
    val name: String,
    val updatedAt: Long,
    val hasRemoteSources: Boolean,
    val userInfo: String? = null
)

/** URLs, source material and device tokens are not stored in profile preferences. */
internal class SubscriptionStore(private val context: Context) {
    fun head(id: String): SubscriptionHead? {
        val file = headFile(id)
        if (!file.isFile && !File("${file.path}.bak").isFile) return null
        return AtomicFile(file).openRead().use {
            subscriptionJson.decodeFromString<SubscriptionHead>(readBounded(it, 4096))
        }
    }

    fun read(id: String): ManagedSubscription? {
        val pointer = head(id) ?: return null
        return try {
            val file = stateFile(id, pointer.stateId)
            require(file.length() in 29..MAX_STATE_BYTES.toLong())
            val bytes = file.readBytes()
            require(bytes[0] == 1.toByte())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            cipher.updateAAD(id.toByteArray())
            val plain = cipher.doFinal(bytes, 13, bytes.size - 13).toString(Charsets.UTF_8)
            subscriptionJson.decodeFromString<ManagedSubscription>(plain).also {
                require(it.schema == 1 && it.profileId == id && it.current.versionId == pointer.versionId)
            }
        } catch (_: Exception) {
            error("无法读取订阅版本，当前配置仍保留")
        }
    }

    fun write(state: ManagedSubscription): File {
        val id = state.profileId
        val version = state.current
        val target = versionFile(id, version.versionId)
        val stateId = UUID.randomUUID().toString()
        val snapshot = stateFile(id, stateId)
        val old = head(id)
        val bytes = subscriptionJson.encodeToString(ManagedSubscription.serializer(), state).toByteArray(Charsets.UTF_8)
        require(bytes.size < MAX_STATE_BYTES - 64) { "来源快照超过存储上限，请减少来源" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        cipher.updateAAD(id.toByteArray())
        val payload = byteArrayOf(1) + cipher.iv + cipher.doFinal(bytes)
        val userInfo = version.sources.singleOrNull()?.userInfo?.let {
            com.github.arca.android.data.ConfigManager.parseSubscriptionUserInfo(it)
        }?.let { "upload=${it.upload}; download=${it.download}; total=${it.total}; expire=${it.expire}" }
        val head = SubscriptionHead(stateId, version.versionId, version.name, version.generatedAt,
            version.sources.any { it.source.url != null }, userInfo)
        try {
            if (!target.exists()) atomicWrite(target, version.content.toByteArray(Charsets.UTF_8))
            atomicWrite(snapshot, payload)
            atomicWrite(headFile(id), subscriptionJson.encodeToString(SubscriptionHead.serializer(), head).toByteArray())
            check(head(id)?.stateId == stateId) { "配置版本未提交" }
        } catch (failure: Exception) {
            if (runCatching { head(id)?.stateId }.getOrNull() != stateId) snapshot.delete()
            throw failure
        }
        old?.let { stateFile(id, it.stateId).delete() }
        // Keep the former runtime file through the next publication in case it is still being read.
        val keep = setOfNotNull(head.versionId, state.previous?.versionId, old?.versionId)
        target.parentFile?.listFiles()?.filter { it.extension == "yaml" && it.nameWithoutExtension !in keep }
            ?.forEach { it.delete() }
        return target
    }

    fun delete(id: String) {
        AtomicFile(headFile(id)).delete()
        stateDir(id).listFiles()?.forEach { if (it.isFile) it.delete() }
        versionDir(id).listFiles()?.forEach { if (it.isFile) it.delete() }
        stateDir(id).delete()
        versionDir(id).delete()
    }

    fun versionFile(id: String, versionId: String): File = versionDir(id).resolve("${checked(versionId)}.yaml")

    private fun versionDir(id: String) = File(context.filesDir, "subscription-versions/${checked(id)}")
    private fun stateDir(id: String) = File(context.noBackupFilesDir, "subscriptions/${checked(id)}")
    private fun stateFile(id: String, stateId: String) = stateDir(id).resolve("${checked(stateId)}.bin")
    private fun headFile(id: String) = File(context.filesDir, "profiles/${checked(id)}.subscription")
    private fun checked(id: String): String = id.also { require(ID.matches(it)) { "配置标识无效" } }

    private fun key(create: Boolean): SecretKey = synchronized(KEY_LOCK) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return@synchronized it }
        check(create) { "订阅加密密钥不可用" }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    private companion object {
        val ID = Regex("[A-Za-z0-9_-]{1,100}")
        val KEY_LOCK = Any()
        const val KEY_ALIAS = "arca.subscription.snapshots.v1"
        const val MAX_STATE_BYTES = 48 * 1024 * 1024
    }
}

internal fun atomicWrite(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val atomic = AtomicFile(file)
    val stream = atomic.startWrite()
    try {
        stream.write(bytes)
        atomic.finishWrite(stream)
    } catch (failure: Throwable) {
        atomic.failWrite(stream)
        throw failure
    }
}
