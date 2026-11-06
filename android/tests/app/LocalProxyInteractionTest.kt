package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModelStore
import com.google.gson.JsonObject
import com.lonnnnnng.flclash.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：冻结节点快照的受理边界与全局目录提交分别检查，不能用最终成功掩盖失败后丢失确认。 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalProxyInteractionTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val owner = ViewModelStore()
    @After fun tearDown() { owner.clear(); Dispatchers.resetMain() }
    private fun model(ops: Operations): LocalProxiesViewModel {
        Dispatchers.setMain(dispatcher)
        return LocalProxiesViewModel(ops, NoopProfileDraftStore, dispatcher).also { owner.put("local", it) }
    }

    @Test fun runtimeAdditionIgnoresRepeatedClicksAndAcceptedSaveKeepsOriginalSnapshotAfterProfileSwitch() = runTest {
        val ops = Operations(); val model = model(ops); advanceUntilIdle()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        ops.beforeSave = { entered.complete(Unit); release.await() }
        model.addFromRuntime(1L, "original"); entered.await()
        model.addFromRuntime(1L, "original")
        assertTrue(model.state.value.busy); assertEquals(1, ops.definitions); assertEquals(1, ops.saves)
        ops.selectedId = 2L
        release.complete(Unit); advanceUntilIdle()
        assertEquals(1, ops.directory.value.size)
        assertEquals("original", ops.directory.value.single().name)
        assertFalse(model.state.value.busy); assertNotNull(model.state.value.message)
    }

    @Test fun switchingProfileBeforeAcceptanceRejectsBothDuplicateAndValidatedNewNode() = runTest {
        for (duplicate in listOf(false, true)) {
            val ops = Operations().apply { if (duplicate) directory.value = listOf(node) }
            val model = model(ops); advanceUntilIdle()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val pause: suspend () -> Unit = { entered.complete(Unit); release.await() }
            if (duplicate) ops.beforeGroups = pause else ops.beforeValidate = pause
            model.addFromRuntime(1L, "original"); entered.await(); ops.selectedId = 2L
            release.complete(Unit); advanceUntilIdle()
            assertEquals(0, ops.saves); assertNull(model.state.value.message)
            assertTrue(model.state.value.error.orEmpty().contains("配置已切换")); assertFalse(model.state.value.busy)
        }
    }

    @Test fun additionFailuresHideNativeDetailsAndAllowExplicitRetry() = runTest {
        for (stage in listOf("definition", "rows", "groups", "validate", "save")) {
            val ops = Operations(); val model = model(ops); advanceUntilIdle()
            ops.failure = stage
            model.addFromRuntime(1L, "original"); advanceUntilIdle()
            assertTrue(ops.directory.value.isEmpty()); assertFalse(model.state.value.busy)
            assertNotNull(model.state.value.error); assertFalse(model.state.value.error.orEmpty().contains(PRIVATE))
            ops.failure = null
            model.addFromRuntime(1L, "original"); advanceUntilIdle()
            assertEquals(1, ops.directory.value.size); assertNull(model.state.value.error)
        }
    }

    @Test fun invalidNativeResultNeverSavesAndDuplicateDoesNotValidateAgain() = runTest {
        val ops = Operations(); val model = model(ops); advanceUntilIdle()
        for (result in listOf(emptyList(), listOf(PRIVATE), listOf("", ""))) {
            ops.validation = result; model.addFromRuntime(1L, "original"); advanceUntilIdle()
            assertEquals(0, ops.saves); assertFalse(model.state.value.error.orEmpty().contains(PRIVATE))
        }
        ops.validation = listOf(""); model.addFromRuntime(1L, "original"); advanceUntilIdle()
        val validates = ops.validates
        model.addFromRuntime(1L, "original"); advanceUntilIdle()
        assertEquals(1, ops.saves); assertEquals(validates, ops.validates)
        assertTrue(model.state.value.message.orEmpty().contains("已在本地代理中"))
    }

    @Test fun deleteFailureKeepsParentAndConfirmationRepeatedClickAndCancelWaitForAcceptedWrite() = runTest {
        val ops = Operations().apply { directory.value = listOf(node) }; val model = model(ops); advanceUntilIdle()
        model.open(); model.edit(node); model.askDelete(setOf(node.id)); advanceUntilIdle()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        ops.beforeSave = { entered.complete(Unit); release.await() }; ops.failure = "save"
        model.delete(); entered.await(); model.delete(); model.cancelDelete(); model.dismissEditor()
        assertEquals(1, ops.saves); assertEquals(setOf(node.id), model.state.value.deleting); assertNotNull(model.state.value.editor)
        release.complete(Unit); advanceUntilIdle()
        assertEquals(listOf(node), ops.directory.value); assertFalse(model.state.value.busy)
        assertEquals(setOf(node.id), model.state.value.deleting); assertFalse(model.state.value.error.orEmpty().contains(PRIVATE))
        model.cancelDelete(); advanceUntilIdle()
        assertNotNull(model.state.value.editor); assertNull(model.state.value.error); assertTrue(model.state.value.users.isEmpty())
        model.askDelete(setOf(node.id)); advanceUntilIdle(); ops.failure = null; ops.beforeSave = {}
        model.delete(); advanceUntilIdle()
        assertTrue(ops.directory.value.isEmpty()); assertTrue(model.state.value.deleting.isEmpty()); assertNull(model.state.value.editor)
    }

    @Test fun replacementFailureRetainsLinksAndPendingSetForRetry() = runTest {
        val ops = Operations().apply { directory.value = listOf(node) }; val model = model(ops); advanceUntilIdle()
        model.open(); model.links(); advanceUntilIdle(); model.applyLinks("incoming"); advanceUntilIdle()
        val pending = model.state.value.replacement
        assertNotNull(pending)
        ops.failure = "save"; model.confirmReplacement(); advanceUntilIdle()
        assertEquals(pending, model.state.value.replacement); assertNotNull(model.state.value.links)
        assertEquals(listOf(node), ops.directory.value); assertFalse(model.state.value.error.orEmpty().contains(PRIVATE))
        ops.failure = null; model.confirmReplacement(); advanceUntilIdle()
        assertEquals(pending, ops.directory.value); assertNull(model.state.value.links); assertNull(model.state.value.replacement)
    }

    private class Operations : LocalProxyEditorOperations {
        val directory = MutableStateFlow(emptyList<LocalProxy>())
        override val changes = directory.map { Unit }
        override var selectedId: Long? = 1L
        var definitions = 0; var validates = 0; var saves = 0
        var failure: String? = null
        var validation = listOf("")
        var beforeGroups: suspend () -> Unit = {}
        var beforeValidate: suspend () -> Unit = {}
        var beforeSave: suspend () -> Unit = {}
        private fun fail(stage: String) { check(stage != failure) { PRIVATE } }
        override suspend fun rows(): List<LocalProxy> { fail("rows"); return directory.value }
        override suspend fun save(expected: List<LocalProxy>, next: List<LocalProxy>, rename: Pair<String, String>?) {
            saves++; beforeSave(); fail("save"); check(expected == directory.value); directory.value = next
        }
        override suspend fun groups(): Set<String> { beforeGroups(); fail("groups"); return emptySet() }
        override suspend fun users(names: Set<String>) = (1..40).map { "引用配置$it" }
        override suspend fun validate(nodes: List<JsonObject>): List<String> { validates++; beforeValidate(); fail("validate"); return validation }
        override suspend fun encode(nodes: List<JsonObject>) = nodes.map { it["name"].asString }
        override suspend fun decode(links: List<String>) = links.map { listOf(JsonObject().apply { addProperty("name", it); addProperty("type", "direct") }) }
        override suspend fun definition(profileId: Long, name: String): JsonObject { definitions++; fail("definition"); return node.fields }
        override suspend fun applyIfReferenced(names: Set<String>) = Unit
    }
    private companion object {
        const val PRIVATE = "private-node-failure-fixture"
        val node = LocalProxy("node", """{"name":"original","type":"direct"}""")
    }
}
