package com.github.arca.android.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** UI interaction checks intentionally avoid PixelCopy/native screenshot capture. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubscriptionImportSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show() {
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    Box(Modifier.fillMaxSize()) {
                        SubscriptionImportSheet(onDismiss = {}, onImported = {})
                    }
                }
            }
        }
    }

    @Test fun emptyInputCannotStartInspection() {
        show()
        compose.onNodeWithText("添加配置").assertIsDisplayed()
        compose.onNodeWithTag("profile-add-submit").assertIsNotEnabled()
        compose.onNodeWithTag("profile-add-local-file").assertIsDisplayed()
        compose.onNodeWithTag("profile-add-clipboard").assertIsDisplayed()
    }

    @Test fun queuedSourceCanBeEditedWithoutLosingItsContents() {
        show()
        val fixture = "ss://YWVzLTEyOC1nY206Zml4dHVyZQ@node.invalid:443#Fixture"
        compose.onNodeWithTag("profile-add-url").performScrollTo().performTextInput(fixture)
        compose.onNodeWithTag("subscription-import-content")
            .performScrollToNode(hasText("再加一个来源", substring = true))
        compose.onNodeWithText("再加一个来源", substring = true).performClick()
        compose.onNodeWithContentDescription("编辑来源").performScrollTo().performClick()
        compose.onNodeWithTag("profile-add-url").performScrollTo().assertTextContains(fixture)
        compose.onNodeWithTag("profile-add-submit").assertIsEnabled()
    }
}
