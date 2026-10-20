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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.components.LoonFloatingBottomBar
import com.github.mihomo.android.ui.screens.DashboardScreen
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.LoonTextPrimary
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Dashboard and real navigation dock rendered through native PixelCopy at two mesh phases. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutHybridRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun reset() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    @Test fun baselineLight() = verify(false, ShortcutGlassDesign.Baseline, ShortcutGlassQuality.Refracted)
    @Test fun hybridLight() = verify(false, ShortcutGlassDesign.Hybrid, ShortcutGlassQuality.Refracted)
    @Test fun baselineDark() = verify(true, ShortcutGlassDesign.Baseline, ShortcutGlassQuality.Refracted)
    @Test fun hybridDark() = verify(true, ShortcutGlassDesign.Hybrid, ShortcutGlassQuality.Refracted)

    @Test
    @Config(sdk = [31], qualifiers = "w400dp-h800dp-mdpi")
    fun api31Shared() = verify(false, ShortcutGlassDesign.Hybrid, ShortcutGlassQuality.Shared, "shared")

    @Test
    @Config(sdk = [26], qualifiers = "w400dp-h800dp-mdpi")
    fun api26Flat() = verify(false, ShortcutGlassDesign.Hybrid, ShortcutGlassQuality.Flat, "flat")

    @Test fun api35ForcedFlatLowRamStructure() = verify(false, ShortcutGlassDesign.Hybrid, ShortcutGlassQuality.Flat, "flat")

    private fun verify(dark: Boolean, design: ShortcutGlassDesign, quality: ShortcutGlassQuality, suffix: String? = null) {
        val seconds = mutableFloatStateOf(0f)
        var compositions = 0
        var foreground = 0
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        compose.setContent {
            MihomoTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "en", LocalSoftMeshTime provides seconds,
                    LocalSoftMeshSeed provides 734921L, LocalShortcutGlassDesign provides design,
                    LocalShortcutGlassQualityOverride provides quality) {
                    ProvideGlassTheme {
                        val cardScene = rememberNavigationLens()
                        val dockScene = rememberNavigationLens()
                        foreground = LoonTextPrimary.toArgb()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(modifier = Modifier.recordNavigationScene(dockScene),
                                backdropRecorder = Modifier.recordNavigationScene(cardScene)) {
                                SideEffect { compositions++ }
                                val shortcuts = listOf("nodes", "profiles", "rules", "request_logs")
                                DashboardScreen(vpnState = VpnState(status = VpnState.Status.STOPPED),
                                    currentMode = TunnelMode.RULE, activeProfileName = "Local config", mainGroupName = "",
                                    mainGroupProxies = emptyList(), activeNodeName = "", totalNodes = 12, totalRules = 64,
                                    visibleShortcuts = shortcuts.toSet(), shortcutOrder = shortcuts, onToggleVpn = {},
                                    onModeSelected = {}, onSelectProxy = { _, _ -> }, onHealthCheck = {},
                                    onNavigateToProfiles = {}, onNavigateToAppRouting = {}, onNavigateToLogs = {},
                                    onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = {}, glassScene = cardScene)
                            }
                            LoonFloatingBottomBar(0, {}, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), dockScene)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val card = compose.onNodeWithTag("shortcut-card-Nodes").fetchSemanticsNode().boundsInRoot
        val regions = listOf("Nodes", "Profiles", "Rules", "Requests").associateWith {
            compose.onNodeWithTag("shortcut-card-$it").fetchSemanticsNode().boundsInRoot
        }
        compose.onNodeWithTag("navigation-dock").fetchSemanticsNode()
        val count = compositions
        val first = screenshot()
        val variant = suffix ?: if (design == ShortcutGlassDesign.Baseline) "baseline" else "hybrid"
        val mode = if (dark) "dark" else "light"
        val name = "api${android.os.Build.VERSION.SDK_INT}-$mode-$variant"
        val regionsJson = regions.entries.joinToString(",", prefix = "{", postfix = "}\n") { (title, bounds) ->
            "\"$title\":[${bounds.left.toInt()},${bounds.top.toInt()},${bounds.right.toInt()},${bounds.bottom.toInt()}]"
        }
        File("build/outputs/shortcut-hybrid").also { it.mkdirs() }
            .resolve("$name-regions.json").writeText(regionsJson)
        save(first, "$name-t00.png")
        val title = Rect(card.left + 14, card.top + 14, card.right - 56, card.top + 33)
        val titleCores = countCores(first, title, foreground)
        assertTrue("actual shortcut title must have stable glyph cores: $titleCores", titleCores > 25)
        compose.runOnIdle { seconds.floatValue = 24f }
        compose.waitForIdle()
        val last = screenshot()
        save(last, "$name-t24.png")
        assertEquals("draw-only background phase must not recompose dashboard", count, compositions)
        val stable = stableCores(first, last, title, foreground)
        assertTrue("title glyph cores remain stable", stable >= titleCores * .98)
        // API 31/26 and forced-flat paths are structural/fallback smoke captures only.
        if (android.os.Build.VERSION.SDK_INT >= 33 && quality == ShortcutGlassQuality.Refracted) {
            val center = Rect(card.left + 34, card.top + 55, card.right - 34, card.bottom - 18)
            val edge = Rect(card.left + 24, card.top + 1, card.right - 24, card.top + 3)
            val centerDelta = difference(first, last, center)
            val edgeDelta = difference(first, last, edge)
            println("hybrid rendering $name centerDelta=$centerDelta edgeDelta=$edgeDelta stableCores=$stable/$titleCores")
            assertTrue("moving backdrop must reach actual shortcut card center: $centerDelta", centerDelta > .4)
            assertTrue("actual shortcut edge follows changed backdrop: $edgeDelta", edgeDelta > .4)
        }
        first.recycle()
        last.recycle()
    }

    private fun screenshot(): Bitmap {
        val window = compose.activity.window
        val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        val result = AtomicInteger(-1)
        compose.runOnIdle { PixelCopy.request(window, bitmap, { result.set(it) }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) { result.get() != -1 }
        assertEquals(PixelCopy.SUCCESS, result.get())
        return bitmap
    }

    private fun save(image: Bitmap, name: String) {
        val dir = File("build/outputs/shortcut-hybrid")
        dir.mkdirs()
        dir.resolve(name).outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun countCores(image: Bitmap, area: Rect, color: Int): Int {
        var count = 0
        for (y in area.top.toInt() until area.bottom.toInt()) for (x in area.left.toInt() until area.right.toInt())
            if (rgb(image.getPixel(x, y), color) <= 3) count++
        return count
    }

    private fun stableCores(before: Bitmap, after: Bitmap, area: Rect, color: Int): Int {
        var count = 0
        for (y in area.top.toInt() until area.bottom.toInt()) for (x in area.left.toInt() until area.right.toInt())
            if (rgb(before.getPixel(x, y), color) <= 3 && rgb(before.getPixel(x, y), after.getPixel(x, y)) <= 3) count++
        return count
    }

    private fun difference(a: Bitmap, b: Bitmap, area: Rect): Double {
        var total = 0L
        var n = 0
        for (y in area.top.toInt() until area.bottom.toInt()) for (x in area.left.toInt() until area.right.toInt()) {
            total += rgb(a.getPixel(x, y), b.getPixel(x, y)); n++
        }
        return total.toDouble() / n
    }

    private fun rgb(a: Int, b: Int): Int = abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
        abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) + abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
}
