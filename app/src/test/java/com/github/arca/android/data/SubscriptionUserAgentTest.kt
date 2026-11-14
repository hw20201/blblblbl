package com.github.arca.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionUserAgentTest {
    @Test
    fun `extracts release from native build string`() {
        assertEquals("v1.19.33", ConfigManager.coreReleaseFromBuildString("v1.19.33_3f27ef3_native-mips2"))
        assertEquals("v1.20.0", ConfigManager.coreReleaseFromBuildString("1.20.0-alpha"))
    }

    @Test
    fun `falls back to bundled core version`() {
        assertEquals("v1.19.33", ConfigManager.coreReleaseFromBuildString("Unknown (no lib)"))
    }
}
