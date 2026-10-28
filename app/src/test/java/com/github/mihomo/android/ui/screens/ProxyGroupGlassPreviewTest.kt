package com.github.mihomo.android.ui.screens

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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.components.LoonFloatingBottomBar
import com.github.mihomo.android.ui.glass.LocalShortcutGlassQualityOverride
import com.github.mihomo.android.ui.glass.ShortcutGlassQuality
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
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProxyGroupGlassPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val names = listOf(
        "默认代理", "手动选择", "自动选择", "负载均衡", "FCM", "YouTube", "Google", "AI",
        "GitHub", "Microsoft", "Apple", "Telegram", "Steam", "TikTok", "Twitter",
        "Meta", "Line", "Netflix", "Disney+", "Spotify", "Tokyo", "Osaka"
    )

    @After fun resetGlass() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    @Test fun lightTop() = render(false, false)
    @Test fun darkTop() = render(true, false)
    @Test fun lightScrolled() = render(false, true)
    @Test fun darkScrolled() = render(true, true)

    @Test @Config(sdk = [31]) fun api31Shared() = render(false, false, "api31-shared")
    @Test @Config(sdk = [26]) fun api26Flat() = render(false, false, "api26-flat")
    @Test fun lowRamFlat() = render(false, false, "lowram-flat", ShortcutGlassQuality.Flat)
    @Test fun classicFallback() = render(false, false, "classic", glass = false)

    private fun render(dark: Boolean, scrolled: Boolean, label: String = "",
        quality: ShortcutGlassQuality? = null, glass: Boolean = true) {
        GlassDefaults.enabled = glass
        GlassDefaults.palette = GlassPalettes.BLUE
        val stage = System.getenv("PROXY_GLASS_STAGE") ?: System.getProperty("proxyGlassStage", "preview")
        val time = mutableFloatStateOf(0f)
        var screenCompositions = 0
        val selected = mutableStateOf("YouTube")
        val selectProxyCalls = mutableListOf<Pair<String, String>>()
        val testNodeCalls = mutableListOf<Pair<String, String>>()
        val selectGroupCalls = mutableListOf<String>()
        val groupNames = names
        val proxies = groupNames.mapIndexed { index, name ->
            if (index >= groupNames.size - 2) Proxy(name, type = "Shadowsocks", delay = -1)
            else Proxy(name, type = "GROUP", delay = 0, isGroup = true)
        }
        val group = ProxyGroup("select", proxies, now = selected.value)
        val tabStates = mutableStateMapOf("总策略" to LazyListState(firstVisibleItemIndex = if (scrolled) 5 else 0))

        compose.setContent {
            MihomoTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "zh",
                    LocalSoftMeshTime provides time,
                    LocalSoftMeshSeed provides 734921L,
                    LocalShortcutGlassQualityOverride provides quality
                ) {
                    ProvideGlassTheme {
                        val cardScene = rememberNavigationLens()
                        val dockScene = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(
                                modifier = Modifier.recordNavigationScene(dockScene),
                                backdropRecorder = Modifier.recordNavigationScene(cardScene)
                            ) {
                                SideEffect { screenCompositions++ }
                                ProxiesScreen(
                                    groups = listOf("总策略", "TikTok", "Twitter", "Meta", "Line", "备用策略"),
                                    groupMap = mapOf("总策略" to group, "备用策略" to group),
                                    currentGroup = group,
                                    selectedGroupName = "总策略",
                                    currentViewMode = "TABS",
                                    nodeColumns = 2,
                                    onViewModeChanged = {},
                                    onShowGroupIconsChanged = {},
                                    onSelectGroup = { selectGroupCalls += it },
                                    onSelectProxy = { groupName, proxy -> selectProxyCalls += groupName to proxy.name },
                                    onHealthCheck = {},
                                    onTestSingleNode = { groupName, node -> testNodeCalls += groupName to node },
                                    perGroupTabStates = tabStates,
                                    glassScene = if (stage == "before") null else cardScene
                                )
                            }
                            LoonFloatingBottomBar(1, {}, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), dockScene)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        val visibleGroupBounds = groupNames.filter { name ->
            compose.onAllNodesWithTag("proxy-card-$name", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }.associateWith { name ->
            val rect = compose.onNodeWithTag("proxy-card-$name", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            mapOf("left" to rect.left, "top" to rect.top, "right" to rect.right, "bottom" to rect.bottom)
        }
        assertTrue("At least one proxy group title should be visible", visibleGroupBounds.isNotEmpty())
        if (stage != "before" && glass) {
            assertTrue("Strategy node rows must not replay a per-card glass backdrop",
                compose.onAllNodesWithTag("proxy-group-glass-material", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        }
        val mode = if (dark) "dark" else "light"
        val suffix = (if (scrolled) "scroll" else "top") + (if (label.isEmpty()) "" else "-$label")
        val outputDir = File("build/outputs/proxy-group-glass")
        outputDir.mkdirs()
        File(outputDir, "$stage-$mode-$suffix-bounds.json").writeText(
            visibleGroupBounds.entries.joinToString(prefix = "{\n", postfix = "\n}\n", separator = ",\n") { (name, rect) ->
                "  \"${name.replace("\\", "\\\\").replace("\"", "\\\"")}\": {\"left\": ${rect["left"]}, \"top\": ${rect["top"]}, \"right\": ${rect["right"]}, \"bottom\": ${rect["bottom"]}}"
            }
        )
        saveScreenshot(File(outputDir, "$stage-$mode-$suffix.png"))
        val stableCompositions = screenCompositions
        compose.runOnIdle { time.floatValue = 24f }
        compose.waitForIdle()
        saveScreenshot(File(outputDir, "$stage-$mode-$suffix-t24.png"))
        assertEquals("Background draw-only motion does not recompose strategy content", stableCompositions, screenCompositions)
        compose.runOnIdle { time.floatValue = 0f }
        compose.waitForIdle()

        // Interactions run only after the image is captured so every mode keeps the same preview state.
        compose.runOnIdle { runBlocking { tabStates["总策略"]?.scrollToItem(0) } }
        compose.waitForIdle()
        compose.onNodeWithText("总策略").performClick()
        compose.waitForIdle()
        assertEquals(listOf("总策略"), selectGroupCalls)
        compose.onNodeWithText("手动选择").performClick()
        compose.waitForIdle()
        assertEquals("总策略" to "手动选择", selectProxyCalls.last())
        compose.onAllNodesWithText("--").fetchSemanticsNodes().first()
        compose.onAllNodesWithText("--")[0].performClick()
        compose.waitForIdle()
        assertEquals("总策略" to "默认代理", testNodeCalls.last())
        compose.runOnIdle { runBlocking { tabStates["总策略"]?.scrollToItem(10) } }
        compose.waitForIdle()
        compose.onNodeWithText("Tokyo").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals("总策略" to "Tokyo", selectProxyCalls.last())
    }

    private fun saveScreenshot(file: File) {
        val window = compose.activity.window
        val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        var result = PixelCopy.ERROR_UNKNOWN
        compose.runOnIdle {
            PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
        }
        compose.waitForIdle()
        assertEquals("Robolectric native PixelCopy succeeds (not a real-device screenshot)", PixelCopy.SUCCESS, result)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
