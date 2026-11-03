package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：正式入口只创建本用例的脚本，验证草稿重建和保存回读，原配置、绑定与脚本全部保留。 */
class ScriptsPageHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun actualScriptPageRetainsDraftAcrossRecreationAndReturnsToLibraryAfterSave() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val db = entry.database()
        val repo = entry.scriptRepository()
        val profiles = db.profiles().getAll()
        val selected = entry.profileRepository().currentProfileId.value
        val files = profiles.associate { it.id to entry.profileRepository().profileFile(it.id).let { f -> Triple(f.name, f.length(), f.lastModified()) } }
        val scripts = db.scripts().getAll()
        val name = "script-ui-${System.nanoTime()}"
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        try {
            compose.onNodeWithTag("nav_tools").performClick()
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_advanced"))
            compose.onNodeWithTag("tools_item_advanced").performClick()
            compose.onNodeWithTag("advanced_item_scripts").performClick()
            compose.onNodeWithTag("scripts_page").assertExists()
            compose.onNodeWithTag("scripts_add").performClick()
            compose.onNodeWithText("导入远程脚本").performClick()
            compose.onNodeWithTag("script_remote_url").performTextInput("https://example.test/retained.js")
            compose.activityRule.scenario.recreate(); compose.waitForIdle()
            compose.onNodeWithTag("script_remote_url").assertTextContains("https://example.test/retained.js", substring = true)
            compose.onNodeWithTag("script_remote_back").performClick()
            compose.onNodeWithTag("scripts_add").performClick()
            compose.onNodeWithTag("scripts_create").performClick()
            compose.onNodeWithTag("script_label").performTextReplacement(name)
            compose.onNodeWithTag("script_content").performTextReplacement("const main = config => config; // retained")
            compose.activityRule.scenario.recreate(); compose.waitForIdle()
            compose.onNodeWithTag("script_label").assertTextContains(name, substring = true)
            compose.onNodeWithTag("script_content").assertTextContains("// retained", substring = true)
            compose.onNodeWithTag("script_save").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("script_editor_page").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("scripts_page").assertExists()
            val created = db.scripts().getAll().single { it.label == name }
            assertEquals("const main = config => config; // retained", repo.readContent(created.id))
            assertEquals(profiles, db.profiles().getAll())
            assertEquals(selected, entry.profileRepository().currentProfileId.value)
            assertEquals(files, profiles.associate { it.id to entry.profileRepository().profileFile(it.id).let { f -> Triple(f.name, f.length(), f.lastModified()) } })
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        } finally {
            db.scripts().getAll().filter { it.label == name && scripts.none { old -> old.id == it.id } }.forEach { repo.delete(it.id) }
            assertEquals(scripts, db.scripts().getAll())
        }
    }
}
