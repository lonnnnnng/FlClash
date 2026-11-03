package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.Script
import com.lonnnnnng.flclash.data.ScriptStore
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptsViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Test fun `persisted picker waits for restore consumes once and releases orphan or cancelled requests`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val store = FakeScriptStore(); val drafts = PickerMemoryStore()
        fun model() = ScriptsViewModel(store, FakeScriptRuntimeStatusProvider(CoreRuntimeStatus.STOPPED), drafts)
        val first = model(); val owner = androidx.lifecycle.ViewModelStore().apply { put("scripts", first) }
        val token = requireNotNull(first.beginFilePick()); assertTrue(first.persistFilePick(token))
        val pending = drafts.bytes!!.copyOf(); owner.clear()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>(); drafts.readGate = gate
        val restored = model(); val owner2 = androidx.lifecycle.ViewModelStore().apply { put("scripts", restored) }
        backgroundScope.launch(mainDispatcher) { restored.state.collect {} }
        restored.receiveFilePick(token + 1) { error("wrong token") }
        restored.receiveFilePick(token) { "fixture.js" to "const main = config => config;".toByteArray() }
        restored.receiveFilePick(token) { error("duplicate") }
        assertTrue(store.scripts.value.isEmpty())
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(1, store.scripts.value.size); assertEquals(null, drafts.bytes)
        val cancelled = requireNotNull(restored.beginFilePick()); assertTrue(restored.persistFilePick(cancelled))
        restored.receiveFilePick(cancelled, null); advanceUntilIdle()
        assertEquals(1, store.scripts.value.size); assertEquals(null, drafts.bytes)
        val failed = requireNotNull(restored.beginFilePick()); assertTrue(restored.persistFilePick(failed))
        restored.receiveFilePick(failed) { error("无法读取脚本文件") }; advanceUntilIdle()
        assertNotBusy(restored); assertTrue(restored.state.value.error != null)
        owner2.clear(); drafts.bytes = pending; drafts.readGate = null
        val fresh = model(); val owner3 = androidx.lifecycle.ViewModelStore().apply { put("scripts", fresh) }
        backgroundScope.launch(mainDispatcher) { fresh.state.collect {} }
        assertFalse(fresh.reconcileFilePicker(null)); advanceUntilIdle()
        assertNotBusy(fresh); assertEquals(null, drafts.bytes); assertTrue(fresh.beginFilePick() != null)
        owner3.clear()
    }

    private suspend fun assertNotBusy(model: ScriptsViewModel) {
        assertFalse(model.state.first().isBusy)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `new script is persisted through the store`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val store = FakeScriptStore()
        val viewModel = ScriptsViewModel(store, FakeScriptRuntimeStatusProvider(CoreRuntimeStatus.STOPPED))

        viewModel.openNewEditor()
        viewModel.save("请求改写", "const main = (config) => config;")
        advanceUntilIdle()

        assertEquals("请求改写", store.scripts.value.single().label)
        assertEquals("const main = (config) => config;", store.contents.values.single())
        assertFalse(viewModel.editorState.value.isOpen)
    }

    @Test
    fun `editing a script reads and writes its private content`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val initial = Script(id = 11L, label = "旧脚本")
        val store = FakeScriptStore(initial, "old")
        val viewModel = ScriptsViewModel(store, FakeScriptRuntimeStatusProvider(CoreRuntimeStatus.STOPPED))

        viewModel.openEditor(initial)
        advanceUntilIdle()
        assertEquals("old", viewModel.editorState.value.content)

        viewModel.save("新脚本", "new")
        advanceUntilIdle()

        assertEquals("新脚本", store.scripts.value.single().label)
        assertEquals("new", store.contents[11L])
    }

    @Test
    fun `script mutation is allowed while running and rejected during transitions`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val store = FakeScriptStore()
        val viewModel = ScriptsViewModel(store, FakeScriptRuntimeStatusProvider(CoreRuntimeStatus.RUNNING))
        viewModel.openNewEditor()
        assertTrue(viewModel.editorState.value.isOpen)
        viewModel.save("运行中", "valid")
        advanceUntilIdle()
        assertEquals(1, store.scripts.value.size)
        for (status in listOf(CoreRuntimeStatus.STARTING, CoreRuntimeStatus.STOPPING)) {
            val transitioning = ScriptsViewModel(store, FakeScriptRuntimeStatusProvider(status))
            val error = async(start = CoroutineStart.UNDISPATCHED) { transitioning.errors.first() }
            transitioning.openNewEditor()
            assertEquals("核心正在启停，请稍后重试", error.await())
            assertFalse(transitioning.editorState.value.isOpen)
        }
    }

    @Test fun `committed script apply failure closes editor and retries only application`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val script = Script(11, "绑定脚本")
        val store = FakeScriptStore(script, "original")
        val runtime = FakeScriptRuntimeStatusProvider(CoreRuntimeStatus.RUNNING)
        val vm = ScriptsViewModel(store, runtime)
        backgroundScope.launch(mainDispatcher) { vm.state.collect {} }
        vm.openEditor(script); advanceUntilIdle()
        store.failApply = true
        vm.save("绑定脚本", "saved"); advanceUntilIdle()
        assertFalse(vm.editorState.value.isOpen)
        assertEquals("saved", store.contents[11])
        assertEquals(71L, vm.state.value.applyTargetId)
        vm.importFile("other.js", "unrelated".toByteArray()); advanceUntilIdle()
        assertTrue(vm.state.value.applyError != null)
        vm.retryApply(); advanceUntilIdle()
        assertEquals(1, runtime.applies)
        assertEquals(null, vm.state.value.applyError)
        assertEquals("saved", store.contents[11])
        assertEquals(2, store.scripts.value.size)
    }

    @Test
    fun `late read cannot reopen dismissed editor and read failure cannot save template`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val script = Script(11L, "原脚本")
        val store = FakeScriptStore(script, "original")
        store.readGate = kotlinx.coroutines.CompletableDeferred()
        val vm = ScriptsViewModel(store, FakeScriptRuntimeStatusProvider(CoreRuntimeStatus.STOPPED))
        vm.openEditor(script)
        vm.closeEditor()
        store.readGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.editorState.value.isOpen)
        store.readFailure = true
        vm.openEditor(script)
        advanceUntilIdle()
        assertTrue(vm.editorState.value.loadFailed)
        vm.save("错误保存", "replacement")
        advanceUntilIdle()
        assertEquals("original", store.contents[script.id])
    }

    @Test
    fun `file picker is single claim and drafts survive failed stale save`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val store = FakeScriptStore()
        val vm = ScriptsViewModel(store, FakeScriptRuntimeStatusProvider(CoreRuntimeStatus.STOPPED))
        val token = vm.beginFilePick()
        assertEquals(null, vm.beginFilePick())
        vm.completeFilePick(token) { "hello.js" to "original".toByteArray() }
        vm.completeFilePick(token) { error("duplicate callback") }
        advanceUntilIdle()
        assertEquals(1, store.scripts.value.size)
        val original = store.scripts.value.single()
        vm.openEditor(original)
        advanceUntilIdle()
        vm.editDraft("draft", "edited")
        assertEquals("original", vm.editorState.value.originalContent)
        store.update(original.id, "external", "latest")
        vm.save("draft", "edited")
        advanceUntilIdle()
        assertTrue(vm.editorState.value.isOpen)
        assertFalse(vm.editorState.value.isSaving)
        assertEquals("edited", vm.editorState.value.content)
        assertTrue(vm.editorState.value.error != null)
        assertEquals("latest", store.contents[original.id])
    }

    @Test
    fun `remote script import and sync delegate to the store`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val store = FakeScriptStore()
        val viewModel = ScriptsViewModel(store, FakeScriptRuntimeStatusProvider(CoreRuntimeStatus.STOPPED))

        viewModel.importRemote("远程脚本", "http://127.0.0.1/script.js")
        advanceUntilIdle()
        val imported = store.scripts.value.single()
        assertEquals("http://127.0.0.1/script.js", imported.url)
        assertEquals("remote", store.contents[imported.id])

        viewModel.sync(imported)
        advanceUntilIdle()
        assertEquals("synced", store.contents[imported.id])
    }
}

private class FakeScriptRuntimeStatusProvider(
    status: CoreRuntimeStatus,
) : CoreRuntimeStatusProvider {
    private val mutableState = MutableStateFlow(status)
    override val state: StateFlow<CoreRuntimeStatus> = mutableState.asStateFlow()
    var applies = 0
    override suspend fun applyCurrentProfile(id: Long) { assertEquals(71L, id); applies++ }
}

private class FakeScriptStore(
    initial: Script? = null,
    initialContent: String = "",
) : ScriptStore {
    private val mutableScripts = MutableStateFlow(initial?.let(::listOf).orEmpty())
    val contents = mutableMapOf<Long, String>().apply {
        if (initial != null) put(initial.id, initialContent)
    }
    private var nextId = 100L
    var failApply = false

    override val scripts: StateFlow<List<Script>> = mutableScripts.asStateFlow()

    override suspend fun create(label: String, content: String): Script {
        val script = Script(nextId++, label, lastUpdateAt = 1L, orderIndex = mutableScripts.value.size)
        contents[script.id] = content
        mutableScripts.value = mutableScripts.value + script
        return script
    }

    override suspend fun createRemote(label: String, url: String): Script {
        val script = Script(nextId++, label, url, lastUpdateAt = 1L, orderIndex = mutableScripts.value.size)
        contents[script.id] = "remote"
        mutableScripts.value = mutableScripts.value + script
        return script
    }

    var readGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var readFailure: Boolean = false
    override suspend fun readContent(id: Long): String {
        readGate?.await()
        check(!readFailure) { "读取失败" }
        return contents[id] ?: error("脚本内容不存在")
    }

    override suspend fun update(id: Long, label: String, content: String): Script {
        val current = mutableScripts.value.single { it.id == id }
        contents[id] = content
        val updated = current.copy(label = label, lastUpdateAt = 2L)
        mutableScripts.value = mutableScripts.value.map { if (it.id == id) updated else it }
        if (failApply) { failApply = false; throw com.lonnnnnng.flclash.service.ProfileApplyAfterSaveException(71) }
        return updated
    }

    override suspend fun importFile(fileName: String, bytes: ByteArray): Script =
        create(com.lonnnnnng.flclash.data.uniqueScriptLabel(fileName.substringBeforeLast('.'), scripts.value.map { it.label }), com.lonnnnnng.flclash.data.decodeScriptBytes(bytes))

    override suspend fun updateIfUnchanged(expected: Script, label: String, content: String): Script {
        check(scripts.value.single { it.id == expected.id } == expected) { "脚本已变化" }
        return update(expected.id, label, content)
    }

    override suspend fun updateRemote(expected: Script, url: String): Script {
        check(scripts.value.single { it.id == expected.id } == expected)
        val updated = expected.copy(url = url, lastUpdateAt = 4L)
        mutableScripts.value = mutableScripts.value.map { if (it.id == expected.id) updated else it }
        contents[expected.id] = "new remote"
        return updated
    }

    override suspend fun sync(id: Long): Script {
        val current = mutableScripts.value.single { it.id == id }
        contents[id] = "synced"
        val updated = current.copy(lastUpdateAt = 3L)
        mutableScripts.value = mutableScripts.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun delete(id: Long) {
        mutableScripts.value = mutableScripts.value.filterNot { it.id == id }
        contents.remove(id)
    }

    override suspend fun reorder(ids: List<Long>) {
        val byId = mutableScripts.value.associateBy { it.id }
        mutableScripts.value = ids.mapNotNull(byId::get).mapIndexed { index, script ->
            script.copy(orderIndex = index)
        }
    }
}
