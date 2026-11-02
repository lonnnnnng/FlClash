package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.Script
import com.lonnnnnng.flclash.service.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：扩展页整版在隔离状态下操作，正式 Profile 与核心服务不参与页面测试。 */
class ProfileExtensionEditorInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private var screen by mutableStateOf(ProfileExtensionUiState())
    private var dark by mutableStateOf(false)
    private var applies = 0
    private var exits = 0
    private var scriptsOpened = 0

    @Test fun rulesFormReorderAndBatchDeletePersistTheDisplayedOrder() {
        assertPortraitDefaultFontConfiguration()
        render()
        compose.onNodeWithTag("extension_rule_add").performScrollTo().performClick()
        compose.onNodeWithTag("extension_rule_content").performTextInput("example.test")
        compose.onNodeWithTag("extension_rule_confirm").performClick()
        compose.runOnIdle { assertEquals("DOMAIN-SUFFIX,example.test,DIRECT", screen.settings.rules.last().value); assertEquals(2, screen.settings.rules.size) }
        compose.onNodeWithTag("extension_sort").performScrollTo().performClick()
        val id = screen.settings.rules.last().id
        compose.onNodeWithTag("extension_up_$id").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(id, screen.settings.rules.first().id) }
        compose.onNodeWithTag("extension_rule_select_$id").performScrollTo().performClick()
        compose.onNodeWithTag("extension_select_all").performScrollTo().performClick()
        compose.onNodeWithText("已选 2 项").assertIsDisplayed()
        compose.onNodeWithTag("extension_delete_selected").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.runOnIdle { assertEquals("所选规则均应删除", emptyList<ExtensionRule>(), screen.settings.rules) }
    }

    @Test fun globalControlsSearchDisableAndEditKeepStableRuleIdentity() {
        assertPortraitDefaultFontConfiguration()
        render()
        compose.onNodeWithTag("extension_global_control").performScrollTo().performClick()
        compose.onNodeWithTag("extension_global_search").performTextInput("global.test")
        compose.onNodeWithTag("extension_global_toggle_g").performClick()
        compose.runOnIdle { assertEquals(setOf("g"), screen.settings.disabledGlobalRuleIds) }
        compose.onNodeWithTag("extension_global_edit").performScrollTo().performClick()
        compose.onNodeWithTag("extension_rule_g").performScrollTo().performClick()
        compose.onNodeWithTag("extension_rule_content").performTextReplacement("changed.test")
        compose.onNodeWithTag("extension_rule_confirm").performClick()
        compose.runOnIdle {
            assertEquals("g", screen.globalRules.single().id)
            assertEquals("DOMAIN,changed.test,DIRECT", screen.globalRules.single().value)
            assertEquals(setOf("g"), screen.settings.disabledGlobalRuleIds)
        }
    }

    @Test fun standardScriptAndAppliedStatesRenderAcrossThemes() {
        assertPortraitDefaultFontConfiguration()
        render()
        for (theme in listOf("light", "dark")) {
            compose.runOnIdle { dark = theme == "dark"; screen = screen.copy(mode = "standard", applied = false, error = null) }
            compose.onNodeWithTag("profile_extension_mode_standard").performScrollTo()
            capture("extension_standard_$theme")
            compose.onNodeWithTag("extension_match_target").performScrollTo().performClick()
            compose.onNodeWithTag("extension_target_Main").performClick()
            compose.runOnIdle { assertEquals("Main", screen.profile?.matchTarget) }
            compose.onNodeWithTag("profile_extension_mode_script").performScrollTo().performClick()
            compose.onNodeWithTag("extension_script_9").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(9L, screen.profile?.scriptId) }
            capture("extension_script_$theme")
            compose.onNodeWithTag("extension_script_9").performClick()
            compose.runOnIdle { assertNull(screen.profile?.scriptId) }
            compose.onNodeWithTag("extension_manage_scripts").performClick()
            compose.onNodeWithTag("profile_extension_apply").performClick()
            compose.onNodeWithTag("profile_extension_apply").assertDoesNotExist()
            capture("extension_applied_$theme")
            compose.runOnIdle { screen = screen.copy(error = "配置切换失败，已恢复原配置", applied = false) }
            compose.onNodeWithTag("profile_extension_error").performScrollTo()
            capture("extension_retry_$theme")
        }
        assertEquals(2, applies); assertEquals(2, scriptsOpened)
    }

    @Test fun presetsKeepLogicalRuleAndNoncurrentProfileHasNoApplyButton() {
        assertPortraitDefaultFontConfiguration()
        render(current = false)
        compose.onNodeWithTag("profile_extension_apply").assertDoesNotExist()
        compose.onNodeWithTag("extension_presets").performScrollTo().performClick()
        compose.onNodeWithText("阻止 QUIC").performClick()
        compose.onNodeWithTag("extension_preset_confirm").performClick()
        compose.runOnIdle { assertEquals("AND,((NETWORK,UDP),(DST-PORT,443)),REJECT-DROP", screen.settings.rules.last().value) }
        compose.onNodeWithTag("profile_extension_back").performClick()
        assertEquals(1, exits); assertEquals(0, applies)
    }

    private fun render(current: Boolean = true) {
        screen = ProfileExtensionUiState(Profile.remote(1, "https://example.test/sub", "家庭与出行"),
            settings = ProfileExtensionOverrides(true, listOf(ExtensionRule("r", "DOMAIN,local.test,DIRECT"))),
            globalRules = listOf(ExtensionRule("g", "DOMAIN,global.test,DIRECT")), targets = listOf("DIRECT", "REJECT", "Main", "Local"), isCurrent = current)
        compose.setContent { KClashTheme(darkTheme = dark) {
            ProfileExtensionEditor(screen, listOf(Script(9, "工作配置脚本")), onDismiss = { exits++ },
                onApply = { applies++; screen = screen.copy(applied = true) }, onMode = { screen = screen.copy(mode = it) },
                onScript = { screen = screen.copy(profile = screen.profile?.copy(scriptId = it)) },
                onTarget = { screen = screen.copy(profile = screen.profile?.copy(matchTarget = it)) },
                onRules = { screen = screen.copy(settings = screen.settings.copy(rules = it)) },
                onGlobalRules = { screen = screen.copy(globalRules = it) },
                onDisabled = { screen = screen.copy(settings = screen.settings.copy(disabledGlobalRuleIds = it)) },
                onOpenScripts = { scriptsOpened++ }, onRetry = {}, onSkipApply = { exits++ })
        } }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
