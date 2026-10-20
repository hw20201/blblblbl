package com.github.mihomo.android.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.mihomo.android.data.OutboundIpInfo
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [35])
class OutboundIpSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lookupRunsOnEntryAndExplicitRefreshOnly() {
        val calls = AtomicInteger()
        compose.setContent {
            com.github.mihomo.android.ui.theme.MihomoTheme() {
                OutboundIpSheet(onRefresh = {
                    calls.incrementAndGet()
                    OutboundIpInfo("203.0.113.9", source = "fixture")
                })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("IP 地址").assertExists()
        compose.onNodeWithText("203.0.113.9").assertExists()
        compose.onNodeWithText("等级").assertExists()
        compose.onAllNodesWithText("未知").assertCountEquals(5)
        compose.runOnIdle { org.junit.Assert.assertEquals(1, calls.get()) }
        compose.waitForIdle()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, calls.get()) }
        compose.onNodeWithText("隐藏 IP").assertExists()
        compose.onNode(isToggleable()).performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("203.0.113.9").assertDoesNotExist()
        compose.onNodeWithText("••••••••").assertExists()
        compose.waitForIdle()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, calls.get()) }
        compose.onNodeWithText("重新检测").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle { org.junit.Assert.assertEquals(2, calls.get()) }
    }

    @Test fun failedRetryDoesNotDisplayPreviousExitIp() {
        val calls = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                OutboundIpSheet(onRefresh = {
                    check(calls.incrementAndGet() <= 1) { "offline" }
                    OutboundIpInfo("203.0.113.12", source = "fixture")
                })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("203.0.113.12").assertExists()
        compose.onNodeWithText("重新检测").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("检测失败：offline").assertExists()
        compose.onNodeWithText("203.0.113.12").assertDoesNotExist()
        compose.onAllNodesWithText("未知").assertCountEquals(7)
        compose.runOnIdle { org.junit.Assert.assertEquals(2, calls.get()) }
    }

    @Test fun refreshFailureIsShown() {
        compose.setContent {
            MaterialTheme {
                OutboundIpSheet(onRefresh = { throw IllegalStateException("offline") })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("检测失败：offline").assertExists()
    }
}
