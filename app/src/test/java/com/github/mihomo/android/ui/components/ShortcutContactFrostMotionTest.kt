package com.github.mihomo.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.ui.glass.ShortcutRefraction
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutContactFrostMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun allVariantsStartClearThenFrostAndReturnWithoutChangingClicks() {
        val style = mutableStateOf(ShortcutTouchVariant.GentleFloat)
        var readOptics: () -> ShortcutRefraction = { ShortcutRefraction() }
        var clicks = 0
        compose.setContent {
            val motion = rememberShortcutTouchMotion(style.value, enabled = true)
            SideEffect { readOptics = motion.refraction }
            Box(Modifier.clickable { clicks++ }.then(motion.modifier).size(120.dp).testTag("frost-motion"))
        }
        val node = compose.onNodeWithTag("frost-motion")
        ShortcutTouchVariant.entries.forEach { variant ->
            compose.runOnIdle { style.value = variant }
            compose.waitForIdle()
            val previousClicks = clicks
            compose.mainClock.autoAdvance = false
            node.performTouchInput { down(center) }
            compose.waitForIdle()
            compose.runOnIdle {
                val visible = readOptics()
                assertTrue("bevel retains immediate response", visible.lift >= .35f)
                assertEquals("initial contact must not inherit the bevel kick", 0f, visible.frostLift, 0f)
            }
            compose.mainClock.advanceTimeBy(128)
            compose.waitForIdle()
            compose.runOnIdle { assertEquals("lifted slab is frosted", 1f, readOptics().frostLift, .01f) }
            node.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1000)
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals("release returns clear", 0f, readOptics().frostLift, 0f)
                assertEquals("one click on release", previousClicks + 1, clicks)
            }
        }
    }
}
