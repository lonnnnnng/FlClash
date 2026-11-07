package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.inspector.WindowInspector
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：资源子页须经正式导航和同一个ViewModel承受Activity重建，只提交授权正文的自有副本。 */
@androidx.test.filters.SdkSuppress(minSdkVersion = 29)
class MainProviderOptionsStateInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val alias = "重建验收资源"
    private fun model() = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java]

    @Test fun optionErrorAndInputSurviveActualActivityRecreation() = runBlocking {
        fixture { s ->
            options(); addOption("INTERVAL")
            compose.onNodeWithTag("provider_option_INTERVAL").performTextReplacement("0")
            compose.onNodeWithTag("provider_options_save").performClick()
            compose.onNodeWithTag("provider_options_error").assertIsDisplayed()
            recreate()
            compose.onNodeWithTag("provider_options_error").assertIsDisplayed().assertTextContains("必须是正整数", substring = true)
            compose.onNodeWithTag("provider_option_INTERVAL").assertTextContains("0")
            unchanged(s); capture("main_provider_option_error_recreated")
            compose.onNodeWithTag("provider_option_INTERVAL").performTextReplacement("450")
            compose.onNodeWithTag("provider_options_save").performClick()
            compose.waitUntil(20_000) { !exists("provider_options_save") }
            assertEquals(450, persisted(s)?.interval)
            capture("main_provider_option_error_corrected")
        }
    }

    @Test fun optionDiscardConfirmationSurvivesActualActivityRecreation() = runBlocking {
        fixture { s ->
            options(); addOption("TIMEOUT")
            compose.onNodeWithTag("provider_options_back").performClick()
            compose.onNodeWithText("放弃选项修改？").assertIsDisplayed()
            recreate()
            compose.onNodeWithText("放弃选项修改？").assertIsDisplayed()
            capture("main_provider_option_discard_recreated")
            compose.onNodeWithText("继续编辑").performClick()
            compose.onNodeWithTag("provider_option_TIMEOUT").assertTextContains("5000")
            compose.onNodeWithTag("provider_options_back").performClick()
            compose.onNodeWithText("放弃修改").performClick()
            unchanged(s); options()
            compose.onNodeWithTag("provider_option_TIMEOUT").assertDoesNotExist()
            capture("main_provider_option_discard_reopened")
        }
    }

    @Test fun quickYamlErrorAndDiscardSurviveActualActivityRecreation() = runBlocking {
        fixture { s ->
            quick()
            val originalProxy = editable("providers_yaml_proxy")
            compose.onNodeWithTag("providers_yaml_rule").performScrollTo().performTextReplacement("bad: scalar")
            compose.onNodeWithTag("providers_yaml_save").performClick()
            compose.onNodeWithTag("providers_yaml_error").assertIsDisplayed()
            recreate()
            compose.onNodeWithTag("providers_yaml_error").assertIsDisplayed()
            assertEquals(originalProxy, editable("providers_yaml_proxy"))
            assertEquals("bad: scalar", editable("providers_yaml_rule"))
            unchanged(s); capture("main_provider_yaml_error_recreated")
            compose.onNodeWithTag("providers_yaml_back").performClick()
            recreate()
            compose.onNodeWithText("放弃 YAML 修改？").assertIsDisplayed()
            capture("main_provider_yaml_discard_recreated")
            compose.onNodeWithText("继续编辑").performClick()
            assertEquals("bad: scalar", editable("providers_yaml_rule"))
            compose.onNodeWithTag("providers_yaml_back").performClick()
            compose.onNodeWithText("放弃修改").performClick()
            unchanged(s); quick()
            assertNotEquals("bad: scalar", editable("providers_yaml_rule"))
        }
    }

    @Test fun optionFilterAndIpChildDraftsSurviveActualActivityRecreation() = runBlocking {
        fixture { s ->
            options()
            compose.onNodeWithTag("provider_option_add").performScrollTo().performClick()
            compose.onNodeWithTag("provider_option_pick_IP_VERSION").performScrollTo().performClick()
            recreate()
            compose.onNodeWithTag("provider_option_picker_back").assertIsDisplayed().performClick()
            compose.onNodeWithTag("provider_option_IP_VERSION").performScrollTo().performClick()
            compose.onNodeWithTag("provider_ip_ipv4").performClick()
            compose.onNodeWithTag("provider_option_IP_VERSION").performClick()
            recreate()
            compose.onNodeWithTag("provider_ip_ipv4").assertIsSelected()
            capture("main_provider_ip_recreated")
            compose.onNodeWithTag("provider_ip_back").performClick()
            compose.onNodeWithTag("provider_filters_add").performScrollTo().performClick()
            compose.onNodeWithTag("provider_filter_kind_EXCLUDE").performScrollTo().performClick()
            compose.onNodeWithTag("provider_filter_custom").performTextReplacement("recreated-exclude")
            compose.onNodeWithTag("provider_filter_custom_add").performScrollTo().performClick()
            compose.onNodeWithTag("provider_filter_custom").performTextReplacement("unsubmitted-expression")
            compose.onNodeWithTag("provider_filter_search").performScrollTo().performTextReplacement("no-matching-filter")
            closeSoftKeyboard(); recreate()
            assertEquals("no-matching-filter", editable("provider_filter_search"))
            compose.onNodeWithTag("provider_filter_kind_EXCLUDE").performScrollTo().assertIsSelected()
            assertEquals("unsubmitted-expression", editable("provider_filter_custom"))
            capture("main_provider_filter_recreated")
            compose.onNodeWithTag("provider_filter_picker_back").performClick()
            compose.onNodeWithTag("provider_filter_row_0").assertTextContains("recreated-exclude")
            compose.onNodeWithTag("provider_filter_row_1").assertDoesNotExist()
            compose.onNodeWithTag("provider_option_IP_VERSION").performScrollTo().assertTextContains("ipv4")
            unchanged(s)
            compose.onNodeWithTag("provider_options_save").performClick()
            compose.waitUntil(20_000) { !exists("provider_options_save") }
            assertEquals("ipv4", persisted(s)?.ipVersion)
            assertEquals("recreated-exclude", persisted(s)?.excludeFilter)
            capture("main_provider_filter_saved")
        }
    }

    @Test fun proxyAndRuleBindingSearchSurviveActualActivityRecreationWithoutWriting() = runBlocking {
        fixture { s ->
            val rule = ProviderCatalog.from(s.profile, s.source, ProfileProvidersEditor.extract(s.source).proxyProviders,
                ProfileProvidersEditor.extract(s.source).ruleProviders).rules.first()
            for ((proxy, target) in listOf(true to alias, false to rule)) {
                val tag = if (proxy) "profile_provider_bind_proxy_$target" else "profile_provider_bind_rule_$target"
                compose.onNodeWithTag(tag).performScrollTo().performClick()
                val query = if (proxy) s.original.realLabel else "不存在的规则-${s.token}"
                compose.onNodeWithTag("provider_binding_search").performTextReplacement(query)
                closeSoftKeyboard(); recreate()
                assertEquals(query, editable("provider_binding_search"))
                compose.onNodeWithText(if (proxy) "选择代理资源" else "选择规则资源").assertIsDisplayed()
                if (proxy) compose.onNodeWithTag("provider_binding_proxy_${s.original.id}").assertIsDisplayed()
                else compose.onNodeWithText("没有匹配的资源").assertIsDisplayed()
                capture("main_provider_binding_${if (proxy) "proxy" else "rule"}_recreated")
                compose.onNodeWithTag("provider_binding_back").performClick()
                compose.onNodeWithTag(tag).assertIsDisplayed()
                unchanged(s)
            }
        }
    }

    @Test fun localValidationCancelledByRecreationKeepsDraftUntilExplicitRetry() = runBlocking {
        fixture { s ->
            options(); addOption("INTERVAL")
            compose.onNodeWithTag("provider_option_INTERVAL").performTextReplacement("450")
            closeSoftKeyboard()
            compose.onNodeWithTag("provider_filters_add").performScrollTo().performClick()
            compose.onNodeWithTag("provider_filter_custom").performTextReplacement("^recreated-provider$")
            compose.onNodeWithTag("provider_filter_custom_add").performScrollTo().performClick()
            closeSoftKeyboard()
            compose.onNodeWithTag("provider_filter_picker_back").performClick()
            val before = model().providersEditorState.value.revision
            val gate = ProviderValidationProbe.arm()
            try {
                compose.onNodeWithTag("provider_options_save").performClick()
                compose.waitUntil(10_000) { gate.isReached() }
                compose.onNodeWithTag("provider_options_save").assertIsNotEnabled().assertTextContains("校验中…")
                    .performTouchInput { click(); advanceEventTime(40); click() }
                back()
                compose.onNodeWithText("放弃选项修改？").assertDoesNotExist()
                assertFalse(model().providersEditorState.value.isSaving)
                unchanged(s)
                recreate()
                compose.waitUntil(5_000) { gate.isCancelled() }
                compose.onNodeWithTag("provider_options_save").assertIsEnabled().assertTextContains("保存")
                compose.onNodeWithTag("provider_option_INTERVAL").performScrollTo().assertTextContains("450")
                compose.onNodeWithTag("provider_filter_row_0").assertTextContains("^recreated-provider$")
                compose.onNodeWithTag("provider_options_error").assertDoesNotExist()
                compose.onNodeWithText("放弃选项修改？").assertDoesNotExist()
                // 作者 long：校验结果已返回但尚未受理保存；旧窗口取消后即使释放旧门闩也不能迟到写库。
                gate.release()
                compose.waitForIdle()
                assertEquals(1, gate.visits())
                assertEquals(before, model().providersEditorState.value.revision)
                assertFalse(model().providersEditorState.value.isSaving)
                unchanged(s)
                capture("main_provider_validation_cancelled_recreated")
                compose.onNodeWithTag("provider_options_save").performClick()
                compose.waitUntil(20_000) { !model().providersEditorState.value.isSaving && !exists("provider_options_save") }
                assertEquals(2, gate.visits())
                assertEquals(before + 1, model().providersEditorState.value.revision)
                assertEquals(450, persisted(s)?.interval)
                assertEquals("^recreated-provider$", persisted(s)?.filter)
                compose.onNodeWithTag("profile_providers_page").assertIsDisplayed()
                capture("main_provider_validation_retried_saved")
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("kclash_provider_validation_cancel", "cancelled=true;local-validations=2;revision-delta=1;explicit-retry=true")
                })
            } finally { ProviderValidationProbe.reset() }
        }
    }

    @Test fun acceptedSaveSurvivesActivityRecreationAndRejectsRepeatedActions() = runBlocking {
        fixture { s ->
            options(); addOption("INTERVAL")
            compose.onNodeWithTag("provider_option_INTERVAL").performTextReplacement("450")
            closeSoftKeyboard()
            val before = model().providersEditorState.value.revision
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            // 作者 long：持有真实Room事务而不写任何数据，给正式VM保存提供可观测等待窗口；不替换Repository或保存回调。
            val holder = launch(Dispatchers.IO) { s.entry.database().withTransaction { entered.complete(Unit); release.await() } }
            try {
                withTimeout(10_000) { entered.await() }
                compose.onNodeWithTag("provider_options_save").performClick()
                compose.waitUntil(15_000) { model().providersEditorState.value.isSaving }
                recreate()
                compose.onNodeWithTag("provider_options_save").assertIsNotEnabled().performTouchInput { click(); advanceEventTime(40); click() }
                compose.onNodeWithTag("provider_options_back").performClick()
                back()
                compose.onNodeWithTag("provider_options_page").assertIsDisplayed()
                compose.onNodeWithText("放弃选项修改？").assertDoesNotExist()
                assertEquals(before, model().providersEditorState.value.revision)
                capture("main_provider_save_recreated_waiting")
            } finally { release.complete(Unit); holder.join() }
            compose.waitUntil(20_000) { !model().providersEditorState.value.isSaving && !exists("provider_options_save") }
            assertEquals(before + 1, model().providersEditorState.value.revision)
            assertEquals(450, persisted(s)?.interval)
            compose.onNodeWithTag("profile_providers_page").assertIsDisplayed()
            capture("main_provider_save_recreated_complete")
        }
    }

    @Test fun systemBackDuringProviderChildEntranceKeepsParentSession() = runBlocking {
        fixture { s ->
            entranceBack("profile_provider_edit_$alias", "provider_options_back")
            compose.onNodeWithTag("profile_providers_page").assertIsDisplayed()
            options(); addOption("IP_VERSION")
            entranceBack("provider_option_add", "provider_option_picker_back")
            entranceBack("provider_option_IP_VERSION", "provider_ip_back")
            entranceBack("provider_filters_add", "provider_filter_picker_back")
            compose.onNodeWithTag("provider_options_page").assertIsDisplayed()
            capture("main_provider_entrance_parent_options")
            compose.onNodeWithTag("provider_options_back").performClick()
            compose.onNodeWithText("放弃修改").performClick()
            entranceBack("profile_provider_bind_proxy_$alias", "provider_binding_back")
            val rule = ProviderCatalog.from(s.profile, s.source, ProfileProvidersEditor.extract(s.source).proxyProviders,
                ProfileProvidersEditor.extract(s.source).ruleProviders).rules.first()
            entranceBack("profile_provider_bind_rule_$rule", "provider_binding_back")
            compose.onNodeWithTag("profile_providers_menu").performClick()
            entranceBack(null, "providers_yaml_back") { compose.onNodeWithText("快捷 YAML").performClick() }
            assertEquals(s.profile.id, model().providersEditorState.value.profile?.id)
            unchanged(s); capture("main_provider_entrance_parent_resources")
        }
    }

    private data class Session(val entry: ConnectionAcceptanceEntryPoint, val token: String, val profile: Profile,
        val original: Profile, val source: String, val row: ProfileEntity, val signature: Triple<Long, Long, String>)

    private suspend fun fixture(block: suspend (Session) -> Unit) {
        assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture()
        val args = InstrumentationRegistry.getArguments()
        assertEquals("true", args.getString("kclashProviderOptionsStateAcceptance"))
        val expectedSha = requireNotNull(args.getString("kclashLocalYamlSha256"))
        require(Regex("[a-f0-9]{64}").matches(expectedSha))
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        repository.profilesLoaded.first { it }
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        val rows = entry.database().profiles().getAll()
        val selected = requireNotNull(repository.currentProfileId.value)
        val files = rows.associate { it.id to signature(repository.profileFile(it.id)) }
        val preferences = entry.database().appPreferences().getByPrefix("").associate { it.key to it.value }
        val scripts = entry.database().scripts().getAll()
        val original = rows.single { it.type == ProfileType.FILE && files.getValue(it.id).third == expectedSha }.toDomain()
        val bytes = repository.profileFile(original.id).readBytes()
        val label = "资源恢复-${UUID.randomUUID()}"
        val created = repository.addCustomProfile(label)
        var failure: Throwable? = null
        try {
            repository.updateLocalProfileContent(created.id, bytes.decodeToString())
            val groups = ProxyGroupDrafts.read(bytes.decodeToString()).mapIndexed { index, group ->
                if (index == 0) ProxyGroupDrafts.withMembers(group, ProxyGroupDrafts.members(group) + ProxyGroupMember(alias, true)) else group
            }
            repository.saveCustomProxyGroups(created.id, ProxyGroupsSave(groups, mapOf(alias to original.id)), bytes.decodeToString()) { true }
            val row = requireNotNull(entry.database().profiles().getById(created.id))
            val s = Session(entry, label, row.toDomain(), original, repository.readLocalProfileContent(created.id), row,
                signature(repository.profileFile(created.id)))
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.waitUntil(15_000) { model().state.value.profiles.any { it.id == created.id } }
            // 作者 long：正式列表会首次定位当前配置；自有副本可能在LazyColumn屏外，先按真实列表滚到目标再打开。
            val list = hasScrollToNodeAction() and hasAnyAncestor(hasTestTag("profiles_grid"))
            // 作者 long：VM发出列表早于页面完成收集和挂载，必须等实际可滚动列表出现，不能只把内存就绪当成UI就绪。
            compose.waitUntil(15_000) { compose.onAllNodes(list).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(list).performScrollToNode(hasTestTag("profile_card_${created.id}"))
            compose.onNodeWithTag("profile_card_${created.id}").performScrollTo().performTouchInput { longClick() }
            compose.onNodeWithText("编辑").performClick()
            compose.onNodeWithTag("custom_profile_resources").performScrollTo().performClick()
            compose.waitUntil(15_000) { exists("profile_provider_edit_$alias") }
            block(s)
        } catch (error: Throwable) { failure = error; throw error }
        finally {
            var closeFailure: Throwable? = null
            try {
                compose.mainClock.autoAdvance = true
                compose.runOnIdle { model().closeProvidersEditor() }
            } catch (error: Throwable) { closeFailure = error }
            try {
                // 作者 long：只回收本例的CUSTOM副本，正式行/正文元数据/脚本/偏好逐项保护，失败也保留原错误。
                entry.database().profiles().getById(created.id)?.let { row ->
                    check(row.type == ProfileType.CUSTOM && row.label == label)
                    repository.deleteProfile(created.id)
                }
                // 作者 long：Room行消失不能证明正文文件已删除；即使窗口关闭报错也必须继续回收并检查本例磁盘文件。
                assertFalse("临时正文文件必须确实移除", repository.profileFile(created.id).exists())
                assertTrue("正式配置行保持", rows == entry.database().profiles().getAll())
                assertEquals(files, rows.associate { it.id to signature(repository.profileFile(it.id)) })
                assertEquals(selected, repository.currentProfileId.value)
                assertTrue("正式脚本保持", scripts == entry.database().scripts().getAll())
                assertTrue("正式偏好保持", preferences == entry.database().appPreferences().getByPrefix("").associate { it.key to it.value })
                assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("kclash_provider_state_preservation", "formal-count=${rows.size};own-row-and-file-removed=true;rows-bodies-script-rows-preferences-selection-preserved=true;runtime=STOPPED")
                })
            } catch (cleanup: Throwable) {
                closeFailure?.let(cleanup::addSuppressed)
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
            closeFailure?.let { if (failure != null) failure.addSuppressed(it) else throw it }
        }
    }

    private suspend fun unchanged(s: Session) {
        assertTrue("子页草稿尚未写正式副本", s.row == s.entry.database().profiles().getById(s.profile.id))
        assertEquals(s.signature, signature(s.entry.profileRepository().profileFile(s.profile.id)))
    }
    private suspend fun persisted(s: Session) = ProfileProviderOverrides.fromJsonString(
        requireNotNull(s.entry.database().profiles().getById(s.profile.id)).overridesJson).proxyProviders[alias]
    private fun options() = compose.onNodeWithTag("profile_provider_edit_$alias").performScrollTo().performClick()
    private fun quick() { compose.onNodeWithTag("profile_providers_menu").performClick(); compose.onNodeWithText("快捷 YAML").performClick() }
    private fun addOption(name: String) {
        compose.onNodeWithTag("provider_option_add").performScrollTo().performClick()
        compose.onNodeWithTag("provider_option_pick_$name").performScrollTo().performClick()
        compose.onNodeWithTag("provider_option_picker_back").performClick()
    }
    private fun recreate() {
        val retained = model()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertSame("Activity重建必须沿用正式VM", retained, model())
    }
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun editable(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
    private fun signature(file: File) = Triple(file.length(), file.lastModified(), MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) })
    private fun back() {
        compose.waitForIdle(); SystemClock.sleep(650)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle(); SystemClock.sleep(650)
    }
    private fun focusedWindow(): android.os.IBinder? {
        var token: android.os.IBinder? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            token = WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }?.windowToken
        }
        return token
    }
    private fun entranceBack(opener: String?, childBack: String, open: (() -> Unit)? = null) {
        compose.waitForIdle(); SystemClock.sleep(650)
        // 作者 long：此用例没有文字输入；直接核对焦点Window的IME，避免Espresso在刚退出的Dialog上等待错误的根窗口。
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = requireNotNull(WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() })
            assertFalse(requireNotNull(ViewCompat.getRootWindowInsets(view)).isVisible(WindowInsetsCompat.Type.ime()))
        }
        if (opener != null) compose.onNodeWithTag(opener).performScrollTo()
        val parentWindow = focusedWindow()
        assertNotNull(parentWindow)
        compose.mainClock.autoAdvance = false
        try {
            if (open != null) open() else compose.onNodeWithTag(requireNotNull(opener)).performClick()
            // 作者 long：菜单选择在自身退场后才导航；只推进到子窗首次挂载，再测子页入场，不能把菜单等待误判成子窗焦点失败。
            var openingFrames = 0
            while (!exists(childBack) && openingFrames < 180) {
                compose.mainClock.advanceTimeByFrame(); openingFrames++
            }
            compose.onNodeWithTag(childBack).assertExists()
            compose.mainClock.advanceTimeBy(64)
            compose.waitUntil(5_000) { focusedWindow()?.let { it != parentWindow } == true }
            val child = compose.onNodeWithTag(childBack).fetchSemanticsNode()
            assertTrue("入场中返回必须发生在仍有横向位移的阶段", child.boundsInWindow.left > 50f)
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("kclash_provider_entrance", "$childBack;openingFrames=$openingFrames;childAdvanceMs=64;translatedLeftPx=${child.boundsInWindow.left};focused=true")
            })
            // 作者 long：冻结的是Compose入场时钟；按键仍经真实系统Window投递，不用父Activity dispatcher冒充子窗返回。
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.mainClock.advanceTimeBy(600)
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitForIdle(); SystemClock.sleep(650)
        compose.onNodeWithTag(childBack).assertDoesNotExist()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); SystemClock.sleep(650)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
