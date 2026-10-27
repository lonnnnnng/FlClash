package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileDialerEditor
import com.lonnnnnng.flclash.data.ProfileProxyGroupsEditor
import com.lonnnnnng.flclash.data.ProfileProxiesEditor
import com.lonnnnnng.flclash.data.ProfileProvidersEditor
import com.lonnnnnng.flclash.data.ProfileRulesEditor
import com.lonnnnnng.flclash.data.ProfileStore
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.CoreRuntimeStatusProvider
import com.lonnnnnng.flclash.service.ProfileDnsOverrides
import com.lonnnnnng.flclash.service.ProfileProviderOverrides
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
)

data class ProfileRulesEditorUiState(
    val profile: Profile? = null,
    val sourceContent: String = "",
    val rules: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
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

internal const val PROFILE_MUTATION_BLOCKED_MESSAGE = "核心运行中，请先停止核心后再修改配置"

/**
 * 作者 long：核心运行期间配置文件已经被 Mihomo 打开，阻止会改变文件或当前选择的操作，
 * 避免删除/编辑后运行中的核心继续持有失效路径，导致页面状态与实际代理配置分裂。
 */
internal fun isProfileMutationBlocked(runtimeStatus: CoreRuntimeStatus): Boolean =
    runtimeStatus.isRunning

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repository: ProfileStore,
    private val runtimeManager: CoreRuntimeStatusProvider,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val mutableEditor = MutableStateFlow(ProfileEditorUiState())
    private val mutableRulesEditor = MutableStateFlow(ProfileRulesEditorUiState())
    private val mutableProxiesEditor = MutableStateFlow(ProfileProxiesEditorUiState())
    private val mutableProxyGroupsEditor = MutableStateFlow(ProfileProxyGroupsEditorUiState())
    private val mutableProvidersEditor = MutableStateFlow(ProfileProvidersEditorUiState())
    private val mutableDnsEditor = MutableStateFlow(ProfileDnsEditorUiState())
    private val mutableDialersEditor = MutableStateFlow(ProfileDialersEditorUiState())

    val state: StateFlow<ProfilesUiState> = combine(
        repository.profiles,
        repository.currentProfileId,
        busy,
    ) { profiles, currentId, isBusy ->
        ProfilesUiState(profiles, currentId, isBusy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUiState())

    val errors = mutableErrors.asSharedFlow()
    val editorState: StateFlow<ProfileEditorUiState> = mutableEditor
    val rulesEditorState: StateFlow<ProfileRulesEditorUiState> = mutableRulesEditor
    val proxiesEditorState: StateFlow<ProfileProxiesEditorUiState> = mutableProxiesEditor
    val proxyGroupsEditorState: StateFlow<ProfileProxyGroupsEditorUiState> = mutableProxyGroupsEditor
    val providersEditorState: StateFlow<ProfileProvidersEditorUiState> = mutableProvidersEditor
    val dnsEditorState: StateFlow<ProfileDnsEditorUiState> = mutableDnsEditor
    val dialersEditorState: StateFlow<ProfileDialersEditorUiState> = mutableDialersEditor

    fun select(id: Long) {
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再切换配置")
            return
        }
        launchAction { repository.selectProfile(id) }
    }

    fun addRemote(url: String, label: String) = launchAction {
        repository.addRemoteProfile(url, label)
    }

    fun addFile(label: String, bytes: ByteArray) = launchAction {
        repository.addFileProfile(label, bytes)
    }

    fun addCustom(label: String) = launchAction {
        repository.addCustomProfile(label)
    }

    fun reportError(message: String) {
        mutableErrors.tryEmit(message)
    }

    fun openEditor(profile: Profile) {
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再编辑配置")
            return
        }
        viewModelScope.launch {
            mutableEditor.value = ProfileEditorUiState(profile = profile, isLoading = true)
            runCatching { repository.readLocalProfileContent(profile.id) }
                .onSuccess { content ->
                    mutableEditor.value = ProfileEditorUiState(profile = profile, content = content)
                }
                .onFailure { error ->
                    mutableEditor.value = ProfileEditorUiState()
                    mutableErrors.emit(error.message ?: "无法读取配置")
                }
        }
    }

    fun closeEditor() {
        mutableEditor.value = ProfileEditorUiState()
    }

    fun openRulesEditor(profile: Profile) {
        if (profile.type != ProfileType.CUSTOM) {
            mutableErrors.tryEmit("高级规则编辑仅支持自定义配置")
            return
        }
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再编辑规则")
            return
        }
        viewModelScope.launch {
            mutableRulesEditor.value = ProfileRulesEditorUiState(profile = profile, isLoading = true)
            runCatching {
                val content = repository.readLocalProfileContent(profile.id)
                content to ProfileRulesEditor.extractRules(content)
            }.onSuccess { (content, rules) ->
                mutableRulesEditor.value = ProfileRulesEditorUiState(
                    profile = profile,
                    sourceContent = content,
                    rules = rules,
                )
            }.onFailure { error ->
                mutableRulesEditor.value = ProfileRulesEditorUiState()
                mutableErrors.emit(error.message ?: "无法读取规则配置")
            }
        }
    }

    fun closeRulesEditor() {
        mutableRulesEditor.value = ProfileRulesEditorUiState()
    }

    fun saveRules(rules: String) {
        val state = mutableRulesEditor.value
        val profile = state.profile ?: return
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再保存规则")
            return
        }
        viewModelScope.launch {
            mutableRulesEditor.value = state.copy(isSaving = true)
            runCatching {
                val updatedContent = ProfileRulesEditor.replaceRules(state.sourceContent, rules)
                repository.updateLocalProfileContent(profile.id, updatedContent)
            }.onSuccess {
                closeRulesEditor()
            }.onFailure { error ->
                mutableRulesEditor.value = mutableRulesEditor.value.copy(isSaving = false, rules = rules)
                mutableErrors.emit(error.message ?: "保存规则失败")
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
                repository.updateLocalProfileContent(profile.id, updatedContent)
            }.onSuccess {
                closeProxiesEditor()
            }.onFailure { error ->
                mutableProxiesEditor.value = mutableProxiesEditor.value.copy(isSaving = false, proxies = proxies)
                mutableErrors.emit(error.message ?: "保存代理失败")
            }
        }
    }

    fun openProxyGroupsEditor(profile: Profile) {
        if (profile.type != ProfileType.CUSTOM) {
            mutableErrors.tryEmit("高级代理组编辑仅支持自定义配置")
            return
        }
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再编辑代理组")
            return
        }
        viewModelScope.launch {
            mutableProxyGroupsEditor.value = ProfileProxyGroupsEditorUiState(profile = profile, isLoading = true)
            runCatching {
                val content = repository.readLocalProfileContent(profile.id)
                content to ProfileProxyGroupsEditor.extractGroups(content)
            }.onSuccess { (content, groups) ->
                mutableProxyGroupsEditor.value = ProfileProxyGroupsEditorUiState(
                    profile = profile,
                    sourceContent = content,
                    groups = groups,
                )
            }.onFailure { error ->
                mutableProxyGroupsEditor.value = ProfileProxyGroupsEditorUiState()
                mutableErrors.emit(error.message ?: "无法读取代理组配置")
            }
        }
    }

    fun closeProxyGroupsEditor() {
        mutableProxyGroupsEditor.value = ProfileProxyGroupsEditorUiState()
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
                repository.updateLocalProfileContent(profile.id, updatedContent)
            }.onSuccess {
                closeProxyGroupsEditor()
            }.onFailure { error ->
                mutableProxyGroupsEditor.value = mutableProxyGroupsEditor.value.copy(isSaving = false, groups = groups)
                mutableErrors.emit(error.message ?: "保存代理组失败")
            }
        }
    }

    fun openProvidersEditor(profile: Profile) {
        if (profile.type != ProfileType.CUSTOM) {
            mutableErrors.tryEmit("高级 Provider 编辑仅支持自定义配置")
            return
        }
        if (runtimeManager.state.value.isRunning) {
            mutableErrors.tryEmit("核心运行中，请先停止核心后再编辑 Provider")
            return
        }
        viewModelScope.launch {
            mutableProvidersEditor.value = ProfileProvidersEditorUiState(profile = profile, isLoading = true)
            runCatching {
                val content = repository.readLocalProfileContent(profile.id)
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
                val updatedContent = ProfileProvidersEditor.replace(
                    state.sourceContent,
                    ProfileProvidersEditor.Sections(proxyProviders, ruleProviders),
                )
                repository.updateLocalProfileContent(profile.id, updatedContent)
                repository.updateProfileOverrides(
                    profile.id,
                    ProfileProviderOverrides(
                        proxyProviders = proxyProviderOverrides,
                        dns = ProfileProviderOverrides.fromJsonString(profile.overridesJson).dns,
                        // 作者 long：Provider、DNS 和 Dialers 共用一份覆盖元数据；保存任一分区时必须保留其他分区，
                        // 否则用户刚配置的出站链路会在下一次编辑 Provider 后静默消失。
                        dialers = ProfileProviderOverrides.fromJsonString(profile.overridesJson).dialers,
                    ).toJsonString(),
                )
            }.onSuccess {
                closeProvidersEditor()
            }.onFailure { error ->
                mutableProvidersEditor.value = mutableProvidersEditor.value.copy(isSaving = false)
                mutableErrors.emit(error.message ?: "保存 Provider 失败")
            }
        }
    }

    fun openDnsEditor(profile: Profile) {
        if (profile.type != ProfileType.CUSTOM) {
            mutableErrors.tryEmit("高级 DNS 编辑仅支持自定义配置")
            return
        }
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
                val current = ProfileProviderOverrides.fromJsonString(profile.overridesJson)
                repository.updateProfileOverrides(
                    profile.id,
                    current.copy(dns = overrides).toJsonString(),
                )
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
                val current = ProfileProviderOverrides.fromJsonString(profile.overridesJson)
                val mapping = ProfileDialerEditor.parseDialers(dialers)
                repository.updateProfileOverrides(
                    profile.id,
                    current.copy(dialers = mapping).toJsonString(),
                )
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

    fun bindScript(profile: Profile, scriptId: Long?) {
        if (isProfileMutationBlocked(runtimeManager.state.value)) {
            mutableErrors.tryEmit(PROFILE_MUTATION_BLOCKED_MESSAGE)
            return
        }
        // 作者 long：绑定只改变 Profile 的元数据，不执行脚本；执行链路完成前必须保持显式的无副作用边界。
        launchAction { repository.updateProfileScript(profile.id, scriptId) }
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
}
