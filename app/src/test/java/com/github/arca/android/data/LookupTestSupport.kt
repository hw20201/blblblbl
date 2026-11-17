package com.github.arca.android.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.zip.GZIPOutputStream

internal const val IP_API_OK =
    """{"status":"success","country":"美国","city":"芝加哥","isp":"Cloudflare, Inc.","org":"Cloudflare WARP","as":"AS13335 Cloudflare, Inc.","query":"203.0.113.5"}"""
internal const val GEOJS_OK =
    """{"ip":"198.51.100.2","country":"United States","city":"Morgan Hill","organization":"AS13335 Cloudflare, Inc.","organization_name":"Cloudflare, Inc.","asn":13335}"""

internal fun fakeResponse(request: Request, code: Int, body: String, vararg headers: Pair<String, String>): Response =
    Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code)
        .message(if (code in 200..299) "OK" else "Failure")
        .apply { headers.forEach { (k, v) -> header(k, v) } }
        .body(body.toResponseBody()).build()

/** Client whose only behaviour is [handler]; no network is ever touched. */
internal fun fakeClient(handler: (Interceptor.Chain) -> Response): OkHttpClient =
    OkHttpClient.Builder().addInterceptor { handler(it) }.build()

internal fun repo(
    client: OkHttpClient,
    exitTimeoutMillis: Long = EXIT_STAGE_TIMEOUT_MILLIS,
    riskTimeoutMillis: Long = RISK_STAGE_TIMEOUT_MILLIS
) = OutboundIpRepository(
    client, InetSocketAddress(VPN_LOCAL_PROXY_HOST, VPN_LOCAL_PROXY_PORT), okhttp3.Dns.SYSTEM,
    exitTimeoutMillis, riskTimeoutMillis
)

internal fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { out ->
    GZIPOutputStream(out).use { it.write(text.toByteArray()) }
}.toByteArray()

internal class RawReply(val status: Int, val headers: List<Pair<String, String>> = emptyList(), val body: ByteArray = ByteArray(0))

/**
 * Minimal blocking HTTP/1.1 server on loopback (JDK sockets only; MockWebServer is not a test
 * dependency). Works as an origin or as a plain HTTP proxy: every request line is recorded.
 */
internal class TinyHttpServer(private val reply: (requestLine: String, headers: Map<String, String>) -> RawReply) : AutoCloseable {
    private val server = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
    val requestLines: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val port: Int get() = server.localPort
    val address: InetSocketAddress get() = InetSocketAddress("127.0.0.1", server.localPort)

    init {
        Thread {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                Thread { serve(socket) }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true; start() }
    }

    private fun serve(socket: Socket) { socket.use {
        try {
            val input = it.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val line = input.readLine() ?: return@use
            val headers = linkedMapOf<String, String>()
            while (true) {
                val header = input.readLine() ?: break
                if (header.isEmpty()) break
                headers[header.substringBefore(':').lowercase()] = header.substringAfter(':').trim()
            }
            requestLines += line
            val r = reply(line, headers)
            val out = it.getOutputStream()
            val head = StringBuilder("HTTP/1.1 ${r.status} X\r\n")
            r.headers.forEach { (k, v) -> head.append("$k: $v\r\n") }
            head.append("Content-Length: ${r.body.size}\r\nConnection: close\r\n\r\n")
            out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
            out.write(r.body)
            out.flush()
        } catch (_: Exception) {
        }
    } }

    override fun close() { server.close() }
}

/** Application interceptor that redirects whatever the repository asks for to a local origin. */
internal fun rewriteTo(port: Int) = Interceptor { chain ->
    val request = chain.request()
    chain.proceed(request.newBuilder().url(request.url.newBuilder().scheme("http").host("127.0.0.1").port(port).build()).build())
}

/** A loopback port that nothing listens on (the proxy is "down"). */
internal fun closedPort(): Int = ServerSocket(0).use { it.localPort }

internal class RecordingDns(private val fail: Boolean) : okhttp3.Dns {
    val lookups: MutableList<String> = Collections.synchronizedList(mutableListOf())
    override fun lookup(hostname: String): List<java.net.InetAddress> {
        lookups += hostname
        if (fail || hostname != "127.0.0.1") throw java.net.UnknownHostException("test")
        return listOf(java.net.InetAddress.getByName("127.0.0.1"))
    }
}
