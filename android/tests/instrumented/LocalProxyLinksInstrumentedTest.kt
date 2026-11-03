package com.lonnnnnng.flclash.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：正式页面与真实分享链接codec连接隔离仓库，原配置只读，截图用脱敏链接独立采集。 */
class LocalProxyLinksInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun realCodecAndViewModelKeepDraftOptionsRejectStaleSaveAndConfirmBatchReplacement() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, ConnectionAcceptanceEntryPoint::class.java)
        val originalProfiles = entry.database().profiles().getAll()
        val originalNodes = entry.profileRepository().localProxies()
        val runtime = entry.runtimeManager()
        val originalStatus = runtime.state.value
        val directory = File(context.cacheDir, "local-link-edit-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getApplicationContext(): Context = this; override fun getFilesDir() = directory }
        val db = Room.inMemoryDatabaseBuilder(context, KClashDatabase::class.java).build()
        val repository = ProfileRepository(isolated, db, object : SubscriptionClient {
            override suspend fun download(url: String): SubscriptionDownload = error("本测试禁止下载")
        })
        val bridge = CoreBridge()
        val store = ViewModelStore()
        var stage = "准备原文件节点"
        try {
            val definitions = ProxyGroupDrafts.root(readAuthorizedProxyProfile().toString(Charsets.UTF_8)).getAsJsonArray("proxies").map { it.asJsonObject }
            val encoded = bridge.encodeShareLinks(definitions)
            val first = definitions[encoded.indexOfFirst { it.isNotBlank() }.also { check(it >= 0) }].deepCopy().apply {
                addProperty("name", "链接原节点"); addProperty("tfo", true); addProperty("x-kclash-option", "保留扩展")
            }
            val a = LocalProxy("link-a", first.toString())
            val unsupported = LocalProxy("link-u", first.deepCopy().apply { addProperty("name", "链接目标"); addProperty("type", "direct") }.toString())
            assertTrue(bridge.encodeShareLinks(listOf(unsupported.fields)).single().isEmpty())
            val initial = listOf(a, unsupported)
            repository.saveLocalProxies(emptyList(), initial)
            lateinit var model: LocalProxiesViewModel
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                model = ViewModelProvider(store, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = LocalProxiesViewModel(repository, bridge, runtime) as T
                })[LocalProxiesViewModel::class.java]
            }
            suspend fun idle() { repeat(100) { if (!model.state.value.loading && !model.state.value.busy) return; delay(50) }; error("节点操作未结束：$stage") }
            idle()
            compose.setContent { val state by model.state.collectAsState(); KClashTheme {
                LocalProxiesPage(state, model::close, model::edit, model::save, model::dismissEditor, model::links,
                    model::applyLinks, model::cancelLinks, model::reorder, model::askDelete, model::delete, model::cancelDelete, model::confirmReplacement)
            } }
            compose.runOnIdle { model.open() }
            stage = "链接填充父页草稿"
            compose.onNodeWithTag("local_proxy_link-a").performClick()
            compose.onNodeWithContentDescription("从分享链接填写").performClick(); idle()
            val changed = a.fields.apply { addProperty("name", unsupported.name); remove("tfo"); remove("x-kclash-option") }
            val link = bridge.encodeShareLinks(listOf(changed)).single()
            compose.onNodeWithTag("local_proxy_links_content").performTextReplacement(link)
            compose.onNodeWithTag("local_proxy_links_save").performClick(); idle()
            val filled = checkNotNull(model.state.value.editor).value
            assertEquals(a.id, filled.id); assertEquals("链接目标-01", filled.name)
            assertEquals("保留扩展", filled.fields["x-kclash-option"].asString)
            assertEquals(initial, repository.localProxies())
            stage = "并发目录保护与失败重试"
            val external = unsupported.copy(id = "link-external", json = unsupported.fields.apply { addProperty("name", "外部新增") }.toString())
            repository.saveLocalProxies(initial, initial + external)
            compose.onNodeWithTag("local_proxy_save").performClick(); idle()
            assertNotNull(model.state.value.editor); assertTrue(model.state.value.error.orEmpty().contains("已变化"))
            assertEquals(initial + external, repository.localProxies())
            repository.saveLocalProxies(initial + external, initial)
            compose.onNodeWithTag("local_proxy_save").performClick(); idle()
            assertNull(model.state.value.editor)
            val saved = repository.localProxies()
            assertEquals(filled, saved.first())
            stage = "批量非法链接保留输入"
            compose.onNodeWithTag("local_proxies_links").performClick(); idle()
            val stable = checkNotNull(model.state.value.links).text
            val invalid = "$stable\n\nnot-a-link"
            compose.onNodeWithTag("local_proxy_links_content").performTextReplacement(invalid)
            compose.onNodeWithTag("local_proxy_links_save").performClick(); idle()
            compose.onNodeWithTag("local_proxy_links_error").assertTextContains("第 3 行分享链接无效")
            compose.onNodeWithTag("local_proxy_links_content").assertTextContains(invalid)
            assertEquals(saved, repository.localProxies())
            stage = "替换取消与确认"
            val renamedLink = bridge.encodeShareLinks(listOf(filled.fields.apply { addProperty("name", "批量新增") })).single()
            compose.onNodeWithTag("local_proxy_links_content").performTextReplacement(renamedLink)
            compose.onNodeWithTag("local_proxy_links_save").performClick(); idle()
            compose.onNodeWithText("将添加：批量新增", substring = true).assertIsDisplayed()
            compose.onNodeWithText("继续编辑", substring = false).performClick()
            assertEquals(saved, repository.localProxies())
            compose.onNodeWithTag("local_proxy_links_content").assertTextContains(renamedLink)
            compose.onNodeWithTag("local_proxy_links_save").performClick(); idle()
            compose.onNodeWithTag("local_proxy_links_replace").performClick(); idle()
            val replaced = repository.localProxies()
            assertEquals(2, replaced.size); assertEquals(unsupported, replaced.first())
            assertEquals("批量新增", replaced.last().name); assertNotEquals(a.id, replaced.last().id)
            assertEquals(replaced, LocalProxies.decode(db.appPreferences().get(LocalProxies.KEY)))
            assertEquals(originalProfiles, entry.database().profiles().getAll())
            assertEquals(originalNodes, entry.profileRepository().localProxies()); assertEquals(originalStatus, runtime.state.value)
        } catch (failure: Exception) {
            throw IllegalStateException("链接整页验收失败：$stage/${failure.javaClass.simpleName}\n" + failure.stackTrace.filter { it.className.startsWith("com.lonnnnnng") }.take(8).joinToString("\n") { "${it.className}.${it.methodName}:${it.lineNumber}" })
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
            db.close()
            check(directory.parentFile == context.cacheDir && directory.name.startsWith("local-link-edit-")); directory.deleteRecursively()
        }
    }

    @Test fun localDirectoryDragScrollsCancelsAndSavesOnceThenSearchDisablesSorting() {
        assertPortraitDefaultFontConfiguration()
        val original = (0..35).map { LocalProxy("$it", """{"name":"本地节点$it","type":"direct"}""") }
        var rows by mutableStateOf(original)
        var commits = 0
        compose.setContent { KClashTheme {
            LocalProxiesPage(LocalProxiesUiState(rows = rows, loading = false), {}, {}, {}, {}, {}, {}, {},
                { ids -> commits++; rows = ids.map { id -> original.first { it.id == id } } }, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("local_proxies_sort").performClick()
        val list = compose.onNodeWithTag("local_proxies_order_list")
        val bounds = list.fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("local_proxies_order_0").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        list.performTouchInput { down(Offset(90f, row.center.y - bounds.top)); advanceEventTime(800); moveTo(Offset(90f, bounds.height - 12f), delayMillis = 200) }
        compose.mainClock.advanceTimeBy(250); compose.waitForIdle()
        val first = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.mainClock.advanceTimeBy(250); compose.waitForIdle()
        assertTrue(list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > first)
        compose.runOnIdle { assertEquals(0, commits) }
        list.performTouchInput { cancel() }; compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(original, rows); assertEquals(0, commits) }
        list.performScrollToIndex(0)
        val next = compose.onNodeWithTag("local_proxies_order_1").fetchSemanticsNode().boundsInRoot
        list.performTouchInput { down(Offset(90f, row.center.y - bounds.top)); advanceEventTime(800); moveBy(Offset(0f, next.center.y - row.center.y), delayMillis = 200); up() }
        compose.runOnIdle { assertEquals(1, commits); assertEquals("1", rows.first().id) }
        compose.onNodeWithTag("local_proxies_order_1").assertIsDisplayed()
        capture("local_proxies_drag_saved")
        compose.onNodeWithTag("local_proxies_search").performTextReplacement("节点2")
        compose.onNodeWithTag("local_proxies_order_list").assertDoesNotExist()
        compose.onNodeWithTag("local_proxies_sort").assertIsNotEnabled()
    }

    @Test fun linkDraftErrorsReplacementAndDiscardRenderInBothThemes() {
        assertPortraitDefaultFontConfiguration()
        val row = LocalProxy("a", """{"name":"原节点","type":"direct"}""")
        val added = row.copy(id = "b", json = """{"name":"新增节点","type":"direct"}""")
        val draft = ProxyLinksDraft("fixture://original", listOf(row), listOf("fixture://original"), false)
        var dark by mutableStateOf(false)
        var state by mutableStateOf(LocalProxiesUiState(rows = listOf(row), loading = false, links = draft))
        var confirms = 0
        compose.setContent { KClashTheme(darkTheme = dark) { key(dark) {
            LocalProxiesPage(state, {}, {}, {}, {}, {}, { state = state.copy(error = "第 2 行分享链接无效") },
                { state = state.copy(links = null) }, {}, {}, {}, { state = state.copy(replacement = null) }, { confirms++ })
        } } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; state = LocalProxiesUiState(rows = listOf(row), loading = false, links = draft) }
            compose.onNodeWithTag("local_proxy_links_content").performTextReplacement("fixture://changed\ninvalid")
            compose.onNodeWithTag("local_proxy_links_save").performClick()
            compose.onNodeWithTag("local_proxy_links_error").assertIsDisplayed()
            capture("local_proxy_links_error_$theme")
            compose.onNodeWithTag("local_proxy_links_back").performClick()
            compose.onNodeWithText("放弃修改？").assertIsDisplayed()
            capture("local_proxy_links_discard_$theme")
            compose.onNodeWithText("继续编辑", substring = false).performClick()
            compose.runOnIdle { state = state.copy(replacement = listOf(added), removed = listOf(row.name), error = null) }
            compose.onNodeWithText("将添加：新增节点", substring = true).assertIsDisplayed()
            capture("local_proxy_links_replacement_$theme")
            compose.onNodeWithText("继续编辑", substring = false).performClick()
            compose.onNodeWithTag("local_proxy_links_content").assertTextContains("fixture://changed\ninvalid")
            compose.runOnIdle { assertEquals(0, confirms) }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(350)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        recordVisualPng(name, java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }
}
