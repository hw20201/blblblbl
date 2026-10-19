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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import com.github.mihomo.android.ui.components.LoonFloatingBottomBar
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.UiStyle
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.components.LocalGlassBackdrop
import com.github.mihomo.android.ui.components.LocalMainBottomBarInset
import com.github.mihomo.android.ui.components.glassBackdropSource
import com.github.mihomo.android.ui.components.rememberGlassBackdrop
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h711dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewAcceptanceRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val shortcutOrder = listOf("nodes", "profiles", "ip_test", "request_logs", "dns", "routing")
    private val nodes = listOf(
        Proxy("🇭🇰 香港 01", title = "🇭🇰 香港 01", delay = 28),
        Proxy("🇭🇰 香港 02", title = "🇭🇰 香港 02", delay = 35),
        Proxy("🇯🇵 日本 01", title = "🇯🇵 日本 01", delay = 62),
        Proxy("🇯🇵 日本 02", title = "🇯🇵 日本 02", delay = 71),
        Proxy("🇸🇬 新加坡 01", title = "🇸🇬 新加坡 01", delay = 54),
        Proxy("🇸🇬 新加坡 02", title = "🇸🇬 新加坡 02", delay = 68),
        Proxy("🇺🇸 美国 01", title = "🇺🇸 美国 01", delay = 168),
        Proxy("🇺🇸 美国 02", title = "🇺🇸 美国 02", delay = -2),
    )

    private fun pixelCopy(): Bitmap {
        val window = compose.activity.window
        val decor = window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle {
            PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
        }
        compose.waitForIdle()
        org.junit.Assert.assertEquals("native hardware window PixelCopy must succeed", PixelCopy.SUCCESS, result)
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String) {
        val output = File("build/reports/preview-acceptance/$name.png")
        output.parentFile.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun changedPixels(a: Bitmap, b: Bitmap, bounds: androidx.compose.ui.geometry.Rect): Int {
        require(a.width == b.width && a.height == b.height)
        var changed = 0
        for (y in bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(a.height) step 2) for (x in bounds.left.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(a.width) step 2) {
            if (a.getPixel(x, y) != b.getPixel(x, y)) changed++
        }
        return changed
    }

    @Test
    fun realScreensRenderAndGlassChangesSurfacePixels() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val style = mutableStateOf(UiStyle.CUPERTINO)
        val glassEnabled = mutableStateOf(true)
        val intensity = mutableStateOf(0f)
        val page = mutableStateOf("dashboard")
        compose.setContent {
            MihomoTheme(
                themeMode = mode.value,
                uiStyle = style.value,
                glassEnabled = glassEnabled.value,
                glassIntensity = intensity.value
            ) {
                val backdrop = rememberGlassBackdrop()
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, 1f),
                    LocalAppLanguage provides "zh",
                    LocalGlassBackdrop provides backdrop,
                    LocalMainBottomBarInset provides 76.dp
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.width(400.dp).fillMaxSize().glassBackdropSource(backdrop)) {
                            when (page.value) {
                                "dashboard" -> DashboardScreen(
                                    scrollState = ScrollState(0),
                                    vpnState = VpnState(status = VpnState.Status.RUNNING, upSpeed = "128 KB/s", downSpeed = "2.36 MB/s"),
                                    currentMode = TunnelMode.RULE,
                                    activeProfileName = "自选配置",
                                    mainGroupName = "节点选择",
                                    mainGroupProxies = nodes,
                                    activeNodeName = nodes.first().name,
                                    totalNodes = nodes.size,
                                    totalRules = 326,
                                    requestMonitoringEnabled = true,
                                    visibleShortcuts = shortcutOrder.toSet(),
                                    shortcutOrder = shortcutOrder,
                                    onToggleVpn = {}, onModeSelected = {}, onSelectProxy = { _, _ -> },
                                    onHealthCheck = {}, onNavigateToProfiles = {}, onNavigateToAppRouting = {},
                                    onNavigateToLogs = {}, onNavigateToRules = {}, onNavigateToDns = {},
                                    onNavigateToRequests = {}, onNavigateToNetworkShare = {},
                                    onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = {},
                                    uiStyle = style.value
                                )
                                "proxies" -> ProxiesScreen(
                                    groups = listOf("节点选择", "自动选择", "故障转移"),
                                    groupMap = mapOf(
                                        "节点选择" to ProxyGroup(type = "Selector", proxies = nodes, now = nodes.first().name),
                                        "自动选择" to ProxyGroup(type = "URLTest", proxies = nodes, now = nodes[1].name),
                                        "故障转移" to ProxyGroup(type = "Fallback", proxies = nodes, now = nodes[0].name)
                                    ),
                                    currentGroup = ProxyGroup(type = "Selector", proxies = nodes, now = nodes.first().name),
                                    selectedGroupName = "节点选择",
                                    currentViewMode = "TABS",
                                    nodeColumns = 2,
                                    onViewModeChanged = {}, onShowGroupIconsChanged = {}, onNodeColumnsChanged = {},
                                    onSelectGroup = {}, onSelectProxy = { _, _ -> }, onHealthCheck = {},
                                    onTestSingleNode = { _, _ -> }
                                )
                                else -> SettingsScreen(
                                    scrollState = ScrollState(0), tunStack = "system",
                                    bootOnStartup = true, allowLan = true, fakeIpEnabled = true, hapticEnabled = true,
                                    onTunStackChanged = {}, onBootOnStartupChanged = {}, onAllowLanChanged = {},
                                    onFakeIpEnabledChanged = {}, onHapticEnabledChanged = {},
                                    onNavigateToConfigurationCenter = {}, onNavigateToLogs = {},
                                    onNavigateToAppearance = {}, onNavigateToDns = {}
                                )
                            }
                        }
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            LoonFloatingBottomBar(when (page.value) { "dashboard" -> 0; "proxies" -> 1; else -> 2 }, {}, Modifier.padding(bottom = 16.dp))
                        }
                    }
                }
            }
        }

        for (targetStyle in UiStyle.entries) {
            style.value = targetStyle
            mode.value = ThemeMode.LIGHT
            for (screen in listOf("dashboard", "proxies", "settings")) {
                page.value = screen
                glassEnabled.value = true
                intensity.value = 0f
                compose.waitForIdle()
                when (screen) {
                    "dashboard" -> compose.onNodeWithText("Nexus").assertExists()
                    "proxies" -> compose.onAllNodesWithText("策略")[0].assertExists()
                    else -> compose.onAllNodesWithText("设置")[0].assertExists()
                }
                val materialBounds = when (screen) {
                    "dashboard" -> compose.onNodeWithTag("glass-home-hero").fetchSemanticsNode().boundsInRoot
                    "proxies" -> compose.onNodeWithTag("glass-active-node").fetchSemanticsNode().boundsInRoot
                    else -> compose.onNodeWithContentDescription("开机自启动").fetchSemanticsNode().boundsInRoot
                }
                val onZero = pixelCopy()
                save(onZero, "$screen-${targetStyle.name.lowercase()}-light-glass-0")
                glassEnabled.value = false
                compose.waitForIdle()
                val off = pixelCopy()
                save(off, "$screen-${targetStyle.name.lowercase()}-light-glass-off")
                val offDelta = changedPixels(onZero, off, materialBounds)
                assertTrue("$screen $targetStyle glass on/off must alter pixels INSIDE actual material bounds: $offDelta", offDelta > 20)
                glassEnabled.value = true
                intensity.value = 1f
                compose.waitForIdle()
                val onFull = pixelCopy()
                save(onFull, "$screen-${targetStyle.name.lowercase()}-light-glass-1")
                val intensityDelta = changedPixels(onZero, onFull, materialBounds)
                assertTrue("$screen $targetStyle intensity 0/1 must alter pixels INSIDE actual material bounds: $intensityDelta", intensityDelta > 20)
                // Confirm screen content remains present after changing glass parameters.
                when (screen) {
                    "dashboard" -> compose.onNodeWithText("Nexus").assertExists()
                    "proxies" -> compose.onAllNodesWithText("策略")[0].assertExists()
                    else -> compose.onAllNodesWithText("设置")[0].assertExists()
                }
                onZero.recycle(); off.recycle(); onFull.recycle()
            }
            // Dark-mode smoke screenshot per actual production screen and style.
            mode.value = ThemeMode.DARK
            glassEnabled.value = true
            intensity.value = 1f
            for (screen in listOf("dashboard", "proxies", "settings")) {
                page.value = screen
                compose.waitForIdle()
                val image = pixelCopy()
                save(image, "$screen-${targetStyle.name.lowercase()}-dark-smoke")
                image.recycle()
            }
        }
    }
}
