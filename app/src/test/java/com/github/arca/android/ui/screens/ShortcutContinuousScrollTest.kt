package com.github.arca.android.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.InstalledApp
import com.github.arca.android.data.LanCredentials
import com.github.arca.android.data.PerAppProxyMode
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class ShortcutContinuousScrollTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun networkSharingUsesOneContinuousEdgeToEdgeScrollSurface() {
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                androidx.compose.runtime.CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        NetworkShareScreen(
                            allowLan = true,
                            credentials = LanCredentials("user", "pass"),
                            onAllowLanChanged = {}, onRegenerateCredentials = {},
                            vpnState = VpnState(), onBack = {}
                        )
                    }
                }
            }
        }
        val list = compose.onNodeWithTag("network-share-list")
        list.assertExists()
        repeat(5) { list.performTouchInput { swipeUp() } }
        compose.onNodeWithText("设备 Wi-Fi 代理设置指南").assertIsDisplayed()
    }

    @Test fun perAppProxyControlsAndResultsShareOneContinuousScrollSurface() {
        val apps = (1..32).map { InstalledApp("example.app$it", "Example app $it", false) }
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                androidx.compose.runtime.CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        AppRoutingScreen(
                            currentMode = PerAppProxyMode.WHITELIST,
                            selectedPackages = emptySet(), apps = apps, isLoading = false,
                            onBack = {}, onModeChanged = {}, onTogglePackage = {}
                        )
                    }
                }
            }
        }
        val list = compose.onNodeWithTag("app-routing-list")
        list.assertExists()
        compose.onNodeWithText("Example app 32").assertDoesNotExist()
        repeat(8) { list.performTouchInput { swipeUp() } }
        compose.onNodeWithText("Example app 32").assertIsDisplayed()
    }
}
