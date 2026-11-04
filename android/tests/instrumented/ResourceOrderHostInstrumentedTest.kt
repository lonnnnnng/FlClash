package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：正式页面只排序本次新增的两对资源，回收前核对ID/唯一名；既有目录、Profile正文和选择保持原值。 */
class ResourceOrderHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun bothActualPagesPersistOrderAcrossRecreationWithoutChangingProfiles() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val app = compose.activity.application
        val entry = EntryPointAccessors.fromApplication(app, ConnectionAcceptanceEntryPoint::class.java)
        val common = entry
        assertEquals(CoreRuntimeStatus.STOPPED, common.runtimeManager().state.value)
        val beforeProfiles = common.database().profiles().getAll()
        val selected = common.profileRepository().currentProfileId.value
        val beforeIcons = entry.icons().all(); val beforeRules = entry.rules().all()
        val token = "排序验收-${System.nanoTime()}"
        val icons = mutableListOf<ResourceIconSet>(); val rules = mutableListOf<RuleResource>()
        try {
            for (i in 1..2) {
                icons += entry.icons().importContent("""{"name":"$token-$i","icons":[{"name":"YouTube.png","url":"http://127.0.0.1:9/$i.png"}]}""")
                rules += entry.rules().save("$token-$i", "", "+.example.org\n".toByteArray())
            }
            for (gallery in listOf(true, false)) {
                val tag = if (gallery) "icon_sets" else "rule_resources"
                val ids = if (gallery) icons.map { it.id } else rules.map { it.id }
                open(gallery)
                compose.waitUntil(5_000) { compose.onAllNodesWithTag("${tag}_sort").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("${tag}_sort").performClick()
                compose.onNodeWithTag("${tag}_order_list").performScrollToNode(hasTestTag("${tag}_order_${ids.last()}"))
                compose.onNodeWithTag("${tag}_order_${ids.last()}").performOrderTouchMove(-1)
                compose.waitUntil(5_000) { runBlocking {
                    val now = if (gallery) entry.icons().all().map { it.id } else entry.rules().all().map { it.id }
                    now.takeLast(2) == ids.reversed()
                } }
                compose.onNodeWithTag("${tag}_sort_done").performClick()
                compose.activityRule.scenario.recreate()
                compose.waitForIdle()
                compose.onNodeWithTag("${tag}_page").assertIsDisplayed()
                compose.onNodeWithTag("${tag}_sort").performClick()
                compose.onNodeWithTag("${tag}_order_list").performScrollToNode(hasTestTag("${tag}_order_${ids.last()}"))
                val now = if (gallery) entry.icons().all().map { it.id } else entry.rules().all().map { it.id }
                assertEquals(ids.reversed(), now.takeLast(2))
                compose.onNodeWithTag("${tag}_sort_done").performClick()
                compose.onNodeWithTag("${tag}_back").performClick()
                compose.onNodeWithTag("profile_editor_back").performClick()
            }
            assertEquals(beforeProfiles, common.database().profiles().getAll())
            assertEquals(selected, common.profileRepository().currentProfileId.value)
            assertEquals(CoreRuntimeStatus.STOPPED, common.runtimeManager().state.value)
        } finally {
            for (row in icons) entry.icons().all().firstOrNull { it.id == row.id && it.name.startsWith(token) }?.let { entry.icons().delete(it.id) }
            for (row in rules) entry.rules().all().firstOrNull { it.id == row.id && it.label.startsWith(token) }?.let {
                entry.rules().delete(it)
                val file = entry.rules().file(it)
                check(file.name == row.fileName && file.parentFile?.name == "rules")
                file.delete(); File(file.parentFile, it.coreFileName).takeIf { compiled -> compiled != file }?.delete()
            }
            assertEquals(beforeIcons, entry.icons().all()); assertEquals(beforeRules, entry.rules().all())
            assertEquals(beforeProfiles, common.database().profiles().getAll())
        }
    }
    private fun open(gallery: Boolean) {
        compose.onNodeWithTag("nav_tools").performClick()
        compose.onNodeWithTag("tools_item_resources_settings").performScrollTo().performClick()
        compose.onNodeWithText(if (gallery) "图标集" else "规则资源").performClick()
    }
}
