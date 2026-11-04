package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModelStore
import com.google.gson.*
import com.lonnnnnng.flclash.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：用可暂停的正式边界替身检查冷恢复和提交顺序，不用重新打开编辑页掩盖丢失的会话。 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalProxyDraftRecoveryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @After fun tearDown() = Dispatchers.resetMain()
    private fun model(ops: Operations, drafts: MemoryDrafts): Pair<LocalProxiesViewModel, ViewModelStore> {
        Dispatchers.setMain(dispatcher)
        val model = LocalProxiesViewModel(ops, drafts, dispatcher)
        return model to ViewModelStore().apply { put("local", model) }
    }

    @Test fun recoveryKeepsCollectionCredentialsAndDirectoryConflictBaseline() = runTest {
        val ops = Operations(); val drafts = MemoryDrafts()
        val (first, owner) = model(ops, drafts); advanceUntilIdle()
        first.open(); first.edit(ops.rows.value.first())
        val field = ProxyField("headers", "headers", "请求头", ProxyFieldKind.MAP)
        val ui = EditorSaveableState(mapOf("scroll" to listOf(EditorSavedValue.capture(42))))
        val originalDraft = first.state.value.editor!!
        first.updateEditor(originalDraft.copy(collection = LocalProxyCollectionDraft(field, listOf("Authorization" to PRIVATE)), uiDraft = ui))
        first.updateUiDraft(first.state.value.session, ui); advanceUntilIdle(); owner.clear()
        val external = node.copy(id = "external", json = """{"name":"external","type":"direct"}""")
        ops.rows.value = listOf(node, external)
        val (second, owner2) = model(ops, drafts); advanceUntilIdle()
        assertTrue(second.state.value.isOpen)
        assertEquals(listOf("Authorization" to PRIVATE), second.state.value.editor!!.collection!!.values)
        assertEquals(ui, second.state.value.editor!!.uiDraft)
        val changed = node.copy(json = """{"name":"changed","type":"direct"}""")
        second.save(changed); advanceUntilIdle()
        assertEquals(listOf(node, external), ops.rows.value)
        assertTrue(second.state.value.error!!.contains("已变化"))
        second.updateEditor(second.state.value.editor!!.copy(uiDraft = ui))
        assertNotNull(second.state.value.error)
        second.dismissEditor(); advanceUntilIdle()
        second.edit(node)
        second.updateEditor(originalDraft.copy(collection = LocalProxyCollectionDraft(field, listOf("stale" to PRIVATE))))
        assertNull(second.state.value.editor!!.collection)
        second.dismissEditor(); advanceUntilIdle(); second.close(); advanceUntilIdle()
        assertNull(drafts.bytes); owner2.clear()
    }

    @Test fun linksInputAndReplacementConfirmationRecoverWithoutWritingDirectory() = runTest {
        val ops = Operations(); val drafts = MemoryDrafts()
        val (first, owner) = model(ops, drafts); advanceUntilIdle(); first.open(); first.links(); advanceUntilIdle()
        val ui = EditorSaveableState(mapOf("text" to listOf(EditorSavedValue.capture(PRIVATE))))
        first.updateLinksUiDraft(first.state.value.links!!.session, ui)
        first.applyLinks("incoming"); advanceUntilIdle()
        assertNotNull(first.state.value.replacement); assertEquals(listOf(node), ops.rows.value)
        owner.clear()
        val (second, owner2) = model(ops, drafts); advanceUntilIdle()
        assertEquals(ui, second.state.value.links!!.uiDraft)
        assertNotNull(second.state.value.replacement); assertEquals(listOf("original"), second.state.value.removed)
        assertEquals(0, ops.saves)
        second.cancelDelete(); advanceUntilIdle(); assertNotNull(second.state.value.links)
        second.cancelLinks(); advanceUntilIdle(); second.close(); advanceUntilIdle(); assertNull(drafts.bytes); owner2.clear()
    }

    @Test fun lateUiCheckpointPreservesLatestCollectionErrorAndRejectsClosedSession() = runTest {
        val ops = Operations(); val drafts = MemoryDrafts()
        val (first, owner) = model(ops, drafts); advanceUntilIdle(); first.open(); first.edit(node)
        val session = first.state.value.editor!!.session
        val field = ProxyField("alpn", "alpn", "ALPN", ProxyFieldKind.LIST)
        val collection = LocalProxyCollectionDraft(field, listOf("" to "h2", "" to "http/1.1"))
        first.updateEditor(first.state.value.editor!!.copy(collection = collection))
        first.reportDraftFailure()
        val error = first.state.value.error
        val ui = EditorSaveableState(mapOf("scroll" to listOf(EditorSavedValue.capture(42))))
        first.updateEditorUiDraft(session, ui)
        assertEquals(collection, first.state.value.editor!!.collection)
        assertEquals(error, first.state.value.error)
        assertEquals(ui, first.state.value.editor!!.uiDraft)
        advanceUntilIdle(); owner.clear()
        val (second, owner2) = model(ops, drafts); advanceUntilIdle()
        assertEquals(collection, second.state.value.editor!!.collection)
        second.dismissEditor(); advanceUntilIdle(); second.edit(node)
        second.updateEditorUiDraft(session, ui)
        assertNull(second.state.value.editor!!.uiDraft)
        second.dismissEditor(); advanceUntilIdle(); second.close(); advanceUntilIdle(); owner2.clear()
    }

    @Test fun pendingSaveRecoveryDistinguishesCommittedAndInterruptedWithoutAutomaticRetry() = runTest {
        for (committed in listOf(false, true)) {
            val ops = Operations(); val drafts = MemoryDrafts()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            ops.onSave = { next -> if (committed) ops.rows.value = next; entered.complete(Unit); release.await() }
            val (first, owner) = model(ops, drafts); advanceUntilIdle(); first.open(); first.edit(node)
            first.save(node.copy(json = """{"name":"changed","type":"direct"}""")); entered.await()
            first.save(node); assertEquals(1, ops.saves)
            owner.clear(); advanceUntilIdle()
            val (second, owner2) = model(ops, drafts); advanceUntilIdle()
            assertTrue(second.state.value.isOpen); assertEquals(1, ops.saves)
            if (committed) assertNull(second.state.value.editor) else assertNotNull(second.state.value.editor)
            assertNotNull(second.state.value.error)
            if (!committed) second.dismissEditor()
            advanceUntilIdle(); second.close(); advanceUntilIdle()
            assertNull(drafts.bytes); assertEquals(if (committed) 1 else 0, ops.applies); owner2.clear()
        }
    }

    @Test fun failedDraftWritePreventsFormalSaveAndFailedClearKeepsPageForRetry() = runTest {
        val ops = Operations(); val drafts = MemoryDrafts()
        val (first, owner) = model(ops, drafts); advanceUntilIdle(); first.open(); first.edit(node)
        drafts.failWrite = true
        first.save(node.copy(json = """{"name":"changed","type":"direct"}""")); advanceUntilIdle()
        assertEquals(0, ops.saves); assertNotNull(first.state.value.editor); assertFalse(first.state.value.busy)
        drafts.failWrite = false; first.dismissEditor(); advanceUntilIdle()
        drafts.failClear = true; first.close(); advanceUntilIdle()
        assertTrue(first.state.value.isOpen); assertNotNull(first.state.value.error)
        drafts.failClear = false; first.close(); advanceUntilIdle()
        assertFalse(first.state.value.isOpen); assertNull(drafts.bytes); owner.clear()
    }

    @Test fun delayedAcceptedWriteCannotResurrectClosedSessionAfterViewModelDestruction() = runTest {
        val ops = Operations(); val drafts = MemoryDrafts()
        val (first, owner) = model(ops, drafts); advanceUntilIdle(); first.open(); advanceUntilIdle()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        drafts.onWrite = { bytes -> if (bytes != null && !entered.isCompleted) withContext(NonCancellable) { entered.complete(Unit); release.await() } }
        first.edit(node); entered.await(); first.close(); owner.clear()
        release.complete(Unit); advanceUntilIdle()
        assertNull(drafts.bytes)
    }

    @Test fun localDraftStringsNeverExposeCredentials() {
        val draft = LocalProxyDraft(node, node)
        val links = ProxyLinksDraft(PRIVATE, listOf(node), listOf(PRIVATE), true)
        val state = LocalProxiesUiState(editor = draft, links = links, error = PRIVATE)
        listOf(draft, links, state, LocalProxyDraftSnapshot(state = state, opened = listOf(node), expected = listOf(node), seenNames = setOf(PRIVATE)))
            .forEach { assertFalse(it.toString().contains(PRIVATE)) }
    }

    @Test fun failedRecoveryPreservesCiphertextAndOpeningAgainRetriesOriginalSession() = runTest {
        for (storeFailure in listOf(false, true)) {
            val ops = Operations(); val drafts = MemoryDrafts()
            val (first, owner) = model(ops, drafts); advanceUntilIdle(); first.open(); first.edit(node); advanceUntilIdle(); owner.clear()
            val saved = drafts.bytes!!.copyOf(); val writes = drafts.writes
            drafts.failRead = storeFailure; ops.failRead = !storeFailure
            val (second, owner2) = model(ops, drafts); advanceUntilIdle()
            assertArrayEquals(saved, drafts.bytes); assertEquals(writes, drafts.writes)
            assertFalse(second.state.value.isOpen); assertNotNull(second.state.value.error)
            drafts.failRead = false; ops.failRead = false
            second.open(); advanceUntilIdle()
            assertTrue(second.state.value.isOpen); assertEquals(node, second.state.value.editor!!.value)
            second.dismissEditor(); advanceUntilIdle(); second.close(); advanceUntilIdle(); owner2.clear()
        }
    }

    @Test fun discardingWhileDirectoryChangesNeverReplaysOldRowsOnSuccessOrFailure() = runTest {
        for (fail in listOf(false, true)) {
            val ops = Operations(); val drafts = MemoryDrafts()
            val (first, owner) = model(ops, drafts); advanceUntilIdle(); first.open(); first.edit(node); advanceUntilIdle()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            drafts.onWrite = { bytes -> if (bytes != null && !entered.isCompleted) { entered.complete(Unit); release.await() } }
            first.dismissEditor(); entered.await()
            val latest = listOf(node, node.copy(id = "external", json = """{"name":"external","type":"direct"}"""))
            ops.rows.value = latest; advanceUntilIdle(); assertEquals(latest, first.state.value.rows)
            drafts.failWrite = fail; release.complete(Unit); advanceUntilIdle()
            assertEquals(latest, first.state.value.rows)
            if (fail) assertNotNull(first.state.value.editor) else assertNull(first.state.value.editor)
            drafts.failWrite = false; if (fail) first.dismissEditor()
            advanceUntilIdle(); first.close(); advanceUntilIdle(); owner.clear()
        }
    }

    private class MemoryDrafts : ProfileDraftStore {
        var bytes: ByteArray? = null
        var failWrite = false; var failClear = false
        var failRead = false; var writes = 0
        var onWrite: suspend (ByteArray?) -> Unit = {}
        override suspend fun read(): ByteArray? { check(!failRead); return bytes?.copyOf() }
        override suspend fun write(content: ByteArray?) {
            writes++
            onWrite(content)
            check(!failWrite && !(content == null && failClear)) { "draft IO failed" }
            bytes = content?.copyOf()
        }
    }

    private class Operations : LocalProxyEditorOperations {
        val rows = MutableStateFlow(listOf(node))
        override val changes = rows.map { Unit }
        override val selectedId = 1L
        var saves = 0; var applies = 0
        var failRead = false
        var onSave: suspend (List<LocalProxy>) -> Unit = { rows.value = it }
        override suspend fun rows(): List<LocalProxy> { check(!failRead); return rows.value }
        override suspend fun save(expected: List<LocalProxy>, next: List<LocalProxy>, rename: Pair<String, String>?) {
            check(expected == rows.value) { "目录已变化" }; saves++; onSave(next)
        }
        override suspend fun groups() = emptySet<String>()
        override suspend fun users(names: Set<String>) = emptyList<String>()
        override suspend fun validate(nodes: List<JsonObject>) = nodes.map { "" }
        override suspend fun encode(nodes: List<JsonObject>) = nodes.map { it["name"].asString }
        override suspend fun decode(links: List<String>) = links.map { listOf(JsonObject().apply { addProperty("name", it); addProperty("type", "direct") }) }
        override suspend fun definition(profileId: Long, name: String) = node.fields
        override suspend fun applyIfReferenced(names: Set<String>) { applies++ }
    }
    private companion object {
        const val PRIVATE = "local-private-draft-20261105"
        val node = LocalProxy("node", """{"name":"original","type":"direct","password":"$PRIVATE"}""")
    }
}
