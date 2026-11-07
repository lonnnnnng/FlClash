package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.ProfileConfigValidator
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.LocalProxies
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
import com.lonnnnnng.flclash.data.ProfileMorePatch
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.data.ProfileDocumentSnapshot
import com.lonnnnnng.flclash.data.ProfileDocumentSettings
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProfilesUiState(
    val profiles: List<Profile> = emptyList(),
    val currentProfileId: Long? = null,
    val isBusy: Boolean = false,
    val resourceUsers: Map<Long, List<String>> = emptyMap(),
    val sync: ProfileSyncUiState = ProfileSyncUiState(),
    val orderRevision: Long = 0,
    val orderError: String? = null,
    val profilesLoaded: Boolean = true,
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
    val document: ProfileDocumentSnapshot? = null,
    val draftContent: String? = null,
    val error: String? = null,
    val draftLabel: String? = null,
    val draftAgeKey: String? = null,
    val isPickingFile: Boolean = false,
) {
    override fun toString() = "ProfileEditorUiState(id=${profile?.id}, saving=$isSaving)"
}

data class ProfileFileReplacementPreview(val name: String, val sizeBytes: Int, val isEncrypted: Boolean, val isValidYaml: Boolean)

data class RemoteProfileEditorUiState(
    val profile: Profile? = null,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val isReadingFile: Boolean = false,
    val document: ProfileDocumentSnapshot? = null,
    val draft: RemoteProfileDraft = RemoteProfileDraft.from(profile),
    val isPickingFile: Boolean = false,
    val draftContent: String? = null,
    val replacement: ProfileFileReplacementPreview? = null,
    val error: String? = null,
) {
    override fun toString() = "RemoteProfileEditorUiState(id=${profile?.id}, saving=$isSaving)"
}

data class RemoteProfileDraft(val label: String, val url: String, val ageKey: String, val autoUpdate: Boolean, val intervalMinutes: String) {
    override fun toString() = "RemoteProfileDraft([REDACTED])"
    companion object {
        fun from(profile: Profile?) = RemoteProfileDraft(profile?.label.orEmpty(), profile?.url.orEmpty(), profile?.ageSecretKey.orEmpty(),
            profile?.autoUpdate ?: false, ((profile?.autoUpdateIntervalMillis ?: Profile.DEFAULT_UPDATE_INTERVAL_MILLIS) / 60_000L).coerceAtLeast(1).toString())
    }
}

data class ProfileRulesEditorUiState(
    val profile: Profile? = null,
    val sourceContent: String = "",
    val rules: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val catalog: CustomRuleCatalog = CustomRuleCatalog(),
    val error: String? = null,
    val session: Long = 0,
    val uiDraft: EditorSaveableState? = null,
    val pendingContent: String? = null,
    val interruptedRules: String? = null,
) { override fun toString() = "ProfileRulesEditorUiState(id=${profile?.id}, saving=$isSaving)" }

data class ProfileProxiesEditorUiState(
    val profile: Profile? = null,
    val sourceContent: String = "",
    val proxies: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val session: Long = 0,
    val uiDraft: EditorSaveableState? = null,
    val pendingContent: String? = null,
) { override fun toString() = "ProfileProxiesEditorUiState(id=${profile?.id}, saving=$isSaving)" }

data class ProfileProxyGroupsEditorUiState(
    val profile: Profile? = null,
    val sourceContent: String = "",
    val groups: String = "",
    val initialGroupIndex: Int? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val session: Long = 0,
    val uiDraft: EditorSaveableState? = null,
    val pendingContent: String? = null,
) { override fun toString() = "ProfileProxyGroupsEditorUiState(id=${profile?.id}, saving=$isSaving)" }

data class ProfileProvidersEditorUiState(
    val profile: Profile? = null,
    val sourceContent: String = "",
    val proxyProviders: String = "",
    val ruleProviders: String = "",
    val proxyProviderOverrides: Map<String, ProxyProviderOverride> = emptyMap(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val revision: Long = 0,
    val error: String? = null,
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
    val error: String? = null,
)

data class ProfileMoreEditorUiState(
    val profile: Profile? = null,
    val ntp: ProfileNtpOverrides = ProfileNtpOverrides(),
    val sniffer: ProfileSnifferOverrides = ProfileSnifferOverrides(),
    val isSaving: Boolean = false,
    val revision: Long = 0,
    val error: String? = null,
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

internal fun isProfileTransitioning(status: CoreRuntimeStatus): Boolean =
    status == CoreRuntimeStatus.STARTING || status == CoreRuntimeStatus.STOPPING

@HiltViewModel
class ProfilesViewModel internal constructor(
    private val repository: ProfileStore,
    private val runtimeManager: CoreRuntimeStatusProvider,
    private val configValidator: ProfileConfigValidator = YamlProfileConfigValidator(),
    private val fileReader: com.lonnnnnng.flclash.data.ProfileFileDocumentReader = com.lonnnnnng.flclash.data.ProfileFileDocumentReader { error("文件读取器不可用") },
    private val overviewDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    private val groupFilterValidator: ProxyGroupFilterValidator = ProxyGroupFilterValidator { error("过滤表达式校验不可用") },
    private val draftStore: com.lonnnnnng.flclash.data.ProfileDraftStore = com.lonnnnnng.flclash.data.NoopProfileDraftStore,
    managementStore: com.lonnnnnng.flclash.data.ProfileDraftStore = com.lonnnnnng.flclash.data.NoopProfileDraftStore,
    extensionStore: com.lonnnnnng.flclash.data.ProfileDraftStore = com.lonnnnnng.flclash.data.NoopProfileDraftStore,
) : ViewModel() {
    @Inject constructor(
        repository: ProfileStore,
        runtimeManager: CoreRuntimeStatusProvider,
        configValidator: ProfileConfigValidator = YamlProfileConfigValidator(),
        fileReader: com.lonnnnnng.flclash.data.ProfileFileDocumentReader = com.lonnnnnng.flclash.data.ProfileFileDocumentReader { error("文件读取器不可用") },
        groupFilterValidator: ProxyGroupFilterValidator = ProxyGroupFilterValidator { error("过滤表达式校验不可用") },
        draftStore: com.lonnnnnng.flclash.data.ProfileDraftStore = com.lonnnnnng.flclash.data.NoopProfileDraftStore,
        @javax.inject.Named("profile-management") managementStore: com.lonnnnnng.flclash.data.ProfileDraftStore = com.lonnnnnng.flclash.data.NoopProfileDraftStore,
        @javax.inject.Named("profile-extension") extensionStore: com.lonnnnnng.flclash.data.ProfileDraftStore = com.lonnnnnng.flclash.data.NoopProfileDraftStore,
    ) : this(repository, runtimeManager, configValidator, fileReader, Dispatchers.Default, groupFilterValidator, draftStore, managementStore, extensionStore)
    internal val management = ProfileManagementController(repository, runtimeManager, managementStore, viewModelScope)
    internal val globalRules = GlobalRulesController(repository, runtimeManager, viewModelScope)
    internal val extension = ProfileExtensionController(repository, runtimeManager, viewModelScope, store = extensionStore)
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
    private val mutableSync = MutableStateFlow(ProfileSyncUiState())
    private val orderResult = MutableStateFlow(0L to (null as String?))
    val syncState: StateFlow<ProfileSyncUiState> = mutableSync
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val mutableEditor = MutableStateFlow(ProfileEditorUiState())
    private val mutableRemoteEditor = MutableStateFlow(RemoteProfileEditorUiState())
    private val mutableDocumentEditor = MutableStateFlow(ProfileDocumentEditorState())
    val documentEditorState: StateFlow<ProfileDocumentEditorState> = mutableDocumentEditor
    private var remoteReplacementBytes: ByteArray? = null
    private var pendingRemotePick: Triple<Long, Long, Long>? = null
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
    private var proxiesGeneration = 0L
    private var proxiesClosing = false
    private var rulesClosing = false
    private var groupsClosing = false
    private var dialersGeneration = 0L
    private var moreGeneration = 0L
    private var providersGeneration = 0L
    private var pendingFilePick: Triple<Long, Long, Long>? = null
    private var nextFilePickToken = 0L
    private var replacementBytes: ByteArray? = null
    private var replacementReadJob: kotlinx.coroutines.Job? = null
    private var overviewJob: kotlinx.coroutines.Job? = null
    val ageKeyRequired: StateFlow<Boolean> = mutableAgeKeyRequired

    val state: StateFlow<ProfilesUiState> = flow {
        // 作者 long：先等仓库首读与选择校正完成再订阅快照，避免独立合流把就绪标记配到旧空列表并误关恢复中的详情。
        repository.profilesLoaded.first { it }
        emitAll(combine(
            repository.profiles,
            repository.currentProfileId,
            busy, mutableSync, orderResult,
        ) { profiles, currentId, isBusy, sync, order ->
            ProfilesUiState(profiles, currentId, isBusy, repository.proxyResourceUsage(), sync, order.first, order.second)
        })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUiState(profilesLoaded = false))

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

    private val draftRevision = MutableStateFlow(0L)
    private val draftGson = com.google.gson.Gson()
    private val draftWriteLock = kotlinx.coroutines.sync.Mutex()
    private var draftEpoch = 0L
    private var draftClosing = false
    private val draftRestored = kotlinx.coroutines.CompletableDeferred<Unit>().apply {
        if (draftStore === com.lonnnnnng.flclash.data.NoopProfileDraftStore) complete(Unit)
    }
    private var restoredFilePick: Long? = null
    private var restoredRemotePick: Long? = null
    private var draftRecoveryFailed = false
    private var draftRecoveryRetrying = false

    // 作者 long：冷恢复验收必须等完整读取结束，初始空状态不能冒充已清除草稿；此入口只等待、不修改状态。
    internal suspend fun awaitEditorDraftRestoration() {
        draftRestored.await()
        check(!draftRecoveryFailed) { "编辑草稿恢复失败" }
    }

    init {
        if (draftStore !== com.lonnnnnng.flclash.data.NoopProfileDraftStore) viewModelScope.launch {
            try {
                restoreEditorDraft()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                draftRecoveryFailed = true
                mutableErrors.emit("无法恢复上次编辑草稿，密文已保留；请重新打开编辑器重试")
            }
            finally { draftRestored.complete(Unit) }
            combine(mutableEditor, mutableRemoteEditor, mutableDocumentEditor, draftRevision,
                combine(mutableRulesEditor, mutableProxyGroupsEditor, mutableProxiesEditor) { _, _, _ -> Unit }) { _, _, _, _, _ ->
                draftEpoch to currentDraftSnapshot()
            }.collect { (epoch, _) ->
                if (draftRecoveryFailed) return@collect
                try {
                    draftWriteLock.withLock {
                        if (!draftRecoveryFailed && epoch == draftEpoch) {
                            // 作者 long：等待写锁期间可能已登记保存意图；拿锁后重取状态，不能用排队旧快照擦掉提交前的恢复记录。
                            val latest = currentDraftSnapshot()
                            val bytes = latest?.let { withContext(overviewDispatcher) { draftGson.toJson(it).toByteArray(Charsets.UTF_8) } }
                            if (epoch == draftEpoch) {
                                // 作者 long：关闭的清除也可能由自动写入器先完成；验收必须停在实际清除边界，不能只盯显式关闭协程。
                                if (draftClosing && bytes == null) EditorCommitProbe.checkpoint(EditorCommitWindow.PROFILE_CLOSE_BEFORE_CLEAR)
                                draftStore.write(bytes)
                            }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableErrors.emit("草稿暂存失败，请保持编辑页面并重试保存") }
            }
        }
    }

    private suspend fun restoreEditorDraft(allowPartial: Boolean = false) {
        val generation = localEditorGeneration
        val epoch = draftEpoch
        val rulesAtRead = rulesGeneration; val groupsAtRead = proxyGroupsGeneration; val proxiesAtRead = proxiesGeneration
        fun canRestore() = generation == localEditorGeneration && epoch == draftEpoch && !draftClosing &&
            rulesAtRead == rulesGeneration && groupsAtRead == proxyGroupsGeneration && proxiesAtRead == proxiesGeneration
        val saved = draftStore.read()?.let { withContext(overviewDispatcher) { draftGson.fromJson(it.toString(Charsets.UTF_8), ProfileEditorDraftSnapshot::class.java) } }
        if (saved != null && canRestore() &&
            (allowPartial || mutableEditor.value.profile == null && mutableRemoteEditor.value.profile == null)) {
            require(saved.version in 1..3)
            val id = saved.editor.profile?.id ?: saved.remote.profile?.id ?: saved.rules?.profile?.id ?: saved.groups?.profile?.id ?: saved.proxies?.profile?.id
            if (id != null && repository.extensionProfile(id) != null) {
                // 作者 long：元数据查询也可能切到IO线程；返回后再次复核首次读取的会话，放弃过的父页不能被迟到查询复活。
                if (!canRestore()) return
                if (saved.editor.isLoading || saved.remote.isLoading) {
                    openEditorNow(requireNotNull(saved.editor.profile ?: saved.remote.profile))
                } else {
                    mutableEditor.value = saved.editor.copy(isLoading = false, isSaving = false, isReadingFile = false, overview = null, isPickingFile = saved.filePick != null)
                    mutableRemoteEditor.value = saved.remote.copy(isLoading = false, isSaving = false, isReadingFile = false, isPickingFile = saved.remotePick != null)
                    mutableDocumentEditor.value = saved.document.copy(saving = false)
                    replacementBytes = saved.fileBytes; remoteReplacementBytes = saved.remoteBytes
                    pendingFilePick = saved.filePick?.let { Triple(it.first, generation, it.third) }
                    pendingRemotePick = saved.remotePick?.let { Triple(it.first, generation, it.third) }
                    restoredFilePick = pendingFilePick?.third; restoredRemotePick = pendingRemotePick?.third
                    nextFilePickToken = saved.nextToken
                    restoreStructuredDrafts(saved, id, generation, epoch)
                    refreshOverview()
                }
            }
        }
    }

    private fun deferUntilDraftRestored(open: () -> Unit): Boolean {
        if (draftRestored.isCompleted) return false
        val epoch = draftEpoch
        viewModelScope.launch {
            draftRestored.await()
            // 作者 long：冷启动先还原原密文；早到的打开动作仅在没有旧会话且期间未放弃时执行，避免新空表单覆盖原草稿。
            if (epoch == draftEpoch && !draftClosing && mutableEditor.value.profile == null && mutableRemoteEditor.value.profile == null &&
                mutableRulesEditor.value.profile == null && mutableProxyGroupsEditor.value.profile == null && mutableProxiesEditor.value.profile == null) open()
        }
        return true
    }

    private fun retryFailedDraftRecovery(): Boolean {
        if (!draftRecoveryFailed) return false
        if (!draftRecoveryRetrying) {
            draftRecoveryRetrying = true
            viewModelScope.launch {
                try {
                    // 作者 long：恢复失败可能已发布父页但尚未发布凭据子页；整份原密文重读成功前不能用半份内存覆盖它。
                    restoreEditorDraft(allowPartial = true)
                    draftRecoveryFailed = false
                    draftRevision.value++
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableErrors.emit("无法恢复上次编辑草稿，密文已保留；请重新打开编辑器重试") }
                finally { draftRecoveryRetrying = false }
            }
        }
        return true
    }

    private fun currentDraftSnapshot(): ProfileEditorDraftSnapshot? {
        val editor = mutableEditor.value; val remote = mutableRemoteEditor.value
        val rules = mutableRulesEditor.value.takeUnless { rulesClosing || it.profile == null || it.isLoading }
        val groups = mutableProxyGroupsEditor.value.takeUnless { groupsClosing || it.profile == null || it.isLoading }
        val proxies = mutableProxiesEditor.value.takeUnless { proxiesClosing || it.profile == null || it.isLoading }
        if (draftClosing || editor.isLoading || remote.isLoading || editor.profile == null && remote.profile == null && rules == null && groups == null && proxies == null) return null
        return ProfileEditorDraftSnapshot(editor = editor.copy(overview = null), remote = remote, document = mutableDocumentEditor.value,
            fileBytes = replacementBytes?.copyOf(), remoteBytes = remoteReplacementBytes?.copyOf(),
            filePick = pendingFilePick, remotePick = pendingRemotePick, nextToken = nextFilePickToken, rules = rules, groups = groups, proxies = proxies)
    }

    private suspend fun persistCurrentEditorDraft() {
        draftRestored.await()
        check(!draftRecoveryFailed) { "草稿恢复尚未完成，请重新打开编辑器重试" }
        val epoch = draftEpoch
        draftWriteLock.withLock {
            val snapshot = currentDraftSnapshot()
            val bytes = snapshot?.let { withContext(overviewDispatcher) { draftGson.toJson(it).toByteArray(Charsets.UTF_8) } }
            check(epoch == draftEpoch) { "编辑会话已变化" }
            draftStore.write(bytes)
        }
    }

    private suspend fun restoreStructuredDrafts(saved: ProfileEditorDraftSnapshot, ownerId: Long, parentGeneration: Long, epoch: Long) {
        fun canRestore() = parentGeneration == localEditorGeneration && epoch == draftEpoch && !draftClosing
        saved.rules?.takeIf { it.profile?.id == ownerId && it.profile.type == ProfileType.CUSTOM }?.let { draft ->
            val generation = rulesGeneration
            draft.uiDraft?.restoredValues()
            val current = repository.readLocalProfileContent(ownerId)
            val committed = draft.pendingContent != null && current == draft.pendingContent
            val profile = repository.extensionProfile(ownerId)
            if (!canRestore() || generation != rulesGeneration || rulesClosing) return
            val message = when {
                committed -> null
                current != draft.sourceContent -> "配置正文已变化，请保留草稿并重新打开规则页"
                draft.isSaving || draft.pendingContent != null -> "上次保存已中断，草稿已恢复，请检查后重试"
                else -> draft.error
            }
            mutableRulesEditor.value = draft.copy(session = ++rulesGeneration, isLoading = false, isSaving = false,
                profile = if (committed) profile else draft.profile,
                sourceContent = if (committed) current else draft.sourceContent,
                rules = if (committed) ProfileRulesEditor.extractRules(current) else draft.rules, error = message, pendingContent = null,
                interruptedRules = if (committed) null else draft.pendingContent?.let(ProfileRulesEditor::extractRules) ?: draft.interruptedRules)
            if (committed) reflectStructuredSave(ownerId, current, profile)
        }
        saved.groups?.takeIf { it.profile?.id == ownerId && it.profile.type == ProfileType.CUSTOM }?.let { draft ->
            val generation = proxyGroupsGeneration
            draft.uiDraft?.restoredValues()
            val current = repository.readLocalProfileContent(ownerId)
            val committed = draft.pendingContent != null && current == draft.pendingContent
            val profile = repository.extensionProfile(ownerId)
            if (!canRestore() || generation != proxyGroupsGeneration || groupsClosing) return
            if (committed) {
                // 作者 long：正文完全匹配才认定上次提交完成；不重新提交，也不把磁盘成功冒充运行态应用成功。
                reflectStructuredSave(ownerId, current, profile)
                mutableErrors.tryEmit("已确认上次策略组保存，请检查当前配置的运行状态")
            } else {
                mutableProxyGroupsEditor.value = draft.copy(session = ++proxyGroupsGeneration, isLoading = false, isSaving = false,
                    pendingContent = null, error = when {
                        current != draft.sourceContent -> "配置正文已变化，请保留草稿并重新打开策略组"
                        draft.isSaving || draft.pendingContent != null -> "上次保存已中断，草稿已恢复，请检查后重试"
                        else -> draft.error
                    })
            }
        }
        saved.proxies?.takeIf { it.profile?.id == ownerId && it.profile.type == ProfileType.CUSTOM }?.let { draft ->
            val generation = proxiesGeneration
            draft.uiDraft?.restoredValues()
            val current = repository.readLocalProfileContent(ownerId)
            val committed = draft.pendingContent != null && current == draft.pendingContent
            val profile = repository.extensionProfile(ownerId)
            if (!canRestore() || generation != proxiesGeneration || proxiesClosing) return
            if (committed) {
                // 作者 long：落盘后死亡只确认原提交，不重写节点凭据，也不将源保存成功冒充运行态应用成功。
                reflectStructuredSave(ownerId, current, profile)
                mutableErrors.tryEmit("已确认上次代理保存，请检查当前配置的运行状态")
            } else {
                mutableProxiesEditor.value = draft.copy(session = ++proxiesGeneration, isLoading = false, isSaving = false,
                    pendingContent = null, error = when {
                        current != draft.sourceContent -> "配置正文已变化，请保留草稿并重新打开代理页"
                        draft.isSaving || draft.pendingContent != null -> "上次保存已中断，草稿已恢复，请检查后重试"
                        else -> draft.error
                    })
            }
        }
    }

    fun updateRulesUiDraft(profileId: Long, session: Long, draft: EditorSaveableState) {
        val state = mutableRulesEditor.value
        if (!draftClosing && !rulesClosing && state.profile?.id == profileId && state.session == session)
            mutableRulesEditor.value = state.copy(uiDraft = draft)
    }

    fun updateGroupsUiDraft(profileId: Long, session: Long, draft: EditorSaveableState) {
        val state = mutableProxyGroupsEditor.value
        if (!draftClosing && !groupsClosing && state.profile?.id == profileId && state.session == session)
            mutableProxyGroupsEditor.value = state.copy(uiDraft = draft)
    }

    fun updateProxiesUiDraft(profileId: Long, session: Long, draft: EditorSaveableState) {
        val state = mutableProxiesEditor.value
        if (!draftClosing && !proxiesClosing && state.profile?.id == profileId && state.session == session)
            mutableProxiesEditor.value = state.copy(uiDraft = draft)
    }

    fun discardInterruptedRules() {
        if (rulesClosing || mutableRulesEditor.value.isSaving) return
        mutableRulesEditor.update { it.copy(interruptedRules = null, error = it.error?.takeUnless { message -> message.startsWith("上次保存已中断") }) }
    }

    suspend fun persistPickerBeforeLaunch(token: Long): Boolean {
        val generation = localEditorGeneration
        return try {
            draftWriteLock.withLock {
                check(!draftRecoveryFailed && !draftClosing && (pendingFilePick?.third == token || pendingRemotePick?.third == token))
                val snapshot = requireNotNull(currentDraftSnapshot())
                val bytes = withContext(overviewDispatcher) { draftGson.toJson(snapshot).toByteArray(Charsets.UTF_8) }
                draftStore.write(bytes)
                generation == localEditorGeneration && !draftClosing
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableErrors.emit("无法暂存文件选择，请重试"); false }
    }

    fun receiveFilePickerResult(remote: Boolean, token: Long?, uri: String?) {
        // 作者 long：系统可能先交付URI再完成Keystore读取；等待草稿后按原令牌消费，不能把结果交给新开的表单。
        viewModelScope.launch {
            draftRestored.await()
            if (remote) acceptRemoteFilePick(token, uri) else acceptFileReplacement(token, uri)
        }
    }

    suspend fun reconcileFilePicker(remote: Boolean, launcherToken: Long?): Boolean {
        draftRestored.await()
        val pending = if (remote) pendingRemotePick else pendingFilePick
        val restored = if (remote) restoredRemotePick else restoredFilePick
        if (pending != null && pending.third == launcherToken) return true
        // 作者 long：磁盘草稿不代表系统任务仍存在；从桌面新开而没有对应launcher时解除孤立选择，不锁死父表单。
        if (pending != null && pending.third == restored) {
            if (remote) acceptRemoteFilePick(pending.third, null) else acceptFileReplacement(pending.third, null)
        }
        return false
    }

    fun changeCustomDraft(content: String) {
        if (!mutableEditor.value.isSaving) mutableEditor.update { it.copy(draftContent = content) }
    }

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

        if (!busy.compareAndSet(false, true)) return
        viewModelScope.launch {
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
        if (!busy.compareAndSet(false, true)) return
        viewModelScope.launch {
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
        if (deferUntilDraftRestored { openEditor(profile, isNew) }) return
        if (retryFailedDraftRecovery()) return
        openEditorNow(profile, isNew)
    }

    private fun openEditorNow(profile: Profile, isNew: Boolean = false) {
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit("当前状态暂不支持编辑此配置")
            return
        }
        if (draftClosing) return
        resetEditorMemory()
        val generation = localEditorGeneration
        if (profile.type == ProfileType.URL) {
            mutableRemoteEditor.value = RemoteProfileEditorUiState(profile, isLoading = true)
            loadRemoteDocument(generation)
            return
        }
        mutableEditor.value = ProfileEditorUiState(profile = profile, isLoading = true, isNew = isNew)
        viewModelScope.launch {
            if (generation != localEditorGeneration) return@launch
            runCatching {
                val document = if (profile.type == ProfileType.FILE) repository.readEditableProfileDocument(profile.id) else null
                (document?.content ?: repository.readLocalProfileContent(profile.id)) to document
            }.onSuccess { (content, document) ->
                    if (generation == localEditorGeneration) {
                        mutableEditor.value = ProfileEditorUiState(profile = document?.profile ?: profile, content = content, isNew = isNew, document = document)
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

    private fun loadRemoteDocument(generation: Long) {
        val profile = mutableRemoteEditor.value.profile ?: return
        viewModelScope.launch {
            try {
                val document = repository.readEditableProfileDocument(profile.id)
                if (generation == localEditorGeneration) mutableRemoteEditor.update {
                    it.copy(profile = document.profile, isLoading = false, document = document, error = null,
                        draft = if (it.draft == RemoteProfileDraft.from(profile)) RemoteProfileDraft.from(document.profile) else it.draft)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == localEditorGeneration) mutableRemoteEditor.value = mutableRemoteEditor.value.copy(isLoading = false, error = "无法读取本地副本，请重试；仍可修改订阅信息")
            }
        }
    }

    fun retryRemoteDocument() {
        val state = mutableRemoteEditor.value
        if (state.isLoading || state.isSaving || state.draftContent != null || state.replacement != null) return
        mutableRemoteEditor.value = state.copy(isLoading = true, error = null)
        loadRemoteDocument(localEditorGeneration)
    }

    fun updateRemoteDraft(draft: RemoteProfileDraft) {
        if (!mutableRemoteEditor.value.isSaving) mutableRemoteEditor.update { it.copy(draft = draft) }
    }

    fun openProfileDocument(remote: Boolean) {
        val target = if (remote) {
            val state = mutableRemoteEditor.value
            val id = state.profile?.id ?: return
            if (state.isLoading || state.isSaving || state.isReadingFile || state.replacement?.isEncrypted == true) {
                return
            }
            val content = state.draftContent ?: state.document?.content ?: return
            id to content
        } else {
            val state = mutableEditor.value
            val id = state.profile?.id ?: return
            if (state.isLoading || state.isSaving || state.isReadingFile || state.replacement != null) {
                return
            }
            id to (state.draftContent ?: state.content)
        }
        val (id, content) = target
        mutableDocumentEditor.value = ProfileDocumentEditorState(id, ++nextFilePickToken, remote, original = content, content = content)
    }

    fun changeProfileDocument(content: String) {
        mutableDocumentEditor.update { if (it.saving || it.content == content) it else it.copy(content = content, error = null, errorLocation = null) }
    }

    fun closeProfileDocument() { if (!mutableDocumentEditor.value.saving) mutableDocumentEditor.value = ProfileDocumentEditorState() }

    fun acceptProfileDocument() {
        val state = mutableDocumentEditor.value
        if (state.ownerId == null || state.saving) return
        val generation = localEditorGeneration
        mutableDocumentEditor.value = state.copy(saving = true, error = null, errorLocation = null)
        viewModelScope.launch {
            try {
                withContext(overviewDispatcher) {
                    com.lonnnnnng.flclash.data.profileDocumentText(state.content.toByteArray())
                    configValidator.validate(state.content.toByteArray())
                }
                if (generation != localEditorGeneration || mutableDocumentEditor.value.session != state.session) return@launch
                if (state.remote) {
                    remoteReplacementBytes = null
                    mutableRemoteEditor.update { it.copy(draftContent = state.content, replacement = null, error = null) }
                } else mutableEditor.update { it.copy(draftContent = state.content, error = null) }
                mutableDocumentEditor.value = ProfileDocumentEditorState()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation == localEditorGeneration && mutableDocumentEditor.value.session == state.session) {
                    val location = com.lonnnnnng.flclash.service.YamlConfigCodec.errorLocation(state.content, error)
                    val message = if (location == null) "正文校验失败，输入已保留，请检查格式与文件大小后重试"
                        else "YAML 语法错误：第 ${location.line} 行，第 ${location.column} 列；输入已保留，请修改后重试"
                    mutableDocumentEditor.value = state.copy(error = message, errorLocation = location)
                }
            }
        }
    }

    fun beginRemoteFilePick(): Long? {
        val state = mutableRemoteEditor.value
        val profile = state.profile ?: return null
        if (state.isSaving || state.isLoading || state.isReadingFile || state.isPickingFile || state.document == null || busy.value) return null
        val token = ++nextFilePickToken
        pendingRemotePick = Triple(profile.id, localEditorGeneration, token)
        mutableRemoteEditor.update { it.copy(isPickingFile = true) }
        draftRevision.value++
        return token
    }

    fun acceptRemoteFilePick(token: Long?, uri: String?) {
        val request = pendingRemotePick ?: return
        if (token != request.third) return
        pendingRemotePick = null
        mutableRemoteEditor.update { it.copy(isPickingFile = false) }
        draftRevision.value++
        if (uri == null || request.first != mutableRemoteEditor.value.profile?.id || request.second != localEditorGeneration) return
        mutableRemoteEditor.update { it.copy(isReadingFile = true, error = null) }
        viewModelScope.launch {
            try {
                val document = fileReader.read(uri)
                val encrypted = com.lonnnnnng.flclash.data.isAgeEncryptedProfile(document.bytes)
                require(document.bytes.isNotEmpty() && document.bytes.size <= com.lonnnnnng.flclash.data.MAX_PROFILE_FILE_BYTES)
                val text = if (!encrypted) withContext(overviewDispatcher) {
                    com.lonnnnnng.flclash.data.profileDocumentText(document.bytes).also { configValidator.validate(document.bytes) }
                } else null
                if (request.second == localEditorGeneration) {
                    remoteReplacementBytes = document.bytes.copyOf()
                    mutableRemoteEditor.update { it.copy(isReadingFile = false, draftContent = text,
                        replacement = ProfileFileReplacementPreview(document.name, document.bytes.size, encrypted, !encrypted)) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (request.second == localEditorGeneration) mutableRemoteEditor.update { it.copy(isReadingFile = false, error = "读取或校验文件失败，原草稿已保留") }
            }
        }
    }

    fun clearRemoteReplacement() {
        if (mutableRemoteEditor.value.isSaving || mutableRemoteEditor.value.isReadingFile) return
        remoteReplacementBytes = null
        mutableRemoteEditor.update { it.copy(replacement = null, draftContent = null, error = null) }
    }

    fun closeEditor() {
        if (retryFailedDraftRecovery()) return
        if (draftClosing) return
        if (draftStore === com.lonnnnnng.flclash.data.NoopProfileDraftStore) { resetEditorMemory(); return }
        val generation = localEditorGeneration
        draftClosing = true; draftEpoch++; draftRevision.value++
        mutableEditor.update { it.copy(isSaving = true) }
        mutableRemoteEditor.update { it.copy(isSaving = true) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(kotlinx.coroutines.NonCancellable) {
                try {
                    // 作者 long：先等旧写入结束并删除草稿，再关闭页面；VM清理不能取消已确认的放弃或让旧快照复活。
                    draftWriteLock.withLock {
                        EditorCommitProbe.checkpoint(EditorCommitWindow.PROFILE_CLOSE_BEFORE_CLEAR)
                        draftStore.write(null)
                    }
                    EditorCommitProbe.checkpoint(EditorCommitWindow.PROFILE_CLOSE_AFTER_CLEAR)
                    if (generation == localEditorGeneration) resetEditorMemory()
                } catch (_: Exception) {
                    draftClosing = false
                    mutableEditor.update { it.copy(isSaving = false, error = "草稿清除失败，请重试关闭") }
                    mutableRemoteEditor.update { it.copy(isSaving = false, error = "草稿清除失败，请重试关闭") }
                }
            }
        }
    }

    private fun resetEditorMemory() {
        draftEpoch++; draftClosing = false
        localEditorGeneration++
        rulesGeneration++
        proxyGroupsGeneration++
        proxiesGeneration++
        rulesClosing = false; groupsClosing = false
        proxiesClosing = false
        mutableRulesEditor.value = ProfileRulesEditorUiState()
        mutableProxyGroupsEditor.value = ProfileProxyGroupsEditorUiState()
        mutableProxiesEditor.value = ProfileProxiesEditorUiState()
        closeProvidersEditor()
        cancelCustomImport()
        overviewJob?.cancel()
        overviewJob = null
        pendingFilePick = null
        replacementReadJob?.cancel()
        replacementReadJob = null
        replacementBytes = null
        remoteReplacementBytes = null
        pendingRemotePick = null
        mutableDocumentEditor.value = ProfileDocumentEditorState()
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
                    reflectStructuredSave(profile.id, repository.readLocalProfileContent(profile.id), updated)
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
        if (profile.type != ProfileType.FILE || editor.isSaving || editor.isReadingFile || editor.isPickingFile || busy.value) return null
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return null
        }
        // 作者 long：系统选择器回调只属于发起时的配置和编辑会话，关闭重开后即使 ID 相同也不能接收旧文件。
        val token = ++nextFilePickToken
        pendingFilePick = Triple(profile.id, localEditorGeneration, token)
        mutableEditor.update { it.copy(isPickingFile = true) }
        draftRevision.value++
        return token
    }

    fun acceptFileReplacement(token: Long?, uri: String?) {
        val request = pendingFilePick ?: return
        if (token != request.third) return
        pendingFilePick = null
        mutableEditor.update { it.copy(isPickingFile = false) }
        draftRevision.value++
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
        mutableEditor.value = mutableEditor.value.copy(replacement = null, draftContent = null)
    }

    fun updateFileMetadataDraft(label: String, key: String) {
        if (!mutableEditor.value.isSaving) mutableEditor.update { it.copy(draftLabel = label, draftAgeKey = key) }
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
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        val preview = editor.replacement
        if (preview != null && !preview.isEncrypted && !preview.isValidYaml) return
        val bytes = replacementBytes?.copyOf() ?: (editedContent ?: editor.draftContent)?.toByteArray(Charsets.UTF_8)
        val generation = localEditorGeneration
        busy.value = true
        mutableEditor.value = editor.copy(isSaving = true)
        viewModelScope.launch {
            try {
                if (editor.document != null) {
                    profileWrite { repository.saveProfileDocument(
                        editor.document,
                        ProfileDocumentSettings(label ?: profile.label, profile.url, ageSecretKey.trim(), profile.autoUpdate, profile.autoUpdateIntervalMillis),
                        bytes,
                    ) {
                        !isProfileTransitioning(runtimeManager.state.value) && generation == localEditorGeneration
                    } }
                } else {
                    profileWrite { repository.updateFileProfile(profile.id, ageSecretKey.trim(), bytes, label) {
                        !isProfileTransitioning(runtimeManager.state.value)
                    } }
                }
                if (generation == localEditorGeneration) closeEditor()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == localEditorGeneration) {
                    mutableEditor.value = mutableEditor.value.copy(isSaving = false, error = "保存失败，输入已保留；请检查配置或重新打开")
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
            draftContent = if (content != null) null else editor.draftContent,
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
            val overview = withContext(overviewDispatcher) { customProfileSummary(snapshot.content, profile, repository.profiles.value, repository.localProxies()) }
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
        val state = mutableRemoteEditor.value
        val profile = state.profile ?: return
        if (state.isSaving || state.isLoading || state.isReadingFile || mutableDocumentEditor.value.ownerId != null) return
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit("核心正在启停，请稍后重试")
            return
        }
        val normalizedLabel = label.trim()
        if (normalizedLabel.isBlank() || normalizedLabel.length > MAX_PROFILE_LABEL_LENGTH) {
            mutableErrors.tryEmit("配置名称不能为空，最多 64 个字符")
            return
        }
        val normalizedUrl = runCatching { normalizeRemoteProfileUrl(url) }.getOrElse {
            mutableErrors.tryEmit("请输入有效的 HTTP/HTTPS 订阅链接")
            return
        }
        if (autoUpdate && intervalMinutes <= 0L) {
            mutableErrors.tryEmit("自动更新间隔必须大于 0 分钟")
            return
        }
        val interval = (intervalMinutes.takeIf { it > 0L } ?: (profile.autoUpdateIntervalMillis / MILLIS_PER_MINUTE).coerceAtLeast(1L))
            .coerceAtMost(MAX_AUTO_UPDATE_INTERVAL_MINUTES) * MILLIS_PER_MINUTE
        val key = ageSecretKey?.trim() ?: state.draft.ageKey
        val bytes = remoteReplacementBytes?.copyOf() ?: state.draftContent?.toByteArray(Charsets.UTF_8)
        val settings = ProfileDocumentSettings(normalizedLabel, normalizedUrl, key, autoUpdate, interval)
        val generation = localEditorGeneration
        if (!busy.compareAndSet(false, true)) return
        mutableRemoteEditor.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                val updated = if (state.document != null) profileWrite { repository.saveProfileDocument(state.document, settings, bytes) {
                    generation == localEditorGeneration && !isProfileTransitioning(runtimeManager.state.value)
                } } else {
                    check(bytes == null) { "请先重新读取本地副本" }
                    repository.updateRemoteProfileSettings(profile.id, normalizedLabel, normalizedUrl, autoUpdate, interval, key)
                }
                if (generation == localEditorGeneration) closeEditor()
                // 作者 long：改源时才重新下载；正文编辑已经在生命周期锁内应用，不能再下载覆盖刚保存的副本。
                if (bytes == null && (updated.url != profile.url || updated.ageSecretKey != profile.ageSecretKey)) {
                    try {
                        repository.updateRemoteProfile(profile.id)
                        applySyncedCurrent()?.let { mutableErrors.emit(it) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { mutableErrors.emit("订阅更新失败，旧缓存已保留") }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == localEditorGeneration) mutableRemoteEditor.update {
                    it.copy(isSaving = false, error = "保存失败，输入已保留；请检查配置、解密密钥或重新打开已变化的副本")
                }
            } finally { busy.value = false }
        }
    }

    fun openRulesEditor(profile: Profile) {
        if (deferUntilDraftRestored { openRulesEditor(profile) }) return
        if (retryFailedDraftRecovery()) return
        if (draftClosing || rulesClosing || mutableRulesEditor.value.isSaving) return
        if (profile.type != ProfileType.CUSTOM) {
            mutableErrors.tryEmit("高级规则编辑仅支持自定义配置")
            return
        }
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit("核心正在启停，请稍后重试")
            return
        }
        val generation = ++rulesGeneration
        draftEpoch++
        mutableRulesEditor.value = ProfileRulesEditorUiState(profile = profile, isLoading = true, session = generation)
        viewModelScope.launch {
            runCatching {
                val content = repository.readLocalProfileContent(profile.id)
                withContext(overviewDispatcher) {
                    // 作者 long：规则目标与运行节点使用同一引用投影，候选节点不能写回源正文或触发全局目录迁移。
                    Triple(content, ProfileRulesEditor.extractRules(content), CustomRuleCatalog.fromSource(LocalProxies.project(content, repository.localProxies())))
                }
            }.onSuccess { (content, rules, catalog) ->
                if (generation == rulesGeneration) mutableRulesEditor.value = ProfileRulesEditorUiState(
                    profile = profile,
                    sourceContent = content,
                    rules = rules,
                    catalog = catalog,
                    session = generation,
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
        if (mutableRulesEditor.value.isSaving || rulesClosing) return
        closeStructuredEditor(rules = true)
    }

    private fun closeStructuredEditor(rules: Boolean) {
        if (retryFailedDraftRecovery()) return
        if (rules) { rulesClosing = true; rulesGeneration++; mutableRulesEditor.update { it.copy(isSaving = true) } }
        else { groupsClosing = true; proxyGroupsGeneration++; mutableProxyGroupsEditor.update { it.copy(isSaving = true) } }
        val generation = if (rules) rulesGeneration else proxyGroupsGeneration
        val parentGeneration = localEditorGeneration
        draftEpoch++; draftRevision.value++
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(kotlinx.coroutines.NonCancellable) {
                try {
                    // 作者 long：页面关闭必须等移除该子页的密文落盘；旧写入和迟到回调都不能让已放弃的草稿复活。
                    persistCurrentEditorDraft()
                    if (parentGeneration == localEditorGeneration && generation == if (rules) rulesGeneration else proxyGroupsGeneration) {
                        if (rules) { mutableRulesEditor.value = ProfileRulesEditorUiState(); rulesClosing = false }
                        else { mutableProxyGroupsEditor.value = ProfileProxyGroupsEditorUiState(); groupsClosing = false }
                    }
                } catch (_: Exception) {
                    if (parentGeneration == localEditorGeneration && generation == if (rules) rulesGeneration else proxyGroupsGeneration) {
                        if (rules) { rulesClosing = false; mutableRulesEditor.update { it.copy(isSaving = false, error = "草稿清除失败，请重试关闭") } }
                        else { groupsClosing = false; mutableProxyGroupsEditor.update { it.copy(isSaving = false, error = "草稿清除失败，请重试关闭") } }
                    }
                }
            }
        }
    }

    fun saveRules(rules: String) {
        val state = mutableRulesEditor.value
        val profile = state.profile ?: return
        if (draftClosing || rulesClosing || state.isLoading || state.isSaving) return
        if (isProfileTransitioning(runtimeManager.state.value)) {
            val message = "核心正在启停，请稍后重试"
            mutableRulesEditor.value = state.copy(error = message)
            mutableErrors.tryEmit(message)
            return
        }
        val generation = rulesGeneration
        mutableRulesEditor.value = state.copy(isSaving = true, error = null, interruptedRules = null)
        viewModelScope.launch {
            runCatching {
                val updatedContent = withContext(overviewDispatcher) { ProfileRulesEditor.replaceRules(state.sourceContent, rules) }
                check(generation == rulesGeneration) { "编辑会话已变化" }
                mutableRulesEditor.update { it.copy(pendingContent = updatedContent) }
                persistCurrentEditorDraft()
                EditorCommitProbe.checkpoint(EditorCommitWindow.RULES_INTENT)
                val updatedProfile = profileWrite { repository.saveCustomRules(profile.id, rules, state.sourceContent) {
                    generation == rulesGeneration && !isProfileTransitioning(runtimeManager.state.value)
                }.also { EditorCommitProbe.checkpoint(EditorCommitWindow.RULES_COMMITTED) } }
                if (generation == rulesGeneration) {
                    reflectStructuredSave(profile.id, updatedContent, updatedProfile)
                    // 作者 long：与 Flutter 一样逐操作保存并留在列表；更新正文快照供下一次保存检测过期写入。
                    mutableRulesEditor.value = state.copy(profile = updatedProfile, sourceContent = updatedContent,
                        rules = ProfileRulesEditor.extractRules(updatedContent), isSaving = false, error = null,
                        uiDraft = mutableRulesEditor.value.uiDraft, pendingContent = null, interruptedRules = null)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (generation == rulesGeneration) {
                    mutableRulesEditor.value = state.copy(isSaving = false, error = error.message ?: "保存规则失败，原配置已保留",
                        uiDraft = mutableRulesEditor.value.uiDraft, pendingContent = null)
                }
            }
        }
    }

    fun openProxiesEditor(profile: Profile) {
        if (deferUntilDraftRestored { openProxiesEditor(profile) }) return
        if (retryFailedDraftRecovery()) return
        if (draftClosing || proxiesClosing || mutableProxiesEditor.value.isSaving) return
        if (profile.type != ProfileType.CUSTOM) {
            mutableErrors.tryEmit("高级代理编辑仅支持自定义配置")
            return
        }
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit("核心正在启停，请稍后重试")
            return
        }
        val generation = ++proxiesGeneration
        draftEpoch++
        mutableProxiesEditor.value = ProfileProxiesEditorUiState(profile = profile, isLoading = true, session = generation)
        viewModelScope.launch {
            runCatching {
                val content = repository.readLocalProfileContent(profile.id)
                content to withContext(overviewDispatcher) { ProfileProxiesEditor.extractProxies(content) }
            }.onSuccess { (content, proxies) ->
                if (generation == proxiesGeneration) mutableProxiesEditor.value = ProfileProxiesEditorUiState(
                    profile = profile,
                    sourceContent = content,
                    proxies = proxies,
                    session = generation,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (generation == proxiesGeneration) {
                    mutableProxiesEditor.value = ProfileProxiesEditorUiState()
                    mutableErrors.emit("无法读取代理配置，请检查 YAML 格式")
                }
            }
        }
    }

    fun closeProxiesEditor() {
        if (retryFailedDraftRecovery()) return
        if (proxiesClosing || mutableProxiesEditor.value.isSaving) return
        closeProxiesDraft()
    }

    private fun closeProxiesDraft() {
        proxiesClosing = true
        val generation = ++proxiesGeneration
        val parentGeneration = localEditorGeneration
        draftEpoch++; draftRevision.value++
        mutableProxiesEditor.update { it.copy(isSaving = true) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(kotlinx.coroutines.NonCancellable) {
                try {
                    // 作者 long：先持久移除子页，再关闭窗口；排队写入和旧窗口回调不能复活已放弃的代理正文。
                    persistCurrentEditorDraft()
                    if (generation == proxiesGeneration && parentGeneration == localEditorGeneration) {
                        mutableProxiesEditor.value = ProfileProxiesEditorUiState(); proxiesClosing = false
                    }
                } catch (_: Exception) {
                    if (generation == proxiesGeneration && parentGeneration == localEditorGeneration) {
                        proxiesClosing = false
                        mutableProxiesEditor.update { it.copy(isSaving = false, error = "草稿清除失败，请重试关闭") }
                    }
                }
            }
        }
    }

    fun saveProxies(proxies: String) {
        val state = mutableProxiesEditor.value
        val profile = state.profile ?: return
        if (draftClosing || proxiesClosing || state.isLoading || state.isSaving) return
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableProxiesEditor.value = state.copy(error = "核心正在启停，请稍后重试")
            return
        }
        val generation = proxiesGeneration
        mutableProxiesEditor.value = state.copy(isSaving = true, error = null, proxies = proxies)
        viewModelScope.launch {
            runCatching {
                val updatedContent = withContext(overviewDispatcher) { ProfileProxiesEditor.replaceProxies(state.sourceContent, proxies) }
                check(generation == proxiesGeneration) { "编辑会话已变化" }
                mutableProxiesEditor.update { it.copy(pendingContent = updatedContent) }
                persistCurrentEditorDraft()
                EditorCommitProbe.checkpoint(EditorCommitWindow.PROXIES_INTENT)
                val updatedProfile = profileWrite { repository.saveCustomDocument(profile.id, updatedContent, state.sourceContent) {
                    generation == proxiesGeneration && !isProfileTransitioning(runtimeManager.state.value)
                }.also { EditorCommitProbe.checkpoint(EditorCommitWindow.PROXIES_COMMITTED) } }
                if (generation == proxiesGeneration) reflectStructuredSave(profile.id, updatedContent, updatedProfile)
            }.onSuccess {
                if (generation == proxiesGeneration) closeProxiesDraft()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (generation == proxiesGeneration) {
                    // 作者 long：解析器或核心异常可能包含节点凭据；界面只给安全恢复动作，完整输入仍留在密文草稿。
                    val detail = error.message.orEmpty()
                    val message = when {
                        detail.matches(Regex("第 [0-9]+ (?:个代理缺少 (?:name|type)|行(?:代理条目格式无效|必须从列表条目开始|代理字段必须缩进))")) -> detail
                        detail.startsWith("代理名称重复：") -> "代理名称重复，请修改后重试"
                        else -> "保存失败，输入已保留；请检查代理格式，或重新打开已变化的配置"
                    }
                    mutableProxiesEditor.update { it.copy(isSaving = false, pendingContent = null, error = message) }
                    mutableErrors.emit(message)
                }
            }
        }
    }

    fun openProxyGroupsEditor(profile: Profile) = openProxyGroupsEditorPage(profile, null)

    fun openSingleProxyGroupEditor(profile: Profile, index: Int) = openProxyGroupsEditorPage(profile, index)

    private fun openProxyGroupsEditorPage(profile: Profile, index: Int?) {
        if (deferUntilDraftRestored { openProxyGroupsEditorPage(profile, index) }) return
        if (retryFailedDraftRecovery()) return
        if (profile.type != ProfileType.CUSTOM) {
            mutableErrors.tryEmit("高级代理组编辑仅支持自定义配置")
            return
        }
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit("核心正在启停，请稍后重试")
            return
        }
        if (draftClosing || groupsClosing || mutableProxyGroupsEditor.value.isSaving) return
        val generation = ++proxyGroupsGeneration
        draftEpoch++
        mutableProxyGroupsEditor.value = ProfileProxyGroupsEditorUiState(profile = profile, isLoading = true, initialGroupIndex = index, session = generation)
        viewModelScope.launch {
            runCatching {
                val content = repository.readLocalProfileContent(profile.id)
                content to ProfileProxyGroupsEditor.extractGroups(content)
            }.onSuccess { (content, groups) ->
                if (generation == proxyGroupsGeneration) mutableProxyGroupsEditor.value = ProfileProxyGroupsEditorUiState(
                    profile = profile, sourceContent = content, groups = groups, initialGroupIndex = index, session = generation,
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
        if (groupsClosing || mutableProxyGroupsEditor.value.isSaving) return
        closeStructuredEditor(rules = false)
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
        if (draftClosing || groupsClosing || state.isLoading || state.isSaving) return
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit("核心正在启停，请稍后重试")
            return
        }
        val generation = proxyGroupsGeneration
        mutableProxyGroupsEditor.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            runCatching {
                // 作者 long：快捷 YAML 和列表保存也必须经过核心校验，不能仅依赖表单的防抖预检。
                val filters = save.groups.flatMap { listOf(it.text("filter"), it.text("exclude-filter")) }
                require(validateGroupFilters(filters).all(String::isEmpty)) { "过滤表达式无效，请打开对应策略组修复" }
                val updatedContent = withContext(overviewDispatcher) { ProxyGroupDrafts.apply(state.sourceContent, save, repository.localProxies().map { it.name }) }
                check(generation == proxyGroupsGeneration) { "编辑会话已变化" }
                mutableProxyGroupsEditor.update { it.copy(pendingContent = updatedContent) }
                persistCurrentEditorDraft()
                EditorCommitProbe.checkpoint(EditorCommitWindow.GROUPS_INTENT)
                val updated = profileWrite { repository.saveCustomProxyGroups(profile.id, save, state.sourceContent) {
                    generation == proxyGroupsGeneration && !isProfileTransitioning(runtimeManager.state.value)
                }.also { EditorCommitProbe.checkpoint(EditorCommitWindow.GROUPS_COMMITTED) } }
                if (generation == proxyGroupsGeneration) {
                    reflectStructuredSave(profile.id, repository.readLocalProfileContent(profile.id), updated)
                    closeStructuredEditor(rules = false)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (generation == proxyGroupsGeneration) {
                    mutableProxyGroupsEditor.value = state.copy(isSaving = false, error = error.message ?: "保存代理组失败",
                        uiDraft = mutableProxyGroupsEditor.value.uiDraft, pendingContent = null)
                    mutableErrors.emit(error.message ?: "保存代理组失败")
                }
            }
        }
    }

    fun saveProxyGroups(groups: String) {
        val state = mutableProxyGroupsEditor.value
        val profile = state.profile ?: return
        if (draftClosing || groupsClosing || state.isLoading || state.isSaving) return
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit("核心正在启停，请稍后重试")
            return
        }
        val generation = proxyGroupsGeneration
        mutableProxyGroupsEditor.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            runCatching {
                val updatedContent = withContext(overviewDispatcher) { ProfileProxyGroupsEditor.replaceGroups(state.sourceContent, groups) }
                check(generation == proxyGroupsGeneration) { "编辑会话已变化" }
                mutableProxyGroupsEditor.update { it.copy(pendingContent = updatedContent) }
                persistCurrentEditorDraft()
                EditorCommitProbe.checkpoint(EditorCommitWindow.GROUPS_YAML_INTENT)
                val updatedProfile = profileWrite { repository.saveCustomDocument(profile.id, updatedContent, state.sourceContent) {
                    generation == proxyGroupsGeneration && !isProfileTransitioning(runtimeManager.state.value)
                }.also { EditorCommitProbe.checkpoint(EditorCommitWindow.GROUPS_YAML_COMMITTED) } }
                if (generation == proxyGroupsGeneration) reflectStructuredSave(profile.id, updatedContent, updatedProfile)
            }.onSuccess {
                if (generation == proxyGroupsGeneration) closeStructuredEditor(rules = false)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (generation == proxyGroupsGeneration) {
                    mutableProxyGroupsEditor.value = mutableProxyGroupsEditor.value.copy(isSaving = false, groups = groups,
                        pendingContent = null, error = error.message ?: "保存代理组失败")
                    mutableErrors.emit(error.message ?: "保存代理组失败")
                }
            }
        }
    }

    fun openProvidersEditor(profile: Profile) {
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit("核心正在启停，请稍后重试")
            return
        }
        val generation = ++providersGeneration
        mutableProvidersEditor.value = ProfileProvidersEditorUiState(profile = profile, isLoading = true)
        viewModelScope.launch {
            runCatching {
                val latest = repository.extensionProfile(profile.id) ?: error("配置不存在")
                val content = repository.readProfileSourceContent(profile.id)
                Triple(latest, content, ProfileProvidersEditor.extract(content))
            }.onSuccess { (latest, content, sections) ->
                // 作者 long：关闭或切换配置后到达的读取结果不能重新打开旧资源页。
                if (generation == providersGeneration) mutableProvidersEditor.value = ProfileProvidersEditorUiState(
                    profile = latest, sourceContent = content,
                    proxyProviders = sections.proxyProviders, ruleProviders = sections.ruleProviders,
                    proxyProviderOverrides = ProfileProviderOverrides.fromJsonString(latest.overridesJson).proxyProviders,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (generation == providersGeneration) {
                    mutableProvidersEditor.value = ProfileProvidersEditorUiState()
                    mutableErrors.emit(error.message ?: "无法读取 Provider 配置")
                }
            }
        }
    }

    fun closeProvidersEditor() {
        providersGeneration++
        mutableProvidersEditor.value = ProfileProvidersEditorUiState()
    }

    fun saveProviders(proxyProviders: String, ruleProviders: String) = saveProviders(
        proxyProviders, ruleProviders, mutableProvidersEditor.value.proxyProviderOverrides,
    )

    fun saveProviders(proxyProviders: String, ruleProviders: String, proxyProviderOverrides: Map<String, ProxyProviderOverride>) =
        persistProviders(proxyProviders, ruleProviders, proxyProviderOverrides, close = true)

    fun saveProvidersInPlace(proxyProviders: String, ruleProviders: String, proxyProviderOverrides: Map<String, ProxyProviderOverride>) =
        persistProviders(proxyProviders, ruleProviders, proxyProviderOverrides, close = false)

    private fun persistProviders(proxyProviders: String, ruleProviders: String, proxyProviderOverrides: Map<String, ProxyProviderOverride>, close: Boolean) {
        val state = mutableProvidersEditor.value
        val profile = state.profile ?: return
        if (state.isSaving || state.isLoading) return
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableProvidersEditor.value = state.copy(error = "核心正在启停，请稍后重试")
            return
        }
        val generation = providersGeneration
        // 作者 long：同步认领保存，防止同一帧的重复确认启动两次写入；失败仍保留子页草稿供重试。
        mutableProvidersEditor.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            runCatching {
                val filters = proxyProviderOverrides.values.flatMap { listOf(it.filter.orEmpty(), it.excludeFilter.orEmpty()) }
                val errors = validateGroupFilters(filters)
                require(errors.size == filters.size && errors.all(String::isEmpty)) { "Provider 过滤表达式无效，请检查后重试" }
                check(generation == providersGeneration) { "资源编辑会话已关闭" }
                val updated = if (profile.type == ProfileType.CUSTOM) {
                    profileWrite { repository.saveCustomProviders(profile.id,
                        ProfileProvidersEditor.Sections(proxyProviders, ruleProviders), proxyProviderOverrides, state.sourceContent,
                        ProfileProviderOverrides.fromJsonString(profile.overridesJson).proxyProviders) {
                        generation == providersGeneration && !isProfileTransitioning(runtimeManager.state.value)
                    } }
                } else {
                    profileWrite { repository.saveProviderOptions(profile.id, proxyProviderOverrides,
                        ProfileProviderOverrides.fromJsonString(profile.overridesJson).proxyProviders) {
                        generation == providersGeneration && !isProfileTransitioning(runtimeManager.state.value)
                    } }
                }
                val content = if (profile.type == ProfileType.CUSTOM) repository.readLocalProfileContent(profile.id) else state.sourceContent
                updated to content
            }.onSuccess { (updated, content) ->
                if (generation != providersGeneration) return@onSuccess
                reflectStructuredSave(profile.id, content, updated)
                if (close) closeProvidersEditor() else {
                    val sections = ProfileProvidersEditor.extract(content)
                    mutableProvidersEditor.value = state.copy(profile = updated, sourceContent = content,
                        proxyProviders = sections.proxyProviders, ruleProviders = sections.ruleProviders,
                        proxyProviderOverrides = ProfileProviderOverrides.fromJsonString(updated.overridesJson).proxyProviders,
                        isSaving = false, revision = state.revision + 1, error = null)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (generation == providersGeneration) {
                    mutableProvidersEditor.value = state.copy(isSaving = false, error = error.message ?: "保存 Provider 失败")
                    mutableErrors.emit(error.message ?: "保存 Provider 失败")
                }
            }
        }
    }

    fun openDnsEditor(profile: Profile) {
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit("核心正在启停，请稍后重试")
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
        if (state.isSaving) return
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit("核心正在启停，请稍后重试")
            return
        }
        viewModelScope.launch {
            mutableDnsEditor.value = state.copy(overrides = overrides, isSaving = true)
            runCatching {
                val updatedProfile = profileWrite {
                    repository.patchProfileOverrides(profile.id, { mutableDnsEditor.value.profile?.id == profile.id && !isProfileTransitioning(runtimeManager.state.value) }) { current ->
                        // 作者 long：失败后保留的是待提交草稿，并发基线始终取打开时的配置，才能安全重试。
                        check(current.dns == ProfileProviderOverrides.fromJsonString(profile.overridesJson).dns) { "DNS设置已变化，请重新打开后编辑" }
                        current.copy(dns = overrides)
                    }
                }
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
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit("核心正在启停，请稍后重试")
            return
        }
        val generation = ++dialersGeneration
        viewModelScope.launch {
            mutableDialersEditor.value = ProfileDialersEditorUiState(profile = profile, isLoading = true)
            runCatching {
                val content = LocalProxies.project(repository.readLocalProfileContent(profile.id), repository.localProxies())
                val stored = ProfileProviderOverrides.fromJsonString(profile.overridesJson).dialers
                content to ProfileDialerEditor.renderDialers(
                    if (stored != null) stored else ProfileDialerEditor.extractDialers(content),
                )
            }.onSuccess { (content, dialers) ->
                if (generation != dialersGeneration) return@onSuccess
                mutableDialersEditor.value = ProfileDialersEditorUiState(
                    profile = profile,
                    sourceContent = content,
                    dialers = dialers,
                )
            }.onFailure { error ->
                if (generation != dialersGeneration) return@onFailure
                mutableDialersEditor.value = ProfileDialersEditorUiState()
                mutableErrors.emit(error.message ?: "无法读取 Dialers 配置")
            }
        }
    }

    fun closeDialersEditor() {
        if (mutableDialersEditor.value.isSaving) return
        dialersGeneration++
        mutableDialersEditor.value = ProfileDialersEditorUiState()
    }

    fun openMoreEditor(profile: Profile) {
        if (profile.type != ProfileType.CUSTOM) return
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        moreGeneration++
        val latest = repository.profiles.value.firstOrNull { it.id == profile.id } ?: profile
        val overrides = ProfileProviderOverrides.fromJsonString(latest.overridesJson)
        mutableMoreEditor.value = ProfileMoreEditorUiState(latest, overrides.ntp, overrides.sniffer)
    }

    fun closeMoreEditor() {
        if (mutableMoreEditor.value.isSaving) return
        moreGeneration++
        mutableMoreEditor.value = ProfileMoreEditorUiState()
    }

    fun saveMoreEditor(ntp: ProfileNtpOverrides, sniffer: ProfileSnifferOverrides) {
        val state = mutableMoreEditor.value
        val profile = state.profile ?: return
        if (state.isSaving || busy.value) return
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableMoreEditor.value = state.copy(error = PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        try {
            validateMoreFields(ntp.toJson(), true)
            validateMoreFields(sniffer.toJson(), false)
        } catch (error: IllegalArgumentException) {
            mutableMoreEditor.value = state.copy(error = error.message ?: "NTP 或 Sniffer 字段无效")
            return
        }
        val generation = moreGeneration
        val parentGeneration = localEditorGeneration
        val patch = ProfileMorePatch(state.ntp, ntp, state.sniffer, sniffer)
        fun canWrite() = generation == moreGeneration && parentGeneration == localEditorGeneration &&
            mutableMoreEditor.value.profile?.id == profile.id && !isProfileTransitioning(runtimeManager.state.value)
        // 作者 long：同步认领写入，阻止同一帧重复提交；保存成功后留在分区，revision让正在确认的子页安全收起。
        mutableMoreEditor.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                val updated = profileWrite { repository.saveMoreOptions(profile.id, patch, ::canWrite) }
                if (generation == moreGeneration && parentGeneration == localEditorGeneration) {
                    reflectStructuredSave(profile.id, profile = updated)
                    val saved = ProfileProviderOverrides.fromJsonString(updated.overridesJson)
                    mutableMoreEditor.value = ProfileMoreEditorUiState(updated, saved.ntp, saved.sniffer, revision = state.revision + 1)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == moreGeneration && parentGeneration == localEditorGeneration) {
                    mutableMoreEditor.value = state.copy(error = error.message ?: "保存 NTP 或 Sniffer 失败，请重试")
                }
            }
        }
    }

    fun saveDialers(dialers: String) = saveDialerMapping(dialers, close = true)

    fun setDialerTarget(proxy: String, target: String?) {
        val state = mutableDialersEditor.value
        if (state.isLoading || state.isSaving) return
        val mapping = ProfileDialerEditor.parseDialers(state.dialers).toMutableMap()
        if (target == null) mapping.remove(proxy) else mapping[proxy] = target
        saveDialerMapping(ProfileDialerEditor.renderDialers(mapping), close = false, changedProxy = proxy)
    }

    private fun saveDialerMapping(dialers: String, close: Boolean, changedProxy: String? = null) {
        val state = mutableDialersEditor.value
        val profile = state.profile ?: return
        if (state.isSaving || state.isLoading) return
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit("核心正在启停，请稍后重试")
            return
        }
        val generation = dialersGeneration
        mutableDialersEditor.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            runCatching {
                val latest = repository.profiles.value.firstOrNull { it.id == profile.id } ?: error("配置不存在")
                val current = ProfileProviderOverrides.fromJsonString(latest.overridesJson)
                val mapping = ProfileDialerEditor.parseDialers(dialers)
                // 作者 long：保存前重读全局节点与最新覆盖，避免节点已删除、改名或启停状态变化后提交旧选择。
                val source = LocalProxies.project(repository.readLocalProfileContent(profile.id), repository.localProxies())
                if (changedProxy != null) {
                    val catalog = com.lonnnnnng.flclash.data.ProfileDialerCatalog(source)
                    require(catalog.nodes.any { it.name == changedProxy }) { "节点已变化，请重新打开链式代理" }
                    val target = mapping[changedProxy]
                    require(target == null || catalog.selectable(changedProxy, target, mapping)) { "目标已失效或会形成循环，请重新选择" }
                    check(current.dialers == ProfileProviderOverrides.fromJsonString(profile.overridesJson).dialers) { "链式设置已变化，请重新打开后编辑" }
                }
                check(!isProfileTransitioning(runtimeManager.state.value)) { PROFILE_MUTATION_BLOCKED_MESSAGE }
                val updatedProfile = profileWrite {
                    repository.patchProfileOverrides(profile.id, { generation == dialersGeneration && !isProfileTransitioning(runtimeManager.state.value) }) { latest ->
                        check(latest.dialers == current.dialers) { "链式设置已变化，请重新打开后编辑" }
                        latest.copy(dialers = mapping)
                    }
                }
                if (generation == dialersGeneration) {
                    reflectStructuredSave(profile.id, profile = updatedProfile)
                    mutableDialersEditor.value = state.copy(profile = updatedProfile, sourceContent = source, dialers = dialers, isSaving = false)
                }
            }.onSuccess {
                if (generation == dialersGeneration && close) closeDialersEditor()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (generation != dialersGeneration) return@onFailure
                mutableDialersEditor.value = state.copy(isSaving = false, error = error.message ?: "保存链式代理失败")
                mutableErrors.emit(error.message ?: "保存 Dialers 失败")
            }
        }
    }

    fun saveEditor(content: String) {
        val editor = mutableEditor.value
        val profile = editor.profile ?: return
        if (profile.type == ProfileType.FILE) { saveFileEditor(content, profile.ageSecretKey); return }
        if (editor.isSaving || editor.isLoading || isProfileTransitioning(runtimeManager.state.value)) return
        val generation = localEditorGeneration
        mutableEditor.value = editor.copy(isSaving = true, draftContent = content)
        viewModelScope.launch {
            try {
                profileWrite { repository.saveCustomDocument(profile.id, content, editor.content) { generation == localEditorGeneration } }
                if (generation == localEditorGeneration) closeEditor()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == localEditorGeneration) mutableEditor.value = editor.copy(draftContent = content,
                    error = "保存失败，输入已保留；配置可能已更新，请重新打开后重试")
                mutableErrors.emit("保存失败，输入已保留；配置可能已更新，请重新打开后重试")
            }
        }
    }

    fun update(profile: Profile) = syncProfiles(listOf(profile))

    fun updateAll() = syncProfiles(repository.profiles.value)

    fun retrySyncFailures() {
        val ids = mutableSync.value.items.filter { it.status == ProfileSyncStatus.FAILED }.map { it.id }.toSet()
        if (ids.isNotEmpty()) syncProfiles(repository.profiles.value.filter { it.id in ids }, retry = true)
        else if (mutableSync.value.applyError != null) launchAction {
            mutableSync.update { it.copy(running = true) }
            val message = applySyncedCurrent()
            mutableSync.update { it.copy(running = false, applyError = message) }
        }
    }

    fun dismissSyncResult() { if (!mutableSync.value.running) mutableSync.value = ProfileSyncUiState() }

    private suspend fun <T> profileWrite(write: suspend () -> T): T {
        draftRestored.await()
        check(!draftRecoveryFailed) { "草稿恢复尚未完成，请重新打开编辑器重试" }
        var committed: Result<T>? = null
        try {
            return runtimeManager.withProfileWrite(repository.currentProfileId.value) {
                write().also { committed = Result.success(it) }
            }
        } catch (_: com.lonnnnnng.flclash.service.ProfileApplyAfterSaveException) {
            val message = "配置已保存，当前配置应用失败，请重试应用"
            mutableSync.update { it.copy(applyError = message) }
            mutableErrors.emit(message)
            return requireNotNull(committed).getOrThrow()
        }
    }

    private suspend fun applySyncedCurrent(): String? {
        val selected = repository.currentProfileId.value ?: return null
        return try {
            // 作者 long：下载期间用户可能切换配置，只刷新此刻选中项；管理器再次核对ID，绝不把下载目标切成当前项。
            runtimeManager.applyCurrentProfile(selected)
            null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (repository.currentProfileId.value != selected) null else "配置已保存，当前配置应用失败，请重试应用"
        }
    }

    private fun syncProfiles(profiles: List<Profile>, retry: Boolean = false) {
        val targets = profiles.filter { it.type == ProfileType.URL }.distinctBy { it.id }
        if (targets.isEmpty() || isProfileTransitioning(runtimeManager.state.value) || !busy.compareAndSet(false, true)) return
        val previous = mutableSync.value
        // 作者 long：只重试失败项时保留前批已保存项及应用失败，不能让再次下载失败抹掉仍待应用的配置。
        mutableSync.value = if (retry) previous.copy(running = true, items = previous.items.map { row ->
            if (targets.any { it.id == row.id }) row.copy(status = ProfileSyncStatus.WAITING) else row
        }) else ProfileSyncUiState(true, targets.map { ProfileSyncItem(it.id, it.realLabel) })
        viewModelScope.launch {
            try {
                val slots = Semaphore(4)
                targets.map { profile -> async {
                    slots.withPermit {
                        mutableSync.update { it.withStatus(profile.id, ProfileSyncStatus.RUNNING) }
                        try {
                            repository.updateRemoteProfile(profile.id)
                            mutableSync.update { it.withStatus(profile.id, ProfileSyncStatus.SUCCEEDED) }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            // 作者 long：统一安全说明；核心/HTTP原文可能包含订阅凭据，不显示或存入批次状态。
                            mutableSync.update { it.withStatus(profile.id, ProfileSyncStatus.FAILED) }
                        }
                    }
                } }.awaitAll()
                val applyError = if (mutableSync.value.items.any { it.status == ProfileSyncStatus.SUCCEEDED }) applySyncedCurrent() else null
                mutableSync.update { it.copy(applyError = applyError) }
            } finally {
                mutableSync.update { it.copy(running = false) }
                busy.value = false
            }
        }
    }

    fun setAutoUpdate(profile: Profile, enabled: Boolean) {
        if (profile.type != ProfileType.URL) return
        if (isProfileTransitioning(runtimeManager.state.value)) {
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
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        val normalized = label.trim()
        if (normalized.isEmpty()) {
            mutableErrors.tryEmit("配置名称不能为空")
            return
        }
        // 作者 long：名称只改元数据，运行中可保存；事务内合并最新整行，不改变正在转发的节点。
        saveEditorMetadata(profile.id) { repository.updateProfileLabel(profile.id, normalized) }
    }

    fun refreshSelection() {
        viewModelScope.launch { repository.refreshCurrentProfile() }
    }

    fun bindScript(profile: Profile, scriptId: Long?) {
        if (isProfileTransitioning(runtimeManager.state.value)) {
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
                val updated = profileWrite(save)
                if (generation == localEditorGeneration) reflectStructuredSave(profileId, profile = updated)
            } finally {
                if (generation == localEditorGeneration && mutableEditor.value.profile?.id == profileId) {
                    mutableEditor.value = mutableEditor.value.copy(isSaving = false)
                }
            }
        }
    }

    fun reorder(ids: List<Long>) {
        if (isProfileTransitioning(runtimeManager.state.value)) {
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
        if (!busy.compareAndSet(false, true)) return
        orderResult.value = orderResult.value.first to null
        viewModelScope.launch {
            try {
                runtimeManager.withProfileWrite { repository.reorder(orderedIds) }
                orderResult.value = (orderResult.value.first + 1) to null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { orderResult.value = orderResult.value.first to "排序保存失败，请重试" }
            finally { busy.value = false }
        }
    }

    fun delete(profile: Profile) {
        if (isProfileTransitioning(runtimeManager.state.value)) {
            mutableErrors.tryEmit("核心正在启停，请稍后重试")
            return
        }
        launchAction {
            runtimeManager.removeProfile(profile.id, { repository.profiles.value.map(Profile::id) }, repository::selectProfile) {
                repository.deleteProfile(profile.id)
            }
        }
    }

    private fun launchAction(action: suspend () -> Unit) {
        // 作者 long：认领发生在启动协程前，连续点击不能让多笔操作各自清掉另一笔仍在使用的busy。
        if (!busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableErrors.emit(error.message ?: "操作失败") }
            finally { busy.value = false }
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
