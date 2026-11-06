package com.lonnnnnng.flclash.ui

import android.Manifest
import android.os.Bundle
import android.os.Build
import android.view.KeyEvent
import android.view.inspector.WindowInspector
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.filters.SdkSuppress
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ExtensionRule
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：扩展入口需在正式宿主恢复父子层级；只改原1.yaml的非当前副本，避免退出扩展触发正式核心应用。 */
class MainProfileExtensionStateInstrumentedTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun batchSelectionAndDeleteConfirmationSurviveRecreationBeforeExplicitCommit() = runBlocking {
        val rules = listOf("first", "second", "third").map { ExtensionRule(it, "DOMAIN,$it.example.test,DIRECT") }
        fixture(initialRules = rules) { session ->
            val before = session.entry.database().profiles().getById(session.id)
            for (id in listOf("first", "second")) compose.onNodeWithTag("extension_rule_select_$id").performScrollTo().performClick()
            compose.onNodeWithText("已选 2 项").performScrollTo().assertIsDisplayed()
            capture("main_extension_batch_selected")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("已选 2 项").assertIsDisplayed()
            compose.onNodeWithTag("extension_delete_selected").performClick()
            compose.onNodeWithText("删除所选 2 条规则？").assertIsDisplayed()
            capture("main_extension_batch_confirm")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("删除所选 2 条规则？").assertIsDisplayed()
            capture("main_extension_batch_confirm_recreated")
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithText("已选 2 项").assertIsDisplayed()
            assertEquals(before, session.entry.database().profiles().getById(session.id))
            // 作者 long：取消确认保留批选；下一次返回只退出批选，不能跨过扩展页直接回配置列表。
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.onNodeWithText("已选 2 项").assertDoesNotExist()
            compose.onNodeWithTag("profile_extension_mode_standard").assertIsDisplayed()
            for (id in listOf("first", "second")) compose.onNodeWithTag("extension_rule_select_$id").performScrollTo().performClick()
            compose.onNodeWithTag("extension_delete_selected").performScrollTo().performClick()
            compose.onNodeWithText("删除").performTouchInput { click(); advanceEventTime(40); click() }
            compose.waitUntil(15_000) { compose.onAllNodesWithText("删除规则").fetchSemanticsNodes().isEmpty() }
            val saved = requireNotNull(session.entry.database().profiles().getById(session.id))
            assertEquals(rules.takeLast(1), ProfileProviderOverrides.fromJsonString(saved.overridesJson).extension.rules)
            compose.onNodeWithTag("extension_rule_third").assertIsDisplayed()
            capture("main_extension_batch_saved")
            compose.onNodeWithTag("profile_extension_back").performClick()
            compose.onNodeWithTag("profile_card_${session.id}").performTouchInput { longClick() }
            compose.onNodeWithTag("profile_extend_${session.id}").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("extension_rule_third").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("extension_rule_third").assertIsDisplayed()
            compose.onNodeWithTag("extension_rule_first").assertDoesNotExist()
            assertEquals(saved, session.entry.database().profiles().getById(session.id))
            compose.onNodeWithTag("profile_extension_back").performClick()
        }
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun validationErrorImeBackAndRepeatedConfirmKeepOneSavedRule() = runBlocking {
        fixture { session ->
            val before = session.entry.database().profiles().getById(session.id)
            compose.onNodeWithTag("extension_rule_add").performScrollTo().performClick()
            val content = compose.onNodeWithTag("extension_rule_content")
            val invalid = "first.test\nsecond.test"
            content.performClick().performTextReplacement(invalid)
            compose.waitUntil(5_000) { focusedImeTop() != null }
            val confirm = compose.onNodeWithTag("extension_rule_confirm")
            // 作者 long：整页编辑器使用独立Dialog窗口，必须按该窗口的真实IME边界验收，不能只依赖isDisplayed。
            val imeTop = requireNotNull(focusedImeTop())
            for (node in listOf(content, confirm)) {
                assertTrue("规则输入与确认必须完整位于键盘上方", node.fetchSemanticsNode().boundsInWindow.bottom <= imeTop)
            }
            confirm.performClick()
            val error = "每条规则必须为单行且不超过 16 KiB"
            compose.onNodeWithText(error).performScrollTo().assertIsDisplayed()
            capture("main_extension_validation_ime")
            pressBack()
            compose.waitUntil(5_000) { focusedImeTop() == null }
            content.assertIsDisplayed()
            compose.onNodeWithText("放弃未保存修改？").assertDoesNotExist()
            assertEquals(before, session.entry.database().profiles().getById(session.id))
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText(error).assertIsDisplayed()
            assertEquals(invalid, value("extension_rule_content"))
            capture("main_extension_validation_recreated")
            val valid = "saved-${session.token}.example.test"
            content.performClick().performTextReplacement(valid)
            compose.waitUntil(5_000) { focusedImeTop() != null }
            pressBack()
            compose.waitUntil(5_000) { focusedImeTop() == null }
            content.assertIsDisplayed()
            pressBack()
            compose.onNodeWithText("放弃未保存修改？").assertIsDisplayed()
            compose.onNodeWithText("继续编辑").performClick()
            confirm.performTouchInput { click(); advanceEventTime(40); click() }
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("extension_rule_content").fetchSemanticsNodes().isEmpty() }
            val stored = requireNotNull(session.entry.database().profiles().getById(session.id))
            val rule = ProfileProviderOverrides.fromJsonString(stored.overridesJson).extension.rules.single()
            assertEquals("DOMAIN-SUFFIX,$valid,DIRECT", rule.value)
            compose.onNodeWithTag("extension_rule_${rule.id}").assertIsDisplayed()
            capture("main_extension_rule_saved_once")
            compose.onNodeWithTag("profile_extension_back").performClick()
            compose.onNodeWithTag("profile_card_${session.id}").performTouchInput { longClick() }
            compose.onNodeWithTag("profile_extend_${session.id}").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("extension_rule_${rule.id}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("extension_rule_${rule.id}").assertIsDisplayed()
            assertEquals(stored, session.entry.database().profiles().getById(session.id))
            compose.onNodeWithTag("profile_extension_back").performClick()
        }
    }

    @Test fun ruleTargetDraftAndDiscardConfirmationSurviveRecreationWithoutWriting() = runBlocking {
        fixture { session ->
            val before = session.entry.database().profiles().getById(session.id)
            compose.onNodeWithTag("extension_rule_add").performScrollTo().performClick()
            val draft = "long-".repeat(32) + "example.test"
            compose.onNodeWithTag("extension_rule_content").performTextReplacement(draft)
            closeSoftKeyboard()
            compose.onNodeWithTag("extension_rule_target").performScrollTo().performClick()
            compose.onNodeWithText("搜索目标").performTextInput("REJECT")
            closeSoftKeyboard()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("选择目标").assertIsDisplayed()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("extension_target_REJECT").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("extension_target_REJECT").performClick()
            assertEquals(draft, value("extension_rule_content"))
            capture("main_extension_rule_target_recreated")
            pressBack()
            compose.onNodeWithText("放弃未保存修改？").assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("放弃未保存修改？").assertIsDisplayed()
            capture("main_extension_discard_recreated")
            compose.onNodeWithText("继续编辑").performClick()
            assertEquals(draft, value("extension_rule_content"))
            // 作者 long：弹窗重建后Espresso可能选中无焦点的底层Activity；发送系统返回键仍必须由当前窗口处理并显示原确认。
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            compose.onNodeWithText("放弃未保存修改？").assertIsDisplayed()
            compose.onNodeWithText("放弃").performClick()
            compose.onNodeWithTag("extension_rule_content").assertDoesNotExist()
            assertEquals(before, session.entry.database().profiles().getById(session.id))
            compose.onNodeWithTag("profile_extension_back").performClick()
            compose.onNodeWithTag("profile_card_${session.id}").assertIsDisplayed()
        }
    }

    @Test fun globalSubpageSearchRestoresAndBackReturnsOneLevelWithoutChangingRules() = runBlocking {
        fixture { session ->
            val before = session.entry.database().profiles().getById(session.id)
            compose.onNodeWithTag("extension_global_control").performScrollTo().performClick()
            compose.onNodeWithTag("extension_global_search").performTextReplacement("未命中-${session.token}")
            closeSoftKeyboard()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("管理全局附加规则").assertIsDisplayed()
            assertEquals("未命中-${session.token}", value("extension_global_search"))
            capture("main_extension_global_search_recreated")
            compose.onNodeWithTag("extension_global_edit").performScrollTo().performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("全局附加规则").assertIsDisplayed()
            pressBack()
            compose.onNodeWithTag("extension_global_search").assertIsDisplayed()
            pressBack()
            compose.onNodeWithTag("profile_extension_mode_standard").assertIsDisplayed()
            assertEquals(before, session.entry.database().profiles().getById(session.id))
            capture("main_extension_global_returned")
            pressBack()
            compose.onNodeWithTag("profile_card_${session.id}").assertIsDisplayed()
        }
    }

    @Test fun scriptSelectionLongListRecreationAndLibraryReturnKeepNoncurrentProfile() = runBlocking {
        fixture { session ->
            repeat(14) { index ->
                val script = session.entry.scriptRepository().create("扩展验收-${session.token}-$index", "function main(config) { return config; }")
                session.scripts.add(script.id)
            }
            compose.onNodeWithTag("profile_extension_mode_script").performClick()
            val scriptId = session.scripts.last()
            val tag = "extension_script_$scriptId"
            compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.waitUntil(15_000) { runBlocking { session.entry.database().profiles().getById(session.id)?.scriptId == scriptId } }
            val beforeBounds = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            capture("main_extension_script_selected")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
            val afterBounds = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals(beforeBounds.top, afterBounds.top, 1f)
            assertEquals(scriptId, session.entry.database().profiles().getById(session.id)!!.scriptId)
            capture("main_extension_script_recreated")
            compose.onNodeWithTag("extension_manage_scripts").performScrollTo().performClick()
            compose.onNodeWithTag("scripts_back").performClick()
            compose.onNodeWithTag("extension_manage_scripts").assertIsDisplayed()
            compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.waitUntil(15_000) { runBlocking { session.entry.database().profiles().getById(session.id)?.scriptId == null } }
            capture("main_extension_script_unbound")
            pressBack()
            compose.onNodeWithTag("profile_card_${session.id}").assertIsDisplayed()
        }
    }

    private class Session(val entry: ConnectionAcceptanceEntryPoint, val id: Long, val token: String) {
        val scripts = mutableListOf<Long>()
    }

    private suspend fun fixture(initialRules: List<ExtensionRule> = emptyList(), block: suspend (Session) -> Unit) {
        assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture()
        val args = InstrumentationRegistry.getArguments()
        assertEquals("true", args.getString("kclashExtensionAcceptance"))
        val expected = requireNotNull(args.getString("kclashLocalYamlSha256"))
        require(Regex("[a-f0-9]{64}").matches(expected))
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        repository.profilesLoaded.first { it }
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        val rows = entry.database().profiles().getAll()
        val selected = requireNotNull(repository.currentProfileId.value)
        val files = rows.associate { it.id to signature(repository.profileFile(it.id)) }
        val scripts = entry.database().scripts().getAll()
        val scriptFiles = scripts.associate { it.id to signature(entry.scriptRepository().scriptFile(it.id)) }
        val preferences = entry.database().appPreferences().getByPrefix("").associate { it.key to it.value }
        val original = rows.single { it.type == ProfileType.FILE && files.getValue(it.id).third == expected }
        val token = UUID.randomUUID().toString().take(8)
        val label = "扩展验收-$token"
        val profile = repository.addFileProfile(label, repository.profileFile(original.id).readBytes())
        val session = Session(entry, profile.id, token)
        val source = signature(repository.profileFile(profile.id))
        var failure: Throwable? = null
        try {
            if (initialRules.isNotEmpty()) repository.saveExtension(profile.id, "standard", null, null,
                com.lonnnnnng.flclash.service.ProfileExtensionOverrides(configured = true, rules = initialRules))
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_card_${profile.id}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_card_${profile.id}").performScrollTo().performTouchInput { longClick() }
            compose.onNodeWithTag("profile_extend_${profile.id}").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_extension_mode_standard").fetchSemanticsNodes().isNotEmpty() }
            block(session)
            assertEquals(source, signature(repository.profileFile(profile.id)))
        } catch (error: Throwable) { failure = error; throw error }
        finally {
            try {
                // 作者 long：即使窗口恢复失败也只清理本次唯一ID；正式配置、脚本、正文和偏好必须逐项保持。
                val row = requireNotNull(entry.database().profiles().getById(profile.id))
                check(row.type == ProfileType.FILE && row.label == label)
                repository.deleteProfile(profile.id)
                session.scripts.forEach { id ->
                    val script = requireNotNull(entry.database().scripts().getById(id))
                    check(script.label.startsWith("扩展验收-$token-"))
                    entry.scriptRepository().delete(id)
                }
                assertEquals(rows, entry.database().profiles().getAll())
                assertEquals(files, rows.associate { it.id to signature(repository.profileFile(it.id)) })
                // 作者 long：ScriptEntity默认文本包含远程URL，保护失败只报告布尔结果，避免把正式脚本来源写入JUnit日志。
                assertTrue("正式脚本元数据必须保持且不输出来源", scripts == entry.database().scripts().getAll())
                assertEquals(scriptFiles, scripts.associate { it.id to signature(entry.scriptRepository().scriptFile(it.id)) })
                assertEquals(selected, repository.currentProfileId.value)
                assertTrue("正式偏好保持且不输出内容", preferences == entry.database().appPreferences().getByPrefix("").associate { it.key to it.value })
                assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("kclash_extension_preservation", "formal-count=${rows.size};temporary-removed=true;profiles-scripts-bodies-preferences-selection-preserved=true;runtime=STOPPED")
                })
            } catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
        }
    }

    private fun value(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
    private fun focusedImeTop(): Int? {
        if (Build.VERSION.SDK_INT < 29) error("独立窗口IME验收需要API29及以上")
        var top: Int? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
            val insets = view?.let(ViewCompat::getRootWindowInsets)
            if (view != null && insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                top = view.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            }
        }
        return top
    }
    private fun signature(file: File) = Triple(file.length(), file.lastModified(), MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) })
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // 作者 long：系统Window退场450ms不受Compose测试时钟驱动，静态证据须等它完成，避免将中间遮罩误记为恢复后外观。
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(650)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }
}
