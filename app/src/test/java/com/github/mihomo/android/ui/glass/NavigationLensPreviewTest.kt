package com.github.mihomo.android.ui.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.components.LoonFloatingBottomBar
import com.github.mihomo.android.ui.screens.DashboardScreen
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationLensPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun reset() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    @Test fun liquidShapeUsesActualSpeedAndBrakingAndStaysVerticallyCentered() {
        assertEquals(0f, lensJellyTarget(0f, 0f), 0.0001f)
        assertTrue(lensJellyTarget(6f, 0f) > 0.075f)
        assertEquals(lensJellyTarget(6f, 0f), lensJellyTarget(-6f, 0f), 0.0001f)
        assertTrue("braking must produce a negative squash, not just stop stretching",
            lensJellyTarget(0.1f, -100f) < -0.025f)
        for (height in listOf(64f, 80f, 128f)) {
            for (stretch in listOf(-0.15f, 0f, 0.2f)) {
                val rect = liquidLensRect(1f, stretch, 101.333f, 109.333f, height)
                assertEquals(height / 2f, rect.center.y, 0.001f)
                assertEquals(152f, rect.center.x, 0.01f)
                val lifted = liquidLensRect(1f, stretch, 101.333f, 109.333f, height, 16f)
                assertEquals("lift must also remain centred", height / 2f, lifted.center.y, 0.001f)
                assertTrue(lifted.width > rect.width)
                assertTrue(lifted.height > rect.height)
                if (stretch > 0) {
                    assertTrue(rect.width > 109.333f)
                    assertTrue(rect.height < height)
                } else if (stretch < 0) {
                    assertTrue(rect.width < 109.333f)
                    assertTrue(rect.height > height)
                }
            }
        }
    }

    @Test fun insetLensExpandsUniformlyAroundFixedCenterAndContainsRebound() {
        val rest = insetLensRect(0f, 0f, 0f, 304f / 3f, 304f, 64f, 1f)
        assertEquals(6f, rest.left, 0.001f)
        assertEquals(6f, rest.top, 0.001f)
        assertEquals(52f, rest.height, 0.001f)
        val expanded = insetLensRect(0f, 0f, 1f, 304f / 3f, 304f, 64f, 1f)
        assertEquals(rest.center.x, expanded.center.x, 0.001f)
        assertEquals(rest.center.y, expanded.center.y, 0.001f)
        assertEquals(LensMotion.expansionScale, expanded.width / rest.width, 0.001f)
        assertEquals(LensMotion.expansionScale, expanded.height / rest.height, 0.001f)
        assertTrue("peak glass covers selected slot width", expanded.width >= 304f / 3f)
        assertTrue("peak glass covers full option height", expanded.height >= 64f)
        for (density in listOf(1f, 2f, 3f)) {
            for (position in listOf(-0.04f, 0f, 0.7f, 1.3f, 2f, 2.04f)) {
                for (stretch in listOf(-0.25f, 0f, 0.25f)) {
                    for (expansion in listOf(0f, 0.5f, 1f)) {
                        val r = insetLensRect(position, stretch, expansion, 304f * density / 3f,
                            304f * density, 64f * density, density)
                        assertEquals(32f * density, r.center.y, 0.001f)
                        assertEquals((position + 0.5f) * 304f * density / 3f, r.center.x, 0.001f)
                        assertTrue(r.left >= -16f * density - 0.001f)
                        assertTrue(r.right <= 320f * density + 0.001f)
                        assertTrue(r.top >= -16f * density - 0.001f)
                        assertTrue(r.bottom <= 80f * density + 0.001f)
                    }
                }
            }
        }
    }

    @Test fun interruptedTravelAndContinuouslyRetargetedJellyKeepMoving() {
        val travel = LensSpring(0f, LensMotion.travelStiffness, LensMotion.travelDamping)
        val jelly = LensSpring(0f, LensMotion.jellyStiffness, LensMotion.jellyDamping)
        travel.springTo(2f, 0L)
        var peak = 0f
        var trough = 0f
        for (step in 1..180) {
            val now = step * 16_000_000L
            travel.tick(now)
            jelly.tick(now)
            jelly.springTo(lensJellyTarget(travel.velocity, travel.acceleration), now)
            peak = maxOf(peak, jelly.value)
            trough = minOf(trough, jelly.value)
            if (step == 6) {
                val before = travel.value
                val velocity = travel.velocity
                travel.springTo(0f, now)
                assertEquals("retarget must not teleport", before, travel.value, 0.0001f)
                assertEquals("retarget preserves momentum", velocity, travel.velocity, 0.0001f)
            }
        }
        assertTrue("jelly must visibly stretch, not freeze on retarget", peak > 0.005f)
        assertTrue("restrained follow-through stays bounded without forcing a return wobble", trough >= -0.031f && peak <= 0.081f)
        assertEquals(0f, travel.value, 0.001f)
        assertEquals(0f, jelly.value, 0.001f)
    }

    @Test fun dashboardPreviewsAndNavigationCallbacks() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val selected = mutableStateOf(0)
        val shortcuts = listOf("nodes", "profiles", "rules", "request_logs")
        compose.setContent {
            MihomoTheme(themeMode = mode.value) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        val lens = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(Modifier.recordNavigationScene(lens)) {
                                DashboardScreen(
                                    vpnState = VpnState(status = VpnState.Status.STOPPED),
                                    currentMode = TunnelMode.RULE,
                                    activeProfileName = "未选择配置",
                                    mainGroupName = "", mainGroupProxies = emptyList(), activeNodeName = "",
                                    totalNodes = 0, totalRules = 0,
                                    visibleShortcuts = shortcuts.toSet(), shortcutOrder = shortcuts,
                                    onToggleVpn = {}, onModeSelected = {}, onSelectProxy = { _, _ -> },
                                    onHealthCheck = {}, onNavigateToProfiles = {}, onNavigateToAppRouting = {},
                                    onNavigateToLogs = {}, onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = {}
                                )
                            }
                            LoonFloatingBottomBar(
                                selectedTab = selected.value, onTabSelected = { selected.value = it },
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), lens = lens
                            )
                        }
                    }
                }
            }
        }
        for ((name, palette) in listOf("cream" to GlassPalettes.CREAM, "blue" to GlassPalettes.BLUE,
            "white" to GlassPalettes.WHITE, "midnight" to GlassPalettes.MIDNIGHT)) {
            compose.runOnIdle {
                mode.value = if (palette.isDark) ThemeMode.DARK else ThemeMode.LIGHT
                GlassDefaults.palette = palette
            }
            compose.waitForIdle()
            save(name)
        }
        compose.runOnIdle {
            mode.value = ThemeMode.LIGHT
            GlassDefaults.palette = GlassPalettes.CREAM
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("设置").performClick()
        compose.runOnIdle { assertEquals(2, selected.value) }
        for (step in 0..59) {
            compose.mainClock.advanceTimeBy(16L)
            compose.waitForIdle()
            save("expansion-${step.toString().padStart(3, '0')}")
        }
        compose.mainClock.advanceTimeBy(3000L)
        compose.onNodeWithText("仪表").performClick()
        compose.runOnIdle { assertEquals(0, selected.value) }
        compose.mainClock.advanceTimeBy(3000L)
        compose.waitForIdle()
        val rest = compose.onNodeWithTag("navigation-liquid-lens").getUnclippedBoundsInRoot()
        val labels = listOf("仪表", "策略", "设置").map { label ->
            compose.onNodeWithText(label).getUnclippedBoundsInRoot()
        }
        // Same-tab clicks expand the entire glass shell, without translating its centre.
        save("same-tab-rest")
        compose.onNodeWithText("仪表").performClick()
        var peakWidth = (rest.right.value - rest.left.value)
        var peakHeight = (rest.bottom.value - rest.top.value)
        for (step in 1..50) {
            compose.mainClock.advanceTimeBy(16L)
            compose.waitForIdle()
            val expanded = compose.onNodeWithTag("navigation-liquid-lens").getUnclippedBoundsInRoot()
            assertEquals("expansion never translates vertically", (rest.top.value + rest.bottom.value) / 2f,
                (expanded.top.value + expanded.bottom.value) / 2f, 0.5f)
            assertEquals("same-tab expansion keeps horizontal centre", (rest.left.value + rest.right.value) / 2f,
                (expanded.left.value + expanded.right.value) / 2f, 0.5f)
            assertEquals("whole shell scales uniformly", (expanded.right.value - expanded.left.value) / (rest.right.value - rest.left.value),
                (expanded.bottom.value - expanded.top.value) / (rest.bottom.value - rest.top.value), 0.025f)
            listOf("仪表", "策略", "设置").forEachIndexed { i, label ->
                assertEquals("navigation labels stay stationary", labels[i],
                    compose.onNodeWithText(label).getUnclippedBoundsInRoot())
            }
            save("same-tab-${step.toString().padStart(3, '0')}")
            if ((expanded.right.value - expanded.left.value) > peakWidth) save("same-tab-expanded")
            peakWidth = maxOf(peakWidth, (expanded.right.value - expanded.left.value))
            peakHeight = maxOf(peakHeight, (expanded.bottom.value - expanded.top.value))
        }
        val dock = compose.onNodeWithTag("navigation-dock").getUnclippedBoundsInRoot()
        assertTrue("glass grows over the entire selected option", peakWidth >= (dock.right.value - dock.left.value) / 3f)
        assertTrue("glass covers the icon and label height", peakHeight >= (dock.bottom.value - dock.top.value))
        compose.onNodeWithText("设置").performClick()
        compose.mainClock.advanceTimeBy(64L)
        compose.onNodeWithText("仪表").performClick()
        compose.mainClock.advanceTimeBy(3000L)
        compose.waitForIdle()
        val settled = compose.onNodeWithTag("navigation-liquid-lens").getUnclippedBoundsInRoot()
        assertEquals((rest.top.value + rest.bottom.value) / 2f, (settled.top.value + settled.bottom.value) / 2f, 1f)
        assertEquals((rest.left.value + rest.right.value) / 2f, (settled.left.value + settled.right.value) / 2f, 1f)
        compose.runOnIdle {
            mode.value = ThemeMode.DARK
            GlassDefaults.palette = GlassPalettes.MIDNIGHT
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        save("dark-expansion-rest")
        compose.onNodeWithText("设置").performClick()
        for (step in 0..59) {
            compose.mainClock.advanceTimeBy(16L)
            compose.waitForIdle()
            save("dark-expansion-${step.toString().padStart(3, '0')}")
        }
        compose.mainClock.advanceTimeBy(3000L)
        compose.waitForIdle()
        save("dark-scale-only-rest")
        compose.onNodeWithText("设置").performClick()
        for (step in 0..59) {
            compose.mainClock.advanceTimeBy(16L)
            compose.waitForIdle()
            save("dark-scale-only-${step.toString().padStart(3, '0')}")
        }
    }

    @Test fun renderedLensExpandsAndMorphsWithoutVerticalTranslation() {
        val selected = mutableStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoonFloatingBottomBar(selected.value, { selected.value = it }, lens = rememberNavigationLens())
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        fun boundsOf(tag: String): Rect {
            val r = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            return Rect(r.left.value, r.top.value, r.right.value, r.bottom.value)
        }
        val dock = boundsOf("navigation-dock")
        fun bounds() = boundsOf("navigation-liquid-lens")
        val rest = bounds()
        assertEquals(dock.center.y, rest.center.y, 1f)
        assertEquals("resting lens has a visible inset", 6f, rest.top - dock.top, 1f)
        assertEquals(6f, rest.left - dock.left, 1f)
        save("motion-000-rest")
        compose.runOnIdle { selected.value = 2 }
        var widest = rest.width
        var tallest = rest.height
        var shortest = rest.height
        var previousX = rest.center.x
        val targetX = dock.left + dock.width * 5f / 6f
        var maxVerticalError = 0f
        for (step in 1..60) {
            compose.mainClock.advanceTimeBy(16L)
            compose.waitForIdle()
            val lens = bounds()
            val verticalError = kotlin.math.abs(lens.center.y - dock.center.y)
            maxVerticalError = maxOf(maxVerticalError, verticalError)
            assertEquals("subpixel placement removes centre jitter", dock.center.y, lens.center.y, 0.05f)
            assertTrue("travel does not bounce backward", lens.center.x >= previousX - 0.01f)
            assertTrue("travel never overshoots the destination", lens.center.x <= targetX + 0.01f)
            previousX = lens.center.x
            assertTrue("symmetric top overhang stays bounded", lens.top >= dock.top - 17f)
            assertTrue("symmetric bottom overhang stays bounded", lens.bottom <= dock.bottom + 17f)
            assertTrue("side overhang stays bounded", lens.left >= dock.left - 17f && lens.right <= dock.right + 17f)
            widest = maxOf(widest, lens.width)
            tallest = maxOf(tallest, lens.height)
            shortest = minOf(shortest, lens.height)
            println("liquid frame ${step * 16}ms: x=${lens.center.x} y=${lens.center.y} w=${lens.width} h=${lens.height}")
            if (step in listOf(2, 4, 6, 10, 16)) save("motion-${step.toString().padStart(3, '0')}")
        }
        println("liquid geometry: rest=${rest.width}x${rest.height}, widest=$widest tallest=$tallest shortest=$shortest, dock-center=${dock.center.y}, maxVerticalError=$maxVerticalError")
        assertTrue("actual rendered lens must stretch horizontally", widest > rest.width * 1.03f)
        assertTrue("actual rendered lens must rebound vertically", tallest > rest.height * 1.02f)
        assertTrue("whole lens covers the dock height at peak", tallest >= dock.height)
        compose.mainClock.advanceTimeBy(3000L)
        compose.waitForIdle()
        val settled = bounds()
        assertEquals(rest.width, settled.width, 1f)
        assertEquals(rest.height, settled.height, 1f)
        assertEquals(dock.center.y, settled.center.y, 1f)
        assertEquals(dock.left + dock.width * 5f / 6f, settled.center.x, 1f)
        save("motion-999-settled")
    }

    private fun save(name: String) {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle {
            PixelCopy.request(compose.activity.window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
        }
        compose.waitForIdle()
        assertEquals(PixelCopy.SUCCESS, result)
        val file = File("build/reports/navigation-lens-polished/dashboard-$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
