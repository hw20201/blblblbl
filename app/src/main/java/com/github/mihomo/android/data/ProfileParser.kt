package com.github.mihomo.android.data

import android.content.Context
import android.util.Log
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File

data class ParsedRule(
    val type: String,
    val payload: String,
    val target: String,
    val noResolve: Boolean = false
)

data class ParsedProfile(
    val groupNames: List<String> = emptyList(),
    val groups: Map<String, ProxyGroup> = emptyMap(),
    val groupIcons: Map<String, String> = emptyMap(),
    val totalNodes: Int = 0,
    val totalRules: Int = 0,
    val activeNode: String = "",
    val rules: List<ParsedRule> = emptyList()
)

object ProfileParser {
    private const val TAG = "ProfileParser"

    private val yaml: Yaml by lazy {
        val loaderOptions = LoaderOptions().apply {
            codePointLimit = 100 * 1024 * 1024 // 100 MB safe limit
        }
        Yaml(SafeConstructor(loaderOptions))
    }

    private data class CacheEntry(
        val lastModified: Long,
        val length: Long,
        val savedNodesHash: Int,
        val scriptKey: String,
        val providerSignature: Int,
        val parsed: ParsedProfile
    ) {
        fun isValid(lm: Long, len: Long, hash: Int, sKey: String, providers: Int): Boolean =
            lastModified == lm && length == len && savedNodesHash == hash && scriptKey == sKey && providerSignature == providers
    }
    private val parsedCache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()

    fun clearCache() {
        parsedCache.clear()
    }

    fun parse(file: File?, savedNodes: Map<String, String> = emptyMap(), context: Context? = null): ParsedProfile {
        if (file == null || !file.exists()) {
            return ParsedProfile()
        }

        val path = file.absolutePath
        val lm = file.lastModified()
        val len = file.length()
        val savedHash = savedNodes.hashCode()

        val activeProfile = if (context != null) {
            ConfigManager.getProfiles(context).find {
                it.file.name == file.name || it.file.absolutePath == file.absolutePath || it.id == file.nameWithoutExtension
            }
        } else null
        val scriptKey = if (activeProfile != null && activeProfile.scriptEnabled) {
            activeProfile.scriptIds.sorted().joinToString(",")
        } else ""

        var content = file.readText()
        val providerSignature = providerCacheSignature(content, file, context)
        val cached = parsedCache[path]
        if (cached?.isValid(lm, len, savedHash, scriptKey, providerSignature) == true) {
            return cached.parsed
        }

        if (context != null) {
            val targetIds = if (activeProfile != null && activeProfile.scriptEnabled && activeProfile.scriptIds.isNotEmpty()) {
                activeProfile.scriptIds
            } else null
            if (!targetIds.isNullOrEmpty()) {
                content = runCatching {
                    ConfigScriptEngine.executeScripts(
                        context = context,
                        rawYaml = content,
                        targetScriptIds = targetIds,
                        logToRepo = false
                    )
                }.getOrElse {
                    Log.w(TAG, "ConfigScriptEngine execution in ProfileParser failed: ${it.message}")
                    content
                }
            }
        }

        try {
            val data = runCatching { yaml.load<Map<String, Any?>>(content) }.getOrNull()
            if (data == null) {
                return parseFallback(content, savedNodes)
            }

            // 1. Extract inline and locally cached provider proxies. Mihomo rewrites
            // provider paths under filesDir/clash/providers at runtime; user paths
            // are also checked relative to the profile and Clash home.
            val proxyMap = extractProxyMap(data).toMutableMap()
            proxyMap.putAll(extractCachedProviderProxies(data, file, context))

            // 2. Extract rules (support 'rules' and 'Rule')
            val rawRules = (data["rules"] ?: data["Rule"]) as? List<*>
            val parsedRules = extractParsedRules(rawRules)
            // Match the rules page: count parsed top-level entries only (never
            // synthesize counts by expanding rule-providers or rule sets).
            val rulesCount = parsedRules.size

            // 3. Extract proxy-groups (support 'proxy-groups' and 'Proxy Group')
            val groupNames = mutableListOf<String>()
            val groupMap = mutableMapOf<String, ProxyGroup>()
            val groupIcons = mutableMapOf<String, String>()
            val rawGroups = (data["proxy-groups"] ?: data["Proxy Group"]) as? List<*>
            val rawGroupNames = rawGroups?.mapNotNull { (it as? Map<*, *>)?.get("name")?.toString() }?.toSet() ?: emptySet()
            if (rawGroups != null) {
                for (item in rawGroups) {
                    if (item is Map<*, *>) {
                        val gName = item["name"]?.toString() ?: continue
                        val gType = item["type"]?.toString() ?: "select"
                        val gIcon = item["icon"]?.toString() ?: item["img-url"]?.toString()
                        val gHidden = item["hidden"] == true || item["hidden"]?.toString()?.equals("true", ignoreCase = true) == true
                        if (!gIcon.isNullOrBlank()) {
                            groupIcons[gName] = gIcon.trim()
                        }
                        val explicitProxies = ((item["proxies"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()) +
                            resolveUsedProviderNames(item["use"], data, proxyMap.keys, file, context)
                        val filterPattern = item["filter"]?.toString()?.trim()
                        val excludePattern = item["exclude-filter"]?.toString()?.trim()
                        val includeAll = item["include-all"] == true ||
                                item["include-all"]?.toString()?.equals("true", ignoreCase = true) == true ||
                                item["include-all-proxies"] == true ||
                                item["include-all-proxies"]?.toString()?.equals("true", ignoreCase = true) == true

                        val memberNames = resolveGroupMembers(
                            explicitProxies = explicitProxies,
                            proxyNames = proxyMap.keys,
                            filterPattern = filterPattern,
                            excludePattern = excludePattern,
                            includeAll = includeAll
                        )
                        val proxies = memberNames.mapNotNull { mName ->
                            val mType = proxyMap[mName] ?: if (mName in listOf("DIRECT", "REJECT", "GLOBAL")) mName else "Group"
                            val isSubGroup = rawGroupNames.contains(mName)
                            Proxy(
                                name = mName,
                                title = mName,
                                subtitle = "",
                                type = mType,
                                delay = -1,
                                isGroup = isSubGroup
                            )
                        }

                        val saved = savedNodes[gName]
                        val now = if (saved != null && proxies.any { it.name == saved }) {
                            saved
                        } else {
                            proxies.firstOrNull()?.name ?: ""
                        }
                        if (!gHidden) {
                            groupNames.add(gName)
                        }
                        groupMap[gName] = ProxyGroup(type = gType, proxies = proxies, now = now, hidden = gHidden)
                    }
                }
            }

            // Fallback: If no proxy-groups defined, construct default "节点选择" group with all proxies
            if (groupNames.isEmpty() && proxyMap.isNotEmpty()) {
                val proxies = proxyMap.map { (name, type) ->
                    Proxy(name = name, title = name, type = type, delay = -1)
                }
                val saved = savedNodes["节点选择"]
                val now = if (saved != null && proxies.any { it.name == saved }) saved else (proxies.firstOrNull()?.name ?: "")
                groupNames.add("节点选择")
                groupMap["节点选择"] = ProxyGroup(type = "select", proxies = proxies, now = now)
            }

            val totalNodes = proxyMap.keys.count { !it.equals("DIRECT", true) && !it.equals("REJECT", true) && !it.equals("GLOBAL", true) }
            val activeNode = groupMap.values.firstOrNull { !it.hidden }?.now ?: groupMap.values.firstOrNull()?.now ?: ""
            Log.i(TAG, "Parsed profile: ${groupNames.size} groups, $totalNodes nodes, $rulesCount rules")

            val result = ParsedProfile(
                groupNames = groupNames,
                groups = groupMap,
                groupIcons = groupIcons,
                totalNodes = totalNodes,
                totalRules = rulesCount,
                activeNode = activeNode,
                rules = parsedRules
            )
            parsedCache[path] = CacheEntry(lm, len, savedHash, scriptKey, providerSignature, result)
            return result
        } catch (e: Throwable) {
            Log.w(TAG, "SnakeYAML failed, using robust line fallback: ${e.message}")
            val fallback = parseFallback(content, savedNodes)
            parsedCache[path] = CacheEntry(lm, len, savedHash, scriptKey, providerSignature, fallback)
            return fallback
        }
    }

    private fun parseFallback(content: String, savedNodes: Map<String, String>): ParsedProfile {
        val proxyMap = mutableMapOf<String, String>()
        val groupNames = mutableListOf<String>()
        val groupMap = mutableMapOf<String, ProxyGroup>()
        val groupIcons = mutableMapOf<String, String>()
        var inProxies = false
        var inGroups = false
        var currentName: String? = null
        var currentType: String? = null
        var currentGroupName: String? = null
        var currentGroupType = "select"
        var currentGroupHidden = false
        var currentGroupFilter: String? = null
        var currentGroupIncludeAll = false
        var currentGroupProxies = mutableListOf<Proxy>()
        var rulesCount = 0
        val fallbackRules = mutableListOf<ParsedRule>()

        fun finalizeGroup() {
            if (currentGroupName != null) {
                if (!currentGroupHidden) {
                    groupNames.add(currentGroupName!!)
                }
                val existingNames = currentGroupProxies.map { it.name }.toSet()
                if (currentGroupIncludeAll) {
                    for ((pName, pType) in proxyMap) {
                        if (!existingNames.contains(pName)) {
                            currentGroupProxies.add(Proxy(name = pName, title = pName, type = pType, delay = -1))
                        }
                    }
                } else if (!currentGroupFilter.isNullOrBlank()) {
                    val regex = runCatching { Regex(currentGroupFilter!!, RegexOption.IGNORE_CASE) }.getOrNull()
                    if (regex != null) {
                        for ((pName, pType) in proxyMap) {
                            if (!existingNames.contains(pName) && regex.containsMatchIn(pName)) {
                                currentGroupProxies.add(Proxy(name = pName, title = pName, type = pType, delay = -1))
                            }
                        }
                    }
                }
                val saved = savedNodes[currentGroupName!!]
                val now = if (saved != null && currentGroupProxies.any { it.name == saved }) saved else (currentGroupProxies.firstOrNull()?.name ?: "")
                groupMap[currentGroupName!!] = ProxyGroup(
                    type = currentGroupType,
                    proxies = currentGroupProxies.toList(),
                    now = now,
                    hidden = currentGroupHidden
                )
            }
        }

        content.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            val lower = trimmed.lowercase()

            if (lower.startsWith("proxies:") || lower.startsWith("proxy:")) {
                inProxies = true
                inGroups = false
                return@forEach
            }
            if (lower.startsWith("proxy-groups:") || lower.startsWith("proxy group:")) {
                if (currentName != null) {
                    proxyMap[currentName!!] = currentType ?: "Proxy"
                    currentName = null
                }
                inProxies = false
                inGroups = true
                return@forEach
            }
            if (lower.startsWith("rules:") || lower.startsWith("rule:")) {
                finalizeGroup()
                currentGroupName = null
                inProxies = false
                inGroups = false
            }

            if (inProxies) {
                if (trimmed.startsWith("- name:") || trimmed.startsWith("- {name:")) {
                    if (currentName != null) {
                        proxyMap[currentName!!] = currentType ?: "Proxy"
                    }
                    val namePart = if (trimmed.startsWith("- name:")) {
                        trimmed.removePrefix("- name:").trim()
                    } else {
                        trimmed.substringAfter("name:").substringBefore(",").trim()
                    }
                    currentName = namePart.trim('"', '\'')
                    currentType = if (trimmed.contains("type:")) {
                        trimmed.substringAfter("type:").substringBefore(",").substringBefore("}").trim().trim('"', '\'')
                    } else null
                } else if (trimmed.startsWith("type:")) {
                    currentType = trimmed.removePrefix("type:").trim().trim('"', '\'')
                }
            } else if (inGroups) {
                if (trimmed.startsWith("- name:")) {
                    finalizeGroup()
                    currentGroupName = trimmed.removePrefix("- name:").trim().trim('"', '\'')
                    currentGroupType = "select"
                    currentGroupHidden = false
                    currentGroupFilter = null
                    currentGroupIncludeAll = false
                    currentGroupProxies = mutableListOf()
                } else if (trimmed.startsWith("type:")) {
                    currentGroupType = trimmed.removePrefix("type:").trim().trim('"', '\'')
                } else if (trimmed.startsWith("hidden:")) {
                    currentGroupHidden = trimmed.removePrefix("hidden:").trim().trim('"', '\'').toBoolean()
                } else if (trimmed.startsWith("filter:")) {
                    currentGroupFilter = trimmed.removePrefix("filter:").trim().trim('"', '\'')
                } else if (trimmed.startsWith("include-all:") || trimmed.startsWith("include-all-proxies:")) {
                    currentGroupIncludeAll = trimmed.substringAfter(":").trim().trim('"', '\'').toBoolean()
                } else if (trimmed.startsWith("icon:") && currentGroupName != null) {
                    groupIcons[currentGroupName!!] = trimmed.removePrefix("icon:").trim().trim('"', '\'')
                } else if (trimmed.startsWith("img-url:") && currentGroupName != null) {
                    groupIcons[currentGroupName!!] = trimmed.removePrefix("img-url:").trim().trim('"', '\'')
                } else if (trimmed.startsWith("- ") && !trimmed.startsWith("- name:")) {
                    val pName = trimmed.removePrefix("- ").trim().trim('"', '\'')
                    if (pName.isNotBlank() && currentGroupName != null) {
                        val pType = proxyMap[pName] ?: if (pName in listOf("DIRECT", "REJECT", "GLOBAL")) pName else "Group"
                        currentGroupProxies.add(Proxy(name = pName, title = pName, type = pType, delay = -1))
                    }
                }
            } else if (!inProxies && !inGroups && content.contains("rules:")) {
                if (trimmed.startsWith("- ")) {
                    val ruleContent = trimmed.removePrefix("- ").trim().trim('"', '\'')
                    val parts = ruleContent.split(",").map { it.trim() }
                    if (parts.isNotEmpty()) {
                        val rType = parts[0]
                        val rPayload = if (parts.size >= 3) parts[1] else ""
                        val rTarget = if (parts.size >= 3) parts[2] else if (parts.size == 2) parts[1] else ""
                        val rNoResolve = parts.any { it.equals("no-resolve", ignoreCase = true) }
                        fallbackRules.add(ParsedRule(rType, rPayload, rTarget, rNoResolve))
                    }
                    rulesCount++
                }
            }
        }

        if (currentName != null) {
            proxyMap[currentName!!] = currentType ?: "Proxy"
        }
        finalizeGroup()

        if (groupNames.isEmpty() && proxyMap.isNotEmpty()) {
            val proxies = proxyMap.map { (name, type) ->
                Proxy(name = name, title = name, type = type, delay = -1)
            }
            val saved = savedNodes["节点选择"]
            val now = if (saved != null && proxies.any { it.name == saved }) saved else (proxies.firstOrNull()?.name ?: "")
            groupNames.add("节点选择")
            groupMap["节点选择"] = ProxyGroup(type = "select", proxies = proxies, now = now)
        }

        return ParsedProfile(
            groupNames = groupNames,
            groups = groupMap,
            groupIcons = groupIcons,
            totalNodes = proxyMap.keys.count { !it.equals("DIRECT", true) && !it.equals("REJECT", true) && !it.equals("GLOBAL", true) },
            totalRules = rulesCount,
            activeNode = groupMap.values.firstOrNull()?.now ?: "",
            rules = fallbackRules
        )
    }

    private fun extractProxyMap(data: Map<String, Any?>): Map<String, String> {
        val proxyMap = mutableMapOf<String, String>()
        val rawProxies = (data["proxies"] ?: data["Proxy"]) as? List<*> ?: return proxyMap
        for (item in rawProxies) {
            if (item is Map<*, *>) {
                val name = item["name"]?.toString() ?: continue
                val type = item["type"]?.toString() ?: "Proxy"
                proxyMap[name] = type
            }
        }
        return proxyMap
    }

    private fun resolveUsedProviderNames(use: Any?, data: Map<String, Any?>, proxyNames: Set<String>, profile: File, context: Context?): List<String> {
        val names = when (use) {
            is List<*> -> use.mapNotNull { it?.toString() }
            is String -> listOf(use)
            else -> emptyList()
        }
        val providers = data["proxy-providers"] as? Map<*, *> ?: return emptyList()
        return names.flatMap { providerName ->
            val provider = providers[providerName] as? Map<*, *> ?: return@flatMap emptyList()
            providerCacheFiles(provider, profile, context).firstNotNullOfOrNull { candidate ->
                candidate.takeIf { it.isFile }?.let(::loadProviderProxyNames)?.keys
            }?.filter { it in proxyNames }.orEmpty()
        }
    }

    private fun extractCachedProviderProxies(data: Map<String, Any?>, profile: File, context: Context?): Map<String, String> {
        val providers = data["proxy-providers"] as? Map<*, *> ?: return emptyMap()
        val result = linkedMapOf<String, String>()
        val providersDir = context?.filesDir?.resolve("clash/providers")
        for ((_, raw) in providers) {
            val provider = raw as? Map<*, *> ?: continue
            val candidates = providerCacheFiles(provider, profile, context)
            candidates.firstNotNullOfOrNull { it.takeIf(File::isFile)?.let(::loadProviderProxyNames) }?.forEach { (name, type) -> result[name] = type }
        }
        return result
    }

    private fun providerCacheFiles(provider: Map<*, *>, profile: File, context: Context?): List<File> {
        val path = provider["path"]?.toString()?.takeIf { it.isNotBlank() }
        val url = provider["url"]?.toString()?.takeIf { it.isNotBlank() }
        val providersDir = context?.filesDir?.resolve("clash/providers")
        return buildList {
            if (path != null) {
                val configured = File(path)
                add(configured)
                if (!configured.isAbsolute) add(File(profile.parentFile, path))
                if (providersDir != null) add(File(providersDir, path.removePrefix("./")))
            }
            if (url != null && providersDir != null) {
                val md5 = java.security.MessageDigest.getInstance("MD5").digest(url.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                add(File(providersDir, "proxies/$md5"))
            }
        }.distinctBy { it.absolutePath }
    }

    private fun providerCacheSignature(content: String, profile: File, context: Context?): Int {
        val data = runCatching { yaml.load<Map<String, Any?>>(content) }.getOrNull() ?: return 0
        val providers = data["proxy-providers"] as? Map<*, *> ?: return 0
        return providers.values.mapNotNull { raw ->
            val provider = raw as? Map<*, *> ?: return@mapNotNull null
            providerCacheFiles(provider, profile, context).firstOrNull { it.isFile }?.let { f ->
                listOf(f.absolutePath, f.lastModified(), f.length(), runCatching { f.readBytes().contentHashCode() }.getOrDefault(0))
            }
        }.hashCode()
    }

    private fun loadProviderProxyNames(file: File): Map<String, String> = runCatching {
        val payload = yaml.load<Map<String, Any?>>(file.readText()) ?: return@runCatching emptyMap()
        extractProxyMap(payload)
    }.getOrDefault(emptyMap())

    private fun extractParsedRules(rawRules: List<*>?): List<ParsedRule> {
        val parsedRules = mutableListOf<ParsedRule>()
        if (rawRules == null) return parsedRules
        for (item in rawRules) {
            val ruleStr = item?.toString()?.trim() ?: continue
            if (ruleStr.isBlank() || ruleStr.startsWith("#")) continue
            val parts = ruleStr.split(",").map { it.trim() }
            if (parts.isNotEmpty()) {
                val rType = parts[0]
                val rPayload = if (parts.size >= 3) parts[1] else ""
                val rTarget = if (parts.size >= 3) parts[2] else if (parts.size == 2) parts[1] else ""
                val rNoResolve = parts.any { it.equals("no-resolve", ignoreCase = true) }
                parsedRules.add(ParsedRule(rType, rPayload, rTarget, rNoResolve))
            }
        }
        return parsedRules
    }

    private fun resolveGroupMembers(
        explicitProxies: List<String>,
        proxyNames: Set<String>,
        filterPattern: String?,
        excludePattern: String?,
        includeAll: Boolean
    ): List<String> {
        val memberSet = LinkedHashSet<String>(explicitProxies)
        val filterRegex = if (!filterPattern.isNullOrBlank()) {
            runCatching { Regex(filterPattern, RegexOption.IGNORE_CASE) }.getOrNull()
        } else null
        val excludeRegex = if (!excludePattern.isNullOrBlank()) {
            runCatching { Regex(excludePattern, RegexOption.IGNORE_CASE) }.getOrNull()
        } else null

        if (includeAll) {
            for (pName in proxyNames) {
                if (excludeRegex == null || !excludeRegex.containsMatchIn(pName)) {
                    memberSet.add(pName)
                }
            }
        } else if (filterRegex != null) {
            for (pName in proxyNames) {
                if (filterRegex.containsMatchIn(pName)) {
                    if (excludeRegex == null || !excludeRegex.containsMatchIn(pName)) {
                        memberSet.add(pName)
                    }
                }
            }
        } else if (excludeRegex != null) {
            memberSet.removeAll { excludeRegex.containsMatchIn(it) }
        }
        return memberSet.toList()
    }
}
