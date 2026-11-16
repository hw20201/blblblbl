package com.github.arca.android.ui.screens

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.github.arca.android.core.model.ConnectionInfo
import com.github.arca.android.core.model.ConnectionSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** The screen owns this job: hiding it, pausing, or stopping the VPN cancels its next sample. */
internal suspend fun pollConnectionsWhileStarted(
    lifecycle: Lifecycle,
    paused: Flow<Boolean>,
    querySnapshot: suspend () -> ConnectionSnapshot?,
    onConnections: (List<ConnectionInfo>) -> Unit,
    onUnsupported: () -> Unit,
    workerDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        paused.collectLatest { isPaused ->
            if (isPaused) return@collectLatest
            while (currentCoroutineContext().isActive) {
                // JNI, JSON decoding, and sorting all stay off the UI thread. Cancellation of
                // a blocking native query discards its result before publishing back to Main.
                val connections = withContext(workerDispatcher) {
                    val snapshot = querySnapshot() ?: return@withContext null
                    snapshot.connections.orEmpty().sortedByDescending { it.start }
                }
                if (connections == null) {
                    onUnsupported()
                    return@collectLatest
                }
                onConnections(connections)
                delay(1000)
            }
        }
    }
}
