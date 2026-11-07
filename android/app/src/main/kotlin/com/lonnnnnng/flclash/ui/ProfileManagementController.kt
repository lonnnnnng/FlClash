package com.lonnnnnng.flclash.ui

import com.google.gson.Gson
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileDraftStore
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 作者 long：管理窗口只保存操作所需的ID与名称，不复制配置凭据；未确认输入与窗口保存树共同进入独立密文。 */
internal data class ProfileManagementDraft(
    val kind: String,
    val session: String = UUID.randomUUID().toString(),
    val profileId: Long? = null,
    val originalLabel: String = "",
    val label: String = "",
    val originalOrder: List<Long> = emptyList(),
    val order: List<Long> = emptyList(),
    val pending: Boolean = false,
    val committed: Boolean = false,
    val uiDraft: EditorSaveableState? = null,
    val version: Int = 1,
) {
    override fun toString() = "ProfileManagementDraft(kind=$kind, [REDACTED])"
    fun validate() {
        require(version == 1 && kind in setOf("rename", "delete", "order") && session.isNotBlank())
        require(kind == "order" || profileId != null)
        require(label.length <= 64 && originalOrder.distinct() == originalOrder && order.distinct() == order)
        require(!committed || pending)
        uiDraft?.restoredValues()
    }
}

internal data class ProfileManagementState(
    val ready: Boolean = false,
    val draft: ProfileManagementDraft? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val recoveryFailed: Boolean = false,
    val blocked: Boolean = false,
)

/** 作者 long：窗口会话独立于Activity和结果计数；恢复只能核对已受理操作，不能把进程重启当作再次确认。 */
internal class ProfileManagementController(
    private val repository: ProfileStore,
    private val runtime: CoreRuntimeStatusProvider,
    private val store: ProfileDraftStore,
    private val scope: CoroutineScope,
    private val testCheckpoint: (suspend (EditorCommitWindow) -> Unit)? = null,
) {
    private val mutableState = MutableStateFlow(ProfileManagementState())
    val state = mutableState.asStateFlow()
    private val lock = Mutex()
    private val gson = Gson()

    init {
        restore()
        scope.launch {
            repository.profilesLoaded.first { it }
            repository.profiles.collect {
                lock.withLock {
                    val current = mutableState.value
                    if (current.ready && !current.busy && !current.recoveryFailed) reconcile()
                }
            }
        }
    }

    fun retryRecovery() { if (!state.value.busy && state.value.recoveryFailed) restore() }

    private fun restore() {
        mutableState.value = state.value.copy(busy = true)
        scope.launch {
            lock.withLock {
                try {
                    val saved = store.read()?.let { gson.fromJson(it.toString(Charsets.UTF_8), ProfileManagementDraft::class.java) }
                    saved?.validate()
                    repository.profilesLoaded.first { it }
                    mutableState.value = ProfileManagementState(ready = true, draft = saved)
                    reconcile()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    // 作者 long：读取失败时保留原密文；不能以初始空窗口覆盖它，也不能开启新会话掩盖旧草稿。
                    mutableState.value = ProfileManagementState(ready = true, recoveryFailed = true,
                        error = "无法恢复配置管理草稿，原草稿已保留，请重试")
                }
            }
        }
    }

    fun open(kind: String, profile: Profile? = null) {
        val current = state.value
        if (!current.ready || current.busy || current.recoveryFailed || current.draft != null) return
        require(kind in setOf("rename", "delete", "order"))
        val ids = repository.profiles.value.map(Profile::id)
        val draft = ProfileManagementDraft(kind, profileId = profile?.id,
            originalLabel = profile?.label.orEmpty(), label = profile?.label.orEmpty().take(64), originalOrder = ids, order = ids)
        draft.validate()
        mutableState.value = ProfileManagementState(ready = true, draft = draft)
        persistLater(draft.session)
    }

    fun label(session: String, value: String) = edit(session) { it.copy(label = value.take(64), pending = false) }
    fun order(session: String, ids: List<Long>) = edit(session) { it.copy(order = ids.distinct(), pending = false) }
    private fun edit(session: String, transform: (ProfileManagementDraft) -> ProfileManagementDraft) {
        val current = state.value; val draft = current.draft ?: return
        if (draft.session != session || current.busy || current.recoveryFailed || draft.committed) return
        mutableState.value = current.copy(draft = transform(draft), error = null)
        persistLater(session)
    }

    fun ui(session: String, tree: EditorSaveableState) {
        val current = state.value; val draft = current.draft ?: return
        if (draft.session != session || current.recoveryFailed || draft.uiDraft == tree) return
        mutableState.value = current.copy(draft = draft.copy(uiDraft = tree))
        persistLater(session)
    }

    fun uiFailure() { mutableState.value = state.value.copy(error = "窗口草稿暂存失败，请保持页面并重试") }

    private fun persistLater(session: String) {
        scope.launch {
            lock.withLock {
                // 作者 long：排队写入重取最新会话，不能用旧窗口注销时的回调复活已放弃的草稿或擦掉保存意图。
                if (state.value.draft?.session != session || state.value.recoveryFailed) return@withLock
                try { persist() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { uiFailure() }
            }
        }
    }

    private suspend fun persist() = store.write(state.value.draft?.let { gson.toJson(it).toByteArray(Charsets.UTF_8) })

    fun close() {
        val current = state.value
        if (current.busy || current.recoveryFailed || current.draft == null) return
        mutableState.value = current.copy(busy = true)
        scope.launch {
            lock.withLock {
                try {
                    val draft = state.value.draft
                    if (draft?.kind == "delete" && draft.pending && !draft.committed &&
                        repository.extensionProfile(checkNotNull(draft.profileId)) == null && !repository.profileDeletionComplete(draft.profileId)) {
                        // 作者 long：删除已经越过Room提交点时不能用“取消”抹掉残留正文的唯一清理入口。
                        mutableState.value = state.value.copy(busy = false, error = "配置记录已删除，请先重试完成正文清理")
                        return@withLock
                    }
                    store.write(null)
                    mutableState.value = ProfileManagementState(ready = true)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableState.value = state.value.copy(busy = false, error = "草稿清除失败，请重试关闭") }
            }
        }
    }

    fun save() {
        val current = state.value; val draft = current.draft ?: return
        if (current.busy || current.recoveryFailed || current.blocked || draft.committed) return
        if (isProfileTransitioning(runtime.state.value)) {
            mutableState.value = current.copy(error = "核心正在启停，请稍后重试")
            return
        }
        if (draft.kind == "rename" && (draft.label.trim().isEmpty() || draft.label.trim() == draft.originalLabel)) return
        // 作者 long：同步认领避免连续点按排出多笔提交；失败与取消都不会让其他操作清掉仍在进行的会话。
        mutableState.value = current.copy(busy = true, error = null)
        scope.launch {
            lock.withLock {
                try {
                    reconcile()
                    if (state.value.blocked || state.value.draft?.committed == true) return@withLock
                    val latest = checkNotNull(state.value.draft)
                    mutableState.value = state.value.copy(draft = latest.copy(pending = true))
                    persist()
                    if (testCheckpoint != null) testCheckpoint.invoke(EditorCommitWindow.MANAGEMENT_INTENT)
                    else EditorCommitProbe.checkpoint(EditorCommitWindow.MANAGEMENT_INTENT)
                    when (latest.kind) {
                        "rename" -> runtime.withProfileWrite {
                            repository.compareAndRenameProfile(checkNotNull(latest.profileId), latest.originalLabel, latest.label.trim())
                        }
                        "order" -> runtime.withProfileWrite {
                            repository.compareAndReorderProfiles(latest.originalOrder, latest.order)
                        }
                        "delete" -> {
                            val id = checkNotNull(latest.profileId)
                            runtime.removeProfile(id, { repository.profiles.value.map(Profile::id) }, repository::selectProfile) {
                                repository.deleteProfile(id)
                            }
                            // 作者 long：Room已提交后文件清理失败只允许补清理，不让运行管理器尝试回滚一条已经删除的配置。
                            check(repository.profileDeletionComplete(id)) { "配置记录已删除，正文清理未完成，请重试" }
                        }
                    }
                    mutableState.value = state.value.copy(draft = checkNotNull(state.value.draft).copy(committed = true))
                    persist()
                    if (testCheckpoint != null) testCheckpoint.invoke(EditorCommitWindow.MANAGEMENT_COMMITTED)
                    else EditorCommitProbe.checkpoint(EditorCommitWindow.MANAGEMENT_COMMITTED)
                    store.write(null)
                    mutableState.value = ProfileManagementState(ready = true)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    ProfileManagementProbe.failed(failure)
                    mutableState.value = state.value.copy(error = if (state.value.draft?.committed == true)
                        "操作已完成，草稿清理失败，请关闭窗口重试" else "操作未完成，请检查目标状态后重试")
                    try { reconcile() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* 作者 long：读取失败保留原意图，等待显式重试。 */ }
                } finally { mutableState.value = state.value.copy(busy = false) }
            }
        }
    }

    private suspend fun reconcile() {
      while (true) {
        val current = state.value; val draft = current.draft ?: return
        if (draft.committed) {
            mutableState.value = current.copy(error = "上次操作已完成，请关闭窗口", blocked = false)
            return
        }
        val target = draft.profileId?.let { repository.extensionProfile(it) }
        val ids = repository.managementProfileOrder()
        val order = draft.order.filter { it in ids } + ids.filterNot { it in draft.order }
        val completed = draft.pending && when (draft.kind) {
            "rename" -> target?.label == draft.label.trim()
            "delete" -> repository.profileDeletionComplete(checkNotNull(draft.profileId))
            "order" -> ids == order
            else -> false
        }
        val conflict = when (draft.kind) {
            "rename" -> target == null || target.label != draft.originalLabel && !completed
            "delete" -> target == null && !draft.pending
            "order" -> !sameSurvivingOrder(draft.originalOrder, ids) && !completed
            else -> true
        }
        // 作者 long：Room读取会挂起；期间输入、选区或保存认领可能已更新，旧快照不能覆盖新值或撤销busy。
        if (state.value != current) continue
        val updated = draft.copy(order = order, committed = completed,
            // 作者 long：已经展示过的新增项也进入比较基线；之后它们互换位置必须视为外部排序，不能永远当作可追加的新项。
            originalOrder = if (!conflict && !draft.pending && draft.kind == "order") ids else draft.originalOrder)
        mutableState.value = current.copy(draft = updated, blocked = conflict,
            error = when {
                completed -> "上次操作已完成，请关闭窗口"
                conflict -> "配置已被删除或修改，草稿已保留；请取消后重新打开"
                draft.pending -> "上次操作中断，请重试或取消"
                else -> current.error
            })
        if (updated != draft) {
            try { persist() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { uiFailure() }
        }
        return
      }
    }
}

/** 作者 long：新增和删除只调整成员集合，不能把另一处刚完成的排序静默覆盖成旧窗口顺序。 */
internal fun sameSurvivingOrder(before: List<Long>, after: List<Long>): Boolean =
    before.filter { it in after } + after.filterNot { it in before } == after
