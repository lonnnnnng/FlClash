package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
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

/** 作者 long：规则资源管理入口从高级配置打开，排序保存和重建只改本批资源，不触碰Profile正文。 */
class ResourceOrderHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun actualRuleResourcePagePersistsOrderAcrossRecreationWithoutChangingProfiles() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val database = entry.database()
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        val beforeProfiles = database.profiles().getAll()
        val selected = entry.profileRepository().currentProfileId.value
        val beforeRules = entry.rules().all()
        val token = "排序验收-${System.nanoTime()}"
        val rows = mutableListOf<RuleResource>()
        try {
            for (index in 1..2) rows += entry.rules().save("$token-$index", "", "+.example.org\n".toByteArray())
            open()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("rule_resources_sort").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("rule_resources_sort").performClick()
            compose.onNodeWithTag("rule_resources_order_list").performScrollToNode(hasTestTag("rule_resources_order_${rows.last().id}"))
            compose.onNodeWithTag("rule_resources_order_${rows.last().id}").performOrderTouchMove(-1)
            compose.waitUntil(5_000) { runBlocking { entry.rules().all().takeLast(2).map { it.id } == rows.map { it.id }.reversed() } }
            compose.onNodeWithTag("rule_resources_sort_done").performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("rule_resources_page").assertIsDisplayed()
            compose.onNodeWithTag("rule_resources_sort").performClick()
            compose.onNodeWithTag("rule_resources_order_list").performScrollToNode(hasTestTag("rule_resources_order_${rows.last().id}"))
            assertEquals(rows.map { it.id }.reversed(), entry.rules().all().takeLast(2).map { it.id })
            compose.onNodeWithTag("rule_resources_sort_done").performClick()
            compose.onNodeWithTag("rule_resources_back").performClick()
            assertEquals(beforeProfiles, database.profiles().getAll())
            assertEquals(selected, entry.profileRepository().currentProfileId.value)
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        } finally {
            for (row in rows) entry.rules().all().firstOrNull { it.id == row.id && it.label.startsWith(token) }?.let {
                entry.rules().delete(it)
                val file = entry.rules().file(it)
                check(file.name == row.fileName && file.parentFile?.name == "rules")
                file.delete(); File(file.parentFile, it.coreFileName).takeIf { compiled -> compiled != file }?.delete()
            }
            assertEquals(beforeRules, entry.rules().all())
            assertEquals(beforeProfiles, database.profiles().getAll())
        }
    }

    private fun open() {
        compose.onNodeWithTag("nav_tools").performClick()
        compose.onNodeWithTag("tools_item_advanced").performScrollTo().performClick()
        compose.onNodeWithTag("advanced_item_rule_resources").performClick()
    }
}
