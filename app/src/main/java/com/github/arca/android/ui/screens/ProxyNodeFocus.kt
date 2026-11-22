package com.github.arca.android.ui.screens

import com.github.kr328.clash.core.model.ProxyGroup

/** Names are the actual names saved by the editor, including collision suffixes. */
data class ProxyNodeFocusRequest(
    val profileId: String,
    val groupName: String?,
    val nodeNames: List<String>
)

internal data class ProxyNodeFocusTarget(val groupName: String, val nodeName: String, val nodeIndex: Int)

/** A requested group is only a preference: it must really contain a newly saved node. */
internal fun findProxyNodeFocusTarget(
    request: ProxyNodeFocusRequest?,
    groups: List<String>,
    groupMap: Map<String, ProxyGroup>
): ProxyNodeFocusTarget? {
    if (request == null || request.nodeNames.isEmpty()) return null
    val visible = groups.filter { groupMap[it]?.hidden == false }
    val ordered = listOfNotNull(request.groupName?.takeIf { it in visible }) + visible
    for (name in ordered.distinct()) {
        val proxies = groupMap[name]?.proxies.orEmpty()
        for (nodeName in request.nodeNames) {
            val index = proxies.indexOfFirst { it.name == nodeName && !it.isGroup }
            if (index >= 0) return ProxyNodeFocusTarget(name, nodeName, index)
        }
    }
    return null
}

/** LIST has one header per visible group and node rows only for expanded groups. */
internal fun proxyNodeFocusListIndex(
    target: ProxyNodeFocusTarget,
    groups: List<String>,
    groupMap: Map<String, ProxyGroup>,
    expandedGroups: Map<String, Boolean>,
    columns: Int
): Int {
    val count = columns.coerceIn(1, 3)
    val priorItems = groups.filter { groupMap[it]?.hidden != true }
        .takeWhile { it != target.groupName }.sumOf { name ->
            1 + if (expandedGroups[name] == true) {
                (groupMap[name]?.proxies.orEmpty().size + count - 1) / count
            } else 0
        }
    return priorItems + 1 + target.nodeIndex / count
}
