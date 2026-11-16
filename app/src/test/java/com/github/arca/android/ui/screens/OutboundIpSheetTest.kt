package com.github.arca.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.arca.android.data.OutboundIpInfo
import com.github.arca.android.data.ScamalyticsCredentials
import com.github.arca.android.data.ScamalyticsCredentialsStorage
import com.github.arca.android.ui.theme.MihomoTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class OutboundIpSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun openingSheetMakesNoRequestAndManualRefreshShowsIp() {
        val calls = AtomicInteger()
        compose.setContent {
            MihomoTheme {
                OutboundIpSheet(onRefresh = {
                    calls.incrementAndGet()
                    OutboundIpInfo("203.0.113.9", source = "fixture")
                })
            }
        }
        awaitReady()
        compose.onNodeWithText("出站 IP").assertExists()
        compose.onNodeWithText("IP 测试 · 就绪").assertExists()
        compose.onNodeWithText("203.0.113.9").assertDoesNotExist()
        compose.onNodeWithText("Hosting / 代理").assertExists()
        compose.onNodeWithText("黑名单标记").assertExists()
        compose.onNodeWithText("代理类型").assertExists()
        compose.runOnIdle { assertEquals(0, calls.get()) }
        compose.onNodeWithTag("quick-ip-refresh").performScrollTo().performClick()
        compose.waitUntil { calls.get() == 1 }
        compose.waitForIdle()
        compose.onNodeWithText("203.0.113.9").assertExists()
        compose.onNodeWithText("重新检测 IP").assertExists()
    }

    @Test fun closeCancelsManualLookupImmediately() {
        val started = java.util.concurrent.atomic.AtomicBoolean()
        val canceled = java.util.concurrent.atomic.AtomicBoolean()
        compose.setContent {
            MihomoTheme {
                OutboundIpSheet(onRefresh = {
                    started.set(true)
                    try {
                        kotlinx.coroutines.awaitCancellation()
                    } finally { canceled.set(true) }
                })
            }
        }
        awaitReady()
        compose.onNodeWithTag("quick-ip-refresh").performScrollTo().performClick()
        compose.waitUntil { started.get() }
        compose.onNodeWithTag("quick-ip-refresh").assertIsNotEnabled()
        compose.onNodeWithText("关闭").performScrollTo().performClick()
        compose.waitUntil { canceled.get() }
    }

    @Test fun savingAndDeletingCredentialsAreIoOnlyAndNeverLookup() {
        val calls = AtomicInteger()
        val saves = AtomicInteger()
        val deletes = AtomicInteger()
        val ioOperations = mutableListOf<Boolean>()
        val storage = object : ScamalyticsCredentialsStorage {
            override fun load(): ScamalyticsCredentials? {
                ioOperations += android.os.Looper.myLooper() != android.os.Looper.getMainLooper()
                return ScamalyticsCredentials("saved-fixture", "saved-fake-key")
            }
            override fun save(credentials: ScamalyticsCredentials) {
                ioOperations += android.os.Looper.myLooper() != android.os.Looper.getMainLooper()
                saves.incrementAndGet()
                assertEquals("replacement-fake-key", credentials.apiKey)
            }
            override fun clear() {
                ioOperations += android.os.Looper.myLooper() != android.os.Looper.getMainLooper()
                deletes.incrementAndGet()
            }
        }
        compose.setContent {
            MihomoTheme {
                // The test hosts the panel the way the sheet does: the low-key config
                // entry is rendered by the host, fixed outside the scrolling column.
                val settingsOpen = remember { mutableStateOf(false) }
                Box {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        OutboundIpPanel(isVpnRunning = false, credentialStorage = storage,
                            settingsOpenState = settingsOpen,
                            onRefresh = { calls.incrementAndGet(); OutboundIpInfo("203.0.113.9", source = "fixture") })
                    }
                    ScamalyticsConfigLink(
                        onClick = { settingsOpen.value = true },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
        awaitReady()
        compose.onNodeWithTag("scamalytics-config-link").performClick()
        compose.onNodeWithTag("scamalytics-api-key").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithText("saved-fake-key").assertDoesNotExist()
        compose.onNodeWithTag("scamalytics-api-key").performTextInput("replacement-fake-key")
        compose.onNodeWithTag("scamalytics-save-credentials").performScrollTo().performClick()
        compose.waitUntil { saves.get() == 1 }
        compose.waitUntil { compose.onAllNodesWithTag("scamalytics-settings-sheet").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("scamalytics-config-link").performClick()
        compose.onNodeWithTag("scamalytics-api-key").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithTag("scamalytics-delete-credentials").performScrollTo().performClick()
        compose.waitUntil { deletes.get() == 1 }
        compose.waitUntil { compose.onAllNodesWithTag("scamalytics-settings-sheet").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle {
            assertEquals(0, calls.get())
            assertEquals(3, ioOperations.size)
            assertTrue(ioOperations.all { it })
        }
    }

    private fun awaitReady() {
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithText("正在读取本机配置…").fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("quick-ip-refresh").assertIsEnabled()
    }
}
