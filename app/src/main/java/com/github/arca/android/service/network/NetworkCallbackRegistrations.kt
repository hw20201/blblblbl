package com.github.arca.android.service.network

/** Install callbacks as a unit, rolling back a partial installation and closing exactly once. */
internal fun registerNetworkCallbacks(
    registrations: List<() -> AutoCloseable>
): AutoCloseable {
    val installed = mutableListOf<AutoCloseable>()
    try {
        registrations.forEach { installed.add(it()) }
    } catch (failure: Exception) {
        installed.asReversed().forEach { registration ->
            try {
                registration.close()
            } catch (cleanupFailure: Exception) {
                failure.addSuppressed(cleanupFailure)
            }
        }
        throw failure
    }
    return object : AutoCloseable {
        private var closed = false

        override fun close() {
            if (closed) return
            closed = true
            var failure: Exception? = null
            installed.asReversed().forEach { registration ->
                try {
                    registration.close()
                } catch (cleanupFailure: Exception) {
                    val firstFailure = failure
                    if (firstFailure == null) failure = cleanupFailure
                    else firstFailure.addSuppressed(cleanupFailure)
                }
            }
            failure?.let { throw it }
        }
    }
}
