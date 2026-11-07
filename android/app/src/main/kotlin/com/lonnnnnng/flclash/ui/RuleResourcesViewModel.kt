package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.google.gson.Gson
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RuleResourcesUiState(
    val rows: List<RuleResource> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val editor: RuleResourceDraft? = null,
    val deleting: RuleResource? = null,
    val users: List<String> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val ready: Boolean = false,
    val session: RuleResourceSession? = null,
    val recoveryFailed: Boolean = false,
)
data class RuleResourceDraft(val original: RuleResource? = null, val label: String = "", val url: String = "", val content: String = "", val readOnly: Boolean = false, val options: Boolean = false, val session: String = UUID.randomUUID().toString())

@HiltViewModel
class RuleResourcesViewModel @Inject constructor(
    private val repository: RuleResourceRepository,
    private val reader: ProfileFileDocumentReader,
    private val runtime: CoreRuntimeManager,
    private val profiles: ProfileStore,
    @javax.inject.Named("rule-resource-draft") private val draftStore: ProfileDraftStore = NoopProfileDraftStore,
    @javax.inject.Named("rule-file-picker") pickerStore: ProfileDraftStore = NoopProfileDraftStore,
) : ViewModel() {
    val urlImports = ResourceUrlImportController(viewModelScope, repository::importUrlIfNew)
    private val mutable = MutableStateFlow(RuleResourcesUiState())
    val state = mutable.asStateFlow()
    internal val filePicker = PersistentFilePicker(viewModelScope, pickerStore)
    private val sessionLock = Mutex()
    private val restored = CompletableDeferred<Unit>()
    private val gson = Gson()
    private var operationBusy = false
    init {
        viewModelScope.launch { repository.changes.collect {
            try {
                val rows = repository.all()
                // 作者 long：目录读取会挂起，返回后只合并列表，不能把读取前的ready/草稿快照盖回已完成的恢复状态。
                mutable.value = mutable.value.copy(rows = rows, loading = false)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(loading = false, error = "无法读取资源列表") }
        } }
        restore()
        viewModelScope.launch { filePicker.state.collect { refreshBusy() } }
        viewModelScope.launch {
            restored.await()
            combine(filePicker.state, mutable.map { it.ready }.distinctUntilChanged()) { picker, ready ->
                Triple(picker.request?.token, picker.request?.phase, ready)
            }.distinctUntilChanged().collect {
                if (it.third) deliverFile()
                refreshBusy()
            }
        }
    }
    private suspend fun deliverFile() {
        val request = filePicker.state.value.request ?: return
        if (mutable.value.recoveryFailed || request.phase !in setOf(FilePickerRequest.RESULT, FilePickerRequest.DONE)) return
        filePicker.complete(request.token, { file -> sessionLock.withLock {
            // 作者 long：结果交付与清请求间死亡时，已恢复的同一预览不能被初始字节覆盖，尤其不能擦掉用户后续编辑。
            if (mutable.value.session?.draft?.session == file.token) { persist(); return@withLock }
            check(mutable.value.editor == null) { "已有资源草稿" }
            val bytes = checkNotNull(file.bytes)
            val draft = RuleResourceDraft(label = file.name.orEmpty().substringBeforeLast('.'),
                content = if (isMrs(bytes)) "MRS 二进制资源，保存后可预览" else bytes.toString(Charsets.UTF_8),
                readOnly = isMrs(bytes), session = file.token)
            setSession(RuleResourceSession(draft, bytes.copyOf())); persist()
        } })
        refreshBusy()
    }
    private fun restore() {
        mutable.value = mutable.value.copy(ready = false, recoveryFailed = false)
        viewModelScope.launch { sessionLock.withLock {
            try {
                val saved = draftStore.read()?.let { gson.fromJson(it.decodeToString(), RuleResourceSession::class.java) }
                saved?.validate(); setSession(saved)
                val intent = saved?.intent
                if (intent != null && (intent.committed || repository.importCommitted(intent.token))) {
                    setSession(saved.copy(intent = intent.copy(committed = true))); persist(); clearSession()
                    mutable.value = mutable.value.copy(message = "上次资源保存已完成，未重复写入；如需应用请检查当前配置")
                } else if (intent != null) {
                    mutable.value = mutable.value.copy(error = "上次保存已中断，草稿已保留，请确认后重新保存")
                    persist()
                }
                mutable.value = mutable.value.copy(ready = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(recoveryFailed = true, error = "无法恢复资源草稿，原草稿已保留，请重试") }
            finally { if (!restored.isCompleted) restored.complete(Unit); refreshBusy() }
        } }
    }
    fun retryRecovery() {
        if (mutable.value.recoveryFailed) restore()
        filePicker.retryRecovery()
        viewModelScope.launch { if (mutable.value.ready) deliverFile() }
    }
    private fun setSession(session: RuleResourceSession?) {
        mutable.value = mutable.value.copy(session = session, editor = session?.draft, error = session?.error)
    }
    private suspend fun persist() {
        val session = mutable.value.session?.copy(error = mutable.value.error)
        mutable.value = mutable.value.copy(session = session)
        draftStore.write(session?.let { gson.toJson(it).toByteArray() })
    }
    fun updateUi(session: String, tree: EditorSaveableState) {
        val current = mutable.value.session ?: return
        if (!mutable.value.ready || current.draft.session != session || current.uiDraft == tree) return
        mutable.value = mutable.value.copy(session = current.copy(uiDraft = tree))
        viewModelScope.launch { sessionLock.withLock {
            if (mutable.value.session?.draft?.session == session) try { persist() }
            catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { draftFailure() }
        } }
    }
    fun draftFailure() { mutable.value = mutable.value.copy(error = "资源草稿暂存失败，请保持页面并重试") }
    fun clearError() { mutable.value = mutable.value.copy(error = null, message = null) }
    fun create(remote: Boolean = false) = perform { sessionLock.withLock {
        setSession(RuleResourceSession(RuleResourceDraft(url = if (remote) "https://" else ""))); persist()
    } }
    fun edit(resource: RuleResource, options: Boolean = false) = perform {
        val content = if (options) "" else repository.content(resource)
        sessionLock.withLock {
            setSession(RuleResourceSession(RuleResourceDraft(resource, resource.label, resource.url, content, !resource.editable && !options, options))); persist()
        }
    }
    fun closeEditor() { closePage {} }
    fun closePage(onClosed: () -> Unit) {
        if (operationBusy || filePicker.state.value.processing || !mutable.value.ready) return
        viewModelScope.launch {
            if (!filePicker.cancel()) return@launch
            sessionLock.withLock {
                try { clearSession(); onClosed() }
                catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { draftFailure() }
            }
        }
    }
    private suspend fun clearSession() {
        val session = mutable.value.session
        session?.intent?.let { intent ->
            if (!intent.committed && !repository.importCommitted(intent.token)) repository.abandonImport(intent.token)
            // 作者 long：完成标记先写密文，清回执后即使退出失败，下一次恢复也只清理而不重新保存。
            if (intent.committed || repository.importCommitted(intent.token)) {
                setSession(session.copy(intent = intent.copy(committed = true))); persist()
                repository.forgetImport(intent.token)
            }
        }
        draftStore.write(null); setSession(null)
    }
    suspend fun beginFilePick(): String? {
        restored.await()
        if (!mutable.value.ready || operationBusy || mutable.value.editor != null) return null
        if (filePicker.state.value.error != null && !filePicker.cancel()) return null
        return filePicker.begin("rules")
    }
    fun receiveFilePick(token: String?, uri: String?) = filePicker.receive(token, uri, reader::read)
    fun importFile(uri: String) { viewModelScope.launch { beginFilePick()?.let { receiveFilePick(it, uri) } } }
    private fun isMrs(bytes: ByteArray) = bytes.size >= 4 && bytes.take(4) == listOf(0x28.toByte(), 0xb5.toByte(), 0x2f.toByte(), 0xfd.toByte())
    fun save(label: String, url: String, content: String) = perform { sessionLock.withLock {
        val session = mutable.value.session ?: return@withLock
        val existing = session.intent
        if (existing != null && (existing.committed || repository.importCommitted(existing.token))) {
            setSession(session.copy(intent = existing.copy(committed = true))); persist(); clearSession()
            mutable.value = mutable.value.copy(message = "资源已保存，未重复写入"); return@withLock
        }
        val intent = RuleResourceWrite(existing?.token ?: UUID.randomUUID().toString(), label, url, content, profiles.currentProfileId.value)
        setSession(session.copy(intent = intent)); persist()
        EditorCommitProbe.checkpoint(EditorCommitWindow.RESOURCE_INTENT)
        withContext(NonCancellable) {
            val draft = session.draft
            val saved = repository.commitImport(intent.token, label, url,
                if (url.isBlank() && !draft.options) session.fileBytes?.takeIf { isMrs(it) } ?: content.toByteArray() else null,
                draft.original, draft.options)
            EditorCommitProbe.checkpoint(EditorCommitWindow.RESOURCE_APPLIED)
            setSession(checkNotNull(mutable.value.session).copy(intent = intent.copy(committed = true))); persist()
            clearSession(); mutable.value = mutable.value.copy(message = "资源已保存")
            if (saved != null && profiles.currentProfileId.value == intent.targetId) applyIfReferenced(saved)
        }
    } }
    private fun refreshBusy() { mutable.value = mutable.value.copy(busy = operationBusy || filePicker.state.value.busy || filePicker.state.value.request?.phase in setOf(FilePickerRequest.RESULT, FilePickerRequest.DONE) || !mutable.value.ready) }
    fun sync(resource: RuleResource) = perform { val saved = repository.sync(resource); applyIfReferenced(saved); mutable.value = mutable.value.copy(message = "资源已同步") }
    fun syncAll() = perform {
        var failures = 0
        for (resource in repository.all().filter { it.url.isNotBlank() }) {
            try { val saved = repository.sync(resource); applyIfReferenced(saved) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failures++ }
        }
        mutable.value = mutable.value.copy(message = if (failures == 0) "全部远程规则资源已更新" else "$failures 项更新失败，旧资源已保留")
    }
    fun askDelete(resource: RuleResource) = perform { mutable.value = mutable.value.copy(deleting = resource, users = repository.profilesUsing(resource)) }
    fun cancelDelete() { mutable.value = mutable.value.copy(deleting = null) }
    fun delete() = perform { val resource = mutable.value.deleting ?: return@perform; repository.delete(resource); mutable.value = mutable.value.copy(deleting = null, message = "资源已删除，配置中的引用已保留") }
    fun move(resource: RuleResource, offset: Int) = perform {
        val rows = repository.all().toMutableList(); val from = rows.indexOfFirst { it.id == resource.id }; val target = from + offset
        if (from >= 0 && target in rows.indices) { rows.add(target, rows.removeAt(from)); repository.reorder(rows.map { it.id }) }
    }
    fun reorder(ids: List<String>) = perform { repository.reorder(ids); mutable.value = mutable.value.copy(message = "顺序已保存") }
    private suspend fun applyIfReferenced(resource: RuleResource) {
        val id = profiles.currentProfileId.value ?: return
        if (repository.isUsedBy(id, resource)) {
            try { runtime.applyCurrentProfile(id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "资源已保存，但当前配置应用失败；请检查配置后重试应用") }
        }
    }

    private fun perform(action: suspend () -> Unit) {
        if (mutable.value.busy || !mutable.value.ready || filePicker.state.value.processing) return
        operationBusy = true
        mutable.value = mutable.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                // 作者 long：核心编译错误可能带原规则和路径；固定业务提示可直接展示，其余失败只给修正办法。
                val message = error.message?.takeIf { it in setOf("请输入资源名称", "请输入资源正文", "资源为空或超过 8 MiB",
                    "资源名称已经存在", "资源已变化，请重新打开", "本地资源没有远程地址", "规则资源列表已变化，请重新排序") }
                mutable.value = mutable.value.copy(error = message ?: "操作失败，请检查名称、资源正文或网络；原资源已保留")
                sessionLock.withLock { try { persist() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { draftFailure() } }
            }
            finally { operationBusy = false; refreshBusy() }
        }
    }
}
