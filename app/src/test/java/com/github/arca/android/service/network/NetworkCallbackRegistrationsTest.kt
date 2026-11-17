package com.github.arca.android.service.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class NetworkCallbackRegistrationsTest {
    @Test
    fun bothCallbacksAreRegisteredAndUnregisteredExactlyOnce() {
        val calls = mutableListOf<String>()
        val registration = registerNetworkCallbacks(listOf(
            { calls.add("register-physical"); AutoCloseable { calls.add("unregister-physical") } },
            { calls.add("register-default"); AutoCloseable { calls.add("unregister-default") } }
        ))
        assertEquals(listOf("register-physical", "register-default"), calls)
        registration.close()
        registration.close()
        assertEquals(
            listOf("register-physical", "register-default", "unregister-default", "unregister-physical"), calls
        )
    }

    @Test
    fun failedSecondRegistrationRollsBackThePhysicalCallback() {
        var physicalRegistrations = 0
        var physicalUnregistrations = 0
        val denied = SecurityException("default callback denied")
        try {
            registerNetworkCallbacks(listOf(
                {
                    physicalRegistrations++
                    AutoCloseable { physicalUnregistrations++ }
                },
                { throw denied }
            ))
            fail("Expected registration failure")
        } catch (failure: SecurityException) {
            assertSame(denied, failure)
        }
        assertEquals(1, physicalRegistrations)
        assertEquals(1, physicalUnregistrations)
    }

    @Test
    fun firstRegistrationFailureDoesNotAttemptTheSecond() {
        var secondRegistrations = 0
        try {
            registerNetworkCallbacks(listOf(
                { throw SecurityException("physical callback denied") },
                { secondRegistrations++; AutoCloseable {} }
            ))
            fail("Expected registration failure")
        } catch (_: SecurityException) {
            assertEquals(0, secondRegistrations)
        }
    }

    @Test
    fun oneUnregisterFailureCannotLeakTheOtherCallbackOrRepeatCleanup() {
        var physicalUnregistrations = 0
        var defaultUnregistrations = 0
        val alreadyRemoved = IllegalArgumentException("default already removed")
        val registration = registerNetworkCallbacks(listOf(
            { AutoCloseable { physicalUnregistrations++ } },
            { AutoCloseable { defaultUnregistrations++; throw alreadyRemoved } }
        ))
        try {
            registration.close()
            fail("Expected unregister failure")
        } catch (failure: IllegalArgumentException) {
            assertSame(alreadyRemoved, failure)
        }
        registration.close()
        assertEquals(1, physicalUnregistrations)
        assertEquals(1, defaultUnregistrations)
    }
}
