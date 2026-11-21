package com.github.arca.android.ui

import com.github.arca.android.ui.screens.ProxyNodeFocusRequest
import com.github.kr328.clash.core.model.ProxyGroup
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialize reloads and every selection, including choices that skip a core load. */
internal class PolicyProfileLoadQueue {
    private val mutex = Mutex()

    suspend fun <T> run(
        resolveProfile: suspend () -> T,
        load: suspend (T) -> Result<Unit>,
        commitOnFailure: Boolean = false,
        commit: suspend (T, Result<Unit>) -> Unit = { _, _ -> }
    ): Result<Unit> = mutex.withLock {
        val profile = resolveProfile()
        val result = load(profile)
        // A node edit is already saved: its selected profile must stay visible on load failure.
        if (result.isSuccess || commitOnFailure) commit(profile, result)
        result
    }
}

/** The selected profile can differ from the running core when hot switching is disabled. */
internal fun canUsePolicyRuntime(profileId: String?, loadedProfileId: String?): Boolean =
    profileId != null && profileId == loadedProfileId

internal data class PolicyProxyDisplay(
    val profileId: String? = null,
    val sourceGroups: Map<String, ProxyGroup> = emptyMap(),
    val groups: Map<String, ProxyGroup> = emptyMap(),
    val runtimeProfileId: String? = null
) {
    // Compare the source snapshot, not just its profile id: editing retains the same id.
    fun matches(profileId: String?, parsedGroups: Map<String, ProxyGroup>, runtimeProfileId: String? = null): Boolean =
        this.profileId == profileId && sourceGroups === parsedGroups && this.runtimeProfileId == runtimeProfileId

    fun readyFocus(
        request: ProxyNodeFocusRequest?,
        activeProfileId: String?,
        parsedGroups: Map<String, ProxyGroup>,
        runtimeProfileId: String? = null
    ): ProxyNodeFocusRequest? = request?.takeIf {
        it.profileId == activeProfileId && matches(activeProfileId, parsedGroups, runtimeProfileId)
    }
}
