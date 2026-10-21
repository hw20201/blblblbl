package com.github.mihomo.android.ui.screens

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.components.*
import com.github.mihomo.android.ui.glass.*
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Real Dashboard scroll lock, not a replacement parent fixture. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutDashboardPickupGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var oldEnabled = true
    private var oldPalette = GlassPalettes.CREAM
    @Before fun saveGlassDefaults() { oldEnabled = GlassDefaults.enabled; oldPalette = GlassDefaults.palette }
    @After fun restoreGlassDefaults() { GlassDefaults.enabled = oldEnabled; GlassDefaults.palette = oldPalette }

    @Test fun aPickupKeepsScrollStationaryAndRestoresTap() = heldPickup(ShortcutTouchVariant.GentleFloat, "A")
    @Test fun bPickupKeepsScrollStationaryAndRestoresTap() = heldPickup(ShortcutTouchVariant.FingerTilt, "B")
    @Test fun cPickupKeepsScrollStationaryAndRestoresTap() = heldPickup(ShortcutTouchVariant.BuoyantSpring, "C")
    @Test fun aCardVerticalDragScrollsWithoutNavigation() = verticalCardDrag(ShortcutTouchVariant.GentleFloat, "A")
    @Test fun bCardVerticalDragScrollsWithoutNavigation() = verticalCardDrag(ShortcutTouchVariant.FingerTilt, "B")
    @Test fun cCardVerticalDragScrollsWithoutNavigation() = verticalCardDrag(ShortcutTouchVariant.BuoyantSpring, "C")

    private fun heldPickup(variant: ShortcutTouchVariant, name: String) {
        val fixture = render(variant)
        val card = compose.onNodeWithTag("shortcut-card-Profiles")
        val bounds = card.fetchSemanticsNode().boundsInRoot
        assertTrue("drag target remains inside screen", bounds.center.x + 140f < compose.activity.window.decorView.width)
        assertTrue("drag target remains inside screen", bounds.center.y + 140f < compose.activity.window.decorView.height)
        val dir = File("build/outputs/refractive-followup-gesture").also { it.mkdirs() }
        save(screenshot(), dir, "$name-before.png")
        val initial = compose.runOnIdle { fixture.scroll.value }
        compose.mainClock.autoAdvance = false
        card.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(48); compose.waitForIdle()
        save(screenshot(), dir, "$name-held.png")
        compose.runOnIdle {
            assertEquals("down must not navigate", 0, fixture.profilesClicks)
            assertEquals("stationary press does not itself move the viewport", initial, fixture.scroll.value)
        }
        // Establish clear horizontal intent before the diagonal continuation locks pickup.
        card.performTouchInput { moveTo(center + Offset(100f, 0f), delayMillis = 120) }
        card.performTouchInput { moveTo(center + Offset(140f, 140f), delayMillis = 120) }
        compose.mainClock.advanceTimeBy(150); compose.waitForIdle()
        save(screenshot(), dir, "$name-moved-held.png")
        compose.runOnIdle {
            assertEquals("scroll stays locked even beyond original card bounds", initial, fixture.scroll.value)
            assertEquals("drag must not click", 0, fixture.profilesClicks)
            assertEquals("drag must not toggle VPN", 0, fixture.vpnToggles)
        }
        card.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        compose.mainClock.autoAdvance = true
        save(screenshot(), dir, "$name-release.png")
        card.performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals("normal tap after release", 1, fixture.profilesClicks)
            assertEquals("only profile navigation fires", 0, fixture.vpnToggles)
        }
    }

    private fun verticalCardDrag(variant: ShortcutTouchVariant, name: String) {
        val fixture = render(variant)
        val card = compose.onNodeWithTag("shortcut-card-Profiles")
        val before = fixture.scroll.value
        assertTrue("fixture really has scrollable content", fixture.scroll.maxValue > 0)
        val dir = File("build/outputs/refractive-followup-gesture").also { it.mkdirs() }
        if (name == "B") save(screenshot(), dir, "B-before-vertical-card-scroll.png")
        card.performTouchInput {
            down(center)
            moveTo(center + Offset(0f, -40f), delayMillis = 80)
            moveTo(center + Offset(0f, -100f), delayMillis = 100)
            up()
        }
        compose.waitForIdle()
        if (name == "B") save(screenshot(), dir, "B-vertical-card-scroll.png")
        compose.runOnIdle {
            assertTrue("vertical gesture from inside card scrolls Dashboard", fixture.scroll.value > before)
            assertEquals("vertical gesture must not navigate", 0, fixture.profilesClicks)
            assertEquals("vertical gesture must not toggle VPN", 0, fixture.vpnToggles)
        }
    }

    @Test fun editReorderCancelKeepsClicksVpnAndSavedOrderUntouched() {
        val fixture = render()
        compose.onNodeWithText("Edit").performClick()
        val card = compose.onNodeWithTag("shortcut-card-Profiles")
        val rest = card.fetchSemanticsNode().boundsInRoot
        val dir = File("build/outputs/shortcut-floating-edit").also { it.mkdirs() }
        save(screenshot(), dir, "before.png")
        compose.mainClock.autoAdvance = false
        // Input timestamps alone do not advance the long-press coroutine while the
        // Compose clock is paused. Dispatch down before advancing both clocks.
        // Root coordinates remain stable when the dragged card itself moves.
        val root = compose.onRoot()
        root.performTouchInput { down(rest.center) }
        compose.mainClock.advanceTimeBy(650); compose.waitForIdle()
        root.performTouchInput {
            advanceEventTime(650)
            moveTo(rest.center + Offset(2f, 0f), delayMillis = 16)
        }
        compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
        root.performTouchInput { moveTo(rest.center + Offset(120f, 24f), delayMillis = 80) }
        compose.mainClock.advanceTimeBy(160); compose.waitForIdle()
        // Flush the placement frame after the final input's snapshot/recomposition.
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        assertTrue("edit reorder moves the actual card", card.fetchSemanticsNode().boundsInRoot.center.x > rest.center.x + 80f)
        // Capture the production edit path for hardware pixel/outline acceptance elsewhere.
        save(screenshot(), dir, "dragging.png")
        root.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        compose.mainClock.autoAdvance = true
        val cancelled = card.fetchSemanticsNode().boundsInRoot
        assertEquals("cancel restores the original horizontal slot", rest.center.x, cancelled.center.x, 1f)
        assertEquals("cancel restores the original vertical slot", rest.center.y, cancelled.center.y, 1f)
        compose.runOnIdle {
            assertEquals("cancel cannot navigate", 0, fixture.profilesClicks)
            assertEquals("cancel cannot change VPN", 0, fixture.vpnToggles)
            assertEquals("cancel cannot persist a reorder", 0, fixture.orderWrites)
        }
        save(screenshot(), dir, "cancelled.png")
        compose.onNodeWithText("Done").performClick()
        card.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals("click is restored after editing", 1, fixture.profilesClicks) }
    }

    @Test fun tapOnProfilesInvokesOnlyProfilesNavigation() {
        val fixture = render()
        compose.onNodeWithTag("shortcut-card-Profiles").performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(1, fixture.profilesClicks); assertEquals(0, fixture.vpnToggles); assertEquals(0, fixture.scroll.value)
        }
    }

    @Test fun blankColumnGapRemainsScrollable() {
        val fixture = render()
        val left = compose.onNodeWithTag("shortcut-card-Profiles").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("shortcut-card-Nodes").fetchSemanticsNode().boundsInRoot
        val x = (left.right + right.left) / 2f
        assertTrue("actual column gap", x > left.right && x < right.left)
        assertTrue("fixture really has scrollable content", fixture.scroll.maxValue > 0)
        val start = Offset(x, left.center.y)
        compose.onRoot().performTouchInput {
            down(start)
            moveTo(start + Offset(0f, -60f), delayMillis = 80)
            moveTo(start + Offset(0f, -130f), delayMillis = 80)
            up()
        }
        compose.runOnIdle { assertTrue("blank-origin drag scrolls Dashboard", fixture.scroll.value > 0) }
    }

    private data class Fixture(val scroll: ScrollState, var profilesClicks: Int = 0,
        var vpnToggles: Int = 0, var orderWrites: Int = 0)
    private fun render(variant: ShortcutTouchVariant = ShortcutTouchVariant.FingerTilt): Fixture {
        GlassDefaults.enabled = true; GlassDefaults.palette = GlassPalettes.CREAM
        val fixture = Fixture(ScrollState(0))
        val seconds = mutableFloatStateOf(24f)
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "en", LocalSoftMeshTime provides seconds,
                    LocalSoftMeshSeed provides 734921L, LocalShortcutTouchVariant provides variant) {
                    ProvideGlassTheme {
                        val cardScene = rememberNavigationLens(); val dockScene = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(modifier = Modifier.recordNavigationScene(dockScene),
                                backdropRecorder = Modifier.recordNavigationScene(cardScene)) {
                                val shortcuts = listOf("profiles", "nodes", "rules", "request_logs", "ip_test", "core_logs", "plugins", "network_share", "vpn")
                                DashboardScreen(scrollState = fixture.scroll, vpnState = VpnState(status = VpnState.Status.STOPPED),
                                    currentMode = TunnelMode.RULE, activeProfileName = "Local config", mainGroupName = "",
                                    mainGroupProxies = emptyList(), activeNodeName = "", totalNodes = 12, totalRules = 64,
                                    visibleShortcuts = shortcuts.toSet(), shortcutOrder = shortcuts,
                                    onToggleVpn = { fixture.vpnToggles++ }, onModeSelected = {}, onSelectProxy = { _, _ -> },
                                    onHealthCheck = {}, onNavigateToProfiles = { fixture.profilesClicks++ },
                                    onNavigateToAppRouting = {}, onNavigateToLogs = {}, onUpdateVisibleShortcuts = {},
                                    onUpdateShortcutOrder = { fixture.orderWrites++ }, glassScene = cardScene)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        return fixture
    }

    private fun screenshot(): Bitmap {
        val window = compose.activity.window
        val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        val result = AtomicInteger(-1)
        compose.runOnIdle { PixelCopy.request(window, image, { result.set(it) }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle(); compose.waitUntil(5000) { result.get() != -1 }; assertEquals(PixelCopy.SUCCESS, result.get())
        return image
    }
    private fun save(image: Bitmap, dir: File, name: String) {
        dir.resolve(name).outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }; image.recycle()
    }
}
