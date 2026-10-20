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

/** Frozen Dashboard keyframes isolate shortcut contact-frost material changes. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutContactFrostPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var oldEnabled = true
    private var oldPalette = GlassPalettes.CREAM

    @Before fun rememberGlassDefaults() {
        oldEnabled = GlassDefaults.enabled
        oldPalette = GlassDefaults.palette
    }

    @After fun restoreGlassDefaults() {
        GlassDefaults.enabled = oldEnabled
        GlassDefaults.palette = oldPalette
    }

    @Test fun gentleLight() = render(ShortcutTouchVariant.GentleFloat, false)
    @Test fun tiltLight() = render(ShortcutTouchVariant.FingerTilt, false)
    @Test fun springLight() = render(ShortcutTouchVariant.BuoyantSpring, false)
    @Test fun gentleDark() = render(ShortcutTouchVariant.GentleFloat, true)
    @Test fun tiltDark() = render(ShortcutTouchVariant.FingerTilt, true)
    @Test fun springDark() = render(ShortcutTouchVariant.BuoyantSpring, true)

    private fun render(variant: ShortcutTouchVariant, dark: Boolean) {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        val preview = mutableStateOf<ShortcutTouchPreview?>(ShortcutTouchPreview("Nodes", ShortcutTouchPose()))
        val blur = mutableStateOf(true)
        content(variant, dark, preview, blur)
        val bounds = compose.onNodeWithTag("shortcut-card-Nodes").fetchSemanticsNode().boundsInRoot
        val mode = if (dark) "dark" else "light"
        val dir = File("build/outputs/contact-frost-dashboard").also { it.mkdirs() }
        dir.resolve("${variant.name}-$mode-bounds.json").writeText(
            "{\"left\":${bounds.left},\"top\":${bounds.top},\"right\":${bounds.right},\"bottom\":${bounds.bottom}}\n")

        val idle = ShortcutTouchPose()
        val full = shortcutTouchTarget(variant, 0f, 0f, tuned = false)
        val half = ShortcutTouchPose(
            scale = (idle.scale + full.scale) / 2f,
            offsetXDp = (idle.offsetXDp + full.offsetXDp) / 2f,
            offsetYDp = (idle.offsetYDp + full.offsetYDp) / 2f,
            rotationX = (idle.rotationX + full.rotationX) / 2f,
            rotationY = (idle.rotationY + full.rotationY) / 2f
        )
        val baseline = screenshot()
        val poses = listOf(idle, idle, half, full, idle)
        val labels = listOf("0-idle", "1-contact", "2-half-lift", "3-lift", "4-release")
        poses.forEachIndexed { index, pose ->
            compose.runOnIdle { preview.value = ShortcutTouchPreview("Nodes", pose) }
            compose.waitForIdle()
            val image = screenshot()
            save(image, dir, "${variant.name}-$mode-${labels[index]}.png")
            if (index == 4) assertTrue("release must exactly return to idle", baseline.sameAs(image))
            if (index in 1..3) assertOutsideUnchanged(baseline, image, bounds)
            image.recycle()
        }

        // Compare legacy blur off with the new contact-frost material at the identical full lift.
        compose.runOnIdle {
            preview.value = ShortcutTouchPreview("Nodes", full)
            blur.value = false
        }
        compose.waitForIdle()
        val old = screenshot()
        save(old, dir, "${variant.name}-$mode-5-old-material-lift.png")
        assertOutsideUnchanged(baseline, old, bounds)
        compose.runOnIdle { blur.value = true }
        compose.waitForIdle()
        val new = screenshot()
        save(new, dir, "${variant.name}-$mode-6-new-material-lift.png")
        assertOutsideUnchanged(baseline, new, bounds)
        old.recycle(); new.recycle(); baseline.recycle()
    }

    private fun assertOutsideUnchanged(baseline: Bitmap, image: Bitmap, bounds: androidx.compose.ui.geometry.Rect) {
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val outsideX = x < bounds.left - 20 || x > bounds.right + 20
            val outsideY = y < bounds.top - 25 || y > bounds.bottom + 25
            if (outsideX || outsideY)
                assertEquals("outside Nodes changed at $x,$y", baseline.getPixel(x, y), image.getPixel(x, y))
        }
    }

    private fun content(
        variant: ShortcutTouchVariant,
        dark: Boolean,
        preview: State<ShortcutTouchPreview?>,
        blur: State<Boolean>
    ) {
        val seconds = mutableFloatStateOf(24f)
        compose.setContent {
            MihomoTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "en",
                    LocalSoftMeshTime provides seconds,
                    LocalSoftMeshSeed provides 734921L,
                    LocalShortcutTouchVariant provides variant,
                    LocalShortcutTouchPreview provides preview.value,
                    LocalShortcutBlurEnabled provides blur.value,
                    LocalShortcutTunedMaterialEnabled provides false
                ) {
                    ProvideGlassTheme {
                        val cardScene = rememberNavigationLens()
                        val dockScene = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(
                                modifier = Modifier.recordNavigationScene(dockScene),
                                backdropRecorder = Modifier.recordNavigationScene(cardScene)
                            ) {
                                val shortcuts = listOf("nodes", "profiles", "rules", "request_logs")
                                DashboardScreen(
                                    vpnState = VpnState(status = VpnState.Status.STOPPED),
                                    currentMode = TunnelMode.RULE,
                                    activeProfileName = "Local config", mainGroupName = "", mainGroupProxies = emptyList(),
                                    activeNodeName = "", totalNodes = 12, totalRules = 64,
                                    visibleShortcuts = shortcuts.toSet(), shortcutOrder = shortcuts,
                                    onToggleVpn = {}, onModeSelected = {}, onSelectProxy = { _, _ -> }, onHealthCheck = {},
                                    onNavigateToProfiles = {}, onNavigateToAppRouting = {}, onNavigateToLogs = {},
                                    onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = {}, glassScene = cardScene
                                )
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
        compose.waitForIdle()
        compose.waitUntil(5000) { result.get() != -1 }
        assertEquals(PixelCopy.SUCCESS, result.get())
        return bitmap
    }

    private fun save(bitmap: Bitmap, dir: File, name: String) {
        dir.resolve(name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
