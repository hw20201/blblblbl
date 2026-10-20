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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** Test-only dashboard captures for the old-vs-follow-up refractive material profiles. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutRefractiveDashboardPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var oldEnabled = true
    private var oldPalette = GlassPalettes.CREAM

    @Before fun saveGlassDefaults() { oldEnabled = GlassDefaults.enabled; oldPalette = GlassDefaults.palette }
    @After fun restoreGlassDefaults() { GlassDefaults.enabled = oldEnabled; GlassDefaults.palette = oldPalette }

    @Test fun lightRealMesh() = render(dark = false, stripes = false)
    @Test fun darkRealMesh() = render(dark = true, stripes = false)
    @Test fun lightTestOnlyStripes() = render(dark = false, stripes = true)
    @Test fun darkTestOnlyStripes() = render(dark = true, stripes = true)

    private fun render(dark: Boolean, stripes: Boolean) {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        val preview = mutableStateOf<ShortcutTouchPreview?>(null)
        val tuned = mutableStateOf(true)
        val followup = mutableStateOf(false)
        content(dark, stripes, preview, tuned, followup)
        val theme = if (dark) "dark" else "light"
        val mode = if (stripes) "stripes" else "mesh"
        val dir = File("build/outputs/refractive-followup-dashboard").also { it.mkdirs() }
        val prefix = "$theme-$mode"
        val contact = ShortcutTouchPose()
        val full = shortcutTouchTarget(ShortcutTouchVariant.FingerTilt, 0f, 0f)
        val offset = full.copy(offsetXDp = full.offsetXDp + 24f, offsetYDp = full.offsetYDp + 16f)
        val halfway = ShortcutTouchPose(
            scale = (contact.scale + offset.scale) / 2f,
            offsetXDp = (contact.offsetXDp + offset.offsetXDp) / 2f,
            offsetYDp = (contact.offsetYDp + offset.offsetYDp) / 2f,
            rotationX = (contact.rotationX + offset.rotationX) / 2f,
            rotationY = (contact.rotationY + offset.rotationY) / 2f
        )
        val references = mutableMapOf<String, Bitmap>()
        for (isNew in listOf(false, true)) {
            compose.runOnIdle {
                tuned.value = true
                followup.value = isNew
            }
            compose.waitForIdle()
            val profile = if (isNew) "new" else "old"
            // In old mode tuned stays enabled so the legacy 1.0.26 shader uses its old parameters;
            // only the optical follow-up switch distinguishes the new padded/world-space path.
            val frames = listOf("contact" to contact, "before" to contact, "after" to halfway,
                "full" to offset, "release" to contact)
            frames.forEach { (label, pose) ->
                compose.runOnIdle { preview.value = ShortcutTouchPreview("Nodes", pose) }
                compose.waitForIdle()
                val image = screenshot()
                save(image, dir, "$profile-$prefix-$label.png")
                if (label == "contact") references["$profile-contact"] = image
                if (label == "release") {
                    val baseline = references["$profile-contact"]
                    assertNotNull("$profile contact reference", baseline)
                    assertTrue("$profile release returns exactly to its own contact", requireNotNull(baseline).sameAs(image))
                    image.recycle()
                }
                if (label == "before") {
                    references["$profile-before"] = image
                }
                if (label == "after" || label == "full") image.recycle()
            }
        }
        references.values.forEach { it.recycle() }
    }

    private fun content(dark: Boolean, stripes: Boolean, preview: State<ShortcutTouchPreview?>,
                        tuned: State<Boolean>, followup: State<Boolean>) {
        val seconds = mutableFloatStateOf(24f)
        compose.setContent {
            MihomoTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "en", LocalSoftMeshTime provides seconds,
                    LocalSoftMeshSeed provides 734921L,
                    LocalShortcutTouchVariant provides ShortcutTouchVariant.FingerTilt,
                    LocalShortcutTouchPreview provides preview.value,
                    LocalShortcutBlurEnabled provides true,
                    LocalShortcutTunedMaterialEnabled provides tuned.value,
                    LocalShortcutOpticalFollowupEnabled provides followup.value
                ) {
                    ProvideGlassTheme {
                        val cardScene = rememberNavigationLens()
                        val dockScene = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            val cardRecorder = Modifier.recordNavigationScene(cardScene).let { recorder ->
                                if (!stripes) recorder else recorder.drawWithContent {
                                    drawContent()
                                    // Test-only stripe source is drawn inside the recorded scene layer.
                                    val spacing = 22.dp.toPx()
                                    for (x in -size.height.toInt()..size.width.toInt() step spacing.toInt().coerceAtLeast(1)) {
                                        drawLine(Color(0xFFEF365D).copy(alpha = .78f),
                                            androidx.compose.ui.geometry.Offset(x.toFloat(), 0f),
                                            androidx.compose.ui.geometry.Offset(x + size.height, size.height),
                                            strokeWidth = 4.dp.toPx())
                                    }
                                }
                            }
                            NexusGlassBackground(
                                modifier = Modifier.recordNavigationScene(dockScene),
                                backdropRecorder = cardRecorder
                            ) {
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

    private fun save(bitmap: Bitmap, dir: File, name: String) {
        dir.resolve(name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
