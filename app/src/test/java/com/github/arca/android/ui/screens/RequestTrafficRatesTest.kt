package com.github.arca.android.ui.screens

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.service.TrafficMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RequestTrafficRatesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun businessLeafShowsLiveFlowUpdatesWithoutVpnStateChanges() {
        val metrics = MutableStateFlow(TrafficMetrics("5 KB/s", "12 KB/s"))
        compose.setContent { RequestTrafficRates(isRunning = true, trafficMetrics = metrics) }
        compose.onNodeWithText("5 KB/s  /  12 KB/s").assertTextEquals("5 KB/s  /  12 KB/s")
        compose.runOnIdle { metrics.value = TrafficMetrics("8 KB/s", "25 KB/s") }
        compose.onNodeWithText("8 KB/s  /  25 KB/s").assertTextEquals("8 KB/s  /  25 KB/s")
    }

    @Test fun stoppedTunnelDoesNotDisplayPreviousSpeedSample() {
        val metrics = MutableStateFlow(TrafficMetrics("5 KB/s", "12 KB/s"))
        compose.setContent { RequestTrafficRates(isRunning = false, trafficMetrics = metrics) }
        compose.onNodeWithText("0 B/s  /  0 B/s").assertTextEquals("0 B/s  /  0 B/s")
    }
}
