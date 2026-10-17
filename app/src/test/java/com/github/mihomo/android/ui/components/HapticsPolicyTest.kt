package com.github.mihomo.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class HapticsPolicyTest {
    @Test
    fun disabledPreferenceSuppressesFeedback() {
        var requests = 0
        requestHapticFeedback(false) { requests++ }
        assertEquals(0, requests)
    }

    @Test
    fun enabledPreferenceRequestsExactlyOneTick() {
        var requests = 0
        requestHapticFeedback(true) { requests++ }
        assertEquals(1, requests)
    }

    @Test
    fun enablingUsesNewPreferenceAndDisablingImmediatelySuppressesFeedback() {
        var requests = 0
        val feedback = { requests++; Unit }
        requestHapticFeedback(false, feedback)
        requestHapticFeedback(true, feedback)
        requestHapticFeedback(false, feedback)
        assertEquals(1, requests)
    }
}
