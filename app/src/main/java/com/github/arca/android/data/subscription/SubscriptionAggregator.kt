package com.github.arca.android.data.subscription

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.security.MessageDigest

/** Pure generation: no source rules/DNS/scripts are mixed into an aggregate. */
object SubscriptionAggregator {
    fun fingerprint(data: JsonObject): String = digest(canonical(JsonObject(data - "name")))

    fun generate(sources: List<SourceSnapshot>, previousNames: Map<String, String> = emptyMap()): AggregationOutput {
        require(sources.size <= SUBSCRIPTION_MAX_SOURCES) { "最多添加 20 个来源" }
        val enabled = sources.filter { it.source.enabled }
        require(enabled.isNotEmpty()) { "至少启用一个来源" }
        val nodes = linkedMapOf<String, Map<String, Any?>>()
        val names = linkedMapOf<String, String>()
        val groupMembers = linkedMapOf<String, MutableSet<String>>()
        val reserved = mutableSetOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "GLOBAL", "节点选择")
        var duplicates = 0
        var count = 0
        val groupNames = enabled.associate { it.source.id to "来源 · ${safeName(it.source.name)} · ${it.source.id}" }
        reserved += groupNames.values
        for (snapshot in enabled) {
            val source = snapshot.source
            val groupName = groupNames.getValue(source.id)
            val members = groupMembers.getOrPut(groupName) { linkedSetOf() }
            val include = source.include.split(',', '，').map(String::trim).filter(String::isNotEmpty)
            val exclude = source.exclude.split(',', '，').map(String::trim).filter(String::isNotEmpty)
            for (node in snapshot.inspection.nodes) {
                count++
                require(count <= SUBSCRIPTION_MAX_NODES) { "聚合最多处理 10,000 个节点" }
                if (include.isNotEmpty() && include.none { node.name.contains(it, ignoreCase = true) }) continue
                if (exclude.any { node.name.contains(it, ignoreCase = true) }) continue
                require(node.data["dialer-proxy"]?.let { it is JsonNull || (it as? JsonPrimitive)?.contentOrNull.isNullOrEmpty() } != false) {
                    "${source.name} 含链式依赖，请保留为完整独立配置"
                }
                val id = fingerprint(node.data)
                if (id in names) duplicates++
                val assigned = names[id] ?: run {
                    val label = previousNames[id]?.takeUnless { it in reserved }
                        ?: "${safeName(source.prefix + node.name)} · ${id.take(8)}"
                    var unique = label
                    var suffix = 2
                    while (unique in reserved) unique = "$label · ${suffix++}"
                    reserved += unique
                    names[id] = unique
                    val native = node.data.mapValues { nativeValue(it.value) }.toMutableMap()
                    native["name"] = unique
                    nodes[id] = native
                    unique
                }
                members += assigned
            }
        }
        require(nodes.isNotEmpty()) { "筛选后没有可用节点；请调整来源或筛选条件" }
        val groups = mutableListOf<Map<String, Any?>>(
            linkedMapOf("name" to "节点选择", "type" to "select", "proxies" to names.values.toList())
        )
        for ((groupName, members) in groupMembers) {
            if (members.isNotEmpty()) groups += linkedMapOf("name" to groupName, "type" to "select", "proxies" to members.toList())
        }
        val output = linkedMapOf<String, Any?>(
            "proxies" to nodes.values.toList(),
            "proxy-groups" to groups,
            "rules" to listOf("MATCH,节点选择")
        )
        val yaml = Yaml(DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
        }).dump(output)
        require(yaml.toByteArray(Charsets.UTF_8).size <= SUBSCRIPTION_MAX_BYTES) { "生成配置超过 10 MiB" }
        return AggregationOutput(yaml, names, nodes.size, duplicates)
    }

    private fun safeName(name: String): String = name.filterNot { it.isISOControl() }.take(100).ifBlank { "节点" }

    private fun nativeValue(value: JsonElement): Any? = when (value) {
        is JsonNull -> null
        is JsonObject -> value.mapValues { nativeValue(it.value) }
        is JsonArray -> value.map { nativeValue(it) }
        is JsonPrimitive -> if (value.isString) value.content else value.booleanOrNull ?: value.longOrNull
            ?: value.content.toDoubleOrNull() ?: value.content
    }

    internal fun canonical(value: JsonElement): String = when (value) {
        is JsonObject -> value.entries.sortedBy { it.key }.joinToString(",", "{", "}") { (key, item) ->
            JsonPrimitive(key).toString() + ":" + canonical(item)
        }
        is JsonArray -> value.joinToString(",", "[", "]") { canonical(it) }
        else -> value.toString()
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
