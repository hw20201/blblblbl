package com.github.mihomo.android.ui.screens

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

/** Real Dashboard contact-follow frames; frozen mesh and clocks isolate pointer response. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutContactFollowPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var oldEnabled = true
    private var oldPalette = GlassPalettes.CREAM

    @Before fun saveDefaults() { oldEnabled = GlassDefaults.enabled; oldPalette = GlassDefaults.palette }
    @After fun restoreDefaults() { GlassDefaults.enabled = oldEnabled; GlassDefaults.palette = oldPalette }

    @Test fun lightContactFollowABCAndTapCounts() = contactFollowABC(dark = false)
    @Test fun darkContactFollowABC() = contactFollowABC(dark = true)

    private data class Fixture(val scroll: ScrollState, var profiles: Int = 0, var vpn: Int = 0)

    private fun contactFollowABC(dark: Boolean) {
        val variant = mutableStateOf(ShortcutTouchVariant.GentleFloat)
        val fixture = render(dark, ScrollState(0), variant = variant)
        val dir = out()
        val initial = fixture.scroll.value
        // A / C retain a compact set of keyframes; B records the complete path and settles.
        listOf("Profiles" to ShortcutTouchVariant.GentleFloat, "Rules" to ShortcutTouchVariant.BuoyantSpring).forEach { (title, choice) ->
            compose.runOnIdle { variant.value = choice }
            compose.waitForIdle()
            val card = compose.onNodeWithTag("shortcut-card-$title")
            save(screenshot(), dir, "${if (dark) "dark" else "light"}-contact-$title-rest.png")
            card.performTouchInput { down(Offset(center.x * 2f * .28f, center.y * 2f * .48f)) }
            compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
            save(screenshot(), dir, "${if (dark) "dark" else "light"}-contact-$title-down.png")
            card.performTouchInput { moveTo(Offset(center.x * 2f * .72f, center.y * 2f * .48f), delayMillis = 160) }
            compose.mainClock.advanceTimeBy(160); compose.waitForIdle()
            save(screenshot(), dir, "${if (dark) "dark" else "light"}-contact-$title-move.png")
            card.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(700); compose.waitForIdle()
            save(screenshot(), dir, "${if (dark) "dark" else "light"}-contact-$title-released.png")
        }

        compose.runOnIdle { variant.value = ShortcutTouchVariant.FingerTilt }
        compose.waitForIdle()
        val card = compose.onNodeWithTag("shortcut-card-Nodes")
        val theme = if (dark) "dark" else "light"
        compose.mainClock.autoAdvance = false
        save(screenshot(), dir, "$theme-contact-B-rest.png")
        card.performTouchInput { down(Offset(center.x * 2f * .28f, center.y * 2f * .48f)) }
        compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
        save(screenshot(), dir, "$theme-contact-B-down-left.png")
        compose.runOnIdle {
            assertEquals("down does not navigate", 0, fixture.profiles)
            assertEquals("down does not toggle VPN", 0, fixture.vpn)
            assertEquals(initial, fixture.scroll.value)
        }
        // Explicit hold-before-pickup locks the optical path in all directions. Ordinary
        // horizontal follow yielding to page scroll is exercised by the Dashboard gesture tests.
        card.performTouchInput { moveTo(Offset(center.x * 2f * .72f, center.y * 2f * .48f), delayMillis = 600) }
        compose.mainClock.advanceTimeBy(180); compose.waitForIdle()
        save(screenshot(), dir, "$theme-contact-B-horizontal-right.png")
        card.performTouchInput { moveTo(Offset(center.x * 2f * .72f, center.y * 2f * .73f), delayMillis = 140) }
        compose.mainClock.advanceTimeBy(140); compose.waitForIdle()
        save(screenshot(), dir, "$theme-contact-B-right-bottom.png")
        card.performTouchInput { moveTo(Offset(center.x * 2f * .50f, center.y * 2f * .28f), delayMillis = 140) }
        compose.mainClock.advanceTimeBy(140); compose.waitForIdle()
        save(screenshot(), dir, "$theme-contact-B-center-up.png")
        compose.runOnIdle {
            assertEquals("locked pointer motion must not scroll the Dashboard", initial, fixture.scroll.value)
            assertEquals(0, fixture.profiles); assertEquals(0, fixture.vpn)
        }
        card.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
        save(screenshot(), dir, "$theme-contact-B-released-80ms.png")
        compose.mainClock.advanceTimeBy(620); compose.waitForIdle()
        compose.mainClock.autoAdvance = true
        save(screenshot(), dir, "$theme-contact-B-settled-700ms.png")
        compose.runOnIdle {
            assertEquals(initial, fixture.scroll.value)
            assertEquals(0, fixture.profiles); assertEquals(0, fixture.vpn)
        }
        compose.onNodeWithTag("shortcut-card-Profiles").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, fixture.profiles); assertEquals(0, fixture.vpn) }

        // Separate gesture begins inside the card and moves vertically: this must remain ordinary Dashboard scrolling.
        val beforeScroll = fixture.scroll.value
        card.performTouchInput {
            down(Offset(center.x * 2f * .5f, center.y * 2f * .45f))
            moveTo(Offset(center.x * 2f * .5f, center.y * 2f * .18f), delayMillis = 100)
            moveTo(Offset(center.x * 2f * .5f, center.y * 2f * .05f), delayMillis = 100)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("vertical-origin gesture should scroll", fixture.scroll.value > beforeScroll) }
        assertEquals("scroll gesture must not navigate or toggle VPN", 1, fixture.profiles)
        assertEquals(0, fixture.vpn)
    }

    @Test fun oldAndNewContactFollowDownSameFrozenMesh() {
        val enabled = mutableStateOf(false)
        val fixture = render(false, ScrollState(0), contactFollow = enabled)
        val card = compose.onNodeWithTag("shortcut-card-Nodes")
        val dir = out()
        compose.mainClock.autoAdvance = false
        save(screenshot(), dir, "comparison-rest-frozen-mesh.png")
        card.performTouchInput { down(Offset(center.x * 2f * .28f, center.y * 2f * .48f)) }
        compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
        save(screenshot(), dir, "comparison-old-down-frozen-mesh.png")
        card.performTouchInput { moveTo(Offset(center.x * 2f * .72f, center.y * 2f * .48f), delayMillis = 100); up() }
        compose.mainClock.advanceTimeBy(700); compose.waitForIdle()
        compose.runOnIdle { enabled.value = true }
        compose.mainClock.advanceTimeBy(16)
        compose.waitForIdle()
        card.performTouchInput { down(Offset(center.x * 2f * .28f, center.y * 2f * .48f)) }
        compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
        save(screenshot(), dir, "comparison-new-down-frozen-mesh.png")
        card.performTouchInput { moveTo(Offset(center.x * 2f * .72f, center.y * 2f * .48f), delayMillis = 100); up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(0, fixture.profiles); assertEquals(0, fixture.vpn) }
    }

    private fun render(dark: Boolean, scroll: ScrollState, contactFollow: State<Boolean> = mutableStateOf(true),
        variant: State<ShortcutTouchVariant> = mutableStateOf(ShortcutTouchVariant.FingerTilt)): Fixture {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        val fixture = Fixture(scroll)
        val seconds = mutableFloatStateOf(24f)
        compose.setContent {
            MihomoTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "en", LocalSoftMeshTime provides seconds,
                    LocalSoftMeshSeed provides 734921L,
                    LocalShortcutTouchVariant provides variant.value,
                    LocalShortcutContactFollowEnabled provides contactFollow.value
                ) {
                    ProvideGlassTheme {
                        val cardScene = rememberNavigationLens(); val dockScene = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            NexusGlassBackground(modifier = Modifier.recordNavigationScene(dockScene),
                                backdropRecorder = Modifier.recordNavigationScene(cardScene)) {
                                val shortcuts = listOf("profiles", "nodes", "rules", "request_logs", "ip_test", "core_logs", "plugins", "network_share", "vpn")
                                DashboardScreen(scrollState = scroll, vpnState = VpnState(status = VpnState.Status.STOPPED),
                                    currentMode = TunnelMode.RULE, activeProfileName = "Local config", mainGroupName = "",
                                    mainGroupProxies = emptyList(), activeNodeName = "", totalNodes = 12, totalRules = 64,
                                    visibleShortcuts = shortcuts.toSet(), shortcutOrder = shortcuts,
                                    onToggleVpn = { fixture.vpn++ }, onModeSelected = {}, onSelectProxy = { _, _ -> },
                                    onHealthCheck = {}, onNavigateToProfiles = { fixture.profiles++ },
                                    onNavigateToAppRouting = {}, onNavigateToLogs = {}, onUpdateVisibleShortcuts = {},
                                    onUpdateShortcutOrder = {}, glassScene = cardScene)
                            }
                            LoonFloatingBottomBar(0, {}, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), dockScene)
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
        val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        val result = AtomicInteger(-1)
        compose.runOnIdle { PixelCopy.request(window, bitmap, { result.set(it) }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle(); compose.waitUntil(5000) { result.get() != -1 }
        assertEquals("Robolectric NATIVE PixelCopy", PixelCopy.SUCCESS, result.get())
        return bitmap
    }

    private fun save(bitmap: Bitmap, dir: File, name: String) {
        dir.resolve(name).outputStream().use { assertTrue("write $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    private fun out() = File("build/outputs/contact-follow-dashboard").also { it.mkdirs() }
}
