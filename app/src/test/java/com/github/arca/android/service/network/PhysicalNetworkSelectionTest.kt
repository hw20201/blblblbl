package com.github.arca.android.service.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalNetworkSelectionTest {
    @Test
    fun explicitPhysicalDefaultWinsEvenBeforeValidation() {
        val mobileDefault = PhysicalNetworkCandidate("mobile-default")
        val validatedWifi = PhysicalNetworkCandidate("wifi", validated = true)
        assertEquals(
            mobileDefault,
            PhysicalNetworkSelection.select(listOf(validatedWifi, mobileDefault), "mobile-default", "wifi")
        )
    }

    @Test
    fun vpnCannotBeSelectedEvenIfItIsTheExplicitDefault() {
        val vpn = PhysicalNetworkCandidate("vpn", listOf("172.19.0.2"), isVpn = true, validated = true)
        val physical = PhysicalNetworkCandidate("physical", listOf("8.8.8.8"))
        assertEquals(physical, PhysicalNetworkSelection.select(listOf(vpn, physical), "vpn", "vpn"))
        assertNull(PhysicalNetworkSelection.select(listOf(vpn), "vpn", "vpn"))
    }

    @Test
    fun networksWithoutInternetAreIneligibleEvenIfExplicitDefault() {
        val localOnly = PhysicalNetworkCandidate("local-only", hasInternet = false)
        assertNull(PhysicalNetworkSelection.select(listOf(localOnly), "local-only", null))
    }

    @Test
    fun hiddenPhysicalDefaultUsesValidationAndStableTiesInsteadOfTransportRank() {
        val mobile = PhysicalNetworkCandidate("mobile", validated = true)
        val wifi = PhysicalNetworkCandidate("wifi", validated = true)
        assertEquals(mobile, PhysicalNetworkSelection.select(listOf(wifi, mobile), null, "mobile"))
        assertEquals(wifi, PhysicalNetworkSelection.select(listOf(mobile, wifi), null, "wifi"))
        assertEquals(mobile, PhysicalNetworkSelection.select(listOf(wifi, mobile), null, null))
    }

    @Test
    fun improvedValidationCanReplaceCurrentAndSuspensionPrefersUsableCandidate() {
        val current = PhysicalNetworkCandidate("current")
        val replacement = PhysicalNetworkCandidate("replacement", validated = true)
        assertEquals(
            replacement,
            PhysicalNetworkSelection.select(listOf(current, replacement), null, "current")
        )
        val suspended = replacement.copy(notSuspended = false)
        assertEquals(current, PhysicalNetworkSelection.select(listOf(suspended, current), null, "replacement"))
    }

    @Test
    fun emptyCandidateSetDoesNotInventANetwork() {
        assertNull(PhysicalNetworkSelection.select(emptyList(), "lost", "lost"))
    }
}
