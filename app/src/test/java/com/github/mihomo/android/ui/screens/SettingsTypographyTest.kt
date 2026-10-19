package com.github.mihomo.android.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.UiStyle
import com.github.mihomo.android.ui.components.LocalMainBottomBarInset
import com.github.mihomo.android.ui.components.LoonFloatingBottomBar
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class SettingsTypographyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsRowsShareTypographyAndReflowWithoutLosingTextOrSwitchActions() {
        val style = mutableStateOf(UiStyle.CUPERTINO)
        val width = mutableFloatStateOf(360f)
        val scale = mutableFloatStateOf(1f)
        val scroll = mutableStateOf(ScrollState(0))
        val ipv6 = mutableStateOf(false)
        compose.setContent {
            MihomoTheme(uiStyle = style.value, themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, scale.floatValue),
                    LocalAppLanguage provides "zh",
                    LocalMainBottomBarInset provides 100.dp
                ) {
                    Box(Modifier.width(width.floatValue.dp).fillMaxHeight()) {
                        SettingsScreen(
                            scrollState = scroll.value, tunStack = "mips", ipv6Enabled = ipv6.value,
                            bootOnStartup = false, allowLan = false, fakeIpEnabled = false,
                            hapticEnabled = false, onTunStackChanged = {},
                            onIpv6EnabledChanged = { ipv6.value = it },
                            onBootOnStartupChanged = {}, onAllowLanChanged = {},
                            onFakeIpEnabledChanged = {}, onHapticEnabledChanged = {},
                            onNavigateToConfigurationCenter = {}, onNavigateToLogs = {},
                            onNavigateToAppearance = {}, onNavigateToDns = {}
                        )
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            LoonFloatingBottomBar(selectedTab = 2, onTabSelected = {})
                        }
                    }
                }
            }
        }
        for (targetStyle in UiStyle.entries) for (targetWidth in listOf(360f, 320f)) {
            for (targetScale in listOf(1f, 1.3f, 2f)) {
                val context = "$targetStyle width=$targetWidth scale=$targetScale"
                compose.runOnIdle {
                    style.value = targetStyle
                    width.floatValue = targetWidth
                    scale.floatValue = targetScale
                    scroll.value = ScrollState(0)
                }
                compose.waitForIdle()
                val navigationSizes = compose.onAllNodesWithText("设置", useUnmergedTree = true)
                    .fetchSemanticsNodes().map { node ->
                        val results = mutableListOf<TextLayoutResult>()
                        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                        results.single().layoutInput.style.fontSize
                    }.toSet()
                assertEquals(context, setOf(32.sp, 11.sp), navigationSizes)
                if (targetWidth == 360f && targetScale == 1f) saveWindow("${targetStyle.name.lowercase()}-initial.png")
                val title = layout("TUN 堆栈模式")
                assertEquals(context, 15.sp, title.layoutInput.style.fontSize)
                assertEquals(context, 21.sp, title.layoutInput.style.lineHeight)
                assertUnclipped(context, title)
                if (targetWidth == 360f && targetScale == 1f) assertEquals(context, 1, title.lineCount)
                val titleBounds = bounds("TUN 堆栈模式")
                val valueBounds = bounds("MIPS")
                assertTrue("$context title/value overlap", titleBounds.right <= valueBounds.left + 1f || titleBounds.bottom <= valueBounds.top + 1f)
                assertEquals(context, 13.sp, layout("MIPS").layoutInput.style.fontSize)
                assertEquals(context, 13.sp, layout("核心与服务").layoutInput.style.fontSize)
                val descriptionText = "开启后 IPv6 进入隧道并由节点出站；关闭时不捕获 IPv6，可能绕过 VPN。"
                val description = layout(descriptionText)
                assertEquals(context, 13.sp, description.layoutInput.style.fontSize)
                assertUnclipped(context, description)
                assertTrue(context, bounds(descriptionText).top >= bounds("IPv6 路由").bottom + 5f)
                compose.onNodeWithContentDescription("IPv6 路由").assertIsOff().performClick()
                compose.runOnIdle { assertTrue("$context IPv6 callback", ipv6.value) }
                compose.onNodeWithContentDescription("IPv6 路由").assertIsOn().performClick()
                compose.runOnIdle { assertFalse(ipv6.value) }
                listOf("MIPS 拥塞控制", "始终开启的 VPN").forEach {
                    val result = layout(it)
                    assertEquals(context, 15.sp, result.layoutInput.style.fontSize)
                    assertUnclipped("$context: $it", result)
                }
                val vpnSubtitle = layout("在系统设置中管理")
                assertEquals(context, 13.sp, vpnSubtitle.layoutInput.style.fontSize)
                assertUnclipped(context, vpnSubtitle)
                compose.runOnIdle { scroll.value = ScrollState(0) }
                compose.waitForIdle()
                val normalPreview = targetWidth == 360f && targetScale == 1f
                val largePreview = targetWidth == 320f && targetScale == 2f
                if (normalPreview || largePreview) {
                    saveWindow("${targetStyle.name.lowercase()}-${targetWidth.toInt()}-${targetScale}.png")
                }
            }
        }
    }

    @Test
    fun finalRowAndNextSectionRemainAboveReservedBarAtAllFontScalesAndStyles() {
        val style = mutableStateOf(UiStyle.CUPERTINO)
        val scale = mutableFloatStateOf(1f)
        val scroll = mutableStateOf(ScrollState(0))
        compose.setContent {
            MihomoTheme(uiStyle = style.value, themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, scale.floatValue),
                    LocalAppLanguage provides "zh",
                    LocalMainBottomBarInset provides 100.dp
                ) {
                    Box(Modifier.width(320.dp).fillMaxHeight().testTag("viewport")) {
                        SettingsScreen(
                            scrollState = scroll.value, tunStack = "mips",
                            bootOnStartup = false, allowLan = false, fakeIpEnabled = false,
                            hapticEnabled = false, onTunStackChanged = {}, onBootOnStartupChanged = {},
                            onAllowLanChanged = {}, onFakeIpEnabledChanged = {}, onHapticEnabledChanged = {},
                            onNavigateToConfigurationCenter = {}, onNavigateToLogs = {},
                            onNavigateToAppearance = {}, onNavigateToDns = {}
                        )
                    }
                }
            }
        }
        for (targetStyle in UiStyle.entries) for (targetScale in listOf(1f, 1.3f, 2f)) {
            compose.runOnIdle { style.value = targetStyle; scale.floatValue = targetScale; scroll.value = ScrollState(0) }
            compose.waitForIdle()
            val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            listOf("通用与外观", "外观与显示", "应用版本").forEach {
                compose.onNodeWithText(it, useUnmergedTree = true).performScrollTo()
                assertTrue("$targetStyle $targetScale: $it under bottom bar", bounds(it).bottom <= viewport.bottom - 100f + 1f)
            }
        }
    }

    private fun assertUnclipped(context: String, layout: TextLayoutResult) {
        // Compose rounds native paragraph dimensions to integer pixels; allow less than one
        // pixel of rounding, but never missing characters, ellipsis, or actual constrained text.
        for (line in 0 until layout.lineCount) {
            assertTrue("$context width clipped: size=${layout.size} paragraph=${layout.multiParagraph.width} lineRight=${layout.getLineRight(line)}", layout.getLineRight(line) <= layout.size.width + 1f)
        }
        assertTrue("$context height clipped", layout.multiParagraph.height <= layout.size.height + 1f)
        val lastLine = layout.lineCount - 1
        assertFalse("$context ellipsized", layout.isLineEllipsized(lastLine))
        assertEquals(context, layout.layoutInput.text.length, layout.getLineEnd(lastLine))
    }

    private fun bounds(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun layout(text: String): TextLayoutResult {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo().fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        assertTrue("No text layout: $text", node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results) == true)
        return results.single()
    }

    private fun saveWindow(name: String) {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File("build/reports/settings-typography/$name")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
