package com.github.mihomo.android.ui.glass

import org.junit.Assert.*
import org.junit.Test

class StructuredMeshWallpaperTest {
    @Test fun cellsKeepColourAcrossTheWholePageRatherThanClumpingIntoOneCorner() {
        val cells = structuredMeshCells(GlassPalettes.CREAM, IceLavenderMesh)
        assertEquals(15, cells.size)
        assertEquals(5, cells.map { it.y }.distinct().size)
        cells.chunked(3).forEach { row ->
            assertTrue(row.first().x < .2f)
            assertTrue(row.last().x > .8f)
        }
        assertTrue(cells.first().y < .15f && cells.last().y > .85f)
        assertTrue(cells.map { it.color }.distinct().size >= 6)
        assertTrue(cells.all { it.radius in .30f.. .42f && it.color.alpha <= .91f })
    }

    @Test fun customThemeKeepsItsActualColoursInsteadOfUsingTheDefaultBlueField() {
        val custom = GlassPalettes.byKey("custom:19AACC")
        val cells = structuredMeshCells(custom, custom.blobs)
        cells.forEach { cell ->
            assertTrue(custom.blobs.any { source ->
                kotlin.math.abs(source.color.red - cell.color.red) < .005f &&
                    kotlin.math.abs(source.color.green - cell.color.green) < .005f &&
                    kotlin.math.abs(source.color.blue - cell.color.blue) < .005f
            })
        }
    }

    @Test fun defaultDarkCellsStayCoolAndBelowTextLuminance() {
        val dark = GlassPalettes.all.first { it.isDark }
        val cells = structuredMeshCells(dark, dark.blobs)
        assertEquals(15, cells.size)
        cells.forEach {
            assertTrue(it.color.blue >= it.color.red)
            assertTrue(maxOf(it.color.red, it.color.green, it.color.blue) < .40f)
            assertTrue(it.color.alpha <= .73f)
        }
    }

    @Test fun emptyColourFieldFallsBackToBaseWithoutDivideByZero() {
        assertTrue(structuredMeshCells(GlassPalettes.CREAM.copy(blobs = emptyList()), emptyList()).isEmpty())
    }
}
