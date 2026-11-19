package com.github.arca.android.data.subscription

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import com.github.kr328.clash.core.bridge.SubscriptionInspection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.Executors

private const val INSPECT_DESCRIPTOR = "com.github.arca.android.SubscriptionInspection.v2"
private const val RESULT_DESCRIPTOR = "com.github.arca.android.SubscriptionInspectionResult.v2"
private const val TRANSACTION = IBinder.FIRST_CALL_TRANSACTION
private const val MAX_RESULT_BYTES = SUBSCRIPTION_MAX_BYTES * 6
private const val BIND_TIMEOUT_MS = 10_000L
private const val INSPECT_TIMEOUT_MS = 25_000L
private val inputModes = setOf("auto", "yaml", "uri", "base64", "proxy")

/** Isolated UID, no INTERNET, and no app data access beyond the two scratch descriptors. */
class SubscriptionValidationService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val requestLock = Any()
    private var accepting = false
    private var running = false
    private val watchdog = Runnable {
        synchronized(requestLock) { if (running) Process.killProcess(Process.myPid()) }
    }

    override fun onBind(intent: Intent): IBinder {
        synchronized(requestLock) { accepting = true }
        return endpoint
    }

    override fun onUnbind(intent: Intent): Boolean {
        synchronized(requestLock) {
            accepting = false
            // Native parsing mutates globals and may create resources on failed parses.
            // Dispose the whole isolated process on success as well as on cancellation.
            Process.killProcess(Process.myPid())
        }
        return false
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private val endpoint = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != TRANSACTION) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(INSPECT_DESCRIPTOR)
            val callback = data.readStrongBinder()
            val mode = data.readString()
            val input = data.readFileDescriptor() ?: return true
            val output = data.readFileDescriptor()
            if (callback == null || mode !in inputModes || output == null) {
                input.close()
                output?.close()
                if (callback != null) sendResult(callback, false)
                return true
            }
            synchronized(requestLock) {
                if (!accepting || running) {
                    input.close()
                    output.close()
                    sendResult(callback, false)
                    return true
                }
                running = true
                main.postDelayed(watchdog, INSPECT_TIMEOUT_MS + 2_000)
                worker.execute { inspect(input, output, requireNotNull(mode), callback) }
            }
            return true
        }
    }

    private fun inspect(input: ParcelFileDescriptor, output: ParcelFileDescriptor, mode: String, callback: IBinder) {
        val ok = try {
            ParcelFileDescriptor.AutoCloseOutputStream(output).use { sink ->
                val text = ParcelFileDescriptor.AutoCloseInputStream(input).use { readBounded(it) }
                require('\u0000' !in text)
                val result = SubscriptionInspection.nativeInspect(text, mode, assets).toByteArray(Charsets.UTF_8)
                require(result.size <= MAX_RESULT_BYTES)
                sink.write(result)
            }
            true
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            false
        } finally {
            runCatching { input.close() }
            runCatching { output.close() }
            synchronized(requestLock) {
                main.removeCallbacks(watchdog)
                running = false
            }
        }
        sendResult(callback, ok)
    }

    private fun sendResult(callback: IBinder, ok: Boolean) {
        val response = Parcel.obtain()
        try {
            response.writeInterfaceToken(RESULT_DESCRIPTOR)
            response.writeInt(if (ok) 1 else 0)
            runCatching { callback.transact(TRANSACTION, response, null, IBinder.FLAG_ONEWAY) }
        } finally {
            response.recycle()
        }
    }
}

internal fun readBounded(input: InputStream, limit: Int = SUBSCRIPTION_MAX_BYTES): String {
    val bytes = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count == -1) break
        require(bytes.size().toLong() + count <= limit) { "内容超过大小限制" }
        bytes.write(buffer, 0, count)
    }
    return Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
}

object SubscriptionValidator {
    // Mihomo parsing temporarily changes core globals, even without applying a VPN config.
    private val mutex = Mutex()

    suspend fun inspect(context: Context, text: String, mode: String = "auto"): ImportInspection = mutex.withLock {
        require('\u0000' !in text) { "内容含有 NUL 字符，无法作为文本导入" }
        require(mode in inputModes) { "导入格式无效" }
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= SUBSCRIPTION_MAX_BYTES) { "内容超过 10 MiB 限制" }
        withContext(Dispatchers.IO) { inspectInService(context.applicationContext, bytes, mode) }
    }

    private suspend fun inspectInService(context: Context, bytes: ByteArray, mode: String): ImportInspection {
        val ready = CompletableDeferred<IBinder>()
        val result = CompletableDeferred<Boolean>()
        val callback = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code != TRANSACTION) return super.onTransact(code, data, reply, flags)
                data.enforceInterface(RESULT_DESCRIPTOR)
                result.complete(data.readInt() == 1)
                return true
            }
        }
        fun disconnected() {
            val failure = IllegalStateException("校验进程已断开，请重试")
            ready.completeExceptionally(failure)
            result.completeExceptionally(failure)
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { ready.complete(service) }
            override fun onServiceDisconnected(name: ComponentName) = disconnected()
            override fun onNullBinding(name: ComponentName) = disconnected()
            override fun onBindingDied(name: ComponentName) = disconnected()
        }
        val directory = File(context.cacheDir, "subscription-preview").apply { mkdirs() }
        val input = File.createTempFile("input-", ".tmp", directory)
        var output: File? = null
        var bound = false
        try {
            val outputFile = File.createTempFile("result-", ".tmp", directory).also { output = it }
            input.writeBytes(bytes)
            ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { source ->
                ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { reader ->
                    ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_WRITE_ONLY).use { sink ->
                        // Keep only open descriptors during parsing; no raw preview remains on process death.
                        check(input.delete() && outputFile.delete()) { "无法准备独立校验" }
                        bound = context.bindService(Intent(context, SubscriptionValidationService::class.java),
                            connection, Context.BIND_AUTO_CREATE)
                        check(bound) { "无法启动独立校验服务" }
                        val service = withTimeoutOrNull(BIND_TIMEOUT_MS) { ready.await() }
                            ?: error("独立校验启动超时，请重试")
                        val request = Parcel.obtain()
                        try {
                            request.writeInterfaceToken(INSPECT_DESCRIPTOR)
                            request.writeStrongBinder(callback)
                            request.writeString(mode)
                            request.writeFileDescriptor(source.fileDescriptor)
                            request.writeFileDescriptor(sink.fileDescriptor)
                            check(service.transact(TRANSACTION, request, null, IBinder.FLAG_ONEWAY)) { "核心校验接口不匹配" }
                        } finally {
                            request.recycle()
                        }
                        val ok = withTimeoutOrNull(INSPECT_TIMEOUT_MS) { result.await() }
                            ?: error("独立校验超时，请缩小来源后重试")
                        check(ok) { "独立校验失败，请检查输入或重试" }
                    }
                    val json = ParcelFileDescriptor.AutoCloseInputStream(reader).use { readBounded(it, MAX_RESULT_BYTES) }
                    return subscriptionJson.decodeFromString<ImportInspection>(json).also {
                        check(it.contract == 1) { "核心校验接口版本不匹配" }
                    }
                }
            }
        } finally {
            if (bound) context.unbindService(connection)
            input.delete()
            output?.delete()
        }
    }
}
