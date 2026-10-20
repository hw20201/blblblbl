package com.github.mihomo.android.data

import android.util.Log
import com.quickjs.JSContext
import java.io.Closeable

/**
 * Used only with QuickJS.createRuntime(): its EventQueue has no HandlerThread and executes
 * synchronously on the caller/owner thread (verified against the pinned 1.4.6 AAR).
 * A session must be closed on that same thread, before JSContext/QuickJS disposal.
 */
internal object QuickJsGuard {
    private val loaded: Boolean by lazy {
        runCatching { System.loadLibrary("qjsguard") }
            .onFailure { Log.w("QuickJsGuard", "Native guard unavailable; refusing unguarded QuickJS", it) }
            .isSuccess
    }

    fun install(context: JSContext, timeoutMs: Long = 3_000L, memoryBytes: Long = 64L * 1024 * 1024): Closeable {
        require(timeoutMs in 1..30_000L && memoryBytes > 0)
        check(loaded) { "QuickJS 安全守卫不可用，将尝试既有 Rhino 兜底" }
        check(!context.isReleased) { "QuickJS context already closed" }
        val handle = nativeInstall(context, timeoutMs, memoryBytes)
        check(handle != 0L) { "无法安装 QuickJS 安全守卫，将尝试既有 Rhino 兜底" }
        return Session(handle, Thread.currentThread(), context)
    }

    private class Session(private var handle: Long, private val owner: Thread, private val context: JSContext) : Closeable {
        override fun close() {
            check(Thread.currentThread() === owner) { "QuickJS guard must close on its owner thread" }
            if (handle == 0L) return
            check(!context.isReleased) { "Detach QuickJS guard before closing context" }
            check(nativeClear(handle)) { "Could not detach QuickJS guard" }
            handle = 0L
        }
    }

    private external fun nativeInstall(context: JSContext, timeoutMs: Long, memoryBytes: Long): Long
    private external fun nativeClear(handle: Long): Boolean
}
