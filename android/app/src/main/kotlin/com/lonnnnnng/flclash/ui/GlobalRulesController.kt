package com.lonnnnnng.flclash.ui

import com.google.gson.Gson
import com.lonnnnnng.flclash.data.NoopProfileDraftStore
import com.lonnnnnng.flclash.data.ProfileDraftStore
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import com.lonnnnnng.flclash.service.ExtensionRule
import com.lonnnnnng.flclash.service.ProfileApplyAfterSaveException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class GlobalRulesUiState(
    val rules: List<ExtensionRule> = emptyList(), val loading: Boolean = false,
    val saving: Boolean = false, val applying: Boolean = false, val loaded: Boolean = false, val error: String? = null,
    val ready: Boolean = true, val recoveryFailed: Boolean = false, val blocked: Boolean = false,
    val interrupted: Boolean = false, val saveResultVersion: Long = 0, val draft: GlobalRulesDraft? = null,
    val closeVersion: Long = 0,
)

/** 作者 long：独立入口拥有自己的密文会话；恢复只核对事务结果，绝不自动重放写库或切换当前配置。 */
internal class GlobalRulesController(
    private val store: ProfileStore, private val runtime: CoreRuntimeStatusProvider,
    private val scope: CoroutineScope, private val draftStore: ProfileDraftStore = NoopProfileDraftStore,
    private val checkpoint: suspend (EditorCommitWindow) -> Unit = { EditorCommitProbe.checkpoint(it) },
) {
    private val lock = Mutex()
    private val gson = Gson()
    private var closing = false
    private val mutableState = MutableStateFlow(GlobalRulesUiState(ready = draftStore === NoopProfileDraftStore))
    val state = mutableState.asStateFlow()

    init { if (draftStore !== NoopProfileDraftStore) restore() }

    private fun restore() {
        mutableState.value = state.value.copy(ready = false, loading = true, recoveryFailed = false)
        scope.launch {
            lock.withLock {
                try {
                    val draft = draftStore.read()?.let { gson.fromJson(it.decodeToString(), GlobalRulesDraft::class.java) }
                    draft?.validate()
                    mutableState.value = GlobalRulesUiState(ready = false, loading = true, draft = draft,
                        error = draft?.error, saveResultVersion = draft?.saveResultVersion ?: 0)
                    if (draft != null) {
                        readRules()
                        reconcilePending()
                        persist()
                    }
                    // 作者 long：回执核对和恢复密文落盘之前不显示空目录，避免消费掉批选或表单保存树。
                    mutableState.value = state.value.copy(ready = true, loading = false)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    mutableState.value = GlobalRulesUiState(recoveryFailed = true, error = "无法恢复全局规则草稿，原草稿已保留，请重试")
                }
            }
        }
    }

    fun retryRecovery() { if (state.value.recoveryFailed && !state.value.loading) restore() }

    fun open() {
        if (state.value.recoveryFailed) return
        if (!state.value.ready) {
            if (!state.value.recoveryFailed) scope.launch { state.first { it.ready }; open() }
            return
        }
        // 作者 long：重建仍是同一会话，不能重置已受理保存、错误或未提交表单。
        if (closing || state.value.draft != null) return
        mutableState.value = state.value.copy(draft = GlobalRulesDraft(), loading = true, error = null)
        scope.launch {
            lock.withLock {
                try { readRules(); persist() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableState.value = state.value.copy(error = "读取全局规则失败，请重试") }
                finally { mutableState.value = state.value.copy(loading = false) }
            }
        }
    }

    fun reload() {
        if (!state.value.ready || busy() || closing || state.value.draft?.pending != null) return
        mutableState.value = state.value.copy(loading = true)
        scope.launch { lock.withLock {
            try { readRules(); mutableState.value = state.value.copy(error = null, blocked = false); persist() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = state.value.copy(error = "读取全局规则失败，草稿已保留，请重试") }
            finally { mutableState.value = state.value.copy(loading = false) }
        } }
    }

    fun ui(session: String, tree: EditorSaveableState) {
        val current = state.value; val draft = current.draft ?: return
        if (!current.ready || current.recoveryFailed || closing || draft.session != session || draft.uiDraft == tree) return
        mutableState.value = current.copy(draft = draft.copy(uiDraft = tree))
        scope.launch { lock.withLock {
            if (state.value.draft?.session != session || closing) return@withLock
            try { persist() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { uiFailure() }
        } }
    }
    fun uiFailure() { mutableState.value = state.value.copy(error = "全局规则草稿暂存失败，请保持页面并重试") }
    private fun busy() = state.value.let { it.loading || it.saving || it.applying }
    private suspend fun persist() {
        val current = state.value
        val draft = current.draft?.copy(error = current.error, saveResultVersion = current.saveResultVersion)
        mutableState.value = current.copy(draft = draft)
        draftStore.write(draft?.let { gson.toJson(it).toByteArray() })
    }
    private suspend fun readRules() {
        val rules = store.globalExtensionRules()
        mutableState.value = state.value.copy(rules = rules, loaded = true)
    }

    fun save(value: List<ExtensionRule>) = save(state.value.rules, value)
    fun save(expected: List<ExtensionRule>, value: List<ExtensionRule>) {
        val before = state.value
        if (!before.ready || !before.loaded || busy() || closing || before.draft?.pending != null) return
        val pending = GlobalRulesCommit(before = expected, after = value, targetId = store.currentProfileId.value)
        mutableState.value = before.copy(saving = true, error = null)
        scope.launch { lock.withLock {
            try {
                pending.validate()
                check(expected == state.value.rules) { "规则目录已变化" }
                mutableState.value = state.value.copy(draft = checkNotNull(state.value.draft).copy(pending = pending))
                // 作者 long：意图先落盘；一旦受理，即使宿主销毁也完成事务，进程死亡则交由回执恢复。
                withContext(NonCancellable) { persist(); commitPending() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { failed(failure) }
            finally { mutableState.value = state.value.copy(saving = false, applying = false) }
        } }
    }

    private suspend fun commitPending() {
        val pending = checkNotNull(state.value.draft?.pending)
        check(!pending.committed && !pending.abandoned)
        checkpoint(EditorCommitWindow.GLOBAL_RULES_INTENT)
        var applyFailed = false
        try {
            runtime.withExtensionWrite(pending.targetId?.takeIf { store.currentProfileId.value == it }) {
                store.commitGlobalExtensionRules(pending.before, pending.after, pending.operationId)
                checkpoint(EditorCommitWindow.GLOBAL_RULES_APPLIED)
                mutableState.value = state.value.copy(rules = pending.after, applying = true,
                    draft = checkNotNull(state.value.draft).copy(pending = pending.copy(committed = true),
                        applyPending = pending.targetId != null, applyTargetId = pending.targetId))
                persist()
                checkpoint(EditorCommitWindow.GLOBAL_RULES_COMMITTED)
            }
        } catch (_: ProfileApplyAfterSaveException) { applyFailed = true }
        val current = state.value
        mutableState.value = current.copy(applying = false, draft = checkNotNull(current.draft).copy(applyPending = applyFailed))
        readRules()
        clearPending()
        if (applyFailed) {
            mutableState.value = state.value.copy(error = "规则已保存，原配置应用失败；可重试应用")
            persist()
        }
    }

    private suspend fun reconcilePending() {
        val pending = state.value.draft?.pending ?: return
        readRules()
        val receipt = store.extensionCommitApplied(pending.operationId)
        val completed = pending.committed || receipt
        val conflict = !pending.abandoned && !completed && state.value.rules != pending.before
        mutableState.value = state.value.copy(interrupted = true, blocked = conflict,
            draft = checkNotNull(state.value.draft).copy(pending = pending.copy(committed = completed),
                applyPending = state.value.draft!!.applyPending || completed && !pending.abandoned && pending.targetId != null,
                // 作者 long：新操作尚未提交时，不能把上一笔应用失败的冻结目标改成新配置。
                applyTargetId = if (completed && !pending.abandoned) pending.targetId else state.value.draft!!.applyTargetId), error = when {
                pending.abandoned -> "上次保存已放弃，请继续编辑"
                completed -> "上次保存已完成，请继续编辑；如需应用请显式重试"
                conflict -> "规则已被修改或删除，草稿已保留；请放弃这次保存后重新打开"
                else -> "上次保存中断，尚未提交；请重试或放弃这次保存"
            })
    }
    private suspend fun failed(failure: Exception) {
        GlobalRulesProbe.failed(failure)
        try { reconcilePending() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
        mutableState.value = state.value.copy(error = when {
            state.value.draft?.pending?.committed == true -> "规则已保存，结果核对失败，请重试核对"
            state.value.blocked -> "规则已被修改或删除，草稿已保留，请重新打开"
            else -> "保存失败，输入和选择已保留，请重试"
        }, saveResultVersion = state.value.saveResultVersion + 1, interrupted = state.value.draft?.pending != null)
        try { persist() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { uiFailure() }
    }
    private suspend fun clearPending() {
        val pending = state.value.draft?.pending?.let { if (it.committed) it else it.copy(abandoned = true) }
        if (pending != null) {
            mutableState.value = state.value.copy(draft = checkNotNull(state.value.draft).copy(pending = pending))
            persist()
            store.forgetExtensionCommit(pending.operationId)
        }
        mutableState.value = state.value.copy(draft = checkNotNull(state.value.draft).copy(pending = null),
            interrupted = false, blocked = false, error = null, saveResultVersion = state.value.saveResultVersion + 1)
        try { persist() } catch (failure: Exception) {
            mutableState.value = state.value.copy(draft = checkNotNull(state.value.draft).copy(pending = pending), interrupted = true)
            throw failure
        }
    }
    fun retryPending() = processPending(false)
    fun discardPending() = processPending(true)
    private fun processPending(discard: Boolean) {
        if (!state.value.ready || busy() || closing || state.value.draft?.pending == null) return
        mutableState.value = state.value.copy(saving = true)
        scope.launch { lock.withLock {
            try { withContext(NonCancellable) {
                reconcilePending()
                when {
                    discard || state.value.draft?.pending?.let { it.committed || it.abandoned } == true -> clearPending()
                    !state.value.blocked -> { persist(); commitPending() }
                }
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { failed(failure) }
            finally { mutableState.value = state.value.copy(saving = false, applying = false) }
        } }
    }
    fun retryApply() {
        val current = state.value; val draft = current.draft ?: return
        if (!current.ready || busy() || closing || draft.pending != null || !draft.applyPending) return
        val target = draft.applyTargetId
        if (target == null || store.currentProfileId.value != target) {
            mutableState.value = current.copy(error = "当前配置已切换，未应用到新配置")
            return
        }
        mutableState.value = current.copy(applying = true, error = null)
        scope.launch { lock.withLock {
            try {
                if (store.currentProfileId.value == target) runtime.applyCurrentProfile(target)
                mutableState.value = state.value.copy(draft = checkNotNull(state.value.draft).copy(applyPending = false))
                persist()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = state.value.copy(error = "规则已保存，原配置应用失败，请重试") }
            finally { mutableState.value = state.value.copy(applying = false) }
        } }
    }
    fun close(onClosed: () -> Unit) {
        if (!state.value.ready || busy() || closing || state.value.recoveryFailed) return
        closing = true
        mutableState.value = state.value.copy(saving = true)
        scope.launch { lock.withLock {
            try { withContext(NonCancellable) {
                if (state.value.draft?.pending != null) { reconcilePending(); clearPending() }
                checkpoint(EditorCommitWindow.GLOBAL_RULES_CLOSE_BEFORE_CLEAR)
                draftStore.write(null)
                checkpoint(EditorCommitWindow.GLOBAL_RULES_CLOSE_AFTER_CLEAR)
                mutableState.value = GlobalRulesUiState(closeVersion = state.value.closeVersion + 1)
                onClosed()
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { uiFailure() }
            finally { closing = false; mutableState.value = state.value.copy(saving = false) }
        } }
    }
}
