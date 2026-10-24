package com.github.mihomo.android.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Fill alpha used by existing title capsules outside this focused frosted-title correction. */
const val DEFAULT_PAGE_TITLE_FILL_ALPHA = 0.88f

/** Light frosted title fill: keeps text readable while letting the blurred page colors show through. */
const val FROSTED_PAGE_TITLE_FILL_ALPHA = 0.42f

/** Neutral gray tint for selected glass capsules; alpha is supplied by the shared frost fill. */
val FROSTED_SELECTED_GLASS_TINT: Color = Color(0xFF858585)

/** One restrained refraction preset shared by frosted title and selected-tab capsules. */
val FROSTED_CAPSULE_LENS_STRENGTH: LensStrength = LensStrength.SUBTLE

/** Small pinned title lens; only the scroll scene below it is recorded, never this lens. */
@Composable
fun PageTitleGlass(
    lens: NavigationLensState?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    showRim: Boolean = true,
    frostedFillAlpha: Float = DEFAULT_PAGE_TITLE_FILL_ALPHA,
    contentTint: Color = Color.Transparent,
    frostedTint: Color? = null,
    shape: Shape = RoundedCornerShape(26.dp),
    cornerRadius: Dp = 26.dp,
    content: @Composable BoxScope.() -> Unit
) {
    if (GlassDefaults.enabled) {
        NavigationGlassSurface(modifier, lens, shape, cornerRadius,
            strength = FROSTED_CAPSULE_LENS_STRENGTH, frosted = true, showRim = showRim,
            frostedFillAlpha = frostedFillAlpha, frostedTint = frostedTint,
            surfaceCategory = GlassSurfaceCategory.TITLE_CAPSULES) {
            Box(Modifier.background(contentTint, shape).padding(contentPadding), content = content)
        }
    } else Box(modifier.clip(shape).padding(horizontal = 4.dp, vertical = 8.dp), content = content)
}
