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
        val preview = mutableStateOf<ShortcutTouchPreview?>(ShortcutTouchPreview("Nodes", ShortcutTouchPose()))
        content(variant, dark, preview)
        val tag = compose.onNodeWithTag("shortcut-card-Nodes")
        compose.waitForIdle()
        val bounds = tag.fetchSemanticsNode().boundsInRoot
        val dir = File("build/outputs/shortcut-touch").also { it.mkdirs() }
        dir.resolve("bounds.json").writeText("[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]\n")
        val pressed = shortcutTouchTarget(variant, 0f, 0f)
        val following = shortcutTouchTarget(variant, 1f, .75f)
        val overshoot = when (variant) {
            ShortcutTouchVariant.GentleFloat -> .01f
            ShortcutTouchVariant.FingerTilt -> .026f
            ShortcutTouchVariant.BuoyantSpring -> .126f
        }
        val rebound = ShortcutTouchPose(
            scale = 1f + (1f - following.scale) * overshoot,
            offsetXDp = -following.offsetXDp * overshoot,
            offsetYDp = -following.offsetYDp * overshoot,
            rotationX = -following.rotationX * overshoot,
            rotationY = -following.rotationY * overshoot)
        val frames = listOf(ShortcutTouchPose(), pressed, following, rebound, ShortcutTouchPose())
        val baseline = screenshot()
        frames.forEachIndexed { index, pose ->
            compose.runOnIdle { preview.value = ShortcutTouchPreview("Nodes", pose) }
            compose.waitForIdle()
            val image = screenshot()
            save(image, "${variant.name}-${if(dark) "dark" else "light"}-$index.png")
            if (index == 4) assertTrue("neutral must exactly return", baseline.sameAs(image))
            if (index in 1..3) {
                assertFalse("touch keyframe must visibly change", baseline.sameAs(image))
                // The shader, background, neighbours, dock and all other UI remain frozen.
                for (y in 0 until image.height) for (x in 0 until image.width) {
                    val outsideX = x < bounds.left - 20 || x > bounds.right + 20
                    val outsideY = y < bounds.top - 25 || y > bounds.bottom + 25
                    if (outsideX || outsideY)
                        assertEquals("outside card changed at $x,$y", baseline.getPixel(x,y), image.getPixel(x,y))
                }
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
        val card = compose.onNodeWithTag("shortcut-card-Profiles")
        val before = screenshot()
        save(before, "${variant.name}-actual-rest.png")
        val restBounds = card.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        card.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        val immediate = screenshot()
        save(immediate, "${variant.name}-actual-down32ms.png")
        assertFalse("visible lift starts within first 32ms even with a frozen background", before.sameAs(immediate))
        val lifted = card.fetchSemanticsNode().boundsInRoot
        assertEquals("click and accessibility hit area must not move with the visual slab", restBounds, lifted)
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        val held = screenshot()
        save(held, "${variant.name}-actual-down128ms.png")
        held.recycle()
        before.recycle(); immediate.recycle()
        compose.runOnIdle { assertEquals("never activate on down", 0, clicks) }
        card.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, clicks) }
        card.performTouchInput { down(center); moveBy(Offset(0f, -120f), delayMillis=200); up() }
        compose.runOnIdle { assertEquals("scroll must cancel click", 1, clicks) }
        card.performClick()
        compose.runOnIdle { assertEquals("accessibility click retained", 2, clicks) }
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
