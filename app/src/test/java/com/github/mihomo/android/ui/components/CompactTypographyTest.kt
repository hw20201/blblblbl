package com.github.mihomo.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.UiStyle
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.compose.ui.text.TextLayoutResult

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CompactTypographyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longKernelValueDoesNotCrushChineseTitleAcrossStylesAndFontScales() {
        val title = "内核版本"
        val version = "v1.19.32_88dcbf7_native-mips2"
        val style = mutableStateOf(UiStyle.CUPERTINO)
        val width = mutableFloatStateOf(320f)
        val fontScale = mutableFloatStateOf(1f)

        compose.setContent {
            MihomoTheme( themeMode = ThemeMode.LIGHT) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.floatValue)) {
                    Box(Modifier.width(width.floatValue.dp)) {
                        CupertinoCell(title = title, value = version, icon = Icons.Default.Info,
                            modifier = Modifier.testTag("kernel-row"))
                    }
                }
            }
        }

        for (uiStyle in listOf(UiStyle.CUPERTINO)) {
            for (screenWidth in listOf(320f, 360f)) {
                for (scale in listOf(1f, 1.6f, 2f)) {
                    compose.runOnIdle {
                        style.value = uiStyle
                        width.floatValue = screenWidth
                        fontScale.floatValue = scale
                    }
                    compose.waitForIdle()
                    val titleNode = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode()
                    val valueNode = compose.onNodeWithText(version, useUnmergedTree = true).fetchSemanticsNode()
                    val titleBounds = titleNode.boundsInRoot
                    val valueBounds = valueNode.boundsInRoot
                    assertTrue("$uiStyle width=$screenWidth fontScale=$scale: title has no layout", titleBounds.width > 0f)
                    assertTrue("$uiStyle width=$screenWidth fontScale=$scale: value has no layout", valueBounds.width > 0f)
                    assertTrue("$uiStyle width=$screenWidth fontScale=$scale: title overlaps value: $titleBounds / $valueBounds", titleBounds.right <= valueBounds.left + 1f || titleBounds.bottom <= valueBounds.top + 1f)

                    val layouts = mutableListOf<TextLayoutResult>()
                    val hasLayoutAction = titleNode.config.getOrNull(SemanticsActions.GetTextLayoutResult)
                        ?.action?.invoke(layouts) == true
                    assertTrue("$uiStyle title must expose text layout", hasLayoutAction && layouts.isNotEmpty())
                    val maxLines = if (scale == 1f) 1 else 2
                    assertTrue("$uiStyle width=$screenWidth fontScale=$scale title lineCount=${layouts.single().lineCount}", layouts.single().lineCount in 1..maxLines)
                }
            }
        }
    }
}
