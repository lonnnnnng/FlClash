package com.lonnnnnng.flclash.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：用真实IME检查搜索层级，用受控Compose时钟检查中途反向；正文位置和单一测速按钮必须始终保持。 */
class ProxySearchMotionInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private var expanded by mutableStateOf(false)
    private var query by mutableStateOf("")
    private var view: android.view.View? = null
    private var primaryClicks = 0
    private var secondaryClicks = 0

    private fun content(primary: Boolean) {
        assertPortraitDefaultFontConfiguration()
        compose.setContent { KClashTheme {
            view = LocalView.current
            BackHandler(expanded) { query = ""; expanded = false }
            Surface(color = dashboardPageColor()) { Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                ProxySearchToolbar(query, expanded, primary, { query = it }, { expanded = it; if (!it) query = "" },
                    primaryAction = { if (primary) IconButton({ primaryClicks++ }, Modifier.size(44.dp).testTag("search_test_primary")) { Text("测") } },
                    secondaryActions = { IconButton({ secondaryClicks++ }, Modifier.size(44.dp).testTag("search_test_secondary")) { Text("更") } })
                Text("固定正文位置", Modifier.testTag("search_test_body"))
                Text(query, Modifier.testTag("search_test_query"))
            } }
        } }
    }

    @Test fun groupedSearchImeClearCancelAndReturnKeepSingleActions() {
        content(true)
        val bodyTop = compose.onNodeWithTag("search_test_body").fetchSemanticsNode().boundsInRoot.top
        capture("search_group_closed")
        compose.onNodeWithTag("proxy_group_search_toggle").performClick()
        compose.onNodeWithTag("proxy_group_search").performTextInput("node-query")
        awaitIme(true)
        compose.onAllNodesWithTag("search_test_primary").assertCountEquals(1)
        compose.onNodeWithTag("search_test_primary").performClick()
        assertEquals(1, primaryClicks)
        compose.onNodeWithTag("proxy_search_clear").performClick()
        assertEquals("", input())
        assertTrue(expanded)
        assertEquals(bodyTop, compose.onNodeWithTag("search_test_body").fetchSemanticsNode().boundsInRoot.top, 1f)
        capture("search_group_open_ime")
        compose.onNodeWithTag("proxy_group_search").performTextInput("second-query")
        compose.onNodeWithTag("proxy_group_search_toggle").performClick()
        awaitIme(false)
        assertEquals("", query)
        compose.onNodeWithTag("proxy_group_search").assertDoesNotExist()
        compose.onNodeWithTag("search_test_secondary").performClick()
        assertEquals(1, secondaryClicks)
        compose.onNodeWithTag("proxy_group_search_toggle").performClick()
        assertEquals("", input())
        awaitIme(true)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        awaitIme(false)
        assertTrue("第一次系统返回只关闭真实键盘", expanded)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !expanded }
        compose.onNodeWithTag("proxy_group_search").assertDoesNotExist()
        capture("search_group_returned")
    }

    @Test fun interruptedOpeningAndClosingPreserveIntermediateGeometry() {
        content(true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("proxy_group_search_toggle").performClick()
        compose.mainClock.advanceTimeBy(130)
        val intermediate = compose.onNodeWithTag("proxy_search_morph_surface").fetchSemanticsNode().boundsInRoot
        assertTrue(intermediate.width > 60f)
        capture("search_group_opening_130ms")
        compose.runOnIdle { expanded = false }
        compose.mainClock.advanceTimeByFrame()
        val reversed = compose.onNodeWithTag("proxy_search_morph_surface").fetchSemanticsNode().boundsInRoot
        assertTrue("反向第一帧不能把字段缩回起点", reversed.width >= intermediate.width * .8f)
        compose.mainClock.advanceTimeBy(80)
        capture("search_group_reversing")
        compose.runOnIdle { expanded = true }
        compose.mainClock.advanceTimeBy(1_200)
        val final = compose.onNodeWithTag("proxy_search_morph_surface").fetchSemanticsNode().boundsInRoot
        assertTrue("展开必须经历中间宽度", final.width > intermediate.width)
        compose.onNodeWithTag("proxy_group_search").performTextReplacement("kept")
        compose.runOnIdle { expanded = false; query = "" }
        compose.mainClock.advanceTimeBy(150)
        capture("search_group_closing_150ms")
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithTag("proxy_group_search").assertDoesNotExist()
        assertEquals(0, primaryClicks); assertEquals(0, secondaryClicks)
        compose.mainClock.autoAdvance = true
    }

    @Test fun loneSearchReturnsThroughDropletAndReopensEmpty() {
        content(false)
        compose.onNodeWithTag("proxy_group_search_toggle").performClick()
        compose.onNodeWithTag("proxy_group_search").performTextReplacement("list")
        awaitIme(true)
        capture("search_lone_open")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("proxy_group_search_toggle").performClick()
        compose.mainClock.advanceTimeBy(180)
        capture("search_lone_close_180ms")
        compose.mainClock.advanceTimeBy(250)
        capture("search_lone_close_430ms")
        compose.mainClock.advanceTimeBy(1_200)
        compose.mainClock.autoAdvance = true
        awaitIme(false)
        compose.onNodeWithTag("proxy_group_search_toggle").performClick()
        assertEquals("", input())
        compose.onAllNodesWithTag("search_test_primary").assertCountEquals(0)
        compose.onNodeWithTag("proxy_group_search_toggle").performClick()
        awaitIme(false)
        compose.onNodeWithTag("search_test_secondary").assertIsDisplayed()
    }

    private fun input() = compose.onNodeWithTag("proxy_group_search").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
    private fun awaitIme(show: Boolean) = compose.waitUntil(5_000) {
        ViewCompat.getRootWindowInsets(checkNotNull(view))?.isVisible(WindowInsetsCompat.Type.ime()) == show
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(200)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
