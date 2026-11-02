package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.IpInformationStore
import com.lonnnnnng.flclash.data.IpQuality
import com.lonnnnnng.flclash.data.IpQualityException
import com.lonnnnnng.flclash.data.IpQualitySource
import com.lonnnnnng.flclash.data.IpSourceFailure
import com.lonnnnnng.flclash.data.OutboundIpInfo
import com.lonnnnnng.flclash.service.OutboundIpRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OutboundIpUiState(
    val info: OutboundIpInfo? = null,
    val quality: IpQuality? = null,
    val isLoading: Boolean = false,
    val qualityLoading: Boolean = false,
    val error: String? = null,
    val qualityFailures: Map<IpQualitySource, IpSourceFailure> = emptyMap(),
    val qualityError: String? = null,
)

@HiltViewModel
class OutboundIpViewModel @Inject constructor(
    private val runtime: OutboundIpRuntime,
    private val repository: IpInformationStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OutboundIpUiState())
    val state = mutableState.asStateFlow()
    private val foreground = MutableStateFlow(false)
    private val dashboardVisible = MutableStateFlow(false)
    private val visible get() = foreground.value && dashboardVisible.value
    private var request: Job? = null
    private var generation = 0L
    private var retryPolls = 0
    private var lastRouteKey: List<Any?>? = null
    private var currentStamp: com.lonnnnnng.flclash.core.CoreRouteStamp? = null

    init {
        viewModelScope.launch {
            combine(runtime.state, runtime.runningSinceElapsedMillis, runtime.ipRouteRevision, combine(foreground, dashboardVisible) { front, dashboard -> front && dashboard }) { status, since, revision, shown ->
                listOf(status, since, revision, shown)
            }.collectLatest { key ->
                val routeKey = key.take(3)
                if (lastRouteKey != routeKey) {
                    lastRouteKey = routeKey
                    currentStamp = null
                    mutableState.value = OutboundIpUiState()
                }
                request?.cancel()
                generation++
                val active = runtime.state.value != com.lonnnnnng.flclash.service.CoreRuntimeStatus.STARTING &&
                    runtime.state.value != com.lonnnnnng.flclash.service.CoreRuntimeStatus.STOPPING
                if (!active) {
                    currentStamp = null
                    mutableState.value = OutboundIpUiState()
                    return@collectLatest
                }
                // 作者 long：离开首页或应用后台时取消等待；回来先读核心版本再决定能否复用旧值。
                if (!visible) {
                    mutableState.update { it.copy(isLoading = false, qualityLoading = false) }
                    return@collectLatest
                }
                while (isActive) {
                    try {
                        val stamp = runtime.ipRouteStamp()
                        if (stamp != currentStamp) {
                            currentStamp = stamp
                            invalidate()
                        } else if ((mutableState.value.info == null || mutableState.value.error != null) && request?.isActive != true) {
                            // 作者 long：路由收敛或短暂断网会使查询失效，首页可见时每五轮重试，避免永久停在失败状态。
                            if (mutableState.value.error == null || ++retryPolls >= 5) refresh()
                        } else if (mutableState.value.info != null && mutableState.value.quality == null &&
                            mutableState.value.qualityFailures.isEmpty() && request?.isActive != true
                        ) {
                            startQuality(force = false)
                        } else if (request?.isActive != true && ++retryPolls >= 60) {
                            // 作者 long：节点后端可能更换出口而不改变本地选择，首页前台定期复查实际 IP。
                            refresh()
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        invalidate(startRequest = false)
                        mutableState.value = OutboundIpUiState(error = "出口状态读取失败，点击重试")
                    }
                    delay(1_000L)
                }
            }
        }
    }

    fun setForeground(value: Boolean) { foreground.value = value }
    fun networkChanged() { runtime.networkChanged() }
    fun setDashboardVisible(value: Boolean) { dashboardVisible.value = value }

    private fun invalidate(startRequest: Boolean = true) {
        request?.cancel()
        generation++
        mutableState.value = OutboundIpUiState()
        if (startRequest) refresh()
    }

    fun refresh() {
        if (!visible || request?.isActive == true) return
        retryPolls = 0
        val token = ++generation
        mutableState.update { it.copy(isLoading = true, error = null) }
        request = viewModelScope.launch {
            try {
                val answer = repository.outbound()
                val stamp = runtime.ipRouteStamp()
                if (token != generation || answer.stamp != stamp) {
                    if (token == generation) {
                        currentStamp = null
                        mutableState.value = OutboundIpUiState()
                    }
                    return@launch
                }
                val old = mutableState.value
                currentStamp = stamp
                val sameIp = old.info?.ip == answer.info.ip
                mutableState.value = OutboundIpUiState(
                    info = answer.info, quality = old.quality.takeIf { sameIp }, qualityLoading = true,
                )
                loadQuality(answer.info.ip, token, force = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (token == generation) mutableState.update {
                    it.copy(isLoading = false, error = "出口查询失败，点击图标重试")
                }
            }
        }
    }

    fun refreshQuality() = startQuality(force = true)

    private fun startQuality(force: Boolean) {
        val ip = mutableState.value.info?.ip ?: return
        if (!visible || request?.isActive == true) return
        val token = ++generation
        mutableState.update { it.copy(qualityLoading = true, qualityFailures = emptyMap()) }
        request = viewModelScope.launch { loadQuality(ip, token, force) }
    }

    private suspend fun loadQuality(ip: String, token: Long, force: Boolean) {
        try {
            val quality = repository.quality(ip, force)
            // 作者 long：缓存命中也要核对路由，避免一秒轮询之前的切换让旧质量重新出现在卡片。
            if (runtime.ipRouteStamp() != currentStamp) {
                if (token == generation) { currentStamp = null; invalidate(startRequest = false) }
                return
            }
            if (token == generation && mutableState.value.info?.ip == ip) {
                mutableState.update { it.copy(quality = quality, qualityLoading = false, qualityFailures = emptyMap()) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (token == generation && mutableState.value.info?.ip == ip) mutableState.update {
                it.copy(qualityLoading = false, qualityFailures = (error as? IpQualityException)?.failures
                    ?: mapOf(IpQualitySource.IDENT_ME to IpSourceFailure.FAILED))
            }
        }
    }
}
