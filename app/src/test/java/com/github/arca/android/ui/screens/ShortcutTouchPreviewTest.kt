package com.github.arca.android.ui.screens

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
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.components.*
import com.github.arca.android.ui.glass.*
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Frozen visual keyframes use exactly the production transform modifier, not edited screenshots. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutTouchPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun gentleLight() = render(ShortcutTouchVariant.GentleFloat, false)
    @Test fun tiltLight() = render(ShortcutTouchVariant.FingerTilt, false)
    @Test fun springLight() = render(ShortcutTouchVariant.BuoyantSpring, false)
    @Test fun gentleDark() = render(ShortcutTouchVariant.GentleFloat, true)
    @Test fun tiltDark() = render(ShortcutTouchVariant.FingerTilt, true)
    @Test fun springDark() = render(ShortcutTouchVariant.BuoyantSpring, true)

    private fun render(variant: ShortcutTouchVariant, dark: Boolean) {
        val rest = shortcutLiveRestingPose(variant)
        val preview = mutableStateOf<ShortcutTouchPreview?>(ShortcutTouchPreview("Nodes", rest))
        content(variant, dark, preview)
        val tag = compose.onNodeWithTag("shortcut-card-Nodes")
        compose.waitForIdle()
        val bounds = tag.fetchSemanticsNode().boundsInRoot
        val dir = File("build/outputs/shortcut-touch").also { it.mkdirs() }
        dir.resolve("bounds.json").writeText("[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]\n")
        // B starts elevated; its live centre contact preserves that resting offset while
        // adding zero tilt. The legacy frozen target omits the live rest offset.
        val pressed = if (variant == ShortcutTouchVariant.FingerTilt)
            shortcutLiveTouchTarget(variant, 0f, 0f)
        else shortcutTouchTarget(variant, 0f, 0f)
        val following = if (variant == ShortcutTouchVariant.FingerTilt)
            shortcutLiveTouchTarget(variant, 1f, .75f)
        else shortcutTouchTarget(variant, 1f, .75f)
        val overshoot = when (variant) {
            ShortcutTouchVariant.GentleFloat -> .01f
            ShortcutTouchVariant.FingerTilt -> .026f
            ShortcutTouchVariant.BuoyantSpring -> .126f
        }
        val rebound = ShortcutTouchPose(
            scale = 1f + (1f - following.scale) * overshoot,
            offsetXDp = rest.offsetXDp - (following.offsetXDp - rest.offsetXDp) * overshoot,
            offsetYDp = rest.offsetYDp - (following.offsetYDp - rest.offsetYDp) * overshoot,
            rotationX = -following.rotationX * overshoot,
            rotationY = -following.rotationY * overshoot)
        val frames = listOf(rest, pressed, following, rebound, rest)
        val baseline = screenshot()
        frames.forEachIndexed { index, pose ->
            compose.runOnIdle { preview.value = ShortcutTouchPreview("Nodes", pose) }
            compose.waitForIdle()
            val image = screenshot()
            save(image, "${variant.name}-${if(dark) "dark" else "light"}-$index.png")
            // v91 renders the card on the Glasmio backdrop; each backdrop re-capture
            // shifts anti-aliased edges (notably the VPN switch thumb) by a sub-pixel, so
            // exact whole-frame equality no longer holds. The card itself is deterministic
            // for an unchanged pose, so these helpers check the card tightly and the rest
            // of the frame with a loose backdrop-noise tolerance.
            if (index == 4) assertFramesNearlyEqual(baseline, image, bounds, "neutral must exactly return")
            if (index == 1 && variant == ShortcutTouchVariant.FingerTilt) {
                assertFramesNearlyEqual(baseline, image, bounds, "B centre press stays at rest without scale or lift")
            } else if (index in 1..3) {
                assertCardRegionVisiblyChanged(baseline, image, bounds, "touch keyframe must visibly change")
                // The shader, background, neighbours, dock and all other UI remain frozen.
                assertOutsideRegionNearlyUnchanged(baseline, image, bounds, 20f, 25f, "outside card changed")
            }
            image.recycle()
        }
        baseline.recycle()
    }

    @Test fun gentleTapAndScroll() = verifyTapAndScroll(ShortcutTouchVariant.GentleFloat)
    @Test fun tiltTapAndScroll() = verifyTapAndScroll(ShortcutTouchVariant.FingerTilt)
    @Test fun springTapAndScroll() = verifyTapAndScroll(ShortcutTouchVariant.BuoyantSpring)

    @Test fun editingDisablesTouchMotionAndRetainsDelete() {
        var clicks = 0
        var deletes = 0
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalShortcutTouchVariant provides ShortcutTouchVariant.BuoyantSpring) {
                    LoonShortcutCard(Modifier.width(174.dp), "Editing", "", {}, isEditing=true,
                        onDelete={deletes++}, onClick={clicks++})
                }
            }
        }
        compose.onNodeWithTag("shortcut-card-Editing").performTouchInput { click(center) }
        compose.onNodeWithText("−").performClick()
        compose.runOnIdle { assertEquals(0, clicks); assertEquals(1, deletes) }
    }

    private fun verifyTapAndScroll(variant: ShortcutTouchVariant) {
        var clicks = 0
        content(variant, false, mutableStateOf(null), { clicks++ })
        // The Dashboard's inner card tag can alias another slot's coordinates in this
        // merged semantics tree. Use the positioned parent slot for physical input.
        val slot = compose.onNodeWithTag("shortcut-profiles")
        val restBounds = slot.fetchSemanticsNode().boundsInRoot
        val pressPoint = if (variant == ShortcutTouchVariant.FingerTilt)
            Offset(restBounds.left + restBounds.width * .85f, restBounds.top + restBounds.height * .2f)
        else Offset(restBounds.center.x, restBounds.center.y)

        // Keep down, hold, and up in one injection. Ending a performTouchInput block
        // cancels its pointer, so a later independent up() is not the same held gesture.
        // v91: tapping a shortcut opens its morph action panel instead of navigating.
        compose.onRoot().performTouchInput { click(pressPoint) }
        compose.waitForIdle()
        assertEquals("visual motion must not move the click/accessibility slot", restBounds,
            slot.fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("shortcut-panel-profiles-sheet").assertExists()
        compose.runOnIdle { assertEquals("a complete short tap opens the action panel instead of navigating", 0, clicks) }
        // Close the panel so the scroll gesture reaches the Dashboard again.
        compose.onNodeWithTag("shortcut-panel-close").performClick()
        compose.waitForIdle()

        compose.onRoot().performTouchInput {
            down(pressPoint)
            moveTo(pressPoint + Offset(0f, -60f), delayMillis = 80)
            moveTo(pressPoint + Offset(0f, -120f), delayMillis = 100)
            up()
        }
        compose.waitForIdle()
        assertEquals("scroll gesture leaves the hit slot fixed", restBounds, slot.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals("scroll must not navigate", 0, clicks) }
        slot.performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("shortcut-panel-profiles-sheet").assertExists()
        compose.runOnIdle { assertEquals("accessibility click opens the panel instead of navigating", 0, clicks) }
    }

    private fun content(variant: ShortcutTouchVariant, dark: Boolean, preview: State<ShortcutTouchPreview?>, onProfiles: () -> Unit = {}) {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        val seconds = mutableFloatStateOf(24f)
        compose.setContent {
            MihomoTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "en", LocalSoftMeshTime provides seconds,
                    LocalSoftMeshSeed provides 734921L, LocalShortcutTouchVariant provides variant,
                    LocalShortcutTouchPreview provides preview.value) {
                    ProvideGlassTheme {
                        val cardScene = rememberNavigationLens()
                        val dockScene = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(modifier=Modifier.recordNavigationScene(dockScene), backdropRecorder=Modifier.recordNavigationScene(cardScene)) {
                                val shortcuts = listOf("nodes", "profiles", "rules", "request_logs")
                                DashboardScreen(vpnState=VpnState(status=VpnState.Status.STOPPED), currentMode=TunnelMode.RULE,
                                    activeProfileName="Local config", mainGroupName="", mainGroupProxies=emptyList(), activeNodeName="",
                                    totalNodes=12,totalRules=64,visibleShortcuts=shortcuts.toSet(),shortcutOrder=shortcuts,
                                    onToggleVpn={},onModeSelected={},onSelectProxy={_,_->},onHealthCheck={},onNavigateToProfiles=onProfiles,
                                    onNavigateToAppRouting={},onNavigateToLogs={},onUpdateVisibleShortcuts={},onUpdateShortcutOrder={},glassScene=cardScene)
                            }
                            LoonFloatingBottomBar(0,{},Modifier.align(Alignment.BottomCenter).padding(bottom=16.dp),dockScene)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun screenshot(): Bitmap {
        val window=compose.activity.window
        val bitmap=Bitmap.createBitmap(window.decorView.width,window.decorView.height,Bitmap.Config.ARGB_8888)
        val result=AtomicInteger(-1)
        compose.runOnIdle { PixelCopy.request(window,bitmap,{result.set(it)},Handler(Looper.getMainLooper())) }
        compose.waitForIdle()
        compose.waitUntil(5000) { result.get()!=-1 }
        assertEquals(PixelCopy.SUCCESS,result.get())
        return bitmap
    }
    private fun save(bitmap: Bitmap,name: String) {
        File("build/outputs/shortcut-touch").also{it.mkdirs()}.resolve(name).outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
    }
}
