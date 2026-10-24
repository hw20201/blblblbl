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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.TunStack
import com.github.mihomo.android.ui.components.LoonFloatingBottomBar
import com.github.mihomo.android.ui.glass.*
import com.github.mihomo.android.ui.theme.AppStrings
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConfigurationCenterScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun settingsHasOneConfigurationEntryInsteadOfScatteredManagementRows() {
        var opened = 0
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        val lens = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(Modifier.recordNavigationScene(lens)) {
                                SettingsScreen(
                                    scrollState = ScrollState(0), tunStack = TunStack.MIPS,
                                    bootOnStartup = false, allowLan = false, fakeIpEnabled = true,
                                    onTunStackChanged = {}, onBootOnStartupChanged = {}, onAllowLanChanged = {},
                                    onNavigateToConfigurationCenter = { opened++ }, onNavigateToLogs = {}
                                )
                            }
                            LoonFloatingBottomBar(2, {}, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), lens)
                        }
                    }
                }
            }
        }
        compose.onAllNodesWithText("配置中心").assertCountEquals(1)
        listOf("脚本中心", "配置与订阅", "应用分流").forEach {
            compose.onAllNodesWithText(it).assertCountEquals(0)
        }
        compose.onNodeWithText("配置中心").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
        save("settings-configuration-center")
    }

    @Test fun allOriginalDestinationsRemainAccessibleWithNavigationGlass() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val calls = mutableListOf<String>()
        compose.setContent {
            MihomoTheme(themeMode = mode.value) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        val lens = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(Modifier.recordNavigationScene(lens)) {
                                ConfigurationCenterScreen(
                                    onNavigateToProfiles = { calls += "profiles" },
                                    onNavigateToScripts = { calls += "scripts" },
                                    onNavigateToRules = { calls += "rules" },
                                    onNavigateToAppRouting = { calls += "routing" }, onBack = { calls += "back" }
                                )
                            }
                            LoonFloatingBottomBar(2, {}, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), lens)
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("配置中心").assertExists()
        compose.onAllNodesWithTag("navigation-title-capsule").assertCountEquals(1)
        compose.onAllNodesWithTag("navigation-title-plain").assertCountEquals(0)
        listOf("配置 / 订阅", "脚本", "规则 / 路由资源").forEachIndexed { index, label ->
            val heading = compose.onNodeWithTag("configuration-center-section-header-$index")
            heading.assertExists()
            compose.onAllNodesWithText(label).assertCountEquals(1)
            assertTrue("section labels stay compact and unboxed", heading.fetchSemanticsNode().boundsInRoot.height < 32f)
        }
        listOf("配置文件与订阅", "脚本管理", "规则资源", "应用分流").forEach { label ->
            compose.onNodeWithText(label).assertExists()
        }
        compose.onNodeWithTag("configuration-center-scroll").assertExists()
        for (target in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            compose.runOnIdle { mode.value = target }
            compose.waitForIdle()
            save("configuration-center-${target.name.lowercase()}")
            listOf("配置文件与订阅", "脚本管理", "规则资源", "应用分流").forEach {
                compose.onNodeWithText(it).performScrollTo().performClick()
            }
            compose.onNodeWithContentDescription(AppStrings.get("back", "zh")).performClick()
        }
        compose.runOnIdle { assertEquals(List(2) { listOf("profiles", "scripts", "rules", "routing", "back") }.flatten(), calls) }
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
        val file = File("build/reports/navigation-lens/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
