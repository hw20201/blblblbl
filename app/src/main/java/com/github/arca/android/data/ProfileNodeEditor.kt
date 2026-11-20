package com.github.arca.android.data

import com.github.arca.android.data.subscription.SUBSCRIPTION_MAX_BYTES
import kotlinx.serialization.json.JsonObject
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

internal object ProfileNodeEditor {
    private val reserved = setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE", "GLOBAL")

    fun append(raw: String, proxy: JsonObject, name: String): String {
        val config = parse(raw)
        val used = usedNames(config) + reserved
        val base = name.filterNot { it.isISOControl() || it == ',' }.trim().take(100).ifBlank { "节点" }
        val resolved = generateSequence(1) { it + 1 }.map { n -> if (n == 1) base else "-$n".let { base.take(100 - it.length) + it } }.first { it !in used }
        val nodes = entries(config, "proxies")
        require(nodes.size < 10_000) { "节点超过 10,000 条限制" }
        val node = parse(proxy.toString()).apply { put("name", resolved) }
        config["proxies"] = nodes + listOf(node)
        val groups = entries(config, "proxy-groups")
        groups.firstOrNull { it["type"] == "select" }?.let { group ->
            group["proxies"] = ((group["proxies"] as? List<*>) ?: emptyList<Any>()) + resolved
            config["proxy-groups"] = groups
        }
        return dump(config)
    }

    fun rename(raw: String, oldName: String, newName: String): String {
        val name = newName.trim()
        require(name.length in 1..100 && name.none { it.isISOControl() || it == ',' }) { "节点名称必须为 1–100 个字符，且不能含逗号或控制字符" }
        val config = parse(raw)
        val nodes = entries(config, "proxies")
        require(nodes.singleOrNull { it["name"] == oldName } != null) { "找不到唯一的内联节点" }
        if (oldName == name) return raw
        require(name !in usedNames(config) && name !in reserved) { "节点名称已存在或为保留名称" }
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
        return dump(config)
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
