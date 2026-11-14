package com.github.arca.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors Mihomo's statistic.Snapshot / TrackerInfo JSON (same shape as REST /connections). */
@Serializable
data class ConnectionSnapshot(
    val downloadTotal: Long = 0,
    val uploadTotal: Long = 0,
    val connections: List<ConnectionInfo>? = null
)

@Serializable
data class ConnectionInfo(
    val id: String,
    val metadata: ConnectionMetadata = ConnectionMetadata(),
    val upload: Long = 0,
    val download: Long = 0,
    val start: String = "",
    val chains: List<String> = emptyList(),
    val rule: String = "",
    val rulePayload: String = ""
) {
    /** Host if sniffed/known, else destination IP. */
    val target: String
        get() = metadata.host.ifBlank { metadata.sniffHost }.ifBlank { metadata.destinationIP }
            .let { if (metadata.destinationPort.isNotBlank()) "$it:${metadata.destinationPort}" else it }

    /** Chains are stored leaf-first; the outbound node is the first element. */
    val outbound: String get() = chains.firstOrNull().orEmpty()
}

@Serializable
data class ConnectionMetadata(
    val network: String = "",
    val type: String = "",
    val sourceIP: String = "",
    val destinationIP: String = "",
    val sourcePort: String = "",
    val destinationPort: String = "",
    val host: String = "",
    val sniffHost: String = "",
    val process: String = "",
    val processPath: String = "",
    @SerialName("uid") val uid: Long? = null
)
