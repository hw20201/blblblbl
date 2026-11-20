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
    private val originalQuietPages = GlassDefaults.quietPages

    @After fun restoreDefaults() {
        GlassDefaults.enabled = originalEnabled
        GlassDefaults.quietPages = originalQuietPages
    }

    @Test fun quietProviderStartsWhenMeshIsShownFreezesWhenHiddenAndResumesWithoutJump() {
        compose.mainClock.autoAdvance = false
        GlassDefaults.enabled = true
        // Quiet pages do not run the mesh timeline.
        GlassDefaults.quietPages = true
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

        compose.runOnIdle { GlassDefaults.quietPages = false }
        frames(8)
        assertSame("quiet-page changes retain the provider's timeline state", providerTime, time)
        assertTrue("showing the mesh starts the actual provider clock", time.value > 0f)

        compose.runOnIdle { GlassDefaults.quietPages = true }
        // Let recomposition and effect cancellation settle before establishing the pause baseline.
        frames(2)
        val frozen = time.value
        frames(12)
        assertEquals("quiet mode remains frozen after the transition settles", frozen, time.value, .0001f)

        compose.runOnIdle { GlassDefaults.quietPages = false }
        frames(4)
        assertSame("showing the mesh again retains accumulated time", providerTime, time)
        assertTrue("visible mesh resumes the actual clock", time.value > frozen)
        assertTrue("the disabled interval is excluded rather than accumulated", time.value - frozen < .5f)
    }
}
