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
    val managedNames: Set<String> = emptySet())

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
            combine(foreground, profile, runtime.profileStatisticsRevision, runtime.state, retries) { front, id, revision, status, retry ->
                listOf(front, id, revision, status, retry)
            }.collectLatest {
                val id = profile.value
                mutableState.value = ProfileStatisticsUiState(id)
                if (!foreground.value || id == null || runtime.state.value in listOf(CoreRuntimeStatus.STARTING, CoreRuntimeStatus.STOPPING)) return@collectLatest
                // 作者 long：仅当前详情可见且应用前台时刷新 Provider 数量，关闭/切配置/后台取消整轮读取并清除旧统计。
                while (isActive) {
                    mutableState.update { it.copy(isLoading = it.counts == null, error = null) }
                    try {
                        val answer = withTimeout(8_000L) { runtime.profileStatistics(id) }
                        currentCoroutineContext().ensureActive()
                        check(answer.profileId == id)
                        mutableState.value = ProfileStatisticsUiState(id, answer)
                    } catch (_: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                        mutableState.value = ProfileStatisticsUiState(id, error = "读取配置统计超时，请重试")
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        // 作者 long：配置解析错误可能夹带节点或密码，页面只显示受控提示，不将错误正文向 UI 透传。
                        mutableState.value = ProfileStatisticsUiState(id, error = "读取配置统计失败，请重试")
                    }
                    delay(5_000L)
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            combine(foreground, profile, runtime.profileStatisticsRevision, runtime.state, providerRetries) { front, id, revision, status, retry ->
                listOf(front, id, revision, status, retry)
            }.collectLatest {
                val id = profile.value
                mutableProviders.update { old -> if (old.profileId == id && foreground.value)
                    old.copy(providers = null, isLoading = false, error = null) else ProfileProvidersUiState(id, updatingName = inFlightName) }
                if (!foreground.value || id == null || runtime.state.value in listOf(CoreRuntimeStatus.STARTING, CoreRuntimeStatus.STOPPING)) return@collectLatest
                while (isActive) {
                    mutableProviders.update { it.copy(isLoading = it.providers == null, error = null) }
                    try {
                        val rows = withTimeout(8_000L) { runtime.profileProviders(id) }
                        currentCoroutineContext().ensureActive()
                        val bindings = managed?.bindings(id, rows).orEmpty()
                        currentCoroutineContext().ensureActive()
                        managedBindings = bindings
                        mutableProviders.update { it.copy(providers = rows, isLoading = false, error = null, managedNames = bindings.keys) }
                    } catch (error: CancellationException) {
                        if (error !is TimeoutCancellationException) throw error
                        currentCoroutineContext().ensureActive()
                        mutableProviders.update { it.copy(providers = null, isLoading = false, error = "读取 Providers 超时，请重试") }
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        mutableProviders.update { it.copy(providers = null, isLoading = false, error = "读取 Providers 失败，请重试") }
                    }
                    delay(5_000L)
                }
            }
        }
    }

    fun updateProvider(name: String) {
        val id = profile.value ?: return
        if (!foreground.value || updateJob?.isActive == true || providers.value.profileId != id ||
            document.value?.saving == true || providers.value.providers?.any { it.name == name && (it.canUpdate || name in providers.value.managedNames) } != true) return
        val generation = visibilityGeneration
        inFlightName = name
        val resource = managedBindings[name]
        mutableProviders.update { it.copy(updatingName = name, updatingNames = setOf(name), updateMessage = null, updateFailed = false, failedNames = emptySet()) }
        updateJob = viewModelScope.launch {
            var failure: String? = null
            try {
                if (resource != null && managed != null) { managed.sync(resource); managed.apply(id) } else runtime.updateProfileProvider(id, name)
                currentCoroutineContext().ensureActive()
            } catch (error: CancellationException) { throw error }
            catch (error: com.lonnnnnng.flclash.service.ProviderOperationException) { failure = error.message }
            catch (_: Exception) { failure = "更新失败，请稍后重试" }
            inFlightName = null
            mutableProviders.update { it.copy(updatingName = null, updatingNames = emptySet()) }
            // 作者 long：关闭、后台或换配置后，旧下载即使完成也不再向新详情写入提示或触发代理组刷新。
            if (generation == visibilityGeneration && foreground.value && profile.value == id) {
                mutableProviders.update { it.copy(updatingName = null, updateMessage = failure ?: "Provider 已更新", updateFailed = failure != null,
                    failedNames = if (failure == null) emptySet() else setOf(name)) }
                providerRetries.update { it + 1 }
                retries.update { it + 1 }
            }
        }
    }

    fun updateAllProviders(onlyFailed: Boolean = false) {
        val id = profile.value ?: return
        if (!foreground.value || updateJob?.isActive == true || document.value?.saving == true) return
        val names = providers.value.providers.orEmpty().filter { (it.canUpdate || it.name in providers.value.managedNames) &&
            (!onlyFailed || it.name in providers.value.failedNames) }.map { it.name }
        if (names.isEmpty()) return
        val generation = visibilityGeneration
        val bindings = managedBindings.filterKeys { it in names }
        val http = names.filterNot { it in bindings }
        mutableProviders.update { it.copy(updatingNames = names.toSet(), updateMessage = null, updateFailed = false, failedNames = emptySet()) }
        updateJob = viewModelScope.launch {
            val failures = linkedSetOf<String>()
            try {
                supervisorScope {
                    val core = async {
                        if (http.isEmpty()) emptyList() else try { runtime.updateProfileProviders(id, http) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { http.map { CoreProviderUpdateResult(it, "update_failed") } }
                    }
                    val resources = bindings.map { (name, resource) -> async {
                        try { checkNotNull(managed).sync(resource); CoreProviderUpdateResult(name, "") }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { CoreProviderUpdateResult(name, "update_failed") }
                    } }
                    failures += (core.await() + resources.awaitAll()).filter { it.code.isNotEmpty() }.map { it.name }
                }
                if (bindings.isNotEmpty()) try { managed?.apply(id) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failures += bindings.keys }
                currentCoroutineContext().ensureActive()
                if (generation == visibilityGeneration && foreground.value && profile.value == id) {
                    mutableProviders.update { it.copy(failedNames = failures, updateFailed = failures.isNotEmpty(),
                        updateMessage = if (failures.isEmpty()) "Providers 已全部更新" else "${failures.size} 项更新失败，可重试失败项") }
                    retryProviders(); retry()
                }
            } finally { mutableProviders.update { it.copy(updatingNames = emptySet()) } }
        }
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
        if (!old.editing || old.saving || old.committed || old.document == null) return
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
            visibilityGeneration++; documentGeneration++; documentJob?.cancel(); mutableDocument.value = null; managedBindings = emptyMap()
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
            mutableProviders.value = ProfileProvidersUiState(profile.value, updatingName = inFlightName, updatingNames = providers.value.updatingNames)
        }
        foreground.value = value
    }
    fun retry() { if (foreground.value && profile.value != null && !state.value.isLoading) retries.update { it + 1 } }
}
