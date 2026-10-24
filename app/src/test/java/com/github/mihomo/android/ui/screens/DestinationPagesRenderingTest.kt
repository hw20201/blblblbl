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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.data.LogRepository
import com.github.mihomo.android.data.ParsedRule
import com.github.mihomo.android.data.ProfileItem
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.TunStack
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.components.LoonFloatingBottomBar
import com.github.mihomo.android.ui.glass.*
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DestinationPagesRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val currentPage = mutableStateOf<(@Composable () -> Unit)?>(null)
    private var contentInstalled = false

    @Test fun captureShortcutDestinationsWithRepresentativeData() {
        val profiles = listOf(
            ProfileItem("remote", "Tokyo · nodes.yaml", "https://example.invalid/sub", File("/tmp/tokyo.yaml"), 1_700_000_000_000L, autoUpdate = true),
            ProfileItem("local", "Local backup.yaml", null, File("/tmp/backup.yaml"), 1_700_000_000_000L)
        )
        show {
            ProfilesScreen(profiles, "remote", onBack = {}, onSelectProfile = {}, onAddSubscription = { _, _ -> },
                onUpdateProfile = {}, onDeleteProfile = {}, onRefreshSubscription = {}, onImportLocalFile = {},
                onReorderProfiles = {}, onNavigateToOverride = {})
        }
        compose.onNodeWithText("Tokyo · nodes.yaml").assertIsDisplayed()
        capture("profiles-light")

        show {
            RulesScreen(
                rules = listOf(
                    ParsedRule("DOMAIN-SUFFIX", "example.com", "Proxy"),
                    ParsedRule("DOMAIN-KEYWORD", "stream", "DIRECT"),
                    ParsedRule("IP-CIDR", "192.0.2.0/24", "REJECT", true)
                ),
                totalRules = 3, tunnelMode = TunnelMode.RULE, onBack = {}
            )
        }
        compose.onNodeWithText("example.com").assertIsDisplayed()
        capture("rules-light")

        LogRepository.clear()
        LogRepository.addLog("info", "[TCP] 192.168.1.12:52643 --> api.example.com:443 match DomainSuffix using Proxy")
        LogRepository.addLog("info", "[DNS] 192.168.1.12:52644 --> resolver.example:53 match Domain using DIRECT")
        show { RequestLogsScreen(VpnState(status = VpnState.Status.RUNNING), onBack = {}) }
        compose.onNodeWithText("api.example.com:443").assertIsDisplayed()
        capture("requests-light")
        LogRepository.clear()
    }

    private fun show(page: @Composable () -> Unit) {
        if (!contentInstalled) {
            compose.setContent {
                MihomoTheme(ThemeMode.LIGHT) {
                    CompositionLocalProvider(LocalAppLanguage provides "zh") {
                        ProvideGlassTheme {
                            val lens = rememberNavigationLens()
                            Box(Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxSize().recordNavigationScene(lens)) { NexusGlassBackground {} }
                                currentPage.value?.invoke()
                                LoonFloatingBottomBar(1, {}, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), lens)
                            }
                        }
                    }
                }
            }
            contentInstalled = true
        }
        compose.runOnIdle { currentPage.value = page }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        val window = compose.activity.window
        val view = window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val result = AtomicInteger(-1)
        compose.runOnIdle { PixelCopy.request(window, image, { result.set(it) }, Handler(Looper.getMainLooper())) }
        compose.waitUntil(5_000) { result.get() != -1 }
        check(result.get() == PixelCopy.SUCCESS) { "PixelCopy failed for $name: ${result.get()}" }
        val dir = File("build/reports/destination-refresh").also { it.mkdirs() }
        dir.resolve("$name.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
