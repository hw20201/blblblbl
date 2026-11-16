package com.github.arca.android.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real sockets: transparent gzip is decoded exactly once, and the 1 MiB cap applies to decoded text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScamalyticsWebTransportTest {
    private val ip = "203.0.113.5"
    private val html = """<html><head><title>$ip IP Fraud Risk</title></head><body><h1>$ip</h1>
        <div>Fraud Score: 31</div><div>Medium Risk</div>
        <table><tr><td>Datacenter</td><td>No</td></tr><tr><td>Tor Exit Node</td><td>Yes</td></tr></table></body></html>"""

    private fun repositoryFor(server: TinyHttpServer) =
        repo(OkHttpClient.Builder().addInterceptor(rewriteTo(server.port)).build())

    @Test fun plainHtmlIsParsed() = runBlocking {
        TinyHttpServer { _, _ -> RawReply(200, listOf("Content-Type" to "text/html; charset=utf-8"), html.toByteArray()) }.use { server ->
            val info = repositoryFor(server).lookupScamalyticsWeb(ip)
            assertEquals(31, info.score); assertEquals("medium", info.risk)
            assertTrue(server.requestLines.single().startsWith("GET /ip/$ip"))
        }
    }

    @Test fun gzipBodyIsDecodedOnceByOkHttp() = runBlocking {
        var acceptEncoding: String? = null
        TinyHttpServer { _, headers ->
            acceptEncoding = headers["accept-encoding"]
            RawReply(200, listOf("Content-Type" to "text/html", "Content-Encoding" to "gzip"), gzip(html))
        }.use { server ->
            val info = repositoryFor(server).lookupScamalyticsWeb(ip)
            assertEquals(31, info.score)
            assertEquals(true, (info.hostingProxySignals + info.blacklistMarkers).first { it.label == "Tor" }.detected)
            assertEquals("gzip", acceptEncoding)
        }
    }

    @Test fun oversizedPlainAndGzipBombBodiesAreFailures() = runBlocking {
        val big = html + "<!-- " + "x".repeat(SCAMALYTICS_WEB_MAX_BYTES) + " -->"
        TinyHttpServer { _, _ -> RawReply(200, body = big.toByteArray()) }.use { server ->
            val failure = runCatching { repositoryFor(server).lookupScamalyticsWeb(ip) }.exceptionOrNull()
            assertTrue(failure is IpLookupException)
            assertTrue(failure!!.message!!.contains("过大"))
        }
        // ~1 KiB on the wire, > 1 MiB once decoded.
        TinyHttpServer { _, _ -> RawReply(200, listOf("Content-Encoding" to "gzip"), gzip(big)) }.use { server ->
            assertTrue(gzip(big).size < 10_000)
            val failure = runCatching { repositoryFor(server).lookupScamalyticsWeb(ip) }.exceptionOrNull()
            assertTrue(failure is IpLookupException)
            assertTrue(failure!!.message!!.contains("过大"))
        }
    }

    @Test fun exactlyAtTheLimitIsStillAccepted() = runBlocking {
        val pad = SCAMALYTICS_WEB_MAX_BYTES - html.toByteArray().size - "<!---->".length
        val body = html + "<!--" + "x".repeat(pad) + "-->"
        assertEquals(SCAMALYTICS_WEB_MAX_BYTES, body.toByteArray().size)
        TinyHttpServer { _, _ -> RawReply(200, body = body.toByteArray()) }.use { server ->
            assertEquals(31, repositoryFor(server).lookupScamalyticsWeb(ip).score)
        }
    }

    @Test fun deniedAndRateLimitedStatusesAreFixedFailures() = runBlocking {
        listOf(403 to "拒绝", 429 to "频繁").forEach { (code, text) ->
            TinyHttpServer { _, _ -> RawReply(code, body = "secret page $ip".toByteArray()) }.use { server ->
                val failure = runCatching { repositoryFor(server).lookupScamalyticsWeb(ip) }.exceptionOrNull()
                assertTrue(failure is IpLookupException)
                assertTrue(failure!!.message, failure.message!!.contains(text))
                assertFalse(failure.message!!.contains("secret"))
            }
        }
    }
}
