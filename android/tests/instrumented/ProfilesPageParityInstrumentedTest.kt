package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：整页批次同时验证列表、菜单、扩展和编辑层级，避免单个控件通过却无法走完整条路径。 */
class ProfilesPageParityInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedProfileRevealsInLongListAndLongPressKeepsSelection() {
        assertPortraitDefaultFontConfiguration()
        val profiles = (1L..28L).map { Profile.custom(it, "家庭配置 $it") }
        var selected: Long? = null
        compose.setContent { KClashTheme {
            ProfilesPanel(state = ProfilesUiState(profiles, 28L), editorState = ProfileEditorUiState(),
                runtimeState = CoreRuntimeStatus.STOPPED, showAddDialog = false, onShowAddDialog = {},
                onDismissAddDialog = {}, onPickFile = {}, onPickQrCode = {}, onAddRemote = { _, _ -> },
                onAddCustom = {}, onSelect = { selected = it }, onEdit = {}, onUpdate = {},
                onCloseEditor = {}, onSaveEditor = {}, onDelete = {})
        } }
        compose.onNodeWithTag("profile_card_28").assertIsDisplayed().performTouchInput { longClick() }
        compose.onNodeWithText("编辑").assertIsDisplayed()
        compose.onNodeWithText("重命名").assertIsDisplayed()
        compose.onNodeWithText("运行配置").assertIsDisplayed()
        compose.onNodeWithText("高级").assertDoesNotExist()
        assertNull(selected)
    }

    @Test fun remoteMenuExtensionAndFullPageEditorRenderBothThemes() {
        assertPortraitDefaultFontConfiguration()
        val profile = Profile.remote(801L, "https://example.test/sub", "家庭与出行配置名称很长时也保持单行并显示选中状态")
        var dark by mutableStateOf(false)
        var editing by mutableStateOf(false)
        var openedDns = 0
        compose.setContent { KClashTheme(darkTheme = dark) {
            ProfilesPanel(state = ProfilesUiState(listOf(profile), profile.id), editorState = ProfileEditorUiState(),
                remoteEditorState = RemoteProfileEditorUiState(profile.takeIf { editing }),
                runtimeState = CoreRuntimeStatus.STOPPED, showAddDialog = false, onShowAddDialog = {},
                onDismissAddDialog = {}, onPickFile = {}, onPickQrCode = {}, onAddRemote = { _, _ -> },
                onAddCustom = {}, onSelect = {}, onEdit = { editing = true }, onUpdate = {},
                onCloseEditor = { editing = false }, onSaveEditor = {}, onDelete = {},
                onOpenDnsEditor = { openedDns++ })
        } }
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark" }
            capture("profiles_page_$theme")
            compose.onNodeWithTag("profile_actions_selected").performClick()
            compose.onNodeWithTag("profile_extend_801").performClick()
            compose.onNodeWithTag("profile_extension_dns").assertIsDisplayed().performClick()
            capture("profiles_extension_$theme")
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithTag("profile_actions_selected").performClick()
            compose.onNodeWithTag("profile_edit_801").performClick()
            compose.onNodeWithTag("profile_editor_page").assertIsDisplayed()
            compose.onNodeWithTag("remote_profile_editor_age_key").assertIsDisplayed()
            compose.onNodeWithTag("remote_profile_editor_auto_update").performClick()
            compose.onNodeWithTag("remote_profile_editor_interval").assertDoesNotExist()
            capture("profiles_remote_editor_$theme")
            compose.onNodeWithText("取消").performClick()
        }
        assertEquals(2, openedDns)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // 作者 long：全屏 Dialog 的窗口提交晚于 Compose 空闲，等待系统绘制完成再采集，避免混入前一页。
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(400)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val png = ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        recordVisualPng(name, png)
    }
}
