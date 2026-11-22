package com.github.arca.android.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.InstalledApp
import com.github.arca.android.data.ParsedRule
import com.github.arca.android.data.PerAppProxyMode
import com.github.arca.android.data.ProfileItem
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.data.ConfigManager
import com.github.arca.android.service.TrafficMetrics
import com.github.arca.android.service.VpnState
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.theme.MihomoTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardShortcutActionPanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ipFlagsDistinguishUnavailableNoHitsAndPositiveTags() {
        assertEquals("来源未返回", outboundIpFlagsSummary(null))
        assertEquals("本次未命中已知标记", outboundIpFlagsSummary(emptyList()))
        assertEquals("代理 · VPN", outboundIpFlagsSummary(listOf("Proxy", "VPN")))
    }

    @Test
    fun ipClassificationUsesOnlyReturnedValues() {
        assertEquals("来源未提供", outboundIpClassification(null, null))
        assertEquals("机房", outboundIpClassification(null, "Hosting"))
        assertEquals("住宅", outboundIpClassification(null, "Residential"))
        assertEquals("Elevated · 机房", outboundIpClassification("Elevated", "Hosting"))
    }

    @Test
    fun profileChoicesCanBeLongPressedReorderedAndPersistedWithoutChangingClickSemantics() {
        val persisted = AtomicReference<String?>()
        val selected = AtomicReference<String?>()
        val closeCount = AtomicInteger()
        val profiles = listOf("a", "b", "c").map {
            ProfileItem(it, "Profile $it", null, File("$it.yaml"))
        }
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    Box(Modifier.fillMaxSize()) {
                        DashboardShortcutActionPanel(
                            shortcut = ShortcutDef("profiles", "配置文件", "", icon = {}, onClick = {}),
                            vpnStatus = VpnState.Status.STOPPED,
                            activeProfileName = "Profile a",
                            profiles = profiles,
                            selectedProfileId = "a",
                            onSelectProfile = { selected.set(it.id) },
                            onReorderProfiles = { ordered ->
                                ConfigManager.saveProfileOrder(compose.activity, ordered.map { it.id })
                                persisted.set(compose.activity.getSharedPreferences("profiles_meta", 0)
                                    .getString("custom_profile_order", null))
                            },
                            rules = emptyList<ParsedRule>(),
                            requestMonitoringEnabled = false,
                            fakeIpEnabled = false,
                            onFakeIpEnabledChanged = {},
                            perAppProxyMode = PerAppProxyMode.DISABLED,
                            selectedPackages = emptySet(),
                            apps = emptyList<InstalledApp>(),
                            isAppsLoading = false,
                            onEnsureAppsLoaded = {},
                            onPerAppProxyModeChanged = {},
                            onToggleAppPackage = {},
                            allowLan = false,
                            onAllowLanChanged = {},
                            trafficMetrics = MutableStateFlow(TrafficMetrics()),
                            coreVersion = "",
                            onClose = { closeCount.incrementAndGet() }
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("quick-profile-choice-a").assertIsDisplayed()
        compose.onNodeWithTag("quick-profile-choice-b").assertIsDisplayed()
        compose.onNodeWithTag("quick-profile-choice-c").assertIsDisplayed()
        val fromBounds = compose.onNodeWithTag("quick-profile-choice-a").getUnclippedBoundsInRoot()
        val toBounds = compose.onNodeWithTag("quick-profile-choice-c").getUnclippedBoundsInRoot()
        val dragDistance = ((toBounds.top.value + toBounds.bottom.value) -
            (fromBounds.top.value + fromBounds.bottom.value)) / 2f + 1f
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("quick-profile-choice-a").performTouchInput {
            down(center)
            advanceEventTime(650)
            moveBy(Offset.Zero)
            moveBy(Offset(0f, dragDistance), delayMillis = 16)
            up()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.waitUntil(5_000) { persisted.get() != null }
        assertEquals("b,c,a", persisted.get())
        assertEquals(null, selected.get())
        assertEquals(0, closeCount.get())

        compose.onNodeWithTag("quick-profile-choice-a").performClick()
        assertEquals(null, selected.get())
        assertEquals(0, closeCount.get())
        compose.onNodeWithTag("quick-profile-choice-b").performClick()
        assertEquals("b", selected.get())
        assertEquals(1, closeCount.get())
    }
}
