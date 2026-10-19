package com.github.mihomo.android.ui.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.components.LoonFloatingBottomBar
import com.github.mihomo.android.ui.screens.DashboardScreen
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationLensPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun reset() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    @Test fun dashboardPreviewsAndNavigationCallbacks() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val selected = mutableStateOf(0)
        val shortcuts = listOf("nodes", "profiles", "rules", "request_logs")
        compose.setContent {
            MihomoTheme(themeMode = mode.value) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        val lens = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(Modifier.recordNavigationScene(lens)) {
                                DashboardScreen(
                                    vpnState = VpnState(status = VpnState.Status.STOPPED),
                                    currentMode = TunnelMode.RULE,
                                    activeProfileName = "未选择配置",
                                    mainGroupName = "", mainGroupProxies = emptyList(), activeNodeName = "",
                                    totalNodes = 0, totalRules = 0,
                                    visibleShortcuts = shortcuts.toSet(), shortcutOrder = shortcuts,
                                    onToggleVpn = {}, onModeSelected = {}, onSelectProxy = { _, _ -> },
                                    onHealthCheck = {}, onNavigateToProfiles = {}, onNavigateToAppRouting = {},
                                    onNavigateToLogs = {}, onUpdateVisibleShortcuts = {}, onUpdateShortcutOrder = {}
                                )
                            }
                            LoonFloatingBottomBar(
                                selectedTab = selected.value, onTabSelected = { selected.value = it },
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), lens = lens
                            )
                        }
                    }
                }
            }
        }
        for ((name, palette) in listOf("cream" to GlassPalettes.CREAM, "blue" to GlassPalettes.BLUE,
            "white" to GlassPalettes.WHITE, "midnight" to GlassPalettes.MIDNIGHT)) {
            compose.runOnIdle {
                mode.value = if (palette.isDark) ThemeMode.DARK else ThemeMode.LIGHT
                GlassDefaults.palette = palette
            }
            compose.waitForIdle()
            save(name)
        }
        compose.onNodeWithText("设置").performClick()
        compose.runOnIdle { assertEquals(2, selected.value) }
        compose.onNodeWithText("仪表").performClick()
        compose.runOnIdle { assertEquals(0, selected.value) }
    }

    private fun save(name: String) {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle {
            PixelCopy.request(compose.activity.window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
        }
        compose.waitForIdle()
        assertEquals(PixelCopy.SUCCESS, result)
        val file = File("build/reports/navigation-lens/dashboard-$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
