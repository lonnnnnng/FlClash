package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ExtensionRule
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：正式工具入口验收只修改并恢复全局偏好，配置行、选择和正文必须保持原样。 */
class GlobalSettingsHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun actualGlobalDnsAndRulesPersistAcrossRecreationWithoutChangingProfiles() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
        val manager = entry.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        val repository = entry.profileRepository()
        val before = entry.database().profiles().getAll()
        val selected = repository.currentProfileId.value
        val files = before.associate { row -> row.id to repository.profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } }
        val originalDns = entry.globalDnsSettingsStore().read()
        val originalRules = repository.globalExtensionRules()
        try {
            compose.onNodeWithTag("nav_tools").performClick()
            compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_advanced"))
            compose.onNodeWithTag("tools_item_advanced").performClick()
            compose.onNodeWithTag("advanced_item_dns").performClick()
            if (originalDns.listen == null) {
                compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("global_dns_add"))
                compose.onNodeWithTag("global_dns_add").performClick()
                compose.onNodeWithTag("global_dns_add_listen").performClick()
            } else compose.onNodeWithTag("global_dns_listen").performClick()
            compose.onNodeWithTag("global_dns_input").performTextReplacement("")
            compose.onNodeWithTag("global_dns_save").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("global_dns_editor").fetchSemanticsNodes().isEmpty() }
            assertEquals("", entry.globalDnsSettingsStore().read().listen)
            capture("global_dns_actual")
            compose.activityRule.scenario.recreate(); compose.waitForIdle()
            compose.onNodeWithTag("page_global_dns").assertExists()
            compose.onNodeWithTag("tools_subpage_back").performClick()
            compose.onNodeWithTag("advanced_item_rules").performClick()
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("global_rules_add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("global_rules_add").performClick()
            compose.onNodeWithTag("extension_rule_content").performTextInput("roadmap-acceptance.invalid")
            compose.onNodeWithTag("extension_rule_confirm").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("page_global_rules").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("extension_rule_confirm").fetchSemanticsNodes().isEmpty() }
            val saved = repository.globalExtensionRules()
            assertEquals(originalRules.size + 1, saved.size)
            assertTrue(saved.last().value.contains("roadmap-acceptance.invalid"))
            // 作者 long：真实 Room 事务拒绝旧目录写入，避免另一个编辑页把刚新增的规则丢掉。
            assertTrue(runCatching { repository.compareAndSaveGlobalExtensionRules(originalRules, emptyList()) }.isFailure)
            assertEquals(saved, repository.globalExtensionRules())
            compose.activityRule.scenario.recreate(); compose.waitForIdle()
            compose.onNodeWithTag("global_rules_search").performTextInput("roadmap-acceptance.invalid")
            compose.onNodeWithText(saved.last().value).assertIsDisplayed()
            capture("global_rules_actual")
            assertEquals(before, entry.database().profiles().getAll())
            assertEquals(selected, repository.currentProfileId.value)
            assertEquals(files, before.associate { row -> row.id to repository.profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } })
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        } finally {
            repository.compareAndSaveGlobalExtensionRules(repository.globalExtensionRules(), originalRules)
            manager.updateGlobalDns(entry.globalDnsSettingsStore().read(), originalDns).getOrThrow()
            selected?.let { manager.applyCurrentProfile(it) }
            assertEquals(originalRules, repository.globalExtensionRules())
            assertEquals(originalDns, entry.globalDnsSettingsStore().read())
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(450)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }
}
