package com.github.arca.android.data

import com.github.arca.android.data.subscription.SUBSCRIPTION_MAX_BYTES
import kotlinx.serialization.json.JsonObject
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

internal object ProfileNodeEditor {
    private val reserved = setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE", "GLOBAL")

    private fun isReserved(name: String) = reserved.any { it.equals(name, ignoreCase = true) }

    /** Full domestic-direct split for the built-in default profile. */
    internal val defaultSplitRules = listOf(
        "GEOSITE,CN,DIRECT",
        "GEOIP,CN,DIRECT",
        "IP-CIDR,192.168.0.0/16,DIRECT,no-resolve",
        "IP-CIDR,10.0.0.0/8,DIRECT,no-resolve",
        "IP-CIDR,172.16.0.0/12,DIRECT,no-resolve",
        "IP-CIDR,127.0.0.0/8,DIRECT,no-resolve",
        "IP-CIDR,224.0.0.0/4,DIRECT,no-resolve",
        "IP-CIDR,240.0.0.0/4,DIRECT,no-resolve",
        "MATCH,GLOBAL"
    )

    /**
     * Replace the legacy single `MATCH,DIRECT` rule with the full split rules,
     * keeping every proxy and group untouched. Returns null when the content
     * is not a legacy built-in default (user content is never rewritten).
     */
    fun upgradeLegacyDefaultRules(raw: String): String? {
        val config = parse(raw)
        val rules = config["rules"] as? List<*> ?: return null
        if (rules.size != 1 || rules[0].toString().trim().uppercase() != "MATCH,DIRECT") return null
        val groups = entries(config, "proxy-groups")
        if (groups.none { it["name"] == "GLOBAL" && it["type"] == "select" }) return null
        config["rules"] = defaultSplitRules
        return dump(config)
    }

    fun proxyNames(raw: String): List<String> = entries(parse(raw), "proxies").mapNotNull { it["name"] as? String }

    /** Only unambiguous, visible raw groups whose explicit membership can be updated. */
    fun writableGroupNames(raw: String): List<String> = writableGroups(entries(parse(raw), "proxy-groups"))
        .map { it["name"] as String }

    private fun writableGroups(groups: List<MutableMap<Any?, Any?>>): List<MutableMap<Any?, Any?>> {
        val counts = groups.groupingBy { it["name"] }.eachCount()
        return groups.filter { group ->
            val name = group["name"] as? String
            name != null && name.isNotBlank() && counts[name] == 1 &&
                !group["hidden"].toString().equals("true", ignoreCase = true) &&
                group["type"] in setOf("select", "url-test", "fallback", "load-balance", "relay") &&
                ("proxies" !in group || group["proxies"] is List<*>)
        }
    }

    fun append(raw: String, proxy: JsonObject, name: String, groupName: String? = null): String {
        return appendAll(raw, listOf(proxy to name), groupName)
    }

    /** Append one checked import batch with a single YAML parse/dump and no partial file writes. */
    fun appendAll(raw: String, imported: List<Pair<JsonObject, String>>, groupName: String? = null): String {
        require(imported.isNotEmpty()) { "没有可导入的节点" }
        val config = parse(raw)
        val nodes = entries(config, "proxies")
        require(nodes.size + imported.size <= 10_000) { "节点超过 10,000 条限制" }
        val used = usedNames(config).toMutableSet()
        val resolved = imported.map { (_, name) ->
            val base = name.filterNot { it.isISOControl() || it == ',' }.trim().take(100).ifBlank { "节点" }
            generateSequence(1) { it + 1 }
                .map { n -> if (n == 1) base else "-$n".let { base.take(100 - it.length) + it } }
                .first { it !in used && !isReserved(it) }
                .also { used += it }
        }
        val appended = imported.mapIndexed { index, (proxy, _) ->
            parse(proxy.toString()).apply { put("name", resolved[index]) }
        }
        config["proxies"] = nodes + appended
        val groups = entries(config, "proxy-groups")
        val writable = writableGroups(groups)
        val group = if (!groupName.isNullOrBlank()) {
            groups.singleOrNull { it["name"] == groupName }?.also {
                require(it in writable) { "目标策略组不可写或已隐藏，请重新选择" }
            } ?: error("目标策略组已不存在，请重新选择")
        } else writable.firstOrNull { it["type"] == "select" } ?: writable.firstOrNull()
        require(group != null || groups.isEmpty()) { "没有可见且可写的策略组，请先编辑配置" }
        if (group != null) {
            val exclusion = (group["exclude-filter"] as? String)?.trim()?.takeIf { it.isNotBlank() }
                ?.let { Regex(it, RegexOption.IGNORE_CASE) }
            require(exclusion == null || resolved.none { exclusion.containsMatchIn(it) }) {
                "目标策略组的排除筛选会隐藏此节点，请选择其他策略组"
            }
            group["proxies"] = ((group["proxies"] as? List<*>) ?: emptyList<Any>()) + resolved
            // Explicit membership must remain visible even in groups with provider filters.
            config["proxy-groups"] = groups
        } else {
            config["proxy-groups"] = listOf(mapOf("name" to "节点选择", "type" to "select", "proxies" to (resolved + "DIRECT")))
            if (config["rules"] == null) config["rules"] = listOf("MATCH,节点选择")
        }
        return dump(config)
    }

    fun node(raw: String, name: String): String {
        require(!isReserved(name)) { "保留名称不可作为独立节点管理" }
        return dump(mapOf("proxies" to listOf(entries(parse(raw), "proxies")
            .singleOrNull { it["name"] == name } ?: error("找不到唯一节点，请先更新配置"))))
    }

    fun replace(raw: String, name: String, proxy: JsonObject): String {
        require(!isReserved(name)) { "保留名称不可作为独立节点管理" }
        val config = parse(raw)
        val nodes = entries(config, "proxies")
        require(nodes.count { it["name"] == name } == 1) { "找不到唯一的内联节点" }
        config["proxies"] = nodes.map { if (it["name"] == name) parse(proxy.toString()).apply { put("name", name) } else it }
        return dump(config)
    }

    fun delete(raw: String, name: String): String {
        // Replace exact routing references first, then remove the node itself. Empty groups
        // keep DIRECT so deleting their final member never leaves an invalid configuration.
        val config = parse(raw)
        val nodes = entries(config, "proxies")
        require(nodes.count { it["name"] == name } == 1 && !isReserved(name)) { "找不到可删除的唯一节点" }
        config["proxies"] = nodes.filter { it["name"] != name }
        val groups = entries(config, "proxy-groups")
        groups.forEach { group ->
            (group["proxies"] as? List<*>)?.let { values ->
                group["proxies"] = values.filterNot { it == name }.ifEmpty { listOf("DIRECT") }
            }
        }
        if ("proxy-groups" in config) config["proxy-groups"] = groups
        replaceReferences(config, name, "DIRECT")
        return dump(config)
    }

    fun rename(raw: String, oldName: String, newName: String): String {
        val name = newName.trim()
        require(name.length in 1..100 && name.none { it.isISOControl() || it == ',' }) { "节点名称必须为 1–100 个字符，且不能含逗号或控制字符" }
        val config = parse(raw)
        val nodes = entries(config, "proxies")
        require(!isReserved(oldName) && nodes.singleOrNull { it["name"] == oldName } != null) { "找不到唯一的内联节点" }
        if (oldName == name) return raw
        require(name !in usedNames(config) && !isReserved(name)) { "节点名称已存在或为保留名称" }
        nodes.forEach { node ->
            if (node["name"] == oldName) node["name"] = name
            replaceField(node, "dialer-proxy", oldName, name)
        }
        config["proxies"] = nodes
        val groups = entries(config, "proxy-groups")
        groups.forEach { group ->
            (group["proxies"] as? List<*>)?.let { group["proxies"] = it.map { v -> if (v == oldName) name else v } }
        }
        if ("proxy-groups" in config) config["proxy-groups"] = groups
        replaceReferences(config, oldName, name)
        return dump(config)
    }

    private fun replaceReferences(config: MutableMap<Any?, Any?>, oldName: String, name: String) {
        config["proxies"] = entries(config, "proxies").onEach { replaceField(it, "dialer-proxy", oldName, name) }
        if ("listeners" in config) config["listeners"] = entries(config, "listeners").onEach { replaceField(it, "proxy", oldName, name) }
        for (key in listOf("proxy-providers", "rule-providers")) {
            (config[key] as? Map<*, *>)?.let { providers ->
                config[key] = providers.mapValues { (_, source) ->
                    map(source).apply {
                        replaceField(this, "proxy", oldName, name)
                        (get("override") as? Map<*, *>)?.let { put("override", map(it).apply { replaceField(this, "dialer-proxy", oldName, name) }) }
                    }
                }
            }
        }
        (config["rules"] as? List<*>)?.let { config["rules"] = rules(it, oldName, name) }
        (config["sub-rules"] as? Map<*, *>)?.let { sub -> config["sub-rules"] = sub.mapValues { (_, values) -> if (values is List<*>) rules(values, oldName, name) else values } }
    }

    private fun rules(values: List<*>, old: String, name: String): List<Any?> = values.map { raw ->
        if (raw !is String) return@map raw
        val kind = raw.substringBefore(',').trim().uppercase()
        if (kind == "SUB-RULE") return@map raw
        val fields = mutableListOf<String>()
        var depth = 0
        var start = 0
        val logical = kind in setOf("AND", "OR", "NOT")
        raw.forEachIndexed { index, char ->
            when {
                logical && char == '(' -> depth++
                logical && char == ')' -> depth--
                char == ',' && depth == 0 -> { fields += raw.substring(start, index); start = index + 1 }
            }
        }
        fields += raw.substring(start)
        val target = if (kind == "MATCH") 1 else 2
        if (fields.getOrNull(target)?.trim() != old) return@map raw
        fields[target] = name
        fields.joinToString(",")
    }

    private fun replaceField(map: MutableMap<Any?, Any?>, key: String, old: String, name: String) { if (map[key] == old) map[key] = name }
    private fun usedNames(config: Map<*, *>) = (entries(config, "proxies") + entries(config, "proxy-groups")).mapNotNull { it["name"] as? String }.toSet()
    private fun entries(config: Map<*, *>, key: String): List<MutableMap<Any?, Any?>> {
        if (key in config && config[key] !is List<*>) error("配置项 $key 必须为列表")
        return (config[key] as? List<*>)?.map(::map) ?: emptyList()
    }
    private fun parse(raw: String) = map(Yaml(SafeConstructor(LoaderOptions().apply { isAllowDuplicateKeys = false; codePointLimit = SUBSCRIPTION_MAX_BYTES })).load<Any?>(raw))
    private fun map(value: Any?): MutableMap<Any?, Any?> {
        require(value is Map<*, *>) { "配置必须为有效的 YAML 映射" }
        return LinkedHashMap<Any?, Any?>().apply { putAll(value) }
    }
    private fun dump(config: Map<*, *>): String = Yaml(DumperOptions().apply { defaultFlowStyle = DumperOptions.FlowStyle.BLOCK }).dump(config).also {
        require(it.toByteArray(Charsets.UTF_8).size <= SUBSCRIPTION_MAX_BYTES) { "内容超过 10 MiB 限制" }
    }
}
