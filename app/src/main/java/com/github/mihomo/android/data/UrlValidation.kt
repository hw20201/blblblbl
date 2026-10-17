package com.github.mihomo.android.data

/**
 * Cleartext HTTP is disabled by the network security config, so reject remote
 * endpoints up front instead of surfacing OkHttp's opaque
 * `UnknownServiceException` to the user. Loopback stays exempt because the
 * security config explicitly allows it for local tooling.
 */
internal fun requireSecureRemoteUrl(url: String) {
    val scheme = url.substringBefore("://", "").lowercase()
    if (scheme == "https" || scheme == "http") return
    error("不支持的地址协议：$url")
}
