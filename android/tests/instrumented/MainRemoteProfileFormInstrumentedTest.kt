package com.lonnnnnng.flclash.ui

import android.Manifest
import android.os.Bundle
import android.view.KeyEvent
import android.view.inspector.WindowInspector
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：真实宿主只编辑和取消输入；测试域名绝不提交下载，正式配置与脚本须逐项保持。 */
@SdkSuppress(minSdkVersion = 29)
class MainRemoteProfileFormInstrumentedTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun invalidSingleInputImeNavigationAndRecreationKeepDraftWithoutImporting() = runBlocking {
        fixture {
            compose.waitUntil(5_000) { focusedImeTop() != null }
            compose.onNodeWithTag("profile_add_remote_url").assertIsFocused()
            compose.onNodeWithTag("profile_add_remote_label").performTextReplacement("保留名称-".repeat(6))
            compose.onNodeWithTag("profile_add_remote_label").performImeAction()
            compose.onNodeWithTag("profile_add_remote_url").assertIsFocused()
            val invalid = "file:///private/" + "long-path/".repeat(24)
            compose.onNodeWithTag("profile_add_remote_url").performTextReplacement(invalid)
            compose.onNodeWithTag("profile_add_remote_url").performImeAction()
            compose.onNodeWithTag("profile_add_remote_age_key").assertIsFocused().performTextInput("fixture-key")
            assertAboveIme("profile_add_remote_age_key", "profile_add_submit")
            capture("remote_single_key_ime")
            compose.onNodeWithTag("profile_add_remote_age_key").performImeAction()
            compose.onNodeWithTag("profile_add_remote_error", useUnmergedTree = true).performScrollTo().assertTextContains("请输入有效的 HTTP/HTTPS 订阅链接")
            assertEquals(invalid, value("profile_add_remote_url"))
            assertAboveIme("profile_add_remote_url", "profile_add_submit")
            capture("remote_single_invalid_ime")
            back()
            compose.waitUntil(5_000) { focusedImeTop() == null }
            compose.onNodeWithTag("profile_add_remote_url").assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("profile_add_remote_error", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            assertEquals(invalid, value("profile_add_remote_url"))
            assertEquals("保留名称-".repeat(6), value("profile_add_remote_label"))
            assertEquals("", value("profile_add_remote_age_key"))
            closeSoftKeyboard()
            capture("remote_single_invalid_recreated")
            back()
            compose.onNodeWithTag("profile_add_remote_url").assertDoesNotExist()
            compose.onNodeWithTag("profiles_add_fab").assertIsDisplayed()
        }
    }

    @Test fun longBatchInputScrollAndRecreationKeepModePreviewAndSourceRoundTrip() = runBlocking {
        fixture {
            val first = "https://batch-0.invalid/sub"
            compose.onNodeWithTag("profile_add_remote_url").performTextReplacement("  $first  ")
            compose.onNodeWithTag("profile_add_mode_batch").performClick()
            assertEquals(first, value("profile_add_remote_batch"))
            val valid = (0 until 40).joinToString("\n") { "https://batch-$it.invalid/sub" }
            val text = "$valid\n$first\ninvalid-final-line"
            compose.onNodeWithTag("profile_add_remote_batch").performTextReplacement(text)
            compose.onNodeWithTag("profile_add_submit").assertIsNotEnabled()
            compose.onNodeWithTag("profile_add_remote_batch").performTextInputSelection(TextRange(0))
            assertAboveIme("profile_add_remote_batch", "profile_add_submit")
            capture("remote_batch_first_ime")
            compose.onNodeWithTag("profile_add_remote_batch").performTextInputSelection(TextRange(text.length))
            compose.onNodeWithText("有 1 条链接格式无效，修正后才能导入").performScrollTo().assertIsDisplayed()
            capture("remote_batch_last_ime")
            closeSoftKeyboard()
            compose.activityRule.scenario.recreate()
            assertEquals(text, value("profile_add_remote_batch"))
            compose.onNodeWithTag("profile_add_mode_batch").assertIsSelected()
            compose.onNodeWithText("识别到 40 个链接").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("profile_add_submit").assertIsNotEnabled()
            closeSoftKeyboard()
            capture("remote_batch_recreated")
            compose.onNodeWithTag("profile_add_remote_batch").performTextReplacement(valid)
            compose.onNodeWithTag("profile_add_submit").assertIsEnabled()
            // 作者 long：此处只证明修正后可提交，不访问测试域名；返回来源再打开也应保留用户的批量草稿。
            compose.onNodeWithTag("profile_add_back").performClick()
            compose.onNodeWithTag("profile_add_source_remote").performClick()
            assertEquals(valid, value("profile_add_remote_batch"))
            compose.onNodeWithTag("profile_add_mode_single").performClick()
            assertEquals("  $first  ", value("profile_add_remote_url"))
            compose.onNodeWithTag("profile_add_mode_batch").performClick()
            assertEquals(valid, value("profile_add_remote_batch"))
            closeSoftKeyboard()
            capture("remote_batch_returned")
            back()
            compose.onNodeWithTag("profile_add_remote_batch").assertDoesNotExist()
        }
    }

    @Test fun backgroundClearsKeyAndCancelledFormReopensWithEmptyDraft() = runBlocking {
        fixture {
            compose.onNodeWithTag("profile_add_remote_label").performTextReplacement("临时名称")
            compose.onNodeWithTag("profile_add_remote_url").performTextReplacement("https://cancel.invalid/sub")
            compose.onNodeWithTag("profile_add_remote_age_key").performScrollTo().performTextInput("fixture-only-secret")
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals("", value("profile_add_remote_age_key"))
            assertEquals("临时名称", value("profile_add_remote_label"))
            assertEquals("https://cancel.invalid/sub", value("profile_add_remote_url"))
            closeSoftKeyboard()
            capture("remote_single_background")
            back()
            compose.onNodeWithTag("profile_add_remote_url").assertDoesNotExist()
            openForm()
            assertEquals("", value("profile_add_remote_label"))
            assertEquals("", value("profile_add_remote_url"))
            assertEquals("", value("profile_add_remote_age_key"))
            compose.onNodeWithTag("profile_add_submit").assertIsNotEnabled()
            closeSoftKeyboard()
            capture("remote_single_reopened_empty")
            back()
        }
    }

    private suspend fun fixture(block: suspend () -> Unit) {
        assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture()
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashRemoteFormAcceptance"))
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repo = entry.profileRepository()
        repo.profilesLoaded.first { it }
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        val rows = entry.database().profiles().getAll()
        require(rows.isNotEmpty())
        val files = rows.associate { it.id to signature(repo.profileFile(it.id)) }
        val scripts = entry.database().scripts().getAll()
        val scriptFiles = scripts.associate { it.id to signature(entry.scriptRepository().scriptFile(it.id)) }
        val preferences = entry.database().appPreferences().getByPrefix("").associate { it.key to it.value }
        val selected = repo.currentProfileId.value
        var failure: Throwable? = null
        try {
            compose.onNodeWithTag("nav_profiles").performClick()
            openForm(); block()
        } catch (error: Throwable) { failure = error; throw error }
        finally {
            try {
                assertTrue("正式配置元数据保持", rows == entry.database().profiles().getAll())
                assertEquals(files, rows.associate { it.id to signature(repo.profileFile(it.id)) })
                assertTrue("正式脚本元数据保持", scripts == entry.database().scripts().getAll())
                assertEquals(scriptFiles, scripts.associate { it.id to signature(entry.scriptRepository().scriptFile(it.id)) })
                assertTrue("正式偏好保持", preferences == entry.database().appPreferences().getByPrefix("").associate { it.key to it.value })
                assertEquals(selected, repo.currentProfileId.value)
                assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("kclash_remote_form_preservation", "count=${rows.size};profiles-scripts-bodies-preferences-selection-preserved=true;runtime=STOPPED")
                })
            } catch (protection: Throwable) { if (failure != null) failure.addSuppressed(protection) else throw protection }
        }
    }

    private fun openForm() {
        compose.onNodeWithTag("profiles_add_fab").performClick()
        compose.onNodeWithTag("profile_add_source_remote").performClick()
        compose.onNodeWithTag("profile_add_remote_url").assertIsDisplayed()
    }
    private fun value(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
    private fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }
    private fun focusedImeTop(): Int? {
        var top: Int? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
            val insets = view?.let(ViewCompat::getRootWindowInsets)
            if (view != null && insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) top = view.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        }
        return top
    }
    private fun assertAboveIme(vararg tags: String) {
        compose.waitUntil(5_000) { focusedImeTop() != null }
        val top = requireNotNull(focusedImeTop())
        tags.forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInWindow
            assertTrue("$tag 必须完整位于真实键盘上方", bounds.bottom <= top)
        }
    }
    private fun signature(file: File) = Triple(file.length(), file.lastModified(), MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) })
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(650)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
