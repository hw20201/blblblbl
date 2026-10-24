package com.github.mihomo.android.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable, low-refraction frosted strip for compact section labels.
 * Keeps backdrop text diffuse and readable without the strong bevel used by the dock.
 */
@Composable
fun SectionStripGlass(
    modifier: Modifier = Modifier,
    lens: NavigationLensState? = null,
    content: @Composable BoxScope.() -> Unit
) {
    NavigationGlassSurface(
        modifier = modifier,
        lens = lens,
        shape = RoundedCornerShape(12.dp),
        cornerRadius = 12.dp,
        strength = LensStrength.SUBTLE,
        surfaceCategory = GlassSurfaceCategory.LARGE_PANELS,
        frosted = true,
        showRim = false,
        frostedFillAlpha = 0.34f
    ) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}
