package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** 作者 long：同一导航容器在真机逐帧推进，证据包含中间位移及状态恢复；固定文本不读取或截图用户配置正文。 */
class MainPageMotionInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun continuousPagesKeepDockAndRestoreScrollAndDraftInBothThemes() {
        assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture()
        var destination by mutableStateOf(MainDestination.DASHBOARD)
        var dark by mutableStateOf(false)
        compose.setContent { KClashTheme(darkTheme = dark) {
            MainNavigationHost(destination, MainDestination.entries, { destination = it }) { page ->
                MainPageScaffold(page, { destination = it }, showNavigation = false) { padding ->
                    val scroll = rememberLazyListState()
                    var draft by rememberSaveable { mutableStateOf("") }
                    Column(Modifier.fillMaxSize().padding(padding).testTag("motion_page_${page.name}")) {
                        OutlinedTextField(draft, { draft = it }, Modifier.testTag("motion_draft_${page.name}"), label = { Text("草稿") })
                        LazyColumn(state = scroll, modifier = Modifier.weight(1f).testTag("motion_list_${page.name}")) {
                            items(60) { Text("${page.label}条目 $it", Modifier.fillMaxWidth().height(56.dp).testTag("motion_row_${page.name}_$it")) }
                        }
                    }
                }
            }
        } }
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark"; destination = MainDestination.PROFILES }
            compose.onNodeWithTag("motion_draft_PROFILES").performTextReplacement("保留草稿 $theme")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("motion_list_PROFILES").performScrollToIndex(30)
            val scrollBounds = compose.onNodeWithTag("motion_row_PROFILES_30").fetchSemanticsNode().boundsInRoot
            val dockBounds = compose.onNodeWithTag("main_navigation_dock").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("nav_dashboard").performClick()
            // 作者 long：先等待前一次导航完成，再冻结测试时钟；否则两次点击会在同一帧合并，根本没有发生返程动画。
            compose.onNodeWithTag("motion_page_DASHBOARD").assertIsDisplayed()
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.mainClock.advanceTimeByFrame()
            val hashes = mutableSetOf<String>()
            val positions = mutableListOf<Float>()
            repeat(5) { frame ->
                compose.mainClock.advanceTimeBy(48)
                val node = compose.onNodeWithTag("motion_page_PROFILES").fetchSemanticsNode()
                positions += node.layoutInfo.coordinates.positionInRoot().x
                assertEquals(dockBounds, compose.onNodeWithTag("main_navigation_dock").fetchSemanticsNode().boundsInRoot)
                hashes += capture("main_page_${theme}_frame_$frame")
            }
            assertTrue("至少三个不同的中间帧", hashes.size >= 3)
            assertTrue("页面必须从右向左连续接近终点", positions.first() > 0 && positions.zipWithNext().all { (a, b) -> b < a })
            compose.mainClock.advanceTimeBy(500)
            compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("motion_draft_PROFILES").assertTextContains("保留草稿 $theme")
            assertEquals(scrollBounds, compose.onNodeWithTag("motion_row_PROFILES_30").fetchSemanticsNode().boundsInRoot)
            capture("main_page_${theme}_settled")
        }
    }

    @Test fun rapidReversalAndSkippedDestinationsFinishWithoutDuplicatePages() {
        assertPortraitDefaultFontConfiguration()
        var destination by mutableStateOf(MainDestination.PROFILES)
        val built = mutableSetOf<MainDestination>()
        compose.setContent { KClashTheme {
            MainPageTransition(destination, Modifier.fillMaxSize()) { page ->
                SideEffect { built += page }
                Text(page.label, Modifier.fillMaxSize().testTag("motion_${page.name}"))
            }
        } }
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { destination = MainDestination.TOOLS }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnUiThread { destination = MainDestination.PROFILES }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnUiThread { destination = MainDestination.DASHBOARD }
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("motion_DASHBOARD").assertIsDisplayed()
        compose.onNodeWithTag("motion_TOOLS").assertDoesNotExist()
        compose.onNodeWithTag("motion_PROFILES").assertDoesNotExist()
        assertFalse("跨页导航不能构建或触发中间代理页", MainDestination.PROXY in built)
    }

    private fun capture(name: String): String {
        val bitmap = compose.onNodeWithTag("main_page_transition").captureToImage().asAndroidBitmap()
        val png = ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        recordVisualPng(name, png)
        return MessageDigest.getInstance("SHA-256").digest(png).joinToString("") { "%02x".format(it) }
    }
}
