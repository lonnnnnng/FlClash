package com.lonnnnnng.flclash.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProviderHostInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun actualProfileProviderOptionsSaveInPlaceSurviveRecreationAndKeepSource() = runBlocking(Dispatchers.IO) {
        assertPortraitDefaultFontConfiguration()
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        val repository = entry.profileRepository()
        val before = entry.database().profiles().getAll()
        val selected = repository.currentProfileId.value
        val resource = before.first { it.type != ProfileType.CUSTOM }.toDomain()
        val source = readAuthorizedProxyProfile().toString(Charsets.UTF_8)
        val label = "Provider页面验收-${System.nanoTime()}"
        val created = repository.addCustomProfile(label)
        try {
            repository.updateLocalProfileContent(created.id, source)
            val groups = ProxyGroupDrafts.read(source).mapIndexed { index, group ->
                if (index == 0) ProxyGroupDrafts.withMembers(group, ProxyGroupDrafts.members(group) + ProxyGroupMember("验收资源", true)) else group
            }
            repository.saveCustomProxyGroups(created.id, ProxyGroupsSave(groups, mapOf("验收资源" to resource.id)), source) { true }
            val file = repository.profileFile(created.id)
            val bytes = file.readBytes(); val modified = file.lastModified()
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile_card_${created.id}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_card_${created.id}").performTouchInput { longClick() }
            compose.onNodeWithText("编辑").performClick()
            compose.onNodeWithTag("custom_profile_resources").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile_provider_edit_验收资源").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_provider_edit_验收资源").performScrollTo().performClick()
            compose.onNodeWithTag("provider_option_add").performScrollTo().performClick()
            compose.onNodeWithTag("provider_option_pick_TIMEOUT").performClick()
            compose.onNodeWithTag("provider_option_picker_back").performClick()
            compose.onNodeWithTag("provider_options_save").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("provider_options_save").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("profile_providers_page").assertIsDisplayed()
            val persisted = entry.database().profiles().getById(created.id)!!.toDomain()
            assertEquals(5000, ProfileProviderOverrides.fromJsonString(persisted.overridesJson).proxyProviders["验收资源"]?.timeout)
            assertArrayEquals(bytes, file.readBytes()); assertEquals(modified, file.lastModified())
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile_provider_edit_验收资源").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_provider_edit_验收资源").performScrollTo().performClick()
            compose.onNodeWithTag("provider_option_TIMEOUT").performScrollTo().assertTextContains("5000")
            compose.onNodeWithTag("provider_options_back").performClick()
            compose.onNodeWithTag("profile_providers_back").performClick()
            compose.onNodeWithTag("custom_profile_back").performClick()
            assertEquals(selected, repository.currentProfileId.value)
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        } finally {
            // 作者 long：只删除本次创建且 ID/类型/名称仍一致的临时配置，所有既有行及当前选择必须保持。
            val row = entry.database().profiles().getById(created.id)
            check(row == null || row.type == ProfileType.CUSTOM && row.label == label)
            if (row != null) repository.deleteProfile(created.id)
            assertEquals(before, entry.database().profiles().getAll())
            assertEquals(selected, repository.currentProfileId.value)
        }
    }
}
