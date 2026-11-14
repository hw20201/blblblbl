package com.github.arca.android.ui

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubScreenBackdropPolicyTest {
    @Test
    fun glassEffectsUsesOnlyRootWallpaperAndDrawsItsOwnBackground() {
        val policy = subScreenBackdropPolicy(
            screen = SubScreen.GLASS_EFFECTS,
            apiLevel = Build.VERSION_CODES.TIRAMISU + 3,
            pageBackdropAvailable = true
        )
        assertTrue(policy.useRootWallpaperBackdrop)
        assertTrue(policy.drawWallpaperBackground)
    }

    @Test
    fun otherNestedScreensKeepTheirCompositePageBackdrop() {
        val policy = subScreenBackdropPolicy(
            screen = SubScreen.APPEARANCE,
            apiLevel = Build.VERSION_CODES.TIRAMISU + 3,
            pageBackdropAvailable = true
        )
        assertFalse(policy.useRootWallpaperBackdrop)
        assertFalse(policy.drawWallpaperBackground)
    }

    @Test
    fun nestedScreensStillDrawFallbackBackgroundWithoutPageBackdrop() {
        val policy = subScreenBackdropPolicy(
            screen = SubScreen.APPEARANCE,
            apiLevel = Build.VERSION_CODES.TIRAMISU + 3,
            pageBackdropAvailable = false
        )
        assertFalse(policy.useRootWallpaperBackdrop)
        assertTrue(policy.drawWallpaperBackground)
    }
}
