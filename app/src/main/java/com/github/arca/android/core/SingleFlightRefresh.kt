package com.github.arca.android.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex

/** The caller owns cancellation; concurrent triggers are coalesced, never queued. */
internal class SingleFlightRefresh {
    private val mutex = Mutex()

    suspend fun <K, T> run(
        currentKey: () -> K,
        load: suspend () -> T,
        canPublish: () -> Boolean,
        publish: (T) -> Unit
    ): Boolean {
        if (!mutex.tryLock()) return false
        try {
            val key = currentKey()
            val value = load()
            currentCoroutineContext().ensureActive()
            if (currentKey() != key || !canPublish()) return false
            publish(value)
            return true
        } finally {
            mutex.unlock()
        }
    }
}
