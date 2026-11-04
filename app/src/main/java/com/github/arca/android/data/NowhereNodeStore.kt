package com.github.arca.android.data

import android.content.Context
import android.util.Base64
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Private, cross-process metadata for profiles backed by the official Vector runtime. */
data class StoredNowhereNode(
    val vectorUrl: String,
    val socksPort: Int,
    val profileSha256: String
)

object NowhereNodeStore {
    private val profileIdPattern = Regex("^[0-9a-fA-F-]{36}$")

    private fun fileFor(context: Context, profileId: String): File {
        require(profileIdPattern.matches(profileId)) { "无效的配置 ID" }
        return File(File(context.filesDir, "nowhere_nodes"), "$profileId.node")
    }

    fun save(context: Context, profileId: String, vectorUrl: String, socksPort: Int, profileYaml: String) {
        require(vectorUrl.startsWith("vector://")) { "仅能保存规范化的 Vector 链接" }
        require(socksPort in 1..65535) { "无效的本地 SOCKS 端口" }
        val target = fileFor(context, profileId)
        val directory = target.parentFile ?: error("无法创建 Nowhere 数据目录")
        check(directory.exists() || directory.mkdirs()) { "无法创建 Nowhere 数据目录" }
        val encodedUrl = Base64.encodeToString(vectorUrl.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val profileHash = sha256(profileYaml.toByteArray(StandardCharsets.UTF_8))
        val temp = File(directory, "$profileId.tmp")
        try {
            temp.writeText("$socksPort\n$encodedUrl\n$profileHash", StandardCharsets.UTF_8)
            temp.setReadable(false, false)
            temp.setReadable(true, true)
            check(temp.renameTo(target)) { "无法保存 Nowhere 节点配置" }
            target.setReadable(false, false)
            target.setReadable(true, true)
        } finally {
            temp.delete()
        }
    }

    /** Reads from disk each time so the :nowhere process never relies on stale SharedPreferences. */
    fun load(context: Context, profileId: String, profileFile: File? = null): StoredNowhereNode? {
        val file = fileFor(context, profileId)
        if (!file.isFile) return null
        val lines = file.readLines(StandardCharsets.UTF_8)
        require(lines.size == 3) { "Nowhere 配置文件格式损坏" }
        val port = lines[0].toIntOrNull()?.takeIf { it in 1..65535 }
            ?: error("Nowhere 本地 SOCKS 端口损坏")
        val url = String(Base64.decode(lines[1], Base64.NO_WRAP), StandardCharsets.UTF_8)
        val hash = lines[2]
        require(hash.matches(Regex("[0-9a-f]{64}"))) { "Nowhere profile 校验信息损坏" }
        require(url.startsWith("vector://")) { "Nowhere runtime URL 无效" }
        if (profileFile != null) {
            require(profileFile.isFile) { "Nowhere profile 文件不存在" }
            val currentHash = sha256(profileFile.readBytes())
            require(currentHash == hash) { "Nowhere profile 内容已变更；为避免流量绕过 Vector，请重新导入节点" }
        }
        return StoredNowhereNode(url, port, hash)
    }

    fun delete(context: Context, profileId: String) {
        runCatching {
            val target = fileFor(context, profileId)
            target.delete()
            File(target.parentFile, "$profileId.tmp").delete()
        }
    }

    fun hasNode(context: Context, profileId: String): Boolean = fileFor(context, profileId).isFile

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
