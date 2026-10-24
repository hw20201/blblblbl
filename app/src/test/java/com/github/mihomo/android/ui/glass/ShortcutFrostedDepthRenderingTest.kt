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

/** Real dashboard, two shared scene recorders and navigation dock; captures native PixelCopy output. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutFrostedDepthRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun reset() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    @Test fun api35LightDashboard() = verify(false)

    @Test fun api35DarkDashboard() = verify(true)

    @Test
    @Config(sdk = [31], qualifiers = "w400dp-h800dp-mdpi")
    fun api31DashboardFallback() = verify(false)

    @Test fun tunedLightDashboard() = verify(false, tuned = true)

    @Test fun tunedDarkDashboard() = verify(true, tuned = true)

    private fun verify(dark: Boolean, tuned: Boolean = false) {
        val seconds = mutableFloatStateOf(0f)
        var compositions = 0
        var foreground = 0
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        compose.setContent {
            MihomoTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "en", LocalSoftMeshTime provides seconds,
                    LocalSoftMeshSeed provides 734921L, LocalShortcutTunedMaterialEnabled provides tuned) {
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
        val card = compose.onNodeWithTag("shortcut-card-Nodes").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("navigation-dock").fetchSemanticsNode().boundsInRoot
        val count = compositions
        val first = screenshot()
        val label = System.getenv("SHORTCUT_DEPTH_LABEL")?.takeIf { it.isNotBlank() }
            ?.replace(Regex("[^A-Za-z0-9._-]"), "-") ?: "current"
        val name = "api${android.os.Build.VERSION.SDK_INT}-${if (dark) "dark" else "light"}-${if (tuned) "tuned" else "legacy"}-$label"
        save(first, "$name-t00.png")
        // Only the accepted legacy material promises a double bright bevel. The tuned
        // profile bends the backdrop instead; both profiles must pass the glyph and
        // draw-only animated-scene checks below.
        if (android.os.Build.VERSION.SDK_INT >= 33 && !tuned) {
            var bevel = 0L
            var samples = 0
            // The captured rounded card edge is antialiased across more than two pixels;
            // sample the inset return beyond that coverage rather than inside the same rim.
            val insetReturnOffset = 4
            for (x in (card.left + 32).toInt() until (card.right - 32).toInt()) {
                val outer = first.getPixel(x, card.top.toInt())
                val insetReturn = first.getPixel(x, card.top.toInt() + insetReturnOffset)
                bevel += android.graphics.Color.red(outer) - android.graphics.Color.red(insetReturn)
                bevel += android.graphics.Color.green(outer) - android.graphics.Color.green(insetReturn)
                bevel += android.graphics.Color.blue(outer) - android.graphics.Color.blue(insetReturn)
                samples += 3
            }
            val contrast = bevel.toDouble() / samples
            println("actual local double-bevel contrast=$contrast")
            assertTrue("outer catch must be distinct from inset return, not a uniform card tint: $contrast", contrast > 3.0)
        }
        val titleText = Rect(card.left + 14, card.top + 14, card.right - 56, card.top + 33)
        val titleCores = countCores(first, titleText, foreground)
        assertTrue("actual shortcut title must have opaque glyph cores: $titleCores", titleCores > 25)
        val center = Rect(card.left + 34, card.top + 55, card.right - 34, card.bottom - 18)
        val edge = Rect(card.left + 24, card.top + 1, card.right - 24, card.top + 3)
        // Active navigation glyph is a stable, real UI reference inside the separately recorded dock.
        val dockReference = Rect(dock.left + 28, dock.top + 9, dock.left + 48, dock.top + 18)
        val log = StringBuilder("Actual Compose dashboard; native PixelCopy; seed=734921\n")
        log.appendLine("size=${first.width}x${first.height}; shortcutCard=$card; dock=$dock; titleCores=$titleCores")
        compose.runOnIdle { seconds.floatValue = 24f }
        compose.waitForIdle()
        val last = screenshot()
        save(last, "$name-t24.png")
        assertEquals("draw-only background phase must not recompose dashboard", count, compositions)
        val centerDelta = difference(first, last, center)
        val edgeDelta = difference(first, last, edge)
        val dockDelta = difference(first, last, dockReference)
        val sharpCores = stableCores(first, last, titleText, foreground)
        log.appendLine("t=24s: card center RGB-sum mean delta=$centerDelta; card edge=$edgeDelta; dock reference=$dockDelta; stable title cores=$sharpCores/$titleCores; compositions=$compositions")
        assertTrue("moving backdrop must reach actual shortcut card center: $centerDelta", centerDelta > 0.4)
        assertTrue("actual shortcut edge must follow the changed backdrop: $edgeDelta", edgeDelta > 0.4)
        assertTrue("background motion must not blur or warp actual title glyph cores: $sharpCores/$titleCores",
            sharpCores >= titleCores * .98)
        output("$name-metrics.txt").writeText(log.toString())
        println(log)
        first.recycle()
        last.recycle()
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

    private fun output(name: String): File = File("build/outputs/shortcut-depth/$name").also { it.parentFile!!.mkdirs() }

    private fun save(image: Bitmap, name: String) {
        output(name).outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun countCores(image: Bitmap, area: Rect, color: Int): Int {
        var count = 0
        for (y in area.top.toInt() until area.bottom.toInt()) for (x in area.left.toInt() until area.right.toInt()) {
            if (rgb(image.getPixel(x, y), color) <= 3) count++
        }
        return count
    }

    private fun stableCores(before: Bitmap, after: Bitmap, area: Rect, color: Int): Int {
        var count = 0
        for (y in area.top.toInt() until area.bottom.toInt()) for (x in area.left.toInt() until area.right.toInt()) {
            if (rgb(before.getPixel(x, y), color) <= 3 && rgb(before.getPixel(x, y), after.getPixel(x, y)) <= 3) count++
        }
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

    private fun rgb(a: Int, b: Int): Int =
        abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
            abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
            abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
}
