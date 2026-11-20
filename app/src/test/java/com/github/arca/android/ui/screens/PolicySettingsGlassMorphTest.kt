package com.github.arca.android.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.core.model.TunnelMode
import com.github.arca.android.data.InstalledApp
import com.github.arca.android.data.ParsedRule
import com.github.arca.android.data.PerAppProxyMode
import com.github.arca.android.data.ProfileItem
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.data.TunStack
import com.github.arca.android.ui.components.ArcaGlasmioSwitch
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.GlasmioFrostedTuning
import com.github.arca.android.ui.glass.GlassPalettes
import com.github.arca.android.ui.glass.GlassPreset
import com.github.arca.android.ui.glass.GlassSettings
import com.github.arca.android.ui.glass.LocalArcaGlasmioBackdrop
import com.github.arca.android.ui.glass.LocalGlassSettings
import com.github.arca.android.ui.glass.NexusGlassBackground
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.glass.rememberArcaGlasmioBackdrop
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "zh-w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PolicySettingsGlassMorphTest {
    @get:Rule val compose = createComposeRule()

    @After
    fun resetGlass() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
    }

    @Test
    fun policyMoreButtonReallyExpandsIntoPanelAndKeepsViewModeCallback() {
        val viewMode = mutableStateOf("TABS")
        var speedTestGroup: String? = null
        setGlassContent {
            ProxiesScreen(
                groups = listOf("Proxy"),
                groupMap = emptyMap(),
                currentGroup = null,
                selectedGroupName = "Proxy",
                currentViewMode = viewMode.value,
                showGroupIcons = true,
                nodeColumns = 1,
                onViewModeChanged = { viewMode.value = it },
                onShowGroupIconsChanged = {},
                onNodeColumnsChanged = {},
                onSelectGroup = {},
                onSelectProxy = { _, _ -> },
                onHealthCheck = { speedTestGroup = it }
            )
        }

        val sourceHeight = compose.onNodeWithTag("policy-more-button").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("policy-more-button").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("policy-options-panel").assertExists()
        compose.onNodeWithText("策略设置").assertExists()
        val panelHeight = compose.onNodeWithTag("policy-options-panel").fetchSemanticsNode().boundsInRoot.height
        assertTrue("more button should grow into a real glass panel, source=$sourceHeight panel=$panelHeight", panelHeight > sourceHeight * 5f)

        compose.onNodeWithText("列表模式").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("LIST", viewMode.value) }
        compose.onNodeWithTag("policy-options-panel").assertExists()
        compose.onNodeWithTag("policy-options-close").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("policy-options-panel").assertDoesNotExist()

        compose.onNodeWithTag("policy-health-check-button").performClick()
        compose.runOnIdle { assertEquals("Proxy", speedTestGroup) }
    }

    @Test
    fun refractingPolicySegmentsKeepTapDragAndDismissCallbacks() {
        val viewMode = mutableStateOf("TABS")
        val columns = mutableStateOf(1)
        setGlasmioContent {
            ProxiesScreen(
                groups = listOf("Proxy"), groupMap = emptyMap(), currentGroup = null,
                selectedGroupName = "Proxy", currentViewMode = viewMode.value,
                showGroupIcons = true, nodeColumns = columns.value,
                onViewModeChanged = { viewMode.value = it },
                onShowGroupIconsChanged = {}, onNodeColumnsChanged = { columns.value = it },
                onSelectGroup = {}, onSelectProxy = { _, _ -> }, onHealthCheck = {}
            )
        }
        compose.onNodeWithTag("policy-more-button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("proxies-view-mode-glasmio-control").assertIsDisplayed()
        compose.onNodeWithTag("proxies-node-columns-glasmio-control").assertIsDisplayed()
        compose.onNodeWithTag("proxies-view-mode-selection-lens").assertIsDisplayed()
        compose.onNodeWithTag("proxies-node-columns-optical-labels").assertIsDisplayed()
        compose.onNodeWithText("列表模式").performClick()
        compose.onNodeWithText("三列").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("LIST", viewMode.value)
            assertEquals(3, columns.value)
        }
        val track = compose.onNodeWithTag("proxies-node-columns-glasmio-control")
        val trackBounds = track.getUnclippedBoundsInRoot()
        val firstTarget = compose.onNodeWithTag("proxies-node-columns-1").getUnclippedBoundsInRoot()
        track.performTouchInput {
            down(Offset(width * 5f / 6f, centerY))
            moveTo(Offset(width * .52f, centerY), 300)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("selection waits until release", 3, columns.value) }
        val movingLens = compose.onNodeWithTag("proxies-node-columns-selection-lens").getUnclippedBoundsInRoot()
        val movingCenter = (movingLens.left.value + movingLens.right.value) / 2f
        val width = trackBounds.right.value - trackBounds.left.value
        assertTrue("lens follows the finger between confirmed slots",
            movingCenter > trackBounds.left.value + width * .4f &&
                movingCenter < trackBounds.left.value + width * .7f)
        assertEquals("the optical layer must not move the hit targets", firstTarget,
            compose.onNodeWithTag("proxies-node-columns-1").getUnclippedBoundsInRoot())
        track.performTouchInput {
            moveTo(Offset(width / 6f, centerY), 200)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, columns.value) }
        compose.onNodeWithTag("policy-options-close").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("policy-options-panel").assertDoesNotExist()
        compose.onNodeWithTag("policy-more-button").assertIsDisplayed()
    }

    @Test
    fun policyMenuReturnsToItsButtonFaceBeforeTheOverlayDisappears() {
        setGlassContent {
            ProxiesScreen(
                groups = listOf("Proxy"),
                groupMap = emptyMap(),
                currentGroup = null,
                selectedGroupName = "Proxy",
                currentViewMode = "TABS",
                showGroupIcons = true,
                nodeColumns = 1,
                onViewModeChanged = {},
                onShowGroupIconsChanged = {},
                onNodeColumnsChanged = {},
                onSelectGroup = {},
                onSelectProxy = { _, _ -> },
                onHealthCheck = {}
            )
        }
        compose.mainClock.autoAdvance = false

        val sourceHeight = compose.onNodeWithTag("policy-more-button").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("policy-more-button").performClick()
        advanceFrames(58)
        val openHeight = compose.onNodeWithTag("policy-options-panel").fetchSemanticsNode().boundsInRoot.height
        assertTrue(openHeight > sourceHeight * 5f)

        compose.onNodeWithTag("policy-options-close").performClick()
        advanceFrames(10)
        compose.onNodeWithTag("policy-options-panel").assertExists()
        val closingHeight = compose.onNodeWithTag("policy-options-panel").fetchSemanticsNode().boundsInRoot.height
        assertTrue("closing glass should be smaller than its open panel", closingHeight < openHeight)
        assertTrue("the source face should be visible before the glass fully lands", closingHeight > sourceHeight)
        val closingProgress = ((closingHeight - sourceHeight) / (openHeight - sourceHeight)).coerceIn(0f, 1f)
        val sourceFaceExists = runCatching { compose.onNodeWithTag("policy-more-source-face").fetchSemanticsNode() }.isSuccess
        assertTrue("source icon face should be in the closing handoff (estimated progress=$closingProgress)", sourceFaceExists)

        advanceFrames(20)
        compose.onNodeWithTag("policy-options-panel").assertDoesNotExist()
        compose.onNodeWithTag("policy-more-button").assertExists()
    }

    @Test
    fun settingsEntryMorphsIntoDetailPageAndSelectionUsesExistingCallback() {
        var selectedStack = TunStack.DEFAULT
        setGlassContent {
            SettingsScreen(
                scrollState = ScrollState(0),
                tunStack = TunStack.DEFAULT,
                bootOnStartup = false,
                allowLan = false,
                fakeIpEnabled = true,
                onTunStackChanged = { selectedStack = it },
                onBootOnStartupChanged = {},
                onAllowLanChanged = {},
                onNavigateToConfigurationCenter = {},
                onNavigateToLogs = {}
            )
        }

        val sourceHeight = compose.onNodeWithTag("settings-tun-stack-entry").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("settings-tun-stack-entry").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("settings-detail-page").assertExists()
        compose.onNodeWithTag("settings-detail-content-body").assertExists()
        compose.onNodeWithText("TUN 协议栈").assertExists()
        val pageHeight = compose.onNodeWithTag("settings-detail-page").fetchSemanticsNode().boundsInRoot.height
        val rootHeight = compose.onRoot().fetchSemanticsNode().boundsInRoot.height
        assertTrue("setting cell should expand into a compact detail surface, source=$sourceHeight page=$pageHeight", pageHeight > sourceHeight * 6f)
        assertTrue("four options should not leave a screen-sized empty panel, root=$rootHeight page=$pageHeight", pageHeight < rootHeight * 0.62f)

        compose.onNodeWithTag("settings-detail-tun-gvisor").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(TunStack.GVISOR, selectedStack) }
        compose.onNodeWithTag("settings-detail-page").assertDoesNotExist()
    }

    @Test
    fun settingsPageReturnsToItsSourceRowBeforeTheOverlayDisappears() {
        setGlassContent {
            SettingsScreen(
                scrollState = ScrollState(0),
                tunStack = TunStack.DEFAULT,
                bootOnStartup = false,
                allowLan = false,
                fakeIpEnabled = true,
                onTunStackChanged = {},
                onBootOnStartupChanged = {},
                onAllowLanChanged = {},
                onNavigateToConfigurationCenter = {},
                onNavigateToLogs = {}
            )
        }
        compose.mainClock.autoAdvance = false

        val sourceHeight = compose.onNodeWithTag("settings-tun-stack-entry").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("settings-tun-stack-entry").performClick()
        advanceFrames(58)
        val openHeight = compose.onNodeWithTag("settings-detail-page").fetchSemanticsNode().boundsInRoot.height
        assertTrue(openHeight > sourceHeight * 6f)
        val rootHeight = compose.onRoot().fetchSemanticsNode().boundsInRoot.height
        assertTrue("content-sized detail remains below the previous near-full-height panel", openHeight < rootHeight * 0.62f)

        val closeBounds = compose.onNodeWithTag("settings-detail-back").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { click(Offset(closeBounds.center.x, closeBounds.center.y)) }
        advanceFrames(9)
        compose.onNodeWithTag("settings-detail-page").assertExists()
        val closingHeight = compose.onNodeWithTag("settings-detail-page").fetchSemanticsNode().boundsInRoot.height
        assertTrue("closing settings glass should be smaller than the open page", closingHeight < openHeight)
        assertTrue("the source settings row should return before the page lands", closingHeight > sourceHeight)
        val closingProgress = ((closingHeight - sourceHeight) / (openHeight - sourceHeight)).coerceIn(0f, 1f)
        val sourceFaceExists = runCatching { compose.onNodeWithTag("settings-detail-source-face").fetchSemanticsNode() }.isSuccess
        assertTrue("source settings row should be in the closing handoff (estimated progress=$closingProgress)", sourceFaceExists)

        advanceFrames(3)
        val settledHeight = compose.onNodeWithTag("settings-detail-page").fetchSemanticsNode().boundsInRoot.height
        assertTrue("the final morph segment continues shrinking instead of snapping", settledHeight < closingHeight)
        assertTrue("the panel stays attached to its source through the final settle", settledHeight > sourceHeight)

        advanceFrames(12)
        compose.onNodeWithTag("settings-detail-page").assertDoesNotExist()
    }

    @Test
    fun tunAndMipsOptionPanelsShowEveryChoiceWithAVisibleBottomTail() {
        setGlassContent {
            SettingsScreen(
                scrollState = ScrollState(0), tunStack = TunStack.MIPS,
                bootOnStartup = false, allowLan = false, fakeIpEnabled = true,
                onTunStackChanged = {}, onBootOnStartupChanged = {}, onAllowLanChanged = {},
                onNavigateToConfigurationCenter = {}, onNavigateToLogs = {}
            )
        }

        compose.onNodeWithTag("settings-tun-stack-entry").performClick()
        compose.waitForIdle()
        listOf("mixed", "gvisor", "system", "mips").forEach {
            compose.onNodeWithTag("settings-detail-tun-$it").assertIsDisplayed()
        }
        assertVisiblePanelTail("settings-detail-tun-mips")
        compose.onNodeWithTag("settings-detail-back").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("settings-mips-congestion-entry").performScrollTo().performClick()
        compose.waitForIdle()
        (0..3).forEach { compose.onNodeWithTag("settings-detail-congestion-$it").assertIsDisplayed() }
        assertVisiblePanelTail("settings-detail-congestion-3")
    }

    @Test
    fun appearanceGlassAndDnsRowsOpenMorphPanelsAndKeepTheirExistingSaveAndBackCallbacks() {
        val theme = mutableStateOf(ThemeMode.DARK)
        val glass = mutableStateOf(GlassSettings(glasmioFrosted = GlasmioFrostedTuning(blurRadiusDp = 0f)))
        val glassScrollState = ScrollState(0)
        val dnsListState = LazyListState(0)
        val fakeIp = mutableStateOf(true)
        var fallbackNavigationCount = 0
        setGlassContent {
            SettingsScreen(
                scrollState = ScrollState(0), tunStack = TunStack.DEFAULT,
                bootOnStartup = false, allowLan = false, fakeIpEnabled = fakeIp.value,
                themeMode = theme.value,
                onTunStackChanged = {}, onBootOnStartupChanged = {}, onAllowLanChanged = {},
                onNavigateToConfigurationCenter = {}, onNavigateToLogs = {},
                onNavigateToAppearance = { fallbackNavigationCount++ },
                onNavigateToGlassEffects = { fallbackNavigationCount++ },
                onNavigateToDns = { fallbackNavigationCount++ },
                appearanceDetailContent = {
                    AppearanceScreen(
                        themeMode = theme.value,
                        onThemeModeChanged = { theme.value = it },
                        onLanguageChanged = {},
                        nodeColumns = 2,
                        onNodeColumnsChanged = {},
                        showGroupIcons = true,
                        onShowGroupIconsChanged = {},
                        onBack = {},
                        embeddedPanel = true
                    )
                },
                glassEffectsDetailContent = {
                    GlassEffectsScreen(
                        settings = glass.value,
                        onSettingsChanged = { glass.value = it },
                        onBack = {},
                        scrollState = glassScrollState,
                        embeddedPanel = true
                    )
                },
                dnsDetailContent = {
                    DnsScreen(
                        fakeIpEnabled = fakeIp.value,
                        onFakeIpEnabledChanged = { fakeIp.value = it },
                        onBack = {},
                        embeddedPanel = true,
                        listState = dnsListState
                    )
                }
            )
        }

        compose.onNodeWithTag("settings-appearance-entry").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-detail-page").assertExists()
        assertSettingsMorphTopBlur()
        assertScrollEdgeViewport()
        compose.onNodeWithTag("appearance-scroll-content").assertExists()
        compose.onNodeWithTag("appearance-pinned-header").assertDoesNotExist()
        compose.onNodeWithTag("appearance-theme-light").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ThemeMode.LIGHT, theme.value) }
        compose.onNodeWithTag("settings-detail-back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-detail-page").assertDoesNotExist()

        compose.onNodeWithTag("settings-glass-effects-entry").performScrollTo().performClick()
        compose.waitForIdle()
        assertSettingsMorphTopBlur()
        assertScrollEdgeViewport()
        compose.onNodeWithTag("glass-effects-scroll").assertExists()
        compose.onNodeWithTag("glass-effects-pinned-header").assertDoesNotExist()
        compose.runOnIdle {
            glassScrollState.dispatchRawDelta((glassScrollState.maxValue - glassScrollState.value).toFloat())
            assertEquals("the scroll reaches its actual content end", glassScrollState.maxValue, glassScrollState.value)
        }
        compose.waitForIdle()
        val glassPanelBottom = compose.onNodeWithTag("settings-detail-page").fetchSemanticsNode().boundsInRoot.bottom
        val wallpaperCardBottom = compose.onNodeWithTag("glass-wallpaper-group").fetchSemanticsNode().boundsInRoot.bottom
        val resetButtonBottom = compose.onNodeWithTag("glass-effects-reset-all").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("the final glass settings card must be fully scrollable above the morph panel edge", wallpaperCardBottom <= glassPanelBottom - 32f)
        assertTrue("the reset action must retain a clear bottom tail", resetButtonBottom <= glassPanelBottom - 32f)
        compose.onNodeWithTag("glass-effects-reset-all").performClick()
        compose.runOnIdle { assertEquals(GlassSettings.DEFAULT, glass.value) }
        compose.onNodeWithTag("settings-detail-back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-detail-page").assertDoesNotExist()

        compose.onNodeWithTag("settings-dns-entry").performScrollTo().performClick()
        compose.waitForIdle()
        assertSettingsMorphTopBlur()
        compose.onNodeWithTag("dns-page-list").assertExists()
        compose.onNodeWithTag("dns-page-header").assertDoesNotExist()
        compose.onNodeWithTag("dns-fakeip-switch").assertIsOn().performClick().assertIsOff()
        val dnsPanelBottom = compose.onNodeWithTag("settings-detail-page").fetchSemanticsNode().boundsInRoot.bottom
        val hint = compose.onNodeWithTag("dns-panel-bottom-hint").assertIsDisplayed()
        val hintBounds = hint.fetchSemanticsNode().boundsInRoot
        val hintBottom = hintBounds.bottom
        assertTrue("DNS explanation remains fully inside the morph above the Dock", dnsPanelBottom - hintBottom >= 8f)
        val dnsList = compose.onNodeWithTag("dns-page-list")
        assertTrue("the DNS list viewport must end before the pinned hint", dnsList.fetchSemanticsNode().boundsInRoot.bottom <= hintBounds.top + 1f)
        compose.runOnIdle { dnsListState.dispatchRawDelta(100_000f) }
        compose.waitForIdle()
        assertFalse("the DNS list reached its actual last scroll position", dnsListState.canScrollForward)
        val lastDnsRow = compose.onNodeWithTag("dns-upstream-row-5").fetchSemanticsNode().boundsInRoot
        val currentHintTop = compose.onNodeWithTag("dns-panel-bottom-hint").fetchSemanticsNode().boundsInRoot.top
        assertTrue("the last configured DNS row must be wholly visible above the fixed hint with a clear gap", lastDnsRow.bottom <= currentHintTop - 16f)
        val logsHeaderBottom = compose.onNodeWithTag("dns-logs-header").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("the DNS log display area remains reachable above its reserved hint", logsHeaderBottom <= currentHintTop - 16f)
        dnsList.performScrollToNode(hasTestTag("dns-panel-bypass"))
        compose.onNodeWithTag("dns-panel-bypass").assertIsDisplayed()
        compose.onNodeWithTag("dns-panel-bottom-hint").assertIsDisplayed()
        assertEquals(
            "the bottom explanation stays pinned while DNS cards scroll",
            hintBottom,
            compose.onNodeWithTag("dns-panel-bottom-hint").fetchSemanticsNode().boundsInRoot.bottom,
            1f
        )
        compose.runOnIdle {
            assertEquals(false, fakeIp.value)
            assertEquals("morph routes must not invoke the old full-page navigation callbacks", 0, fallbackNavigationCount)
        }
        compose.onNodeWithTag("settings-detail-back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-detail-page").assertDoesNotExist()
    }

    @Test
    fun profilesBackTouchInvokesOnBackCallback() {
        var backCalls = 0
        setGlassContent {
            ProfilesScreen(
                profiles = emptyList(),
                selectedProfileId = null,
                onBack = { backCalls++ },
                onSelectProfile = {},
                onUpdateProfile = {},
                onDeleteProfile = {},
                embeddedPanel = true
            )
        }
        compose.onNodeWithTag("navigation-back-button").assertIsDisplayed()
        compose.onNodeWithTag("navigation-back-button").performTouchInput { click() }
        compose.runOnIdle { assertEquals("the visible back hit area invokes onBack", 1, backCalls) }
    }

    @Test
    fun connectionsLogsAndConfigurationCenterOpenAsGlasmioMorphPanelsAndReturnToSettings() {
        var legacyNavigations = 0
        var rootBackdrop: Any? = null
        var configurationBackdrop: Any? = null
        val nestedDestination = mutableStateOf<ConfigurationCenterDestination?>(null)
        val selectedProfileId = mutableStateOf<String?>(null)
        val selectedPackages = mutableStateOf<Set<String>>(emptySet())
        val profiles = List(24) { index ->
            ProfileItem("profile-$index", "测试配置 $index", null, File("profile-$index.yaml"))
        }
        val rules = List(48) { index -> ParsedRule("DOMAIN", "rule-$index.example", "PROXY") }
        val apps = List(36) { index -> InstalledApp("test.app.$index", "测试应用 $index", false) }

        setGlasmioContent {
            val backdropInRoot = LocalArcaGlasmioBackdrop.current
            SideEffect { rootBackdrop = backdropInRoot }
            SettingsScreen(
                scrollState = ScrollState(0), tunStack = TunStack.DEFAULT,
                bootOnStartup = false, allowLan = false, fakeIpEnabled = true,
                onTunStackChanged = {}, onBootOnStartupChanged = {}, onAllowLanChanged = {},
                onNavigateToConfigurationCenter = { legacyNavigations++ },
                onNavigateToLogs = { legacyNavigations++ },
                onNavigateToConnections = { legacyNavigations++ },
                connectionsDetailContent = {
                    ConnectionsScreen(
                        vpnState = com.github.arca.android.service.VpnState(
                            status = com.github.arca.android.service.VpnState.Status.STOPPED
                        ),
                        onBack = {},
                        embeddedPanel = true
                    )
                },
                logsDetailContent = { LogsScreen(onBack = {}, embeddedPanel = true) },
                configurationCenterDetailContent = { closePanel ->
                    val backdropInConfiguration = LocalArcaGlasmioBackdrop.current
                    SideEffect { configurationBackdrop = backdropInConfiguration }
                    when (nestedDestination.value) {
                        null -> ConfigurationCenterScreen(
                            onNavigateToProfiles = { nestedDestination.value = ConfigurationCenterDestination.PROFILES },
                            onNavigateToScripts = { nestedDestination.value = ConfigurationCenterDestination.SCRIPTS },
                            onNavigateToRules = { nestedDestination.value = ConfigurationCenterDestination.RULES },
                            onNavigateToAppRouting = { nestedDestination.value = ConfigurationCenterDestination.APP_ROUTING },
                            onBack = closePanel,
                            embeddedPanel = true
                        )
                        ConfigurationCenterDestination.PROFILES -> ProfilesScreen(
                            profiles = profiles,
                            selectedProfileId = selectedProfileId.value,
                            onBack = { nestedDestination.value = null },
                            onSelectProfile = { selectedProfileId.value = it.id },
                            onUpdateProfile = {},
                            onDeleteProfile = {},
                            embeddedPanel = true
                        )
                        ConfigurationCenterDestination.SCRIPTS -> ScriptsScreen(
                            onBack = { nestedDestination.value = null },
                            embeddedPanel = true
                        )
                        ConfigurationCenterDestination.RULES -> RulesScreen(
                            rules = rules,
                            totalRules = rules.size,
                            tunnelMode = TunnelMode.RULE,
                            onBack = { nestedDestination.value = null },
                            embeddedPanel = true
                        )
                        ConfigurationCenterDestination.APP_ROUTING -> AppRoutingScreen(
                            currentMode = PerAppProxyMode.WHITELIST,
                            selectedPackages = selectedPackages.value,
                            apps = apps,
                            isLoading = false,
                            onBack = { nestedDestination.value = null },
                            onModeChanged = {},
                            onTogglePackage = { packageName ->
                                selectedPackages.value = if (packageName in selectedPackages.value) {
                                    selectedPackages.value - packageName
                                } else {
                                    selectedPackages.value + packageName
                                }
                            },
                            embeddedPanel = true
                        )
                    }
                }
            )
        }

        val destinations = listOf(
            Triple("settings-connections-entry", "connections-root", "connections-embedded-toolbar"),
            Triple("settings-logs-entry", "logs-console-area", "logs-embedded-toolbar"),
            Triple("settings-configuration-center-entry", "configuration-center-scroll", "configuration-center-section-header-2")
        )
        destinations.forEach { (entryTag, contentTag, requiredTag) ->
            compose.onNodeWithTag(entryTag).performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("settings-detail-page").assertExists()
            compose.onNodeWithTag("settings-detail-content-body").assertExists()
            compose.onNodeWithTag(contentTag).assertExists()
            compose.onNodeWithTag(requiredTag).assertExists()
            compose.onNodeWithTag("settings-detail-page-glasmio-main-material", useUnmergedTree = true).assertExists()
            assertSettingsMorphTopBlur()
            if (entryTag == "settings-logs-entry") {
                compose.onNodeWithTag("logs-toggle-search").performClick()
                compose.waitForIdle()
                compose.onNodeWithText("搜索日志关键字...").assertExists()
            }
            compose.onNodeWithTag("settings-detail-back").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("settings-detail-page").assertDoesNotExist()
            compose.onNodeWithTag(entryTag).assertExists()
        }

        compose.onNodeWithTag("settings-configuration-center-entry").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertNotNull("a real Glasmio backdrop is available", rootBackdrop)
            assertSame("the configuration morph reuses the root Glasmio backdrop", rootBackdrop, configurationBackdrop)
            assertEquals("morph entries do not invoke legacy full-screen routes", 0, legacyNavigations)
        }

        val nestedEntries = listOf(
            "configuration-center-profiles-entry",
            "configuration-center-scripts-entry",
            "configuration-center-rules-entry",
            "configuration-center-app-routing-entry"
        )
        nestedEntries.forEach { entryTag ->
            compose.onNodeWithTag(entryTag).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("settings-detail-page").assertExists()
            compose.onNodeWithTag("settings-detail-page-glasmio-main-material", useUnmergedTree = true).assertExists()
            assertSettingsMorphTopBlur()
            when (entryTag) {
                "configuration-center-profiles-entry" -> {
                    compose.onNodeWithTag("profiles-screen-root").assertExists()
                    compose.onNodeWithTag("profiles-list").performScrollToNode(hasText("测试配置 23"))
                    compose.onNodeWithText("测试配置 23").assertIsDisplayed()
                }
                "configuration-center-scripts-entry" -> compose.onNodeWithTag("scripts-screen-root").assertExists()
                "configuration-center-rules-entry" -> {
                    compose.onNodeWithTag("rules-list").performScrollToNode(hasText("rule-47.example"))
                    compose.onNodeWithText("rule-47.example").assertIsDisplayed()
                }
                "configuration-center-app-routing-entry" -> {
                    compose.onNodeWithTag("app-routing-list").performScrollToNode(hasText("测试应用 35"))
                    compose.onNodeWithText("测试应用 35").assertIsDisplayed().performClick()
                    compose.runOnIdle { assertTrue("app selection callback still persists its package", "test.app.35" in selectedPackages.value) }
                }
            }
            val backBounds = compose.onNodeWithTag("navigation-back-button").fetchSemanticsNode().boundsInRoot
            val panelBounds = compose.onNodeWithTag("settings-detail-page").fetchSemanticsNode().boundsInRoot
            if (entryTag == "configuration-center-profiles-entry") {
                val viewportBounds = compose.onNodeWithTag("profiles-list-viewport", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("Profiles list viewport must start below its toolbar hit area: back=$backBounds viewport=$viewportBounds", viewportBounds.top >= backBounds.bottom - 1f)
            }
            assertTrue("nested back hit area is inside the active morph panel: back=$backBounds panel=$panelBounds", panelBounds.contains(backBounds.center))
            compose.onRoot().performTouchInput { click(Offset(backBounds.center.x, backBounds.center.y)) }
            compose.waitForIdle()
            compose.onNodeWithTag("settings-detail-page").assertExists()
            compose.runOnIdle { assertEquals("child back restores the configuration hub state", null, nestedDestination.value) }
            compose.onNodeWithTag("configuration-center-profiles-entry").assertExists()
        }
        compose.onNodeWithTag("settings-detail-back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-detail-page").assertDoesNotExist()
        compose.onNodeWithTag("settings-configuration-center-entry").assertExists()
    }

    @Test
    fun glasmioSwitchRetainsAccessibleToggleStateAndCallback() {
        val checked = mutableStateOf(false)
        setGlassContent {
            ArcaGlasmioSwitch(checked.value, { checked.value = it }, Modifier.testTag("glasmio-switch"))
        }
        compose.onNodeWithTag("glasmio-switch").assertIsOff().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("glasmio-switch").assertIsOn()
    }

    private fun setGlasmioContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        GlassDefaults.enabled = true
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "zh",
                    LocalGlassSettings provides GlassSettings(glassPreset = GlassPreset.UNIFIED)
                ) {
                    ProvideGlassTheme {
                        val rootBackdrop = rememberArcaGlasmioBackdrop()
                        NexusGlassBackground(
                            sharedGlasmioBackdrop = rootBackdrop,
                            recordGlasmioBackdropSource = true
                        ) { content() }
                    }
                }
            }
        }
    }

    private fun setGlassContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        GlassDefaults.enabled = true
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalAppLanguage provides "zh",
                    LocalGlassSettings provides GlassSettings(glassPreset = GlassPreset.UNIFIED)
                ) {
                    ProvideGlassTheme {
                        NexusGlassBackground { content() }
                    }
                }
            }
        }
    }

    private fun advanceFrames(count: Int) {
        repeat(count) {
            compose.runOnUiThread { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun assertVisiblePanelTail(lastOptionTag: String) {
        compose.onNodeWithTag("settings-detail-page").assertIsDisplayed()
        val panelBottom = compose.onNodeWithTag("settings-detail-page").fetchSemanticsNode().boundsInRoot.bottom
        val lastOptionBottom = compose.onNodeWithTag(lastOptionTag).fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(
            "last option should have a clear >=24dp gap from the morph panel bottom, tag=$lastOptionTag, gap=${panelBottom - lastOptionBottom}",
            panelBottom - lastOptionBottom >= 24f
        )
    }

    /**
     * The fixed 64dp rectangular blur strip drew a hard-edged dark block over the title zone and
     * was removed; scrolling bodies now use the Glasmio-style scroll-edge viewport instead.
     */
    private fun assertSettingsMorphTopBlur() {
        compose.onNodeWithTag(
            "settings-detail-page-glasmio-top-backdrop-blur",
            useUnmergedTree = true
        ).assertDoesNotExist()
    }

    private fun assertScrollEdgeViewport() {
        compose.onNodeWithTag("settings-detail-edge-viewport", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("settings-detail-edge-header", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("settings-detail-edge-body", useUnmergedTree = true).assertExists()
    }
}
