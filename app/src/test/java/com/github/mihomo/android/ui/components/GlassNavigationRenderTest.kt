package com.github.mihomo.android.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.UiStyle
import com.github.mihomo.android.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassNavigationRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun glassNavigationAndSettingsRenderInBothStylesAndThemes() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val style = mutableStateOf(UiStyle.MIUIX)
        var tab = -1
        compose.setContent {
            MihomoTheme(themeMode = mode.value, uiStyle = style.value) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalAppLanguage provides "zh") {
                    Column(Modifier.width(360.dp).background(LoonBg).padding(vertical = 20.dp)) {
                        CupertinoNavBar(title = "设置")
                        Spacer(Modifier.height(16.dp))
                        CupertinoGroupedSection(title = "内核与系统日志") {
                            CupertinoCell(title = "内核版本", value = "v1.19.32_88dcbf7_native-mips2", icon = Icons.Default.Info)
                            CupertinoIndentedDivider()
                            CupertinoCell(title = "系统架构", value = "arm64-v8a", icon = Icons.Default.Memory)
                            CupertinoIndentedDivider()
                            CupertinoCell(title = "重置内核状态", showChevron = true, icon = Icons.Default.RestartAlt, onClick = {})
                        }
                        Spacer(Modifier.height(20.dp))
                        CupertinoGroupedSection(title = "关于 Nexus") {
                            CupertinoCell(title = "应用版本", value = "1.0.12 (16)", icon = Icons.Default.Info)
                        }
                        Spacer(Modifier.height(24.dp))
                        LoonFloatingBottomBar(2, { tab = it })
                    }
                }
            }
        }
        for (targetStyle in UiStyle.entries) {
            for (targetMode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                compose.runOnIdle { mode.value = targetMode; style.value = targetStyle }
                compose.waitForIdle()
                compose.onNodeWithText("仪表").performClick()
                compose.runOnIdle { assertEquals(0, tab) }
                compose.runOnIdle {
                    val view = compose.activity.window.decorView
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val image = File("build/reports/ui-preview/${targetStyle.name.lowercase()}-${targetMode.name.lowercase()}.png")
                    image.parentFile.mkdirs()
                    image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        }
    }
}
