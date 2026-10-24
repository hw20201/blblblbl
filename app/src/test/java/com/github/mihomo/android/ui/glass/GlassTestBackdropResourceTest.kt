package com.github.mihomo.android.ui.glass

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class GlassTestBackdropResourceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun performanceBackgroundComposesOnlyTheSelectedVariant() {
        val variant = mutableStateOf("original")
        compose.setContent {
            NexusGlassBackground(testBackdropVariant = variant.value) {
                Column {
                    Button(onClick = { variant.value = "inverted" }) { Text("choose inverted") }
                }
            }
        }
        compose.onNodeWithTag("glass-test-backdrop-original").assertExists()
        compose.onNodeWithTag("glass-test-backdrop-inverted").assertDoesNotExist()
        compose.onNodeWithText("choose inverted").performClick()
        compose.onNodeWithTag("glass-test-backdrop-original").assertDoesNotExist()
        compose.onNodeWithTag("glass-test-backdrop-inverted").assertExists()
    }

    @Test fun performanceResourcesContainOriginalAndExactRgbInversion() {
        val context = RuntimeEnvironment.getApplication()
        val originalId = context.resources.getIdentifier(
            "nexus_glass_test_grid", "drawable", context.packageName
        )
        val invertedId = context.resources.getIdentifier(
            "nexus_glass_test_grid_inverted", "drawable", context.packageName
        )
        assertTrue("original grid resource must be packaged", originalId != 0)
        assertTrue("inverted grid resource must be packaged", invertedId != 0)
        val original = BitmapFactory.decodeResource(context.resources, originalId)
        val inverted = BitmapFactory.decodeResource(context.resources, invertedId)
        assertEquals(original.width, inverted.width)
        assertEquals(original.height, inverted.height)
        for (y in 0 until original.height) {
            for (x in 0 until original.width) {
                val a = original.getPixel(x, y)
                val b = inverted.getPixel(x, y)
                assertEquals("red differs at $x,$y", 255 - android.graphics.Color.red(a), android.graphics.Color.red(b))
                assertEquals("green differs at $x,$y", 255 - android.graphics.Color.green(a), android.graphics.Color.green(b))
                assertEquals("blue differs at $x,$y", 255 - android.graphics.Color.blue(a), android.graphics.Color.blue(b))
            }
        }
    }
}
