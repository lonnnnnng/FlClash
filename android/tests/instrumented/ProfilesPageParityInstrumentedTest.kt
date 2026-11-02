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
            compose.onNodeWithTag("profile_actions_selected").performTouchInput { longClick() }
            compose.onNodeWithTag("profile_extend_801").performClick()
            compose.onNodeWithTag("profile_extension_dns").assertIsDisplayed().performClick()
            capture("profiles_extension_$theme")
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithTag("profile_actions_selected").performTouchInput { longClick() }
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

    @Test fun customOverviewTitleMenuAndMoreRenderBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var profile by mutableStateOf(Profile.custom(802, "家庭与出行的自定义配置"))
        var dark by mutableStateOf(false)
        var previewId: Long? = null
        var openedDialers = 0
        val summary = CustomProfileSummary(rules = 16, referencedProviders = 3, missingProviders = 1,
            proxies = 8, groups = listOf(CustomProfileGroupSummary("常用策略", "select", 8, 2)),
            dnsEnabled = true, dialers = 2)
        compose.setContent { KClashTheme(darkTheme = dark) {
            ProfileEditorDialog(profile, "mixed-port: 7890", false, onDismiss = {}, onSave = {},
                overview = summary, onRename = { _, label -> profile = profile.copy(label = label) },
                onPreview = { previewId = it }, onOpenDialersEditor = { openedDialers++ })
        } }
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark" }
            compose.onNodeWithTag("custom_profile_rules").assertIsDisplayed()
            compose.onNodeWithTag("custom_profile_resources").assertTextContains("3 · 缺失 1")
            compose.onNodeWithTag("custom_profile_group_0").assertIsDisplayed()
            capture("profiles_custom_overview_$theme")
            compose.onNodeWithTag("custom_profile_title_menu").performClick()
            compose.onNodeWithTag("custom_profile_preview").performClick()
            assertEquals(profile.id, previewId)
            compose.onNodeWithTag("custom_profile_title_menu").performClick()
            compose.onNodeWithTag("custom_profile_rename").performClick()
            compose.onNodeWithTag("profile_rename_label").performTextClearance()
            compose.onNodeWithTag("profile_rename_label").performTextInput("家庭配置 $theme")
            compose.onNodeWithTag("profile_rename_save").performClick()
            compose.onNodeWithText("家庭配置 $theme").assertIsDisplayed()
            compose.onNodeWithTag("custom_profile_more").performClick()
            compose.onNodeWithTag("custom_profile_dialers").assertIsDisplayed().performClick()
            compose.onNodeWithTag("custom_profile_script").assertIsDisplayed()
            capture("profiles_custom_more_$theme")
            compose.onNodeWithTag("custom_profile_more_back").performClick()
        }
        assertEquals(2, openedDialers)
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
