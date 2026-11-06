package com.lonnnnnng.flclash.ui

import android.Manifest
import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
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

/** 作者 long：正式主页面和真实核心缓存共用一条链路；只编辑原1.yaml临时副本的资源投影，不触碰正式源。 */
class MainProviderDocumentInstrumentedTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realSaveFailureKeepsDraftSelectionAndDiscardAcrossMainActivityRecreation() = runBlocking {
        fixture { session ->
            open(session.alias, edit = true)
            val original = requireNotNull(model().document.value?.document).document
            val cacheBefore = signature(session.cache)
            val draft = (1..90).joinToString("\n") { "# 第${it}行无效正文验收" } + "\nproxies: ["
            editor().performTextReplacement(draft)
            compose.onNodeWithTag("profile_provider_document_save").performClick()
            compose.waitUntil(20_000) { model().document.value?.let { !it.saving && it.error != null } == true }
            compose.onNodeWithTag("profile_provider_document_error")
                .assertTextEquals("内容无法被此 Provider 读取，请检查格式后重试")
            assertEquals(cacheBefore, signature(session.cache))
            val range = TextRange(draft.length - 10, draft.length)
            editor().performSemanticsAction(SemanticsActions.SetSelection) { it(range.start, range.end, false) }
            val retainedModel = model()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue(retainedModel.document.value?.content == draft)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.activityRule.scenario.recreate()
            assertSame(retainedModel, model())
            editor().assertTextEquals(draft)
            editor().assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, range))
            compose.onNodeWithTag("profile_provider_document_error").assertIsDisplayed()
            closeSoftKeyboard()
            capture("main_provider_failed_recreated")
            compose.onNodeWithTag("profile_provider_document_back").performClick()
            compose.onNodeWithText("保存更改？").assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("保存更改？").assertIsDisplayed()
            capture("main_provider_discard_recreated")
            compose.onNodeWithText("继续编辑").performClick()
            editor().assertTextEquals(draft)
            compose.onNodeWithTag("profile_provider_document_back").performClick()
            compose.onNodeWithText("放弃").performClick()
            returnedToPanel(session.alias)
            open(session.alias, edit = true)
            // 作者 long：正文可能含凭据，失败断言只输出布尔结果，不让完整正文进入JUnit或设备日志。
            assertTrue(model().document.value?.content == original.content)
            assertEquals(original.digest, model().document.value?.document?.document?.digest)
            assertEquals(cacheBefore, signature(session.cache))
            compose.onNodeWithTag("profile_provider_document_back").performClick()
            returnedToPanel(session.alias)
        }
    }

    @Test fun realCacheSaveReopensThroughMainPanelAndKeepsReferencedProfileSource() = runBlocking {
        fixture { session ->
            open(session.alias, edit = true)
            val before = requireNotNull(model().document.value?.document).document
            val sourceBefore = signature(session.source)
            val marker = "# main-provider-save-${UUID.randomUUID()}"
            val content = before.content + "\n$marker\n"
            editor().performTextReplacement(content)
            closeSoftKeyboard()
            compose.onNodeWithTag("profile_provider_document_save").performClick()
            compose.waitUntil(20_000) { model().document.value.let { it == null || !it.saving && it.error != null } }
            assertNull("真实缓存保存及回读后应退出正文", model().document.value)
            returnedToPanel(session.alias)
            assertTrue(session.cache.readText() == content)
            assertEquals(sourceBefore, signature(session.source))
            capture("main_provider_saved_panel")
            compose.activityRule.scenario.recreate()
            returnedToPanel(session.alias)
            open(session.alias, edit = true)
            val after = requireNotNull(model().document.value?.document).document
            assertTrue(after.content == content)
            assertNotEquals(before.digest, after.digest)
            assertFalse(model().document.value!!.dirty)
            compose.onNodeWithTag("profile_provider_document_back").performClick()
            compose.onNodeWithText("保存更改？").assertDoesNotExist()
            returnedToPanel(session.alias)
            assertEquals(sourceBefore, signature(session.source))
            capture("main_provider_saved_reopened_return")
        }
    }

    @Test fun realMrsReadOnlyPreviewScrollRecreatesAndReturnsToSameMainPanel() = runBlocking {
        fixture { session ->
            val providers = requireNotNull(model().providers.value.providers)
            // 作者 long：小于一屏的规则集不能证明滚动恢复；从原配置选择足量且有界的真实资源，不增造规则正文。
            val name = providers.filter { it.type == "Rule" && it.count in 200..3_000 }.minBy { it.count }.name
            open(name, edit = false)
            val before = requireNotNull(model().document.value?.document).document
            assertFalse(before.editable)
            assertFalse(model().document.value!!.editing)
            compose.onNodeWithText("MRS 规则只读预览").assertIsDisplayed()
            compose.onNodeWithTag("profile_provider_document_save").assertDoesNotExist()
            assertTrue(before.content.count { it == '\n' } >= 100)
            capture("main_provider_mrs_before_scroll")
            editor().performTouchInput { swipeUp() }
            val scroll = editor().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue("真实MRS内容应可滚动", scroll > 0f)
            capture("main_provider_mrs_scrolled")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("MRS 规则只读预览").assertIsDisplayed()
            assertEquals(before.digest, model().document.value?.document?.document?.digest)
            assertEquals(scroll, editor().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 1f)
            compose.onNodeWithTag("profile_provider_document_save").assertDoesNotExist()
            capture("main_provider_mrs_recreated")
            compose.onNodeWithTag("profile_provider_document_back").performClick()
            returnedToPanel(name)
            compose.onNodeWithTag("dashboard_profile_detail_back").performClick()
            compose.onNodeWithTag("page_dashboard").assertIsDisplayed()
            compose.onNodeWithTag("dashboard_profile_card").performScrollTo().performClick()
            returnedToPanel(session.alias)
            capture("main_provider_detail_reopened")
        }
    }

    private class Session(val alias: String, val source: File, val cache: File)

    private suspend fun fixture(block: suspend (Session) -> Unit) {
        assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture()
        val args = InstrumentationRegistry.getArguments()
        assertEquals("true", args.getString("kclashMainProviderAcceptance"))
        val expectedSha = requireNotNull(args.getString("kclashLocalYamlSha256"))
        require(Regex("[a-f0-9]{64}").matches(expectedSha))
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        repository.profilesLoaded.first { it }
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        val rows = entry.database().profiles().getAll()
        val selected = requireNotNull(repository.currentProfileId.value)
        val signatures = rows.associate { it.id to signature(repository.profileFile(it.id)) }
        val preferences = entry.database().appPreferences().getByPrefix("").associate { it.key to it.value }
        val original = rows.single { it.type == ProfileType.FILE && signature(repository.profileFile(it.id)).third == expectedSha }
        val bytes = repository.profileFile(original.id).readBytes()
        val source = bytes.decodeToString()
        val token = UUID.randomUUID().toString()
        val label = "main-provider-$token"
        val alias = "正文资源-$token"
        var resourceId: Long? = null
        var customId: Long? = null
        var cache: File? = null
        var failure: Throwable? = null
        try {
            compose.onNodeWithTag("nav_dashboard").performClick()
            resourceId = repository.addFileProfile("$label-source", bytes).id
            customId = repository.addCustomProfile(label).id
            repository.updateLocalProfileContent(customId, source)
            val groups = ProxyGroupDrafts.read(source).mapIndexed { index, group ->
                if (index == 0) ProxyGroupDrafts.withMembers(group, ProxyGroupDrafts.members(group) + ProxyGroupMember(alias, true)) else group
            }
            repository.saveCustomProxyGroups(customId, ProxyGroupsSave(groups, mapOf(alias to resourceId)), source) { true }
            cache = File(compose.activity.filesDir, "mihomo/providers/profiles/$resourceId-$expectedSha.yaml")
            entry.runtimeManager().selectProfile(customId, repository::selectProfile)
            entry.runtimeManager().getProxyGroups(customId).getOrThrow()
            assertTrue("只可编辑当前临时资源投影", cache.isFile && cache.readBytes().contentEquals(bytes))
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("dashboard_profile_card").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("dashboard_profile_card").performScrollTo().performClick()
            awaitProviders(customId)
            assertEquals(91, model().providers.value.providers!!.size)
            assertTrue(model().providers.value.providers!!.any { it.name == alias && it.vehicleType == "File" && it.count > 0 })
            block(Session(alias, repository.profileFile(resourceId), cache))
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try {
                // 作者 long：先退回主页面并恢复正式选择，再按唯一ID/标签清理测试副本；不按目录或名称前缀批量删除。
                val currentModel = model()
                compose.runOnIdle { currentModel.closeProvider() }
                runCatching { compose.onNodeWithTag("dashboard_profile_detail_back").performClick() }
                entry.runtimeManager().selectProfile(selected, repository::selectProfile)
                customId?.let { id ->
                    check(entry.database().profiles().getById(id)?.label == label)
                    repository.deleteProfile(id)
                }
                resourceId?.let { id ->
                    check(entry.database().profiles().getById(id)?.label == "$label-source")
                    repository.deleteProfile(id)
                    cache?.let { target ->
                        check(target.name == "$id-$expectedSha.yaml" && target.parentFile?.name == "profiles")
                        check(!target.exists() || target.delete())
                    }
                }
                assertEquals(rows, entry.database().profiles().getAll())
                assertEquals(signatures, rows.associate { it.id to signature(repository.profileFile(it.id)) })
                assertEquals(selected, repository.currentProfileId.value)
                assertTrue("正式偏好值必须保持，失败时不输出可能含网络信息的缓存正文",
                    preferences == entry.database().appPreferences().getByPrefix("").associate { it.key to it.value })
                assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("kclash_main_provider_preservation", "formal-count=${rows.size};source-sha256=$expectedSha;temporary-profiles-and-cache-removed=true;body-metadata-selection-preferences-preserved=true;runtime=STOPPED")
                })
            } catch (cleanup: Throwable) {
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    private fun model(): ProfileStatisticsViewModel {
        lateinit var value: ProfileStatisticsViewModel
        compose.runOnIdle { value = ViewModelProvider(compose.activity)[ProfileStatisticsViewModel::class.java] }
        return value
    }

    private fun awaitProviders(id: Long) {
        compose.waitUntil(20_000) { model().providers.value.let { it.profileId == id && it.providers != null && !it.isLoading && it.error == null } }
    }

    private fun returnedToPanel(name: String) {
        compose.onNodeWithTag("profile_provider_document").assertDoesNotExist()
        val id = requireNotNull(model().providers.value.profileId)
        awaitProviders(id)
        val index = model().providers.value.providers!!.indexOfFirst { it.name == name }
        assertTrue(index >= 0)
        compose.onNodeWithTag("profile_provider_row_$index").performScrollTo().assertIsDisplayed()
    }

    private fun open(name: String, edit: Boolean) {
        val index = model().providers.value.providers!!.indexOfFirst { it.name == name }
        assertTrue(index >= 0)
        if (edit) {
            compose.onNodeWithTag("profile_provider_menu_$index").performScrollTo().performClick()
            compose.onNodeWithTag("profile_provider_edit_$index").performClick()
        } else compose.onNodeWithTag("profile_provider_row_$index").performScrollTo().performClick()
        compose.waitUntil(20_000) { model().document.value?.loading == false }
        assertNull("真实Provider正文必须读取成功", model().document.value?.error)
        assertNotNull(model().document.value?.document)
    }

    private fun editor() = compose.onNodeWithTag("profile_provider_document_content")
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
