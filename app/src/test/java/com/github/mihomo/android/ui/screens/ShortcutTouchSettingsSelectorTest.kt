package com.github.mihomo.android.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.TunStack
import com.github.mihomo.android.ui.components.ShortcutTouchVariant
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutTouchSettingsSelectorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun settingsAllowsAllThreeSelections() {
        val chosen = mutableStateOf(ShortcutTouchVariant.GentleFloat)
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                SettingsScreen(
                    scrollState = rememberScrollState(), tunStack = TunStack.MIPS,
                    bootOnStartup = false, allowLan = false, fakeIpEnabled = false,
                    shortcutTouchVariant = chosen.value, onShortcutTouchVariantChanged = { chosen.value = it },
                    onTunStackChanged = {}, onBootOnStartupChanged = {}, onAllowLanChanged = {},
                    onNavigateToConfigurationCenter = {}, onNavigateToLogs = {}
                )
            }
        }
        listOf(
            "A 轻按浮起" to ShortcutTouchVariant.GentleFloat,
            "B 跟手倾斜" to ShortcutTouchVariant.FingerTilt,
            "C 浮力回弹" to ShortcutTouchVariant.BuoyantSpring
        ).forEach { (label, variant) ->
            compose.onNodeWithText("快捷卡片触感").performScrollTo().performClick()
            compose.onNodeWithText(label).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(variant, chosen.value) }
            compose.onNodeWithText(label).assertDoesNotExist()
        }
    }
    @Test fun runtimeBehaviorSettingsExposeStatefulControls() {
        val unifiedDelay = mutableStateOf(false)
        val hotSwitch = mutableStateOf(true)
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                SettingsScreen(
                    scrollState = rememberScrollState(), tunStack = TunStack.DEFAULT,
                    bootOnStartup = false, allowLan = false, fakeIpEnabled = false,
                    unifiedDelayEnabled = unifiedDelay.value,
                    profileHotSwitchEnabled = hotSwitch.value,
                    onUnifiedDelayChanged = { unifiedDelay.value = it },
                    onProfileHotSwitchChanged = { hotSwitch.value = it },
                    onTunStackChanged = {}, onBootOnStartupChanged = {}, onAllowLanChanged = {},
                    onNavigateToConfigurationCenter = {}, onNavigateToLogs = {}
                )
            }
        }
        compose.onNodeWithTag("settings-profile-hot-switch").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(false, hotSwitch.value) }
        compose.onNodeWithTag("settings-unified-delay").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, unifiedDelay.value) }
    }

}
