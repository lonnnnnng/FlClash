package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ProfileStatistics
import com.lonnnnnng.flclash.service.ProfileStatisticsRuntime
import com.lonnnnnng.flclash.service.ProfileProviderDocument
import com.lonnnnnng.flclash.service.ProviderOperationException
import com.lonnnnnng.flclash.core.CoreProviderUpdateResult
import com.lonnnnnng.flclash.data.ProfileFileDocumentReader
import com.lonnnnnng.flclash.data.ManagedProfileProviders
import com.lonnnnnng.flclash.data.RuleResource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock

/** 作者 long：只持久化重建会话所需的目标与摘要，不序列化管理器内部绑定。 */
internal class ProviderPickerSnapshot(val profileId: Long, val name: String, val recoveryKey: String,
    val digest: String, val token: Long, val version: Int = 1)

data class ProfileStatisticsUiState(val profileId: Long? = null, val counts: ProfileStatistics? = null,
    val isLoading: Boolean = false, val error: String? = null)

data class ProfileProvidersUiState(val profileId: Long? = null,
    val providers: List<com.lonnnnnng.flclash.core.CoreExternalProvider>? = null,
    val isLoading: Boolean = false, val error: String? = null, val updatingName: String? = null,
    val updateMessage: String? = null, val updateFailed: Boolean = false,
    val updatingNames: Set<String> = emptySet(), val failedNames: Set<String> = emptySet(),
    val managedNames: Set<String> = emptySet(), val failedMessages: Map<String, String> = emptyMap())

/** 作者 long：编辑正文包含凭据，不生成可将正文写入日志的data class.toString。 */
data class ProviderDocumentUiState(val name: String, val loading: Boolean = true,
    val document: ProfileProviderDocument? = null, val content: String = "", val editing: Boolean = false,
    val saving: Boolean = false, val error: String? = null, val picking: Boolean = false,
    val pickerToken: Long? = null, val committed: Boolean = false,
    val draft: ProviderDocumentDraft? = null, val pendingSave: Boolean = false,
    val recoveryBlocked: Boolean = false) {
    val dirty: Boolean get() = recoveryBlocked && draft == null || !committed && editing &&
        (draft?.let { content != it.originalContent } ?: (document != null && content != document.document.content))
    override fun toString() = "ProviderDocumentUiState([REDACTED])"
}

@HiltViewModel
class ProfileStatisticsViewModel internal constructor(private val runtime: ProfileStatisticsRuntime,
    private val reader: ProfileFileDocumentReader,
    private val managed: ManagedProfileProviders?,
    private val pickerStore: com.lonnnnnng.flclash.data.ProfileDraftStore,
    private val documentStore: com.lonnnnnng.flclash.data.ProfileDraftStore,
    private val draftDispatcher: CoroutineDispatcher) : ViewModel() {
    @Inject constructor(runtime: ProfileStatisticsRuntime,
        reader: ProfileFileDocumentReader = ProfileFileDocumentReader { error("文件读取不可用") },
        managed: ManagedProfileProviders? = null,
        @javax.inject.Named("provider-picker") pickerStore: com.lonnnnnng.flclash.data.ProfileDraftStore = com.lonnnnnng.flclash.data.NoopProfileDraftStore,
        @javax.inject.Named("provider-document") documentStore: com.lonnnnnng.flclash.data.ProfileDraftStore = com.lonnnnnng.flclash.data.NoopProfileDraftStore,
    ) : this(runtime, reader, managed, pickerStore, documentStore, Dispatchers.Default)
    private val mutableState = MutableStateFlow(ProfileStatisticsUiState())
    val state = mutableState.asStateFlow()
    private val mutableProviders = MutableStateFlow(ProfileProvidersUiState())
    val providers = mutableProviders.asStateFlow()
    private val providerRetries = MutableStateFlow(0L)
    private var updateJob: Job? = null
    private var inFlightName: String? = null
    private var providerGeneration = 0L
    private var managedBindings: Map<String, RuleResource> = emptyMap()
    private val mutableDocument = MutableStateFlow<ProviderDocumentUiState?>(null)
    val document = mutableDocument.asStateFlow()
    private var documentJob: Job? = null
    private var documentGeneration = 0L
    private var nextPickerToken = 0L
    private var pendingPicker: ProviderPickerSnapshot? = null
    private var recoveringPicker: ProviderPickerSnapshot? = null
    private val pickerLock = kotlinx.coroutines.sync.Mutex()
    private val pickerRestored = CompletableDeferred<Unit>()
    private val pickerGson = com.google.gson.Gson()
    private val foreground = MutableStateFlow(false)
    private val profile = MutableStateFlow<Long?>(null)
    private val retries = MutableStateFlow(0L)
    private val bodyRestored = CompletableDeferred<Unit>()
    private val bodyLock = kotlinx.coroutines.sync.Mutex()
    private var bodyReadFailed = false
    private var bodyRestoring = false
    private var bodyClosing = false
    private var currentSelection: Long? = null
    private var showRequest = 0L
    private val mutableBodyRoute = MutableStateFlow<Long?>(null)
    val restoredProviderProfileId = mutableBodyRoute.asStateFlow()

    init {
        viewModelScope.launch {
            try { readBodyDraft() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                bodyReadFailed = true
                mutableDocument.value = ProviderDocumentUiState("Provider 草稿", loading = false, recoveryBlocked = true,
                    error = "无法恢复 Provider 草稿，密文已保留，请重试")
            } finally { bodyRestored.complete(Unit) }
            if (documentStore !== com.lonnnnnng.flclash.data.NoopProfileDraftStore) document.collect { current ->
                if (current?.draft == null || bodyReadFailed) return@collect
                try { persistBodyDraft(current) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { bodyWriteFailed(current.draft.session) }
            }
        }
    }

    private suspend fun readBodyDraft(generation: Long = documentGeneration) {
        if (documentStore === com.lonnnnnng.flclash.data.NoopProfileDraftStore) return
        val saved = withContext(draftDispatcher) {
            documentStore.read()?.let { pickerGson.fromJson(it.toString(Charsets.UTF_8), ProviderDocumentDraft::class.java) }
                ?.also { it.validate() }
        } ?: return
        // 作者 long：重试读取不拥有新的编辑会话；用户已放弃或换窗后，迟到密文不得重新发布并导航回来。
        currentCoroutineContext().ensureActive()
        if (generation != documentGeneration || bodyClosing) return
        mutableDocument.value = ProviderDocumentUiState(saved.name, loading = false, content = saved.content,
            editing = saved.editing, committed = saved.committed, draft = saved, pendingSave = saved.pending, recoveryBlocked = true,
            error = saved.error)
        mutableBodyRoute.value = saved.profileId
    }

    private fun bodySnapshot(state: ProviderDocumentUiState): ProviderDocumentDraft? = state.draft?.copy(
        content = state.content, editing = state.editing, pending = state.pendingSave,
        committed = state.committed, error = state.error)

    private suspend fun persistBodyDraft(state: ProviderDocumentUiState) = bodyLock.withLock {
        // 作者 long：落盘排队期间可能已关闭或换窗口，旧采样不得复活已放弃的正文。
        if (bodyReadFailed || bodyClosing || document.value !== state) return@withLock
        val saved = bodySnapshot(state) ?: return@withLock
        val bytes = withContext(draftDispatcher) { pickerGson.toJson(saved).toByteArray(Charsets.UTF_8) }
        documentStore.write(bytes)
    }

    private fun bodyWriteFailed(session: String) {
        val old = document.value ?: return
        if (old.draft?.session == session && old.error != BODY_WRITE_ERROR)
            mutableDocument.value = old.copy(error = BODY_WRITE_ERROR)
    }

    fun captureProviderUi(session: String, ui: EditorSaveableState) {
        val old = document.value ?: return
        val saved = old.draft ?: return
        if (saved.session == session && saved.uiDraft != ui && !bodyReadFailed && !bodyClosing)
            mutableDocument.value = old.copy(draft = saved.copy(uiDraft = ui))
    }

    fun providerUiCaptureFailed() { document.value?.draft?.session?.let(::bodyWriteFailed) }

    fun resumeProviderDocument(selectedProfileId: Long?) {
        currentSelection = selectedProfileId
        if (!bodyRestored.isCompleted || bodyRestoring) return
        val old = document.value ?: return
        val saved = bodySnapshot(old) ?: return
        if (!old.recoveryBlocked || old.saving) return
        if (selectedProfileId != saved.profileId) {
            mutableDocument.value = old.copy(error = "原配置已删除或当前配置已切换，草稿已保留；请返回原配置后重试")
            return
        }
        profile.value = selectedProfileId
        bodyRestoring = true
        val generation = documentGeneration
        mutableDocument.value = old.copy(loading = true)
        documentJob = viewModelScope.launch {
            try {
                val loaded = runtime.recoverProfileProviderDocument(saved.profileId, saved.name, saved.recoveryKey, saved.digest,
                    saved.content.takeIf { saved.pending })
                currentCoroutineContext().ensureActive()
                if (generation != documentGeneration) return@launch
                val committed = saved.pending && loaded.document.content == saved.content
                check(!saved.committed || committed)
                mutableDocument.value = old.copy(document = loaded, loading = false, recoveryBlocked = false,
                    draft = document.value?.draft ?: old.draft,
                    committed = committed, pendingSave = committed, error = when {
                        committed -> "已确认上次保存，无需重复提交"
                        saved.pending -> "上次保存已中断，草稿已恢复，请检查后重试"
                        else -> saved.error
                    })
                mutableBodyRoute.value = null
                // 作者 long：冷启动列表可能先于正文绑定读到“核心尚未加载”并暂停轮询；绑定成功后只刷新原父面板一次。
                // 普通版本通知仍不能抹掉读取错误，恢复失败或用户切走时也不能替另一配置自动重试。
                if (profile.value == saved.profileId) {
                    providerRetries.update { it + 1 }
                    retries.update { it + 1 }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == documentGeneration) mutableDocument.value = old.copy(loading = false, recoveryBlocked = true,
                    draft = document.value?.draft ?: old.draft,
                    error = "配置或 Provider 文件已变化或暂不可读，草稿已保留；恢复原目标后可重试")
            } finally { bodyRestoring = false }
        }
    }

    fun reconcileProviderSelection(selectedProfileId: Long?) {
        currentSelection = selectedProfileId
        val old = document.value ?: return
        if (old.draft == null || old.draft.profileId == selectedProfileId || old.saving) return
        documentGeneration++; documentJob?.cancel()
        mutableDocument.value = old.copy(document = null, loading = false, recoveryBlocked = true,
            error = "原配置已删除或当前配置已切换，草稿已保留；请返回原配置后重试")
    }

    fun retryProviderDocument() {
        if (bodyReadFailed) {
            if (bodyRestoring) return
            bodyRestoring = true
            val generation = documentGeneration
            documentJob = viewModelScope.launch {
                try {
                    readBodyDraft(generation)
                    if (generation == documentGeneration && !bodyClosing) {
                        bodyReadFailed = false
                        if (document.value?.draft == null) mutableDocument.value = null
                    }
                }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* 作者 long：读取失败保留原密文，不能以空快照覆盖。 */ }
                finally { bodyRestoring = false }
            }
            return
        }
        val old = document.value ?: return
        when {
            old.recoveryBlocked -> resumeProviderDocument(currentSelection)
            old.committed -> retryProviderReadback()
            else -> openProvider(old.name, old.editing, old.picking)
        }
    }

    private suspend fun clearBodyDraft(generation: Long) = bodyLock.withLock {
        if (!bodyReadFailed && generation == documentGeneration) documentStore.write(null)
    }

    private companion object { const val BODY_WRITE_ERROR = "Provider 草稿暂未保存到本机，请保持页面并重试" }

    init {
        viewModelScope.launch {
            try {
                pendingPicker = pickerStore.read()?.let { pickerGson.fromJson(it.toString(Charsets.UTF_8), ProviderPickerSnapshot::class.java) }
                pendingPicker?.let { check(it.version == 1 && it.token > 0 && it.recoveryKey.isNotBlank()); nextPickerToken = it.token }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { pendingPicker = null }
            finally { pickerRestored.complete(Unit) }
        }
    }

    private suspend fun writePicker(snapshot: ProviderPickerSnapshot?) = pickerLock.withLock {
        if (pendingPicker === snapshot) pickerStore.write(snapshot?.let { pickerGson.toJson(it).toByteArray(Charsets.UTF_8) })
    }

    private fun invalidatePicker() {
        pendingPicker = null
        viewModelScope.launch { withContext(NonCancellable) { writePicker(null) } }
    }

    init {
        viewModelScope.launch {
            pollProfile(retries,
                clear = { id -> mutableState.value = ProfileStatisticsUiState(id) },
                loading = { mutableState.update { it.copy(isLoading = it.counts == null, error = null) } },
                read = { id -> runtime.profileStatistics(id).also { check(it.profileId == id) } },
                publish = { id, result ->
                    mutableState.value = result.fold(
                        { ProfileStatisticsUiState(id, it) },
                        { ProfileStatisticsUiState(id, error = if (it is TimeoutCancellationException)
                            "读取配置统计超时，请重试" else "读取配置统计失败，请重试") })
                })
        }
        viewModelScope.launch {
            pollProfile(providerRetries,
                clear = { id -> mutableProviders.update { old -> if (old.profileId == id)
                    old.copy(providers = null, isLoading = false, error = null) else ProfileProvidersUiState(id, updatingName = inFlightName) } },
                loading = { mutableProviders.update { it.copy(isLoading = it.providers == null, error = null) } },
                read = { id ->
                    val rows = runtime.profileProviders(id)
                    rows to managed?.bindings(id, rows).orEmpty()
                },
                publish = { _, result ->
                    result.onSuccess { (rows, bindings) ->
                        managedBindings = bindings
                        mutableProviders.update { it.copy(providers = rows, isLoading = false, error = null, managedNames = bindings.keys) }
                    }.onFailure { error ->
                        mutableProviders.update { it.copy(isLoading = false, error = if (error is TimeoutCancellationException)
                            "读取 Providers 超时，请重试" else "读取 Providers 失败，请重试") }
                    }
                })
        }
    }

    private suspend fun <T> pollProfile(retry: StateFlow<Long>, clear: (Long?) -> Unit, loading: () -> Unit,
        read: suspend (Long) -> T, publish: (Long, Result<T>) -> Unit) {
        combine(foreground, profile, runtime.state) { front, id, status -> Triple(front, id, status) }.collectLatest { (front, id, status) ->
            clear(id)
            if (!front || id == null || status in listOf(CoreRuntimeStatus.STARTING, CoreRuntimeStatus.STOPPING)) return@collectLatest
            var failed = false
            var lastRetry = retry.value
            // 作者 long：版本通知只排队一次后续刷新，不能取消正在等待的读取并反复重置超时；换配置或退后台仍立即作废旧页面。
            val ticks = flow {
                emit(Unit)
                while (currentCoroutineContext().isActive) { delay(5_000L); if (!failed) emit(Unit) }
            }
            merge(ticks, runtime.profileStatisticsRevision.drop(1).map { Unit }, retry.drop(1).map { Unit }).conflate().collect {
                // 作者 long：失败后暂停自动轮询，版本通知也不能抹掉错误；手动重试或重新打开详情才重新读取。
                if (failed && retry.value == lastRetry) return@collect
                lastRetry = retry.value
                loading()
                // 作者 long：页面只等待独立任务的结果，核心不可取消阶段和资源归属识别都受同一个8秒页面预算约束。
                val reading = viewModelScope.async {
                    try { Result.success(read(id)) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { Result.failure(error) }
                }
                val result = try { withTimeout(8_000L) { reading.await() } }
                catch (timeout: TimeoutCancellationException) { Result.failure(timeout) }
                finally { reading.cancel() }
                currentCoroutineContext().ensureActive()
                failed = result.isFailure
                publish(id, result)
            }
        }
    }

    fun updateProvider(name: String) {
        val id = profile.value ?: return
        if (!foreground.value || updateJob?.isActive == true || providers.value.profileId != id ||
            document.value?.saving == true || providers.value.providers?.any { it.name == name && (it.canUpdate || name in providers.value.managedNames) } != true) return
        val generation = providerGeneration
        inFlightName = name
        val resource = managedBindings[name]
        mutableProviders.update { it.copy(updatingName = name, updatingNames = setOf(name), updateMessage = null) }
        updateJob = viewModelScope.launch {
            var failure: String? = null
            try {
                if (resource != null && managed != null) { managed.sync(resource); managed.apply(id) } else runtime.updateProfileProvider(id, name)
                currentCoroutineContext().ensureActive()
            } catch (_: TimeoutCancellationException) { failure = "更新超时，请重试" }
            catch (error: CancellationException) { throw error }
            catch (error: com.lonnnnnng.flclash.service.ProviderOperationException) { failure = error.message }
            catch (_: Exception) { failure = "更新失败，请稍后重试" }
            finally {
                // 作者 long：与Flutter更新操作一致，超时或取消也必须释放忙态；否则已结束的任务会持续转圈并污染下一份配置。
                inFlightName = null
                mutableProviders.update { it.copy(updatingName = null, updatingNames = emptySet()) }
            }
            // 作者 long：单项恢复只能移除本项失败；后台下载仍属于原详情，换配置或关闭后才丢弃结果。
            if (generation == providerGeneration && profile.value == id) {
                mutableProviders.update { old ->
                    val messages = old.failedMessages.toMutableMap().apply { remove(name); failure?.let { put(name, it) } }.toMap()
                    val names = (old.failedNames - name) + if (failure == null) emptySet() else setOf(name)
                    old.copy(updatingName = null, failedNames = names, failedMessages = messages, updateFailed = names.isNotEmpty(),
                        updateMessage = if (names.size > 1 || failure == null && names.isNotEmpty()) failureSummary(names.size)
                            else failure ?: "Provider 已更新")
                }
                providerRetries.update { it + 1 }
                retries.update { it + 1 }
            }
        }
    }

    fun updateAllProviders(onlyFailed: Boolean = false) {
        val id = profile.value ?: return
        if (!foreground.value || updateJob?.isActive == true || document.value?.saving == true || providers.value.profileId != id) return
        val names = providers.value.providers.orEmpty().filter { (it.canUpdate || it.name in providers.value.managedNames) &&
            (!onlyFailed || it.name in providers.value.failedNames) }.map { it.name }
        if (names.isEmpty()) return
        val generation = providerGeneration
        val bindings = managedBindings.filterKeys { it in names }
        val http = names.filterNot { it in bindings }
        mutableProviders.update { it.copy(updatingNames = names.toSet(), updateMessage = null) }
        updateJob = viewModelScope.launch {
            val failures = linkedMapOf<String, String>()
            val synced = linkedSetOf<String>()
            try {
                supervisorScope {
                    val core = async {
                        if (http.isEmpty()) emptyList() else try { runtime.updateProfileProviders(id, http) }
                        catch (_: TimeoutCancellationException) { http.map { CoreProviderUpdateResult(it, "request_timeout") } }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { http.map { CoreProviderUpdateResult(it, "update_failed") } }
                    }
                    val resources = bindings.map { (name, resource) -> async {
                        try { checkNotNull(managed).sync(resource); CoreProviderUpdateResult(name, "") }
                        catch (_: TimeoutCancellationException) { CoreProviderUpdateResult(name, "request_timeout") }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { CoreProviderUpdateResult(name, "update_failed") }
                    } }
                    (core.await() + resources.awaitAll()).forEach { result ->
                        if (result.code.isNotEmpty()) failures[result.name] = providerFailureMessage(result.code)
                        else if (result.name in bindings) synced += result.name
                    }
                }
                // 作者 long：没有资源提交成功时不重载旧配置；部分成功仍应用已落盘版本，失败项单独保留重试。
                if (synced.isNotEmpty()) try { managed?.apply(id) }
                catch (_: TimeoutCancellationException) { synced.forEach { failures[it] = "应用更新超时，请重试" } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { synced.forEach { failures[it] = "资源已更新，但应用失败，请重试" } }
                currentCoroutineContext().ensureActive()
                if (generation == providerGeneration && profile.value == id) {
                    mutableProviders.update { old ->
                        val remaining = (old.failedNames - names.toSet()) + failures.keys
                        old.copy(failedNames = remaining, failedMessages = old.failedMessages - names.toSet() + failures,
                            updateFailed = remaining.isNotEmpty(), updateMessage = if (remaining.isEmpty()) "Providers 已全部更新" else failureSummary(remaining.size))
                    }
                    retryProviders(); retry()
                }
            } finally { mutableProviders.update { it.copy(updatingNames = emptySet()) } }
        }
    }

    private fun failureSummary(count: Int) = "${count} 项更新失败，可重试失败项"

    // 作者 long：只映射核心错误码，不把下载异常中的订阅地址、响应正文或凭据展示到页面。
    private fun providerFailureMessage(code: String) = when (code) {
        "request_timeout" -> "更新超时，请重试"
        "request_bad_response" -> "更新服务返回异常，请稍后重试"
        "provider_not_found" -> "Provider 已不存在，请刷新列表"
        "provider_updating" -> "Provider 正在更新，请稍后重试"
        else -> "更新失败，请稍后重试"
    }

    fun openProvider(name: String, edit: Boolean = false, pickFile: Boolean = false) {
        val id = profile.value ?: return
        if (!pickerRestored.isCompleted || !bodyRestored.isCompleted || bodyReadFailed || !foreground.value || updateJob?.isActive == true || document.value?.saving == true ||
            providers.value.providers?.none { it.name == name } != false) return
        if (document.value?.recoveryBlocked == true) { resumeProviderDocument(id); return }
        documentJob?.cancel()
        invalidatePicker()
        val generation = ++documentGeneration
        bodyClosing = false
        mutableBodyRoute.value = null
        mutableDocument.value = ProviderDocumentUiState(name, editing = edit, picking = pickFile)
        documentJob = viewModelScope.launch {
            try {
                clearBodyDraft(generation)
                val loaded = runtime.readProfileProvider(id, name)
                currentCoroutineContext().ensureActive()
                check(loaded.profileId == id && loaded.document.name == name)
                if (generation != documentGeneration) return@launch
                val token = if (pickFile) ++nextPickerToken else null
                if (token != null) {
                    pendingPicker = ProviderPickerSnapshot(id, name, loaded.recoveryKey, loaded.document.digest, token)
                    writePicker(pendingPicker)
                    currentCoroutineContext().ensureActive()
                    if (generation != documentGeneration) return@launch
                }
                mutableDocument.value = ProviderDocumentUiState(name, false, loaded, loaded.document.content,
                    editing = edit && loaded.document.editable, picking = pickFile,
                    pickerToken = token, draft = if (pickFile) null else ProviderDocumentDraft(id, name, loaded.recoveryKey,
                        loaded.document.digest, loaded.document.content, loaded.document.kind, loaded.document.editable,
                        loaded.document.content, edit && loaded.document.editable))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == documentGeneration)
                mutableDocument.value = ProviderDocumentUiState(name, false, editing = edit, picking = pickFile, error = "读取失败，请重试") }
        }
    }

    fun changeProviderContent(content: String) {
        val old = document.value ?: return
        if (!old.editing || old.loading || old.saving || old.committed || old.pendingSave && old.recoveryBlocked ||
            old.document == null && old.draft == null || old.content == content) return
        mutableDocument.value = old.copy(content = content, error = if (old.recoveryBlocked) old.error else null)
    }

    fun closeProvider() {
        val old = document.value ?: return
        if (old.saving) return
        val generation = ++documentGeneration
        bodyClosing = true
        documentJob?.cancel()
        invalidatePicker()
        if (old.draft == null && !bodyReadFailed) { mutableDocument.value = null; mutableBodyRoute.value = null; return }
        mutableDocument.value = old.copy(saving = true)
        documentJob = viewModelScope.launch {
            try {
                if (bodyReadFailed) bodyLock.withLock { documentStore.write(null); bodyReadFailed = false }
                else clearBodyDraft(generation)
                if (generation == documentGeneration) { mutableDocument.value = null; mutableBodyRoute.value = null }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == documentGeneration) {
                bodyClosing = false
                mutableDocument.value = old.copy(error = "无法清除 Provider 草稿，请重试退出")
            } }
        }
    }

    fun saveProvider() {
        val old = document.value ?: return
        if (!old.editing || old.loading || old.saving || old.committed || old.recoveryBlocked || old.document == null || !foreground.value || updateJob?.isActive == true) return
        submitProvider(old, old.content)
    }

    fun receiveProviderFile(token: Long, uri: String?) {
        viewModelScope.launch {
            pickerRestored.await()
            val saved = pendingPicker ?: return@launch
            if (saved.token != token) return@launch
            val generation = documentGeneration
            documentJob = currentCoroutineContext()[Job]
            recoveringPicker = saved
            pendingPicker = null
            try {
                writePicker(null)
                if (generation != documentGeneration) return@launch
                if (document.value == null && uri != null) {
                    mutableDocument.value = ProviderDocumentUiState(saved.name, saving = true, picking = true)
                    val rebound = runtime.restoreProfileProvider(saved.profileId, saved.name, saved.recoveryKey, saved.digest)
                    currentCoroutineContext().ensureActive()
                    if (generation != documentGeneration) return@launch
                    check(profile.value == null || profile.value == saved.profileId)
                    profile.value = saved.profileId
                    mutableDocument.value = ProviderDocumentUiState(saved.name, false, rebound, rebound.document.content,
                        picking = true, pickerToken = token)
                }
                consumeProviderFile(token, uri)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == documentGeneration) mutableDocument.value = ProviderDocumentUiState(saved.name, false, picking = true,
                    error = "配置或文件已变化，未导入文件，请重新打开资源后选择")
            } finally { if (recoveringPicker === saved) recoveringPicker = null }
        }
    }

    private fun consumeProviderFile(token: Long, uri: String?) {
        val old = document.value ?: return
        if (!old.picking || old.pickerToken != token || old.saving || old.document?.profileId != profile.value) return
        if (uri == null) { closeProvider(); return }
        val generation = documentGeneration
        mutableDocument.value = ProviderDocumentUiState(old.name, false, old.document, old.content, saving = true, picking = true)
        documentJob = viewModelScope.launch {
            try {
                val bytes = reader.read(uri).bytes
                require(bytes.size <= 8 * 1024 * 1024)
                val content = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                require('\u0000' !in content)
                currentCoroutineContext().ensureActive()
                if (generation != documentGeneration || old.document?.profileId != profile.value) return@launch
                // 作者 long：先冻结目标文件指纹再打开系统选择器；返回时仍使用原绑定，不能改写新配置中的同名资源。
                persistProvider(old, content, generation)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == documentGeneration) mutableDocument.value = ProviderDocumentUiState(old.name, false,
                old.document, old.content, picking = true, error = "文件导入失败，请选择 8 MiB 以内的 UTF-8 文本并检查资源格式") }
        }
    }

    private fun submitProvider(old: ProviderDocumentUiState, content: String) {
        val generation = documentGeneration
        mutableDocument.value = old.copy(content = content, saving = true, pendingSave = old.draft != null)
        documentJob = viewModelScope.launch { persistProvider(old, content, generation) }
    }

    private suspend fun persistProvider(old: ProviderDocumentUiState, content: String, generation: Long) {
        var committed = false
        try {
            if (old.draft != null && documentStore !== com.lonnnnnng.flclash.data.NoopProfileDraftStore) {
                // 作者 long：未成功落盘保存意图时禁止写正式缓存，否则进程中断后无法区分未提交与已提交。
                bodyLock.withLock {
                    check(generation == documentGeneration)
                    val current = checkNotNull(document.value)
                    val snapshot = checkNotNull(bodySnapshot(current)).copy(content = content, pending = true)
                    documentStore.write(withContext(draftDispatcher) { pickerGson.toJson(snapshot).toByteArray(Charsets.UTF_8) })
                }
                EditorCommitProbe.checkpoint(EditorCommitWindow.PROVIDER_INTENT)
                currentCoroutineContext().ensureActive()
                check(generation == documentGeneration)
            }
            // 作者 long：编辑期间核心可刷新路由，重试需取得新进程内绑定；仍核对打开时的输入/文件指纹，绝不把外部新版本当作基线。
            val target = if (old.draft != null && documentStore !== com.lonnnnnng.flclash.data.NoopProfileDraftStore)
                runtime.recoverProfileProviderDocument(old.draft.profileId, old.name, old.draft.recoveryKey, old.draft.digest, null)
            else checkNotNull(old.document)
            currentCoroutineContext().ensureActive()
            check(generation == documentGeneration)
            runtime.saveProfileProvider(target, content)
            committed = true
            if (old.draft != null) EditorCommitProbe.checkpoint(EditorCommitWindow.PROVIDER_COMMITTED)
            verifyProviderReadback(old, content)
            currentCoroutineContext().ensureActive()
            if (generation == documentGeneration) {
                bodyClosing = true
                clearBodyDraft(generation)
                mutableDocument.value = null
                mutableBodyRoute.value = null
                mutableProviders.update { it.copy(updateMessage = "Provider 已保存", updateFailed = false) }
                retryProviders(); retry()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            bodyClosing = false
            committed = committed || (error as? ProviderOperationException)?.committed == true
            if (generation == documentGeneration) mutableDocument.value = old.copy(loading = false, saving = false, content = content,
            error = if (committed) "Provider 已提交，但回读确认失败，请重新读取确认" else (error as? ProviderOperationException)?.message ?: "保存失败，请检查内容后重试",
            committed = committed, pendingSave = committed, draft = document.value?.draft ?: old.draft) }
    }

    private suspend fun verifyProviderReadback(old: ProviderDocumentUiState, content: String) {
        val loaded = runtime.readProfileProvider(checkNotNull(old.document).profileId, old.name)
        check(loaded.profileId == old.document.profileId && loaded.recoveryKey == old.document.recoveryKey &&
            loaded.document.name == old.name && loaded.document.content == content)
    }

    fun retryProviderReadback() {
        val old = document.value ?: return
        if (!old.committed || old.saving) return
        val generation = documentGeneration
        mutableDocument.value = old.copy(saving = true)
        documentJob = viewModelScope.launch {
            try {
                verifyProviderReadback(old, old.content)
                if (generation == documentGeneration) {
                    bodyClosing = true
                    clearBodyDraft(generation)
                    mutableDocument.value = null
                    mutableBodyRoute.value = null
                    mutableProviders.update { it.copy(updateMessage = "Provider 已保存并回读确认", updateFailed = false) }
                    retryProviders(); retry()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == documentGeneration) {
                bodyClosing = false
                mutableDocument.value = old.copy(saving = false, error = "Provider 已提交，但回读确认失败，请稍后重新读取")
            } }
        }
    }

    fun retryProviders() { if (foreground.value && profile.value != null && !providers.value.isLoading) providerRetries.update { it + 1 } }
    fun show(profileId: Long?) {
        val request = ++showRequest
        if (!pickerRestored.isCompleted || !bodyRestored.isCompleted) {
            viewModelScope.launch { pickerRestored.await(); bodyRestored.await(); if (request == showRequest) show(profileId) }
            return
        }
        // 作者 long：Activity组合被销毁不是用户关闭；系统选择器和冷恢复仍持有原目标，不能由暂时空页面清掉。
        val pickerTarget = pendingPicker ?: recoveringPicker
        if (profileId != null) currentSelection = profileId
        if (bodyReadFailed) return
        if (profileId != null && documentStore !== com.lonnnnnng.flclash.data.NoopProfileDraftStore)
            reconcileProviderSelection(profileId)
        if (profileId == null && (mutableBodyRoute.value != null || bodyReadFailed)) return
        val recovered = document.value
        if (recovered?.draft != null && recovered.recoveryBlocked) {
            profile.value = profileId
            // 作者 long：失效目标的正文继续可见；真实选择变化只撤销绑定，不删除尚未确认放弃的草稿。
            if (profileId != null) resumeProviderDocument(profileId)
            return
        }
        if (profileId != null && profileId == mutableBodyRoute.value) mutableBodyRoute.value = null
        if (profileId == null && (pickerTarget != null || document.value?.picking == true || document.value?.saving == true)) return
        if (profile.value != profileId) {
            if (profileId != null && pickerTarget?.profileId == profileId) {
                profile.value = profileId
                mutableProviders.value = ProfileProvidersUiState(profileId, updatingName = inFlightName)
                return
            }
            invalidatePicker()
            providerGeneration++; documentGeneration++; documentJob?.cancel(); mutableDocument.value = null; managedBindings = emptyMap()
            val generation = documentGeneration
            viewModelScope.launch { try { clearBodyDraft(generation) } catch (_: Exception) { /* 作者 long：清除失败不覆盖原密文。 */ } }
            mutableProviders.value = ProfileProvidersUiState(profileId, updatingName = inFlightName)
        }
        profile.value = profileId
    }
    fun setForeground(value: Boolean) {
        if (foreground.value != value) {
            // 作者 long：后台只暂停列表轮询；未保存正文、失败原因和读取任务留在当前ViewModel会话，避免绕过退出确认。
            // 正文和选区只进入独立加密草稿，系统保存状态不承载 Provider 凭据。
            mutableProviders.update { it.copy(providers = null, isLoading = false, error = null, managedNames = emptySet()) }
        }
        foreground.value = value
    }
    fun retry() { if (foreground.value && profile.value != null && !state.value.isLoading) retries.update { it + 1 } }
}
