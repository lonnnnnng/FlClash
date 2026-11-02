package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ProfileStatistics
import com.lonnnnnng.flclash.service.ProfileStatisticsRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ProfileStatisticsUiState(val profileId: Long? = null, val counts: ProfileStatistics? = null,
    val isLoading: Boolean = false, val error: String? = null)

data class ProfileProvidersUiState(val profileId: Long? = null,
    val providers: List<com.lonnnnnng.flclash.core.CoreExternalProvider>? = null,
    val isLoading: Boolean = false, val error: String? = null, val updatingName: String? = null,
    val updateMessage: String? = null, val updateFailed: Boolean = false)

@HiltViewModel
class ProfileStatisticsViewModel @Inject constructor(private val runtime: ProfileStatisticsRuntime) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileStatisticsUiState())
    val state = mutableState.asStateFlow()
    private val mutableProviders = MutableStateFlow(ProfileProvidersUiState())
    val providers = mutableProviders.asStateFlow()
    private val providerRetries = MutableStateFlow(0L)
    private var updateJob: Job? = null
    private var inFlightName: String? = null
    private var visibilityGeneration = 0L
    private val foreground = MutableStateFlow(false)
    private val profile = MutableStateFlow<Long?>(null)
    private val retries = MutableStateFlow(0L)

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
                        mutableProviders.update { it.copy(providers = rows, isLoading = false, error = null) }
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
            providers.value.providers?.any { it.name == name && it.canUpdate } != true) return
        val generation = visibilityGeneration
        inFlightName = name
        mutableProviders.update { it.copy(updatingName = name, updateMessage = null, updateFailed = false) }
        updateJob = viewModelScope.launch {
            var failure: String? = null
            try {
                runtime.updateProfileProvider(id, name)
                currentCoroutineContext().ensureActive()
            } catch (error: CancellationException) { throw error }
            catch (error: com.lonnnnnng.flclash.service.ProviderOperationException) { failure = error.message }
            catch (_: Exception) { failure = "更新失败，请稍后重试" }
            inFlightName = null
            mutableProviders.update { it.copy(updatingName = null) }
            // 作者 long：关闭、后台或换配置后，旧下载即使完成也不再向新详情写入提示或触发代理组刷新。
            if (generation == visibilityGeneration && foreground.value && profile.value == id) {
                mutableProviders.update { it.copy(updatingName = null, updateMessage = failure ?: "Provider 已更新", updateFailed = failure != null) }
                providerRetries.update { it + 1 }
                retries.update { it + 1 }
            }
        }
    }

    fun retryProviders() { if (foreground.value && profile.value != null && !providers.value.isLoading) providerRetries.update { it + 1 } }
    fun show(profileId: Long?) {
        if (profile.value != profileId) { visibilityGeneration++; mutableProviders.value = ProfileProvidersUiState(profileId, updatingName = inFlightName) }
        profile.value = profileId
    }
    fun setForeground(value: Boolean) {
        if (foreground.value != value) { visibilityGeneration++; mutableProviders.value = ProfileProvidersUiState(profile.value, updatingName = inFlightName) }
        foreground.value = value
    }
    fun retry() { if (foreground.value && profile.value != null && !state.value.isLoading) retries.update { it + 1 } }
}
