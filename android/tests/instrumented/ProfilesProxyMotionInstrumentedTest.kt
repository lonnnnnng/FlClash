package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.roundToIntRect
import com.lonnnnnng.flclash.data.readAuthorizedProxyProfile
import com.lonnnnnng.flclash.service.RuntimeConfigPreview
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 作者 long：尺寸和页面动效必须保留实际返回、滚动与草稿确认，验证最终可操作状态而不依赖某一动画帧。 */
class ProfilesProxyMotionInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun prepare() { assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture() }

    @Test fun scrolledRootSubmenuResetsScrollAndReturnsWithoutMovingAnchorOrDuplicatingAction() {
        var open by mutableStateOf(false); var child by mutableStateOf(false)
        var anchor by mutableStateOf(IntRect.Zero); var submissions = 0
        compose.setContent { KClashTheme {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                Button({ open = true }, Modifier.align(Alignment.BottomEnd).testTag("motion_menu_open")
                    .onGloballyPositioned { anchor = it.boundsInWindow().roundToIntRect() }) { Text("菜单") }
                AppDropdownMenu(open, { open = false }, anchor = anchor, pageKey = child,
                    onNavigateBack = if (child) ({ child = false }) else null) {
                    if (child) {
                        AppDropdownMenuItem({ Text("返回") }, { child = false }, Modifier.testTag("motion_menu_back"))
                        AppDropdownMenuItem({ Text("完成") }, { submissions++; open = false }, Modifier.testTag("motion_menu_submit"))
                    } else {
                        repeat(24) { i -> AppDropdownMenuItem({ Text("选项 $i") }, {}, Modifier.testTag("motion_root_$i")) }
                        AppDropdownMenuItem({ Text("更多") }, { child = true }, Modifier.testTag("motion_menu_more"))
                    }
                }
            }
        } }
        compose.onNodeWithTag("motion_menu_open").performClick()
        compose.onNodeWithTag("motion_menu_more").performScrollTo().performClick()
        compose.onNodeWithTag("motion_menu_back").assertIsDisplayed()
        val childBounds = compose.onNodeWithTag("app_popup_menu").fetchSemanticsNode().boundsInWindow
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("motion_root_0").assertIsDisplayed()
        val rootBounds = compose.onNodeWithTag("app_popup_menu").fetchSemanticsNode().boundsInWindow
        assertEquals(rootBounds.right, childBounds.right, 1f)
        assertEquals(rootBounds.top, childBounds.top, 1f)
        compose.onNodeWithTag("motion_menu_more").performScrollTo().performClick()
        compose.onNodeWithTag("motion_menu_submit").performClick()
        compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
        assertEquals(1, submissions)
    }

    @Test fun fullPreviewKeepsToolbarWhileScrollingSearchingAndClosingWithSystemBack() {
        // 作者 long：长正文只读取授权原文件，既不生成替代YAML，也不把包含凭据的正文截图或写入测试日志。
        val content = readAuthorizedProxyProfile().decodeToString()
        val lines = content.lines()
        var state by mutableStateOf(RuntimeConfigPreviewUiState(9,
            preview = RuntimeConfigPreview(9, "原文件只读预览", content, false)))
        compose.setContent { KClashTheme { RuntimeConfigPreviewSheet(state, {}, { state = RuntimeConfigPreviewUiState() }) } }
        val toolbar = compose.onNodeWithTag("runtime_config_preview_find").fetchSemanticsNode().boundsInWindow
        compose.onNodeWithTag("runtime_config_preview_content").performScrollToIndex(lines.lastIndex)
        compose.onNodeWithTag("runtime_config_preview_close").assertIsDisplayed()
        assertEquals(toolbar, compose.onNodeWithTag("runtime_config_preview_find").fetchSemanticsNode().boundsInWindow)
        compose.onNodeWithTag("runtime_config_preview_find").performClick()
        compose.onNodeWithTag("runtime_config_preview_query").performTextInput("kclash-no-matching-line-acceptance")
        compose.onNodeWithTag("runtime_config_preview_next").assertIsNotEnabled()
        compose.onNodeWithTag("runtime_config_preview_query").performTextReplacement("rules:")
        compose.onNodeWithTag("runtime_config_preview_next").assertIsEnabled().performClick()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("runtime_config_preview_previous").assertIsEnabled().performClick()
        compose.onNodeWithTag("runtime_config_preview_retry").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("profile_editor_page").assertDoesNotExist()
        assertNull(state.preview)
    }

    @Test fun documentBackConfirmationKeepsDraftUntilSaveOrDiscardAfterPageEntrance() {
        var state by mutableStateOf(ProfileDocumentEditorState(ownerId = 9, session = 1, original = "正文草稿", content = "正文草稿"))
        var saves = 0; var dismisses = 0
        compose.setContent { KClashTheme {
            ProfileDocumentEditor(state, { state = state.copy(content = it) }, { saves++ }, { dismisses++ })
        } }
        compose.onNodeWithTag("profile_editor_content").performTextReplacement("修改后的正文草稿")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("profile_document_back").performClick()
        compose.onNodeWithText("保存正文修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("profile_editor_content").assertTextContains("修改后的正文草稿")
        assertEquals(0, dismisses); assertEquals(0, saves)
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("保存并返回").performClick()
        assertEquals(1, saves); assertEquals(0, dismisses)
        compose.onNodeWithTag("profile_document_back").performClick()
        compose.onNodeWithTag("profile_document_discard").performClick()
        assertEquals(1, dismisses)
    }
}
