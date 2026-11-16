package com.github.arca.android.ui.screens

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.github.arca.android.data.OutboundIpInfo
import com.github.arca.android.ui.theme.MihomoTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [35])
class OutboundIpSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun maintenanceModeDoesNotCallAnyIpProvider() {
        val calls = AtomicInteger()
        compose.setContent {
            MihomoTheme {
                OutboundIpSheet(onRefresh = {
                    calls.incrementAndGet()
                    OutboundIpInfo("203.0.113.9", source = "fixture")
                })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("出站 IP · 维护中").assertExists()
        compose.onNodeWithTag("outbound-ip-maintenance-action").assertIsNotEnabled()
        compose.onNodeWithText("重新检测").assertDoesNotExist()
        compose.onNodeWithText("203.0.113.9").assertDoesNotExist()
        compose.onNodeWithText("Hosting / 代理").assertExists()
        compose.onNodeWithText("黑名单标记").assertExists()
        compose.onNodeWithText("代理类型").assertExists()
        compose.onNodeWithText("ident.me、Scamalytics 或其他 IP 查询服务", substring = true).assertExists()
        compose.runOnIdle { assertEquals(0, calls.get()) }
    }
}
