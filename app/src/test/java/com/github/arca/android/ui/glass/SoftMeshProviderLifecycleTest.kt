package com.github.arca.android.ui.glass

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.TimeUnit

/** Exercises the real provider clock, not an injected timeline or a wrapper draw counter. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SoftMeshProviderLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val originalEnabled = GlassDefaults.enabled
    private val originalWallpaper = GlassDefaults.wallpaper

    @After fun restoreDefaults() {
        GlassDefaults.enabled = originalEnabled
        GlassDefaults.wallpaper = originalWallpaper
    }

    @Test fun staticProviderStartsOnColorChangeFreezesAfterFlowOffAndResumesWithoutJump() {
        compose.mainClock.autoAdvance = false
        GlassDefaults.enabled = true
        // Equal colours are static even though the user's flow preference remains enabled.
        GlassDefaults.wallpaper = GlassWallpaperSettings()
        lateinit var time: State<Float>
        lateinit var view: View
        compose.setContent {
            ProvideSoftMeshMotion {
                time = requireNotNull(LocalSoftMeshTime.current)
                view = LocalView.current
                Box(Modifier.fillMaxSize())
            }
        }
        compose.waitForIdle()

        // Same real-window setup as SoftMeshLifecycleTest: ActivityScenario's synthetic
        // AttachInfo otherwise retains GONE despite the view reporting shown/focused.
        compose.runOnIdle {
            val info = ReflectionHelpers.getField<Any>(view, "mAttachInfo")
            ReflectionHelpers.setField(info, "mWindowVisibility", View.VISIBLE)
            ReflectionHelpers.setField(info, "mHasWindowFocus", true)
            ReflectionHelpers.callInstanceMethod<Unit>(view.viewTreeObserver, "dispatchOnWindowFocusChange",
                ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, true))
        }
        fun frames(count: Int) {
            repeat(count) {
                shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
                compose.mainClock.advanceTimeBy(64)
                compose.waitForIdle()
            }
        }

        val providerTime = time
        frames(8)
        assertEquals("a focused static provider must not advance its clock", 0f, time.value, 0f)

        val dynamic = GlassWallpaperSettings(
            colorA = 0xFF15263A.toInt(), colorB = 0xFFB68A62.toInt(), colorC = 0xFF7DB5C8.toInt()
        )
        compose.runOnIdle { GlassDefaults.wallpaper = dynamic }
        frames(8)
        assertSame("palette edits retain the provider's timeline state", providerTime, time)
        assertTrue("changing to distinct colours starts the actual provider clock", time.value > 0f)

        compose.runOnIdle { GlassDefaults.wallpaper = dynamic.copy(colorFlow = false) }
        // Let recomposition and effect cancellation settle before establishing the pause baseline.
        frames(2)
        val frozen = time.value
        frames(12)
        assertEquals("flow-off remains frozen after the transition settles", frozen, time.value, .0001f)

        compose.runOnIdle { GlassDefaults.wallpaper = dynamic }
        frames(4)
        assertSame("re-enabling flow retains accumulated time", providerTime, time)
        assertTrue("dynamic wallpaper resumes the actual clock", time.value > frozen)
        assertTrue("the disabled interval is excluded rather than accumulated", time.value - frozen < .5f)
    }
}
