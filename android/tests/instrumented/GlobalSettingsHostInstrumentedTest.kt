package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
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
            capture("global_dns_empty")
            if (originalDns.listen == null) {
                compose.onNodeWithTag("global_dns_add").assertIsDisplayed()
                compose.onNodeWithTag("global_dns_add").performClick()
                capture("global_dns_add_options")
                compose.onNodeWithTag("global_dns_add_listen").performClick()
            } else compose.onNodeWithTag("global_dns_listen").performClick()
            compose.onNodeWithTag("global_dns_input").assertExists()
            capture("global_dns_listen_editor")
            compose.onNodeWithTag("global_dns_input").performTextReplacement("")
            compose.onNodeWithTag("global_dns_save").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("global_dns_editor").fetchSemanticsNodes().isEmpty() }
            assertEquals("", entry.globalDnsSettingsStore().read().listen)
            capture("global_dns_actual")
            compose.activityRule.scenario.recreate(); compose.waitForIdle()
            compose.onNodeWithTag("page_global_dns").assertExists()
            compose.onNodeWithTag("global_dns_back").performClick()
            compose.onNodeWithTag("advanced_item_rules").performClick()
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("global_rules_add") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("global_rules_empty_title").assertIsDisplayed()
            val illustrationBounds = compose.onNodeWithTag("global_rules_empty_illustration").fetchSemanticsNode().boundsInRoot
            val pixelsPerDp = compose.activity.resources.displayMetrics.density
            assertEquals(144f, illustrationBounds.width / pixelsPerDp, 1f)
            assertEquals(144f, illustrationBounds.height / pixelsPerDp, 1f)
            capture("global_rules_empty")
            compose.onNodeWithTag("global_rules_presets").performClick()
            compose.onNodeWithTag("extension_presets_sheet").assertIsDisplayed()
            compose.onNodeWithTag("extension_preset_confirm").assertDoesNotExist()
            capture("global_rules_presets")
            compose.onNodeWithTag("extension_preset_0").performClick()
            compose.onNodeWithTag("extension_preset_confirm").assertIsEnabled()
            capture("global_rules_presets_selected")
            compose.onNodeWithTag("extension_preset_0").performClick()
            compose.onNodeWithTag("extension_preset_confirm").assertDoesNotExist()
            compose.onNodeWithTag("extension_presets_close").performClick()
            compose.onNodeWithTag("global_rules_add").assertExists()
            compose.onNodeWithTag("global_rules_add").performClick()
            compose.onNodeWithTag("extension_rule_content").assertExists()
            compose.onNodeWithTag("extension_rule_basic_section").assertIsDisplayed()
            compose.onNodeWithTag("extension_rule_type").assertTextContains("DOMAIN")
            compose.onNodeWithTag("extension_rule_target").assertTextContains("DIRECT")
            compose.onNodeWithTag("extension_rule_type").performClick()
            compose.onNodeWithTag("extension_rule_option_DOMAIN-SUFFIX").performClick()
            compose.onNodeWithTag("extension_rule_type").assertTextContains("DOMAIN-SUFFIX")
            compose.onNodeWithTag("extension_rule_type").performClick()
            compose.onNodeWithTag("extension_rule_option_DOMAIN").performClick()
            compose.onNodeWithTag("extension_rule_type").assertTextContains("DOMAIN")
            capture("global_rules_add_form")
            compose.onNodeWithTag("extension_rule_content").performClick()
            compose.onNodeWithTag("extension_rule_content").performTextInput("discarded.test")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.waitForIdle()
            assertTrue(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            compose.waitUntil(5_000) { compose.onAllNodesWithText("放弃未保存修改？").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("继续编辑").performClick()
            compose.onNodeWithTag("extension_rule_content").assertTextEquals("discarded.test")
            compose.onNodeWithTag("extension_rule_form_close").performClick()
            compose.onNodeWithText("放弃未保存修改？").assertIsDisplayed()
            compose.onNodeWithText("放弃").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("extension_rule_confirm").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("global_rules_add").performClick()
            assertEquals("", compose.onNodeWithTag("extension_rule_content").fetchSemanticsNode()
                .config[SemanticsProperties.EditableText].text)
            compose.onNodeWithTag("extension_rule_content").performTextInput("roadmap-acceptance.invalid")
            compose.onNodeWithTag("extension_rule_confirm").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("page_global_rules").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("extension_rule_confirm").fetchSemanticsNodes().isEmpty() }
            val saved = repository.globalExtensionRules()
            assertEquals(originalRules.size + 1, saved.size)
            assertEquals("DOMAIN,roadmap-acceptance.invalid,DIRECT", saved.last().value)
            // 作者 long：真实 Room 事务拒绝旧目录写入，避免另一个编辑页把刚新增的规则丢掉。
            assertTrue(runCatching { repository.compareAndSaveGlobalExtensionRules(originalRules, emptyList()) }.isFailure)
            assertEquals(saved, repository.globalExtensionRules())
            compose.activityRule.scenario.recreate(); compose.waitForIdle()
            // 作者 long：保存后的编辑Sheet要完成退场才算回到规则列表，避免动画中的预览被误判为重复记录。
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("extension_rule_confirm").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("global_rules_search").performTextInput("roadmap-acceptance.invalid")
            compose.onNodeWithTag("global_rule_${saved.last().id}").assertIsDisplayed()
                .assertTextContains("roadmap-acceptance.invalid")
            capture("global_rules_actual")
            assertEquals(before, entry.database().profiles().getAll())
            assertEquals(selected, repository.currentProfileId.value)
            assertEquals(files, before.associate { row -> row.id to repository.profileFile(row.id).let { Triple(it.name, it.length(), it.lastModified()) } })
            assertEquals(CoreRuntimeStatus.STOPPED, manager.state.value)
        } finally {
            // 作者 long：独立规则页现在有持久会话；先结束本次测试窗口，避免还原正式目录后留下会再次自动打开的测试草稿。
            lateinit var rulesController: GlobalRulesController
            compose.runOnIdle {
                rulesController = androidx.lifecycle.ViewModelProvider(compose.activity)[ProfilesViewModel::class.java].globalRules
                rulesController.close { }
            }
            compose.waitUntil(15_000) { rulesController.state.value.draft == null }
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
