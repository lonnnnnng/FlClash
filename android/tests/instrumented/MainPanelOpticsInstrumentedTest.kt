package com.lonnnnnng.flclash.ui

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.readAuthorizedProxyProfile
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** 作者 long：只在独立空库用用户授权正文建立本轮临时FILE，实际菜单/VM/Room确认后回读，绝不生成替代YAML或启动服务。 */
class MainPanelOpticsInstrumentedTest {
    @get:Rule(order=0) val permission=GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    @Test fun localMetadataAndManagedRenameSurviveRecreationAndSaveOnlyOnce() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        require(compose.activity.packageName.endsWith(".motion"))
        require(InstrumentationRegistry.getArguments().getString("kclashPanel068")=="true")
        val entry=EntryPointAccessors.fromApplication(compose.activity.application,ConnectionAcceptanceEntryPoint::class.java)
        val repo=entry.profileRepository();repo.profilesLoaded.first{it}
        assertTrue(entry.database().profiles().getAll().isEmpty())
        assertEquals(CoreRuntimeStatus.STOPPED,entry.runtimeManager().state.value)
        val bytes=readAuthorizedProxyProfile()
        val row=repo.addFileProfile("068本地配置",bytes)
        val file=repo.profileFile(row.id)
        val before=Triple(file.length(),file.lastModified(),hash(file.readBytes()))
        var renameId: Long? = null
        fun menu(action:String) {
            val id = if (action.startsWith("profile_rename_")) checkNotNull(renameId) else row.id
            compose.onNodeWithTag("profile_card_$id").performScrollTo().performTouchInput{longClick()}
            compose.onNodeWithTag(action).performClick()
        }
        try {
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.waitUntil(10_000){compose.onAllNodesWithTag("profile_card_${row.id}").fetchSemanticsNodes().isNotEmpty()}
            menu("profile_edit_${row.id}")
            compose.onNodeWithTag("file_profile_editor_label").performTextReplacement("068本地修改")
            compose.onNodeWithTag("file_profile_editor_age_key").performTextReplacement("fixture-private-key")
            compose.onNodeWithTag("file_profile_editor_age_key_visibility").performClick()
            capture("local_edited")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("file_profile_editor_label").assertTextContains("068本地修改")
            compose.onNodeWithTag("file_profile_editor_age_key").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password))
            compose.onNodeWithTag("file_profile_editor_save").performClick()
            compose.waitUntil(15_000){compose.onAllNodesWithTag("file_profile_editor_form").fetchSemanticsNodes().isEmpty()}
            assertEquals("068本地修改",entry.database().profiles().getById(row.id)!!.label)
            assertEquals("fixture-private-key",entry.database().profiles().getById(row.id)!!.ageSecretKey)
            // 作者 long：上游只有CUSTOM提供独立重命名菜单；FILE名称已经由本地编辑验证，不能为测试给它增加多余入口。
            val custom=repo.addCustomProfile("068自定义配置")
            renameId=custom.id
            repo.updateLocalProfileContent(custom.id,bytes.decodeToString())
            compose.waitUntil(10_000){compose.onAllNodesWithTag("profile_card_${custom.id}").fetchSemanticsNodes().isNotEmpty()}
            menu("profile_rename_${custom.id}")
            compose.onNodeWithTag("profile_rename_label").performTextReplacement("  068重命名  ")
            androidx.test.espresso.Espresso.closeSoftKeyboard();capture("rename_draft")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("profile_rename_label").assertTextContains("  068重命名  ")
            compose.onNodeWithTag("profile_rename_save").performClick()
            compose.waitUntil(15_000){compose.onAllNodesWithTag("profile_rename_dialog").fetchSemanticsNodes().isEmpty()}
            assertEquals("068重命名",entry.database().profiles().getById(custom.id)!!.label)
            menu("profile_rename_${custom.id}")
            compose.onNodeWithTag("profile_rename_label").performTextReplacement("取消的名称")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("profile_rename_dialog_cancel").performClick()
            compose.waitUntil(10_000){compose.onAllNodesWithTag("profile_rename_dialog").fetchSemanticsNodes().isEmpty()}
            menu("profile_rename_${custom.id}")
            compose.onNodeWithTag("profile_rename_label").assertTextContains("068重命名")
            compose.onNodeWithTag("profile_rename_dialog_cancel").performClick()
            compose.waitUntil(10_000){compose.onAllNodesWithTag("profile_rename_dialog").fetchSemanticsNodes().isEmpty()}
            capture("rename_saved")
            assertEquals(before,Triple(file.length(),file.lastModified(),hash(file.readBytes())))
        } finally {
            renameId?.let { repo.deleteProfile(it) }
            repo.deleteProfile(row.id)
            assertTrue(entry.database().profiles().getAll().isEmpty())
            assertEquals(CoreRuntimeStatus.STOPPED,entry.runtimeManager().state.value)
        }
    }
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    private fun capture(name:String) {
        compose.waitForIdle();android.os.SystemClock.sleep(400)
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        recordVisualPng("panel_main_$name",ByteArrayOutputStream().also{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray())
    }
}
