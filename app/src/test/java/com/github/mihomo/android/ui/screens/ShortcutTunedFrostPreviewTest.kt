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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

/** Frozen Dashboard keyframes compare both materials at matching variant-specific poses. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutTunedFrostPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var oldEnabled = true
    private var oldPalette = GlassPalettes.CREAM

    @Before fun saveGlassDefaults() { oldEnabled = GlassDefaults.enabled; oldPalette = GlassDefaults.palette }
    @After fun restoreGlassDefaults() { GlassDefaults.enabled = oldEnabled; GlassDefaults.palette = oldPalette }

    @Test fun gentleLight() = render(ShortcutTouchVariant.GentleFloat, false)
    @Test fun fingerTiltLight() = render(ShortcutTouchVariant.FingerTilt, false)
    @Test fun buoyantSpringLight() = render(ShortcutTouchVariant.BuoyantSpring, false)
    @Test fun gentleDark() = render(ShortcutTouchVariant.GentleFloat, true)
    @Test fun fingerTiltDark() = render(ShortcutTouchVariant.FingerTilt, true)
    @Test fun buoyantSpringDark() = render(ShortcutTouchVariant.BuoyantSpring, true)

    private fun render(variant: ShortcutTouchVariant, dark: Boolean) {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        val preview = mutableStateOf<ShortcutTouchPreview?>(null)
        val tuned = mutableStateOf(false)
        content(variant, dark, preview, tuned)
        val bounds = compose.onNodeWithTag("shortcut-card-Nodes").fetchSemanticsNode().boundsInRoot
        val mode = if (dark) "dark" else "light"
        val dir = File("build/outputs/tuned-frost-dashboard").also { it.mkdirs() }
        val prefix = "${variant.name}-$mode"
        dir.resolve("$prefix-bounds.json").writeText("{\"left\":${bounds.left},\"top\":${bounds.top},\"right\":${bounds.right},\"bottom\":${bounds.bottom}}\n")

        val reference = mutableMapOf<String, Bitmap>()
        for (material in listOf(false, true)) {
            val idle = ShortcutTouchPose()
            // Same centered finger and lift phase. B deliberately has different accepted
            // old/new motion parameters, so each profile must use its own full target.
            val full = shortcutTouchTarget(variant, 0f, 0f, tuned = material)
            val half = ShortcutTouchPose(
                scale = (idle.scale + full.scale) / 2f,
                offsetXDp = (idle.offsetXDp + full.offsetXDp) / 2f,
                offsetYDp = (idle.offsetYDp + full.offsetYDp) / 2f,
                rotationX = (idle.rotationX + full.rotationX) / 2f,
                rotationY = (idle.rotationY + full.rotationY) / 2f
            )
            val poses = listOf("contact" to idle, "half" to half, "full" to full, "release" to idle)
            compose.runOnIdle { tuned.value = material }
            compose.waitForIdle()
            poses.forEach { (label, pose) ->
                compose.runOnIdle { preview.value = ShortcutTouchPreview("Nodes", pose) }
                compose.waitForIdle()
                val image = screenshot()
                val name = "$prefix-${if (material) "new" else "old"}-$label.png"
                save(image, dir, name)
                if (label == "contact" && material) assertTrue("Dashboard content rendered", hasVisibleDetail(image, bounds))
                if (label == "release") {
                    val contact = reference["${if (material) "new" else "old"}-contact"]
                    assertNotNull("contact reference captured", contact)
                    assertTrue("$variant $mode ${if (material) "new" else "old"} release reproduces contact", requireNotNull(contact).sameAs(image))
                } else {
                    reference["${if (material) "new" else "old"}-$label"] = image
                }
                // Each material is compared only with its own contact frame.
                assertOutsideUnchanged(reference["${if (material) "new" else "old"}-contact"] ?: image, image, bounds)
            }
        }
        reference.values.toSet().forEach { it.recycle() }
    }

    private fun assertOutsideUnchanged(baseline: Bitmap, image: Bitmap, r: androidx.compose.ui.geometry.Rect) {
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val outsideX = x < r.left - 20 || x > r.right + 20
            val outsideY = y < r.top - 20 || y > r.bottom + 20
            if (outsideX || outsideY)
                assertEquals("outside panel changed at $x,$y", baseline.getPixel(x, y), image.getPixel(x, y))
        }
    }

    private fun hasVisibleDetail(image: Bitmap, r: androidx.compose.ui.geometry.Rect): Boolean {
        val l = r.left.toInt().coerceAtLeast(0); val right = r.right.toInt().coerceAtMost(image.width)
        val t = r.top.toInt().coerceAtLeast(0); val bottom = r.bottom.toInt().coerceAtMost(image.height)
        var varied = 0
        for (y in t until bottom step 3) for (x in l until right step 3)
            if (image.getPixel(x, y) != image.getPixel((x + 1).coerceAtMost(right - 1), y)) varied++
        return varied > 8
    }

    private fun content(variant: ShortcutTouchVariant, dark: Boolean, preview: State<ShortcutTouchPreview?>, tuned: State<Boolean>) {
        val seconds = mutableFloatStateOf(24f)
        compose.setContent {
            MihomoTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "en", LocalSoftMeshTime provides seconds, LocalSoftMeshSeed provides 734921L,
                    LocalShortcutTouchVariant provides variant, LocalShortcutTouchPreview provides preview.value,
                    LocalShortcutBlurEnabled provides true, LocalShortcutTunedMaterialEnabled provides tuned.value
                ) {
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
        compose.waitForIdle(); compose.waitUntil(5000) { result.get() != -1 }; assertEquals(PixelCopy.SUCCESS, result.get()); return bitmap
    }
    private fun save(bitmap: Bitmap, dir: File, name: String) { dir.resolve(name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
}
