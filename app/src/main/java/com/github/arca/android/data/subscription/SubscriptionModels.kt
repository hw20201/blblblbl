package com.github.arca.android.data.subscription

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal val subscriptionJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
const val SUBSCRIPTION_MAX_BYTES = 10 * 1024 * 1024
const val SUBSCRIPTION_MAX_SOURCES = 20
const val SUBSCRIPTION_MAX_NODES = 10_000

@Serializable
data class ImportDiagnostic(val index: Int = 0, val line: Int = 0, val code: String, val message: String)

@Serializable
data class ImportNode(val index: Int, val line: Int = 0, val name: String, val type: String, val data: JsonObject)

@Serializable
data class ImportInspection(
    val contract: Int = 1,
    val format: String = "unknown",
    val nodes: List<ImportNode> = emptyList(),
    val diagnostics: List<ImportDiagnostic> = emptyList(),
    val warnings: List<String> = emptyList(),
    val complete: String = "",
    val completeValid: Boolean = false
) {
    val usable: Boolean get() = nodes.isNotEmpty() || completeValid
    val formatLabel: String get() = when (format) {
        "mihomo" -> "Mihomo 配置"
        "yaml" -> "节点 YAML"
        "uri" -> "节点链接"
        "base64" -> "Base64 订阅"
        else -> "未识别"
    }
}

/** URL and token are private data; UI and diagnostics display the user-assigned label only. */
@Serializable
data class SubscriptionSource(
    val id: String,
    val name: String,
    val url: String? = null,
    val token: String = "",
    val inputMode: String = "auto",
    val enabled: Boolean = true,
    val prefix: String = "",
    val include: String = "",
    val exclude: String = ""
)

@Serializable
data class SourceSnapshot(
    val source: SubscriptionSource,
    val text: String,
    val inspection: ImportInspection,
    val fetchedAt: Long = System.currentTimeMillis(),
    val etag: String? = null,
    val lastModified: String? = null,
    val userInfo: String? = null,
    val status: String = "已校验",
    val retained: Boolean = false,
    val attemptedAt: Long = fetchedAt
)

data class SourceDraft(
    val name: String,
    val text: String,
    val mode: String = "auto",
    val token: String = "",
    val remote: Boolean? = null
)

data class ImportPreview(val sources: List<SourceSnapshot>, val failures: List<ImportDiagnostic> = emptyList()) {
    val nodes: Int get() = sources.sumOf { it.inspection.nodes.size }
    val rejected: Int get() = failures.size + sources.sumOf {
        it.inspection.diagnostics.size.coerceAtLeast(if (it.inspection.usable) 0 else 1)
    }
}

@Serializable
data class SubscriptionVersion(
    val name: String,
    val standalone: Boolean,
    val sources: List<SourceSnapshot>,
    val content: String,
    val generatedAt: Long = System.currentTimeMillis(),
    val nodeNames: Map<String, String> = emptyMap(),
    val nodeCount: Int = 0,
    val duplicateCount: Int = 0,
    val versionId: String = java.util.UUID.randomUUID().toString()
)

@Serializable
data class ManagedSubscription(
    val schema: Int = 1,
    val profileId: String,
    val current: SubscriptionVersion,
    val previous: SubscriptionVersion? = null
)

data class SourceUpdate(val sourceId: String, val name: String, val message: String, val retained: Boolean = false)
data class SubscriptionUpdate(val changed: Boolean, val sources: List<SourceUpdate>)
data class AggregationOutput(val yaml: String, val names: Map<String, String>, val nodes: Int, val duplicates: Int)
