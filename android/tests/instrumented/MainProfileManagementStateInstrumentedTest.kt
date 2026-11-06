package com.lonnnnnng.flclash.ui

import android.Manifest
import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
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

/** 作者 long：常用管理入口必须在真实宿主中保留待确认状态；只提交自己的原1.yaml副本，不触碰正式配置。 */
class MainProfileManagementStateInstrumentedTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun renameDraftSurvivesRecreationAndOnlyExplicitSaveUpdatesTemporaryMetadata() = runBlocking {
        fixture { session ->
            val id = session.ids.first()
            val source = signature(session.entry.profileRepository().profileFile(id))
            val label = "重建后配置-${session.token}"
            session.labels.getValue(id).add(label)
            menu(id, "profile_rename_$id")
            compose.onNodeWithTag("profile_rename_label").performTextReplacement("  $label  ")
            closeSoftKeyboard()
            capture("main_profile_rename_before")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("重命名配置").assertIsDisplayed()
            assertEquals("  $label  ", renameValue())
            assertEquals(session.labels.getValue(id).first(), session.entry.database().profiles().getById(id)!!.label)
            capture("main_profile_rename_recreated")
            compose.onNodeWithTag("profile_rename_save").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_rename_label").fetchSemanticsNodes().isEmpty() }
            compose.waitUntil(15_000) { compose.onAllNodes(hasText(label, substring = true)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("profile_card_$id").assertTextContains(label)
            assertEquals(label, session.entry.database().profiles().getById(id)!!.label)
            assertEquals(source, signature(session.entry.profileRepository().profileFile(id)))
            menu(id, "profile_rename_$id")
            compose.onNodeWithTag("profile_rename_label").performTextReplacement("取消的草稿")
            closeSoftKeyboard()
            compose.onNodeWithText("取消").performClick()
            menu(id, "profile_rename_$id")
            assertEquals(label, renameValue())
            compose.onNodeWithText("取消").performClick()
            capture("main_profile_rename_saved")
        }
    }

    @Test fun deleteConfirmationSurvivesRecreationAndCancelNeverDeletesProfile() = runBlocking {
        fixture { session ->
            val id = session.ids.last()
            val file = session.entry.profileRepository().profileFile(id)
            val before = signature(file)
            menu(id, "delete")
            compose.onNodeWithText("删除配置").assertIsDisplayed()
            capture("main_profile_delete_before")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("删除配置").assertIsDisplayed()
            compose.onNodeWithText("确定删除“${session.labels.getValue(id).first()}”吗？本地配置文件也会一并删除。").assertIsDisplayed()
            assertEquals(before, signature(file))
            capture("main_profile_delete_recreated")
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithTag("profile_card_$id").assertIsDisplayed()
            assertNotNull(session.entry.database().profiles().getById(id))
            assertEquals(before, signature(file))
            menu(id, "delete")
            compose.onNodeWithText("删除").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_card_$id").fetchSemanticsNodes().isEmpty() }
            assertNull(session.entry.database().profiles().getById(id))
            assertFalse(file.exists())
            capture("main_profile_delete_confirmed")
        }
    }

    @Test fun reorderDraftAndDiscardConfirmationSurviveRecreationBeforeExplicitSave() = runBlocking {
        fixture { session ->
            val (first, second) = session.ids
            val before = session.entry.database().profiles().getAll()
            compose.onNodeWithTag("profiles_sort").performClick()
            compose.onNodeWithTag("profiles_order_$first").performOrderTouchMove(1)
            assertBefore(second, first)
            assertEquals(before, session.entry.database().profiles().getAll())
            capture("main_profile_order_draft")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("profiles_order_page").assertIsDisplayed()
            assertBefore(second, first)
            assertEquals(before, session.entry.database().profiles().getAll())
            capture("main_profile_order_recreated")
            compose.onNodeWithTag("profiles_order_back").performClick()
            compose.onNodeWithText("放弃排序修改？").assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("放弃排序修改？").assertIsDisplayed()
            capture("main_profile_order_discard_recreated")
            compose.onNodeWithText("继续排序").performClick()
            assertBefore(second, first)
            compose.onNodeWithTag("profiles_order_back").performClick()
            compose.onNodeWithText("放弃").performClick()
            assertEquals(before, session.entry.database().profiles().getAll())
            compose.onNodeWithTag("profiles_sort").performClick()
            assertBefore(first, second)
            compose.onNodeWithTag("profiles_order_$first").performOrderTouchMove(1)
            assertBefore(second, first)
            compose.onNodeWithTag("profiles_order_save").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("profiles_order_page").fetchSemanticsNodes().isEmpty() }
            val expected = before.map { when (it.id) { first -> second; second -> first; else -> it.id } }
            assertEquals(expected, session.entry.database().profiles().getAll().map { it.id })
            compose.onNodeWithTag("nav_dashboard").performClick()
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.onNodeWithTag("profiles_sort").performClick()
            assertBefore(second, first)
            capture("main_profile_order_saved_reopened")
            compose.onNodeWithTag("profiles_order_back").performClick()
            compose.onNodeWithText("放弃排序修改？").assertDoesNotExist()
        }
    }

    private class Session(val entry: ConnectionAcceptanceEntryPoint, val token: String) {
        val ids = mutableListOf<Long>()
        val labels = mutableMapOf<Long, MutableSet<String>>()
        val types = mutableMapOf<Long, ProfileType>()
    }

    private suspend fun fixture(block: suspend (Session) -> Unit) {
        assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture()
        val args = InstrumentationRegistry.getArguments()
        assertEquals("true", args.getString("kclashProfileManagementAcceptance"))
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
        val original = rows.single { it.type == ProfileType.FILE && signature(repository.profileFile(it.id)).third == expectedSha }
        val bytes = repository.profileFile(original.id).readBytes()
        val session = Session(entry, UUID.randomUUID().toString().take(8))
        var failure: Throwable? = null
        try {
            repeat(2) { index ->
                val label = "配置管理-${session.token}-${index + 1}"
                val profile = if (index == 0) repository.addCustomProfile(label).also {
                    repository.updateLocalProfileContent(it.id, bytes.decodeToString())
                } else repository.addFileProfile(label, bytes)
                val id = profile.id
                session.ids.add(id); session.labels[id] = linkedSetOf(label)
                session.types[id] = profile.type
            }
            compose.onNodeWithTag("nav_profiles").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_card_${session.ids.last()}").fetchSemanticsNodes().isNotEmpty() }
            block(session)
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try {
                // 作者 long：即使恢复断言失败也回收自己的副本；正式行的顺序/名称/选择和源文件元数据必须逐项保持。
                session.ids.forEach { id ->
                    entry.database().profiles().getById(id)?.let { row ->
                        check(row.type == session.types.getValue(id) && row.label in session.labels.getValue(id))
                        repository.deleteProfile(id)
                    }
                }
                assertEquals(rows, entry.database().profiles().getAll())
                assertEquals(files, rows.associate { it.id to signature(repository.profileFile(it.id)) })
                assertEquals(selected, repository.currentProfileId.value)
                assertTrue("正式偏好值保持且不输出缓存内容",
                    preferences == entry.database().appPreferences().getByPrefix("").associate { it.key to it.value })
                assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("kclash_profile_management_preservation", "formal-count=${rows.size};temporary-removed=true;body-metadata-order-selection-preferences-preserved=true;runtime=STOPPED")
                })
            } catch (cleanup: Throwable) {
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    private fun menu(id: Long, action: String) {
        compose.onNodeWithTag("profile_card_$id").performScrollTo().performTouchInput { longClick() }
        if (action == "delete") compose.onNodeWithText("删除").performClick()
        else compose.onNodeWithTag(action).performClick()
    }

    private fun assertBefore(first: Long, second: Long) {
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag("profiles_order_$first").fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithTag("profiles_order_$second").fetchSemanticsNode().boundsInRoot.top)
    }

    // 作者 long：带名称标签和提示的输入框包含多段Text语义；精确比较EditableText才能核对草稿及首尾空格。
    private fun renameValue() = compose.onNodeWithTag("profile_rename_label").fetchSemanticsNode()
        .config[SemanticsProperties.EditableText].text

    private fun signature(file: File) = Triple(file.length(), file.lastModified(), MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) })

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(350)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }
}
