package com.github.mihomo.android.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.UiStyle
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.components.LoonFloatingBottomBar
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SquaredGlassRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dashboardStylesAndGlassSettingsRenderAsNativeWindowScreenshots() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val style = mutableStateOf(UiStyle.MIUIX)
        val glassIntensity = mutableStateOf(0f)
        val showGlassSettings = mutableStateOf(false)
        val shortcuts = listOf("vpn", "ip_test", "ui_style", "nodes", "profiles", "core_logs")

        compose.setContent {
            MihomoTheme(
                themeMode = mode.value,
                uiStyle = style.value,
                glassIntensity = glassIntensity.value
            ) {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, 1f),
                    LocalAppLanguage provides "zh"
                ) {
                    Box(Modifier.fillMaxSize()) {
                        if (showGlassSettings.value) {
                            Box(Modifier.width(360.dp)) {
                                GlassThemeSection(
                                    value = glassIntensity.value,
                                    onValueChange = { glassIntensity.value = it }
                                )
                            }
                        } else {
                            Box(Modifier.width(360.dp)) {
                                DashboardScreen(
                                    scrollState = ScrollState(0),
                                    vpnState = VpnState(status = VpnState.Status.STOPPED),
                                    currentMode = TunnelMode.RULE,
                                    activeProfileName = "测试配置名称",
                                    mainGroupName = "",
                                    mainGroupProxies = emptyList(),
                                    activeNodeName = "",
                                    totalNodes = 12,
                                    totalRules = 0,
                                    requestMonitoringEnabled = false,
                                    visibleShortcuts = shortcuts.toSet(),
                                    shortcutOrder = shortcuts,
                                    onToggleVpn = {},
                                    onModeSelected = {},
                                    onSelectProxy = { _, _ -> },
                                    onHealthCheck = {},
                                    onNavigateToProfiles = {},
                                    onNavigateToAppRouting = {},
                                    onNavigateToLogs = {},
                                    onNavigateToRules = {},
                                    onNavigateToDns = {},
                                    onNavigateToRequests = {},
                                    onNavigateToNetworkShare = {},
                                    onUpdateVisibleShortcuts = {},
                                    onUpdateShortcutOrder = {},
                                    uiStyle = style.value,
                                    onToggleUiStyle = {}
                                )
                            }
                            androidx.compose.foundation.layout.Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = androidx.compose.ui.Alignment.BottomCenter
                            ) {
                                LoonFloatingBottomBar(selectedTab = 0, onTabSelected = {})
                            }
                        }
                    }
                }
            }
        }

        fun saveWindowScreenshot(fileName: String) {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val image = File("build/reports/ui-preview/$fileName")
            image.parentFile.mkdirs()
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }

        for (targetStyle in UiStyle.entries) {
            for (targetMode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                compose.runOnIdle {
                    showGlassSettings.value = false
                    mode.value = targetMode
                    style.value = targetStyle
                }
                compose.waitForIdle()
                saveWindowScreenshot("dashboard-${targetStyle.name.lowercase()}-${targetMode.name.lowercase()}.png")
            }
        }

        compose.runOnIdle { showGlassSettings.value = true; glassIntensity.value = 0f }
        compose.waitForIdle()
        saveWindowScreenshot("glass-settings-0.png")
        compose.runOnIdle { glassIntensity.value = 1f }
        compose.waitForIdle()
        saveWindowScreenshot("glass-settings-100.png")
    }
}
