package com.github.mihomo.android.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Small pinned title lens; only the scroll scene below it is recorded, never this lens. */
@Composable
fun PageTitleGlass(
    lens: NavigationLensState?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable BoxScope.() -> Unit
) {
    if (GlassDefaults.enabled) {
        NavigationGlassSurface(modifier, lens, RoundedCornerShape(26.dp), 26.dp,
            strength = LensStrength.SUBTLE, frosted = true) {
            Box(Modifier.padding(contentPadding), content = content)
        }
    } else Box(modifier.padding(horizontal = 4.dp, vertical = 8.dp), content = content)
}
