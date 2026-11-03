package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)
data class RuleResourceDraft(val original: RuleResource? = null, val label: String = "", val url: String = "", val content: String = "", val readOnly: Boolean = false, val options: Boolean = false)

@HiltViewModel
class RuleResourcesViewModel @Inject constructor(
    private val repository: RuleResourceRepository,
    private val reader: ProfileFileDocumentReader,
    private val runtime: CoreRuntimeManager,
    private val profiles: ProfileStore,
) : ViewModel() {
    private val mutable = MutableStateFlow(RuleResourcesUiState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    init { viewModelScope.launch { repository.changes.collect {
        try { mutable.value = mutable.value.copy(rows = repository.all(), loading = false) }
        catch (_: Exception) { mutable.value = mutable.value.copy(loading = false, error = "无法读取资源列表") }
    } } }
    fun clearError() { mutable.value = mutable.value.copy(error = null, message = null) }
    fun create(remote: Boolean = false) { if (!mutable.value.busy) { generation++; pendingFile = null; mutable.value = mutable.value.copy(editor = RuleResourceDraft(url = if (remote) "https://" else ""), error = null) } }
    fun edit(resource: RuleResource, options: Boolean = false) = perform {
        pendingFile = null
        val content = if (options) "" else repository.content(resource)
        mutable.value = mutable.value.copy(editor = RuleResourceDraft(resource, resource.label, resource.url, content, !resource.editable && !options, options))
    }
    fun closeEditor() { if (!mutable.value.busy) { generation++; pendingFile = null; mutable.value = mutable.value.copy(editor = null, error = null) } }
    fun importFile(uri: String) = perform {
        val token = generation
        val file = reader.read(uri)
        if (generation == token) {
            // 作者 long：文件正文先预览，用户点保存才编译并登记资源；二进制 MRS 直接在仓库识别，不按 UTF-8 破坏字节。
            pendingFile = file.bytes.copyOf()
            mutable.value = mutable.value.copy(editor = RuleResourceDraft(label = file.name.substringBeforeLast('.'), content = if (isMrs(file.bytes)) "MRS 二进制资源，保存后可预览" else file.bytes.toString(Charsets.UTF_8), readOnly = isMrs(file.bytes)))
        }
    }
    private var pendingFile: ByteArray? = null
    private fun isMrs(bytes: ByteArray) = bytes.size >= 4 && bytes.take(4) == listOf(0x28.toByte(), 0xb5.toByte(), 0x2f.toByte(), 0xfd.toByte())
    fun save(label: String, url: String, content: String) = perform {
        val draft = mutable.value.editor ?: return@perform
        val saved = if (draft.options && draft.original != null) repository.updateOptions(draft.original, label, url) else repository.save(label, url,
            if (url.isBlank()) pendingFile?.takeIf { isMrs(it) } ?: content.toByteArray() else null, draft.original)
        pendingFile = null
        mutable.value = mutable.value.copy(editor = null, message = "资源已保存")
        applyIfReferenced(saved)
    }
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
    private suspend fun applyIfReferenced(resource: RuleResource) {
        val id = profiles.currentProfileId.value ?: return
        if (repository.isUsedBy(id, resource)) {
            try { runtime.applyCurrentProfile(id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "资源已保存，但当前配置应用失败；请检查配置后重试应用") }
        }
    }

    private fun perform(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "操作失败，请检查名称、资源正文或网络；原资源已保留") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
