package com.github.mihomo.android.ui.screens

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
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

/** Actual production Dashboard, native Compose rendering, real pointer down/up; no frozen pose. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutDarkLiftPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var oldEnabled = true
    private var oldPalette = GlassPalettes.CREAM
    @Before fun saveDefaults() { oldEnabled = GlassDefaults.enabled; oldPalette = GlassDefaults.palette }
    @After fun restoreDefaults() { GlassDefaults.enabled = oldEnabled; GlassDefaults.palette = oldPalette }

    @Test fun darkLiftIsVisibleAndReturnsToUnchangedRest() = render(true)
    @Test fun lightModeRemainsPixelIdentical() = render(false)

    private fun advance(ms: Long) {
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms); compose.waitForIdle()
    }

    private fun render(dark: Boolean) {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        val enhanced = mutableStateOf(false)
        content(dark, enhanced)
        compose.mainClock.autoAdvance = false
        val card = compose.onNodeWithTag("shortcut-card-Nodes")
        val bounds = card.fetchSemanticsNode().boundsInRoot
        val mode = if (dark) "dark" else "light"
        val dir = File("build/outputs/dark-slab-lift").also { it.mkdirs() }
        dir.resolve("$mode-bounds.json").writeText("[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]\n")
        val before = mutableMapOf<String, Bitmap>()
        for (enabled in listOf(false, true)) {
            compose.runOnIdle { enhanced.value = enabled }; advance(1000)
            val prefix = "$mode-${if (enabled) "after" else "before"}"
            fun capture(label: String): Bitmap {
                val image = screenshot()
                dir.resolve("$prefix-$label.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                if (!enabled) before[label] = image
                else if (!dark || label == "idle" || label == "release") {
                    assertTrue("$mode $label unchanged", requireNotNull(before[label]).sameAs(image))
                } else {
                    assertFalse("dark $label must visibly improve", requireNotNull(before[label]).sameAs(image))
                    for (y in 0 until image.height) for (x in 0 until image.width) {
                        val outsideX = x < bounds.left - 24 || x > bounds.right + 24
                        val outsideY = y < bounds.top - 24 || y > bounds.bottom + 24
                        if (outsideX || outsideY)
                            assertEquals("only active card changes at $x,$y", before[label]!!.getPixel(x, y), image.getPixel(x, y))
                    }
                }
                return image
            }
            val idle = capture("idle")
            card.performTouchInput { down(center + Offset(center.x * .65f, center.y * .3f)) }
            advance(32)
            val immediate = capture("down32ms")
            assertFalse("immediate feedback", idle.sameAs(immediate))
            advance(160)
            val held = capture("held")
            assertEquals("hit slot remains stable", bounds, card.fetchSemanticsNode().boundsInRoot)
            card.performTouchInput { up() }; advance(1200)
            val released = capture("release")
            assertTrue("release restores rest exactly", idle.sameAs(released))
            if (enabled) listOf(idle, immediate, held, released).forEach { it.recycle() }
        }
        before.values.forEach { it.recycle() }
    }

    private fun content(dark: Boolean, enhanced: State<Boolean>) {
        val seconds = mutableFloatStateOf(24f)
        compose.setContent {
            MihomoTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "en", LocalSoftMeshTime provides seconds,
                    LocalSoftMeshSeed provides 734921L, LocalShortcutTouchVariant provides ShortcutTouchVariant.FingerTilt,
                    LocalShortcutDarkLiftEnabled provides enhanced.value) {
                    ProvideGlassTheme {
                        val cardScene = rememberNavigationLens(); val dockScene = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(modifier = Modifier.recordNavigationScene(dockScene), backdropRecorder = Modifier.recordNavigationScene(cardScene)) {
                                val shortcuts = listOf("nodes", "profiles", "rules", "request_logs")
                                DashboardScreen(vpnState = VpnState(status = VpnState.Status.STOPPED), currentMode = TunnelMode.RULE,
                                    activeProfileName = "Local config", mainGroupName = "", mainGroupProxies = emptyList(), activeNodeName = "",
                                    totalNodes = 12, totalRules = 64, visibleShortcuts = shortcuts.toSet(), shortcutOrder = shortcuts,
                                    onToggleVpn = {}, onModeSelected = {}, onSelectProxy = { _, _ -> }, onHealthCheck = {},
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
    }

    private fun screenshot(): Bitmap {
        val window = compose.activity.window
        val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        val result = AtomicInteger(-1)
        compose.runOnIdle { PixelCopy.request(window, bitmap, { result.set(it) }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle(); compose.waitUntil(5000) { result.get() != -1 }
        assertEquals(PixelCopy.SUCCESS, result.get())
        return bitmap
    }
}
