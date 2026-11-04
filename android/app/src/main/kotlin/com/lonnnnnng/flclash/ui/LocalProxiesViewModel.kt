package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.*
import com.lonnnnnng.flclash.core.CoreBridge
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// 作者 long：节点凭据和集合输入仅留内存及独立Keystore密文；Activity系统保存树、备份和日志都不承载正文。
data class LocalProxyDraft(
    val original: LocalProxy?, val value: LocalProxy,
    val session: String = java.util.UUID.randomUUID().toString(),
    val baselineJson: String = value.json,
    val shown: Set<String> = emptySet(), val choosing: String? = null,
    val collection: LocalProxyCollectionDraft? = null, val discarding: Boolean = false,
    val uiDraft: EditorSaveableState? = null,
) { override fun toString() = "LocalProxyDraft([REDACTED_SECRET])" }

data class LocalProxyCollectionDraft(
    val field: ProxyField, val values: List<Pair<String, String>>,
    val error: String? = null, val discarding: Boolean = false,
) { override fun toString() = "LocalProxyCollectionDraft([REDACTED_SECRET])" }
data class ProxyLinksDraft(val text: String, val previous: List<LocalProxy>, val links: List<String>, val single: Boolean,
    val session: String = java.util.UUID.randomUUID().toString(), val uiDraft: EditorSaveableState? = null) {
    override fun toString() = "ProxyLinksDraft([REDACTED_SECRET])"
}
data class LocalProxiesUiState(
    val rows: List<LocalProxy> = emptyList(), val loading: Boolean = true, val busy: Boolean = false,
    val editor: LocalProxyDraft? = null, val links: ProxyLinksDraft? = null,
    val deleting: Set<String> = emptySet(), val users: List<String> = emptyList(),
    val replacement: List<LocalProxy>? = null, val removed: List<String> = emptyList(),
    val error: String? = null, val message: String? = null,
    val isOpen: Boolean = false, val session: Long = 0, val uiDraft: EditorSaveableState? = null,
) { override fun toString() = "LocalProxiesUiState(open=$isOpen, busy=$busy)" }

@HiltViewModel
class LocalProxiesViewModel internal constructor(
    private val operations: LocalProxyEditorOperations,
    private val draftStore: ProfileDraftStore,
    private val draftDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    @Inject constructor(profiles: ProfileRepository, bridge: CoreBridge, runtime: CoreRuntimeManager,
        @javax.inject.Named("local-proxy-draft") draftStore: ProfileDraftStore = NoopProfileDraftStore,
    ) : this(RepositoryLocalProxyEditorOperations(profiles, bridge, runtime), draftStore)
    private val mutable = MutableStateFlow(LocalProxiesUiState())
    val state = mutable.asStateFlow()
    private var opened: List<LocalProxy>? = null
    private var expected: List<LocalProxy> = emptyList()
    private val seenNames = mutableSetOf<String>()
    private val restored = CompletableDeferred<Unit>().apply { if (draftStore === NoopProfileDraftStore) complete(Unit) }
    private val draftLock = Mutex()
    private val gson = Gson()
    private var epoch = 0L
    private var closing = false
    private var nextSession = 0L
    private var pending: LocalProxyPendingWrite? = null
    private var pendingClose = false
    private var recoveryFailed = false
    private var restoringAgain = false
    private var closingChild: LocalProxiesUiState? = null
    init {
        viewModelScope.launch {
            try {
                restoreDraft()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                recoveryFailed = true
                mutable.value = state.value.copy(loading = false, error = "无法恢复本地代理草稿，密文已保留；请再次打开本地代理重试")
            }
            finally { restored.complete(Unit) }
            if (draftStore !== NoopProfileDraftStore) state.collect {
                try { persistDraft() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (state.value.error != DRAFT_ERROR) mutable.value = state.value.copy(error = DRAFT_ERROR) }
            }
        }
        viewModelScope.launch {
            restored.await()
            operations.changes.collect {
                if (recoveryFailed) return@collect
                try {
                    val rows = operations.rows()
                    mutable.value = state.value.copy(rows = rows, loading = false)
                    seenNames += rows.map { it.name }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutable.value = state.value.copy(loading = false, error = "无法读取本地代理，请恢复有效备份后重试") }
            }
        }
    }
    private suspend fun restoreDraft() {
        val bytes = draftStore.read()
        val saved = bytes?.let { withContext(draftDispatcher) { gson.fromJson(it.toString(Charsets.UTF_8), LocalProxyDraftSnapshot::class.java) } }
        val rows = operations.rows()
        if (saved != null) {
            require(saved.version == 1 && saved.state.isOpen)
            saved.state.uiDraft?.restoredValues()
            saved.state.editor?.uiDraft?.restoredValues()
            saved.state.links?.uiDraft?.restoredValues()
            opened = saved.opened; expected = saved.expected; seenNames += saved.seenNames
            pendingClose = saved.pendingClose
            val committed = saved.pending?.next == rows
            var recovered = saved.state.copy(rows = rows, loading = false, busy = false, session = ++nextSession)
            if (committed) recovered = when (saved.pending.kind) {
                "node" -> recovered.copy(editor = null, links = null)
                "links" -> recovered.copy(links = null, replacement = null, removed = emptyList())
                "delete" -> recovered.copy(editor = null, deleting = emptySet())
                else -> recovered
            }
            mutable.value = recovered.copy(error = when {
                saved.pendingClose -> "上次退出已中断，请返回重试应用和关闭"
                committed -> "已确认上次节点保存，请退出管理页检查当前配置应用"
                saved.pending != null || saved.state.busy -> "上次操作已中断，草稿已恢复，请检查后重试"
                else -> recovered.error
            })
        } else mutable.value = state.value.copy(rows = rows, loading = false)
        seenNames += rows.map { it.name }
    }

    fun open() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            restored.await()
            if (closing || restoringAgain) return@launch
            if (recoveryFailed) {
                restoringAgain = true
                try { restoreDraft(); recoveryFailed = false }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    mutable.value = state.value.copy(error = "无法恢复本地代理草稿，密文已保留；请再次打开本地代理重试")
                    return@launch
                } finally { restoringAgain = false }
            }
            if (closing || state.value.isOpen) return@launch
            opened = state.value.rows; seenNames.clear(); seenNames += state.value.rows.map { it.name }
            epoch++
            mutable.value = state.value.copy(isOpen = true, session = ++nextSession, uiDraft = null, error = null, message = null)
        }
    }
    fun close() {
        if (state.value.busy || closing) return
        val before = opened ?: return
        closing = true; pendingClose = true; epoch++
        mutable.value = state.value.copy(busy = true)
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                try {
                    // 作者 long：先保存退出意图，应用失败或中途死亡仍可回到管理页重试；清密文成功后才退场。
                    persistDraft()
                    if (before != state.value.rows) operations.applyIfReferenced(seenNames.toSet())
                    draftLock.withLock { draftStore.write(null) }
                    opened = null; pendingClose = false; pending = null; epoch++
                    mutable.value = state.value.copy(isOpen = false, busy = false, editor = null, links = null,
                        replacement = null, deleting = emptySet(), uiDraft = null, error = null)
                } catch (_: Exception) {
                    mutable.value = state.value.copy(busy = false, error = "退出未完成，已保存节点保留；请检查当前配置后重试返回")
                } finally { closing = false }
            }
        }
    }
    fun addFromRuntime(profileId: Long?, name: String) = perform {
        val id = requireNotNull(profileId) { "配置已变化，请刷新代理列表后重试" }
        val definition = operations.definition(id, name)
        val rows = operations.rows()
        val groups = operations.groups()
        val addition = planLocalProxyAddition(definition, rows, groups)
        if (!addition.added) {
            mutable.value = state.value.copy(message = "${addition.row.name} 已在本地代理中")
        } else {
            val errors = operations.validate(listOf(addition.row.fields))
            require(errors.size == 1 && errors.single().isEmpty()) { "核心拒绝此节点，请刷新代理列表后重试" }
            check(operations.selectedId == id) { "配置已切换，请重新打开节点菜单" }
            val next = rows + addition.row
            operations.save(rows, next)
            mutable.value = state.value.copy(rows = next, message = "${addition.row.name} 已添加到本地代理")
        }
    }
    fun clearFeedback() { mutable.value = mutable.value.copy(error = null, message = null) }
    fun reportDraftFailure() { mutable.value = state.value.copy(error = DRAFT_ERROR) }
    fun updateUiDraft(session: Long, draft: EditorSaveableState) {
        if (!closing && closingChild == null && state.value.isOpen && state.value.session == session)
            mutable.value = state.value.copy(uiDraft = draft)
    }
    fun updateLinksUiDraft(session: String, draft: EditorSaveableState) {
        val links = state.value.links ?: return
        if (!state.value.busy && links.session == session) mutable.value = state.value.copy(links = links.copy(uiDraft = draft))
    }
    fun updateEditorUiDraft(session: String, draft: EditorSaveableState) {
        val editor = state.value.editor ?: return
        // 作者 long：快照可能晚于最后一次输入到达，只合并当前会话的保存树，不能用旧表单覆盖刚输入的集合值或错误反馈。
        if (!state.value.busy && editor.session == session)
            mutable.value = state.value.copy(editor = editor.copy(uiDraft = draft))
    }
    fun edit(row: LocalProxy? = null) {
        if (state.value.busy) return
        expected = state.value.rows
        mutable.value = state.value.copy(editor = LocalProxyDraft(row, row ?: LocalProxy()), error = null)
    }
    fun dismissEditor() = discardChild { it.copy(editor = null, links = null, error = null) }
    fun updateEditor(draft: LocalProxyDraft) {
        // 作者 long：重建前的旧窗口或已关闭编辑器不得把凭据草稿写回后来打开的会话。
        if (!state.value.busy && state.value.editor?.session == draft.session) {
            val previous = requireNotNull(state.value.editor)
            // 作者 long：滚动/选区的加密快照不属于正文修改，不能因此抹掉校验或CAS失败反馈。
            mutable.value = state.value.copy(editor = draft,
                error = state.value.error.takeIf { previous.copy(uiDraft = draft.uiDraft) == draft })
        }
    }
    fun save(row: LocalProxy) = perform {
        val draft = state.value.editor ?: return@perform
        validateLinkNodes(listOf(row.fields))
        val next = if (draft.original == null) expected + row else expected.map { if (it.id == row.id) row else it }
        val rename = draft.original?.takeIf { it.name != row.name }?.let { it.name to row.name }
        saveRows("node", expected, next, rename)
        mutable.value = state.value.copy(rows = next, editor = null, message = "节点已保存")
    }
    fun reorder(ids: List<String>) = perform {
        val rows = state.value.rows
        check(ids.size == rows.size && ids.toSet() == rows.map { it.id }.toSet()) { "节点列表已变化，请重试" }
        val next = ids.map { id -> rows.first { it.id == id } }; saveRows("order", rows, next)
        mutable.value = state.value.copy(rows = next)
    }
    fun askDelete(ids: Set<String>) = perform {
        val rows = state.value.rows.filter { it.id in ids }
        mutable.value = state.value.copy(deleting = ids, users = operations.users(rows.map { it.name }.toSet()))
    }
    fun cancelDelete() = discardChild { it.copy(deleting = emptySet(), replacement = null, removed = emptyList()) }
    fun delete() = perform {
        val rows = state.value.rows; val next = rows.filterNot { it.id in state.value.deleting }
        saveRows("delete", rows, next)
        mutable.value = state.value.copy(rows = next, deleting = emptySet(), editor = null, message = "节点已删除，配置引用已保留")
    }
    fun links(single: LocalProxy? = null) = perform {
        val snapshot = state.value.rows
        val rows = if (single == null) snapshot else listOf(single)
        // 作者 long：不支持导出链接的单节点仍可从链接填写；批量编码失败不能伪装成空目录。
        val links = try { operations.encode(rows.map { it.fields }) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { if (single == null) throw error else listOf("") }
        check(links.size == rows.size) { "分享链接读取不完整，请重试" }
        if (single == null) expected = snapshot
        else check(state.value.editor?.value?.id == single.id) { "节点编辑已关闭，请重新打开" }
        mutable.value = state.value.copy(links = ProxyLinksDraft(links.filter(String::isNotEmpty).joinToString("\n"), rows, links, single != null), error = null)
    }
    fun cancelLinks() = discardChild { it.copy(links = null, replacement = null, removed = emptyList(), error = null) }
    fun applyLinks(text: String) = perform {
        val draft = state.value.links ?: return@perform
        if (text.trim() == draft.text.trim()) { mutable.value = state.value.copy(links = null); return@perform }
        require(text.toByteArray().size <= 2 * 1024 * 1024) { "分享链接不能超过 2 MiB" }
        val groups = operations.groups()
        val edit = LocalProxyLinkEdit(text, draft.previous, draft.links)
        if (draft.single) {
            require(edit.lines.size == 1) { "单节点编辑只能输入一个分享链接" }
            val incoming = decodeLinks(listOf(edit.lines.single().link)).single()
            require(incoming.size == 1) { "单节点编辑只能输入一个分享链接" }
            val original = draft.previous.single()
            val linked = draft.links.single().takeIf(String::isNotEmpty)?.let { decodeLinks(listOf(it)).single().singleOrNull() }
            val next = fillLocalProxyFromLink(incoming.single(), original, linked, state.value.rows, groups)
            validateLinkNodes(listOf(next.fields), listOf(edit.lines.single().number))
            // 作者 long：单节点填充只更新父表单草稿；保留打开父页时的目录快照，防止覆盖并发编辑。
            mutable.value = state.value.copy(links = null, editor = state.value.editor?.copy(value = next))
        } else {
            val pending = edit.pending
            val decoded = decodeLinks(pending.map { it.link })
            decoded.forEachIndexed { index, values -> require(values.isNotEmpty()) { "第 ${pending[index].number} 行分享链接无效" } }
            val entries = pending.indices.flatMap { index -> decoded[index].map { pending[index].number to it } }
            validateLinkNodes(entries.map { it.second }, entries.map { it.first })
            val dropped = edit.droppedLinks.entries.toList()
            val linked = decodeLinks(dropped.map { it.value })
            val next = edit.apply(pending.indices.associate { pending[it].number to decoded[it] }, groups,
                dropped.indices.mapNotNull { index -> linked[index].singleOrNull()?.let { dropped[index].key to it } }.toMap())
            LocalProxies.validate(next, groups)
            val changed = next.filter { row -> draft.previous.none { it == row } }
            validateLinkNodes(changed.map { it.fields })
            val removed = draft.previous.filter { old -> next.none { it.id == old.id } }.map { it.name }
            if (removed.isNotEmpty()) mutable.value = state.value.copy(replacement = next, removed = removed)
            else commitLinks(next)
        }
    }
    private suspend fun decodeLinks(links: List<String>): List<List<JsonObject>> {
        if (links.isEmpty()) return emptyList()
        val decoded = operations.decode(links)
        check(decoded.size == links.size) { "分享链接解析不完整，请重试" }
        return decoded
    }
    private suspend fun validateLinkNodes(nodes: List<JsonObject>, lines: List<Int>? = null) {
        if (nodes.isEmpty()) return
        val errors = operations.validate(nodes)
        check(errors.size == nodes.size) { "节点校验不完整，请重试" }
        errors.forEachIndexed { index, error -> require(error.isEmpty()) {
            // 作者 long：核心报错可能包含链接凭据，界面只展示可定位的行号与修复动作。
            if (lines == null) "第 ${index + 1} 个节点校验失败，请检查协议及可选设置" else "第 ${lines[index]} 行节点校验失败，请检查分享链接"
        } }
    }
    fun confirmReplacement() = perform { state.value.replacement?.let { commitLinks(it) } }
    private suspend fun commitLinks(next: List<LocalProxy>) {
        saveRows("links", expected, next)
        mutable.value = state.value.copy(rows = next, links = null, replacement = null, removed = emptyList(), message = "本地代理已更新")
    }
    private fun perform(action: suspend () -> Unit) {
        if (!restored.isCompleted) { viewModelScope.launch { restored.await(); perform(action) }; return }
        if (state.value.busy || closing) return
        mutable.value = state.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { pending = null; mutable.value = state.value.copy(error = error.message ?: "操作失败，请检查节点后重试") }
            finally { mutable.value = state.value.copy(busy = false) }
        }
    }

    private suspend fun saveRows(kind: String, baseline: List<LocalProxy>, next: List<LocalProxy>, rename: Pair<String, String>? = null) {
        pending = LocalProxyPendingWrite(kind, next, rename)
        // 作者 long：正式目录提交前先持久化意图；恢复仅比较实际目录确认成功，不自动重复改名或批量替换。
        persistDraft()
        operations.save(baseline, next, rename)
        pending = null
        seenNames += next.map { it.name }
    }

    private fun snapshot(): LocalProxyDraftSnapshot? {
        val current = closingChild ?: state.value
        val baseline = opened ?: return null
        if (!current.isOpen) return null
        return LocalProxyDraftSnapshot(state = current.copy(rows = emptyList()), opened = baseline, expected = expected,
            seenNames = seenNames.toSet(), pending = pending, pendingClose = pendingClose)
    }

    private suspend fun persistDraft() {
        // 作者 long：恢复期间的暂时读取失败不能转成空快照删除原密文；下一进程仍有机会恢复完整输入。
        if (recoveryFailed || draftStore === NoopProfileDraftStore) return
        val generation = epoch
        draftLock.withLock {
            if (generation != epoch) return
            val snapshot = snapshot()
            val bytes = snapshot?.let { withContext(draftDispatcher) { gson.toJson(it).toByteArray(Charsets.UTF_8) } }
            if (generation == epoch) draftStore.write(bytes)
        }
    }

    private fun discardChild(change: (LocalProxiesUiState) -> LocalProxiesUiState) {
        if (state.value.busy || closing) return
        val before = state.value
        val next = change(before)
        epoch++; closingChild = next
        mutable.value = before.copy(busy = true)
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                try {
                    // 作者 long：父目录、节点和链接使用独立保存树；移除子草稿不会让旧窗口凭据混入下一次编辑。
                    persistDraft()
                    // 作者 long：等待密文期间目录观察者可能已发布新版本，只移除子页，不能回放旧rows覆盖新目录。
                    mutable.value = change(state.value).copy(busy = false)
                } catch (_: Exception) { mutable.value = state.value.copy(busy = false, error = "草稿清除失败，请重试返回") }
                finally { closingChild = null }
            }
        }
    }

    private companion object { const val DRAFT_ERROR = "草稿暂存失败，请保持编辑页面并重试保存" }
}
