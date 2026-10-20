package com.github.mihomo.android.ui.glass

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SoftMeshLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun accumulatesOnlyWhileResumedAndDoesNotJumpAfterBackground() {
        compose.mainClock.autoAdvance = false
        lateinit var time: State<Float>
        lateinit var view: android.view.View
        compose.setContent {
            time = rememberSoftMeshTime()
            view = LocalView.current
            Box(Modifier.fillMaxSize())
        }
        compose.waitForIdle()
        fun focus(hasFocus: Boolean) {
            compose.runOnIdle {
                // ActivityScenario's synthetic window retains GONE despite isShown/focus=true.
                // Set its actual AttachInfo, not an injected animation flag or replacement clock.
                val info = ReflectionHelpers.getField<Any>(view, "mAttachInfo")
                ReflectionHelpers.setField(info, "mWindowVisibility", android.view.View.VISIBLE)
                ReflectionHelpers.setField(info, "mHasWindowFocus", hasFocus)
                ReflectionHelpers.callInstanceMethod<Unit>(view.viewTreeObserver, "dispatchOnWindowFocusChange",
                    ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, hasFocus))
            }
        }
        fun frames(count: Int) {
            repeat(count) {
                shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
                compose.mainClock.advanceTimeBy(64)
                compose.waitForIdle()
            }
        }
        focus(true)
        frames(8)
        val beforePause = time.value
        println("Clock gates: lifecycle=${compose.activity.lifecycle.currentState}, focused=${view.hasWindowFocus()}, shown=${view.isShown}, visibility=${view.windowVisibility}, animations=${android.animation.ValueAnimator.areAnimatorsEnabled()}")
        assertTrue("active frames should advance the actual clock: $beforePause", beforePause > 0f)

        focus(false)
        frames(2)
        val atFocusLoss = time.value
        frames(6)
        assertEquals("unfocused window stops frame updates", atFocusLoss, time.value, .0001f)

        focus(true)
        frames(4)
        fun windowVisibility(visible: Boolean) {
            compose.runOnIdle {
                val info = ReflectionHelpers.getField<Any>(view, "mAttachInfo")
                ReflectionHelpers.setField(info, "mWindowVisibility",
                    if (visible) android.view.View.VISIBLE else android.view.View.GONE)
                // No focus/attach/layout event: becoming visible only produces a pre-draw.
                view.viewTreeObserver.dispatchOnPreDraw()
            }
        }
        windowVisibility(false)
        frames(2)
        val atHidden = time.value
        frames(6)
        assertEquals("window visibility alone stops the clock", atHidden, time.value, .0001f)
        windowVisibility(true)
        frames(4)
        assertTrue("first visible draw restarts clock without a focus/layout callback", time.value > atHidden)

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitForIdle()
        val atPause = time.value
        frames(12)
        assertEquals("time freezes while CREATED", atPause, time.value, .0001f)

        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        focus(true)
        frames(4)
        assertTrue("actual clock resumes", time.value > atPause)
        assertTrue("background duration is excluded", time.value - atPause < .5f)
        println("Actual Compose clock: active=$beforePause, focus-loss=$atFocusLoss, background=$atPause, resumed=${time.value}")
    }
}
