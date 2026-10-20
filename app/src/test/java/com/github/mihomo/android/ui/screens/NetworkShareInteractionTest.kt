package com.github.mihomo.android.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.mihomo.android.data.LanCredentials
import com.github.mihomo.android.service.VpnState
import com.github.mihomo.android.ui.theme.MihomoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NetworkShareInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun credentialsCanBeCopiedAndRotationUsesTheCallback() {
        val credentials = mutableStateOf(LanCredentials("nexus-test", "initial-password"))
        var rotations = 0
        compose.setContent {
            MihomoTheme {
                NetworkShareScreen(true, credentials.value, {}, {
                    rotations++
                    credentials.value = LanCredentials("nexus-test", "rotated-password")
                }, VpnState(status = VpnState.Status.RUNNING), {})
            }
        }
        compose.onNodeWithText("initial-password").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("复制密码").performClick()
        compose.runOnIdle {
            val context: Context = RuntimeEnvironment.getApplication()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("initial-password", clipboard.primaryClip?.getItemAt(0)?.text.toString())
        }
        compose.onNodeWithText("重新生成").performScrollTo().performClick()
        compose.onNodeWithText("rotated-password").performScrollTo().assertExists()
        compose.onNodeWithText("initial-password").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, rotations) }
    }

    @Test fun sharingToggleRevealsAndHidesTheCredentialCard() {
        val enabled = mutableStateOf(false)
        compose.setContent {
            MihomoTheme {
                NetworkShareScreen(enabled.value, LanCredentials("nexus-test", "test-secret"),
                    { enabled.value = it }, {}, VpnState(), {})
            }
        }
        compose.onNodeWithText("局域网访问凭据").assertDoesNotExist()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("局域网访问凭据").performScrollTo().assertExists()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("局域网访问凭据").assertDoesNotExist()
    }
}
