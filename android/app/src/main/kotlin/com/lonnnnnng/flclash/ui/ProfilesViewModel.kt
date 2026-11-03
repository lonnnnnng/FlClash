package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.ProfileConfigValidator
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProxyGroupFilterValidator
import com.lonnnnnng.flclash.data.ProfileDialerEditor
import com.lonnnnnng.flclash.data.ProxyGroupDrafts
import com.lonnnnnng.flclash.data.ProxyGroupsSave
import com.lonnnnnng.flclash.data.ProfileProxyGroupsEditor
import com.lonnnnnng.flclash.data.ProfileProxiesEditor
import com.lonnnnnng.flclash.data.ProfileProvidersEditor
import com.lonnnnnng.flclash.data.ProfileRulesEditor
import com.lonnnnnng.flclash.data.CustomRuleCatalog
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.data.YamlProfileConfigValidator
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import com.lonnnnnng.flclash.service.ProfileDnsOverrides
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
import com.lonnnnnng.flclash.service.ProfileNtpOverrides
import com.lonnnnnng.flclash.service.ProfileSnifferOverrides
import com.lonnnnnng.flclash.service.ProxyProviderOverride
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProfilesUiState(
    val profiles: List<Profile> = emptyList(),
    val currentProfileId: Long? = null,
    val isBusy: Boolean = false,
)

data class ProfileEditorUiState(
    val profile: Profile? = null,
    val content: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isNew: Boolean = false,
    val hasPersistedChanges: Boolean = false,
    val isReadingFile: Boolean = false,
    val replacement: ProfileFileReplacementPreview? = null,
    val overview: CustomProfileSummary? = null,
)

data class ProfileFileReplacementPreview(val name: String, val sizeBytes: Int, val isEncrypted: Boolean, val isValidYaml: Boolean)

data class RemoteProfileEditorUiState(
    val profile: Profile? = null,
    val isSaving: Boolean = false,
)

data class ProfileRulesEditorUiState(
    val profile: Profile? = null,
    val sourceContent: String = "",
    val rules: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val catalog: CustomRuleCatalog = CustomRuleCatalog(),
    val error: String? = null,
)

data class ProfileProxiesEditorUiState(
    val profile: Profile? = null,
    val sourceContent: String = "",
    val proxies: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
)

data class ProfileProxyGroupsEditorUiState(
    val profile: Profile? = null,
    val sourceContent: String = "",
    val groups: String = "",
    val initialGroupIndex: Int? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
)

data class ProfileProvidersEditorUiState(
    val profile: Profile? = null,
    val sourceContent: String = "",
    val proxyProviders: String = "",
    val ruleProviders: String = "",
    val proxyProviderOverrides: Map<String, ProxyProviderOverride> = emptyMap(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
)

data class ProfileDnsEditorUiState(
    val profile: Profile? = null,
    val overrides: ProfileDnsOverrides = ProfileDnsOverrides(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
)

data class ProfileDialersEditorUiState(
    val profile: Profile? = null,
    val sourceContent: String = "",
    val dialers: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
)

data class ProfileMoreEditorUiState(
    val profile: Profile? = null,
    val ntp: ProfileNtpOverrides = ProfileNtpOverrides(),
    val sniffer: ProfileSnifferOverrides = ProfileSnifferOverrides(),
    val isSaving: Boolean = false,
)

data class CustomImportUiState(
    val isLoading: Boolean = false,
    val name: String? = null,
    val summary: CustomProfileSummary? = null,
    val droppedSubRules: Int = 0,
)

internal const val PROFILE_MUTATION_BLOCKED_MESSAGE = "核心运行中，请先停止核心后再修改配置"

/**
 * 作者 long：核心启停期间也可能正在读取配置文件，文件编辑必须等到稳定停止状态，
 * 避免启动拿到半途变更的输入；配置选择另由生命周期管理器串行处理，不受此写锁限制。
 */
internal fun isProfileMutationBlocked(runtimeStatus: CoreRuntimeStatus): Boolean =
    runtimeStatus == CoreRuntimeStatus.STARTING || runtimeStatus == CoreRuntimeStatus.RUNNING || runtimeStatus == CoreRuntimeStatus.STOPPING

@HiltViewModel
class ProfilesViewModel internal constructor(
    private val repository: ProfileStore,
    private val runtimeManager: CoreRuntimeStatusProvider,
    private val configValidator: ProfileConfigValidator = YamlProfileConfigValidator(),
    private val fileReader: com.lonnnnnng.flclash.data.ProfileFileDocumentReader = com.lonnnnnng.flclash.data.ProfileFileDocumentReader { error("文件读取器不可用") },
    private val overviewDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    private val groupFilterValidator: ProxyGroupFilterValidator = ProxyGroupFilterValidator { error("过滤表达式校验不可用") },
) : ViewModel() {
    @Inject constructor(
        repository: ProfileStore,
        runtimeManager: CoreRuntimeStatusProvider,
        configValidator: ProfileConfigValidator = YamlProfileConfigValidator(),
        fileReader: com.lonnnnnng.flclash.data.ProfileFileDocumentReader = com.lonnnnnng.flclash.data.ProfileFileDocumentReader { error("文件读取器不可用") },
        groupFilterValidator: ProxyGroupFilterValidator = ProxyGroupFilterValidator { error("过滤表达式校验不可用") },
    ) : this(repository, runtimeManager, configValidator, fileReader, Dispatchers.Default, groupFilterValidator)
    internal val extension = ProfileExtensionController(repository, runtimeManager, viewModelScope)
    internal val fileImport = ProfileFileImportController(repository, runtimeManager, fileReader, configValidator, viewModelScope, overviewDispatcher)
    private val mutableRecentGroupIcons = MutableStateFlow<List<String>>(emptyList())
    val recentGroupIcons: StateFlow<List<String>> = mutableRecentGroupIcons
    init { viewModelScope.launch { mutableRecentGroupIcons.value = repository.recentGroupIcons() } }
    fun recordGroupIcon(icon: String, remove: Boolean) {
        viewModelScope.launch {
            try { repository.recordGroupIcon(icon, remove); mutableRecentGroupIcons.value = repository.recentGroupIcons() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableErrors.emit("无法更新最近图标记录，请重试") }
        }
    }
    private val busy = MutableStateFlow(false)
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val mutableEditor = MutableStateFlow(ProfileEditorUiState())
    private val mutableRemoteEditor = MutableStateFlow(RemoteProfileEditorUiState())
    private val mutableRulesEditor = MutableStateFlow(ProfileRulesEditorUiState())
    private val mutableProxiesEditor = MutableStateFlow(ProfileProxiesEditorUiState())
    private val mutableProxyGroupsEditor = MutableStateFlow(ProfileProxyGroupsEditorUiState())
    private val mutableProvidersEditor = MutableStateFlow(ProfileProvidersEditorUiState())
    private val mutableDnsEditor = MutableStateFlow(ProfileDnsEditorUiState())
    private val mutableDialersEditor = MutableStateFlow(ProfileDialersEditorUiState())
    private val mutableMoreEditor = MutableStateFlow(ProfileMoreEditorUiState())
    private val mutableCustomImport = MutableStateFlow(CustomImportUiState())
    private var customImportContent: String? = null
    private var customImportProfile: Profile? = null
    private var customImportOriginal: String? = null
    private var customImportJob: kotlinx.coroutines.Job? = null
    private var customImportToken = 0L
    private val mutableAgeKeyRequired = MutableStateFlow(false)
    private var retryAgeImport: (suspend (String) -> Unit)? = null
    private var ageImportGeneration = 0L
    private var localEditorGeneration = 0L
    private var proxyGroupsGeneration = 0L
    private var rulesGeneration = 0L
    private var pendingFilePick: Triple<Long, Long, Long>? = null
    private var nextFilePickToken = 0L
    private var replacementBytes: ByteArray? = null
    private var replacementReadJob: kotlinx.coroutines.Job? = null
    private var overviewJob: kotlinx.coroutines.Job? = null
    val ageKeyRequired: StateFlow<Boolean> = mutableAgeKeyRequired

    val state: StateFlow<ProfilesUiState> = combine(
        repository.profiles,
        repository.currentProfileId,
        busy,
    ) { profiles, currentId, isBusy ->
        ProfilesUiState(profiles, currentId, isBusy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUiState())

    val errors = mutableErrors.asSharedFlow()
    val editorState: StateFlow<ProfileEditorUiState> = mutableEditor
    val remoteEditorState: StateFlow<RemoteProfileEditorUiState> = mutableRemoteEditor
    val rulesEditorState: StateFlow<ProfileRulesEditorUiState> = mutableRulesEditor
    val proxiesEditorState: StateFlow<ProfileProxiesEditorUiState> = mutableProxiesEditor
    val proxyGroupsEditorState: StateFlow<ProfileProxyGroupsEditorUiState> = mutableProxyGroupsEditor
    val providersEditorState: StateFlow<ProfileProvidersEditorUiState> = mutableProvidersEditor
    val dnsEditorState: StateFlow<ProfileDnsEditorUiState> = mutableDnsEditor
    val dialersEditorState: StateFlow<ProfileDialersEditorUiState> = mutableDialersEditor
    val moreEditorState: StateFlow<ProfileMoreEditorUiState> = mutableMoreEditor
    val customImportState: StateFlow<CustomImportUiState> = mutableCustomImport

    fun select(id: Long) {
        if (busy.value) return
        // 作者 long：配置切换由生命周期管理器串行校验并热应用，运行状态不再限制当前选择。
        launchAction { runtimeManager.selectProfile(id, repository::selectProfile) }
    }

    fun addRemote(url: String, label: String, ageSecretKey: String = "") = importWithAgeKey(ageSecretKey) { key ->
        repository.addRemoteProfile(url, label, key)
    }

    fun addRemoteBatch(urls: List<String>) {
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        val distinctUrls = urls.map(String::trim).filter(String::isNotEmpty).distinct()
        if (distinctUrls.isEmpty()) return

        val existingUrls = repository.profiles.value
            .asSequence()
            .filter { it.type == ProfileType.URL }
            .map(Profile::url)
            .toSet()
        val importUrls = distinctUrls.filterNot(existingUrls::contains)
        val skippedExisting = distinctUrls.size - importUrls.size

        viewModelScope.launch {
            busy.value = true
            var imported = 0
            var failed = 0
            try {
                // 作者 long：逐条导入并隔离订阅失败，避免一个失效链接中断同批其他配置；错误只报告数量以免泄露带凭据的 URL。
                importUrls.forEach { url ->
                    try {
                        repository.addRemoteProfile(url, "")
                        imported++
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        failed++
                    }
                }
            } finally {
                busy.value = false
            }

            mutableErrors.emit(
                "批量导入完成：新增 $imported 个，跳过重复 $skippedExisting 个，失败 $failed 个",
            )
        }
    }

    fun addFile(label: String, bytes: ByteArray) = importWithAgeKey("") { key ->
        repository.addFileProfile(label, bytes, key)
    }

    fun cancelAgeImport() {
        ageImportGeneration++
        retryAgeImport = null
        mutableAgeKeyRequired.value = false
    }

    fun submitAgeKey(key: String) {
        val action = retryAgeImport ?: return
        if (key.isBlank()) return
        cancelAgeImport()
        // 作者 long：只为首次缺钥提供一次补输；错误密钥直接报错，自动更新不进入交互重试。
        importWithAgeKey(key.trim(), allowPrompt = false, action)
    }

    private fun importWithAgeKey(key: String, allowPrompt: Boolean = true, action: suspend (String) -> Unit) {
        if (busy.value) return
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        cancelAgeImport()
        val generation = ageImportGeneration
        busy.value = true
        viewModelScope.launch {
            try {
                action(key.trim())
            } catch (error: CancellationException) {
                throw error
            } catch (error: com.lonnnnnng.flclash.data.AgeSecretKeyRequiredException) {
                if (allowPrompt && generation == ageImportGeneration) {
                    retryAgeImport = action
                    mutableAgeKeyRequired.value = true
                } else if (!allowPrompt) mutableErrors.emit(error.message ?: "请输入 age 解密密钥")
            } catch (error: Exception) {
                mutableErrors.emit(error.message ?: "导入配置失败")
            } finally {
                busy.value = false
            }
        }
    }

    // 作者 long：预检只向确认弹窗返回安全状态，不把包含敏感配置片段的解析器错误直接展示给用户。
    suspend fun validateFileImport(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            configValidator.validate(bytes)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    fun addCustom(label: String) {
        if (busy.value) return
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        viewModelScope.launch {
            busy.value = true
            try {
                // 作者 long：新建后直接进入编辑，编辑状态由 ViewModel 保留，不能依赖一次性页面事件而丢失创建结果。
                runCatching { repository.addCustomProfile(label) }
                    .onSuccess { profile -> openEditor(profile, isNew = true) }
                    .onFailure { error -> mutableErrors.emit(error.message ?: "创建配置失败") }
            } finally {
                busy.value = false
            }
        }
    }

    fun reportError(message: String) {
        mutableErrors.tryEmit(message)
    }

    fun openEditor(profile: Profile, isNew: Boolean = false) {
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再编辑配置")
            return
        }
        closeEditor()
        val generation = localEditorGeneration
        if (profile.type == ProfileType.URL) {
            mutableRemoteEditor.value = RemoteProfileEditorUiState(profile)
            return
        }
        mutableEditor.value = ProfileEditorUiState(profile = profile, isLoading = true, isNew = isNew)
        viewModelScope.launch {
            if (generation != localEditorGeneration) return@launch
            runCatching { repository.readLocalProfileContent(profile.id) }
                .onSuccess { content ->
                    if (generation == localEditorGeneration) {
                        mutableEditor.value = ProfileEditorUiState(profile = profile, content = content, isNew = isNew)
                        refreshOverview()
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (generation == localEditorGeneration) {
                        mutableEditor.value = ProfileEditorUiState()
                        mutableErrors.emit("无法读取配置")
                    }
                }
        }
    }

    fun closeEditor() {
        localEditorGeneration++
        rulesGeneration++
        mutableRulesEditor.value = ProfileRulesEditorUiState()
        closeProxyGroupsEditor()
        cancelCustomImport()
        overviewJob?.cancel()
        overviewJob = null
        pendingFilePick = null
        replacementReadJob?.cancel()
        replacementReadJob = null
        replacementBytes = null
        mutableEditor.value = ProfileEditorUiState()
        mutableRemoteEditor.value = RemoteProfileEditorUiState()
        mutableMoreEditor.value = ProfileMoreEditorUiState()
    }

    fun beginCustomImportPick(): Long? {
        val editor = mutableEditor.value
        if (editor.profile?.type != ProfileType.CUSTOM || editor.isSaving || editor.isReadingFile || busy.value) return null
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return null
        }
        cancelCustomImport()
        return customImportToken
    }

    fun acceptCustomImportFile(token: Long?, uri: String?, ageSecretKey: String = "") {
        if (token != customImportToken || uri == null) return
        prepareCustomImport(token, ageSecretKey) { fileReader.read(uri).let { it.name to it.bytes } }
    }

    fun importCustomFromUrl(url: String, ageSecretKey: String = "") {
        val token = beginCustomImportPick() ?: return
        val normalized = runCatching { normalizeRemoteProfileUrl(url) }.getOrElse {
            mutableErrors.tryEmit("请输入有效的 HTTP/HTTPS 链接")
            return
        }
        prepareCustomImport(token, ageSecretKey) { "URL 配置" to repository.downloadCustomImport(normalized) }
    }

    private fun prepareCustomImport(token: Long, ageSecretKey: String, read: suspend () -> Pair<String, ByteArray>) {
        val editor = mutableEditor.value
        val profile = editor.profile?.takeIf { it.type == ProfileType.CUSTOM } ?: return
        val generation = localEditorGeneration
        mutableCustomImport.value = CustomImportUiState(isLoading = true)
        mutableEditor.value = editor.copy(isReadingFile = true)
        customImportJob = viewModelScope.launch {
            try {
                // 作者 long：读取器和仓库各自负责 IO；统一调度器让取消、旧会话隔离和测试等待都覆盖整条导入链。
                val (name, bytes) = withContext(overviewDispatcher) { read() }
                val imported = withContext(overviewDispatcher) { repository.decodeCustomImport(bytes, ageSecretKey) }
                val content = imported.content
                val summary = withContext(overviewDispatcher) { customProfileSummary(content, profile.copy(overridesJson = "{}")) }
                if (generation == localEditorGeneration && token == customImportToken) {
                    customImportContent = content
                    customImportProfile = profile
                    customImportOriginal = editor.content
                    mutableCustomImport.value = CustomImportUiState(name = name, summary = summary, droppedSubRules = imported.droppedSubRules)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == localEditorGeneration && token == customImportToken) {
                    mutableCustomImport.value = CustomImportUiState()
                    mutableErrors.emit(if (error is com.lonnnnnng.flclash.data.AgeSecretKeyRequiredException) error.message.orEmpty() else "导入失败，请检查链接、文件格式或解密密钥")
                }
            } finally {
                if (generation == localEditorGeneration && token == customImportToken) mutableEditor.value = mutableEditor.value.copy(isReadingFile = false)
            }
        }
    }

    fun cancelCustomImport() {
        customImportToken++
        customImportJob?.cancel()
        customImportJob = null
        customImportContent = null
        customImportProfile = null
        customImportOriginal = null
        mutableCustomImport.value = CustomImportUiState()
        mutableEditor.value = mutableEditor.value.copy(isReadingFile = false)
    }

    fun confirmCustomImport() {
        val profile = customImportProfile ?: return
        val content = customImportContent ?: return
        val original = customImportOriginal ?: return
        if (mutableEditor.value.isSaving || busy.value) return
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        val generation = localEditorGeneration
        val token = customImportToken
        mutableEditor.value = mutableEditor.value.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val updated = repository.replaceCustomProfile(profile.id, content, profile, original) {
                    !isProfileMutationBlocked(runtimeManager.state.value) && generation == localEditorGeneration && token == customImportToken
                }
                if (generation == localEditorGeneration) {
                    reflectStructuredSave(profile.id, content, updated)
                    cancelCustomImport()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == localEditorGeneration) mutableErrors.emit("替换失败，原配置已保留；请检查运行状态或重新导入")
            } finally {
                if (generation == localEditorGeneration) mutableEditor.value = mutableEditor.value.copy(isSaving = false)
            }
        }
    }

    fun beginFileReplacementPick(): Long? {
        val editor = mutableEditor.value
        val profile = editor.profile ?: return null
        if (profile.type != ProfileType.FILE || editor.isSaving || editor.isReadingFile || busy.value) return null
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return null
        }
        // 作者 long：系统选择器回调只属于发起时的配置和编辑会话，关闭重开后即使 ID 相同也不能接收旧文件。
        val token = ++nextFilePickToken
        pendingFilePick = Triple(profile.id, localEditorGeneration, token)
        return token
    }

    fun acceptFileReplacement(token: Long?, uri: String?) {
        val request = pendingFilePick ?: return
        if (token != request.third) return
        pendingFilePick = null
        if (uri == null || request.first != mutableEditor.value.profile?.id || request.second != localEditorGeneration) return
        val generation = localEditorGeneration
        mutableEditor.value = mutableEditor.value.copy(isReadingFile = true)
        replacementReadJob = viewModelScope.launch {
            try {
                val document = fileReader.read(uri)
                val bytes = document.bytes.copyOf()
                require(bytes.isNotEmpty() && bytes.size <= com.lonnnnnng.flclash.data.MAX_PROFILE_FILE_BYTES)
                val encrypted = com.lonnnnnng.flclash.data.isAgeEncryptedProfile(bytes)
                val valid = !encrypted && validateFileImport(bytes)
                if (generation == localEditorGeneration) {
                    replacementBytes = bytes
                    mutableEditor.value = mutableEditor.value.copy(isReadingFile = false,
                        replacement = ProfileFileReplacementPreview(document.name, bytes.size, encrypted, valid))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == localEditorGeneration) {
                    mutableEditor.value = mutableEditor.value.copy(isReadingFile = false)
                    mutableErrors.emit("无法读取配置文件，文件为空、超过 8 MiB 或已不可用")
                }
            }
        }
    }

    fun clearFileReplacement() {
        if (mutableEditor.value.isSaving || mutableEditor.value.isReadingFile) return
        replacementBytes = null
        mutableEditor.value = mutableEditor.value.copy(replacement = null)
    }

    fun saveFileEditor(editedContent: String?, ageSecretKey: String) {
        saveFileEditor(editedContent, ageSecretKey, null)
    }

    fun saveFileEditor(editedContent: String?, ageSecretKey: String, label: String?) {
        val editor = mutableEditor.value
        val profile = editor.profile ?: return
        if (profile.type != ProfileType.FILE || editor.isSaving || editor.isReadingFile || busy.value) return
        if (label != null && (label.isBlank() || label.trim().length > MAX_PROFILE_LABEL_LENGTH)) {
            mutableErrors.tryEmit("配置名称不能为空，最多 64 个字符")
            return
        }
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        val preview = editor.replacement
        if (preview != null && !preview.isEncrypted && !preview.isValidYaml) return
        val bytes = replacementBytes?.copyOf() ?: editedContent?.toByteArray(Charsets.UTF_8)
        val generation = localEditorGeneration
        busy.value = true
        mutableEditor.value = editor.copy(isSaving = true)
        viewModelScope.launch {
            try {
                repository.updateFileProfile(profile.id, ageSecretKey.trim(), bytes, label) { !isProfileMutationBlocked(runtimeManager.state.value) }
                if (generation == localEditorGeneration) closeEditor()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == localEditorGeneration) {
                    mutableEditor.value = mutableEditor.value.copy(isSaving = false)
                    mutableErrors.emit(error.message ?: "保存本地配置失败")
                }
            } finally {
                busy.value = false
            }
        }
    }

    fun discardEditor() {
        val editor = mutableEditor.value
        val profile = editor.profile ?: return
        if (editor.isSaving) return
        if (!editor.isNew || editor.hasPersistedChanges) {
            closeEditor()
            return
        }
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        viewModelScope.launch {
            // 作者 long：新草稿删除失败时保留编辑器和正文，避免用户以为已经放弃却在列表留下空配置。
            mutableEditor.value = editor.copy(isSaving = true)
            runCatching { repository.deleteProfile(profile.id) }
                .onSuccess { closeEditor() }
                .onFailure { error ->
                    mutableEditor.value = editor
                    mutableErrors.emit(error.message ?: "放弃新配置失败")
                }
        }
    }

    /**
     * 作者 long：结构化分区直接写入配置文件或覆盖元数据，必须同步父级编辑器，
     * 否则稍后保存原始 YAML 会用旧快照覆盖刚保存的配置。
     */
    private fun reflectStructuredSave(profileId: Long, content: String? = null, profile: Profile? = null) {
        val editor = mutableEditor.value
        if (editor.profile?.id != profileId) return
        mutableEditor.value = editor.copy(
            profile = profile ?: editor.profile,
            content = content ?: editor.content,
            hasPersistedChanges = true,
            overview = null,
        )
        refreshOverview()
    }

    private fun refreshOverview() {
        overviewJob?.cancel()
        val snapshot = mutableEditor.value
        val profile = snapshot.profile?.takeIf { it.type == ProfileType.CUSTOM } ?: return
        val generation = localEditorGeneration
        overviewJob = viewModelScope.launch {
            // 作者 long：较大的 YAML 在后台解析；离开、重开或分区保存后，旧统计不得覆盖新概览。
            val overview = withContext(overviewDispatcher) { customProfileSummary(snapshot.content, profile) }
            if (generation == localEditorGeneration && mutableEditor.value.profile == profile &&
                mutableEditor.value.content == snapshot.content) {
                mutableEditor.value = mutableEditor.value.copy(overview = overview)
            }
        }
    }

    fun finishNewDraftEditor(label: String?) {
        val editor = mutableEditor.value
        val profile = editor.profile ?: return
        if (!editor.isNew || !editor.hasPersistedChanges) {
            if (label == null) discardEditor()
            return
        }
        if (label == null) {
            closeEditor()
            return
        }
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        val normalized = label.trim()
        if (normalized.isEmpty()) {
            mutableErrors.tryEmit("配置名称不能为空")
            return
        }
        if (normalized.length > MAX_PROFILE_LABEL_LENGTH) {
            mutableErrors.tryEmit("配置名称不能超过 ${MAX_PROFILE_LABEL_LENGTH} 个字符")
            return
        }
        if (normalized == profile.label.trim()) {
            closeEditor()
            return
        }
        viewModelScope.launch {
            mutableEditor.value = editor.copy(isSaving = true)
            runCatching { repository.updateProfileLabel(profile.id, normalized) }
                .onSuccess { closeEditor() }
                .onFailure { error ->
                    mutableEditor.value = mutableEditor.value.copy(isSaving = false)
                    mutableErrors.emit(error.message ?: "重命名配置失败")
                }
        }
    }

    fun saveRemoteEditor(label: String, url: String, autoUpdate: Boolean, intervalMinutes: Long, ageSecretKey: String? = null) {
        val profile = mutableRemoteEditor.value.profile ?: return
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        val normalizedLabel = label.trim()
        if (normalizedLabel.length > MAX_PROFILE_LABEL_LENGTH) {
            mutableErrors.tryEmit("配置名称不能超过 ${MAX_PROFILE_LABEL_LENGTH} 个字符")
            return
        }
        val normalizedUrl = runCatching { normalizeRemoteProfileUrl(url) }
            .getOrElse {
                mutableErrors.tryEmit(it.message ?: "请输入有效的 HTTP/HTTPS 订阅链接")
                return
            }
        if (autoUpdate && intervalMinutes <= 0L) {
            mutableErrors.tryEmit("自动更新间隔必须大于 0 分钟")
            return
        }
        val savedIntervalMinutes = intervalMinutes.takeIf { it > 0L }
            ?: (profile.autoUpdateIntervalMillis / MILLIS_PER_MINUTE).coerceAtLeast(1L)
        viewModelScope.launch {
            busy.value = true
            mutableRemoteEditor.value = mutableRemoteEditor.value.copy(isSaving = true)
            try {
                val updated = repository.updateRemoteProfileSettings(
                    id = profile.id,
                    label = normalizedLabel,
                    url = normalizedUrl,
                    autoUpdate = autoUpdate,
                    intervalMillis = savedIntervalMinutes.coerceAtMost(MAX_AUTO_UPDATE_INTERVAL_MINUTES) * MILLIS_PER_MINUTE,
                    ageSecretKey = ageSecretKey?.trim() ?: profile.ageSecretKey,
                )
                closeEditor()
                if (updated.url != profile.url || updated.ageSecretKey != profile.ageSecretKey) {
                    // 作者 long：密钥改变后也要重新解密验证；名称和更新周期不改变下载内容，不产生网络请求。
                    runCatching { repository.updateRemoteProfile(profile.id) }
                        .onFailure { error -> mutableErrors.emit(error.message ?: "订阅更新失败") }
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                mutableRemoteEditor.value = mutableRemoteEditor.value.copy(isSaving = false)
                mutableErrors.emit(error.message ?: "保存订阅设置失败")
            } finally {
                busy.value = false
            }
        }
    }

    fun openRulesEditor(profile: Profile) {
        if (mutableRulesEditor.value.isSaving) return
        if (profile.type != ProfileType.CUSTOM) {
            mutableErrors.tryEmit("高级规则编辑仅支持自定义配置")
            return
        }
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再编辑规则")
            return
        }
        val generation = ++rulesGeneration
        mutableRulesEditor.value = ProfileRulesEditorUiState(profile = profile, isLoading = true)
        viewModelScope.launch {
            runCatching {
                val content = repository.readLocalProfileContent(profile.id)
                withContext(overviewDispatcher) { Triple(content, ProfileRulesEditor.extractRules(content), CustomRuleCatalog.fromSource(content)) }
            }.onSuccess { (content, rules, catalog) ->
                if (generation == rulesGeneration) mutableRulesEditor.value = ProfileRulesEditorUiState(
                    profile = profile,
                    sourceContent = content,
                    rules = rules,
                    catalog = catalog,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (generation == rulesGeneration) {
                    mutableRulesEditor.value = ProfileRulesEditorUiState()
                    mutableErrors.emit("无法读取规则配置，请检查 YAML 格式")
                }
            }
        }
    }

    fun closeRulesEditor() {
        if (mutableRulesEditor.value.isSaving) return
        rulesGeneration++
        mutableRulesEditor.value = ProfileRulesEditorUiState()
    }

    fun saveRules(rules: String) {
        val state = mutableRulesEditor.value
        val profile = state.profile ?: return
        if (state.isLoading || state.isSaving) return
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            val message = "核心运行中，请先停止核心后再保存规则"
            mutableRulesEditor.value = state.copy(error = message)
            mutableErrors.tryEmit(message)
            return
        }
        val generation = rulesGeneration
        mutableRulesEditor.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            runCatching {
                val updatedContent = withContext(overviewDispatcher) { ProfileRulesEditor.replaceRules(state.sourceContent, rules) }
                val updatedProfile = repository.saveCustomRules(profile.id, rules, state.sourceContent) {
                    generation == rulesGeneration && !isProfileMutationBlocked(runtimeManager.state.value)
                }
                if (generation == rulesGeneration) {
                    reflectStructuredSave(profile.id, updatedContent, updatedProfile)
                    // 作者 long：与 Flutter 一样逐操作保存并留在列表；更新正文快照供下一次保存检测过期写入。
                    mutableRulesEditor.value = state.copy(profile = updatedProfile, sourceContent = updatedContent,
                        rules = ProfileRulesEditor.extractRules(updatedContent), isSaving = false, error = null)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (generation == rulesGeneration) {
                    mutableRulesEditor.value = state.copy(isSaving = false, error = error.message ?: "保存规则失败，原配置已保留")
                }
            }
        }
    }

    fun openProxiesEditor(profile: Profile) {
        if (profile.type != ProfileType.CUSTOM) {
            mutableErrors.tryEmit("高级代理编辑仅支持自定义配置")
            return
        }
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再编辑代理")
            return
        }
        viewModelScope.launch {
            mutableProxiesEditor.value = ProfileProxiesEditorUiState(profile = profile, isLoading = true)
            runCatching {
                val content = repository.readLocalProfileContent(profile.id)
                content to ProfileProxiesEditor.extractProxies(content)
            }.onSuccess { (content, proxies) ->
                mutableProxiesEditor.value = ProfileProxiesEditorUiState(
                    profile = profile,
                    sourceContent = content,
                    proxies = proxies,
                )
            }.onFailure { error ->
                mutableProxiesEditor.value = ProfileProxiesEditorUiState()
                mutableErrors.emit(error.message ?: "无法读取代理配置")
            }
        }
    }

    fun closeProxiesEditor() {
        mutableProxiesEditor.value = ProfileProxiesEditorUiState()
    }

    fun saveProxies(proxies: String) {
        val state = mutableProxiesEditor.value
        val profile = state.profile ?: return
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再保存代理")
            return
        }
        viewModelScope.launch {
            mutableProxiesEditor.value = state.copy(isSaving = true)
            runCatching {
                val updatedContent = ProfileProxiesEditor.replaceProxies(state.sourceContent, proxies)
                val updatedProfile = repository.updateLocalProfileContent(profile.id, updatedContent)
                reflectStructuredSave(profile.id, updatedContent, updatedProfile)
            }.onSuccess {
                closeProxiesEditor()
            }.onFailure { error ->
                mutableProxiesEditor.value = mutableProxiesEditor.value.copy(isSaving = false, proxies = proxies)
                mutableErrors.emit(error.message ?: "保存代理失败")
            }
        }
    }

    fun openProxyGroupsEditor(profile: Profile) = openProxyGroupsEditorPage(profile, null)

    fun openSingleProxyGroupEditor(profile: Profile, index: Int) = openProxyGroupsEditorPage(profile, index)

    private fun openProxyGroupsEditorPage(profile: Profile, index: Int?) {
        if (profile.type != ProfileType.CUSTOM) {
            mutableErrors.tryEmit("高级代理组编辑仅支持自定义配置")
            return
        }
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再编辑代理组")
            return
        }
        if (mutableProxyGroupsEditor.value.isSaving) return
        val generation = ++proxyGroupsGeneration
        mutableProxyGroupsEditor.value = ProfileProxyGroupsEditorUiState(profile = profile, isLoading = true, initialGroupIndex = index)
        viewModelScope.launch {
            runCatching {
                val content = repository.readLocalProfileContent(profile.id)
                content to ProfileProxyGroupsEditor.extractGroups(content)
            }.onSuccess { (content, groups) ->
                if (generation == proxyGroupsGeneration) mutableProxyGroupsEditor.value = ProfileProxyGroupsEditorUiState(
                    profile = profile, sourceContent = content, groups = groups, initialGroupIndex = index,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (generation == proxyGroupsGeneration) {
                    mutableProxyGroupsEditor.value = ProfileProxyGroupsEditorUiState()
                    mutableErrors.emit("无法读取代理组配置")
                }
            }
        }
    }

    fun closeProxyGroupsEditor() {
        proxyGroupsGeneration++
        mutableProxyGroupsEditor.value = ProfileProxyGroupsEditorUiState()
    }

    suspend fun readGroupIcon(uri: String): String = withContext(overviewDispatcher) {
        com.lonnnnnng.flclash.data.ProfileGroupIconData.encode(fileReader.read(uri).bytes)
    }

    suspend fun validateGroupFilters(filters: List<String>): List<String> = withContext(overviewDispatcher) {
        if (filters.all(String::isBlank)) List(filters.size) { "" } else groupFilterValidator.validate(filters)
    }

    fun saveProxyGroupsDraft(save: ProxyGroupsSave) {
        val state = mutableProxyGroupsEditor.value
        val profile = state.profile ?: return
        if (state.isLoading || state.isSaving) return
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再保存代理组")
            return
        }
        val generation = proxyGroupsGeneration
        mutableProxyGroupsEditor.value = state.copy(isSaving = true)
        viewModelScope.launch {
            runCatching {
                // 作者 long：快捷 YAML 和列表保存也必须经过核心校验，不能仅依赖表单的防抖预检。
                val filters = save.groups.flatMap { listOf(it.text("filter"), it.text("exclude-filter")) }
                require(validateGroupFilters(filters).all(String::isEmpty)) { "过滤表达式无效，请打开对应策略组修复" }
                val content = withContext(overviewDispatcher) { ProxyGroupDrafts.apply(state.sourceContent, save) }
                val updated = repository.saveCustomProxyGroups(profile.id, save, state.sourceContent) {
                    generation == proxyGroupsGeneration && !isProfileMutationBlocked(runtimeManager.state.value)
                }
                if (generation == proxyGroupsGeneration) {
                    reflectStructuredSave(profile.id, content, updated)
                    closeProxyGroupsEditor()
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (generation == proxyGroupsGeneration) {
                    mutableProxyGroupsEditor.value = state.copy(isSaving = false)
                    mutableErrors.emit(error.message ?: "保存代理组失败")
                }
            }
        }
    }

    fun saveProxyGroups(groups: String) {
        val state = mutableProxyGroupsEditor.value
        val profile = state.profile ?: return
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再保存代理组")
            return
        }
        viewModelScope.launch {
            mutableProxyGroupsEditor.value = state.copy(isSaving = true)
            runCatching {
                val updatedContent = ProfileProxyGroupsEditor.replaceGroups(state.sourceContent, groups)
                val updatedProfile = repository.updateLocalProfileContent(profile.id, updatedContent)
                reflectStructuredSave(profile.id, updatedContent, updatedProfile)
            }.onSuccess {
                closeProxyGroupsEditor()
            }.onFailure { error ->
                mutableProxyGroupsEditor.value = mutableProxyGroupsEditor.value.copy(isSaving = false, groups = groups)
                mutableErrors.emit(error.message ?: "保存代理组失败")
            }
        }
    }

    fun openProvidersEditor(profile: Profile) {
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再编辑 Provider")
            return
        }
        viewModelScope.launch {
            mutableProvidersEditor.value = ProfileProvidersEditorUiState(profile = profile, isLoading = true)
            runCatching {
                val content = repository.readProfileSourceContent(profile.id)
                content to ProfileProvidersEditor.extract(content)
            }.onSuccess { (content, sections) ->
                mutableProvidersEditor.value = ProfileProvidersEditorUiState(
                    profile = profile,
                    sourceContent = content,
                    proxyProviders = sections.proxyProviders,
                    ruleProviders = sections.ruleProviders,
                    proxyProviderOverrides = ProfileProviderOverrides
                        .fromJsonString(profile.overridesJson)
                        .proxyProviders,
                )
            }.onFailure { error ->
                mutableProvidersEditor.value = ProfileProvidersEditorUiState()
                mutableErrors.emit(error.message ?: "无法读取 Provider 配置")
            }
        }
    }

    fun closeProvidersEditor() {
        mutableProvidersEditor.value = ProfileProvidersEditorUiState()
    }

    fun saveProviders(proxyProviders: String, ruleProviders: String) {
        saveProviders(
            proxyProviders,
            ruleProviders,
            mutableProvidersEditor.value.proxyProviderOverrides,
        )
    }

    fun saveProviders(
        proxyProviders: String,
        ruleProviders: String,
        proxyProviderOverrides: Map<String, ProxyProviderOverride>,
    ) {
        val state = mutableProvidersEditor.value
        val profile = state.profile ?: return
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再保存 Provider")
            return
        }
        viewModelScope.launch {
            mutableProvidersEditor.value = state.copy(
                isSaving = true,
                proxyProviders = proxyProviders,
                ruleProviders = ruleProviders,
                proxyProviderOverrides = proxyProviderOverrides,
            )
            runCatching {
                // 作者 long：订阅/文件的扩展只保存覆盖元数据；只有自定义配置允许修改 Provider 定义正文。
                if (profile.type == ProfileType.CUSTOM) {
                    val updatedContent = ProfileProvidersEditor.replace(
                        state.sourceContent, ProfileProvidersEditor.Sections(proxyProviders, ruleProviders),
                    )
                    val contentProfile = repository.updateLocalProfileContent(profile.id, updatedContent)
                    reflectStructuredSave(profile.id, updatedContent, contentProfile)
                }
                val latest = repository.profiles.value.firstOrNull { it.id == profile.id } ?: error("配置不存在")
                val current = ProfileProviderOverrides.fromJsonString(latest.overridesJson)
                val updatedProfile = repository.updateProfileOverrides(
                    profile.id,
                    current.copy(
                        proxyProviders = proxyProviderOverrides,
                    ).toJsonString(),
                )
                reflectStructuredSave(profile.id, profile = updatedProfile)
            }.onSuccess {
                closeProvidersEditor()
            }.onFailure { error ->
                mutableProvidersEditor.value = mutableProvidersEditor.value.copy(isSaving = false)
                mutableErrors.emit(error.message ?: "保存 Provider 失败")
            }
        }
    }

    fun openDnsEditor(profile: Profile) {
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再编辑 DNS")
            return
        }
        mutableDnsEditor.value = ProfileDnsEditorUiState(
            profile = profile,
            overrides = ProfileProviderOverrides.fromJsonString(profile.overridesJson).dns,
        )
    }

    fun closeDnsEditor() {
        mutableDnsEditor.value = ProfileDnsEditorUiState()
    }

    fun saveDns(overrides: ProfileDnsOverrides) {
        val state = mutableDnsEditor.value
        val profile = state.profile ?: return
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再保存 DNS")
            return
        }
        viewModelScope.launch {
            mutableDnsEditor.value = state.copy(overrides = overrides, isSaving = true)
            runCatching {
                // 作者 long：扩展页可能先保存其他分区，合并最新元数据避免用打开时快照覆盖刚保存的 Provider。
                val latest = repository.profiles.value.firstOrNull { it.id == profile.id } ?: error("配置不存在")
                val current = ProfileProviderOverrides.fromJsonString(latest.overridesJson)
                val updatedProfile = repository.updateProfileOverrides(
                    profile.id,
                    current.copy(dns = overrides).toJsonString(),
                )
                reflectStructuredSave(profile.id, profile = updatedProfile)
            }.onSuccess {
                closeDnsEditor()
            }.onFailure { error ->
                mutableDnsEditor.value = mutableDnsEditor.value.copy(isSaving = false, overrides = overrides)
                mutableErrors.emit(error.message ?: "保存 DNS 失败")
            }
        }
    }

    fun openDialersEditor(profile: Profile) {
        if (profile.type != ProfileType.CUSTOM) {
            mutableErrors.tryEmit("Dialers 编辑仅支持自定义配置")
            return
        }
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再编辑 Dialers")
            return
        }
        viewModelScope.launch {
            mutableDialersEditor.value = ProfileDialersEditorUiState(profile = profile, isLoading = true)
            runCatching {
                val content = repository.readLocalProfileContent(profile.id)
                val stored = ProfileProviderOverrides.fromJsonString(profile.overridesJson).dialers
                content to ProfileDialerEditor.renderDialers(
                    if (stored != null) stored else ProfileDialerEditor.extractDialers(content),
                )
            }.onSuccess { (content, dialers) ->
                mutableDialersEditor.value = ProfileDialersEditorUiState(
                    profile = profile,
                    sourceContent = content,
                    dialers = dialers,
                )
            }.onFailure { error ->
                mutableDialersEditor.value = ProfileDialersEditorUiState()
                mutableErrors.emit(error.message ?: "无法读取 Dialers 配置")
            }
        }
    }

    fun closeDialersEditor() {
        mutableDialersEditor.value = ProfileDialersEditorUiState()
    }

    fun openMoreEditor(profile: Profile) {
        if (profile.type != ProfileType.CUSTOM) return
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        val overrides = ProfileProviderOverrides.fromJsonString(profile.overridesJson)
        mutableMoreEditor.value = ProfileMoreEditorUiState(profile, overrides.ntp, overrides.sniffer)
    }

    fun closeMoreEditor() {
        if (!mutableMoreEditor.value.isSaving) mutableMoreEditor.value = ProfileMoreEditorUiState()
    }

    fun saveMoreEditor(ntp: ProfileNtpOverrides, sniffer: ProfileSnifferOverrides) {
        val state = mutableMoreEditor.value
        val profile = state.profile ?: return
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再保存 NTP 或 Sniffer")
            return
        }
        if (state.isSaving || busy.value) return
        try {
            validateMoreFields(ntp.toJson(), true)
            validateMoreFields(sniffer.toJson(), false)
        } catch (error: IllegalArgumentException) {
            mutableErrors.tryEmit(error.message ?: "NTP 或 Sniffer 字段无效")
            return
        }
        val generation = localEditorGeneration
        viewModelScope.launch {
            mutableMoreEditor.value = state.copy(ntp = ntp, sniffer = sniffer, isSaving = true)
            try {
                // 作者 long：分区保存读取最新覆盖，避免连续修改更多、DNS 或 Provider 时覆盖另一分区。
                val latest = repository.profiles.value.firstOrNull { it.id == profile.id } ?: error("配置不存在")
                val current = ProfileProviderOverrides.fromJsonString(latest.overridesJson)
                val updated = repository.updateProfileOverrides(profile.id, current.copy(ntp = ntp, sniffer = sniffer).toJsonString())
                if (generation == localEditorGeneration) {
                    reflectStructuredSave(profile.id, profile = updated)
                    mutableMoreEditor.value = ProfileMoreEditorUiState()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == localEditorGeneration) {
                    mutableMoreEditor.value = mutableMoreEditor.value.copy(isSaving = false)
                    mutableErrors.emit(error.message ?: "保存 NTP 或 Sniffer 失败")
                }
            }
        }
    }

    fun saveDialers(dialers: String) {
        val state = mutableDialersEditor.value
        val profile = state.profile ?: return
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再保存 Dialers")
            return
        }
        viewModelScope.launch {
            mutableDialersEditor.value = state.copy(dialers = dialers, isSaving = true)
            runCatching {
                val latest = repository.profiles.value.firstOrNull { it.id == profile.id } ?: error("配置不存在")
                val current = ProfileProviderOverrides.fromJsonString(latest.overridesJson)
                val mapping = ProfileDialerEditor.parseDialers(dialers)
                val updatedProfile = repository.updateProfileOverrides(
                    profile.id,
                    current.copy(dialers = mapping).toJsonString(),
                )
                reflectStructuredSave(profile.id, profile = updatedProfile)
            }.onSuccess {
                closeDialersEditor()
            }.onFailure { error ->
                mutableDialersEditor.value = mutableDialersEditor.value.copy(isSaving = false)
                mutableErrors.emit(error.message ?: "保存 Dialers 失败")
            }
        }
    }

    fun saveEditor(content: String) {
        val profile = mutableEditor.value.profile ?: return
        if (profile.type == ProfileType.FILE) {
            saveFileEditor(content, profile.ageSecretKey)
            return
        }
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再保存配置")
            return
        }
        viewModelScope.launch {
            mutableEditor.value = mutableEditor.value.copy(isSaving = true)
            runCatching { repository.updateLocalProfileContent(profile.id, content) }
                .onSuccess { closeEditor() }
                .onFailure { error ->
                    mutableEditor.value = mutableEditor.value.copy(isSaving = false)
                    mutableErrors.emit(error.message ?: "保存配置失败")
                }
        }
    }

    fun update(profile: Profile) {
        if (profile.type != ProfileType.URL) return
        launchAction { repository.updateRemoteProfile(profile.id) }
    }

    fun setAutoUpdate(profile: Profile, enabled: Boolean) {
        if (profile.type != ProfileType.URL) return
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        // 作者 long：开关只修改自动同步元数据，不立即下载；后台 Worker 会按保存的间隔筛选到期订阅。
        launchAction {
            repository.updateProfileAutoUpdate(
                id = profile.id,
                enabled = enabled,
                intervalMillis = profile.autoUpdateIntervalMillis,
            )
        }
    }

    fun rename(profile: Profile, label: String) {
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        val normalized = label.trim()
        if (normalized.isEmpty()) {
            mutableErrors.tryEmit("配置名称不能为空")
            return
        }
        // 作者 long：名称编辑只更新 Profile 元数据，核心运行期间拒绝写入，保证卡片标题与当前运行配置一致。
        saveEditorMetadata(profile.id) { repository.updateProfileLabel(profile.id, normalized) }
    }

    fun refreshSelection() {
        viewModelScope.launch { repository.refreshCurrentProfile() }
    }

    fun bindScript(profile: Profile, scriptId: Long?) {
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        // 作者 long：绑定只保存元数据；同步父概览以保留新草稿，实际脚本在核心配置应用时才执行。
        saveEditorMetadata(profile.id) { repository.updateProfileScript(profile.id, scriptId) }
    }

    private fun saveEditorMetadata(profileId: Long, save: suspend () -> Profile) {
        if (busy.value || mutableEditor.value.isSaving) return
        val generation = localEditorGeneration
        if (mutableEditor.value.profile?.id == profileId) {
            // 作者 long：元数据写入尚未完成时不能退出并删除新草稿；失败则留在概览供用户重试。
            mutableEditor.value = mutableEditor.value.copy(isSaving = true)
        }
        launchAction {
            try {
                val updated = save()
                if (generation == localEditorGeneration) reflectStructuredSave(profileId, profile = updated)
            } finally {
                if (generation == localEditorGeneration && mutableEditor.value.profile?.id == profileId) {
                    mutableEditor.value = mutableEditor.value.copy(isSaving = false)
                }
            }
        }
    }

    fun reorder(ids: List<Long>) {
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        val currentIds = repository.profiles.value.map(Profile::id)
        val requested = ids
            .filter { it in currentIds }
            .distinct()
        val orderedIds = requested + currentIds.filterNot { it in requested }
        if (orderedIds.isEmpty()) return
        // 作者 long：把弹窗中的局部顺序补齐为当前最新 Profile 列表，避免排序期间新增订阅后丢失新条目。
        launchAction { repository.reorder(orderedIds) }
    }

    fun delete(profile: Profile) {
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        launchAction { repository.deleteProfile(profile.id) }
    }

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            runCatching { action() }
                .onFailure { error -> mutableErrors.emit(error.message ?: "操作失败") }
            busy.value = false
        }
    }

    private companion object {
        const val MAX_PROFILE_LABEL_LENGTH = 64
        const val MAX_AUTO_UPDATE_INTERVAL_MINUTES = Long.MAX_VALUE / 60_000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

private fun normalizeRemoteProfileUrl(value: String): String =
    com.lonnnnnng.flclash.data.normalizeSubscriptionUrl(value)
