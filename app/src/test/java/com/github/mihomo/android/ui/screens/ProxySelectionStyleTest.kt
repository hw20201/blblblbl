package com.github.mihomo.android.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ProxySelectionStyleTest {
    @Test fun selectedAndUnselectedRowsUseDistinctOpaqueNeutralMattesInBothThemes() {
        for (dark in listOf(false, true)) {
            val selected = proxyCardSurfaceStyle(selected = true, dark = dark)
            val unselected = proxyCardSurfaceStyle(selected = false, dark = dark)
            assertEquals(1f, selected.fill.alpha, 0f)
            assertEquals(1f, unselected.fill.alpha, 0f)
            assertNotEquals("selected and unselected surfaces must be visually distinct", selected.fill, unselected.fill)
            assertTrue("selected fill stays neutral gray", isNeutralGray(selected.fill))
            assertTrue("unselected fill stays neutral gray", isNeutralGray(unselected.fill))
            assertTrue("selected label remains readable", contrastRatio(selected.text, selected.fill) >= 4.5f)
            assertTrue("selected secondary label remains readable", contrastRatio(selected.secondaryText, selected.fill) >= 4.5f)
            assertTrue("unselected label remains readable", contrastRatio(unselected.text, unselected.fill) >= 4.5f)
            assertTrue("unselected secondary label remains readable", contrastRatio(unselected.secondaryText, unselected.fill) >= 4.5f)
            assertTrue("selection changes luminance enough to read at a glance",
                abs(selected.fill.luminance() - unselected.fill.luminance()) >= .12f)
        }
    }

    private fun isNeutralGray(color: Color): Boolean =
        abs(color.red - color.green) <= .02f && abs(color.green - color.blue) <= .02f

    private fun contrastRatio(first: Color, second: Color): Float {
        val high = max(first.luminance(), second.luminance())
        val low = min(first.luminance(), second.luminance())
        return (high + .05f) / (low + .05f)
    }
}
