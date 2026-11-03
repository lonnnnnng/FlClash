package com.lonnnnnng.flclash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.CustomRules
import com.lonnnnnng.flclash.data.ProxyGroupDrafts
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ProfileDnsOverrides
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import com.lonnnnnng.flclash.ui.assertPortraitDefaultFontConfiguration
import dagger.hilt.android.EntryPointAccessors
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class CustomRulesActivityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realCustomRulesAutosaveAndRecreateKeepOtherSectionsAndCurrentProfile() {
        assertPortraitDefaultFontConfiguration()
        val args = InstrumentationRegistry.getArguments(); assumeTrue(args.getString("kclashLocalFile") == "true")
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat /sdcard/Download/1.yaml")
        val bytes = FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }; descriptor.close()
        assertEquals(args.getString("kclashLocalFileSha256"), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val source = bytes.toString(Charsets.UTF_8)
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        compose.waitUntil(10_000) { repository.currentProfileId.value != null }
        val selected = repository.currentProfileId.value
        val before = runBlocking(Dispatchers.IO) { entry.database().profiles().getAll().map { it.id }.toSet() }
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        val profile = runBlocking(Dispatchers.IO) { repository.addCustomProfile("规则页 Activity 验收").also {
            repository.updateLocalProfileContent(it.id, source)
            repository.updateProfileOverrides(it.id, ProfileProviderOverrides(dns = ProfileDnsOverrides(enable = false)).toJsonString())
        } }
        try {
            fun open() {
                compose.onNodeWithTag("nav_profiles").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile_card_${profile.id}").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("profile_actions_${profile.id}").performTouchInput { longClick() }
                compose.onNodeWithTag("profile_edit_${profile.id}").performClick()
                compose.onNodeWithTag("custom_profile_rules").performScrollTo().performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("custom_rules_add").fetchSemanticsNodes().isNotEmpty() }
            }
            open()
            compose.onNodeWithTag("custom_rules_add").performClick()
            compose.onNodeWithTag("custom_rule_payload").performTextInput("rules-activity.test")
            compose.onNodeWithTag("custom_rule_confirm").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("custom_rule_confirm").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("custom_rules_back").performClick(); compose.onNodeWithTag("custom_profile_back").performClick()
            compose.activityRule.scenario.recreate(); open()
            compose.onNodeWithTag("custom_rules_search_toggle").performClick(); compose.onNodeWithTag("custom_rules_search").performTextInput("rules-activity.test")
            compose.onNodeWithTag("custom_rule_edit_0").assertTextContains("rules-activity.test", substring = true)
            val updated = repository.profileFile(profile.id).readText()
            val oldRules = CustomRules.fromSource(source); val newRules = CustomRules.fromSource(updated)
            assertTrue(newRules.dropLast(1) == oldRules); assertEquals("DOMAIN,rules-activity.test,DIRECT", newRules.last())
            val oldRoot = ProxyGroupDrafts.root(source).apply { remove("rules") }; val newRoot = ProxyGroupDrafts.root(updated).apply { remove("rules") }
            assertTrue("其他分区不得被规则编辑修改", oldRoot == newRoot)
            val saved = runBlocking(Dispatchers.IO) { checkNotNull(entry.database().profiles().getById(profile.id)) }
            assertEquals(false, ProfileProviderOverrides.fromJsonString(saved.overridesJson).dns.enable)
            assertEquals(selected, repository.currentProfileId.value); assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        } finally {
            // 作者 long：授权原文件只读；按本用例记录的 ID/名称回收临时配置，不触碰正式配置及当前选择。
            runBlocking(Dispatchers.IO) {
                val row = entry.database().profiles().getById(profile.id)
                if (row != null) { check(profile.id !in before && row.label == "规则页 Activity 验收"); repository.deleteProfile(profile.id) }
                assertEquals(before, entry.database().profiles().getAll().map { it.id }.toSet()); assertEquals(selected, repository.currentProfileId.value)
            }
        }
    }
}
