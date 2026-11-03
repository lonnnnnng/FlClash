package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：正式宿主只验证新入口和核心转换，临时规则文件用唯一名回收，用户配置和列表不参与写入。 */
class RuleResourcesHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun actualToolsEntryAndNativeCompilerWorkWithoutStartingVpn() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        val rowsBefore = entry.database().profiles().getAll()
        compose.onNodeWithTag("nav_tools").performClick()
        compose.onNodeWithTag("tools_item_resources").performScrollTo().performClick()
        compose.onNodeWithTag("resources_page").assertIsDisplayed()
        compose.onNodeWithTag("resources_back").performClick()
        compose.onNodeWithTag("tools_item_resources_settings").performScrollTo().performClick()
        compose.onNodeWithText("规则资源").performClick()
        compose.onNodeWithTag("rule_resources_page").assertIsDisplayed()
        compose.onNodeWithTag("rule_resources_back").performClick()
        val directory = File(entry.paths().homeDir, "providers/rules").apply { mkdirs() }
        val file = File(directory, "acceptance-" + java.util.UUID.randomUUID().toString() + ".rules")
        try {
            file.writeText("+.example.com\n")
            val info = entry.runtimeManager().compileRuleResource(file.name)
            assertEquals("domain", info.behavior); assertEquals("text", info.format)
            val compiled = File(directory, file.name + ".mrs")
            assertTrue(compiled.isFile)
            assertTrue(entry.runtimeManager().previewRuleResource(compiled.path).contains("example.com"))
            val mrs = entry.runtimeManager().compileRuleResource(compiled.name)
            assertEquals("mrs", mrs.format); assertEquals("domain", mrs.behavior)
            file.writeText("invalid/name\n")
            assertTrue(runCatching { entry.runtimeManager().compileRuleResource(file.name) }.isFailure)
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
            assertEquals(rowsBefore, entry.database().profiles().getAll())
        } finally {
            check(file.parentFile == directory && file.name.startsWith("acceptance-"))
            file.delete(); File(directory, file.name + ".mrs").delete()
        }
    }
}
