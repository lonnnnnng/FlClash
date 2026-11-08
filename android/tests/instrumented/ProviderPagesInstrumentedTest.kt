package com.lonnnnnng.flclash.ui

import android.os.Build
import android.view.inspector.WindowInspector
import android.view.KeyEvent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.ProxyProviderOverride
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：整组页面一次覆盖默认选项、失败重试、混排、引用绑定和草稿退出，不以 YAML 表单替代选项页。 */
class ProviderPagesInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun repeatedIdenticalOptionErrorReturnsToTopWithoutEditing() {
        assertPortraitDefaultFontConfiguration()
        var saves = 0
        val initial = ProviderOption.entries.fold(ProxyProviderOverride()) { value, option -> option.apply(value, option.default) }
        compose.setContent { KClashTheme {
            ProviderOptionsPage("cloud", initial, false, null, {}, { saves++ })
        } }
        compose.onNodeWithTag("provider_option_INTERVAL").performScrollTo().performTextReplacement("0")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("provider_options_save").performClick()
        assertErrorInViewport("provider_options_error", "provider_options_page")
        compose.onNodeWithTag("provider_option_IP_VERSION").performScrollTo()
        compose.onNodeWithTag("provider_options_error").assertIsNotDisplayed()
        // 作者 long：第二次不改输入，不能靠改变错误文案或测试主动滚动来触发提示回顶。
        compose.onNodeWithTag("provider_options_save").performClick()
        assertErrorInViewport("provider_options_error", "provider_options_page")
        capture("provider_options_repeated_error")
        compose.onNodeWithTag("provider_option_INTERVAL").performScrollTo().assertTextContains("0")
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun optionValidationHidesRealImeAndKeepsInputForCorrection() {
        assertPortraitDefaultFontConfiguration()
        var saved: ProxyProviderOverride? = null
        compose.setContent { KClashTheme {
            ProviderOptionsPage("cloud", ProxyProviderOverride(interval = 300), false, null, {}, { saved = it })
        } }
        val input = compose.onNodeWithTag("provider_option_INTERVAL")
        input.performScrollTo().performClick().performTextReplacement("0")
        compose.waitUntil(5_000) { focusedImeTop() != null }
        capture("provider_options_invalid_ime")
        compose.onNodeWithTag("provider_options_save").performClick()
        compose.waitUntil(5_000) { focusedImeIsHidden() }
        assertErrorInViewport("provider_options_error", "provider_options_page")
        capture("provider_options_error_ime_hidden")
        input.performScrollTo().assertTextContains("0").performTextReplacement("450")
        compose.onNodeWithTag("provider_options_save").performClick()
        compose.runOnIdle { assertEquals(450, saved?.interval) }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun filterLongInputBackAndDiscardKeepParentBoundary() {
        assertPortraitDefaultFontConfiguration()
        var visible by mutableStateOf(true)
        var dismissals = 0
        var saves = 0
        val longExpression = (1..30).joinToString("\n") { "filter-value-$it" }
        compose.setContent { KClashTheme { if (visible) {
            ProviderOptionsPage("cloud", ProxyProviderOverride(ipVersion = "dual"), false, null,
                { dismissals++; visible = false }, { saves++ })
        } } }
        compose.onNodeWithTag("provider_option_IP_VERSION").performScrollTo().performClick()
        compose.onNodeWithTag("provider_ip_back").assertIsDisplayed()
        back()
        compose.onNodeWithTag("provider_option_IP_VERSION").assertTextContains("dual")
        compose.onNodeWithTag("provider_filters_add").performScrollTo().performClick()
        val input = compose.onNodeWithTag("provider_filter_custom")
        input.performScrollTo().performClick().performTextInput(longExpression)
        compose.waitUntil(5_000) { focusedImeTop() != null }
        val add = compose.onNodeWithTag("provider_filter_custom_add").performScrollTo()
        assertTrue("长表达式下添加按钮仍完整位于真实键盘上方", add.fetchSemanticsNode().boundsInWindow.bottom <= requireNotNull(focusedImeTop()))
        capture("provider_filter_long_ime")
        add.performTouchInput { click(); advanceEventTime(40); click() }
        assertEquals("", editable("provider_filter_custom"))
        back()
        compose.waitUntil(5_000) { focusedImeIsHidden() }
        compose.onNodeWithTag("provider_filter_picker_back").assertIsDisplayed()
        back()
        compose.onNodeWithTag("provider_filter_row_0").assertTextContains(longExpression)
        compose.onNodeWithTag("provider_filter_row_1").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, dismissals); assertEquals(0, saves) }
        capture("provider_filter_return_stable")
        back()
        compose.onNodeWithText("放弃选项修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("provider_filter_row_0").assertTextContains(longExpression)
        back()
        compose.onNodeWithText("放弃修改").performClick()
        compose.runOnIdle { assertEquals(1, dismissals); assertEquals(0, saves); visible = true }
        compose.onNodeWithTag("provider_filter_row_0").assertDoesNotExist()
        compose.onNodeWithTag("provider_option_IP_VERSION").performScrollTo().assertTextContains("dual")
    }

    @Test fun optionValidationAndPersistenceBusyRejectDuplicateSaveAndBack() {
        assertPortraitDefaultFontConfiguration()
        var saves = 0
        var validations = 0
        var saving by mutableStateOf(false)
        var error by mutableStateOf<String?>(null)
        var revision by mutableStateOf(0L)
        var persisted by mutableStateOf(mapOf("cloud" to ProxyProviderOverride(interval = 300)))
        var pending: Map<String, ProxyProviderOverride>? = null
        val gate = CompletableDeferred<Unit>()
        compose.setContent { KClashTheme {
            ProfileProvidersPage(Profile.custom(810, "资源"), "cloud:\n  type: file\n  path: cloud.yaml", "",
                "proxy-groups:\n  - name: Select\n    type: select\n    use: [cloud]\n", persisted, emptyList(), emptyList(), saving, revision, error, {},
                { _, _, fields -> saves++; pending = fields; saving = true }, {}, { values ->
                    validations++; gate.await(); List(values.size) { "" }
                })
        } }
        compose.onNodeWithTag("profile_provider_edit_cloud").performClick()
        compose.onNodeWithTag("provider_option_INTERVAL").performTextReplacement("450")
        compose.onNodeWithTag("provider_options_save").performTouchInput { click(); advanceEventTime(40); click() }
        compose.onNodeWithTag("provider_options_save").assertIsNotEnabled()
        back()
        compose.onNodeWithTag("provider_options_page").assertIsDisplayed()
        compose.onNodeWithText("放弃选项修改？").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, validations); assertEquals(0, saves); gate.complete(Unit) }
        compose.waitUntil(5_000) { saves == 1 }
        compose.onNodeWithTag("provider_options_save").assertIsNotEnabled().performTouchInput { click(); advanceEventTime(40); click() }
        back()
        compose.onNodeWithTag("provider_options_page").assertIsDisplayed()
        compose.onNodeWithText("放弃选项修改？").assertDoesNotExist()
        capture("provider_options_saving_stable")
        compose.runOnIdle { assertEquals(1, saves); saving = false; error = "保存失败，请重试" }
        assertErrorInViewport("provider_options_error", "provider_options_page")
        compose.onNodeWithTag("provider_option_INTERVAL").assertTextContains("450")
        compose.onNodeWithTag("provider_options_save").performClick()
        compose.waitUntil(5_000) { saves == 2 }
        compose.runOnIdle { persisted = checkNotNull(pending); saving = false; error = null; revision++ }
        compose.onNodeWithTag("provider_options_page").assertDoesNotExist()
        compose.onNodeWithTag("profile_provider_edit_cloud").performClick()
        compose.onNodeWithTag("provider_option_INTERVAL").assertTextContains("450")
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun quickYamlRepeatedErrorLongDraftAndDiscardRespectKeyboard() {
        assertPortraitDefaultFontConfiguration()
        var saves = 0
        val longProxy = (1..24).joinToString("\n") { "resource$it:\n  type: file\n  path: resource$it.yaml" }
        compose.setContent { KClashTheme {
            ProfileProvidersPage(Profile.custom(811, "资源"), "", "", "", emptyMap(), emptyList(), emptyList(), false, 0, null, {},
                { _, _, _ -> saves++ }, {}, { values -> List(values.size) { "" } })
        } }
        compose.onNodeWithTag("profile_providers_menu").performClick()
        compose.onNodeWithText("快捷 YAML").performClick()
        compose.onNodeWithTag("providers_yaml_proxy").performTextReplacement(longProxy)
        compose.onNodeWithTag("providers_yaml_rule").performScrollTo().performClick().performTextReplacement("bad: scalar")
        compose.waitUntil(5_000) { focusedImeTop() != null }
        compose.onNodeWithTag("providers_yaml_save").performClick()
        compose.waitUntil(5_000) { focusedImeIsHidden() }
        assertErrorInViewport("providers_yaml_error", "providers_yaml_scroll")
        compose.onNodeWithTag("providers_yaml_rule").performScrollTo()
        compose.onNodeWithTag("providers_yaml_error").assertIsNotDisplayed()
        compose.onNodeWithTag("providers_yaml_save").performClick()
        assertErrorInViewport("providers_yaml_error", "providers_yaml_scroll")
        capture("provider_yaml_repeated_error")
        compose.onNodeWithTag("providers_yaml_proxy").assertTextContains(longProxy)
        back()
        compose.onNodeWithText("放弃 YAML 修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("providers_yaml_proxy").assertTextContains(longProxy)
        back()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithTag("profile_providers_menu").performClick()
        compose.onNodeWithText("快捷 YAML").performClick()
        assertEquals("", editable("providers_yaml_proxy"))
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun proxyAndRuleBindingLongListsKeepAliasOnFailureCancelAndRetry() {
        assertPortraitDefaultFontConfiguration()
        var proxy by mutableStateOf("")
        var rule by mutableStateOf("")
        var revision by mutableStateOf(0L)
        var saving by mutableStateOf(false)
        var error by mutableStateOf<String?>(null)
        var pending: Pair<String, String>? = null
        var saves = 0
        var dismissals = 0
        val profiles = (1..40).map { Profile.remote(900L + it, "", "文件资源-$it").copy(type = ProfileType.FILE) }
        val rules = (1..40).map { RuleResource("r$it", "规则资源-$it", fileName = "r$it.rules", behavior = "domain", format = "yaml", updatedAt = 0) }
        compose.setContent { KClashTheme {
            ProfileProvidersPage(Profile.custom(812, "资源"), proxy, rule,
                "proxy-groups:\n  - name: Select\n    type: select\n    use: [代理别名]\nrules:\n  - RULE-SET,规则别名,DIRECT\n",
                emptyMap(), profiles, rules, saving, revision, error, { dismissals++ },
                { p, r, _ -> saves++; pending = p to r; saving = true }, {}, { values -> List(values.size) { "" } })
        } }
        for (isProxy in listOf(true, false)) {
            val opener = if (isProxy) "profile_provider_bind_proxy_代理别名" else "profile_provider_bind_rule_规则别名"
            val first = if (isProxy) "provider_binding_proxy_901" else "provider_binding_rule_r1"
            val last = if (isProxy) "provider_binding_proxy_940" else "provider_binding_rule_r40"
            val query = if (isProxy) "文件资源-40" else "规则资源-40"
            val previousSaves = saves
            compose.onNodeWithTag(opener).performScrollTo().performClick()
            compose.onNodeWithTag(first).assertIsDisplayed()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(39)
            compose.onNodeWithTag(last).assertIsDisplayed()
            compose.onNodeWithTag("provider_binding_search").performClick().performTextInput(query)
            compose.waitUntil(5_000) { focusedImeTop() != null }
            back()
            compose.waitUntil(5_000) { focusedImeIsHidden() }
            compose.onNodeWithTag("provider_binding_search").assertTextContains(query)
            // 作者 long：正式保存由父级busy认领，连续选择和返回不能绕过同一次持久化的等待边界。
            compose.onNodeWithTag(last).performTouchInput { click(); advanceEventTime(40); click() }
            compose.runOnIdle { assertEquals(previousSaves + 1, saves) }
            compose.onNodeWithTag(last).assertIsNotEnabled()
            back()
            compose.onNodeWithTag("provider_binding_search").assertIsDisplayed()
            compose.runOnIdle { saving = false; error = "绑定失败，请重试" }
            compose.onNodeWithText("绑定失败，请重试").assertIsDisplayed()
            compose.onNodeWithTag("provider_binding_search").assertTextContains(query)
            capture("provider_binding_failure_$isProxy")
            back()
            compose.onNodeWithTag(opener).assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(0, dismissals)
                if (isProxy) assertEquals("", proxy) else assertEquals("", rule)
                error = null
            }
            compose.onNodeWithTag(opener).performClick()
            compose.onNodeWithTag("provider_binding_search").performTextInput(query)
            back()
            compose.onNodeWithTag(last).performClick()
            compose.runOnIdle {
                assertEquals(previousSaves + 2, saves)
                proxy = checkNotNull(pending).first; rule = checkNotNull(pending).second
                saving = false; error = null; revision++
            }
            compose.onNodeWithTag("provider_binding_search").assertDoesNotExist()
            compose.runOnIdle {
                if (isProxy) assertEquals(940L, ProfileProxyResources.idOf(ProfileProxyResources.definitions(proxy).get("代理别名")))
                else {
                    assertEquals("r40", RuleResources.idOf(ProviderCatalog.ruleDefinitions(rule).get("规则别名")))
                    assertEquals(940L, ProfileProxyResources.idOf(ProfileProxyResources.definitions(proxy).get("代理别名")))
                }
            }
            capture("provider_binding_saved_$isProxy")
        }
    }

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
            compose.onNodeWithTag("provider_options_error").assertIsDisplayed().assertTextContains("保存失败", substring = true)
            capture("provider_options_error_$theme")
            compose.onNodeWithTag("provider_option_INTERVAL").performScrollTo().assertTextContains("450")
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
        // 作者 long：用触点启动涟漪并两次回源取消，过滤草稿应保持，菜单按钮必须能再次打开。
        repeat(2) {
            compose.onNodeWithTag("provider_filter_menu").performTouchInput { click() }
            compose.onNodeWithText("沿用源过滤").assertIsDisplayed()
            back()
            compose.onNodeWithTag("app_popup_menu").assertDoesNotExist()
            compose.onNodeWithTag("provider_filter_menu").assertIsDisplayed()
        }
        compose.onNodeWithTag("provider_filters_add").performScrollTo().performClick()
        compose.onNodeWithTag("provider_filter_category_FILTER").performClick()
        compose.onNodeWithTag("provider_filter_pick_FILTER_地区").performClick()
        compose.onNodeWithTag("provider_filter_pick_FILTER_地区").performClick()
        compose.onNodeWithTag("provider_filter_pick_FILTER_地区").performClick()
        compose.onNodeWithTag("provider_filter_kind_EXCLUDE").performScrollTo().performClick()
        compose.onNodeWithTag("provider_filter_custom").performScrollTo().performTextInput("ads")
        // 作者 long：键盘收起后再重新测量按钮位置，防止触摸沿用输入前的屏幕坐标而漏掉自定义条件。
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle(); android.os.SystemClock.sleep(350)
        compose.onNodeWithTag("provider_filter_custom_add").performScrollTo().assertIsDisplayed().performClick()
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
        compose.onNodeWithText("快捷 YAML").assertIsDisplayed()
        compose.onNodeWithTag("app_popup_outside").performTouchInput { click(Offset(4f, centerY)) }
        compose.onNodeWithText("快捷 YAML").assertDoesNotExist()
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
        // 作者 long：系统Window的450ms退场不由Compose时钟驱动，静态截图须跨过退场后再采样。
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(650)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }

    private fun editable(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun back() {
        // 作者 long：先等独立Window完成入场取得按键焦点，避免返回被发送给尚未退居后台的父窗口。
        compose.waitForIdle()
        android.os.SystemClock.sleep(650)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        android.os.SystemClock.sleep(650)
    }

    private fun assertErrorInViewport(errorTag: String, pageTag: String) {
        compose.waitForIdle()
        val error = compose.onNodeWithTag(errorTag).assertIsDisplayed().fetchSemanticsNode()
        val page = compose.onNodeWithTag(pageTag).fetchSemanticsNode()
        assertTrue("失败提示顶部不得裁切", error.boundsInWindow.top >= page.boundsInWindow.top)
        assertTrue("失败提示底部不得裁切", error.boundsInWindow.bottom <= page.boundsInWindow.bottom)
        assertEquals("每次拒绝保存都应回到错误提示", 0f, page.config[SemanticsProperties.VerticalScrollAxisRange].value(), 1f)
    }

    private var observedImeWindow: android.os.IBinder? = null

    private data class ImeSnapshot(val token: android.os.IBinder, val visible: Boolean, val top: Int)

    private fun imeSnapshot(): ImeSnapshot? {
        check(Build.VERSION.SDK_INT >= 29)
        var result: ImeSnapshot? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
            val insets = view?.let(ViewCompat::getRootWindowInsets)
            val token = view?.windowToken
            if (view != null && insets != null && token != null) {
                result = ImeSnapshot(token, insets.isVisible(WindowInsetsCompat.Type.ime()),
                    view.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            }
        }
        return result
    }

    private fun focusedImeTop(): Int? = imeSnapshot()?.takeIf { it.visible }?.let {
        observedImeWindow = it.token
        it.top
    }

    private fun focusedImeIsHidden(): Boolean {
        // 作者 long：切窗时暂时拿不到Insets不能冒充收键盘成功，必须回读之前真正显示IME的同一窗口。
        val current = imeSnapshot() ?: return false
        return current.token == observedImeWindow && !current.visible
    }
}
