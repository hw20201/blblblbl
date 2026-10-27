package com.github.mihomo.android.ui.screens

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.glass.*
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

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProxiesGlassRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun resetGlass() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    @Test fun draftPolicyTitleStaysPinnedWhileNodesScrollBehindIt() {
        GlassDefaults.enabled = true
        val proxies = (0..40).map { Proxy("Node $it", delay = 40 + it) }
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        val backdrop = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().recordNavigationScene(backdrop)) {
                                NexusGlassBackground {}
                            }
                            val group = ProxyGroup("select", proxies, "Node 0")
                            ProxiesScreen(groups = listOf("Test"), groupMap = mapOf("Test" to group),
                                currentGroup = group, selectedGroupName = "Test", currentViewMode = "TABS",
                                onViewModeChanged = {}, onShowGroupIconsChanged = {}, onSelectGroup = {},
                                onSelectProxy = { _, _ -> }, onHealthCheck = {}, glassScene = backdrop)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val before = compose.onNodeWithText("策略").fetchSemanticsNode().boundsInRoot
        saveScreenshot("draft-policy-before-scroll")
        compose.onNodeWithTag("policy-node-list").performScrollToIndex(15)
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithText("策略").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Node 15").assertIsDisplayed()
        saveScreenshot("draft-policy-after-scroll")
    }

    @Test fun strategyCardsRenderAndRemainSelectableInLightDarkAndEveryColumnCount() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.BLUE
        val dark = mutableStateOf(false)
        val columns = mutableIntStateOf(2)
        val mode = mutableStateOf("TABS")
        val selected = mutableStateOf("Tokyo")
        val time = mutableFloatStateOf(8f)
        val proxies = listOf(
            Proxy("Tokyo", type = "Shadowsocks", delay = 45),
            Proxy("Osaka", type = "VMess", delay = 120),
            Proxy("Seoul", type = "Trojan", delay = -2),
            Proxy("YouTube", type = "GROUP", delay = 0),
            Proxy("Google", type = "GROUP", delay = 0),
            Proxy("AI", type = "GROUP", delay = 0),
            Proxy("GitHub", type = "GROUP", delay = 0),
            Proxy("Microsoft", type = "GROUP", delay = 0)
        )
        compose.setContent {
            MihomoTheme(if (dark.value) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh", LocalSoftMeshTime provides time,
                    LocalSoftMeshSeed provides 734921L) {
                    ProvideGlassTheme {
                        val backdrop = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().recordNavigationScene(backdrop)) {
                                NexusGlassBackground {}
                            }
                            val group = ProxyGroup("select", proxies, now = selected.value)
                            ProxiesScreen(
                                groups = listOf("Proxy", "Auto"),
                                groupMap = mapOf("Proxy" to group, "Auto" to group),
                                currentGroup = group, selectedGroupName = "Proxy",
                                currentViewMode = mode.value, nodeColumns = columns.intValue,
                                onViewModeChanged = { mode.value = it }, onShowGroupIconsChanged = {},
                                onNodeColumnsChanged = { columns.intValue = it }, onSelectGroup = {},
                                onSelectProxy = { _, proxy -> selected.value = proxy.name }, onHealthCheck = {},
                                glassScene = backdrop
                            )
                        }
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark }
            for (count in listOf(1, 2, 3)) {
                compose.runOnIdle { columns.intValue = count; selected.value = "Tokyo" }
                compose.waitForIdle()
                compose.onNodeWithText("Osaka").assertIsDisplayed().performClick()
                compose.waitForIdle()
                assertEquals("Osaka", selected.value)
                compose.onNodeWithTag("proxy-selected-glass-material").assertExists()
                saveScreenshot("proxies-${if (isDark) "dark" else "light"}-$count-columns")
            }
        }
        compose.runOnIdle { mode.value = "LIST"; dark.value = false }
        compose.waitForIdle()
        saveScreenshot("proxies-light-group-headers")
        compose.runOnIdle { GlassDefaults.enabled = false }
        compose.waitForIdle()
        saveScreenshot("proxies-classic-fallback")
    }

    private fun saveScreenshot(name: String) {
        val window = compose.activity.window
        val view = window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle { PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle()
        assertEquals("Robolectric native PixelCopy succeeds (not a real device)", PixelCopy.SUCCESS, result)
        val out = File("build/reports/proxies-glass/$name.png")
        out.parentFile!!.mkdirs()
        out.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
