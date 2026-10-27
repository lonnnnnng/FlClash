package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.data.BackupRestoreScope
import com.lonnnnnng.flclash.data.BackupRestoreStrategy
import com.lonnnnnng.flclash.data.WebDavCredentials
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：WebDAV 页面回归只验证用户可见的保存、测试、覆盖确认和下载恢复入口，
 * 不把真实账号或远程服务写入测试；协议和仓库边界由各自的集成测试验证。
 */
@RunWith(AndroidJUnit4::class)
class WebDavUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun webDavDialogForwardsExplicitActionsAndRestoreOptions() {
        var saved: WebDavCredentials? = null
        var probed: WebDavCredentials? = null
        var uploaded: Pair<WebDavCredentials, BackupRestoreScope>? = null
        var downloaded: WebDavCredentials? = null

        composeRule.setContent {
            KClashTheme {
                WebDavBackupDialog(
                    initial = null,
                    busy = false,
                    onDismiss = {},
                    onSave = { saved = it },
                    onClear = {},
                    onProbe = { probed = it },
                    onUpload = { credentials, scope -> uploaded = credentials to scope },
                    onDownload = { downloaded = it },
                )
            }
        }

        composeRule.onNodeWithTag("webdav_base_url").performTextInput("https://dav.example.test/kclash")
        composeRule.onNodeWithTag("webdav_username").performTextInput("alice")
        composeRule.onNodeWithTag("webdav_password").performTextInput("secret")
        composeRule.onNodeWithTag("webdav_file_name").performTextInput("-test.zip")
        composeRule.onNodeWithTag("webdav_save").performClick()
        composeRule.onNodeWithTag("webdav_probe").performClick()
        composeRule.onNodeWithTag("webdav_upload").performClick()
        composeRule.onNodeWithTag("webdav_upload_confirm").performClick()
        composeRule.onNodeWithTag("webdav_download").performClick()

        assertNotNull(saved)
        assertEquals("alice", saved?.username)
        assertEquals("secret", saved?.password)
        assertEquals(saved, probed)
        assertEquals(saved, uploaded?.first)
        assertEquals(BackupRestoreScope.ALL_DATA, uploaded?.second)
        assertEquals(saved, downloaded)
        assertTrue(saved?.fileName?.isNotBlank() == true)
    }

    @Test
    fun webDavRestoreOptionsExposeCompatibleAndOverrideStrategies() {
        var selected: Pair<BackupRestoreScope, BackupRestoreStrategy>? = null
        composeRule.setContent {
            KClashTheme {
                BackupRestoreOptionsDialog(
                    title = "从 WebDAV 恢复",
                    confirmLabel = "下载并恢复",
                    onDismiss = {},
                    onConfirm = { scope, strategy -> selected = scope to strategy },
                )
            }
        }

        composeRule.onNodeWithTag("backup_option_仅恢复配置文件").performClick()
        composeRule.onNodeWithTag("backup_option_覆盖：替换当前数据").performClick()
        composeRule.onNodeWithTag("backup_option_覆盖：替换当前数据").assertExists()
        composeRule.onNodeWithTag("backup_option_仅恢复配置文件").assertExists()
        composeRule.onNodeWithTag("backup_option_仅恢复配置文件").performClick()
        composeRule.onNodeWithTag("backup_option_覆盖：替换当前数据").performClick()
        composeRule.onNodeWithText("下载并恢复").performClick()

        assertEquals(BackupRestoreScope.PROFILES_ONLY, selected?.first)
        assertEquals(BackupRestoreStrategy.OVERRIDE, selected?.second)
    }
}
