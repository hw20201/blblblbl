package com.github.arca.android.service.network

import com.github.arca.android.core.DnsStatePolicy
import com.github.arca.android.core.DnsUpdate
import java.util.concurrent.atomic.AtomicBoolean

/** Implementations accept work asynchronously and execute it serially. close must not join. */
internal interface NetworkObservationWorker : AutoCloseable {
    fun execute(task: () -> Unit)
}

/** All methods except the invalidation callback run on the observation worker. */
internal interface PhysicalNetworkAdapter {
    /** Registration must roll back any partial registration before throwing. */
    fun register(onChanged: () -> Unit): AutoCloseable
    fun snapshot(): PhysicalNetworkSnapshot
}

/** Injectable lifecycle/dispatch seam: queued callbacks never outlive their owning session. */
internal class PhysicalNetworkObservation(
    private val createWorker: () -> NetworkObservationWorker,
    private val createAdapter: () -> PhysicalNetworkAdapter,
    private val onDnsChanged: (DnsUpdate) -> Unit,
    private val onError: (Throwable) -> Unit
) : AutoCloseable {
    private val lifecycleLock = Any()
    @Volatile private var active: Session? = null
    private var closed = false

    private class Session(val worker: NetworkObservationWorker) {
        val dns = DnsStatePolicy()
        var adapter: PhysicalNetworkAdapter? = null
        var registration: AutoCloseable? = null
        var selectedIdentity: String? = null
        val dirty = AtomicBoolean(false)
        val refreshQueued = AtomicBoolean(false)
        var cleanedUp = false
    }

    /** Non-blocking and idempotent. A failed registration may be retried with another start. */
    fun start() {
        synchronized(lifecycleLock) {
            if (closed || active != null) return
            val session = Session(createWorker())
            active = session
            session.worker.execute { initialize(session) }
        }
    }

    private fun initialize(session: Session) {
        if (active !== session) return
        try {
            val adapter = createAdapter()
            session.adapter = adapter
            session.registration = adapter.register { enqueueRefresh(session) }
        } catch (failure: Exception) {
            synchronized(lifecycleLock) {
                if (active === session) active = null
            }
            cleanUp(session)
            report(failure)
            return
        }
        enqueueRefresh(session)
    }

    /** Platform callbacks only invalidate and queue at most one refresh, even during event storms. */
    private fun enqueueRefresh(session: Session) {
        synchronized(lifecycleLock) {
            if (active !== session) return
            session.dirty.set(true)
            if (session.refreshQueued.compareAndSet(false, true)) {
                session.worker.execute { drainRefreshes(session) }
            }
        }
    }

    private fun drainRefreshes(session: Session) {
        try {
            // An invalidation arriving during snapshot/native work requires another final snapshot.
            while (active === session && session.dirty.getAndSet(false)) refresh(session)
        } finally {
            synchronized(lifecycleLock) {
                session.refreshQueued.set(false)
                // Covers an invalidation between the last dirty check and releasing the queue slot.
                if (active === session && session.dirty.get()) enqueueRefresh(session)
            }
        }
    }

    private fun refresh(session: Session) {
        if (active !== session) return
        try {
            val snapshot = session.adapter?.snapshot() ?: return
            if (active !== session) return
            val selected = PhysicalNetworkSelection.select(
                snapshot.candidates, snapshot.activeDefault, session.selectedIdentity
            )
            if (selected != null) session.selectedIdentity = selected.identity
            val update = session.dns.update(selected?.identity, selected?.dnsServers.orEmpty())
            if (update != null && active === session) {
                try {
                    onDnsChanged(update)
                } catch (failure: Exception) {
                    // The consumer may not have accepted this update. Retry on the next event.
                    session.dns.reset()
                    report(failure)
                }
            }
        } catch (failure: Exception) {
            // A transient system query failure must not tear down a healthy registration.
            report(failure)
        }
    }

    /**
     * Terminal and non-blocking. Invalidates queued work immediately; unregisters on the worker.
     * A consumer callback already executing on the worker may finish after close returns.
     */
    override fun close() {
        synchronized(lifecycleLock) {
            if (closed) return
            closed = true
            val session = active
            active = null
            session?.worker?.execute { cleanUp(session) }
        }
    }

    private fun cleanUp(session: Session) {
        if (session.cleanedUp) return
        session.cleanedUp = true
        val registration = session.registration
        session.registration = null
        session.adapter = null
        try {
            registration?.close()
        } catch (failure: Exception) {
            report(failure)
        } finally {
            session.worker.close()
        }
    }

    private fun report(failure: Throwable) {
        // Diagnostic code must not prevent unregistering or kill the serial worker.
        runCatching { onError(failure) }
    }
}
