package com.github.arca.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class MainDockContentInsetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sharedPageViewportContinuesBehindFloatingDock() {
        val navigationInset = 24.dp
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                MainDockContentViewport {
                    Box(Modifier.fillMaxSize().testTag("main-page-content"))
                }
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = navigationInset + BOTTOM_BAR_BOTTOM_MARGIN)
                ) {
                    Box(
                        Modifier.width(300.dp).height(BOTTOM_BAR_HEIGHT)
                            .testTag("floating-dock")
                    )
                }
            }
        }
        val pageBottom = compose.onNodeWithTag("main-page-content-viewport")
            .fetchSemanticsNode().boundsInRoot.bottom
        val dockBottom = compose.onNodeWithTag("floating-dock")
            .fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("page scene must extend beneath the floating dock", pageBottom > dockBottom - 0.5f)
    }
}
