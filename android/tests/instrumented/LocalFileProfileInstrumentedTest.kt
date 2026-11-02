package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LocalFileProfileInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val file = Profile.custom(850, "本地配置").copy(type = ProfileType.FILE, ageSecretKey = "fixture-identity")

    @Test fun localEditorMasksKeyTrimsMetadataSaveAndRendersBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var saved = false
        var body: String? = "not-saved"
        var key: String? = null
        compose.setContent { KClashTheme(darkTheme = dark) {
            FileProfileEditorDialog(ProfileEditorUiState(file, "mode: rule\n"), false, {}, { null }, { _, _ -> }, {}, { content, identity ->
                saved = true; body = content; key = identity
            })
        } }
        compose.onNodeWithTag("file_profile_editor_age_key").assert(
            SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password))
        compose.onNodeWithTag("profile_editor_content").assertDoesNotExist()
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark" }
            compose.waitForIdle()
            val bitmap = checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            val png = ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            recordVisualPng("profile_local_file_editor_$theme", png)
        }
        compose.onNodeWithTag("file_profile_editor_age_key").performTextClearance()
        compose.onNodeWithTag("file_profile_editor_age_key").performTextInput(" new-fixture ")
        compose.onNodeWithTag("file_profile_editor_save").performClick()
        assertTrue(saved)
        assertNull(body)
        assertTrue(key == "new-fixture")
    }

    @Test fun invalidPreviewAndMissingKeyBlockSaveAndClearingSelectionDoesNotWrite() {
        assertPortraitDefaultFontConfiguration()
        var state by mutableStateOf(ProfileEditorUiState(file.copy(ageSecretKey = ""), "mode: rule\n",
            replacement = ProfileFileReplacementPreview("invalid.yaml", 20, false, false)))
        var saves = 0
        compose.setContent { KClashTheme {
            FileProfileEditorDialog(state, false, {}, { null }, { _, _ -> }, { state = state.copy(replacement = null) }, { _, _ -> saves++ })
        } }
        compose.onNodeWithTag("file_profile_editor_save").assertIsNotEnabled()
        compose.onNodeWithTag("file_profile_replacement_clear").performClick()
        assertEquals(0, saves)
        compose.onNodeWithTag("file_profile_replacement_name").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(replacement = ProfileFileReplacementPreview("1.yaml", 400, true, false)) }
        compose.onNodeWithTag("file_profile_editor_save").assertIsNotEnabled()
        compose.onNodeWithTag("file_profile_editor_age_key").performTextInput("fixture")
        compose.onNodeWithTag("file_profile_editor_save").assertIsEnabled()
    }

    @Test fun unsavedKeyAndYamlRequireDiscardConfirmation() {
        assertPortraitDefaultFontConfiguration()
        var dismissed = false
        var saves = 0
        compose.setContent { KClashTheme {
            FileProfileEditorDialog(ProfileEditorUiState(file, "mode: rule\n"), false, { dismissed = true }, { null }, { _, _ -> }, {}, { _, _ -> saves++ })
        } }
        compose.onNodeWithTag("file_profile_toggle_yaml").performClick()
        compose.onNodeWithTag("profile_editor_content").performTextInput("# draft\n")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        assertFalse(dismissed)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("放弃").performClick()
        assertTrue(dismissed)
        assertEquals(0, saves)
    }

    @Test fun runtimeLocksFileMutationsAndBackgroundClosesEditor() {
        assertPortraitDefaultFontConfiguration()
        var open by mutableStateOf(true)
        var running by mutableStateOf(true)
        compose.setContent { KClashTheme { if (open) {
            FileProfileEditorDialog(ProfileEditorUiState(file, "mode: rule\n"), running, { open = false }, { error("running cannot pick") }, { _, _ -> }, {}, { _, _ -> error("running cannot save") })
        } } }
        compose.onNodeWithTag("file_profile_editor_save").assertIsNotEnabled()
        compose.onNodeWithTag("file_profile_replace_pick").assertIsNotEnabled()
        compose.onNodeWithTag("file_profile_editor_age_key").assertIsNotEnabled()
        compose.runOnIdle { running = false }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(open)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("file_profile_editor_age_key").assertDoesNotExist()
    }

    @Test fun loadingAndSavingEditorsCloseOnBackgroundWithoutRestoringSecrets() {
        assertPortraitDefaultFontConfiguration()
        var editor by mutableStateOf(ProfileEditorUiState())
        compose.setContent { KClashTheme {
            ProfilesPanel(state = ProfilesUiState(), editorState = editor, runtimeState = com.lonnnnnng.flclash.service.CoreRuntimeStatus.STOPPED,
                showAddDialog = false, onShowAddDialog = {}, onDismissAddDialog = {}, onPickFile = {}, onPickQrCode = {},
                onAddRemote = { _, _ -> }, onAddCustom = {}, onSelect = {}, onEdit = {}, onUpdate = {},
                onCloseEditor = { editor = ProfileEditorUiState() }, onSaveEditor = {}, onDelete = {})
        } }
        for (saving in listOf(false, true)) {
            compose.runOnIdle { editor = ProfileEditorUiState(file, "mode: rule\n", isLoading = !saving, isSaving = saving) }
            if (saving) compose.onNodeWithTag("file_profile_editor_age_key").assertExists()
            else compose.onNodeWithText("读取配置").assertIsDisplayed()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            assertNull(editor.profile)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("file_profile_editor_age_key").assertDoesNotExist()
        }
    }
}
