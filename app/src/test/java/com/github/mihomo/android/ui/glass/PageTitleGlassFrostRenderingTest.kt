package com.github.mihomo.android.ui.glass

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageTitleGlassFrostRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun titleFillRemainsTranslucentAgainstTheLightGlassBackdrop() {
        assertTrue("frosted title alpha must let the backdrop show through",
            FROSTED_PAGE_TITLE_FILL_ALPHA in 0.30f..0.50f)
        val backdrop = Color(0xFF253B55)
        var centerX = 0f
        var centerY = 0f
        compose.setContent {
            MihomoTheme(themeMode = ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    Box(Modifier.fillMaxSize().background(backdrop), contentAlignment = Alignment.Center) {
                        PageTitleGlass(
                            lens = null,
                            modifier = Modifier.width(220.dp).height(60.dp).onGloballyPositioned {
                                val p = it.positionInWindow()
                                centerX = p.x + it.size.width / 2f
                                centerY = p.y + it.size.height / 2f
                            },
                            frostedFillAlpha = FROSTED_PAGE_TITLE_FILL_ALPHA
                        ) { }
                    }
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window
        val view = window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle {
            PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
        }
        compose.waitForIdle()
        assertEquals("native window capture succeeds", PixelCopy.SUCCESS, result)

        val actual = bitmap.getPixel(centerX.toInt(), centerY.toInt())
        val alpha = FROSTED_PAGE_TITLE_FILL_ALPHA
        fun expectedChannel(source: Int): Int = (source * (1f - alpha) + 255f * alpha).toInt()
        val expected = intArrayOf(
            expectedChannel(AndroidColor.red(backdrop.toArgb())),
            expectedChannel(AndroidColor.green(backdrop.toArgb())),
            expectedChannel(AndroidColor.blue(backdrop.toArgb()))
        )
        val actualChannels = intArrayOf(AndroidColor.red(actual), AndroidColor.green(actual), AndroidColor.blue(actual))
        expected.zip(actualChannels).forEachIndexed { index, (want, got) ->
            assertTrue("title center should visibly preserve backdrop channel $index: expected $want, got $got",
                kotlin.math.abs(want - got) <= 18)
        }
        bitmap.recycle()
    }
}
