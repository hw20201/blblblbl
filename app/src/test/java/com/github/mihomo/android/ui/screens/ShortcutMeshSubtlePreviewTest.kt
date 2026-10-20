package com.github.mihomo.android.ui.screens

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.components.*
import com.github.mihomo.android.ui.glass.*
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Real Dashboard captures and gestures for the subtle structured-mesh change. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutMeshSubtlePreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var oldEnabled = true
    private var oldPalette = GlassPalettes.CREAM
    @Before fun saveDefaults() { oldEnabled = GlassDefaults.enabled; oldPalette = GlassDefaults.palette }
    @After fun restoreDefaults() { GlassDefaults.enabled = oldEnabled; GlassDefaults.palette = oldPalette }

    @Test fun lightDashboardOldAndNewMesh() = captureComparison(dark = false)
    @Test fun darkDashboardOldAndNewMesh() = captureComparison(dark = true)
    @Test fun meshHasDistinctFrozenTime44Preview() {
        val time = mutableFloatStateOf(44f)
        render(dark = false, mesh = true, seconds = time)
        save(screenshot(), out(), "light-new-mesh-time44-topdown.png")
    }

    private fun captureComparison(dark: Boolean) {
        val time = mutableFloatStateOf(24f)
        val theme = if (dark) "dark" else "light"
        val mesh = mutableStateOf(false)
        render(dark, mesh = false, seconds = time, meshOverride = mesh)
        save(screenshot(), out(), "$theme-old-wallpaper-time24-topdown.png")
        compose.runOnIdle { mesh.value = true }; compose.waitForIdle()
        save(screenshot(), out(), "$theme-new-organic-mesh-time24-topdown.png")
    }

    @Test fun realBPressLongDragReleaseAndTapAndBlankOriginScroll() {
        val fixture = render(dark = false, mesh = true, scroll = ScrollState(0))
        val card = compose.onNodeWithTag("shortcut-card-Profiles")
        val initial = fixture.scroll.value
        val dir = out()
        save(screenshot(), dir, "gesture-B-original-topdown.png")
        compose.mainClock.autoAdvance = false
        card.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
        save(screenshot(), dir, "gesture-B-pressed-topdown.png")
        compose.runOnIdle {
            assertEquals("press must not navigate", 0, fixture.profilesClicks)
            assertEquals("light press keeps Dashboard stationary before intent is resolved", initial, fixture.scroll.value)
        }
        // A real long horizontal drag while holding B; retain the same pointer.
        card.performTouchInput { moveTo(center + Offset(150f, 0f), delayMillis = 180) }
        compose.mainClock.advanceTimeBy(180); compose.waitForIdle()
        save(screenshot(), dir, "gesture-B-horizontal-drag-topdown.png")
        compose.runOnIdle {
            assertEquals("held shortcut locks parent scroll", initial, fixture.scroll.value)
            assertEquals("drag must not navigate", 0, fixture.profilesClicks)
            assertEquals("drag must not toggle VPN", 0, fixture.vpnToggles)
        }
        // Continue with long vertical travel. This intentionally exceeds the card bounds.
        card.performTouchInput { moveTo(center + Offset(150f, 150f), delayMillis = 180) }
        compose.mainClock.advanceTimeBy(180); compose.waitForIdle()
        save(screenshot(), dir, "gesture-B-vertical-drag-topdown.png")
        compose.runOnIdle {
            assertEquals("vertical drag also locks scroll", initial, fixture.scroll.value)
            assertEquals(0, fixture.profilesClicks); assertEquals(0, fixture.vpnToggles)
        }
        card.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
        compose.mainClock.autoAdvance = true
        save(screenshot(), dir, "gesture-B-released-topdown.png")
        card.performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals("tap works after release", 1, fixture.profilesClicks)
            assertEquals("tap does not toggle VPN", 0, fixture.vpnToggles)
        }
        // Column gap is deliberately used as the blank-origin scroll path.
        val left = compose.onNodeWithTag("shortcut-card-Profiles").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("shortcut-card-Nodes").fetchSemanticsNode().boundsInRoot
        val x = (left.right + right.left) / 2f
        assertTrue("fixture exposes a real blank column gap", x > left.right && x < right.left)
        assertTrue("dashboard contains scrollable content", fixture.scroll.maxValue > 0)
        compose.onRoot().performTouchInput {
            down(Offset(x, left.center.y)); moveTo(Offset(x, left.center.y - 80f), delayMillis = 100)
            moveTo(Offset(x, left.center.y - 160f), delayMillis = 100); up()
        }
        compose.runOnIdle { assertTrue("blank-origin drag remains scrollable", fixture.scroll.value > initial) }
    }

    @Test fun overlapConstraintCaptureIsWallpaperOnlyNotUnderCardRefraction() {
        val pose = mutableStateOf<ShortcutTouchPreview?>(null)
        val shortcuts = mutableStateOf(listOf("profiles", "nodes", "rules", "request_logs", "ip_test", "core_logs", "plugins", "network_share", "vpn"))
        render(dark = false, mesh = true, preview = pose, shortcutsOverride = shortcuts)
        val nodes = compose.onNodeWithTag("shortcut-card-Nodes").fetchSemanticsNode().boundsInRoot
        val profiles = compose.onNodeWithTag("shortcut-card-Profiles").fetchSemanticsNode().boundsInRoot
        val probeX = (nodes.left - 80f + 24f).toInt()
        val probeY = (nodes.top + 68f).toInt()
        fun probe(image: Bitmap) = IntArray(28 * 28).also { image.getPixels(it, 0, 28, probeX, probeY, 28, 28) }
        fun blueIconPixels(pixels: IntArray) = pixels.count {
            android.graphics.Color.blue(it) > 170 && android.graphics.Color.red(it) < 125 &&
                android.graphics.Color.blue(it) - android.graphics.Color.red(it) > 80
        }
        val initial = screenshot()
        val visibleIcon = blueIconPixels(probe(initial))
        assertTrue("the probe must really contain the lower Profiles icon, not just wallpaper", visibleIcon > 25)
        save(initial, out(), "overlap-before-neighbor-sampling-topdown.png")
        // Forced pose covers the lower tile's icon. Scale=1 gives zero frost: blur is not the cause.
        // This exaggerated -80dp pose is test-only, never the normal +/-4dp gesture range.
        compose.runOnIdle { pose.value = ShortcutTouchPreview("Nodes", ShortcutTouchPose(offsetXDp = -80f)) }
        compose.waitForIdle()
        val image = screenshot()
        val withLowerCard = probe(image)
        assertTrue("forced pose geometrically overlaps adjacent tile", nodes.left - 80f < profiles.right && nodes.right - 80f > profiles.left)
        assertTrue("neighbor tile text region lies within dashboard", profiles.center.y > 0f && profiles.center.y < image.height)
        assertTrue("captured rendered frame has visible content", hasVariance(image))
        save(image, out(), "overlap-forced-minus80dp-wallpaper-only-topdown.png")
        compose.runOnIdle {
            shortcuts.value = shortcuts.value.filterNot { it == "profiles" }
            // Removing the lower tile changes slot order. Keep the upper plane at the exact same world position.
            pose.value = ShortcutTouchPreview("Nodes", ShortcutTouchPose(offsetXDp = nodes.left - profiles.left - 80f))
        }
        compose.waitForIdle()
        val without = screenshot()
        val withoutLowerCard = probe(without)
        val difference = withLowerCard.indices.sumOf { index ->
            val a = withLowerCard[index]; val b = withoutLowerCard[index]
            kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
                kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
                kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
        }.toDouble() / withLowerCard.size
        File(out(), "overlap-source-limit-metrics.txt").writeText(
            "Real Dashboard, zero-frost forced pose. Lower icon blue pixels before=$visibleIcon; " +
                "under upper card=${blueIconPixels(withLowerCard)}. Removing lower card changes upper interior " +
                "by mean RGB-sum=$difference. Current source is wallpaper-only, not sibling-card refraction.\n")
        // This documents an existing limitation, NOT a passing assertion of true stacked-glass optics.
        assertTrue("upper glass ignores lower-card pixels even with zero frost: $difference", difference <= .5)
        save(without, out(), "overlap-lower-card-removed-same-upper-world-position-topdown.png")
    }

    private data class Fixture(val scroll: ScrollState, var profilesClicks: Int = 0, var vpnToggles: Int = 0)

    private fun render(
        dark: Boolean,
        mesh: Boolean,
        seconds: State<Float> = mutableFloatStateOf(24f),
        scroll: ScrollState = ScrollState(0),
        preview: State<ShortcutTouchPreview?> = mutableStateOf(null),
        meshOverride: State<Boolean>? = null,
        shortcutsOverride: State<List<String>>? = null
    ): Fixture {
        GlassDefaults.enabled = true; GlassDefaults.palette = GlassPalettes.CREAM
        val fixture = Fixture(scroll)
        compose.setContent {
            MihomoTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "en", LocalSoftMeshTime provides seconds,
                    LocalSoftMeshSeed provides 734921L,
                    LocalShortcutTouchVariant provides ShortcutTouchVariant.FingerTilt,
                    LocalShortcutTouchPreview provides preview.value,
                    LocalStructuredMeshEnabled provides (meshOverride?.value ?: mesh)
                ) {
                    ProvideGlassTheme {
                        val cardScene = rememberNavigationLens(); val dockScene = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(modifier = Modifier.recordNavigationScene(dockScene),
                                backdropRecorder = Modifier.recordNavigationScene(cardScene)) {
                                val shortcuts = shortcutsOverride?.value ?: listOf("profiles", "nodes", "rules", "request_logs", "ip_test", "core_logs", "plugins", "network_share", "vpn")
                                DashboardScreen(scrollState = scroll, vpnState = VpnState(status = VpnState.Status.STOPPED),
                                    currentMode = TunnelMode.RULE, activeProfileName = "Local config", mainGroupName = "",
                                    mainGroupProxies = emptyList(), activeNodeName = "", totalNodes = 12, totalRules = 64,
                                    visibleShortcuts = shortcuts.toSet(), shortcutOrder = shortcuts,
                                    onToggleVpn = { fixture.vpnToggles++ }, onModeSelected = {}, onSelectProxy = { _, _ -> },
                                    onHealthCheck = {}, onNavigateToProfiles = { fixture.profilesClicks++ },
                                    onNavigateToAppRouting = {}, onNavigateToLogs = {}, onUpdateVisibleShortcuts = {},
                                    onUpdateShortcutOrder = {}, glassScene = cardScene)
                            }
                            LoonFloatingBottomBar(0, {}, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), dockScene)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        return fixture
    }

    private fun screenshot(): Bitmap {
        val window = compose.activity.window
        val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        val result = AtomicInteger(-1)
        compose.runOnIdle { PixelCopy.request(window, bitmap, { result.set(it) }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle(); compose.waitUntil(5000) { result.get() != -1 }
        assertEquals("Robolectric NATIVE PixelCopy", PixelCopy.SUCCESS, result.get())
        return bitmap
    }
    private fun save(bitmap: Bitmap, dir: File, name: String) {
        dir.resolve(name).outputStream().use { assertTrue("write $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    private fun out() = File("build/outputs/mesh-subtle-dashboard").also { it.mkdirs() }
    private fun hasVariance(bitmap: Bitmap): Boolean {
        val first = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8)
            if (bitmap.getPixel(x, y) != first) return true
        return false
    }
}
