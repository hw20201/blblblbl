package com.github.mihomo.android.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quickjs.JSContext
import com.quickjs.QuickJS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Requires an ARM64 Android device; compilation alone is not runtime acceptance. */
@RunWith(AndroidJUnit4::class)
class QuickJsGuardInstrumentedTest {
    private fun <T> guarded(timeout: Long = 3_000L, memory: Long = 64L * 1024 * 1024, block: (JSContext) -> T): T {
        val runtime = QuickJS.createRuntime()
        try {
            val context = runtime.createContext()
            try {
                return QuickJsGuard.install(context, timeout, memory).use { block(context) }
            } finally { context.close() }
        } finally { runtime.close() }
    }

    @Test fun preservesNullishAndOptionalChaining() = guarded { context ->
        assertEquals("fallback", context.executeStringScript("null ?? 'fallback'", "nullish.js"))
        assertEquals("undefined", context.executeStringScript("var x=null; String(x?.value)", "optional.js"))
        assertEquals("node", context.executeStringScript("var c={proxies:[{name:'node'}]}; c.proxies?.[0]?.name ?? 'none'", "profile.js"))
    }

    @Test(timeout = 5_000) fun nativeInfiniteLoopHitsShortDeadline() = guarded(250) { context ->
        val start = System.nanoTime()
        assertThrows(Exception::class.java) { context.executeVoidScript("while(true) {}", "deadline.js") }
        assertTrue(System.nanoTime() - start < 3_000_000_000L)
    }

    @Test(timeout = 5_000) fun interruptionEscapesTryCatchLoop() = guarded(250) { context ->
        assertThrows(Exception::class.java) {
            context.executeVoidScript("while(true) { try { while(true) {} } catch(e) {} }", "caught-deadline.js")
        }
        Unit
    }

    @Test(timeout = 10_000) fun memoryGrowthFailsUnderGuard() = guarded(8_000, 8L * 1024 * 1024) { context ->
        val start = System.nanoTime()
        val error = assertThrows(Exception::class.java) {
            context.executeVoidScript("var a=[]; while(true) a.push('x'.repeat(65536))", "memory-limit.js")
        }
        assertTrue("Expected allocation failure before the deadline: $error", System.nanoTime() - start < 7_000_000_000L)
        assertTrue(error.message.orEmpty().contains("memory", ignoreCase = true))
    }

    @Test fun closeGuardBeforeContextAndRunAgainOnOwnerThread() {
        val runtime = QuickJS.createRuntime()
        try {
            val context = runtime.createContext()
            try {
                val guard = QuickJsGuard.install(context)
                guard.close()
                guard.close() // Idempotent: never clear/free a native handle twice.
                assertEquals("7", context.executeStringScript("String(7)", "after-clear.js"))
            } finally { context.close() }
        } finally { runtime.close() }
        guarded { context -> assertEquals("8", context.executeStringScript("String(8)", "next-runtime.js")) }
    }
}
