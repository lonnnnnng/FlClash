package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.Script
import com.lonnnnnng.flclash.data.ScriptStore
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
    fun `script mutation is rejected while core is running`() = runTest {
        Dispatchers.setMain(mainDispatcher)
        val store = FakeScriptStore()
        val viewModel = ScriptsViewModel(store, FakeScriptRuntimeStatusProvider(CoreRuntimeStatus.RUNNING))
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.errors.first() }

        viewModel.openNewEditor()

        assertEquals("核心运行中，请先停止核心后再修改脚本", error.await())
        advanceUntilIdle()
        assertFalse(viewModel.editorState.value.isOpen)
        assertTrue(store.scripts.value.isEmpty())
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

    override suspend fun readContent(id: Long): String = contents[id] ?: error("脚本内容不存在")

    override suspend fun update(id: Long, label: String, content: String): Script {
        val current = mutableScripts.value.single { it.id == id }
        contents[id] = content
        val updated = current.copy(label = label, lastUpdateAt = 2L)
        mutableScripts.value = mutableScripts.value.map { if (it.id == id) updated else it }
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
