package com.github.arca.android.ui.glass

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.core.GlassColors
import io.github.neoruaa.glasmio.core.GlassDimensions
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.LocalGlassDimensions
import io.github.neoruaa.glasmio.core.defaultGlassColors
import io.github.neoruaa.glasmio.glass.GlassSurface as GlasmioGlassSurface
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import io.github.neoruaa.glasmio.glass.rememberCrossWindowBackdrop
import io.github.neoruaa.glasmio.glass.render.Backdrop

/** The Glasmio source is provided only while its API-33+ renderer is selected. */
val LocalArcaGlasmioBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Keep the primary panel material native to Glasmio; the navigation dock remains compact. */
internal fun glasmioMaterialFor(surfaceCategory: GlassSurfaceCategory): NavigationGlassMaterial =
    if (surfaceCategory == GlassSurfaceCategory.LARGE_PANELS) NavigationGlassMaterial.RegularMedium
    else NavigationGlassMaterial.RegularSmall

@Composable
fun ArcaGlasmioGlassSurface(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    shape: Shape,
    tint: Color,
    material: NavigationGlassMaterial = NavigationGlassMaterial.RegularSmall,
    surfaceCategory: GlassSurfaceCategory = GlassSurfaceCategory.LARGE_PANELS,
    opticsOverride: GlassOptics? = null,
    surfaceAlphaScale: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val tuning = LocalGlassSettings.current.glasmioFrosted.clamped()
    val optics = (opticsOverride ?: LocalGlassSettings.current.opticsFor(surfaceCategory)).clamped()
    val alphaScale = if (surfaceAlphaScale.isFinite()) surfaceAlphaScale.coerceIn(0f, 1f) else 1f
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Box(
            modifier = modifier.clip(shape).background(
                tint.copy(alpha = (tuning.tintStrength * alphaScale).coerceIn(0f, 1f))
            ),
            content = content
        )
        return
    }
    GlasmioGlassSurfaceApi33(
        modifier = modifier,
        backdrop = backdrop,
        shape = shape,
        tint = tint,
        material = material,
        optics = optics,
        surfaceAlphaScale = alphaScale,
        content = content
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun GlasmioGlassSurfaceApi33(
    modifier: Modifier,
    backdrop: Backdrop,
    shape: Shape,
    tint: Color,
    material: NavigationGlassMaterial,
    optics: GlassOptics,
    surfaceAlphaScale: Float,
    content: @Composable BoxScope.() -> Unit
) {
    val tuning = LocalGlassSettings.current.glasmioFrosted.clamped()
    val colors: GlassColors = defaultGlassColors(isDark = glassPalette.isDark)
    val arcaFinish = LocalGlassTokens.current.let { tokens ->
        tokens.copy(
            sheen = (tokens.sheen * 0.18f).coerceAtMost(0.035f),
            rimTop = tokens.rimTop.copy(alpha = tokens.rimTop.alpha * 0.34f),
            rimBottom = tokens.rimBottom.copy(alpha = tokens.rimBottom.alpha * 0.30f),
        )
    }
    CompositionLocalProvider(
        LocalGlassColors provides colors,
        LocalGlassAppearance provides GlassAppearance(
            liquidGlassEnabled = tuning.refractionAmountDp > 0f && optics.refractionDp > 0f,
            reduceTransparency = false,
            adaptiveColorScheme = false
        ),
        LocalGlassDimensions provides GlassDimensions()
    ) {
        GlasmioGlassSurface(
            modifier = modifier.clip(shape).glassSurface(arcaFinish, optics),
            backdrop = rememberCrossWindowBackdrop(backdrop),
            shape = shape,
            navigationSurfaceColor = tint.copy(alpha = tuning.tintStrength),
            navigationSurfaceAlphaMultiplier = (tuning.surfaceAlphaMultiplier * surfaceAlphaScale).coerceIn(0f, 1.6f),
            refractionHeight = tuning.refractionHeightDp.dp,
            // Keep the original Arca refraction control useful while Glasmio owns the main material.
            refractionAmount = (tuning.refractionAmountDp * optics.refractionScale()).coerceIn(
                0f, GlasmioFrostedTuning.MAX_REFRACTION_AMOUNT_DP
            ).dp,
            material = material,
            frostRadius = tuning.blurRadiusDp.dp,
            content = content
        )
    }
}
