package com.github.mihomo.android.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import java.io.IOException

/** Remote subscription/script URLs require TLS; only exact local hosts may use HTTP. */
internal fun requireSecureRemoteUrl(url: String) {
    val scheme = url.substringBefore("://", "").lowercase()
    check(scheme == "https" || scheme == "http") { "不支持的地址协议" }
    val parsed = url.toHttpUrlOrNull()
    check(parsed != null) { "地址格式无效" }
    check(parsed.isHttps || isLoopbackUrl(url)) {
        "订阅与脚本地址必须使用 HTTPS；明文 HTTP 仅允许本机回环地址（127.0.0.1 / localhost）"
    }
}

/** Use the same parser as OkHttp, never a hostname prefix or a hand-parsed authority. */
internal fun isLoopbackUrl(url: String): Boolean {
    val host = url.toHttpUrlOrNull()?.host ?: return false
    return host == "127.0.0.1" || host == "localhost"
}

/** A network interceptor also checks redirect destinations, including requests via local proxy. */
internal val secureRemoteUrlInterceptor = Interceptor { chain ->
    try {
        requireSecureRemoteUrl(chain.request().url.toString())
    } catch (e: IllegalStateException) {
        throw IOException(e.message, e)
    }
    chain.proceed(chain.request())
}
