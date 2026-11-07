package com.lonnnnnng.flclash.ui

import com.google.gson.Gson
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileDraftStore
import com.lonnnnnng.flclash.data.NoopProfileDraftStore
import com.lonnnnnng.flclash.data.ProfileExtensionValues
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.service.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ProfileExtensionUiState(
    val profile: Profile? = null,
    val settings: ProfileExtensionOverrides = ProfileExtensionOverrides(),
    val globalRules: List<ExtensionRule> = emptyList(),
    val targets: List<String> = emptyList(),
    val mode: String = "standard",
    val loading: Boolean = false,
    val saving: Boolean = false,
    val applying: Boolean = false,
    val isCurrent: Boolean = false,
    val applied: Boolean = false,
    val error: String? = null,
    // 作者 long：相同错误文本也可能来自新的保存尝试；递增结果版本让确认窗口准确结束等待并允许重试。
    val saveResultVersion: Long = 0,
    val draft: ProfileExtensionDraft? = null,
    val ready: Boolean = true,
    val recoveryFailed: Boolean = false,
    val blocked: Boolean = false,
    val interrupted: Boolean = false,
)

/** 作者 long：保存和应用共用串行队列；每笔元数据提交另存意图，冷启动只核对结果，不自动重放写入或应用。 */
internal class ProfileExtensionController(
    private val repository: ProfileStore,
    private val runtime: CoreRuntimeStatusProvider,
    private val scope: CoroutineScope,
    private val parserDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
    private val store: ProfileDraftStore = NoopProfileDraftStore,
    private val testCheckpoint: (suspend (EditorCommitWindow) -> Unit)? = null,
) {
    private val lock = Mutex()
    private val gson = Gson()
    private var generation = 0L
    private var openedProfileId: Long? = null
    private var closing = false
    private val queued = mutableSetOf<String>()
    private val mutableState = MutableStateFlow(ProfileExtensionUiState(ready = store === NoopProfileDraftStore))
    val state = mutableState.asStateFlow()

    init {
        if (store !== NoopProfileDraftStore) restore()
        scope.launch { repository.currentProfileId.collect { refreshApplied() } }
        scope.launch { runtime.state.collect { refreshApplied() } }
        scope.launch { repository.profiles.collect { refreshApplied() } }
    }

    private fun restore() {
        mutableState.value = state.value.copy(ready = false, loading = true)
        scope.launch {
            lock.withLock {
                try {
                    val draft = store.read()?.let { gson.fromJson(it.toString(Charsets.UTF_8), ProfileExtensionDraft::class.java) }
                    draft?.validate()
                    repository.profilesLoaded.first { it }
                    openedProfileId = draft?.profileId
                    mutableState.value = ProfileExtensionUiState(draft = draft, ready = false, loading = true,
                        error = draft?.error, saveResultVersion = (draft?.saveResultVersion ?: 0) + 1)
                    if (draft != null) {
                        load(draft.profileId, generation)
                        reconcilePending()
                        persist()
                    }
                    // 作者 long：读到配置不代表恢复完成；先核对同事务提交回执并保存结果，再开放表单，避免旧值回退窗口误导用户。
                    mutableState.value = state.value.copy(ready = true, loading = false)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    // 作者 long：密文不可读时禁止新页面写空快照；保留原文件供显式恢复重试。
                    mutableState.value = ProfileExtensionUiState(recoveryFailed = true, error = "无法恢复扩展草稿，原草稿已保留，请重试")
                }
            }
        }
    }

    fun retryRecovery() { if (state.value.recoveryFailed && !state.value.loading) restore() }

    fun open(id: Long, reload: Boolean = false) {
        if (!state.value.ready || closing) {
            scope.launch {
                state.first { it.ready && !it.loading }
                lock.withLock { }
                if (!closing) open(id, reload)
            }
            return
        }
        if (state.value.recoveryFailed || !reload && openedProfileId == id) return
        if (state.value.draft?.pending != null) return
        openedProfileId = id
        val token = ++generation
        val draft = state.value.draft?.takeIf { it.profileId == id }?.copy(error = null) ?: ProfileExtensionDraft(id)
        mutableState.value = ProfileExtensionUiState(loading = true, draft = draft,
            saveResultVersion = state.value.saveResultVersion)
        scope.launch {
            lock.withLock {
                if (token != generation) return@withLock
                load(id, token)
                try { persist() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { uiFailure() }
            }
        }
    }

    private suspend fun load(id: Long, token: Long) {
        try {
            val profile = repository.extensionProfile(id)
            if (profile == null) {
                if (token == generation) mutableState.value = state.value.copy(profile = null, loading = false,
                    blocked = true, error = "配置已删除，草稿已保留，请退出")
                return
            }
            val values = ProfileExtensionValues.from(profile)
            val global = repository.globalExtensionRules()
            val source = repository.readProfileSourceContent(id)
            val targets = withContext(parserDispatcher) { ProfileExtensionApplier.targets(source) }
            val applied = runtime.profileInputsApplied(id)
            if (token == generation) mutableState.value = state.value.copy(profile = profile, settings = values.settings,
                globalRules = global, targets = targets, mode = values.mode, loading = false, blocked = false,
                isCurrent = repository.currentProfileId.value == id, applied = applied, error = state.value.draft?.error)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (token == generation) mutableState.value = state.value.copy(loading = false, error = "读取扩展失败，请重试")
        }
    }

    fun ui(session: String, tree: EditorSaveableState) {
        val current = state.value; val draft = current.draft ?: return
        if (!current.ready || draft.session != session || current.recoveryFailed || draft.uiDraft == tree) return
        mutableState.value = current.copy(draft = draft.copy(uiDraft = tree))
        scope.launch {
            lock.withLock {
                // 作者 long：退出窗口的迟到回调不能复活已丢弃会话，保存等待期间也不能覆盖提交意图。
                if (state.value.draft?.session != session || state.value.recoveryFailed) return@withLock
                try { persist() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { uiFailure() }
            }
        }
    }

    fun uiFailure() { mutableState.value = state.value.copy(error = "扩展草稿暂存失败，请保持页面并重试") }

    private suspend fun persist() {
        val current = state.value
        val draft = current.draft?.copy(error = current.error, saveResultVersion = current.saveResultVersion)
        mutableState.value = current.copy(draft = draft)
        store.write(draft?.let { gson.toJson(it).toByteArray(Charsets.UTF_8) })
    }

    fun refreshApplied() {
        val token = generation
        scope.launch {
            lock.withLock {
                val current = state.value
                val id = current.profile?.id ?: return@withLock
                if (current.recoveryFailed || !current.ready || current.loading) return@withLock
                try {
                    val profile = repository.extensionProfile(id)
                    val global = repository.globalExtensionRules()
                    val applied = profile != null && runtime.profileInputsApplied(id)
                    if (token != generation) return@withLock
                    if (profile == null) {
                        mutableState.value = state.value.copy(profile = null, blocked = true, error = "配置已删除，草稿已保留，请退出")
                    } else {
                        val values = ProfileExtensionValues.from(profile)
                        // 作者 long：数据库读取会挂起；合并最新UI树和保存认领状态，不回写读取前的整个旧快照。
                        mutableState.value = state.value.copy(profile = profile, settings = values.settings, mode = values.mode,
                            globalRules = global, isCurrent = repository.currentProfileId.value == id, applied = applied)
                    }
                    if (state.value.interrupted || state.value.draft?.pending?.committed == true) reconcilePending()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (token == generation) mutableState.value = state.value.copy(applied = false) }
            }
        }
    }

    fun mode(value: String) = save("mode:$value") { before ->
        ProfileExtensionCommit(before = values(before), after = values(before).copy(mode = value, settings = before.settings.copy(configured = true)))
    }
    fun script(id: Long?) = save("script:$id") { before ->
        ProfileExtensionCommit(before = values(before), after = values(before).copy(scriptId = id, settings = before.settings.copy(configured = true)))
    }
    fun target(value: String?) = save("target:$value") { before ->
        ProfileExtensionCommit(before = values(before), after = values(before).copy(matchTarget = value, settings = before.settings.copy(configured = true)))
    }
    fun rules(value: List<ExtensionRule>) = rules(state.value.settings.rules, value)
    fun rules(expected: List<ExtensionRule>, value: List<ExtensionRule>) = save("rules") { before ->
        check(before.settings.rules == expected) { "附加规则已变化，请重新打开" }
        ProfileExtensionCommit(before = values(before), after = values(before).copy(settings = before.settings.copy(rules = value, configured = true)))
    }
    fun disabled(ids: Set<String>) = save("disabled") { before ->
        ProfileExtensionCommit(before = values(before), after = values(before).copy(settings = before.settings.copy(disabledGlobalRuleIds = ids, configured = true)))
    }
    fun globalRules(rules: List<ExtensionRule>) = globalRules(state.value.globalRules, rules)
    fun globalRules(expected: List<ExtensionRule>, rules: List<ExtensionRule>) = save("global") { before ->
        check(before.globalRules == expected) { "全局规则已变化，请重新打开" }
        ProfileExtensionCommit(global = true, beforeGlobal = expected, afterGlobal = rules)
    }

    private fun values(state: ProfileExtensionUiState) = ProfileExtensionValues(state.mode,
        state.profile?.scriptId, state.profile?.matchTarget, state.settings)

    private fun save(key: String, intent: (ProfileExtensionUiState) -> ProfileExtensionCommit) {
        val current = state.value
        if (!current.ready || current.loading || current.profile == null || current.blocked || current.recoveryFailed || current.interrupted ||
            current.draft?.pending?.committed == true || closing || !queued.add(key)) return
        val token = generation
        val pendingAtSubmission = current.draft?.pending
        // 作者 long：同一规则的连续确认只认领一次；不同设置仍按原队列串行处理，后项基于前项成功后的最新字段。
        mutableState.value = current.copy(saving = true)
        scope.launch {
            lock.withLock {
                try {
                    if (token != generation) return@withLock
                    if (state.value.draft?.pending != null) {
                        // 作者 long：失败之后先核对正式结果；上一笔已写入但回执失败时，重试不能再发一笔相同写入。
                        reconcilePending()
                        if (state.value.draft?.pending?.committed == true) { acknowledgePending(); return@withLock }
                        if (state.value.blocked || pendingAtSubmission == null) return@withLock
                        mutableState.value = state.value.copy(interrupted = false)
                    }
                    val before = state.value
                    val pending = intent(before)
                    pending.validate()
                    mutableState.value = before.copy(error = null, draft = checkNotNull(before.draft).copy(pending = pending))
                    persist()
                    commitPending()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { saveFailed(failure) }
                finally {
                    queued.remove(key)
                    if (token == generation) mutableState.value = state.value.copy(saving = queued.isNotEmpty())
                }
            }
        }
    }

    private suspend fun commitPending() {
        val draft = checkNotNull(state.value.draft)
        val pending = checkNotNull(draft.pending)
        check(!pending.committed)
        if (testCheckpoint != null) testCheckpoint.invoke(EditorCommitWindow.EXTENSION_INTENT)
        else EditorCommitProbe.checkpoint(EditorCommitWindow.EXTENSION_INTENT)
        // 作者 long：全局规则也从具体扩展会话提交，宿主配置删除后不能借旧窗口继续写共享目录。
        checkNotNull(repository.extensionProfile(draft.profileId)) { "配置不存在" }
        if (pending.global) repository.compareAndSaveGlobalExtensionRulesForProfile(draft.profileId, pending.beforeGlobal, pending.afterGlobal, pending.operationId)
        else repository.compareAndSaveExtension(draft.profileId, checkNotNull(pending.before), checkNotNull(pending.after), pending.operationId)
        if (testCheckpoint != null) testCheckpoint.invoke(EditorCommitWindow.EXTENSION_APPLIED)
        else EditorCommitProbe.checkpoint(EditorCommitWindow.EXTENSION_APPLIED)
        mutableState.value = state.value.copy(draft = checkNotNull(state.value.draft).copy(pending = pending.copy(committed = true)))
        persist()
        if (testCheckpoint != null) testCheckpoint.invoke(EditorCommitWindow.EXTENSION_COMMITTED)
        else EditorCommitProbe.checkpoint(EditorCommitWindow.EXTENSION_COMMITTED)
        readResult()
        clearPending()
    }

    private suspend fun readResult() {
        val id = state.value.draft?.profileId ?: return
        val profile = repository.extensionProfile(id)
        val global = repository.globalExtensionRules()
        if (profile != null) {
            val values = ProfileExtensionValues.from(profile)
            mutableState.value = state.value.copy(profile = profile, settings = values.settings, mode = values.mode,
                globalRules = global, isCurrent = repository.currentProfileId.value == id)
        } else mutableState.value = state.value.copy(profile = null, blocked = true, globalRules = global)
    }

    private suspend fun reconcilePending() {
        val draft = state.value.draft ?: return
        val pending = draft.pending ?: return
        readResult()
        val current = state.value
        val exists = current.profile != null
        val actual = current.profile?.let(ProfileExtensionValues::from)
        val receipt = repository.extensionCommitApplied(pending.operationId)
        val completed = pending.committed || receipt || exists && if (pending.global) current.globalRules == pending.afterGlobal else actual == pending.after
        val unchanged = exists && if (pending.global) current.globalRules == pending.beforeGlobal else actual == pending.before
        val conflict = !pending.abandoned && !completed && !unchanged
        // 作者 long：回执读取会挂起；保留期间产生的搜索/子页保存树及同步认领状态，不能回写读取前的整个旧快照。
        val latest = state.value
        mutableState.value = latest.copy(draft = checkNotNull(latest.draft).copy(pending = pending.copy(committed = completed)),
            interrupted = true, blocked = conflict, error = when {
                pending.abandoned -> "上次保存已放弃，请继续编辑"
                completed -> "上次保存已完成，请继续编辑"
                conflict -> "扩展或规则已被修改或删除，草稿已保留；请放弃这次保存后重新打开"
                else -> "上次保存中断，尚未提交；请重试或放弃这次保存"
            })
    }

    private suspend fun saveFailed(failure: Exception) {
        ProfileExtensionProbe.failed(failure)
        try { reconcilePending() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* 作者 long：正式结果暂时不可读时仍保留原意图，下一次重试必须再核对。 */ }
        val committed = state.value.draft?.pending?.committed == true
        mutableState.value = state.value.copy(error = when {
            committed -> "保存已完成，读取结果或清理草稿失败，请重试核对"
            state.value.blocked -> "扩展或规则已被修改或删除，草稿已保留，请重新打开"
            else -> "扩展保存失败，请重试"
        }, saveResultVersion = state.value.saveResultVersion + 1, interrupted = state.value.draft?.pending != null)
        try { persist() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { uiFailure() }
    }

    fun retryPending() {
        val current = state.value
        if (!current.ready || current.loading || current.saving || current.applying || current.draft?.pending == null || current.recoveryFailed) return
        mutableState.value = current.copy(saving = true)
        scope.launch {
            lock.withLock {
                try {
                    reconcilePending()
                    when {
                        state.value.draft?.pending?.let { it.committed || it.abandoned } == true -> acknowledgePending()
                        !state.value.blocked -> { persist(); commitPending() }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { saveFailed(failure) }
                finally { mutableState.value = state.value.copy(saving = false) }
            }
        }
    }

    private suspend fun acknowledgePending() {
        readResult()
        clearPending()
    }

    private suspend fun clearPending() {
        val pending = state.value.draft?.pending?.let { if (it.committed) it else it.copy(abandoned = true) }
        if (pending != null) {
            // 作者 long：先把已完成/明确放弃落到密文，再清Room标记；任一步死亡都不会把已提交操作恢复成可重放。
            mutableState.value = state.value.copy(draft = checkNotNull(state.value.draft).copy(pending = pending))
            persist()
            repository.forgetExtensionCommit(pending.operationId)
        }
        mutableState.value = state.value.copy(draft = checkNotNull(state.value.draft).copy(pending = null), interrupted = false,
            blocked = state.value.profile == null, error = if (state.value.profile == null) "配置已删除，请退出" else null,
            applied = false, saveResultVersion = state.value.saveResultVersion + 1)
        try { persist() }
        catch (failure: Exception) {
            // 作者 long：清理失败时内存也必须保留原意图；否则用户再次确认会绕过已经提交的恢复屏障。
            mutableState.value = state.value.copy(draft = checkNotNull(state.value.draft).copy(pending = pending), interrupted = true)
            throw failure
        }
    }

    fun discardPending() {
        if (!state.value.ready || state.value.loading || state.value.saving || state.value.applying || state.value.draft?.pending == null) return
        mutableState.value = state.value.copy(saving = true)
        scope.launch {
            lock.withLock {
                try { acknowledgePending() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { uiFailure() }
                finally { mutableState.value = state.value.copy(saving = false) }
            }
        }
    }

    fun apply() = applyOrClose(null)
    fun close(onClosed: () -> Unit) = applyOrClose(onClosed)
    fun closeWithoutApplying(onClosed: () -> Unit) = applyOrClose(onClosed, skipApply = true)

    private fun applyOrClose(onClosed: (() -> Unit)?, skipApply: Boolean = false) {
        if (!state.value.ready || state.value.loading || state.value.recoveryFailed || state.value.applying || closing) return
        val token = generation
        if (onClosed != null) closing = true
        scope.launch {
            lock.withLock {
                try {
                    if (token != generation) return@withLock
                    val current = state.value
                    val id = current.profile?.id
                    if (!skipApply && current.draft?.pending != null) {
                        mutableState.value = current.copy(error = "请先重试或放弃这次保存，再应用或退出")
                        return@withLock
                    }
                    if (!skipApply && id != null && repository.currentProfileId.value == id && !runtime.profileInputsApplied(id)) {
                        mutableState.value = current.copy(applying = true, error = null)
                        coroutineScope {
                            val visible = async { delay(600) }
                            try { runtime.applyCurrentProfile(id) } finally { visible.await() }
                        }
                    }
                    if (token == generation) {
                        mutableState.value = state.value.copy(applying = false, applied = id != null && runtime.profileInputsApplied(id), error = null)
                        if (onClosed != null) {
                            // 作者 long：只有清除密文成功才结束会话；否则保留退出入口，避免下次冷启动复活已放弃的草稿。
                            if (state.value.draft?.pending != null) clearPending()
                            store.write(null)
                            generation++; openedProfileId = null
                            mutableState.value = ProfileExtensionUiState()
                            onClosed()
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    mutableState.value = state.value.copy(applying = false, applied = false,
                        error = "应用或退出失败，已保存的扩展保留，请重试")
                    try { persist() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { uiFailure() }
                } finally { closing = false }
            }
        }
    }
}
