package com.github.mihomo.android.ui.glass

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Review-only A/B switch. Neither setting changes navigation, the mesh clock or source recorders. */
internal enum class ShortcutGlassDesign { Baseline, Hybrid }

/** No extra blur: the source mesh is already diffuse. Shared skips AGSL, Flat skips card replay. */
internal enum class ShortcutGlassQuality { Refracted, Shared, Flat }

internal val LocalShortcutGlassDesign = staticCompositionLocalOf { ShortcutGlassDesign.Hybrid }
internal val LocalShortcutGlassQualityOverride = staticCompositionLocalOf<ShortcutGlassQuality?> { null }

internal fun shortcutGlassQuality(apiLevel: Int, lowRam: Boolean): ShortcutGlassQuality = when {
    lowRam || apiLevel < 31 -> ShortcutGlassQuality.Flat
    apiLevel < 33 -> ShortcutGlassQuality.Shared
    else -> ShortcutGlassQuality.Refracted
}

@Composable
internal fun rememberShortcutGlassQuality(): ShortcutGlassQuality {
    val context = LocalContext.current
    val override = LocalShortcutGlassQualityOverride.current
    return remember(context, override) {
        override ?: shortcutGlassQuality(Build.VERSION.SDK_INT,
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true)
    }
}
