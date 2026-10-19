package com.github.mihomo.android.ui.screens

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.SettingsManager
import com.github.mihomo.android.data.UiStyle
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class GlassThemeSectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchDisablesSliderAndReenablesItWithoutChangingIntensity() {
        val value = mutableFloatStateOf(0.73f)
        val enabled = mutableStateOf(true)
        compose.setContent {
            MihomoTheme(glassIntensity = value.floatValue, glassEnabled = enabled.value) {
                GlassThemeSection(
                    value = value.floatValue,
                    onValueChange = { value.floatValue = it },
                    enabled = enabled.value,
                    onEnabledChange = { enabled.value = it }
                )
            }
        }
        compose.onNodeWithContentDescription("玻璃强度").assertIsEnabled()
        compose.onNodeWithContentDescription("玻璃效果").performClick()
        compose.onNodeWithContentDescription("玻璃强度").assertIsNotEnabled()
        compose.onNodeWithText("73%").assertExists()
        compose.runOnIdle { assertEquals(0.73f, value.floatValue, 0.001f) }
        compose.onNodeWithContentDescription("玻璃效果").performClick()
        compose.onNodeWithContentDescription("玻璃强度").assertIsEnabled()
    }

    @Test fun sliderUpdatesLivePercentageAndPersistsInBothStyles() {
        val settings = SettingsManager(RuntimeEnvironment.getApplication())
        val value = mutableFloatStateOf(0.5f)
        val style = mutableStateOf(UiStyle.CUPERTINO)
        compose.setContent {
            MihomoTheme(uiStyle = style.value, glassIntensity = value.floatValue) {
                GlassThemeSection(value = value.floatValue, onValueChange = {
                    settings.glassIntensity = it
                    value.floatValue = settings.glassIntensity
                })
            }
        }
        compose.onNodeWithText("50%").assertExists()
        compose.onNodeWithText("清淡").assertExists()
        compose.onNodeWithText("浓郁").assertExists()
        for (targetStyle in UiStyle.entries) {
            compose.runOnIdle { style.value = targetStyle }
            compose.onNodeWithContentDescription("玻璃强度").performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }
            compose.onNodeWithText("80%").assertExists()
            compose.runOnIdle {
                assertEquals(0.8f, value.floatValue, 0.001f)
                assertEquals(0.8f, SettingsManager(RuntimeEnvironment.getApplication()).glassIntensity, 0.001f)
            }
            compose.onNodeWithContentDescription("玻璃强度").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        }
    }
}
