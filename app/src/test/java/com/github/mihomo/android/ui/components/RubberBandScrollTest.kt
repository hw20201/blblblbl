package com.github.mihomo.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], qualifiers="w400dp-h900dp-mdpi")
@OptIn(ExperimentalFoundationApi::class)
class RubberBandScrollTest {
    @get:Rule val compose = createComposeRule()
    @Test fun dragIsImmediateResistantAndBoundedInBothDirections() {
        val band = RubberBandState(88f)
        band.pull(20f)
        assertEquals(8.4f, band.offset, .01f)
        repeat(500) { band.pull(100f) }
        assertEquals(88f, band.offset, .01f)
        assertEquals(-88f, band.unwind(-200f), .01f)
        assertEquals(0f, band.offset, .01f)
        repeat(500) { band.pull(-100f) }
        assertEquals(-88f, band.offset, .01f)
        assertEquals(88f, band.unwind(200f), .01f)
        assertEquals(0f, band.offset, .01f)
    }
    @Test fun topAndBottomPullTranslateOnlyContentAndReleaseToZero() {
        lateinit var scroll: androidx.compose.foundation.ScrollState
        lateinit var scope: kotlinx.coroutines.CoroutineScope
        compose.setContent {
            scroll = rememberScrollState(); scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            Box(Modifier.size(300.dp,400.dp)) {
                Box(Modifier.fillMaxSize().testTag("fixed"))
                Column(Modifier.size(300.dp,350.dp).testTag("scroll")
                    .then(Modifier.bounceOverscroll()).verticalScroll(scroll)) {
                    Box(Modifier.size(290.dp,900.dp).testTag("content"))
                }
            }
            }
        }
        compose.mainClock.autoAdvance = false
        val rest = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot.top
        val chrome = compose.onNodeWithTag("fixed").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("scroll").performTouchInput { down(Offset(150f,120f)); moveTo(Offset(150f,270f),128) }
        compose.mainClock.advanceTimeBy(32)
        val pulled = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot.top
        assertTrue("top responds", pulled > rest + 1f)
        assertTrue("top bounded", pulled - rest <= 88.1f)
        assertEquals(chrome,compose.onNodeWithTag("fixed").fetchSemanticsNode().boundsInRoot.top,.1f)
        compose.onNodeWithTag("scroll").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1600)
        assertEquals(rest,compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot.top,.5f)
        compose.runOnUiThread { scope.launch { scroll.scrollTo(scroll.maxValue) } }
        compose.mainClock.advanceTimeBy(32)
        val bottomRest=compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("scroll").performTouchInput { down(Offset(150f,270f)); moveTo(Offset(150f,110f),128) }
        compose.mainClock.advanceTimeBy(32)
        val bottom=compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot.top
        assertTrue("bottom responds",bottom < bottomRest - 1f)
        assertTrue("bottom bounded",bottomRest-bottom <= 88.1f)
        compose.onNodeWithTag("scroll").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1600)
        assertEquals(bottomRest,compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot.top,.5f)
    }
}
