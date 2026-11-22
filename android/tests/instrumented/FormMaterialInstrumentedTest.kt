package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

/** 作者 long：使用真实GPU背景层与窗口验证，静态材质截图不能证明实时更新或键盘可达。 */
class FormMaterialInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        // 作者 long：系统截图与Compose提交不在同一时钟；等待实际Window完成绘制，避免深浅切换截到旧主题。
        android.os.SystemClock.sleep(600)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        recordVisualPng("form_material_$name", ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
    @Test fun importSheetThemesBatchErrorAndBusyPreserveDraft() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var feedback by mutableStateOf(ProfileAddFeedback())
        var calls = 0
        compose.setContent { KClashTheme(darkTheme = dark) {
            AddProfileDialog(false, {}, {}, {}, { _, _ -> calls++ }, onAddCustom = {},
                initialRemoteUrl = "", feedback = feedback)
        } }
        compose.onNodeWithTag("profile_add_remote_sheet").assertIsDisplayed()
        compose.onNodeWithTag("profile_add_submit").assertIsNotEnabled()
        capture("empty_light_ime")
        compose.runOnIdle { dark = true }; capture("empty_dark_ime")
        compose.runOnIdle { dark = false }
        compose.onNodeWithTag("profile_add_remote_url").performTextReplacement("https://example.invalid/sub")
        capture("single_light_ime")
        compose.runOnIdle { dark = true }; capture("single_dark_ime")
        compose.onNodeWithTag("profile_add_remote_age_key").performTextInput("fixture-secret")
        compose.onNodeWithTag("profile_add_remote_age_key_visibility").performClick()
        compose.onNodeWithTag("profile_add_remote_age_key").assertTextContains("fixture-secret")
        compose.onNodeWithTag("profile_add_mode_batch").performClick()
        compose.onNodeWithTag("profile_add_remote_age_key").assertDoesNotExist()
        compose.onNodeWithTag("profile_add_remote_batch").assertTextContains("https://example.invalid/sub")
        capture("batch_dark_ime")
        compose.onNodeWithTag("profile_add_mode_single").performClick()
        compose.onNodeWithTag("profile_add_remote_age_key").assert(SemanticsMatcher("切换模式后密钥已清空") {
            it.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty()
        })
        compose.onNodeWithTag("profile_add_submit").performClick()
        compose.runOnIdle { assertEquals(1, calls); feedback = ProfileAddFeedback(phase = ProfileAddPhase.RUNNING, message = "正在处理订阅…") }
        compose.onNodeWithTag("profile_add_back").assertIsNotEnabled()
        compose.onNodeWithTag("profile_add_progress").assertIsDisplayed(); capture("busy_dark")
        compose.runOnIdle { feedback = ProfileAddFeedback(phase = ProfileAddPhase.FAILED, message = "导入失败，请重试") }
        compose.onNodeWithTag("profile_add_remote_url").assertTextContains("https://example.invalid/sub")
        compose.onNodeWithTag("profile_add_submit").assertIsEnabled(); capture("failed_dark")
    }

    @Test fun liveDockTracksBackgroundWithoutSamplingOnTouchAndCancelDoesNotNavigate() {
        assertPortraitDefaultFontConfiguration()
        var color by mutableStateOf(Color(0xFFCA7040))
        var calls = 0
        lateinit var source: AppLiveBackdrop
        compose.setContent { KClashTheme { CompositionLocalProvider(LocalAppMenuGlass provides true) {
            AppLiveMaterialHost(Modifier.fillMaxSize(), background = { Box(Modifier.fillMaxSize().background(color)) }, foreground = {
                source = checkNotNull(LocalAppLiveBackdrop.current)
                Box(Modifier.align(Alignment.BottomCenter)) {
                    MainNavigationDock(MainDestination.DASHBOARD, listOf(MainDestination.DASHBOARD, MainDestination.PROFILES, MainDestination.TOOLS)) { calls++ }
                }
            })
        } } }
        val dock = compose.onNodeWithTag("main_navigation_dock")
        val first = dock.captureToImage().toPixelMap()
        compose.runOnIdle { color = Color(0xFF4070CA) }
        val second = dock.captureToImage().toPixelMap()
        assertNotEquals(first[first.width / 2, 6], second[second.width / 2, 6])
        capture("live_dock_blue")
        val before = source.recordings
        compose.mainClock.autoAdvance = false
        dock.performTouchInput { down(center); moveTo(Offset(width * .8f, height * .5f), 80) }
        compose.mainClock.advanceTimeBy(180)
        compose.runOnIdle { assertEquals(0, calls); assertEquals(before, source.recordings) }
        capture("live_dock_held")
        dock.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1800); compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(0, calls); assertEquals(before, source.recordings) }
    }

    @Test fun liveLayerFollowsScrollAndTransformWithoutBakingForeground() {
        assertPortraitDefaultFontConfiguration()
        lateinit var scroll: androidx.compose.foundation.ScrollState
        var shift by mutableFloatStateOf(0f)
        compose.setContent { KClashTheme { CompositionLocalProvider(LocalAppMenuGlass provides true) {
            scroll = rememberScrollState()
            AppLiveMaterialHost(Modifier.fillMaxSize(), background = {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("live_scroll")) {
                    repeat(40) { i -> Box(Modifier.fillMaxWidth().height(80.dp)
                        .background(if (i % 2 == 0) Color.Red else Color.Blue)) }
                }
                Box(Modifier.fillMaxWidth().height(100.dp).align(Alignment.BottomCenter)
                    .graphicsLayer { translationY = shift }.background(Color.Green))
            }, foreground = {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 45.dp).size(180.dp, 50.dp)
                    .appLiveMaterial(AppSuperellipseShape(20.dp), Color.White.copy(alpha = .1f), blurDp = 0f).testTag("live_probe"))
            })
        } } }
        val pane = compose.onNodeWithTag("live_probe")
        val before = pane.captureToImage().toPixelMap()
        compose.runOnIdle { shift = -600f }
        val moved = pane.captureToImage().toPixelMap()
        assertNotEquals(before[before.width/2, before.height/2], moved[moved.width/2, moved.height/2])
        compose.onNodeWithTag("live_scroll").performTouchInput { swipeUp(durationMillis = 280) }
        compose.runOnIdle { assertTrue(scroll.value > 0) }
        capture("scroll_transformed")
    }

    @Test fun liveButtonUsesSeparateBackgroundAndBusyCancelsPress() {
        assertPortraitDefaultFontConfiguration()
        var color by mutableStateOf(Color.Red)
        var enabled by mutableStateOf(true)
        var calls = 0
        compose.setContent { KClashTheme { CompositionLocalProvider(LocalAppMenuGlass provides true) {
            AppLiveMaterialHost(Modifier.fillMaxSize(), background = { Box(Modifier.fillMaxSize().background(color)) }, foreground = {
                val shape = AppSuperellipseShape(24.dp)
                Button({ calls++ }, Modifier.align(Alignment.Center).size(140.dp, 64.dp)
                    .appLiveMaterial(shape, Color.White.copy(alpha = .25f), refractionDp = 5f)
                    .appGlassTouch(shape, enabled).testTag("live_button"), enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)) { Text("启动") }
            })
        } } }
        val button = compose.onNodeWithTag("live_button")
        val a = button.captureToImage().toPixelMap()
        compose.runOnIdle { color = Color.Blue }
        val b = button.captureToImage().toPixelMap()
        assertNotEquals(a[12, a.height / 2], b[12, b.height / 2])
        compose.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }; compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { enabled = false }; compose.mainClock.advanceTimeBy(800)
        button.performTouchInput { up() }; compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(0, calls) }; capture("button_disabled")
    }

    @Test fun completedImportClosesWindowAndDoesNotReopenSource() {
        assertPortraitDefaultFontConfiguration()
        var visible by mutableStateOf(true)
        var feedback by mutableStateOf(ProfileAddFeedback())
        compose.setContent { KClashTheme {
            Text("已返回配置", Modifier.testTag("form_underlay"))
            if (visible) AddProfileDialog(false, { visible = false }, {}, {}, { _, _ -> }, onAddCustom = {},
                initialRemoteUrl = "https://example.invalid/sub", feedback = feedback)
        } }
        compose.onNodeWithTag("profile_add_submit").performClick()
        compose.runOnIdle { feedback = ProfileAddFeedback(phase = ProfileAddPhase.SUCCEEDED, message = "已添加") }
        compose.onNodeWithTag("profile_add_back").assertIsEnabled().performClick()
        compose.onNodeWithTag("profile_add_remote_sheet").assertDoesNotExist()
        compose.onNodeWithTag("profile_add_source_menu").assertDoesNotExist()
        compose.onNodeWithTag("form_underlay").assertIsDisplayed()
        compose.runOnIdle { assertFalse(visible) }
    }

    @Test fun glassTogglePreservesPageDraftScrollAndLastRowRemainsAboveDock() {
        assertPortraitDefaultFontConfiguration()
        var glass by mutableStateOf(false)
        var destination by mutableStateOf(MainDestination.PROFILES)
        compose.setContent { KClashTheme { CompositionLocalProvider(LocalAppMenuGlass provides glass) {
            MainNavigationHost(destination, MainDestination.entries, { destination = it }) { page ->
                val list = rememberLazyListState()
                var draft by rememberSaveable { mutableStateOf("") }
                Column(Modifier.fillMaxSize()) {
                    TextField(draft, { draft = it }, Modifier.testTag("retained_draft_${page.name}"))
                    LazyColumn(state = list, modifier = Modifier.weight(1f).testTag("retained_list_${page.name}"),
                        contentPadding = PaddingValues(bottom = 16.dp + LocalMainDockInset.current)) {
                        items(60) { i -> Text("条目 $i", Modifier.fillMaxWidth().height(60.dp).testTag("retained_row_$i")) }
                    }
                }
            }
        } } }
        compose.onNodeWithTag("retained_draft_PROFILES").performTextInput("未提交草稿")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("retained_list_PROFILES").performScrollToIndex(30)
        val top = compose.onNodeWithTag("retained_row_30").fetchSemanticsNode().boundsInRoot.top
        for (enabled in listOf(true, false, true)) {
            compose.runOnIdle { glass = enabled }
            compose.onNodeWithTag("retained_draft_PROFILES").assertTextContains("未提交草稿")
            assertEquals(top, compose.onNodeWithTag("retained_row_30").fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        compose.onNodeWithTag("retained_list_PROFILES").performScrollToIndex(59)
        val last = compose.onNodeWithTag("retained_row_59").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("main_navigation_dock").fetchSemanticsNode().boundsInRoot
        assertTrue("末项必须能滚动到Dock上方", last.bottom <= dock.top)
        capture("last_row_above_dock")
    }

    @Test fun runtimeCardKeepsHeightWhenGlassChanges() {
        assertPortraitDefaultFontConfiguration()
        var glass by mutableStateOf(false)
        compose.setContent { KClashTheme { CompositionLocalProvider(LocalAppMenuGlass provides glass) {
            RuntimeCard(com.lonnnnnng.flclash.service.CoreRuntimeStatus.STOPPED, {}, {}, canStart = true)
        } } }
        val card = compose.onNodeWithTag("dashboard_runtime_card")
        val plainHeight = card.fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { glass = true }
        assertEquals(plainHeight, card.fetchSemanticsNode().boundsInRoot.height, 1f)
        capture("runtime_glass")
    }
}
