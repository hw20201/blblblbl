package com.github.mihomo.android.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.ui.theme.AppStrings
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ConfigurationCenterScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cupertinoExposesOriginalDestinationsAndBackAction() {
        val calls = mutableListOf<String>()
        compose.setContent {
            MihomoTheme() {
                ConfigurationCenterScreen(
                    onNavigateToProfiles = { calls += "profiles" },
                    onNavigateToScripts = { calls += "scripts" },
                    onNavigateToRules = { calls += "rules" },
                    onNavigateToAppRouting = { calls += "routing" },
                    onBack = { calls += "back" }
                )
            }
        }
            compose.onNodeWithText("配置中心").assertExists()
            compose.onNodeWithText("配置文件与订阅").performScrollTo().performClick()
            compose.onNodeWithText("脚本管理").performScrollTo().performClick()
            compose.onNodeWithText("规则资源").performScrollTo().performClick()
            compose.onNodeWithText("应用分流").performScrollTo().performClick()
            compose.onNodeWithContentDescription(AppStrings.get("back")).performClick()
        compose.runOnIdle {
            assertEquals(listOf("profiles", "scripts", "rules", "routing", "back"), calls)
        }
    }
}
