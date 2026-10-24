package com.github.mihomo.android.ui.glass

/** Persisted visibility choice. Motion and navigation optics are deliberately independent. */
enum class ShortcutGlassLevel(
    internal val strength: LensStrength,
    internal val displacementDp: Float,
    internal val aberrationDp: Float,
    internal val chromaticSamples: Int,
    internal val shellAlpha: Float,
    internal val rimAlpha: Float
) {
    // All three choices drive the same live lens shader; Extreme is the fresh-install default.
    // The separated red/blue samples are intentionally much more visible on a patterned wallpaper.
    Normal(LensStrength.SUBTLE, 5f, 1.8f, 2, .08f, .90f),
    Obvious(LensStrength.STANDARD, 8f, 3.5f, 2, .10f, 1.08f),
    Extreme(LensStrength.STRONG, 13f, 6f, 2, .12f, 1.30f);

    companion object {
        fun fromPreference(value: String?): ShortcutGlassLevel =
            entries.firstOrNull { it.name == value } ?: Extreme
    }
}



/** Explicit card optics passed to the same live shader. */
internal data class ShortcutDockLensOptics(
    val strength: LensStrength,
    val displacementDp: Float,
    val aberrationDp: Float,
    val bevelDp: Float,
    val saturation: Float,
    val rimGain: Float
)

internal fun shortcutDockLensOptics(level: ShortcutGlassLevel): ShortcutDockLensOptics =
    ShortcutDockLensOptics(
        strength = level.strength,
        displacementDp = level.displacementDp,
        aberrationDp = level.aberrationDp,
        bevelDp = level.strength.bevelDp,
        saturation = level.strength.saturation,
        rimGain = level.strength.rimGain * level.rimAlpha
    )

internal fun shortcutDockLensOptics(tuning: GlassOptics): ShortcutDockLensOptics {
    val safe = tuning.clamped()
    return ShortcutDockLensOptics(
        strength = LensStrength.STRONG,
        displacementDp = safe.refractionDp,
        aberrationDp = safe.dispersionDp,
        bevelDp = safe.edgeWidthDp,
        saturation = LensStrength.STRONG.saturation,
        rimGain = LensStrength.STRONG.rimGain * safe.edgeLightMultiplier
    )
}
