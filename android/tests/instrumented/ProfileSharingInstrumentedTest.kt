package com.lonnnnnng.flclash.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.lonnnnnng.flclash.data.AndroidClipboardGateway
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileSharingInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val file = Profile.custom(711L, "本地文件").copy(type = ProfileType.FILE)
    private val remote = Profile.remote(712L, "https://example.test/sub?token=fixture%2Bvalue", "家庭订阅")
    private val custom = Profile.custom(713L, "自定义配置")

    @Test fun urlSharingMenuWorksForNoncurrentProfileWhileRunning() {
        val actions = mutableListOf<Pair<String, Long>>()
        setPanel(listOf(file, remote), running = true, actions)
        openMore(remote.id)
        composeRule.onNodeWithTag("profile_copy_link_${remote.id}").assertIsEnabled().performClick()
        openMore(remote.id)
        composeRule.onNodeWithTag("profile_show_qr_${remote.id}").assertIsEnabled().performClick()
        openMore(remote.id)
        composeRule.onNodeWithTag("profile_export_${remote.id}").assertIsEnabled().performClick()
        assertEquals(listOf("copy" to remote.id, "qr" to remote.id, "export" to remote.id), actions)
        composeRule.onNodeWithTag("profile_selected_${file.id}", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("profile_actions_${remote.id}").performTouchInput { longClick() }
        composeRule.onNodeWithTag("profile_edit_${remote.id}").assertIsEnabled()
        composeRule.onNodeWithTag("profile_rename_${remote.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("profile_extend_${remote.id}").assertIsEnabled()
    }

    @Test fun transitionsDisableWritesAndKeepReadOnlySharingAvailable() {
        var status by mutableStateOf(CoreRuntimeStatus.STARTING)
        val actions = mutableListOf<Pair<String, Long>>()
        setPanel(listOf(file, remote), running = false, actions, status = { status })
        for (phase in listOf(CoreRuntimeStatus.STARTING, CoreRuntimeStatus.STOPPING)) {
            composeRule.runOnIdle { status = phase }
            composeRule.onNodeWithTag("profile_actions_${remote.id}").performTouchInput { longClick() }
            composeRule.onNodeWithTag("profile_edit_${remote.id}").assertIsNotEnabled()
            composeRule.onNodeWithTag("profile_more_${remote.id}").performClick()
            composeRule.onNodeWithTag("profile_copy_link_${remote.id}").assertIsEnabled().performClick()
        }
        assertEquals(listOf("copy" to remote.id, "copy" to remote.id), actions)
    }

    @Test fun fileAndCustomMenusExposeOnlySupportedActions() {
        val actions = mutableListOf<Pair<String, Long>>()
        setPanel(listOf(remote, file, custom), running = false, actions)
        openMore(file.id)
        composeRule.onNodeWithTag("profile_copy_link_${file.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("profile_show_qr_${file.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("profile_export_${file.id}").performClick()
        assertEquals(listOf("export" to file.id), actions)
        composeRule.onNodeWithTag("profile_actions_${custom.id}").performTouchInput { longClick() }
        composeRule.onNodeWithTag("profile_more_${custom.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("profile_export_${custom.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("profile_edit_${custom.id}").assertIsEnabled()
        composeRule.onNodeWithText("高级").assertDoesNotExist()
    }

    @Test fun qrDialogRendersDecodableCodeInBothThemesAndCloses() {
        assertPortraitDefaultFontConfiguration()
        val matrix = runBlocking { SubscriptionQrEncoder().encode(remote.url) }
        var dark by mutableStateOf(false)
        var visible by mutableStateOf(true)
        var copied = false
        composeRule.setContent {
            KClashTheme(darkTheme = dark) {
                if (visible) ProfileQrDialog(ProfileQrUiState(remote.id, remote.realLabel, matrix),
                    onCopy = { copied = true }, onClose = { visible = false })
            }
        }
        for (theme in listOf("light", "dark")) {
            composeRule.runOnIdle { dark = theme == "dark" }
            composeRule.waitForIdle()
            val bitmap = composeRule.onNodeWithTag("profile_qr_image").captureToImage().asAndroidBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            assertEquals(remote.url, QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text)
            val dialog = composeRule.onNodeWithTag("profile_qr_dialog").captureToImage().asAndroidBitmap()
            val png = ByteArrayOutputStream().also { dialog.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            recordVisualPng("profile_sharing_qr_$theme", png)
        }
        composeRule.onNodeWithText("复制链接").performClick()
        assertTrue(copied)
        composeRule.onNodeWithTag("profile_qr_close").performClick()
        composeRule.onNodeWithTag("profile_qr_dialog").assertDoesNotExist()
    }

    @Test fun sensitiveClipboardKeepsOriginalLink() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(ClipboardManager::class.java)
        // 作者 long：先让测试 Activity 获得前台剪贴板权限，最后恢复用户原剪贴板，避免验收破坏复制内容。
        composeRule.setContent { KClashTheme { androidx.compose.material3.Text("剪贴板验证") } }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val original = manager.primaryClip
            try {
                AndroidClipboardGateway(context).writeSensitiveText(remote.url)
                assertEquals(remote.url, manager.primaryClip?.getItemAt(0)?.text?.toString())
                assertTrue(manager.primaryClipDescription?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true)
            } finally {
                if (original != null) manager.setPrimaryClip(original) else manager.clearPrimaryClip()
            }
        }
    }

    private fun openMore(id: Long) {
        composeRule.onNodeWithTag("profile_actions_$id").performTouchInput { longClick() }
        composeRule.onNodeWithTag("profile_more_$id").performClick()
    }

    private fun setPanel(profiles: List<Profile>, running: Boolean, actions: MutableList<Pair<String, Long>>,
        status: () -> CoreRuntimeStatus = { if (running) CoreRuntimeStatus.RUNNING else CoreRuntimeStatus.STOPPED }) {
        composeRule.setContent {
            KClashTheme {
                ProfilesPanel(
                    state = ProfilesUiState(profiles = profiles, currentProfileId = profiles.first().id),
                    editorState = ProfileEditorUiState(),
                    runtimeState = status(),
                    showAddDialog = false,
                    onShowAddDialog = {}, onDismissAddDialog = {}, onPickFile = {}, onPickQrCode = {},
                    onAddRemote = { _, _ -> }, onAddCustom = {},
                    onSelect = { actions += "select" to it },
                    onCopyLink = { actions += "copy" to it }, onShowQr = { actions += "qr" to it },
                    onExportProfile = { actions += "export" to it },
                    onEdit = {}, onUpdate = {}, onCloseEditor = {}, onSaveEditor = {}, onDelete = {},
                )
            }
        }
    }
}
