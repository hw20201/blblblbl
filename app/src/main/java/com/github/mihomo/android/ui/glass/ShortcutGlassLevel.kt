package com.github.mihomo.android.ui.glass

import androidx.compose.runtime.staticCompositionLocalOf

/** Persisted visibility choice. Motion and navigation optics are deliberately independent. */
enum class ShortcutGlassLevel(
    internal val strength: LensStrength,
    internal val dispersion: Float,
    internal val chromaticSamples: Int,
    internal val shellAlpha: Float,
    internal val rimAlpha: Float
) {
    Normal(LensStrength.SUBTLE, 10f, 1, .045f, .65f),
    Obvious(LensStrength.STANDARD, 15.5f, 2, .065f, .85f),
    Extreme(LensStrength.STRONG, 20f, 2, .08f, 1f);

    // Same dimensionless 0.02 factor as the previous shortcut optical material.
    // It scales edge displacement, never means a ten-dp RGB split.
    internal val chromaFactor: Float get() = (dispersion * .02f).coerceIn(0f, .4f)
    internal val aberrationDp: Float get() = strength.displacementDp * chromaFactor

    companion object {
        fun fromPreference(value: String?): ShortcutGlassLevel =
            entries.firstOrNull { it.name == value } ?: Normal
    }
}

val LocalShortcutGlassLevel = staticCompositionLocalOf { ShortcutGlassLevel.Normal }
