package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.ProxyProviderOverride
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：整组页面一次覆盖默认选项、失败重试、混排、引用绑定和草稿退出，不以 YAML 表单替代选项页。 */
class ProviderPagesInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun optionDefaultsValidationFailureRetryAndRemovalWorkInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        var dark by mutableStateOf(false)
        var revision by mutableStateOf(0L)
        var options by mutableStateOf(emptyMap<String, ProxyProviderOverride>())
        var error by mutableStateOf<String?>(null)
        var attempts = 0
        compose.setContent { KClashTheme(darkTheme = dark) { key(dark) {
            ProfileProvidersPage(Profile.custom(801, "资源"), "cloud:\n  type: file\n  path: cloud.yaml", "",
                "proxy-groups:\n  - name: Select\n    type: select\n    use: [cloud]\n", options, emptyList(), emptyList(), false, revision, error, {},
                { _, _, saved -> attempts++; if (attempts == 1) error = "保存失败，请重试" else { options = saved; revision++; error = null } }, {}, { values -> List(values.size) { "" } })
        } } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; options = emptyMap(); revision = 0; attempts = 0; error = null }
            compose.onNodeWithTag("profile_provider_edit_cloud").performClick()
            compose.onNodeWithTag("provider_option_add").performScrollTo().performClick()
            for (option in listOf("INTERVAL", "TIMEOUT", "PREFIX", "SKIP_CERT", "IP_VERSION"))
                compose.onNodeWithTag("provider_option_pick_$option").performScrollTo().performClick()
            capture("provider_option_picker_$theme")
            compose.onNodeWithTag("provider_option_picker_back").performClick()
            compose.onNodeWithTag("provider_option_INTERVAL").performScrollTo().assertTextContains("300")
            compose.onNodeWithTag("provider_option_INTERVAL").performTextReplacement("0")
            compose.onNodeWithTag("provider_options_save").performClick()
            compose.onNodeWithTag("provider_options_error").assertTextContains("正整数", substring = true)
            compose.runOnIdle { assertEquals(0, attempts) }
            compose.onNodeWithTag("provider_option_INTERVAL").performScrollTo().performTextReplacement("450")
            compose.onNodeWithTag("provider_option_PREFIX").performScrollTo().performTextReplacement("Edge-")
            compose.onNodeWithTag("provider_option_IP_VERSION").performScrollTo().performClick()
            capture("provider_ip_picker_$theme")
            compose.onNodeWithTag("provider_ip_ipv6").performClick()
            compose.onNodeWithTag("provider_options_save").performClick()
            compose.onNodeWithTag("provider_options_error").assertTextContains("保存失败", substring = true)
            compose.onNodeWithTag("provider_option_INTERVAL").performScrollTo().assertTextContains("450")
            capture("provider_options_error_$theme")
            compose.onNodeWithTag("provider_options_save").performClick()
            compose.onNodeWithTag("profile_providers_page").assertIsDisplayed()
            compose.runOnIdle {
                val saved = options.getValue("cloud")
                assertEquals(450, saved.interval); assertEquals(5000, saved.timeout)
                assertEquals(true, saved.skipCertVerify); assertEquals("ipv6", saved.ipVersion)
                assertEquals("Edge-", saved.additionalPrefix)
            }
            compose.onNodeWithTag("profile_provider_edit_cloud").performClick()
            compose.onNodeWithTag("provider_option_remove_PREFIX").performScrollTo().performClick()
            compose.onNodeWithTag("provider_options_save").performClick()
            compose.onNodeWithTag("profile_providers_page").assertIsDisplayed()
            compose.runOnIdle { assertNull(options.getValue("cloud").additionalPrefix) }
        }
    }

    @Test fun filterMembersMixPresetsAndCustomConditionsAndKeepExplicitClear() {
        assertPortraitDefaultFontConfiguration()
        var visible by mutableStateOf(true)
        var initial by mutableStateOf(ProxyProviderOverride(filter = "free`HK"))
        var saved: ProxyProviderOverride? = null
        compose.setContent { KClashTheme { if (visible) CompositionLocalProvider(LocalFilterPresets provides listOf(FilterPreset("地区", "HK`JP"))) {
            ProviderOptionsPage("cloud", initial, false, null, {}, { saved = it; visible = false }, { values -> List(values.size) { "" } })
        } } }
        compose.onNodeWithTag("provider_filters_add").performScrollTo().performClick()
        compose.onNodeWithTag("provider_filter_category_FILTER").performClick()
        compose.onNodeWithTag("provider_filter_pick_FILTER_地区").performClick()
        compose.onNodeWithTag("provider_filter_pick_FILTER_地区").performClick()
        compose.onNodeWithTag("provider_filter_pick_FILTER_地区").performClick()
        compose.onNodeWithTag("provider_filter_kind_EXCLUDE").performScrollTo().performClick()
        compose.onNodeWithTag("provider_filter_custom").performScrollTo().performTextInput("ads")
        compose.onNodeWithTag("provider_filter_custom_add").performScrollTo().performClick()
        compose.onNodeWithTag("provider_filter_picker_done").performClick()
        capture("provider_filter_members")
        for (index in listOf(3, 2, 1)) compose.onNodeWithTag("group_move_up_$index").performScrollTo().performClick()
        compose.onNodeWithTag("provider_options_back").performClick()
        compose.onNodeWithText("放弃选项修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("provider_options_save").performClick()
        compose.runOnIdle {
            assertEquals("free`HK`JP", saved?.filter); assertEquals("ads", saved?.excludeFilter)
            assertEquals(listOf("excludeFilter"), saved?.memberLayout)
            initial = checkNotNull(saved); visible = true
        }
        repeat(4) { compose.onNodeWithTag("provider_filter_remove_0").performScrollTo().performClick() }
        compose.onNodeWithTag("provider_options_save").performClick()
        compose.runOnIdle { assertEquals("", saved?.filter); assertEquals("", saved?.excludeFilter); assertNull(saved?.memberLayout) }
    }

    @Test fun missingReferenceBindingAndQuickYamlKeepAliasAndFailedDraft() {
        assertPortraitDefaultFontConfiguration()
        var proxy by mutableStateOf("")
        var revision by mutableStateOf(0L)
        var error by mutableStateOf<String?>(null)
        var reject = false
        var infoSeen = false
        var saves = 0
        val resource = Profile.remote(902, "", "文件节点").copy(type = ProfileType.FILE)
        compose.setContent { KClashTheme {
            ProfileProvidersPage(Profile.custom(802, "资源"), proxy, "", "proxy-groups:\n  - name: Select\n    type: select\n    use: [旧别名]\n",
                emptyMap(), listOf(resource), emptyList(), false, revision, error, {}, { p, _, _ ->
                    saves++
                    if (reject) error = "保存失败，请重试" else { proxy = p; revision++; error = null }
                }, { infoSeen = true }, { values -> List(values.size) { "" } })
        } }
        compose.onNodeWithTag("profile_provider_proxy_旧别名").performClick()
        compose.onNodeWithText("资源不可用").assertIsDisplayed()
        compose.onNodeWithText("知道了").performClick()
        capture("provider_reference_missing")
        compose.onNodeWithTag("profile_provider_bind_proxy_旧别名").performClick()
        compose.onNodeWithTag("provider_binding_search").performTextInput("不存在")
        compose.onNodeWithText("没有匹配的资源").assertIsDisplayed()
        compose.onNodeWithTag("provider_binding_search").performTextClearance()
        compose.onNodeWithTag("provider_binding_proxy_902").performClick()
        compose.onNodeWithTag("profile_provider_edit_旧别名").assertIsDisplayed()
        compose.runOnIdle { assertEquals(902L, ProfileProxyResources.idOf(ProfileProxyResources.definitions(proxy).get("旧别名"))) }
        capture("provider_reference_bound")
        compose.onNodeWithTag("profile_providers_menu").performClick()
        compose.onNodeWithText("快捷 YAML").performClick()
        compose.onNodeWithTag("providers_yaml_rule").performScrollTo().performTextReplacement("bad: scalar")
        compose.onNodeWithTag("providers_yaml_save").performClick()
        compose.onNodeWithText("Provider YAML 格式无效，请检查缩进和条目类型").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, saves) }
        compose.onNodeWithTag("providers_yaml_rule").performScrollTo().performTextClearance()
        compose.onNodeWithTag("providers_yaml_proxy").performScrollTo().performTextReplacement(proxy + "\n# draft")
        compose.runOnIdle { reject = true }
        compose.onNodeWithTag("providers_yaml_save").performClick()
        compose.runOnIdle { assertEquals(2, saves) }
        compose.onNodeWithText("保存失败，请重试").performScrollTo().assertIsDisplayed()
        capture("provider_quick_yaml_error")
        compose.onNodeWithTag("providers_yaml_back").performClick()
        compose.onNodeWithText("放弃 YAML 修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.runOnIdle { reject = false }
        compose.onNodeWithTag("providers_yaml_save").performClick()
        compose.onNodeWithTag("profile_providers_page").assertIsDisplayed()
        compose.runOnIdle { assertFalse(infoSeen) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(250)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
