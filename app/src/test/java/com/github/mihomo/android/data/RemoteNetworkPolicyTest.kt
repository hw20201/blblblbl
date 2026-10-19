package com.github.mihomo.android.data

import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

class RemoteNetworkPolicyTest {
    @Test
    fun `network interceptor refuses a remote cleartext redirect before proceeding`() {
        val chain = RecordingChain("http://evil.example/redirected?token=private")
        val error = runCatching { secureRemoteUrlInterceptor.intercept(chain) }.exceptionOrNull()
        assertTrue(error is IOException)
        assertFalse(chain.proceeded)
        assertFalse(error!!.message.orEmpty().contains("private"))
    }

    @Test
    fun `network interceptor allows secure and exact loopback destinations`() {
        listOf("https://example.com/sub", "http://localhost:7890/config", "http://127.0.0.1:7890/config")
            .forEach { url ->
                val chain = RecordingChain(url)
                assertEquals(200, secureRemoteUrlInterceptor.intercept(chain).code)
                assertTrue(chain.proceeded)
            }
    }

    @Test
    fun `network security XML denies global cleartext and permits only exact loopback hosts`() {
        val file = listOf(File("src/main/res/xml/network_security_config.xml"),
            File("app/src/main/res/xml/network_security_config.xml")).first { it.isFile }
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val base = xml.getElementsByTagName("base-config").item(0) as org.w3c.dom.Element
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        val domains = xml.getElementsByTagName("domain")
        val names = (0 until domains.length).map {
            val domain = domains.item(it) as org.w3c.dom.Element
            assertEquals("false", domain.getAttribute("includeSubdomains"))
            domain.textContent.trim()
        }.toSet()
        assertEquals(setOf("127.0.0.1", "localhost"), names)
    }

    private class RecordingChain(url: String) : Interceptor.Chain {
        private val req = Request.Builder().url(url).build()
        var proceeded = false
        override fun request() = req
        override fun proceed(request: Request): Response {
            proceeded = true
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").build()
        }
        override fun connection(): Connection? = null
        override fun call(): Call = error("No call needed")
        override fun connectTimeoutMillis() = 1000
        override fun readTimeoutMillis() = 1000
        override fun writeTimeoutMillis() = 1000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}
