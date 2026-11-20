package com.github.arca.android.ui.screens

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.mutableStateMapOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.ui.glass.*
import com.github.arca.android.ui.theme.LocalAppLanguage
import com.github.arca.android.ui.theme.MihomoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProxiesGlassRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun resetGlass() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.CREAM
        GlassDefaults.quietPages = true
    }

    @Test fun draftPolicyTitleStaysPinnedWhileNodesScrollBehindIt() {
        GlassDefaults.enabled = true
        val proxies = (0..40).map { Proxy("Node $it", delay = 40 + it) }
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh") {
                    ProvideGlassTheme {
                        val backdrop = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().recordNavigationScene(backdrop)) {
                                NexusGlassBackground {}
                            }
                            val group = ProxyGroup("select", proxies, "Node 0")
                            ProxiesScreen(groups = listOf("Test"), groupMap = mapOf("Test" to group),
                                currentGroup = group, selectedGroupName = "Test", currentViewMode = "TABS",
                                onViewModeChanged = {}, onShowGroupIconsChanged = {}, onSelectGroup = {},
                                onSelectProxy = { _, _ -> }, onHealthCheck = {}, glassScene = backdrop)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val before = compose.onNodeWithText("策略").fetchSemanticsNode().boundsInRoot
        saveScreenshot("draft-policy-before-scroll")
        compose.onNodeWithTag("policy-node-list").performScrollToIndex(15)
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithText("策略").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Node 15").assertIsDisplayed()
        saveScreenshot("draft-policy-after-scroll")
    }

    @Test fun strategyCardsRenderAndRemainSelectableInLightDarkAndEveryColumnCount() {
        GlassDefaults.enabled = true
        GlassDefaults.palette = GlassPalettes.BLUE
        GlassDefaults.quietPages = false // glasmio material tags only exist on the glass branch, not quiet cards
        val dark = mutableStateOf(false)
        val columns = mutableIntStateOf(2)
        val mode = mutableStateOf("TABS")
        val selected = mutableStateOf("Tokyo")
        val time = mutableFloatStateOf(8f)
        val proxies = listOf(
            Proxy("Tokyo", type = "Shadowsocks", delay = 45),
            Proxy("Osaka", type = "VMess", delay = 120),
            Proxy("Seoul", type = "Trojan", delay = -2),
            Proxy("YouTube", type = "GROUP", delay = 0),
            Proxy("Google", type = "GROUP", delay = 0),
            Proxy("AI", type = "GROUP", delay = 0),
            Proxy("GitHub", type = "GROUP", delay = 0),
            Proxy("Microsoft", type = "GROUP", delay = 0)
        )
        compose.setContent {
            MihomoTheme(if (dark.value) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalAppLanguage provides "zh", LocalSoftMeshTime provides time,
                    LocalSoftMeshSeed provides 734921L) {
                    ProvideGlassTheme {
                        val backdrop = rememberNavigationLens()
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().recordNavigationScene(backdrop)) {
                                NexusGlassBackground {}
                            }
                            val group = ProxyGroup("select", proxies, now = selected.value)
                            ProxiesScreen(
                                groups = listOf("Proxy", "Auto"),
                                groupMap = mapOf("Proxy" to group, "Auto" to group),
                                currentGroup = group, selectedGroupName = "Proxy",
                                currentViewMode = mode.value, nodeColumns = columns.intValue,
                                onViewModeChanged = { mode.value = it }, onShowGroupIconsChanged = {},
                                onNodeColumnsChanged = { columns.intValue = it }, onSelectGroup = {},
                                onSelectProxy = { _, proxy -> selected.value = proxy.name }, onHealthCheck = {},
                                glassScene = backdrop
                            )
                        }
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark }
            for (count in listOf(1, 2, 3)) {
                compose.runOnIdle { columns.intValue = count; selected.value = "Tokyo" }
                compose.waitForIdle()
                compose.onNodeWithText("Osaka").assertIsDisplayed().performClick()
                compose.waitForIdle()
                assertEquals("Osaka", selected.value)
                compose.onNodeWithTag("proxy-selected-glass-material", useUnmergedTree = true).assertExists()
                assertTrue(compose.onAllNodesWithTag("proxy-glasmio-main-material", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty())
                saveScreenshot("proxies-${if (isDark) "dark" else "light"}-$count-columns")
            }
        }
        compose.runOnIdle { mode.value = "LIST"; dark.value = false }
        compose.waitForIdle()
        saveScreenshot("proxies-light-group-headers")
        compose.runOnIdle { GlassDefaults.enabled = false }
        compose.waitForIdle()
        saveScreenshot("proxies-classic-fallback")
    }

    @Test fun headerAndGroupPillsLeaveSpaceAndKeepRawSelectionKeys() {
        GlassDefaults.enabled = true
        GlassDefaults.quietPages = true
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        val rawName = "Auto ($uuid)"
        val group = ProxyGroup("select", listOf(Proxy("Local")), "Local")
        val mode = mutableStateOf("TABS")
        var selectedGroup = ""
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    ProxiesScreen(
                        groups = listOf(rawName, "Manual"), groupMap = mapOf(rawName to group, "Manual" to group),
                        currentGroup = group, selectedGroupName = rawName, currentViewMode = mode.value,
                        onViewModeChanged = {}, onShowGroupIconsChanged = {},
                        onSelectGroup = { selectedGroup = it }, onSelectProxy = { _, _ -> }, onHealthCheck = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Auto").assertIsDisplayed().performClick()
        assertEquals(rawName, selectedGroup)
        compose.onNodeWithText(rawName).assertDoesNotExist()
        val firstPill = compose.onNodeWithTag("policy-group-tab-$rawName").fetchSemanticsNode().boundsInRoot
        val secondPill = compose.onNodeWithTag("policy-group-tab-Manual").fetchSemanticsNode().boundsInRoot
        assertTrue("Pills have visible horizontal separation", secondPill.left - firstPill.right >= 12f)
        assertTrue("First pill has edge padding", firstPill.left >= 20f)
        for (viewMode in listOf("TABS", "LIST")) {
            compose.runOnIdle { mode.value = viewMode }
            val header = compose.onNodeWithTag("policy-header").fetchSemanticsNode().boundsInRoot
            val cardTag = if (viewMode == "TABS") "proxy-card-Local" else "policy-group-header-$rawName"
            val firstCard = compose.onNodeWithTag(cardTag).fetchSemanticsNode().boundsInRoot
            assertTrue("First card stays below pinned header in $viewMode", firstCard.top >= header.bottom + 7f)
        }
    }

    @Test fun anchoredMenusAddAndRenameOnlyAllowlistedLocalNodesInEveryLayout() {
        GlassDefaults.enabled = true
        GlassDefaults.quietPages = true
        val columns = mutableIntStateOf(1)
        val mode = mutableStateOf("TABS")
        val allowlist = mutableStateOf<Set<String>?>(setOf("Local", "Nested"))
        val nodes = listOf(Proxy("Local"), Proxy("Provider"), Proxy("Nested", type = "GROUP", isGroup = true))
        val group = ProxyGroup("select", nodes, "Local")
        val expanded = mutableStateMapOf("Test" to true)
        var added = 0
        var selected = 0
        var edited: String? = null
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    ProxiesScreen(
                        groups = listOf("Test"), groupMap = mapOf("Test" to group), currentGroup = group,
                        selectedGroupName = "Test", currentViewMode = mode.value, nodeColumns = columns.intValue,
                        expandedGroups = expanded, onViewModeChanged = {}, onShowGroupIconsChanged = {},
                        onSelectGroup = {}, onSelectProxy = { _, _ -> selected++ }, onHealthCheck = {},
                        onAddNode = { added++ }, onEditNode = { edited = it }, editableNodeNames = allowlist.value
                    )
                }
            }
        }
        compose.onNodeWithTag("policy-more-button").performClick()
        compose.onNodeWithTag("policy-more-menu").assertIsDisplayed()
        compose.onNodeWithTag("policy-add-node").performClick()
        assertEquals(1, added)
        for (viewMode in listOf("TABS", "LIST")) {
            for (count in 1..3) {
                compose.runOnIdle { mode.value = viewMode; columns.intValue = count; edited = null }
                compose.onNodeWithTag("proxy-card-Local").performTouchInput { longClick() }
                compose.onNodeWithTag("proxy-rename-menu-Local").assertIsDisplayed()
                compose.onNodeWithTag("proxy-rename-action-Local").performClick()
                assertEquals("Local", edited)
                assertEquals("Long press must not select the node", 0, selected)
                compose.onNodeWithTag("proxy-card-Provider")
                    .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
                compose.onNodeWithTag("proxy-card-Nested")
                    .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
            }
        }
        compose.onNodeWithTag("proxy-card-Local").performTouchInput { longClick() }
        compose.runOnIdle { allowlist.value = null }
        compose.onNodeWithTag("proxy-rename-menu-Local").assertDoesNotExist()
        compose.onNodeWithTag("proxy-card-Local")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        compose.onNodeWithTag("proxy-card-Local").performClick()
        assertEquals(1, selected)
    }

    private fun saveScreenshot(name: String) {
        val window = compose.activity.window
        val view = window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        var result = -1
        compose.runOnIdle { PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper())) }
        compose.waitForIdle()
        assertEquals("Robolectric native PixelCopy succeeds (not a real device)", PixelCopy.SUCCESS, result)
        val out = File("build/reports/proxies-glass/$name.png")
        out.parentFile!!.mkdirs()
        out.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
