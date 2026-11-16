package com.github.arca.android.ui.screens

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.PanelAnimationSpeed
import com.github.arca.android.data.SettingsManager
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.data.TunStack
import com.github.arca.android.ui.components.LocalPanelAnimationSpeed
import com.github.arca.android.ui.glass.NexusGlassBackground
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.glass.rememberArcaGlasmioBackdrop
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelAnimationSpeedSettingsSelectorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsSelectsPersistsAndShowsAllThreeSpeeds() {
        val context = compose.activity
        context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val settings = SettingsManager(context)
        val chosen = mutableStateOf(settings.panelAnimationSpeed)
        var selectionCount = 0

        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalPanelAnimationSpeed provides chosen.value) {
                    ProvideGlassTheme {
                        val backdrop = rememberArcaGlasmioBackdrop()
                        NexusGlassBackground(sharedGlasmioBackdrop = backdrop) {
                            SettingsScreen(
                                scrollState = rememberScrollState(),
                                tunStack = TunStack.DEFAULT,
                                bootOnStartup = false,
                                allowLan = false,
                                fakeIpEnabled = false,
                                panelAnimationSpeed = chosen.value,
                                onPanelAnimationSpeedChanged = { speed ->
                                    selectionCount++
                                    chosen.value = speed
                                    settings.panelAnimationSpeed = speed
                                },
                                onTunStackChanged = {},
                                onBootOnStartupChanged = {},
                                onAllowLanChanged = {},
                                onNavigateToConfigurationCenter = {},
                                onNavigateToLogs = {}
                            )
                        }
                    }
                }
            }
        }

        val entry = compose.onNodeWithTag("settings-panel-speed-entry")
        entry.performScrollTo().assertIsDisplayed().assertHasClickAction()
            .assertTextContains("面板动画速度")
            .assertTextContains("首页快捷方式与设置面板")
            .assertTextContains("中")

        PanelAnimationSpeed.entries.forEachIndexed { index, speed ->
            entry.performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("settings-detail-page").assertIsDisplayed()
            compose.onNodeWithTag(optionTag(chosen.value)).assertTextContains("已选")
            PanelAnimationSpeed.entries.forEach { option ->
                compose.onNodeWithTag(optionTag(option)).assertExists().assertHasClickAction()
            }
            compose.onNodeWithTag(optionTag(speed))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(index + 1, selectionCount)
                assertEquals(speed, chosen.value)
                assertEquals(speed, SettingsManager(context).panelAnimationSpeed)
            }
            compose.onNodeWithTag("settings-detail-page").assertDoesNotExist()
            entry.assertTextContains(speed.label)
        }

        // Reopening shows the persisted selection; closing without a choice does not save again.
        entry.performClick()
        compose.onNodeWithTag(optionTag(PanelAnimationSpeed.SLOW)).assertTextContains("已选")
        compose.onNodeWithTag("settings-detail-back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-detail-page").assertDoesNotExist()
        compose.runOnIdle { assertEquals(3, selectionCount) }
    }

    private fun optionTag(speed: PanelAnimationSpeed): String =
        "settings-detail-panel-speed-${speed.name.lowercase(Locale.ROOT)}"
}
