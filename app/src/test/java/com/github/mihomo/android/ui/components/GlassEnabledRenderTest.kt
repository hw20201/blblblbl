package com.github.mihomo.android.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.data.UiStyle
import com.github.mihomo.android.ui.theme.LocalGlassEnabled
import com.github.mihomo.android.ui.theme.LocalGlassIntensity
import com.github.mihomo.android.ui.theme.LocalLoonColors
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassEnabledRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private data class Pixel(val x: Int, val y: Int)

    private fun screenshot(): Bitmap {
        val view = compose.activity.window.decorView
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
            view.draw(Canvas(it))
        }
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File("build/reports/glass-toggle/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sample(bitmap: Bitmap, pixel: Pixel): Int = bitmap.getPixel(pixel.x, pixel.y)

    @Test
    fun nativeGlassPixelsToggleForBothSurfacesStylesAndThemes() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val style = mutableStateOf(UiStyle.CUPERTINO)
        val enabled = mutableStateOf(false)
        var navPixel = Pixel(0, 0)
        var cardPixel = Pixel(0, 0)
        var expectedNav = Color.White
        val magenta = Color(0xFFFF00FF)
        val cardTint = Color(0xFF26A6C8)

        compose.setContent {
            MihomoTheme(themeMode = mode.value, glassIntensity = 1f, glassEnabled = enabled.value) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalGlassIntensity provides 1f, LocalGlassEnabled provides enabled.value) {
                    val navTint = if (LocalLoonColors.current.isDark) Color(0xFF202329) else Color(0xFFF9FBFF)
                    expectedNav = navTint
                    Column(Modifier.fillMaxSize().background(magenta).padding(24.dp)) {
                        GlassNavigationSurface(
                            modifier = Modifier.width(300.dp).height(100.dp).onGloballyPositioned {
                                val origin = it.positionInWindow()
                                navPixel = Pixel((origin.x + it.size.width / 2).toInt(), (origin.y + it.size.height / 2).toInt())
                            },
                            radius = 20.dp
                        ) {}
                        Box(
                            Modifier.padding(top = 28.dp).width(300.dp).height(100.dp)
                                .glassCardFinish(cardTint, 18.dp).onGloballyPositioned {
                                    val origin = it.positionInWindow()
                                    cardPixel = Pixel((origin.x + it.size.width / 2).toInt(), (origin.y + it.size.height / 2).toInt())
                                }
                        )
                    }
                }
            }
        }

        for (targetStyle in listOf(UiStyle.CUPERTINO)) {
            for (targetMode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                compose.runOnIdle {
                    style.value = targetStyle
                    mode.value = targetMode
                    enabled.value = false
                }
                compose.waitForIdle()

                val nav = navPixel
                val card = cardPixel
                val off = screenshot()
                val navOff = sample(off, nav)
                val cardOff = sample(off, card)
                assertEquals("nav off must be the theme's opaque bottom-bar tint", expectedNav.toArgb(), navOff)
                assertEquals("card off must be opaque tint, style=$targetStyle mode=$targetMode", cardTint.toArgb(), cardOff)
                val baseName = "${targetStyle.name.lowercase()}-${targetMode.name.lowercase()}"
                save(off, "$baseName-off")

                compose.runOnIdle { enabled.value = true }
                compose.waitForIdle()
                val on = screenshot()
                val navOn = sample(on, nav)
                val cardOn = sample(on, card)
                assertNotEquals("navigation glass should blend with magenta behind it", navOff, navOn)
                assertNotEquals("card glass should blend with magenta behind it", cardOff, cardOn)
                save(on, "$baseName-on")

                compose.runOnIdle { enabled.value = false }
                compose.waitForIdle()
                val restoredOff = screenshot()
                assertEquals("navigation off pixels should restore exactly", navOff, sample(restoredOff, nav))
                assertEquals("card off pixels should restore exactly", cardOff, sample(restoredOff, card))
                compose.runOnIdle { enabled.value = true }
                compose.waitForIdle()
                val restoredOn = screenshot()
                assertEquals("navigation on pixels should be repeatable", navOn, sample(restoredOn, nav))
                assertEquals("card on pixels should be repeatable", cardOn, sample(restoredOn, card))
                off.recycle()
                on.recycle()
                restoredOff.recycle()
                restoredOn.recycle()
            }
        }
    }
}
