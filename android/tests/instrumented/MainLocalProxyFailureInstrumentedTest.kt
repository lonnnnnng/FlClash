package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
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

/** 作者 long：正式主页面、Hilt、Mihomo和Room贯通；只使用授权原文副本，失败触发器限定本地目录键且finally回收。 */
class MainLocalProxyFailureInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(compose.activity)[LocalProxiesViewModel::class.java]
    private var stage = "准备"

    @Test fun formalNodeMenuDatabaseFailureRetriesThroughSnackbarAndPersistsOnce() = runBlocking {
        fixture { s ->
            failWrites(s, true)
            val gate = LocalProxyOperationProbe.arm(LocalProxyOperationWindow.BEFORE_SAVE)
            addThroughMenu(s); awaitGate(gate)
            stage = "保存等待重复点击"
            addThroughMenu(s)
            assertEquals(1, gate.visits(LocalProxyOperationWindow.BEFORE_SAVE))
            assertTrue(s.repository.localProxies() == s.originalNodes)
            gate.release()
            awaitFeedback("添加未完成，请重新打开节点菜单后重试")
            assertEquals("必须命中真实SQL触发器", 1, gate.databaseAborts())
            assertTrue(s.repository.localProxies() == s.originalNodes)
            capture("formal_local_add_failed")
            failWrites(s, false)
            stage = "正式菜单明确重试"
            addThroughMenu(s); awaitIdle()
            val added = added(s)
            assertEquals(2, gate.visits(LocalProxyOperationWindow.BEFORE_SAVE))
            awaitFeedback("${added.name} 已添加到本地代理")
            capture("formal_local_add_retry_saved")
            compose.activityRule.scenario.recreate(); awaitIdle()
            openDirectory()
            compose.onNodeWithTag("local_proxy_${added.id}").performScrollTo().assertIsDisplayed()
            assertTrue(added(s).fields == s.expectedFields)
            capture("formal_local_add_recreated")
        }
    }

    @Test fun formalNodeMenuProfileSwitchBeforeAcceptanceRejectsWithoutWriting() = runBlocking {
        switchProfile(accepted = false)
    }

    @Test fun formalNodeMenuProfileSwitchAfterAcceptanceCommitsFrozenNodeOnce() = runBlocking {
        switchProfile(accepted = true)
    }

    private suspend fun switchProfile(accepted: Boolean) = fixture { s ->
        val second = s.addFile("切换目标")
        val gate = LocalProxyOperationProbe.arm(if (accepted) LocalProxyOperationWindow.BEFORE_SAVE else LocalProxyOperationWindow.BEFORE_VALIDATE)
        addThroughMenu(s); awaitGate(gate)
        stage = "真实配置卡片切换"
        selectThroughCard(s, second)
        assertTrue(s.repository.localProxies() == s.originalNodes)
        gate.release()
        if (accepted) {
            awaitIdle()
            val added = added(s)
            awaitFeedback("${added.name} 已添加到本地代理")
            assertEquals(1, gate.visits(LocalProxyOperationWindow.BEFORE_SAVE))
        } else {
            awaitFeedback("配置已切换，请重新打开节点菜单")
            assertTrue(s.repository.localProxies() == s.originalNodes)
            assertEquals(0, gate.visits(LocalProxyOperationWindow.BEFORE_SAVE))
        }
        assertEquals(second, s.repository.currentProfileId.value)
        capture(if (accepted) "formal_local_switch_accepted" else "formal_local_switch_rejected")
        compose.activityRule.scenario.recreate(); awaitIdle()
        assertEquals(second, s.repository.currentProfileId.value)
        if (accepted) assertTrue(added(s).fields == s.expectedFields)
        else assertTrue(s.repository.localProxies() == s.originalNodes)
    }

    @Test fun formalLocalProxyDeleteQueriesLongCustomReferencesCancelsThenRetriesRealRoomFailure() = runBlocking {
        fixture { s ->
            addThroughMenu(s); awaitIdle()
            val node = added(s)
            stage = "创建原正文引用副本"
            val labels = (1..40).map { index ->
                val label = "引用验收-${s.token}-${index.toString().padStart(2, '0')}-" + "很长的配置名称".repeat(4)
                val row = s.repository.addCustomProfile(label)
                s.owned[row.id] = label to ProfileType.CUSTOM
                // 作者 long：复制同一授权原文保留真实按名称引用，不能用注入查询结果冒充Repository扫描。
                s.repository.updateLocalProfileContent(row.id, s.bytes.decodeToString())
                s.bodies[row.id] = signature(s.repository.profileFile(row.id))
                label
            }
            assertEquals(labels.toSet(), s.repository.localProxyUsers(setOf(node.name)).toSet())
            openDirectory()
            compose.onNodeWithTag("local_proxy_${node.id}").performScrollTo().onChildren().filterToOne(isToggleable()).performClick()
            compose.onActionMarker("删除选中节点").performClick(); awaitIdle()
            assertEquals(labels.toSet(), model().state.value.users.toSet())
            compose.onNodeWithText(labels.first()).assertIsDisplayed(); capture("formal_local_refs_first")
            compose.onNodeWithText(labels.last()).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("local_proxy_delete_confirm").assertIsDisplayed(); capture("formal_local_refs_last")
            compose.onNodeWithTag("local_proxy_delete_cancel").performClick(); awaitIdle()
            compose.onNodeWithText("已选择 1 项").assertIsDisplayed()
            assertTrue(added(s).fields == s.expectedFields)
            capture("formal_local_refs_cancel")
            compose.onActionMarker("删除选中节点").performClick(); awaitIdle()
            failWrites(s, true)
            val gate = LocalProxyOperationProbe.arm(LocalProxyOperationWindow.BEFORE_SAVE)
            compose.onNodeWithTag("local_proxy_delete_confirm").performClick(); awaitGate(gate)
            stage = "真实删除等待与取消隔离"
            compose.onNodeWithTag("local_proxy_delete_pending").assertIsDisplayed()
            compose.onNodeWithTag("local_proxy_delete_confirm").assertIsNotEnabled().performClick()
            compose.onNodeWithTag("local_proxy_delete_cancel").assertIsNotEnabled().performClick()
            assertEquals(1, gate.visits(LocalProxyOperationWindow.BEFORE_SAVE))
            capture("formal_local_refs_pending")
            gate.release(); awaitIdle()
            compose.onNodeWithTag("local_proxy_delete_error").assertTextContains("删除失败，请重试；配置引用会保留").assertIsDisplayed()
            assertEquals("必须命中真实SQL触发器", 1, gate.databaseAborts())
            assertTrue(added(s).fields == s.expectedFields)
            capture("formal_local_refs_failed")
            compose.activityRule.scenario.recreate(); awaitIdle()
            compose.onNodeWithTag("local_proxy_delete_error").assertIsDisplayed()
            capture("formal_local_refs_failed_recreated")
            failWrites(s, false)
            compose.onNodeWithTag("local_proxy_delete_confirm").performClick(); awaitIdle()
            assertTrue(s.repository.localProxies() == s.originalNodes)
            assertEquals(2, gate.visits(LocalProxyOperationWindow.BEFORE_SAVE))
            assertEquals(labels.toSet(), s.repository.localProxyUsers(setOf(node.name)).toSet())
            assertTrue(s.bodies.all { (id, body) -> signature(s.repository.profileFile(id)) == body })
            compose.onNodeWithTag("local_proxy_${node.id}").assertDoesNotExist()
            capture("formal_local_refs_deleted")
        }
    }

    private class Session(val entry: ConnectionAcceptanceEntryPoint, val bytes: ByteArray, val originalNodes: List<LocalProxy>) {
        val repository get() = entry.profileRepository()
        val token = UUID.randomUUID().toString().replace("-", "")
        val owned = linkedMapOf<Long, Pair<String, ProfileType>>()
        val bodies = linkedMapOf<Long, Triple<Long, Long, String>>()
        val trigger = "local_proxy_failure_$token"
        var sourceId = 0L
        lateinit var group: String
        lateinit var name: String
        lateinit var expectedFields: JsonObject
        suspend fun addFile(suffix: String): Long {
            val label = "正式代理验收-$token-$suffix"
            return repository.addFileProfile(label, bytes).id.also { owned[it] = label to ProfileType.FILE }
        }
    }

    private suspend fun fixture(block: suspend (Session) -> Unit) {
        assertPortraitDefaultFontConfiguration(); prepareTouchVisualCapture()
        val args = InstrumentationRegistry.getArguments()
        assertEquals("true", args.getString("kclashMainLocalProxyAcceptance"))
        val expected = requireNotNull(args.getString("kclashLocalYamlSha256"))
        require(Regex("[a-f0-9]{64}").matches(expected))
        val entry = EntryPointAccessors.fromApplication(compose.activity.application, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository(); repository.profilesLoaded.first { it }; awaitIdle()
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        check(entry.localProxyDraftStore().read() == null) { "已有本地代理草稿，不能覆盖" }
        val rows = entry.database().profiles().getAll()
        val selected = requireNotNull(repository.currentProfileId.value)
        val files = rows.associate { it.id to signature(repository.profileFile(it.id)) }
        val scripts = entry.database().scripts().getAll()
        val scriptFiles = scripts.associate { it.id to signature(entry.scriptRepository().scriptFile(it.id)) }
        val preferences = entry.database().appPreferences().getByPrefix("")
        val options = ProxyPageOptionsStore.load(compose.activity)
        val triggers = temporaryTriggers(entry)
        val original = rows.single { it.type == ProfileType.FILE && files.getValue(it.id).third == expected }
        val s = Session(entry, repository.profileFile(original.id).readBytes(), repository.localProxies())
        var failure: Throwable? = null
        try {
            ProxyPageOptionsStore.save(compose.activity, ProxyPageOptions())
            s.sourceId = s.addFile("原节点")
            selectThroughCard(s, s.sourceId)
            val snapshot = entry.runtimeManager().getProxyGroups(s.sourceId).getOrThrow()
            val groups = rows.filter { it.type == ProfileType.CUSTOM }.flatMap {
                ProxyGroupDrafts.read(repository.readLocalProfileContent(it.id)).map { group -> group.name }
            }.toSet()
            val referenced = LocalProxies.referenced(s.bytes.decodeToString())
            var found = false
            for (group in visibleProxyGroups(snapshot.groups, entry.runtimeManager().outboundMode.value)) {
                for (name in group.all.filter { it in referenced && it !in LocalProxies.reserved && snapshot.groups.none { g -> g.name == it } }) {
                    val plan = planLocalProxyAddition(entry.runtimeManager().getProxyDefinition(s.sourceId, name), s.originalNodes, groups)
                    if (plan.added && plan.row.name == name) {
                        s.group = group.name; s.name = name; s.expectedFields = plan.row.fields; found = true; break
                    }
                }
                if (found) break
            }
            check(found) { "授权原文没有可新增且保持原名的引用节点" }
            block(s)
        } catch (error: Throwable) {
            // 作者 long：Compose树和原生异常可能含节点凭据；失败日志只保留阶段、异常类型和本项目位置。
            failure = safeFailure(error); throw failure
        } finally {
            val cleanupErrors = mutableListOf<Throwable>()
            suspend fun cleanupStep(action: suspend () -> Unit) {
                try { action() } catch (error: Throwable) { cleanupErrors += safeFailure(error) }
            }
            // 作者 long：页面关闭失败不能阻断已停止写入后的数据回收；各步骤独立记录失败，原失败和清理异常都保留。
            // 作者 long：菜单动作在退场帧后执行，先推进已点击的菜单，再释放等待点，不能在尚未执行时误判没有在途写入。
            cleanupStep { compose.waitForIdle() }
            cleanupStep { failWrites(s, false) }
            LocalProxyOperationProbe.reset()
            cleanupStep { awaitIdle() }
            val writesStopped = !model().state.value.busy
            cleanupStep {
                if (model().state.value.isOpen) {
                    compose.runOnIdle { model().close() }; awaitIdle()
                    compose.waitUntil(15_000) { !model().state.value.isOpen }
                }
                if (compose.onAllNodesWithTag("profile_editor_back").fetchSemanticsNodes().isNotEmpty())
                    compose.onNodeWithTag("profile_editor_back").performClick()
            }
            cleanupStep { compose.onNodeWithTag("nav_dashboard").performClick(); compose.waitForIdle() }
            // 作者 long：仍有写入在途时不能并发删夹具或回写旧目录，保留现场并明确失败比掩盖竞态安全。
            if (writesStopped && !model().state.value.busy) {
                cleanupStep { entry.runtimeManager().selectProfile(selected, repository::selectProfile) }
                s.owned.forEach { (id, identity) -> cleanupStep {
                    val row = requireNotNull(entry.database().profiles().getById(id))
                    check(row.label == identity.first && row.type == identity.second && rows.none { it.id == id })
                    cleanupStep {
                        s.bodies[id]?.let { assertEquals(it, signature(repository.profileFile(id))) }
                        if (identity.second == ProfileType.FILE) assertArrayEquals(s.bytes, repository.profileFile(id).readBytes())
                    }
                    repository.deleteProfile(id)
                } }
                cleanupStep {
                val current = repository.localProxies()
                val added = current.filter { row -> s.originalNodes.none { it.id == row.id } }
                check(added.size <= 1 && added.all { it.fields == s.expectedFields })
                check(current.filter { row -> s.originalNodes.any { it.id == row.id } } == s.originalNodes)
                if (current != s.originalNodes) repository.saveLocalProxies(current, s.originalNodes)
                // 作者 long：只恢复本轮主动写入的目录键；原来没有键时删除新增键，不用全量偏好回写掩盖其他异常。
                val local = preferences.singleOrNull { it.key == LocalProxies.KEY }
                if (local == null) entry.database().appPreferences().delete(LocalProxies.KEY)
                else entry.database().appPreferences().put(local)
                }
            } else cleanupErrors += AssertionError("仍有本地目录写入，保留本轮夹具现场")
            cleanupStep { ProxyPageOptionsStore.save(compose.activity, options) }
            cleanupStep {
                assertTrue("正式配置元数据保持", rows == entry.database().profiles().getAll())
                assertEquals(files, rows.associate { it.id to signature(repository.profileFile(it.id)) })
                assertTrue("正式脚本元数据保持", scripts == entry.database().scripts().getAll())
                assertEquals(scriptFiles, scripts.associate { it.id to signature(entry.scriptRepository().scriptFile(it.id)) })
                assertTrue("正式偏好保持", preferences.associate { it.key to it.value } == entry.database().appPreferences().getByPrefix("").associate { it.key to it.value })
                assertTrue(repository.localProxies() == s.originalNodes)
                assertEquals(selected, repository.currentProfileId.value)
                assertEquals(triggers, temporaryTriggers(entry))
                assertNull(entry.localProxyDraftStore().read())
                assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("kclash_main_local_preservation", "formal-count=${rows.size};profiles-scripts-bodies-preferences-selection-preserved=true;temporary-removed=true;triggers-restored=true;runtime=STOPPED")
                })
            }
            if (cleanupErrors.isNotEmpty()) {
                val report = failure ?: cleanupErrors.removeAt(0)
                cleanupErrors.forEach(report::addSuppressed)
                if (failure == null) throw report
            }
        }
    }

    private suspend fun selectThroughCard(s: Session, id: Long) {
        compose.onNodeWithTag("nav_profiles").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_card_$id").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile_card_$id").performScrollTo().performClick()
        compose.waitUntil(15_000) { s.repository.currentProfileId.value == id }
    }

    private fun addThroughMenu(s: Session) {
        stage = "正式节点菜单"
        compose.onNodeWithTag("nav_proxy").performClick()
        val groupTag = "proxy_group_tab_${s.group}"
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(groupTag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(groupTag).performScrollTo().performClick()
        val card = "proxy_node_${s.group}_${s.name}"
        compose.onNodeWithTag("proxy_group_members_${s.group}").performScrollToNode(hasTestTag(card))
        compose.onNodeWithTag(card).performTouchInput { longClick() }
        compose.onNodeWithTag("${card}_menu_add_local").performClick()
        compose.waitForIdle()
    }

    private suspend fun added(s: Session): LocalProxy {
        val current = s.repository.localProxies()
        assertEquals(s.originalNodes.size + 1, current.size)
        return current.single { row -> s.originalNodes.none { it.id == row.id } }.also {
            assertTrue("冻结原节点定义保持", it.fields == s.expectedFields)
            assertFalse(it.fields.has("dialer-proxy"))
        }
    }

    private fun openDirectory() {
        stage = "正式资源入口"
        compose.onNodeWithTag("nav_tools").performClick()
        compose.waitUntil(10_000) { model().state.value.message == null && model().state.value.error == null }
        compose.onNodeWithTag("tools_item_resources_settings").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("本地代理", substring = false).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("本地代理", substring = false).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("local_proxies_page").fetchSemanticsNodes().isNotEmpty() }
        awaitIdle()
    }

    private suspend fun failWrites(s: Session, enabled: Boolean) = s.entry.database().withTransaction {
        val db = s.entry.database().openHelper.writableDatabase
        db.execSQL("DROP TRIGGER IF EXISTS temp.${s.trigger}")
        if (enabled) db.execSQL("CREATE TEMP TRIGGER ${s.trigger} BEFORE INSERT ON app_preferences WHEN NEW.key='global_local_proxies' BEGIN SELECT RAISE(ABORT,'fixture-local-save-failure'); END")
    }

    private suspend fun temporaryTriggers(entry: ConnectionAcceptanceEntryPoint): List<String> = entry.database().withTransaction {
        entry.database().openHelper.writableDatabase.query("SELECT name FROM sqlite_temp_master WHERE type='trigger' ORDER BY name").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
    }

    private fun awaitGate(gate: LocalProxyOperationProbe.Gate) {
        // 作者 long：真实菜单退场依赖Compose帧；直接await会冻结测试时钟，不能把尚未分发的点击误判为业务挂起。
        compose.waitUntil(30_000) { gate.isReached() }
    }
    private fun awaitIdle() { compose.waitForIdle(); compose.waitUntil(30_000) { !model().state.value.loading && !model().state.value.busy }; compose.waitForIdle() }
    private fun awaitFeedback(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text, substring = false).assertIsDisplayed()
    }
    private fun safeFailure(error: Throwable) = AssertionError("正式本地代理验收失败：$stage/${error.javaClass.simpleName}\n" +
        error.stackTrace.filter { it.className.startsWith("com.lonnnnnng") }.take(8).joinToString("\n") { "${it.className}.${it.methodName}:${it.lineNumber}" })
    private fun signature(file: File) = Triple(file.length(), file.lastModified(), MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) })
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(650)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle()
        recordVisualPng(name, output.toByteArray())
    }
}
