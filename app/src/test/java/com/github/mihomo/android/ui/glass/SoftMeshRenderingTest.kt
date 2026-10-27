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
import kotlin.math.abs

/** Real Compose dashboard + exact two-recorder MainActivity path; not a concept image. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SoftMeshRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun reset() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        GlassDefaults.wallpaper = GlassWallpaperSettings()
    }

    @Test fun api35ActualDashboardMovesThroughCardsAndAgslDock() = verify(false)

    @Test @Config(sdk = [31], qualifiers = "w400dp-h800dp-mdpi")
    fun api31ActualDashboardMovesWithoutPretendingAgslExists() = verify(false)

    @Test fun api35DarkDashboardKeepsDarkModeAndSharpText() = verify(true)

    @Test fun api35CustomBlueActuallyPaintsBrandAndNavigation() = verify(false, GlassPalettes.byKey("custom:19AACC"))

    @Test fun api35CustomCoralActuallyPaintsBrandAndNavigation() = verify(false, GlassPalettes.byKey("custom:DD3366"))

    private fun verify(dark: Boolean, palette: GlassPaletteSpec = GlassPalettes.CREAM) {
        val seconds = mutableFloatStateOf(0f)
        var compositions = 0
        var foreground = 0
        GlassDefaults.enabled = true
        // This suite measures the animated SoftMesh renderer, independent of the app's
        // newly enabled saved Fluted wallpaper.
        GlassDefaults.wallpaper = GlassWallpaperSettings(enabled = false)
        GlassDefaults.palette = palette
        compose.setContent {
            MihomoTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "en", LocalSoftMeshTime provides seconds,
                    LocalSoftMeshSeed provides 734921L
                ) {
                    ProvideGlassTheme {
                        val cardScene = rememberNavigationLens()
                        val dockScene = rememberNavigationLens()
                        foreground = LoonTextPrimary.toArgb()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(
                                modifier = Modifier.recordNavigationScene(dockScene),
                                backdropRecorder = Modifier.recordNavigationScene(cardScene)
                            ) {
                                SideEffect { compositions++ }
                                val shortcuts = listOf("nodes", "profiles", "rules", "request_logs")
                                DashboardScreen(
                                    vpnState = VpnState(status = VpnState.Status.STOPPED),
                                    currentMode = TunnelMode.RULE, activeProfileName = "Local config",
                                    mainGroupName = "", mainGroupProxies = emptyList(), activeNodeName = "",
                                    totalNodes = 12, totalRules = 64,
                                    visibleShortcuts = shortcuts.toSet(), shortcutOrder = shortcuts,
                                    onToggleVpn = {}, onModeSelected = {}, onSelectProxy = { _, _ -> },
                                    onHealthCheck = {}, onNavigateToProfiles = {}, onNavigateToAppRouting = {},
                                    onNavigateToLogs = {}, onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = {},
                                    glassScene = cardScene
                                )
                            }
                            LoonFloatingBottomBar(0, {},
                                Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), dockScene)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val title = compose.onNodeWithTag("shortcut-card-Nodes").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("navigation-dock").fetchSemanticsNode().boundsInRoot
        val beforeCount = compositions
        val first = screenshot()
        val custom = palette.key.startsWith("custom:")
        val name = "api${android.os.Build.VERSION.SDK_INT}-${if (custom) palette.key.replace(':', '-') else if (dark) "dark" else "ice-lavender"}"
        save(first, "$name-t00.png")
        if (custom) {
            val accent = palette.uiAccent.toArgb()
            assertTrue("selected RGB must really reach NEXUS brand glyphs", matching(first, Rect(20f, 20f, 180f, 56f), accent) > 100)
            assertTrue("selected RGB must really reach active navigation", matching(first,
                Rect(dock.left + 10, dock.top + 8, dock.left + 90, dock.bottom - 8), accent) > 25)
        }
        val log = StringBuilder("Actual Compose + Robolectric native HWUI/PixelCopy; NOT a physical device.\n")
        log.appendLine("size=${first.width}x${first.height}; title=$title; dock=$dock")
        for (time in listOf(4f, 12f, 24f, 44f)) {
            compose.runOnIdle { seconds.floatValue = time }
            compose.waitForIdle()
            val next = screenshot()
            save(next, "$name-t${time.toInt().toString().padStart(2, '0')}.png")
            assertEquals("draw-only motion must not recompose dashboard", beforeCount, compositions)
            val cardEmpty = Rect(title.left + 30, title.top + 62, title.right - 70, title.bottom - 12)
            val cardDelta = difference(first, next, cardEmpty)
            val rimDelta = difference(first, next,
                Rect(title.left + 24, title.top + 1, title.right - 24, title.top + 3))
            val dockDelta = difference(first, next,
                Rect(dock.left + 20, dock.top + 8, dock.left + 60, dock.top + 16))
            log.appendLine("t=$time: empty shortcut RGB-sum mean delta=$cardDelta; rim=$rimDelta; dock=$dockDelta; content compositions=$compositions")
            assertTrue("moving background reaches actual card sampling: $cardDelta", cardDelta > 0.4)
            assertTrue("actual shortcut bevel follows the mesh rather than a fixed white outline: $rimDelta", rimDelta > 0.4)
            assertTrue("moving background reaches dock sample: $dockDelta", dockDelta > 0.2)
            assertSharpTitle(first, next, title, foreground)
            for (row in 0..1) for (col in 0..1) {
                // Keep away from controls: expose top/middle/bottom in both screen halves.
                val zone = Rect(col * first.width / 2f + 4, (if (row == 0) 80f else 570f),
                    (col + 1) * first.width / 2f - 4, (if (row == 0) 102f else 650f))
                val delta = difference(first, next, zone)
                log.appendLine("  screen quadrant $col,$row RGB-sum delta=$delta")
                assertTrue("motion is not confined to a narrow strip: $col,$row=$delta", delta > 0.3)
            }
            next.recycle()
        }
        output("$name-metrics.txt").writeText(log.toString())
        println(log)
        first.recycle()
    }

    private fun assertSharpTitle(before: Bitmap, after: Bitmap, card: Rect, foreground: Int) {
        var cores = 0
        var stable = 0
        for (y in (card.top + 14).toInt() until (card.top + 33).toInt()) {
            for (x in (card.left + 14).toInt() until (card.right - 56).toInt()) {
                if (rgb(before.getPixel(x, y), foreground) <= 3) {
                    cores++
                    if (rgb(before.getPixel(x, y), after.getPixel(x, y)) <= 3) stable++
                }
            }
        }
        assertTrue("actual title has opaque glyph cores: $cores", cores > 25)
        assertTrue("moving backdrop may not blur/warp title: $stable/$cores", stable >= cores * .98)
    }

    private fun screenshot(): Bitmap {
        val window = compose.activity.window
        val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle { PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle()
        assertEquals(PixelCopy.SUCCESS, result)
        return bitmap
    }

    private fun output(name: String): File = File("build/outputs/mesh-preview/$name").also { it.parentFile!!.mkdirs() }
    private fun save(image: Bitmap, name: String) {
        output(name).outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    private fun difference(a: Bitmap, b: Bitmap, r: Rect): Double {
        var total = 0L
        var n = 0
        for (y in r.top.toInt() until r.bottom.toInt()) for (x in r.left.toInt() until r.right.toInt()) {
            total += rgb(a.getPixel(x, y), b.getPixel(x, y)); n++
        }
        return total.toDouble() / n
    }
    private fun matching(image: Bitmap, rect: Rect, color: Int): Int {
        var count = 0
        for (y in rect.top.toInt() until rect.bottom.toInt()) for (x in rect.left.toInt() until rect.right.toInt()) {
            if (rgb(image.getPixel(x, y), color) <= 3) count++
        }
        return count
    }
    private fun rgb(a: Int, b: Int): Int =
        abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
            abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
            abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
}
