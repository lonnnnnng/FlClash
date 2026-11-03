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

data class LocalProxyDraft(val original: LocalProxy?, val value: LocalProxy)
data class ProxyLinksDraft(val text: String, val previous: List<LocalProxy>, val links: List<String>, val single: Boolean)
data class LocalProxiesUiState(
    val rows: List<LocalProxy> = emptyList(), val loading: Boolean = true, val busy: Boolean = false,
    val editor: LocalProxyDraft? = null, val links: ProxyLinksDraft? = null,
    val deleting: Set<String> = emptySet(), val users: List<String> = emptyList(),
    val replacement: List<LocalProxy>? = null, val removed: List<String> = emptyList(),
    val error: String? = null, val message: String? = null,
)

@HiltViewModel
class LocalProxiesViewModel @Inject constructor(
    private val profiles: ProfileRepository, private val bridge: CoreBridge, private val runtime: CoreRuntimeManager,
) : ViewModel() {
    private val mutable = MutableStateFlow(LocalProxiesUiState())
    val state = mutable.asStateFlow()
    private var opened: List<LocalProxy>? = null
    private var expected: List<LocalProxy> = emptyList()
    private val seenNames = mutableSetOf<String>()
    init { viewModelScope.launch { profiles.localProxyChanges.collect {
        try { val rows = profiles.localProxies(); mutable.value = mutable.value.copy(rows = rows, loading = false); seenNames += rows.map { row -> row.name } }
        catch (_: Exception) { mutable.value = mutable.value.copy(loading = false, error = "无法读取本地代理，请恢复有效备份后重试") }
    } } }
    fun open() { opened = mutable.value.rows; seenNames.clear(); seenNames += mutable.value.rows.map { it.name }; clearFeedback() }
    fun close() {
        val before = opened ?: return; opened = null
        mutable.value = mutable.value.copy(editor = null, links = null, replacement = null, deleting = emptySet())
        if (before != mutable.value.rows) perform {
            val id = profiles.currentProfileId.value ?: return@perform
            val profile = profiles.profiles.value.firstOrNull { it.id == id } ?: return@perform
            if (profile.type == ProfileType.CUSTOM && LocalProxies.referenced(profiles.readLocalProfileContent(id)).any(seenNames::contains)) {
                try { runtime.applyCurrentProfile(id) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutable.value = mutable.value.copy(error = "节点已保存，当前配置应用失败；请修复缺失引用后重新应用") }
            }
        }
    }
    fun addFromRuntime(profileId: Long?, name: String) = perform {
        val id = requireNotNull(profileId) { "配置已变化，请刷新代理列表后重试" }
        val definition = runtime.getProxyDefinition(id, name)
        val rows = profiles.localProxies()
        val groups = profiles.profiles.value.filter { it.type == ProfileType.CUSTOM }.flatMap {
            ProxyGroupDrafts.read(profiles.readLocalProfileContent(it.id)).map { group -> group.name }
        }.toSet()
        val addition = planLocalProxyAddition(definition, rows, groups)
        if (!addition.added) {
            mutable.value = state.value.copy(message = "${addition.row.name} 已在本地代理中")
        } else {
            val errors = bridge.validateProxies(listOf(addition.row.fields))
            require(errors.size == 1 && errors.single().isEmpty()) { "核心拒绝此节点，请刷新代理列表后重试" }
            check(profiles.currentProfileId.value == id) { "配置已切换，请重新打开节点菜单" }
            val next = rows + addition.row
            profiles.saveLocalProxies(rows, next)
            mutable.value = state.value.copy(rows = next, message = "${addition.row.name} 已添加到本地代理")
        }
    }
    fun clearFeedback() { mutable.value = mutable.value.copy(error = null, message = null) }
    fun edit(row: LocalProxy? = null) {
        if (state.value.busy) return
        expected = state.value.rows
        mutable.value = state.value.copy(editor = LocalProxyDraft(row, row ?: LocalProxy()), error = null)
    }
    fun dismissEditor() { if (!state.value.busy) mutable.value = state.value.copy(editor = null, links = null, error = null) }
    fun save(row: LocalProxy) = perform {
        val draft = state.value.editor ?: return@perform
        val errors = bridge.validateProxies(listOf(row.fields)); require(errors.single().isEmpty()) { errors.single() }
        val next = if (draft.original == null) expected + row else expected.map { if (it.id == row.id) row else it }
        val rename = draft.original?.takeIf { it.name != row.name }?.let { it.name to row.name }
        profiles.saveLocalProxies(expected, next, rename)
        mutable.value = state.value.copy(rows = next, editor = null, message = "节点已保存")
    }
    fun reorder(ids: List<String>) = perform {
        val rows = state.value.rows
        check(ids.size == rows.size && ids.toSet() == rows.map { it.id }.toSet()) { "节点列表已变化，请重试" }
        val next = ids.map { id -> rows.first { it.id == id } }; profiles.saveLocalProxies(rows, next)
        mutable.value = state.value.copy(rows = next)
    }
    fun askDelete(ids: Set<String>) = perform {
        val rows = state.value.rows.filter { it.id in ids }
        mutable.value = state.value.copy(deleting = ids, users = profiles.localProxyUsers(rows.map { it.name }.toSet()))
    }
    fun cancelDelete() { if (!state.value.busy) mutable.value = state.value.copy(deleting = emptySet(), replacement = null, removed = emptyList()) }
    fun delete() = perform {
        val rows = state.value.rows; val next = rows.filterNot { it.id in state.value.deleting }
        profiles.saveLocalProxies(rows, next)
        mutable.value = state.value.copy(rows = next, deleting = emptySet(), editor = null, message = "节点已删除，配置引用已保留")
    }
    fun links(single: LocalProxy? = null) = perform {
        val snapshot = state.value.rows
        val rows = if (single == null) snapshot else listOf(single)
        // 作者 long：不支持导出链接的单节点仍可从链接填写；批量编码失败不能伪装成空目录。
        val links = try { bridge.encodeShareLinks(rows.map { it.fields }) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { if (single == null) throw error else listOf("") }
        check(links.size == rows.size) { "分享链接读取不完整，请重试" }
        if (single == null) expected = snapshot
        else check(state.value.editor?.value?.id == single.id) { "节点编辑已关闭，请重新打开" }
        mutable.value = state.value.copy(links = ProxyLinksDraft(links.filter(String::isNotEmpty).joinToString("\n"), rows, links, single != null), error = null)
    }
    fun cancelLinks() { if (!state.value.busy) mutable.value = state.value.copy(links = null, replacement = null, removed = emptyList(), error = null) }
    fun applyLinks(text: String) = perform {
        val draft = state.value.links ?: return@perform
        if (text.trim() == draft.text.trim()) { mutable.value = state.value.copy(links = null); return@perform }
        require(text.toByteArray().size <= 2 * 1024 * 1024) { "分享链接不能超过 2 MiB" }
        val groups = profiles.profiles.value.filter { it.type == ProfileType.CUSTOM }.flatMap {
            ProxyGroupDrafts.read(profiles.readLocalProfileContent(it.id)).map { group -> group.name }
        }.toSet()
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
        val decoded = bridge.decodeShareLinks(links)
        check(decoded.size == links.size) { "分享链接解析不完整，请重试" }
        return decoded
    }
    private suspend fun validateLinkNodes(nodes: List<JsonObject>, lines: List<Int>? = null) {
        if (nodes.isEmpty()) return
        val errors = bridge.validateProxies(nodes)
        check(errors.size == nodes.size) { "节点校验不完整，请重试" }
        errors.forEachIndexed { index, error -> require(error.isEmpty()) {
            // 作者 long：核心报错可能包含链接凭据，界面只展示可定位的行号与修复动作。
            if (lines == null) "第 ${index + 1} 个节点校验失败，请检查协议及可选设置" else "第 ${lines[index]} 行节点校验失败，请检查分享链接"
        } }
    }
    fun confirmReplacement() = perform { state.value.replacement?.let { commitLinks(it) } }
    private suspend fun commitLinks(next: List<LocalProxy>) {
        profiles.saveLocalProxies(expected, next)
        mutable.value = state.value.copy(rows = next, links = null, replacement = null, removed = emptyList(), message = "本地代理已更新")
    }
    private fun perform(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.value = state.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.value = state.value.copy(error = error.message ?: "操作失败，请检查节点后重试") }
            finally { mutable.value = state.value.copy(busy = false) }
        }
    }
}
