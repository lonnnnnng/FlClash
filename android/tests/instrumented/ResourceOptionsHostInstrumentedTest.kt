package com.lonnnnnng.flclash.ui

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：仅独立motion包使用本例图标和脚本；真实入口保存本地名称，脚本改源先验证错误与重建，退出后原目录逐项保持。 */
class ResourceOptionsHostInstrumentedTest {
    @get:Rule(order=0) val permission=GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    @Test fun newRemoteScriptRejectsInvalidUrlAndRetainsDraftAfterRecreation() = runBlocking {
        prepareTouchVisualCapture()
        assertTrue(compose.activity.packageName.endsWith(".motion"))
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val db = entry.database()
        val beforeScripts = db.scripts().getAll()
        val beforeProfiles = db.profiles().getAll()
        compose.onNodeWithTag("nav_tools").performClick()
        compose.onNodeWithTag("tools_item_advanced").performScrollTo().performClick()
        compose.onNodeWithTag("advanced_item_scripts").performClick()
        compose.onNodeWithTag("scripts_add").performClick()
        compose.onNodeWithText("导入远程脚本").performClick()
        compose.onNodeWithTag("script_remote_url").performTextInput("file:///invalid")
        compose.onNodeWithTag("script_remote_submit").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("script_remote_error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("script_remote_url").assertRawInputEquals("file:///invalid")
        val draft = "https://example.invalid/retained.js?raw=a%2Fb"
        compose.onNodeWithTag("script_remote_url").performTextReplacement(draft)
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        compose.onNodeWithTag("script_remote_url").assertRawInputEquals(draft)
        compose.onNodeWithTag("script_remote_back").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("script_remote_page").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("scripts_add").performClick()
        compose.onNodeWithText("导入远程脚本").performClick()
        compose.onNodeWithTag("script_remote_url").assertRawInputEquals("")
        compose.onNodeWithTag("script_remote_back").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("script_remote_page").fetchSemanticsNodes().isEmpty() }
        assertEquals(beforeScripts, db.scripts().getAll())
        assertEquals(beforeProfiles, db.profiles().getAll())
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
    }
    @Test fun realOptionsPersistNameAndScriptDraftSurvivesRecreationAndCancel()=runBlocking {
        prepareTouchVisualCapture()
        assertTrue(compose.activity.packageName.endsWith(".motion"))
        val e=EntryPointAccessors.fromApplication(compose.activity.application,ConnectionAcceptanceEntryPoint::class.java)
        // 作者 long：夹具只准备资源，不借用会自动应用当前配置的生产写入器；实际页面仍使用原DI与VM，空库不能被测试偷偷导入配置。
        val db=e.database();val scripts=ScriptRepository(compose.activity.application,db);val icons=e.icons()
        val profiles=db.profiles().getAll();val beforeScripts=db.scripts().getAll();val beforeIcons=icons.all()
        assertEquals(CoreRuntimeStatus.STOPPED,e.runtimeManager().state.value)
        val token="vis073-${System.nanoTime()}"
        val icon=icons.importContent("""{"name":"$token","icons":[{"name":"Fixture","url":"https://example.invalid/icon.svg"}]}""")
        val local=scripts.create(token,"const main = config => config;")
        val remote=local.copy(url="file:///fixture/source.js")
        db.scripts().update(remote.toEntity())
        try {
            compose.onNodeWithTag("nav_tools").performClick()
            compose.onNodeWithTag("tools_item_resources_settings").performScrollTo().performClick()
            compose.onNodeWithText("图标集").performClick()
            compose.onNodeWithTag("icon_set_${icon.id}").performTouchInput { longClick() }
            compose.onNodeWithText("选项").performClick()
            compose.onNodeWithTag("icon_set_name").performTextReplacement("$token-renamed")
            compose.onNodeWithTag("icon_set_name").performImeAction()
            compose.waitUntil(10000){compose.onAllNodesWithTag("icon_set_options_dialog").fetchSemanticsNodes().isEmpty()}
            assertEquals("$token-renamed",icons.all().single{it.id==icon.id}.name)
            compose.activityRule.scenario.recreate();compose.waitForIdle()
            compose.onNodeWithTag("icon_set_${icon.id}").performTouchInput { longClick() }
            compose.onNodeWithText("选项").performClick()
            compose.onNodeWithTag("icon_set_name").assertTextEquals("$token-renamed")
            compose.onNodeWithTag("icon_set_cancel").performClick()
            compose.waitUntil(5000){compose.onAllNodesWithTag("icon_set_options_dialog").fetchSemanticsNodes().isEmpty()}
            compose.onNodeWithTag("icon_sets_back").performClick()
            compose.onNodeWithTag("profile_editor_back").performClick()
            compose.onNodeWithTag("tools_item_advanced").performScrollTo().performClick()
            compose.onNodeWithTag("advanced_item_scripts").performClick()
            compose.onNodeWithTag("script_menu_${remote.id}").performClick()
            compose.onNodeWithTag("script_url_${remote.id}").performClick()
            compose.onNodeWithTag("script_remote_url").performTextReplacement("file:///invalid")
            compose.onNodeWithTag("script_remote_submit").performClick()
            compose.waitUntil(10000){compose.onAllNodesWithTag("script_remote_error").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("script_remote_url").assertTextEquals("file:///invalid")
            val draft="https://example.invalid/a/long-script.js?raw=a%2Fb"
            compose.onNodeWithTag("script_remote_url").performTextReplacement(draft)
            compose.activityRule.scenario.recreate();compose.waitForIdle()
            compose.onNodeWithTag("script_remote_url").assertRawInputEquals(draft)
            compose.onNodeWithTag("script_remote_back").performClick()
            compose.waitUntil(5000){compose.onAllNodesWithTag("script_remote_page").fetchSemanticsNodes().isEmpty()}
            assertEquals(remote.toEntity(),db.scripts().getById(remote.id))
            assertEquals("const main = config => config;",scripts.readContent(remote.id))
            compose.onNodeWithTag("script_menu_${remote.id}").performClick()
            compose.onNodeWithTag("script_url_${remote.id}").performClick()
            compose.onNodeWithTag("script_remote_url").assertRawInputEquals(remote.url)
            compose.onNodeWithTag("script_remote_back").performClick()
            compose.waitUntil(5000){compose.onAllNodesWithTag("script_remote_page").fetchSemanticsNodes().isEmpty()}
        } finally {
            icons.all().firstOrNull{it.id==icon.id && it.name.startsWith(token)}?.let{icons.delete(it.id)}
            db.scripts().getById(remote.id)?.takeIf{it.label==token}?.let{scripts.delete(it.id)}
            assertEquals(beforeIcons,icons.all());assertEquals(beforeScripts,db.scripts().getAll())
            assertEquals(profiles,db.profiles().getAll());assertEquals(CoreRuntimeStatus.STOPPED,e.runtimeManager().state.value)
        }
    }
}
