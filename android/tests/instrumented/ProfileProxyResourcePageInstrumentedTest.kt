package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfileProxyResourcePageInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun missingBindingCanChooseProfileInBothThemesAndKeepAlias() {
        assertPortraitDefaultFontConfiguration()
        val resource = Profile.remote(122, "", "本地节点资源").copy(type = ProfileType.FILE)
        val source = ProxyGroupDrafts.root(readAuthorizedProxyProfile().toString(Charsets.UTF_8)).apply {
            add("proxy-providers", com.google.gson.JsonObject())
            getAsJsonArray("proxy-groups")[0].asJsonObject.add("use", ProxyGroupDrafts.array(listOf("旧资源")))
        }.let { com.lonnnnnng.flclash.service.YamlConfigCodec.toYaml(it.toString()) }
        var section by mutableStateOf("")
        var dark by mutableStateOf(false)
        compose.setContent { KClashTheme(darkTheme = dark) {
            ProfileEditorPage(onDismissRequest = {}, title = { Text("配置资源") }, text = { Column {
                ProfileProxyResourceBindings(section, source, listOf(resource), true, { section = it })
            } })
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; section = "" }
            compose.onNodeWithText("资源缺失，请重新选择").assertIsDisplayed()
            capture("proxy_resources_missing_$theme")
            compose.onNodeWithText("选择").performClick()
            capture("proxy_resources_picker_$theme")
            compose.onNodeWithTag("proxy_resource_search").performTextInput("不存在")
            compose.onNodeWithText("无搜索结果").assertIsDisplayed()
            capture("proxy_resources_search_empty_$theme")
            compose.onNodeWithTag("proxy_resource_clear_search").performClick()
            assertEquals("", compose.onNodeWithTag("proxy_resource_search").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
            compose.onNodeWithTag("proxy_resource_pick_122").performClick()
            compose.onNodeWithText("配置资源 · 本地节点资源").assertIsDisplayed()
            compose.runOnIdle { assertEquals(122L, ProfileProxyResources.idOf(ProfileProxyResources.definitions(section)["旧资源"])) }
            capture("proxy_resources_bound_$theme")
        }
    }

    @Test fun groupMemberPickerPersistsProfileBindingAlongsideUse() {
        assertPortraitDefaultFontConfiguration()
        val source = readAuthorizedProxyProfile().toString(Charsets.UTF_8)
        val resource = Profile.remote(123, "", "可选文件资源").copy(type = ProfileType.FILE)
        var saved: ProxyGroupsSave? = null
        compose.setContent { KClashTheme {
            VisualProxyGroupsEditor(Profile.custom(124, "成员选择"), ProfileProxyGroupsEditor.extractGroups(source), source, false,
                initialGroupIndex = 0, onDismiss = {}, onSave = {}, onSaveDraft = { saved = it }, resourceProfiles = listOf(resource))
        } }

        compose.onNodeWithTag("group_members_add").performScrollTo().performClick()
        compose.onNodeWithTag("group_member_search").performTextInput("可选文件资源")
        compose.onNodeWithTag("group_member_choice_true_可选文件资源").performClick()
        capture("proxy_resources_group_picker")
        compose.onNodeWithTag("group_member_picker_back").performClick()

        compose.onNodeWithTag("proxy_group_form_confirm").performClick()
        compose.waitUntil(10_000) { saved != null }
        val save = checkNotNull(saved)
        assertEquals(123L, save.providerProfiles["可选文件资源"])
        val updated = ProxyGroupDrafts.root(ProxyGroupDrafts.apply(source, save))
        assertTrue(updated.getAsJsonArray("proxy-groups")[0].asJsonObject.getAsJsonArray("use").any { it.asString == "可选文件资源" })
        assertEquals(123L, ProfileProxyResources.idOf(updated.getAsJsonObject("proxy-providers")["可选文件资源"]))
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(400)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
