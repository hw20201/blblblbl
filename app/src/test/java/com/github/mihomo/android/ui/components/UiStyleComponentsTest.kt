package com.github.mihomo.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.UiStyle
import com.github.mihomo.android.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UiStyleComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchingStyleDoesNotDisposeOrResetContentState() {
        val style = mutableStateOf(UiStyle.CUPERTINO)
        var entries = 0
        var disposals = 0
        compose.setContent {
            MihomoTheme(uiStyle = style.value) {
                DisposableEffect(Unit) { entries++; onDispose { disposals++ } }
                var clicks by remember { mutableIntStateOf(0) }
                CupertinoCell(title = "Clicks $clicks", onClick = { clicks++ })
            }
        }
        compose.onNodeWithText("Clicks 0").performClick()
        compose.runOnIdle { style.value = UiStyle.MIUIX }
        compose.onNodeWithText("Clicks 1").assertExists().performClick()
        compose.runOnIdle { style.value = UiStyle.CUPERTINO }
        compose.onNodeWithText("Clicks 2").assertExists()
        compose.runOnIdle { assertEquals(1, entries); assertEquals(0, disposals) }
    }

    @Test fun miuixSwitchRespectsHapticsAndDisabledState() {
        val enabled = mutableStateOf(true)
        val checked = mutableStateOf(false)
        val feedback = mutableStateOf(false)
        var ticks = 0
        var calls = 0
        compose.setContent {
            MihomoTheme(uiStyle = UiStyle.MIUIX) {
                CompositionLocalProvider(
                    LocalHapticFeedbackEnabled provides { feedback.value },
                    LocalHapticFeedbackPerformer provides { ticks++ }
                ) {
                    CupertinoSwitch(checked = checked.value, enabled = enabled.value,
                        onCheckedChange = { checked.value = it; calls++ })
                }
            }
        }
        compose.onNode(isToggleable()).performClick().assertIsOn()
        compose.runOnIdle { assertEquals(1, calls); assertEquals(0, ticks); feedback.value = true }
        compose.onNode(isToggleable()).performClick().assertIsOff()
        compose.runOnIdle { assertEquals(2, calls); assertEquals(1, ticks); enabled.value = false }
        compose.onNode(isToggleable()).assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(2, calls); assertEquals(1, ticks) }
    }

    @Test fun bothStylesSupportDarkLightAndLargeTextOnNarrowScreens() {
        val style = mutableStateOf(UiStyle.CUPERTINO)
        val mode = mutableStateOf(ThemeMode.LIGHT)
        compose.setContent {
            MihomoTheme(uiStyle = style.value, themeMode = mode.value) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                    Column(Modifier.width(320.dp)) {
                        Text(if (LocalLoonColors.current.isDark) "Dark" else "Light")
                        CupertinoGroupedSection(title = "设置") {
                            CupertinoCell(title = "较长的网络设置标题", subtitle = "两种风格都应保留所有设置与点击动作", value = "已启用")
                        }
                    }
                }
            }
        }
        UiStyle.entries.forEach { target ->
            compose.runOnIdle { style.value = target; mode.value = ThemeMode.LIGHT }
            compose.onNodeWithText("Light").assertExists()
            compose.onNodeWithText("较长的网络设置标题").assertExists()
            compose.runOnIdle { mode.value = ThemeMode.DARK }
            compose.onNodeWithText("Dark").assertExists()
        }
    }
}
