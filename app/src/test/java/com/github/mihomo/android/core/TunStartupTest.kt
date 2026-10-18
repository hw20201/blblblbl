package com.github.mihomo.android.core

import com.github.mihomo.android.data.TunStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TunStartupTest {
    @Test
    fun forwardsEveryStackWithoutSubstitution() {
        for (stack in TunStack.entries) {
            var received: String? = null
            val result = TunStartup.start(stack.key, { 1 }) {
                received = it
                0
            }
            assertEquals(stack.key, received)
            assertEquals(stack, result)
        }
    }

    @Test
    fun mipsFailureDoesNotFallBackOrReportSuccess() {
        val calls = mutableListOf<String>()
        val error = assertThrows(IllegalStateException::class.java) {
            TunStartup.start("mips", { 1 }) {
                calls += it
                1
            }
        }
        assertEquals(listOf("mips"), calls)
        assertTrue(error.message.orEmpty().contains("MIPS"))
        assertTrue(error.message.orEmpty().contains("code 1"))
    }

    @Test
    fun unknownStackNeverReachesJni() {
        var called = false
        assertThrows(IllegalArgumentException::class.java) {
            TunStartup.start("automatic", { called = true; 1 }) { called = true; 0 }
        }
        assertFalse(called)
    }

    @Test
    fun oldBridgeContractNeverReachesStart() {
        var called = false
        assertThrows(IllegalStateException::class.java) {
            TunStartup.start("mips", { 0 }) { called = true; 0 }
        }
        assertFalse(called)
    }

    @Test
    fun missingContractSymbolNeverReachesStart() {
        var called = false
        assertThrows(UnsatisfiedLinkError::class.java) {
            TunStartup.start("mips", { throw UnsatisfiedLinkError("old bridge") }) {
                called = true
                0
            }
        }
        assertFalse(called)
    }

    @Test
    fun everyNonzeroNativeStatusFails() {
        for (status in listOf(-1, 1, 2, 127)) {
            assertThrows(IllegalStateException::class.java) {
                TunStartup.start("mips", { 1 }) { status }
            }
        }
    }
}
