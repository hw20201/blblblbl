package com.github.mihomo.android.ui.glass

import org.junit.Assert.assertTrue
import org.junit.Test

/** Numeric guardrails for the restrained, readable background colour boundaries. */
class BackgroundBoundaryPolicyTest {
    @Test fun lightStructuredFieldHasCalmBroadSeparationWithoutOpaqueCells() {
        val cells = structuredMeshCells(GlassPalettes.CREAM, IceLavenderMesh)
        val redSpread = cells.maxOf { it.color.red } - cells.minOf { it.color.red }
        val greenSpread = cells.maxOf { it.color.green } - cells.minOf { it.color.green }
        val blueSpread = cells.maxOf { it.color.blue } - cells.minOf { it.color.blue }

        assertTrue("light background needs visible but soft colour boundaries", maxOf(redSpread, greenSpread, blueSpread) in .06f.. .24f)
        assertTrue("cells must remain translucent for readable foreground", cells.all { it.color.alpha <= .90f })
        assertTrue("cell scale should stay broad and non-decorative", cells.all { it.radius in .70f.. .90f })
    }

    @Test fun darkStructuredFieldRemainsCoolAndLowLuminance() {
        val palette = GlassPalettes.all.first { it.isDark }
        val cells = structuredMeshCells(palette, palette.blobs)
        assertTrue(cells.all { it.color.blue >= it.color.red })
        assertTrue(cells.all { maxOf(it.color.red, it.color.green, it.color.blue) < .40f })
    }
}
