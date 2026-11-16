package com.github.arca.android.ui.glass

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.neoruaa.glasmio.glass.render.backdrops.emptyBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [32], qualifiers = "w400dp-h800dp-mdpi")
class GlasmioApiFallbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectedGlasmioPresetUsesSafeFallbackOnApi32() {
        compose.setContent {
            CompositionLocalProvider(
                LocalGlassSettings provides GlassSettings(glassPreset = GlassPreset.UNIFIED)
            ) {
                ArcaGlasmioGlassSurface(
                    modifier = Modifier.testTag("glasmio-api32-fallback").size(120.dp),
                    backdrop = emptyBackdrop(),
                    shape = RoundedCornerShape(18.dp),
                    tint = Color(0xFF8099CC),
                ) {
                    androidx.compose.material3.Text("旧系统玻璃回退")
                }
            }
        }

        compose.onNodeWithText("旧系统玻璃回退").assertIsDisplayed()
    }
}
