package com.github.arca.android.data

import android.content.Context
import android.util.Log
import com.github.arca.android.data.subscription.SubscriptionManager
import com.github.arca.android.data.subscription.SubscriptionStore
import com.github.arca.android.data.subscription.SourceDraft
import com.github.arca.android.data.subscription.readBounded
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ProfileItem(
    val id: String,
    val name: String,
    val url: String?,
    val file: File,
    val lastUpdated: Long = System.currentTimeMillis(),
    val scriptEnabled: Boolean = false,
    val scriptIds: List<String> = emptyList(),
    val autoUpdate: Boolean = false,
    val autoUpdateInterval: Int = 1440,
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val expireTimestamp: Long = 0L,
    val managed: Boolean = false,
    val canRefresh: Boolean = url != null
)

data class SubscriptionUserInfo(
    val upload: Long = 0L,
    val download: Long = 0L,
    val total: Long = 0L,
    val expire: Long = 0L
)

object ConfigManager {
    private const val TAG = "ConfigManager"
    const val DEFAULT_PROFILE_ID = "builtin-default"

    /** Top-level config keys that only the injected runtime header may set. */
    private val PROTECTED_TOP_LEVEL_KEYS = setOf(
        "mode", "log-level", "ipv6", "unified-delay", "mixed-port", "port", "socks-port",
        "redir-port", "tproxy-port", "allow-lan", "bind-address",
        "external-controller", "external-controller-cors", "secret",
        "authentication", "skip-auth-prefixes", "lan-allowed-ips", "lan-disallowed-ips",
        "external-ui", "external-ui-name", "external-ui-url",
        "tun", "dns", "interface-name", "routing-mark",
        "geodata-mode", "geo-auto-update", "geox-url", "tcp-concurrent"
    )

    /** Bundled core release; used when the native library has not been loaded yet. */
    private const val BUNDLED_CORE_VERSION = "v1.19.33"

    @Volatile
    private var cachedUserAgent: String? = null

    /**
     * Real core version in the subscription UA. Panels choose the delivered format by these tokens
     * (`clash.meta` / `mihomo`), so advertising the old 1.18 build could get Meta-only fields dropped.
     */
    internal fun subscriptionUserAgent(): String = cachedUserAgent ?: run {
        val raw = runCatching { com.github.arca.android.core.ClashCore.getCoreVersion() }.getOrNull().orEmpty()
        val version = coreReleaseFromBuildString(raw)
        val plain = version.removePrefix("v")
        "clash.meta/$plain mihomo/$plain ClashMeta/$plain Clash".also {
            // Only cache a value read from the loaded core; retry later otherwise.
            if (version != BUNDLED_CORE_VERSION || raw.startsWith("v")) cachedUserAgent = it
        }
    }

    /** "v1.19.33_3f27ef3_native-mips2" -> "v1.19.33"; anything unparsable -> bundled version. */
    internal fun coreReleaseFromBuildString(raw: String): String =
        Regex("""^v?(\d+\.\d+\.\d+)""").find(raw.trim())?.groupValues?.get(1)?.let { "v$it" }
            ?: BUNDLED_CORE_VERSION

    fun parseSubscriptionUserInfo(header: String?): SubscriptionUserInfo? {
        if (header.isNullOrBlank()) return null
        var upload = 0L
        var download = 0L
        var total = 0L
        var expire = 0L
        for (part in header.split(";")) {
            val kv = part.trim().split("=")
            if (kv.size == 2) {
                val k = kv[0].trim().lowercase()
                val v = kv[1].trim().toLongOrNull() ?: 0L
                when (k) {
                    "upload" -> upload = v
                    "download" -> download = v
                    "total" -> total = v
                    "expire" -> expire = v
                }
            }
        }
        return if (total > 0L || expire > 0L) SubscriptionUserInfo(upload, download, total, expire) else null
    }

    internal fun extractProfileName(name: String?, response: okhttp3.Response?, body: String, url: String): String {
        if (!name.isNullOrBlank()) return name.trim()

        val uri = runCatching { java.net.URI(url) }.getOrNull()
        fun decode(value: String): String = runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

        // 1. Check URL Fragment (#Name), decoding only once.
        uri?.rawFragment?.let { fragment ->
            val decoded = decode(fragment).trim()
            if (decoded.isNotBlank()) return decoded
        }

        // 2. Split the raw query before decoding; escaped separators are part of the value.
        uri?.rawQuery?.split('&')?.forEach { param ->
            val pair = param.split('=', limit = 2)
            if (pair.size == 2) {
                val key = decode(pair[0])
                if (key.equals("name", ignoreCase = true) || key.equals("title", ignoreCase = true)) {
                    val decoded = decode(pair[1]).trim()
                    if (decoded.isNotBlank()) return decoded
                }
            }
        }

        // 3. Check response header: profile-title
        response?.header("profile-title")?.let { title ->
            val decoded = decode(title).trim()
            if (decoded.isNotBlank()) return decoded
        }

        // 4. Parse complete parameters so trailing attributes and quoted semicolons aren't mistaken for names.
        response?.header("Content-Disposition")?.let { disposition ->
            val parameters = Regex("""(?:^|;)\s*([^=;\s]+)\s*=\s*(?:"((?:\\.|[^"\\])*)"|([^";]*))""")
                .findAll(disposition).associate { match ->
                    val quoted = match.groups[2]?.value
                    match.groupValues[1].lowercase() to
                        (quoted?.replace(Regex("""\\(.)""")) { it.groupValues[1] } ?: match.groupValues[3].trim())
                }
            fun filename(value: String): String {
                val trimmed = value.trim()
                return when {
                    trimmed.endsWith(".yaml", ignoreCase = true) -> trimmed.dropLast(5).trimEnd()
                    trimmed.endsWith(".yml", ignoreCase = true) -> trimmed.dropLast(4).trimEnd()
                    else -> trimmed
                }
            }
            parameters["filename*"]?.split('\'', limit = 3)?.let { parts ->
                if (parts.size == 3 && parts[0].equals("UTF-8", ignoreCase = true)) {
                    // RFC 5987 uses percent encoding, not form encoding: a literal plus stays a plus.
                    val decoded = runCatching { java.net.URLDecoder.decode(parts[2].replace("+", "%2B"), "UTF-8") }.getOrNull()
                    val clean = decoded?.let { filename(it) }
                    if (!clean.isNullOrBlank()) return clean
                }
            }
            parameters["filename"]?.let { raw ->
                val clean = filename(raw)
                if (clean.isNotBlank()) return clean
            }
        }

        // 5. Check top 25 lines of YAML body for title/name comment
        val lines = body.lineSequence().take(25)
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#")) {
                val afterHash = trimmed.removePrefix("#").trim()
                val prefixes = listOf("name:", "title:", "名称:", "订阅名称:")
                if (prefixes.any { afterHash.startsWith(it, ignoreCase = true) }) {
                    val n = afterHash.substringAfter(":").trim()
                    if (n.isNotBlank()) return n
                }
            }
        }

        // 6. Hostname fallback
        val host = runCatching { java.net.URI(url).host }.getOrNull()
        if (!host.isNullOrBlank()) {
            return host.removePrefix("www.").substringBefore(":")
        }

        return "订阅-${System.currentTimeMillis() % 1000}"
    }

    fun getConfigFile(context: Context): File {
        return context.filesDir.resolve("config.yaml")
    }

    fun getProfilesDir(context: Context): File {
        return context.filesDir.resolve("profiles").apply { mkdirs() }
    }

    suspend fun downloadSubscription(
        context: Context,
        url: String,
        name: String
    ): Result<ProfileItem> = withContext(Dispatchers.IO) {
        try {
            val preview = SubscriptionManager.preview(context, listOf(SourceDraft(name, url)))
            Result.success(SubscriptionManager.create(context, name, preview,
                standalone = preview.supportsStandalone, allowPartial = false))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Result.failure(IllegalArgumentException(SubscriptionManager.safeFailure(failure))) }
    }

    suspend fun updateManagedSubscription(context: Context, id: String): Result<ProfileItem> = withContext(Dispatchers.IO) {
        try {
            val existing = getProfiles(context).firstOrNull { it.id == id } ?: error("找不到要更新的配置")
            SubscriptionManager.update(context, existing)
            Result.success(getProfiles(context, forceReload = true).first { it.id == id })
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Result.failure(IllegalArgumentException(SubscriptionManager.safeFailure(failure))) }
    }

    /** A node URL is saved locally, never persisted as an auto-updating subscription URL. */
    suspend fun importProfileLink(context: Context, raw: String, name: String): Result<ProfileItem> {
        val input = raw.trim()
        if (!NowhereLink.hasScheme(input)) return downloadSubscription(context, input, name)
        val link = NowhereLink.parse(input) ?: return Result.failure(IllegalArgumentException(NowhereLink.INVALID_LINK_MESSAGE))
        return importNowhereLink(context, link, name)
    }

    internal suspend fun importNowhereLink(context: Context, link: NowhereLink, name: String): Result<ProfileItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                saveLocalProfile(context, name.trim().ifEmpty { link.name }, link.toProfileYaml())
            }
        }


    private val defaultProfileLock = Any()

    private fun ensureDefaultProfileFile(context: Context): File = synchronized(defaultProfileLock) {
        val file = getProfilesDir(context).resolve("$DEFAULT_PROFILE_ID.yaml")
        // Older editor replacements accidentally published the default as managed. Preserve
        // its current version before removing that pointer, so the stable id is local again.
        val store = SubscriptionStore(context)
        store.head(DEFAULT_PROFILE_ID)?.let { head ->
            val current = store.versionFile(DEFAULT_PROFILE_ID, head.versionId)
            check(current.isFile) { "默认配置版本不存在，未覆盖本地内容" }
            val content = current.inputStream().use { readBounded(it) }
            com.github.arca.android.data.subscription.atomicWrite(file, content.toByteArray(Charsets.UTF_8))
            store.delete(DEFAULT_PROFILE_ID)
            invalidateProfilesCache()
        }
        if (!file.isFile) {
            val body = """
                proxies: []
                proxy-groups:
                  - name: GLOBAL
                    type: select
                    proxies:
                      - DIRECT
                rules:
                  - MATCH,DIRECT
            """.trimIndent() + "\n"
            com.github.arca.android.data.subscription.atomicWrite(file, body.toByteArray(Charsets.UTF_8))
            invalidateProfilesCache()
        }
        file
    }

    fun getOrCreateDefaultProfile(context: Context): ProfileItem {
        ensureDefaultProfileFile(context)
        return getProfiles(context, forceReload = true).first { it.id == DEFAULT_PROFILE_ID }
    }

    fun saveLocalProfile(context: Context, name: String, content: String): ProfileItem {
        val normalized = NowhereLink.normalizeProfile(content)
        val id = java.util.UUID.randomUUID().toString()
        val file = getProfilesDir(context).resolve("$id.yaml")
        file.writeText(normalized)
        val item = ProfileItem(id, name, null, file, System.currentTimeMillis())
        saveProfileMetadata(context, item)
        return item
    }

    internal fun readProfileDraft(context: Context, uri: android.net.Uri): SourceDraft {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?.substringBeforeLast('.')?.take(100)?.takeIf { it.isNotBlank() } ?: "本地配置"
        val content = resolver.openInputStream(uri)?.use { readBounded(it) } ?: error("无法读取文件内容")
        return SourceDraft(displayName, content, remote = false)
    }

    @Volatile
    private var cachedProfiles: List<ProfileItem>? = null

    fun invalidateProfilesCache() {
        cachedProfiles = null
        ProfileParser.clearCache()
    }

    fun getProfiles(context: Context, forceReload: Boolean = false): List<ProfileItem> {
        ensureDefaultProfileFile(context)
        if (!forceReload) {
            cachedProfiles?.let { return it }
        }

        val dir = getProfilesDir(context)
        val ids = dir.listFiles { f -> f.extension in setOf("yaml", "subscription") || f.name.endsWith(".subscription.bak") }
            .orEmpty().map { it.name.removeSuffix(".bak").substringBeforeLast('.') }.distinct()
        val prefs = context.getSharedPreferences("profiles_meta", Context.MODE_PRIVATE)
        val store = SubscriptionStore(context)
        val items = ids.mapNotNull { id ->
            val pointer = runCatching { store.head(id) }.getOrNull()
            val managed = pointer != null
            val file = if (pointer == null) dir.resolve("$id.yaml") else
                runCatching { store.versionFile(id, pointer.versionId) }.getOrNull() ?: return@mapNotNull null
            if (!file.isFile) return@mapNotNull null
            val defaultName = if (id == DEFAULT_PROFILE_ID) "默认配置" else pointer?.name ?: id
            val name = prefs.getString("${id}_name", defaultName) ?: defaultName
            val url = if (managed || id == DEFAULT_PROFILE_ID) null else prefs.getString("${id}_url", null)
            val updated = pointer?.updatedAt ?: prefs.getLong("${id}_updated", file.lastModified())
            val scriptEnabled = prefs.getBoolean("${id}_script_enabled", false)
            val scriptIds = prefs.getString("${id}_script_ids", "").orEmpty().split(",").filter { it.isNotBlank() }
            val autoUpdate = id != DEFAULT_PROFILE_ID && prefs.getBoolean("${id}_auto_update", false)
            val autoUpdateInterval = prefs.getInt("${id}_auto_update_interval", 1440)
            val info = parseSubscriptionUserInfo(pointer?.userInfo)
            fun quota(field: String, value: Long?): Long = if (managed) value ?: 0L else prefs.getLong("${id}_$field", 0L)
            ProfileItem(id, name, url, file, updated, scriptEnabled, scriptIds, autoUpdate, autoUpdateInterval,
                quota("upload_bytes", info?.upload), quota("download_bytes", info?.download), quota("total_bytes", info?.total),
                quota("expire_timestamp", info?.expire), managed, pointer?.hasRemoteSources ?: (url != null))
        }

        val orderRaw = prefs.getString("custom_profile_order", null)
        val result = if (!orderRaw.isNullOrBlank()) {
            val orderList = orderRaw.split(",").filter { it.isNotBlank() }
            items.sortedWith(compareBy {
                val idx = orderList.indexOf(it.id)
                if (idx != -1) idx else Int.MAX_VALUE
            })
        } else {
            items.sortedByDescending { it.lastUpdated }
        }
        cachedProfiles = result
        return result
    }

    fun saveProfileOrder(context: Context, orderIds: List<String>) {
        val prefs = context.getSharedPreferences("profiles_meta", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_profile_order", orderIds.joinToString(",")).apply()
        invalidateProfilesCache()
    }

    fun updateProfile(context: Context, item: ProfileItem) {
        saveProfileMetadata(context, item)
    }

    fun saveProfileContent(context: Context, id: String, content: String): Boolean {
        return runCatching {
            require(Regex("[A-Za-z0-9_-]{1,100}").matches(id)) { "配置标识无效" }
            if (id == DEFAULT_PROFILE_ID) ensureDefaultProfileFile(context)
            require(SubscriptionStore(context).head(id) == null) { "托管订阅不可直接覆盖" }
            val file = getProfilesDir(context).resolve("$id.yaml")
            com.github.arca.android.data.subscription.atomicWrite(file, content.toByteArray(Charsets.UTF_8))
            val prefs = context.getSharedPreferences("profiles_meta", Context.MODE_PRIVATE)
            prefs.edit().putLong("${id}_updated", System.currentTimeMillis()).apply()
            invalidateProfilesCache()
            true
        }.getOrDefault(false)
    }

    suspend fun appendNodeToProfile(
        context: Context, profileId: String, raw: String, groupName: String? = null
    ): Result<ProfileItem> = withContext(Dispatchers.IO) {
        try {
            require(profileId == DEFAULT_PROFILE_ID) { "独立节点只能添加到默认配置" }
            val node = inspectSingleNode(context, raw)
            editProfileNode(context, profileId) { ProfileNodeEditor.append(it, node.data, node.name, groupName) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Result.failure(IllegalArgumentException(failure.message ?: "节点追加失败")) }
    }

    suspend fun renameProfileNode(
        context: Context, profileId: String, oldName: String, newName: String
    ): Result<ProfileItem> = withContext(Dispatchers.IO) {
        editProfileNode(context, profileId) { ProfileNodeEditor.rename(it, oldName, newName) }
    }

    suspend fun replaceProfileNode(
        context: Context, profileId: String, name: String, raw: String
    ): Result<ProfileItem> = withContext(Dispatchers.IO) {
        try {
            require(profileId == DEFAULT_PROFILE_ID) { "独立节点仅在默认配置中管理" }
            val node = inspectSingleNode(context, raw)
            editProfileNode(context, profileId) { ProfileNodeEditor.replace(it, name, node.data) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Result.failure(IllegalArgumentException(failure.message ?: "节点保存失败")) }
    }

    suspend fun deleteProfileNode(context: Context, profileId: String, name: String): Result<ProfileItem> = withContext(Dispatchers.IO) {
        editProfileNode(context, profileId) { ProfileNodeEditor.delete(it, name) }
    }

    suspend fun readProfileNode(context: Context, profileId: String, name: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(profileId == DEFAULT_PROFILE_ID) { "请在远程配置中管理此节点；独立节点仅在默认配置中管理" }
            val profile = getProfiles(context, true).firstOrNull { it.id == profileId } ?: error("找不到配置")
            require(!profile.managed && !profile.canRefresh) { "请在远程配置中管理此节点" }
            ProfileNodeEditor.node(profile.file.inputStream().use { readBounded(it) }, name)
        }
    }

    /** Raw names retain YAML order; display/parser expansion must not determine added identity. */
    internal suspend fun readProfileProxyNames(context: Context, profileId: String): List<String> = withContext(Dispatchers.IO) {
        val profile = getProfiles(context, true).firstOrNull { it.id == profileId } ?: error("找不到配置")
        ProfileNodeEditor.proxyNames(profile.file.inputStream().use { readBounded(it) })
    }

    internal suspend fun writableNodeGroupNames(context: Context, profile: ProfileItem): List<String> = withContext(Dispatchers.IO) {
        require(profile.id == DEFAULT_PROFILE_ID && !profile.managed && !profile.canRefresh && profile.file.canWrite()) {
            "请在可写的默认配置中管理独立节点"
        }
        val raw = profile.file.inputStream().use { readBounded(it) }
        val visible = ProfileParser.parse(profile.file, context = context).groupNames.toSet()
        ProfileNodeEditor.writableGroupNames(raw).filter { it in visible }
    }

    private suspend fun inspectSingleNode(context: Context, raw: String): com.github.arca.android.data.subscription.ImportNode {
        val input = raw.trim()
        val text = if (NowhereLink.hasScheme(input)) {
            (NowhereLink.parse(input) ?: error(NowhereLink.INVALID_LINK_MESSAGE)).toProfileYaml()
        } else input
        val inspection = com.github.arca.android.data.subscription.SubscriptionValidator.inspect(context, text, "auto")
        require(inspection.nodes.size == 1 && inspection.diagnostics.isEmpty()) { "请仅输入一个有效节点" }
        return inspection.nodes.single()
    }

    private val nodeEditLock = Any()

    private fun editProfileNode(context: Context, id: String, edit: (String) -> String): Result<ProfileItem> = synchronized(nodeEditLock) {
        runCatching {
            require(id == DEFAULT_PROFILE_ID) { "独立节点仅在默认配置中管理" }
            val profile = getProfiles(context, true).firstOrNull { it.id == id } ?: error("找不到配置")
            require(!profile.managed && !profile.canRefresh) { "请在远程配置中管理此节点" }
            val original = profile.file.inputStream().use { readBounded(it) }
            val content = edit(original)
            if (content == original) return@runCatching profile
            com.github.arca.android.data.subscription.atomicWrite(profile.file, content.toByteArray(Charsets.UTF_8))
            context.getSharedPreferences("profiles_meta", Context.MODE_PRIVATE).edit()
                .putLong("${id}_updated", System.currentTimeMillis()).apply()
            invalidateProfilesCache()
            getProfiles(context, forceReload = true).first { it.id == id }
        }
    }


    private fun saveProfileMetadata(context: Context, item: ProfileItem) {
        val prefs = context.getSharedPreferences("profiles_meta", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("${item.id}_name", item.name)
            .putString("${item.id}_url", item.url.takeUnless { item.id == DEFAULT_PROFILE_ID })
            .putLong("${item.id}_updated", item.lastUpdated)
            .putBoolean("${item.id}_script_enabled", item.scriptEnabled)
            .putString("${item.id}_script_ids", item.scriptIds.joinToString(","))
            .putBoolean("${item.id}_auto_update", item.id != DEFAULT_PROFILE_ID && item.autoUpdate)
            .putInt("${item.id}_auto_update_interval", item.autoUpdateInterval)
            .putLong("${item.id}_upload_bytes", item.uploadBytes)
            .putLong("${item.id}_download_bytes", item.downloadBytes)
            .putLong("${item.id}_total_bytes", item.totalBytes)
            .putLong("${item.id}_expire_timestamp", item.expireTimestamp)
            .apply()
        invalidateProfilesCache()
    }

    internal fun hasProfile(context: Context, id: String): Boolean {
        val directory = getProfilesDir(context)
        return directory.resolve("$id.yaml").isFile || directory.resolve("$id.subscription").isFile
    }

    internal fun deleteProfile(context: Context, id: String) {
        if (id == DEFAULT_PROFILE_ID) return
        SubscriptionStore(context).delete(id)
        val file = getProfilesDir(context).resolve("$id.yaml")
        if (file.exists()) file.delete()
        val prefs = context.getSharedPreferences("profiles_meta", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("${id}_name")
            .remove("${id}_url")
            .remove("${id}_updated")
            .remove("${id}_script_enabled")
            .remove("${id}_script_ids")
            .remove("${id}_auto_update")
            .remove("${id}_auto_update_interval")
            .remove("${id}_upload_bytes")
            .remove("${id}_download_bytes")
            .remove("${id}_total_bytes")
            .remove("${id}_expire_timestamp")
            .apply()
        invalidateProfilesCache()
    }

    /**
     * Runtime-critical top-level keys belong to the injected header. A subscription must not be
     * able to reintroduce or override them, so they are removed structurally after parsing: text
     * matching is trivially defeated by quoting a key (`"external-controller": 0.0.0.0:9090`).
     */
    internal fun sanitizeUserConfig(raw: String): String {
        val yaml = org.yaml.snakeyaml.Yaml(org.yaml.snakeyaml.DumperOptions().apply {
            defaultFlowStyle = org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
        })
        val parsed = try {
            yaml.load<Any?>(raw)
        } catch (e: org.yaml.snakeyaml.error.YAMLException) {
            throw IllegalArgumentException("配置 YAML 无效，未应用；请检查订阅内容", e)
        }
        require(parsed is Map<*, *>) { "配置根节点必须为 YAML 映射，未应用" }
        val sanitized = LinkedHashMap<String, Any?>()
        for ((key, value) in parsed) {
            val keyStr = key?.toString() ?: continue
            if (keyStr.lowercase() in PROTECTED_TOP_LEVEL_KEYS) {
                Log.d(TAG, "Ignoring runtime-owned profile key: $keyStr")
            } else {
                sanitized[keyStr] = value
            }
        }
        return if (sanitized.isEmpty()) "" else yaml.dump(sanitized)
    }

    fun prepareConfig(
        context: Context,
        sourceFile: File?,
        ipv6Enabled: Boolean = SettingsManager(context).ipv6Enabled
    ): File {
        if (sourceFile == null) return prepareConfig(context, getOrCreateDefaultProfile(context).file, ipv6Enabled)
        val targetFile = getConfigFile(context)
        val settings = SettingsManager(context)

        var userDns: Map<String, Any?>? = null
        if (sourceFile != null && sourceFile.exists()) {
            var rawContent = sourceFile.readText()
            val activeProfile = getProfiles(context).find {
                it.file.absolutePath == sourceFile.absolutePath || it.file.name == sourceFile.name || it.id == sourceFile.nameWithoutExtension
            }
            val targetIds = if (activeProfile != null && activeProfile.scriptEnabled && activeProfile.scriptIds.isNotEmpty()) {
                activeProfile.scriptIds
            } else null
            if (!targetIds.isNullOrEmpty()) {
                Log.i(TAG, "Applying config rewrite scripts for profile: ${activeProfile?.name ?: sourceFile.name} (${targetIds.size} scripts)...")
                rawContent = ConfigScriptEngine.executeScripts(
                    context = context,
                    rawYaml = rawContent,
                    targetScriptIds = targetIds
                )
            }
            userDns = extractUserDns(rawContent)
            val tcpConcurrent = extractUserTcpConcurrent(rawContent) ?: true
            val userContent = sanitizeUserConfig(rawContent)
            val header = buildRuntimeHeader(settings, userDns, ipv6Enabled, tcpConcurrent)
            targetFile.writeText("$header\n$userContent")
            Log.i(TAG, "Prepared synthesized config with user profile: ${sourceFile.name}")
        } else {
            val header = buildRuntimeHeader(settings, userDns, ipv6Enabled, tcpConcurrent = true)
            val defaultBody = """
                proxies: []
                proxy-groups:
                  - name: GLOBAL
                    type: select
                    proxies:
                      - DIRECT
                rules:
                  - MATCH,DIRECT
            """.trimIndent()
            targetFile.writeText("$header\n$defaultBody")
            Log.i(TAG, "Prepared default fallback config")
        }

        runCatching {
            com.github.kr328.clash.core.bridge.Bridge.init(context)
            val clashHomeConfig = context.filesDir.resolve("clash").apply { mkdirs() }.resolve("config.yaml")
            clashHomeConfig.writeText(targetFile.readText())
        }

        return targetFile
    }
    /** Preserve an explicit profile override; otherwise enable Mihomo's fastest-address TCP race. */
    @Suppress("ThrowsCount")
    internal fun extractUserTcpConcurrent(raw: String): Boolean? {
        val parsed = try {
            org.yaml.snakeyaml.Yaml().load<Any?>(raw) as? Map<*, *>
                ?: throw IllegalArgumentException("配置根节点必须为 YAML 映射，未应用")
        } catch (e: org.yaml.snakeyaml.error.YAMLException) {
            throw IllegalArgumentException("配置 YAML 无效，未应用；请检查订阅内容", e)
        }
        val key = parsed.keys.firstOrNull { it?.toString()?.equals("tcp-concurrent", ignoreCase = true) == true }
            ?: return null
        return parsed[key] as? Boolean
            ?: throw IllegalArgumentException("配置项 tcp-concurrent 必须为 true 或 false")
    }

    internal fun extractUserDns(raw: String): Map<String, Any?>? = runCatching {
        val parsed = org.yaml.snakeyaml.Yaml().load<Any?>(raw) as? Map<*, *> ?: return null
        val dns = parsed["dns"] as? Map<*, *> ?: return null
        LinkedHashMap<String, Any?>().apply { dns.forEach { (k, v) -> if (k != null) put(k.toString(), v) } }
    }.getOrNull()

    private val runtimeOwnedDns = setOf("enable", "listen", "ipv6", "enhanced-mode", "fake-ip-range")

    /** IPv6 capture controls client packets/DNS, not the address family used to reach a proxy server. */
    internal data class RuntimeIpv6Flags(val proxyServerResolution: Boolean, val clientDns: Boolean)

    internal fun runtimeIpv6Flags(ipv6RoutingEnabled: Boolean) =
        RuntimeIpv6Flags(proxyServerResolution = true, clientDns = ipv6RoutingEnabled)
    private val defaultFakeIpFilter = listOf("*.lan", "*.local", "localhost.ptlogin2.qq.com", "*.connectivitycheck.android.com", "*.connectivitycheck.gstatic.com", "connectivitycheck.gstatic.com", "connectivitycheck.android.com", "clients3.google.com", "www.google.com/generate_204", "+.msftconnecttest.com", "+.msftncsi.com", "+.qualcomm.com", "+.miui.com", "+.xiaomi.com")

    internal fun buildDnsSection(fakeIp: Boolean, userDns: Map<String, Any?>?, ipv6Enabled: Boolean = false): LinkedHashMap<String, Any?> {
        val dns = linkedMapOf<String, Any?>("enable" to true, "listen" to "127.0.0.1:1053", "ipv6" to ipv6Enabled,
            "enhanced-mode" to if (fakeIp) "fake-ip" else "redir-host", "fake-ip-range" to "198.18.0.1/16",
            "default-nameserver" to listOf("223.5.5.5", "119.29.29.29"), "nameserver" to listOf("https://223.5.5.5/dns-query", "https://doh.pub/dns-query", "223.5.5.5", "119.29.29.29", "180.76.76.76"),
            "fallback" to listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query", "1.1.1.1", "8.8.8.8"), "fake-ip-filter" to defaultFakeIpFilter)
        userDns?.forEach { (key, value) ->
            if (key.lowercase() !in runtimeOwnedDns) {
                dns[key] = if (key == "fake-ip-filter") (defaultFakeIpFilter + ((value as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList())).distinct() else value
            }
        }
        return dns
    }

    internal fun buildRuntimeHeader(
        settings: SettingsManager,
        userDns: Map<String, Any?>?,
        ipv6Enabled: Boolean = false,
        tcpConcurrent: Boolean = true
    ): String {
        val ipv6Flags = runtimeIpv6Flags(ipv6Enabled)
        val values = linkedMapOf<String, Any?>("mode" to settings.tunnelMode, "log-level" to settings.logLevel, "ipv6" to ipv6Flags.proxyServerResolution, "unified-delay" to settings.unifiedDelayEnabled, "mixed-port" to 7890, "allow-lan" to settings.allowLan, "tcp-concurrent" to tcpConcurrent)
        if (settings.allowLan) {
            values["authentication"] = listOf(settings.lanCredentials().encoded)
            values["skip-auth-prefixes"] = listOf("127.0.0.1/32", "::1/128")
        }
        values["geodata-mode"] = false
        values["geo-auto-update"] = false
        values["geox-url"] = linkedMapOf("geoip" to "https://testingcf.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geoip.metadb", "geosite" to "https://testingcf.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geosite.dat", "mmdb" to "https://testingcf.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/country.mmdb")
        values["dns"] = buildDnsSection(settings.fakeIpEnabled, userDns, ipv6Flags.clientDns)
        val opts = org.yaml.snakeyaml.DumperOptions().apply { defaultFlowStyle = org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK; isPrettyFlow = true; indent = 2 }
        return "# Mihomo Android Injected Runtime Config\n" + org.yaml.snakeyaml.Yaml(opts).dump(values).trimEnd()
    }

}
