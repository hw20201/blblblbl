package com.github.mihomo.android.ui.screens

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.core.model.Proxy
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.components.LoonFloatingBottomBar
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.glass.GlassPalettes
import com.github.mihomo.android.ui.glass.LocalSoftMeshSeed
import com.github.mihomo.android.ui.glass.LocalSoftMeshTime
import com.github.mihomo.android.ui.glass.NexusGlassBackground
import com.github.mihomo.android.ui.glass.ProvideGlassTheme
import com.github.mihomo.android.ui.glass.recordNavigationScene
import com.github.mihomo.android.ui.glass.rememberNavigationLens
import com.github.mihomo.android.ui.theme.LocalAppLanguage
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

/** Captures the actual dashboard picker at successive Compose animation-clock times. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardModePickerAnimationCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val shortcuts = listOf("nodes", "profiles", "rules", "request_logs")
    private var wasGlassEnabled = true
    private var oldPalette = GlassPalettes.CREAM

    @After fun restoreGlassDefaults() {
        GlassDefaults.enabled = wasGlassEnabled
        GlassDefaults.palette = oldPalette
    }

    @Test fun captureActualCapsuleExpansionFrames() {
        wasGlassEnabled = GlassDefaults.enabled
        oldPalette = GlassDefaults.palette
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        val meshTime = mutableFloatStateOf(24f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "zh",
                    LocalSoftMeshTime provides meshTime,
                    LocalSoftMeshSeed provides 734921L
                ) {
                    ProvideGlassTheme {
                        val backgroundLens = rememberNavigationLens()
                        val pageLens = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(
                                modifier = Modifier.recordNavigationScene(backgroundLens),
                                backdropRecorder = Modifier.recordNavigationScene(pageLens)
                            ) {
                                DashboardScreen(
                                    scrollState = ScrollState(0), vpnState = VpnState(status = VpnState.Status.STOPPED),
                                    currentMode = TunnelMode.RULE, activeProfileName = "本地配置", mainGroupName = "默认主组",
                                    mainGroupProxies = emptyList<Proxy>(), activeNodeName = "自动选择",
                                    totalNodes = 24, totalRules = 128, visibleShortcuts = shortcuts.toSet(), shortcutOrder = shortcuts,
                                    onToggleVpn = {}, onModeSelected = {}, onSelectProxy = { _, _ -> }, onHealthCheck = {},
                                    onNavigateToProfiles = {}, onNavigateToAppRouting = {}, onNavigateToLogs = {},
                                    onNavigateToRules = {}, onNavigateToDns = {}, onNavigateToRequests = {},
                                    onNavigateToNetworkShare = {}, onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = {},
                                    glassScene = pageLens
                                )
                            }
                            LoonFloatingBottomBar(0, {}, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), backgroundLens)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        // Let first composition settle (notably the measured status/header inset) before the
        // baseline frame, so the demo starts from the real resting dashboard state.
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val output = File("build/outputs/mode-picker-animation").also {
            it.mkdirs()
            it.listFiles { file -> file.extension == "png" }?.forEach(File::delete)
        }
        val frameTimes = mutableListOf<Int>()
        val collapsedBounds = compose.onNodeWithTag("mode-picker-trigger").fetchSemanticsNode().boundsInRoot
        val capsuleBounds = compose.onNodeWithTag("mode-picker-capsule", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        println("COLLAPSED trigger=$collapsedBounds capsule=$capsuleBounds")
        assertTrue("resting trigger should stay a compact capsule: $collapsedBounds", collapsedBounds.width in 132f..140f && collapsedBounds.height in 46f..50f)
        assertTrue("the capsule label is present before the menu opens", capsuleBounds.width > 100f && capsuleBounds.height in 42f..50f)
        compose.onNodeWithText("规则模式").assertIsDisplayed()
        var boundsAt200ms: androidx.compose.ui.geometry.Rect? = null
        var boundsAt250ms: androidx.compose.ui.geometry.Rect? = null
        var boundsAt300ms: androidx.compose.ui.geometry.Rect? = null
        var boundsAt350ms: androidx.compose.ui.geometry.Rect? = null
        var expandedBoundsAt500ms: androidx.compose.ui.geometry.Rect? = null
        val measuredBounds = mutableListOf<String>()
        var lastAnimatedBounds: androidx.compose.ui.geometry.Rect? = null
        saveFrame(output, 0)
        frameTimes += 0
        compose.onNodeWithTag("mode-picker-trigger").performClick()
        for (index in 1..11) {
            compose.mainClock.advanceTimeBy(50)
            saveFrame(output, index)
            frameTimes += index * 50
            val activeTag = if (compose.onAllNodesWithTag("mode-picker-panel").fetchSemanticsNodes().isNotEmpty())
                "mode-picker-panel" else "mode-picker-trigger"
            val measured = compose.onNodeWithTag(activeTag).fetchSemanticsNode().boundsInRoot
            measuredBounds += "$index,$activeTag,${measured.left},${measured.top},${measured.width},${measured.height}"
            val currentCapsule = compose.onNodeWithTag("mode-picker-capsule", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("the capsule width must not change during expansion", capsuleBounds.width, currentCapsule.width, 1f)
            assertEquals("the capsule height must not change during expansion", capsuleBounds.height, currentCapsule.height, 1f)
            assertEquals("the capsule must remain anchored in place", capsuleBounds.top, currentCapsule.top, .5f)
            assertEquals("the capsule must not shift horizontally", capsuleBounds.left, currentCapsule.left, .5f)
            if (activeTag == "mode-picker-panel") {
                for (option in listOf("global", "direct")) {
                    val nodes = compose.onAllNodesWithTag("mode-option-$option", useUnmergedTree = true).fetchSemanticsNodes()
                    for (node in nodes) {
                        val bounds = node.boundsInRoot
                        // A child clipped completely above the shell's current bottom can
                        // report an empty semantic rect; that is the intended downward reveal.
                        // Validate its geometry once any part is actually visible.
                        if (bounds.width > 0f && bounds.height > 0f) {
                            assertTrue("$option row must begin below the fixed capsule: row=$bounds capsule=$capsuleBounds",
                                bounds.top >= capsuleBounds.bottom - 1f)
                            assertTrue("$option row must fit the unchanged panel width: row=$bounds panel=$measured",
                                bounds.left >= measured.left + 3f && bounds.right <= measured.right - 3f)
                            if (index == 11) {
                                assertTrue("settled $option row must be fully revealed inside the panel: row=$bounds panel=$measured",
                                    bounds.bottom <= measured.bottom - 3f)
                            }
                        }
                    }
                }
            }
            val widthDelta = measured.width - collapsedBounds.width
            val heightDelta = measured.height - (lastAnimatedBounds?.height ?: collapsedBounds.height)
            println("CAPTURE_${index * 50} tag=$activeTag bounds=$measured delta=($widthDelta,$heightDelta)")
            assertTrue("the capsule/menu width must never grow", kotlin.math.abs(widthDelta) <= 1f)
            assertTrue("the menu must only extend downward", measured.top == collapsedBounds.top && measured.height >= collapsedBounds.height)
            if (index == 4) boundsAt200ms = measured
            if (index == 5) boundsAt250ms = measured
            if (index == 6) boundsAt300ms = measured
            if (index == 7) boundsAt350ms = measured
            if (index == 10) expandedBoundsAt500ms = measured
            lastAnimatedBounds = measured
        }
        output.resolve("timeline-ms.txt").writeText("index,tag,left,top,width,height\n" + measuredBounds.joinToString("\n") + "\n")
        val at200 = boundsAt200ms!!
        assertEquals("the clicked capsule retains its original width", collapsedBounds.width, at200.width, 1f)
        assertEquals("the capsule remains anchored at its original top", collapsedBounds.top, at200.top, .5f)
        assertTrue("the panel grows only downward from the unchanged capsule", at200.height > collapsedBounds.height)
        val at250 = boundsAt250ms!!
        val at300 = boundsAt300ms!!
        val at350 = boundsAt350ms!!
        assertEquals(collapsedBounds.width, at250.width, 1f)
        assertEquals(collapsedBounds.width, at300.width, 1f)
        assertEquals(collapsedBounds.width, at350.width, 1f)
        assertTrue("panel height should progress monotonically", at250.height >= at200.height && at300.height >= at250.height && at350.height >= at300.height)
        compose.onNodeWithTag("mode-picker-panel").assertExists()
        compose.onNodeWithTag("mode-option-global", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("mode-option-direct", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("mode-option-rule", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("全局模式").assertIsDisplayed()
        compose.onNodeWithText("直连模式").assertIsDisplayed()
        val titleBounds = compose.onNodeWithTag("dashboard-nexus-title").fetchSemanticsNode().boundsInRoot
        val panelBounds = compose.onNodeWithTag("mode-picker-panel").fetchSemanticsNode().boundsInRoot
        assertTrue("expanded menu must remain below the NEXUS title", panelBounds.top >= titleBounds.bottom)
        assertEquals("expanded menu keeps the capsule's exact width", collapsedBounds.width, panelBounds.width, 1f)
        assertEquals("expanded menu extends down to show two alternatives", 144f, panelBounds.height, 2f)
        for (option in listOf("global", "direct")) {
            val row = compose.onNodeWithTag("mode-option-$option", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("settled $option row lies fully inside the expanded panel: row=$row panel=$panelBounds",
                row.left >= panelBounds.left + 3f && row.right <= panelBounds.right - 3f && row.top >= panelBounds.top + 35f && row.bottom <= panelBounds.bottom - 3f)
        }
        assertEquals("expanded menu keeps the capsule's anchor", collapsedBounds.top, panelBounds.top, .5f)
        assertEquals("all actual-UI PNG frames should be written", 12, output.listFiles { f -> f.name.endsWith(".png") }?.size)
        // No tap-highlight/ripple is expected; the capture validates only the stable capsule
        // bounds and the smooth downward morph through its real rendered frames.
    }

    private fun saveFrame(directory: File, index: Int) {
        compose.waitForIdle()
        val window = compose.activity.window
        val width = window.decorView.width
        val height = window.decorView.height
        assertTrue("real Compose window must have a size", width > 0 && height > 0)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val result = AtomicInteger(-1)
        compose.runOnIdle {
            PixelCopy.request(window, bitmap, { result.set(it) }, Handler(Looper.getMainLooper()))
        }
        compose.waitUntil(5000) { result.get() != -1 }
        assertEquals("PixelCopy must capture a rendered app frame", PixelCopy.SUCCESS, result.get())
        directory.resolve("frame-%02d.png".format(index)).outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
