package com.lonnnnnng.flclash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
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

class ProfileMoreActivityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realCustomMoreSavesReopensAndRecreatesWithoutChangingSourceOrSelection() {
        assertPortraitDefaultFontConfiguration()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("kclashLocalFile") == "true")
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat /sdcard/Download/1.yaml")
        val bytes = FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }; descriptor.close()
        assertEquals(args.getString("kclashLocalFileSha256"), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        compose.waitUntil(10_000) { repository.currentProfileId.value != null }
        val selected = repository.currentProfileId.value
        val before = runBlocking(Dispatchers.IO) { entry.database().profiles().getAll().map { it.id }.toSet() }
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        val profile = runBlocking(Dispatchers.IO) {
            repository.addCustomProfile("更多页 Activity 验收").also {
                repository.updateLocalProfileContent(it.id, bytes.toString(Charsets.UTF_8))
                repository.updateProfileOverrides(it.id, ProfileProviderOverrides(dns = ProfileDnsOverrides(enable = false)).toJsonString())
            }
        }
        val file = repository.profileFile(profile.id); val modified = file.lastModified()
        try {
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile_card_${profile.id}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_actions_${profile.id}").performTouchInput { longClick() }
            compose.onNodeWithTag("profile_edit_${profile.id}").performClick()
            compose.onNodeWithTag("custom_profile_more").performScrollTo().performClick()
            compose.onNodeWithTag("custom_profile_ntp").performClick()
            applyYaml("enable: false\nserver: time.apple.com\nport: 124")
            saveMore()
            compose.onNodeWithTag("custom_profile_sniffer").performClick()
            applyYaml("enable: false\nskip-domain: []\nsniff:\n  http:\n    ports: [80, 8000-9000]\n    override-destination: false")
            saveMore()
            compose.onNodeWithTag("custom_profile_more_back").performClick()
            compose.onNodeWithTag("custom_profile_back").performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile_card_${profile.id}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_actions_${profile.id}").performTouchInput { longClick() }
            compose.onNodeWithTag("profile_edit_${profile.id}").performClick()
            compose.onNodeWithTag("custom_profile_more").performScrollTo().performClick()
            compose.onNodeWithTag("custom_profile_ntp").performClick()
            compose.onNodeWithTag("more_edit_port").assertTextContains("124", substring = true)
            compose.onNodeWithTag("more_enable_value").assertIsOff()
            compose.onNodeWithTag("profile_more_editor_back").performClick()
            compose.onNodeWithTag("custom_profile_sniffer").performClick()
            compose.onNodeWithTag("more_open_sniff.HTTP").performScrollTo().performClick()
            compose.onNodeWithTag("more_protocol_ports").performClick()
            compose.onNodeWithTag("sniff_HTTP_ports").assertTextContains("8000-9000", substring = true)
            compose.onNodeWithTag("more_value_back").performClick()
            compose.onNodeWithTag("more_protocol_back").performClick()
            compose.onNodeWithTag("profile_more_editor_back").performClick()
            val saved = runBlocking(Dispatchers.IO) { ProfileProviderOverrides.fromJsonString(checkNotNull(entry.database().profiles().getById(profile.id)).overridesJson) }
            assertEquals(false, saved.dns.enable); assertEquals(124, saved.ntp.port)
            assertEquals(false, saved.sniffer.enable); assertNull(saved.sniffer.skipDomain)
            assertEquals(false, saved.sniffer.sniff?.get("HTTP")?.overrideDestination)
            assertEquals(selected, repository.currentProfileId.value)
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
            assertTrue(file.readBytes().contentEquals(bytes)); assertEquals(modified, file.lastModified())
        } finally {
            // 作者 long：只删除本用例创建并核对名称/ID的临时配置，正式库的其他行、当前选择和授权原文件均保留。
            runBlocking(Dispatchers.IO) {
                val row = entry.database().profiles().getById(profile.id)
                if (row != null) { check(profile.id !in before && row.label == "更多页 Activity 验收"); repository.deleteProfile(profile.id) }
                assertEquals(before, entry.database().profiles().getAll().map { it.id }.toSet())
                assertEquals(selected, repository.currentProfileId.value)
            }
        }
    }

    private fun applyYaml(value: String) {
        compose.onNodeWithTag("profile_more_yaml").performClick()
        compose.onNodeWithTag("more_yaml_content").performTextReplacement(value)
        compose.onNodeWithTag("more_yaml_confirm").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("more_yaml_confirm").fetchSemanticsNodes().isEmpty() }
    }
    private fun saveMore() {
        compose.onNodeWithTag("profile_more_editor_save").assertDoesNotExist()
        compose.onNodeWithTag("profile_more_editor_back").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile_more_editor_back").fetchSemanticsNodes().isEmpty() }
    }
}
