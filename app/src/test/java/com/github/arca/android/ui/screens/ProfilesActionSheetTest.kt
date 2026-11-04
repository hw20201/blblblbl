package com.github.arca.android.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.ProfileItem
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.glass.GlassPreset
import com.github.arca.android.ui.glass.GlassSettings
import com.github.arca.android.ui.glass.LocalGlassSettings
import com.github.arca.android.ui.glass.LocalKyantBackdrop
import com.github.arca.android.ui.theme.MihomoTheme
import com.github.arca.android.ui.glass.NexusGlassBackground
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-mdpi")
class ProfilesActionSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun tappingProfileMoreShowsActionSheetWithLiquidGlass() {
        val profile = ProfileItem(
            "remote", "Tokyo · nodes.yaml", "https://example.invalid/sub",
            File("/tmp/tokyo.yaml"), 1_700_000_000_000L, autoUpdate = true
        )
        compose.setContent {
            val backdrop = rememberLayerBackdrop()
            CompositionLocalProvider(
                LocalKyantBackdrop provides backdrop,
                LocalGlassSettings provides GlassSettings(glassPreset = GlassPreset.LIQUID)
            ) {
                MihomoTheme(ThemeMode.LIGHT) {
                    ProvideGlassTheme {
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(kyantBackdrop = backdrop)
                            ProfilesScreen(
                                profiles = listOf(profile), selectedProfileId = "remote",
                                onBack = {}, onSelectProfile = {}, onAddSubscription = { _, _ -> },
                                onUpdateProfile = {}, onDeleteProfile = {}, onRefreshSubscription = {},
                                onImportLocalFile = {}, onReorderProfiles = {}, onNavigateToOverride = {}
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("更多").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("编辑配置参数").assertIsDisplayed()
    }
}
