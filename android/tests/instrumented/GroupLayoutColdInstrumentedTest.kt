package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.test.rule.GrantPermissionRule
import android.Manifest

/** 作者 long：真实磁盘Room与主编辑器共同验收；ready后保持原进程，验证端不自动重放保存。 */
class GroupLayoutColdInstrumentedTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private fun entry() = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
    private fun marker() = File(compose.activity.cacheDir, "group-layout-cold.json")
    private fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun case(): String {
        assertPortraitDefaultFontConfiguration()
        assertEquals("com.lonnnnnng.flclash.motion", ins.targetContext.packageName)
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashGroupLayoutCold"))
        return requireNotNull(InstrumentationRegistry.getArguments().getString("kclashGroupLayoutCase")).also {
            require(it in listOf("saved", "intent", "committed"))
        }
    }
    private suspend fun waitDraftClear() {
        withTimeout(20_000) { while (entry().profileDraftStore().read() != null) delay(100) }
    }
    private suspend fun row(id: Long) = requireNotNull(entry().database().profiles().getById(id)).toDomain()
    private fun status(record: JsonObject) = ins.sendStatus(2, Bundle().apply { putString("kclash_group_layout_ready", record.toString()) })

    @Test fun prepareSavedMixedLayoutAndHoldForProcessTermination(): Unit = runBlocking {
        val mode = case(); val e = entry(); val repo = e.profileRepository()
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        assertTrue("仅隔离空库可创建测试配置", e.database().profiles().getAll().isEmpty())
        assertNull(e.profileDraftStore().read()); assertFalse(marker().exists())
        val bytes = readAuthorizedProxyProfile()
        val resource = repo.addFileProfile("layout-resource", bytes)
        val custom = repo.addCustomProfile("layout-custom")
        val record = JsonObject().apply { addProperty("case", mode); addProperty("id", custom.id); addProperty("resource", resource.id); addProperty("preparedPid", android.os.Process.myPid()) }
        marker().writeText(record.toString())
        repo.updateLocalProfileContent(custom.id, bytes.toString(Charsets.UTF_8))
        val source = repo.readLocalProfileContent(custom.id)
        val groups = ProxyGroupDrafts.read(source).toMutableList()
        val members = ProxyGroupMembers.read(groups[0])
        require(members.isNotEmpty())
        // 作者 long：资源和节点均来自授权原文件；只在自有CUSTOM副本中增加引用与有效筛选，原文件不改。
        groups[0] = ProxyGroupMembers.replace(groups[0], listOf(members.first(), GroupMemberEntry(GroupMemberKind.FILTER, "^"),
            GroupMemberEntry(GroupMemberKind.PROVIDER, "layout-resource")) + members.drop(1))
        repo.saveCustomProxyGroups(custom.id, ProxyGroupsSave(groups, mapOf("layout-resource" to resource.id)), source) { true }
        val beforeBody = repo.readLocalProfileContent(custom.id)
        val before = row(custom.id)
        record.addProperty("beforeLayout", sha(ProxyGroupMembers.layoutsJson(ProxyGroupMembers.restoreLayouts(ProxyGroupDrafts.read(beforeBody), before.overridesJson))))
        lateinit var model: ProfilesViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java]; model.openEditor(before) }
        compose.waitUntil(20_000) { model.editorState.value.overview != null }
        compose.runOnIdle { model.openSingleProxyGroupEditor(before, 0) }
        compose.waitUntil(20_000) { !model.proxyGroupsEditorState.value.isLoading && model.proxyGroupsEditorState.value.profile != null }
        compose.onNodeWithTag("connection_detail_handle").performClick()
        val list = compose.onNodeWithTag("group_form_list", useUnmergedTree = true)
        list.performScrollToNode(hasTestTag("group_member_label_1"))
        compose.onNodeWithTag("group_member_label_1", useUnmergedTree = true).assertTextEquals("^")
        val bounds = list.fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("group_member_row_${members.first().id}").fetchSemanticsNode().boundsInRoot
        val filter = compose.onNodeWithTag("group_member_row_FILTER:^").fetchSemanticsNode().boundsInRoot
        list.performTouchInput {
            down(Offset(200f, filter.center.y - bounds.top)); advanceEventTime(800)
            moveTo(Offset(200f, first.center.y - bounds.top), delayMillis = 400); up()
        }
        compose.onNodeWithTag("group_member_label_0", useUnmergedTree = true).assertTextEquals("^")
        val gate = if (mode == "saved") null else EditorCommitProbe.arm(if (mode == "intent") EditorCommitWindow.GROUPS_INTENT else EditorCommitWindow.GROUPS_COMMITTED)
        compose.onNodeWithTag("proxy_group_form_confirm").performClick()
        if (gate != null) {
            // 作者 long：保存由Sheet退场结束触发，必须推进测试帧钟，不能在等待门闩时冻结退场动画。
            compose.waitUntil(25_000) { EditorCommitProbe.visits(gate.window) > 0 }
            withTimeout(5_000) { gate.awaitReached() }
        }
        else compose.waitUntil(25_000) { model.proxyGroupsEditorState.value.profile == null }
        if (mode == "saved") {
            // 作者 long：已存布局后再改名和复制，验证旧名称键清理与副本继承，不能只验第一次保存前的改名。
            val saved = row(custom.id)
            var renamed = ProxyGroupMembers.restoreLayouts(ProxyGroupDrafts.read(repo.readLocalProfileContent(custom.id)), saved.overridesJson)
            val oldName = renamed.first().name
            renamed = renamed.mapIndexed { index, group -> if (index == 0) group.edit { it.addProperty("name", "Layout renamed") } else group }
            renamed = ProxyGroupIssues.duplicate(renamed, setOf(renamed.first().key), repo.readLocalProfileContent(custom.id))
            compose.runOnIdle { model.openProxyGroupsEditor(saved) }
            compose.waitUntil(20_000) { !model.proxyGroupsEditorState.value.isLoading && model.proxyGroupsEditorState.value.profile != null }
            compose.runOnIdle { model.saveProxyGroupsDraft(ProxyGroupsSave(renamed)) }
            compose.waitUntil(25_000) { model.proxyGroupsEditorState.value.profile == null }
            val result = row(custom.id)
            val layouts = JsonParser.parseString(result.overridesJson).asJsonObject.getAsJsonObject("groupMemberLayouts")
            assertFalse(layouts.has(oldName)); assertEquals(2, layouts.size())
            assertEquals(layouts["Layout renamed"], layouts["Layout renamed 副本"])
            val readback = ProxyGroupMembers.restoreLayouts(ProxyGroupDrafts.read(repo.readLocalProfileContent(custom.id)), result.overridesJson)
            assertEquals(ProxyGroupMembers.read(readback.first()), ProxyGroupMembers.read(readback[1]))
            compose.runOnIdle { model.closeEditor() }; waitDraftClear()
        } else {
            val saved = Gson().fromJson(e.profileDraftStore().read()!!.toString(Charsets.UTF_8), ProfileEditorDraftSnapshot::class.java).groups!!
            assertEquals(sha(beforeBody), sha(requireNotNull(saved.pendingContent)))
            assertNotNull(saved.pendingLayouts)
            assertEquals(mode == "committed", ProxyGroupMembers.matchesLayouts(row(custom.id).overridesJson, saved.pendingLayouts!!))
        }
        val body = repo.readLocalProfileContent(custom.id); val saved = row(custom.id)
        val restored = ProxyGroupMembers.restoreLayouts(ProxyGroupDrafts.read(body), saved.overridesJson)
        if (mode != "intent") assertEquals(GroupMemberKind.FILTER, ProxyGroupMembers.read(restored.first()).first().kind)
        assertFalse(body.contains("groupMemberLayouts")); assertFalse(body.contains("memberLayout")); assertFalse(body.contains("member-layout"))
        record.addProperty("body", sha(body)); record.addProperty("row", sha(Gson().toJson(saved))); record.addProperty("mtime", repo.profileFile(custom.id).lastModified())
        record.addProperty("order", sha(restored.joinToString { ProxyGroupMembers.read(it).joinToString { member -> member.id } }))
        record.addProperty("resourceSha", sha(repo.readLocalProfileContent(resource.id)))
        marker().writeText(record.toString()); status(record)
        awaitCancellation()
    }

    @Test fun verifyNewProcessReadsCommittedRoomWithoutReplayAndCleanFixture(): Unit = runBlocking {
        val mode = case(); val record = JsonParser.parseString(marker().readText()).asJsonObject
        assertEquals(mode, record["case"].asString); assertNotEquals(record["preparedPid"].asInt, android.os.Process.myPid())
        val e = entry(); val repo = e.profileRepository(); val id = record["id"].asLong; val resource = record["resource"].asLong
        lateinit var model: ProfilesViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java] }
        try {
            withTimeout(25_000) { model.awaitEditorDraftRestoration() }
            val fresh = row(id); val body = repo.readLocalProfileContent(id)
            assertEquals(record["body"].asString, sha(body)); assertEquals(record["row"].asString, sha(Gson().toJson(fresh)))
            assertEquals(record["mtime"].asLong, repo.profileFile(id).lastModified())
            val restored = ProxyGroupMembers.restoreLayouts(ProxyGroupDrafts.read(body), fresh.overridesJson)
            if (mode == "saved") {
                assertEquals(listOf("Layout renamed", "Layout renamed 副本"), restored.take(2).map { it.name })
                assertEquals(ProxyGroupMembers.read(restored[0]), ProxyGroupMembers.read(restored[1]))
                assertEquals(GroupMemberKind.FILTER, ProxyGroupMembers.read(restored[1]).first().kind)
            }
            assertEquals(record["order"].asString, sha(restored.joinToString { ProxyGroupMembers.read(it).joinToString { member -> member.id } }))
            assertEquals(0, EditorCommitProbe.visits(EditorCommitWindow.GROUPS_INTENT))
            if (mode == "intent") {
                assertNotNull(model.proxyGroupsEditorState.value.profile)
                assertTrue(model.proxyGroupsEditorState.value.error.orEmpty().contains("保存已中断"))
                compose.onNodeWithTag("proxy_group_row_0").performClick()
                compose.onNodeWithTag("connection_detail_handle").performClick()
                compose.onNodeWithTag("group_form_list", useUnmergedTree = true).performScrollToNode(hasTestTag("group_member_label_0"))
                compose.onNodeWithTag("group_member_label_0", useUnmergedTree = true).assertTextEquals("^")
                compose.runOnIdle { model.closeProxyGroupsEditor() }
            } else {
                assertNull(model.proxyGroupsEditorState.value.profile)
                compose.runOnIdle { model.openEditor(fresh) }
                compose.waitUntil(20_000) { model.editorState.value.overview != null }
                compose.runOnIdle { model.openSingleProxyGroupEditor(fresh, 0) }
                compose.waitUntil(20_000) { !model.proxyGroupsEditorState.value.isLoading && model.proxyGroupsEditorState.value.profile != null }
                compose.onNodeWithTag("connection_detail_handle").performClick()
                compose.onNodeWithTag("group_form_list", useUnmergedTree = true).performScrollToNode(hasTestTag("group_member_label_0"))
                compose.onNodeWithTag("group_member_label_0", useUnmergedTree = true).assertTextEquals("^")
                compose.runOnIdle { model.closeProxyGroupsEditor() }
            }
            assertEquals(record["resourceSha"].asString, sha(repo.readLocalProfileContent(resource)))
            ins.sendStatus(2, Bundle().apply { putString("kclash_group_layout_verified", "{\"case\":\"$mode\",\"pid\":${android.os.Process.myPid()},\"databaseReadback\":true,\"noReplay\":true}") })
        } finally {
            compose.runOnIdle { model.closeEditor() }; waitDraftClear()
            assertEquals("layout-custom", row(id).label); repo.deleteProfile(id)
            assertEquals("layout-resource", row(resource).label); repo.deleteProfile(resource)
            assertTrue(e.database().profiles().getAll().isEmpty()); assertTrue(marker().delete())
        }
    }

    @Test fun cleanupInterruptedFixture(): Unit = runBlocking {
        case(); val e = entry(); val repo = e.profileRepository()
        val record = JsonParser.parseString(marker().readText()).asJsonObject
        assertEquals(CoreRuntimeStatus.STOPPED, e.runtimeManager().state.value)
        lateinit var model: ProfilesViewModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java] }
        withTimeout(20_000) { model.awaitEditorDraftRestoration() }
        compose.runOnIdle { model.closeEditor() }; waitDraftClear()
        for ((key, label) in listOf("id" to "layout-custom", "resource" to "layout-resource")) {
            val id = record[key].asLong
            e.database().profiles().getById(id)?.let { assertEquals(label, it.label); repo.deleteProfile(id) }
        }
        assertTrue(e.database().profiles().getAll().isEmpty()); assertTrue(marker().delete())
    }
}
