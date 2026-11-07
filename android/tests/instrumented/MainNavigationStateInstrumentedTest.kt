package com.lonnnnnng.flclash.ui

import android.Manifest
import android.os.Bundle
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.core.ProxyGroupInfo
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

/** 作者 long：在正式主宿主中走跨页链路，只对原1.yaml的仓库副本改变浏览状态，结束后核对正式配置。 */
class MainNavigationStateInstrumentedTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun tabGroupAndScrollSurviveProfilesAndDashboardRoundTrips() = runBlocking {
        fixture { entry, id ->
            val group = openGroup()
            val last = group.all.last()
            compose.onNodeWithTag("proxy_group_members_${group.name}").performScrollToIndex(group.all.lastIndex)
            val anchor = node(group, last).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(compose.onNodeWithTag("proxy_group_members_${group.name}")
                .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
            capture("main_navigation_tab_before")
            for (destination in listOf("profiles", "dashboard")) {
                navigate(destination)
                compose.onNodeWithTag("page_$destination").assertIsDisplayed()
                navigate("proxy")
                compose.onNodeWithTag("proxy_group_tab_${group.name}").assertIsSelected()
                assertSameBounds(anchor, node(group, last).assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
                assertEquals(group.name, entry.database().profiles().getById(id)!!.currentGroupName)
                capture("main_navigation_tab_after_$destination")
            }
        }
    }

    @Test fun listLayoutAndScrollSurviveProfilesRoundTripAndActivityRecreation() = runBlocking {
        fixture { entry, id ->
            val group = openGroup()
            option("display_mode", "proxy_display_mode_LIST")
            val list = compose.onNodeWithTag("proxy_groups_list")
            val header = "proxy_list_group_header_${group.name}"
            list.performScrollToNode(hasTestTag(header))
            if (group.name !in model().state.value.unfoldSet) {
                compose.onNodeWithTag(header).performClick()
                compose.waitUntil(10_000) { group.name in model().state.value.unfoldSet }
            }
            val tag = "proxy_list_node_${group.name}_${group.all.last()}"
            list.performScrollToNode(hasTestTag(tag))
            val anchor = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val headerAnchor = compose.onNodeWithTag(header).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            reportListGeometry("before", tag)
            capture("main_navigation_list_before")
            navigate("profiles"); navigate("proxy")
            compose.onNodeWithTag("proxy_groups_list").assertIsDisplayed()
            assertSameBounds(anchor, compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
            assertSameBounds(headerAnchor, compose.onNodeWithTag(header).assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
            capture("main_navigation_list_after_profiles")

            // 作者 long：在真实Activity重建后重新取正式ViewModel，避免旧宿主引用或夹具回供掩盖恢复缺口。
            compose.activityRule.scenario.recreate()
            awaitProfile(id)
            compose.onNodeWithTag("proxy_groups_list").assertIsDisplayed()
            reportListGeometry("recreated", tag)
            capture("main_navigation_list_recreated")
            assertSameBounds(anchor, compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
            assertSameBounds(headerAnchor, compose.onNodeWithTag(header).assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
            assertEquals(ProxyDisplayMode.LIST, ProxyPageOptionsStore.load(compose.activity).displayMode)
            assertTrue(group.name in model().state.value.unfoldSet)
            assertEquals(group.name, entry.database().profiles().getById(id)!!.currentGroupName)
        }
    }

    @Test fun emptySearchSurvivesMainDestinationsAndRecreationThenClearRecoversNodes() = runBlocking {
        fixture { _, id ->
            val group = openGroup()
            val query = "kclash-navigation-no-result-${UUID.randomUUID()}"
            compose.onNodeWithTag("proxy_group_search_toggle").performClick()
            compose.onNodeWithTag("proxy_group_search").performTextInput(query)
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("proxy_groups_search_empty").assertIsDisplayed()
            navigate("profiles"); navigate("dashboard"); navigate("proxy")
            compose.onNodeWithTag("proxy_group_search").assertTextContains(query)
            compose.onNodeWithTag("proxy_group_tab_${group.name}").assertDoesNotExist()
            compose.onNodeWithTag("proxy_groups_search_empty").assertIsDisplayed()
            capture("main_navigation_search_returned")
            compose.activityRule.scenario.recreate()
            awaitProfile(id)
            compose.onNodeWithTag("proxy_group_search").assertTextContains(query)
            compose.onNodeWithTag("proxy_groups_search_empty").assertIsDisplayed()
            capture("main_navigation_search_recreated")

            // 作者 long：清除空态查询必须恢复原组节点，不能靠刷新或重新选择配置绕过搜索状态。
            compose.onNodeWithTag("proxy_search_clear").performClick()
            assertEquals("", compose.onNodeWithTag("proxy_group_search").fetchSemanticsNode()
                .config[SemanticsProperties.EditableText].text)
            compose.onNodeWithTag("proxy_groups_search_empty").assertDoesNotExist()
            node(group, group.all.first()).assertIsDisplayed()
            capture("main_navigation_search_cleared")
        }
    }

    private suspend fun fixture(block: suspend (ConnectionAcceptanceEntryPoint, Long) -> Unit) {
        assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture()
        val arguments = InstrumentationRegistry.getArguments()
        assertEquals("true", arguments.getString("kclashNavigationAcceptance"))
        val expectedSha = requireNotNull(arguments.getString("kclashLocalYamlSha256"))
        require(Regex("[a-f0-9]{64}").matches(expectedSha))
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository()
        repository.profilesLoaded.first { it }
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        val rows = entry.database().profiles().getAll()
        val originalId = requireNotNull(repository.currentProfileId.value)
        val signatures = rows.associate { it.id to signature(repository.profileFile(it.id)) }
        val preferences = entry.database().appPreferences().getByPrefix("").associate { it.key to it.value }
        val options = ProxyPageOptionsStore.load(compose.activity)
        // 作者 long：用外部传入的当前原1.yaml摘要查找已授权FILE正文，旧日期的硬编码哈希不能替代本轮输入。
        val source = rows.single { it.type == ProfileType.FILE && signature(repository.profileFile(it.id)).third == expectedSha }
        val bytes = repository.profileFile(source.id).readBytes()
        val label = "main-navigation-${UUID.randomUUID()}"
        var temporaryId: Long? = null
        var failure: Throwable? = null
        try {
            navigate("dashboard")
            ProxyPageOptionsStore.save(compose.activity, ProxyPageOptions(gridDensity = ProxyGridDensity.LOOSE, cardSize = ProxyNodeCardSize.LOOSE))
            temporaryId = repository.addFileProfile(label, bytes).id
            assertArrayEquals(bytes, repository.profileFile(temporaryId).readBytes())
            entry.runtimeManager().selectProfile(temporaryId, repository::selectProfile)
            awaitProfile(temporaryId)
            block(entry, temporaryId)
            assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try {
                // 作者 long：先退出代理页再恢复正式选择，避免退场中的分组记忆写入原Profile；UI异常不能跳过仓库收尾。
                runCatching { navigate("dashboard") }
                entry.runtimeManager().selectProfile(originalId, repository::selectProfile)
                temporaryId?.let { id ->
                    check(entry.database().profiles().getById(id)?.label == label)
                    repository.deleteProfile(id)
                }
                ProxyPageOptionsStore.save(compose.activity, options)
                assertEquals(rows, entry.database().profiles().getAll())
                assertEquals(signatures, rows.associate { it.id to signature(repository.profileFile(it.id)) })
                assertEquals(originalId, repository.currentProfileId.value)
                assertEquals(preferences, entry.database().appPreferences().getByPrefix("").associate { it.key to it.value })
                assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("kclash_navigation_preservation", "formal-count=${rows.size};source-sha256=$expectedSha;temporary-removed=true;profiles-body-metadata-selection-preferences-preserved=true;runtime=STOPPED")
                })
            } catch (cleanup: Throwable) {
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    private fun awaitProfile(id: Long) {
        compose.waitUntil(30_000) { model().state.value.let { it.profileId == id && it.groups.isNotEmpty() && !it.isLoading && it.error == null } }
    }

    private fun openGroup(): ProxyGroupInfo {
        navigate("proxy")
        val group = model().state.value.groups.last { !it.hidden && it.name != "GLOBAL" && it.all.size >= 6 }
        compose.onNodeWithTag("proxy_group_more").performClick()
        compose.onNodeWithTag("proxy_group_menu_${group.name}").performScrollTo().performClick()
        compose.onNodeWithTag("proxy_group_tab_${group.name}").assertIsSelected()
        compose.waitForIdle()
        return group
    }

    private fun navigate(destination: String) {
        compose.onNodeWithTag("nav_$destination").performClick()
        compose.waitForIdle()
    }

    private fun model(): ProxyGroupsViewModel {
        lateinit var value: ProxyGroupsViewModel
        compose.runOnIdle { value = ViewModelProvider(compose.activity)[ProxyGroupsViewModel::class.java] }
        return value
    }

    private fun option(menu: String, value: String) {
        compose.onNodeWithTag("proxy_tools").performClick()
        compose.onNodeWithTag("proxy_tools_$menu").performClick()
        compose.onNodeWithTag(value).performClick()
    }

    private fun node(group: ProxyGroupInfo, name: String) = compose.onNodeWithTag("proxy_node_${group.name}_$name")
    private fun signature(file: File) = Triple(file.length(), file.lastModified(), MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) })
    private fun assertSameBounds(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 1f); assertEquals(expected.top, actual.top, 1f)
        assertEquals(expected.right, actual.right, 1f); assertEquals(expected.bottom, actual.bottom, 1f)
    }

    private fun reportListGeometry(phase: String, anchorTag: String) {
        val list = compose.onNodeWithTag("proxy_groups_list").fetchSemanticsNode()
        val anchor = compose.onNodeWithTag(anchorTag).fetchSemanticsNode().boundsInRoot
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("kclash_navigation_geometry", "phase=$phase;viewport=${list.boundsInRoot};anchor=$anchor;scroll=${list.config[SemanticsProperties.VerticalScrollAxisRange].value()}")
        })
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(400)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        recordVisualPng(name, output.toByteArray())
    }
}
