package com.github.mihomo.android.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.Serializable

/** One typed, versioned source of truth for the app's glass rendering controls. */
@Serializable
@Immutable
data class GlassOptics(
    val refractionDp: Float = DEFAULT_REFRACTION_DP,
    val dispersionDp: Float = DEFAULT_DISPERSION_DP,
    val edgeWidthDp: Float = DEFAULT_EDGE_WIDTH_DP,
    val edgeLightMultiplier: Float = DEFAULT_EDGE_LIGHT_MULTIPLIER
) {
    fun clamped() = copy(
        refractionDp = refractionDp.safeIn(MIN_REFRACTION_DP, MAX_REFRACTION_DP, DEFAULT_REFRACTION_DP),
        dispersionDp = dispersionDp.safeIn(MIN_DISPERSION_DP, MAX_DISPERSION_DP, DEFAULT_DISPERSION_DP),
        edgeWidthDp = edgeWidthDp.safeIn(MIN_EDGE_WIDTH_DP, MAX_EDGE_WIDTH_DP, DEFAULT_EDGE_WIDTH_DP),
        edgeLightMultiplier = edgeLightMultiplier.safeIn(MIN_EDGE_LIGHT_MULTIPLIER, MAX_EDGE_LIGHT_MULTIPLIER, DEFAULT_EDGE_LIGHT_MULTIPLIER)
    )

    companion object {
        const val MIN_REFRACTION_DP = 2f
        const val MAX_REFRACTION_DP = 18f
        // A small step up from the previous Extreme candidate, not the slider maximum.
        const val DEFAULT_REFRACTION_DP = 13.5f
        const val MIN_DISPERSION_DP = 0f
        const val MAX_DISPERSION_DP = 12f
        const val DEFAULT_DISPERSION_DP = 6.25f
        const val MIN_EDGE_WIDTH_DP = 6f
        const val MAX_EDGE_WIDTH_DP = 20f
        const val DEFAULT_EDGE_WIDTH_DP = 12.5f
        const val MIN_EDGE_LIGHT_MULTIPLIER = 0.5f
        const val MAX_EDGE_LIGHT_MULTIPLIER = 2f
        const val DEFAULT_EDGE_LIGHT_MULTIPLIER = 1.35f
        val DEFAULT = GlassOptics()
    }
}

@Serializable
enum class GlassSurfaceCategory {
    SHORTCUT_CARDS,
    TITLE_CAPSULES,
    BOTTOM_NAVIGATION,
    LARGE_PANELS
}

@Serializable
@Immutable
data class GlassMotionTuning(
    val tiltGain: Float = DEFAULT_TILT_GAIN,
    val maxTiltDegrees: Float = DEFAULT_MAX_TILT_DEGREES,
    val smoothing: Float = DEFAULT_SMOOTHING
) {
    fun clamped() = copy(
        tiltGain = tiltGain.safeIn(MIN_TILT_GAIN, MAX_TILT_GAIN, DEFAULT_TILT_GAIN),
        maxTiltDegrees = maxTiltDegrees.safeIn(MIN_MAX_TILT_DEGREES, MAX_MAX_TILT_DEGREES, DEFAULT_MAX_TILT_DEGREES),
        smoothing = smoothing.safeIn(MIN_SMOOTHING, MAX_SMOOTHING, DEFAULT_SMOOTHING)
    )

    /** A bounded maximum tilt that remains stronger than the former 9° default. */
    fun effectiveMaxTiltDegrees(): Float = (clamped().maxTiltDegrees * clamped().tiltGain)
        .coerceIn(MIN_EFFECTIVE_TILT_DEGREES, MAX_EFFECTIVE_TILT_DEGREES)

    /** Maps the user-facing smoothness control to a stable, non-oscillating spring. */
    fun dampingRatio(): Float = (0.72f + 0.27f * clamped().smoothing).coerceIn(0.72f, 0.99f)

    companion object {
        const val MIN_TILT_GAIN = 0.75f
        const val MAX_TILT_GAIN = 1.5f
        const val DEFAULT_TILT_GAIN = 1.12f
        const val MIN_MAX_TILT_DEGREES = 4f
        const val MAX_MAX_TILT_DEGREES = 14f
        const val DEFAULT_MAX_TILT_DEGREES = 9.5f
        const val MIN_SMOOTHING = 0f
        const val MAX_SMOOTHING = 1f
        const val DEFAULT_SMOOTHING = 0.82f
        const val MIN_EFFECTIVE_TILT_DEGREES = 3f
        const val MAX_EFFECTIVE_TILT_DEGREES = 18f
        val DEFAULT = GlassMotionTuning()
    }
}

@Serializable
@Immutable
data class GlassSettings(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val globalOptics: GlassOptics = GlassOptics.DEFAULT,
    /** Missing surface keys inherit [globalOptics]; one override is shared by that surface family. */
    val surfaceOverrides: Map<GlassSurfaceCategory, GlassOptics> = emptyMap(),
    val motion: GlassMotionTuning = GlassMotionTuning.DEFAULT,
    val shortcutGlassLevel: ShortcutGlassLevel = ShortcutGlassLevel.Extreme
) {
    fun opticsFor(surface: GlassSurfaceCategory): GlassOptics =
        (surfaceOverrides[surface] ?: globalOptics).clamped()

    fun withOptics(surface: GlassSurfaceCategory, optics: GlassOptics?): GlassSettings = copy(
        surfaceOverrides = if (optics == null) surfaceOverrides - surface
        else surfaceOverrides + (surface to optics.clamped())
    ).clamped()

    fun clamped() = copy(
        schemaVersion = CURRENT_SCHEMA_VERSION,
        globalOptics = globalOptics.clamped(),
        surfaceOverrides = surfaceOverrides.mapValues { it.value.clamped() },
        motion = motion.clamped()
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        val DEFAULT = GlassSettings()
    }
}

private fun Float.safeIn(minimum: Float, maximum: Float, fallback: Float): Float =
    (if (isFinite()) this else fallback).coerceIn(minimum, maximum)

/** Source-compatible name retained for the existing shortcut shader/test surface. */
typealias ShortcutGlassTuning = GlassOptics

/** Factors adapt one shared slider model to each renderer's established optical scale. */
internal fun GlassOptics.refractionScale(): Float =
    (clamped().refractionDp / GlassOptics.DEFAULT_REFRACTION_DP).coerceIn(0.15f, 1.5f)
internal fun GlassOptics.dispersionScale(): Float =
    (clamped().dispersionDp / GlassOptics.DEFAULT_DISPERSION_DP).coerceIn(0f, 2f)
internal fun GlassOptics.edgeScale(): Float =
    (clamped().edgeWidthDp / GlassOptics.DEFAULT_EDGE_WIDTH_DP).coerceIn(0.4f, 1.6f)
internal fun GlassOptics.edgeLightScale(): Float =
    (clamped().edgeLightMultiplier / GlassOptics.DEFAULT_EDGE_LIGHT_MULTIPLIER).coerceIn(0.35f, 1.5f)

val LocalGlassSettings = staticCompositionLocalOf { GlassSettings.DEFAULT }
