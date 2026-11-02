package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.RuntimeConfigPreview
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfilePreviewInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val profiles = listOf(
        Profile.remote(731, "https://example.test/not-downloaded", "未选中的订阅"),
        Profile.custom(732, "未选中的文件").copy(type = ProfileType.FILE),
        Profile.custom(733, "未选中的自定义配置"),
    )

    @Test fun allProfileTypesOfferReadOnlyPreviewWithoutSelectingInStoppedAndRunningStates() {
        val actions = mutableListOf<Pair<String, Long>>()
        var running by mutableStateOf(false)
        compose.setContent {
            KClashTheme {
                ProfilesPanel(
                    state = ProfilesUiState(profiles = profiles, currentProfileId = 799),
                    editorState = ProfileEditorUiState(),
                    runtimeState = if (running) CoreRuntimeStatus.RUNNING else CoreRuntimeStatus.STOPPED,
                    showAddDialog = false, onShowAddDialog = {}, onDismissAddDialog = {}, onPickFile = {}, onPickQrCode = {},
                    onAddRemote = { _, _ -> }, onAddCustom = {}, onSelect = { actions += "select" to it },
                    onPreviewProfile = { actions += "preview" to it },
                    onEdit = {}, onUpdate = {}, onCloseEditor = {}, onSaveEditor = {}, onDelete = {},
                )
            }
        }
        for (run in listOf(false, true)) {
            compose.runOnIdle { running = run }
            for (profile in profiles) {
                compose.onNodeWithTag("profile_actions_${profile.id}").performClick()
                if (profile.type != ProfileType.CUSTOM) compose.onNodeWithTag("profile_more_${profile.id}").performClick()
                compose.onNodeWithTag("profile_preview_${profile.id}").assertIsEnabled().performClick()
            }
        }
        assertEquals(profiles.map { "preview" to it.id } + profiles.map { "preview" to it.id }, actions)
    }

    @Test fun noncurrentPreviewStaysOpenInProfilesAndClearsOnTargetSelectionRuntimeOrPageChanges() {
        val original = profiles.first()
        var target by mutableStateOf<Profile?>(original)
        var current by mutableStateOf<Long?>(799)
        var runtime by mutableStateOf(CoreRuntimeStatus.STOPPED)
        var destination by mutableStateOf("PROFILES")
        var state by mutableStateOf(preview(original))
        compose.setContent {
            KClashTheme {
                RuntimeConfigPreviewHost(state, target, null, current, runtime, destination,
                    onRetry = {}, onClose = { state = RuntimeConfigPreviewUiState() })
            }
        }
        compose.onNodeWithTag("runtime_config_preview_sheet").assertIsDisplayed()
        compose.onNodeWithText(original.label).assertIsDisplayed()
        compose.runOnIdle { target = original.copy(label = "已改名") }
        compose.onNodeWithTag("runtime_config_preview_sheet").assertDoesNotExist()
        for (change in listOf("selection", "runtime", "page", "deleted")) {
            compose.runOnIdle { target = original; current = 799; runtime = CoreRuntimeStatus.STOPPED; destination = "PROFILES" }
            compose.waitForIdle()
            compose.runOnIdle { state = preview(original) }
            compose.onNodeWithTag("runtime_config_preview_sheet").assertIsDisplayed()
            compose.runOnIdle {
                when (change) {
                    "selection" -> current = original.id
                    "runtime" -> runtime = CoreRuntimeStatus.RUNNING
                    "page" -> destination = "DASHBOARD"
                    "deleted" -> target = null
                }
            }
            compose.onNodeWithTag("runtime_config_preview_sheet").assertDoesNotExist()
        }
    }

    @Test fun backgroundClearsPreviewAndReturningDoesNotRestoreContent() {
        val target = profiles.first()
        var state by mutableStateOf(preview(target))
        compose.setContent {
            KClashTheme {
                RuntimeConfigPreviewHost(state, target, null, 799, CoreRuntimeStatus.STOPPED, "PROFILES",
                    onRetry = {}, onClose = { state = RuntimeConfigPreviewUiState() })
            }
        }
        compose.onNodeWithTag("runtime_config_preview_sheet").assertIsDisplayed()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertNull(state.profileId)
        assertNull(state.preview)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("runtime_config_preview_sheet").assertDoesNotExist()
    }

    @Test fun noncurrentPreviewRendersBothThemesAndSupportsFailureRetryAndClose() {
        assertPortraitDefaultFontConfiguration()
        val target = profiles.first()
        var dark by mutableStateOf(false)
        var state by mutableStateOf(RuntimeConfigPreviewUiState(target.id, isLoading = true))
        var retries = 0
        compose.setContent {
            KClashTheme(darkTheme = dark) {
                RuntimeConfigPreviewHost(state, target, null, 799, CoreRuntimeStatus.RUNNING, "PROFILES",
                    onRetry = { retries++ }, onClose = { state = RuntimeConfigPreviewUiState() })
            }
        }
        compose.onNodeWithTag("runtime_config_preview_loading").assertIsDisplayed()
        compose.onNodeWithTag("runtime_config_preview_retry").assertIsNotEnabled()
        compose.runOnIdle { state = RuntimeConfigPreviewUiState(target.id, error = "配置文件不存在") }
        compose.onNodeWithTag("runtime_config_preview_error").assertTextEquals("配置文件不存在")
        compose.onNodeWithTag("runtime_config_preview_retry").performClick()
        assertEquals(1, retries)
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark"; state = preview(target) }
            compose.onNodeWithTag("runtime_config_preview_origin").assertTextEquals("由这份配置生成的预览，只读；尚未经过核心校验。")
            val bitmap = compose.onNodeWithTag("runtime_config_preview_sheet").captureToImage().asAndroidBitmap()
            val png = ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            recordVisualPng("profile_noncurrent_preview_$theme", png)
        }
        compose.onNodeWithTag("runtime_config_preview_close").performClick()
        compose.onNodeWithTag("runtime_config_preview_sheet").assertDoesNotExist()
    }

    private fun preview(profile: Profile) = RuntimeConfigPreviewUiState(profile.id,
        preview = RuntimeConfigPreview(profile.id, profile.label, "mixed-port: 7891\nmode: rule\n", false))
}
