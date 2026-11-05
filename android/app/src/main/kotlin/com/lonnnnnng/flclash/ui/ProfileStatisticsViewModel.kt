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
class ProviderDocumentUiState(val name: String, val loading: Boolean = true,
    val document: ProfileProviderDocument? = null, val content: String = "", val editing: Boolean = false,
    val saving: Boolean = false, val error: String? = null, val picking: Boolean = false,
    val pickerToken: Long? = null, val committed: Boolean = false) {
    val dirty: Boolean get() = !committed && editing && document != null && content != document.document.content
}

@HiltViewModel
class ProfileStatisticsViewModel @Inject constructor(private val runtime: ProfileStatisticsRuntime,
    private val reader: ProfileFileDocumentReader = ProfileFileDocumentReader { error("文件读取不可用") },
    private val managed: ManagedProfileProviders? = null,
    @javax.inject.Named("provider-picker") private val pickerStore: com.lonnnnnng.flclash.data.ProfileDraftStore = com.lonnnnnng.flclash.data.NoopProfileDraftStore) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileStatisticsUiState())
    val state = mutableState.asStateFlow()
    private val mutableProviders = MutableStateFlow(ProfileProvidersUiState())
    val providers = mutableProviders.asStateFlow()
    private val providerRetries = MutableStateFlow(0L)
    private var updateJob: Job? = null
    private var inFlightName: String? = null
    private var visibilityGeneration = 0L
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
        if (!pickerRestored.isCompleted || !foreground.value || updateJob?.isActive == true || document.value?.saving == true ||
            providers.value.providers?.none { it.name == name } != false) return
        documentJob?.cancel()
        invalidatePicker()
        val generation = ++documentGeneration
        val window = visibilityGeneration
        mutableDocument.value = ProviderDocumentUiState(name, editing = edit, picking = pickFile)
        documentJob = viewModelScope.launch {
            try {
                val loaded = runtime.readProfileProvider(id, name)
                currentCoroutineContext().ensureActive()
                check(loaded.profileId == id && loaded.document.name == name)
                if (generation != documentGeneration || window != visibilityGeneration) return@launch
                val token = if (pickFile) ++nextPickerToken else null
                if (token != null) {
                    pendingPicker = ProviderPickerSnapshot(id, name, loaded.recoveryKey, loaded.document.digest, token)
                    writePicker(pendingPicker)
                    currentCoroutineContext().ensureActive()
                    if (generation != documentGeneration || window != visibilityGeneration) return@launch
                }
                mutableDocument.value = ProviderDocumentUiState(name, false, loaded, loaded.document.content,
                    editing = edit && loaded.document.editable, picking = pickFile,
                    pickerToken = token)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == documentGeneration && window == visibilityGeneration)
                mutableDocument.value = ProviderDocumentUiState(name, false, editing = edit, picking = pickFile, error = "读取失败，请重试") }
        }
    }

    fun changeProviderContent(content: String) {
        val old = document.value ?: return
        if (!old.editing || old.saving || old.committed || old.document == null || old.content == content) return
        mutableDocument.value = ProviderDocumentUiState(old.name, false, old.document, content, true)
    }

    fun closeProvider() {
        if (document.value?.saving == true) return
        documentGeneration++; documentJob?.cancel(); mutableDocument.value = null
        invalidatePicker()
    }

    fun saveProvider() {
        val old = document.value ?: return
        if (!old.editing || old.saving || old.committed || old.document == null || !foreground.value || updateJob?.isActive == true) return
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
        mutableDocument.value = ProviderDocumentUiState(old.name, false, old.document, content, old.editing, saving = true)
        documentJob = viewModelScope.launch { persistProvider(old, content, generation) }
    }

    private suspend fun persistProvider(old: ProviderDocumentUiState, content: String, generation: Long) {
        var committed = false
        try {
            runtime.saveProfileProvider(checkNotNull(old.document), content)
            committed = true
            verifyProviderReadback(old, content)
            currentCoroutineContext().ensureActive()
            if (generation == documentGeneration) {
                mutableDocument.value = null
                mutableProviders.update { it.copy(updateMessage = "Provider 已保存", updateFailed = false) }
                retryProviders(); retry()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            committed = committed || (error as? ProviderOperationException)?.committed == true
            if (generation == documentGeneration) mutableDocument.value = ProviderDocumentUiState(old.name, false,
            old.document, content, old.editing,
            error = if (committed) "Provider 已提交，但回读确认失败，请重新读取确认" else (error as? ProviderOperationException)?.message ?: "保存失败，请检查内容后重试",
            picking = old.picking, committed = committed) }
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
        mutableDocument.value = ProviderDocumentUiState(old.name, false, old.document, old.content, old.editing, saving = true, committed = true)
        documentJob = viewModelScope.launch {
            try {
                verifyProviderReadback(old, old.content)
                if (generation == documentGeneration) {
                    mutableDocument.value = null
                    mutableProviders.update { it.copy(updateMessage = "Provider 已保存并回读确认", updateFailed = false) }
                    retryProviders(); retry()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == documentGeneration) mutableDocument.value = ProviderDocumentUiState(old.name, false,
                old.document, old.content, old.editing, error = "Provider 已提交，但回读确认失败，请稍后重新读取", committed = true) }
        }
    }

    fun retryProviders() { if (foreground.value && profile.value != null && !providers.value.isLoading) providerRetries.update { it + 1 } }
    fun show(profileId: Long?) {
        if (!pickerRestored.isCompleted) {
            viewModelScope.launch { pickerRestored.await(); show(profileId) }
            return
        }
        // 作者 long：Activity组合被销毁不是用户关闭；系统选择器和冷恢复仍持有原目标，不能由暂时空页面清掉。
        val pickerTarget = pendingPicker ?: recoveringPicker
        if (profileId == null && (pickerTarget != null || document.value?.picking == true || document.value?.saving == true)) return
        if (profile.value != profileId) {
            if (profileId != null && pickerTarget?.profileId == profileId) {
                profile.value = profileId
                mutableProviders.value = ProfileProvidersUiState(profileId, updatingName = inFlightName)
                return
            }
            invalidatePicker()
            providerGeneration++; visibilityGeneration++; documentGeneration++; documentJob?.cancel(); mutableDocument.value = null; managedBindings = emptyMap()
            mutableProviders.value = ProfileProvidersUiState(profileId, updatingName = inFlightName)
        }
        profile.value = profileId
    }
    fun setForeground(value: Boolean) {
        if (foreground.value != value) {
            visibilityGeneration++
            // 作者 long：系统文件选择器会遮挡应用，保留其绑定；其他后台状态作废未提交文档，已受理保存仍等待核心结果。
            if (!value && document.value?.pickerToken == null && document.value?.saving != true && document.value?.committed != true) {
                documentGeneration++; documentJob?.cancel(); mutableDocument.value = null
            }
            // 作者 long：后台仅清除列表和文档，不丢失当前详情的失败清单；回到前台仍能继续恢复剩余项。
            mutableProviders.update { it.copy(providers = null, isLoading = false, error = null, managedNames = emptySet()) }
        }
        foreground.value = value
    }
    fun retry() { if (foreground.value && profile.value != null && !state.value.isLoading) retries.update { it + 1 } }
}
