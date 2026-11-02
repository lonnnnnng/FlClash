package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.ClipboardGateway
import com.lonnnnnng.flclash.data.LocalAddressState
import com.lonnnnnng.flclash.data.LocalNetworkSource
import com.lonnnnnng.flclash.service.LanSharingRuntime
import com.lonnnnnng.flclash.service.LanSharingState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class IntranetIpUiState(
    val local: LocalAddressState = LocalAddressState(),
    val sharing: LanSharingState = LanSharingState(),
    val settingsLoading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val settingsError: String? = null,
    val feedback: String? = null,
) {
    val copyText: String? get() {
        if (settingsLoading || saving || settingsError != null) return null
        val ip = local.address?.takeUnless { local.isLoading || local.error != null } ?: return null
        if (!sharing.config.allowLan) return ip
        // 作者 long：共享尚未加载配置或端口被禁用时不能复制一个不可用的代理端点。
        if (settingsLoading || sharing.config.mixedPort == 0 || !sharing.hasConfig) return null
        return (if (':' in ip) "[" + ip + "]" else ip) + ":" + sharing.config.mixedPort
    }
}

@HiltViewModel
class IntranetIpViewModel @Inject constructor(
    private val networks: LocalNetworkSource,
    private val runtime: LanSharingRuntime,
    private val clipboard: ClipboardGateway,
) : ViewModel() {
    private val mutable = MutableStateFlow(IntranetIpUiState())
    val state = mutable.asStateFlow()
    private val foreground = MutableStateFlow(false)
    private val visible = MutableStateFlow(false)
    private val retry = MutableStateFlow(0L)

    init {
        viewModelScope.launch { runtime.lanSharing.collect { value -> mutable.update { it.copy(sharing = value) } } }
        viewModelScope.launch {
            combine(foreground, visible, retry) { front, shown, token -> (front && shown) to token }
                .distinctUntilChanged().collectLatest { (active, _) ->
                    // 作者 long：离屏立即撤下地址，回来由新回调重建，不能复制前一个网络的旧地址。
                    mutable.update { it.copy(local = LocalAddressState()) }
                    if (!active) return@collectLatest
                    coroutineScope {
                        launch {
                            runtime.lanRevision.collect {
                                mutable.update { it.copy(settingsLoading = true) }
                                try {
                                    runtime.refreshLanSharing()
                                    currentCoroutineContext().ensureActive()
                                    mutable.update { it.copy(settingsLoading = false, settingsError = null) }
                                } catch (_: TimeoutCancellationException) {
                                    currentCoroutineContext().ensureActive()
                                    mutable.update { it.copy(settingsLoading = false, settingsError = "读取局域网设置超时，请重试") }
                                } catch (cancelled: CancellationException) { throw cancelled
                                } catch (_: Exception) {
                                    currentCoroutineContext().ensureActive()
                                    mutable.update { it.copy(settingsLoading = false, settingsError = "读取局域网设置失败，请重试") }
                                }
                            }
                        }
                        networks.observe().collect { value ->
                            currentCoroutineContext().ensureActive()
                            mutable.update { it.copy(local = value) }
                        }
                    }
                }
        }
    }
    fun setForeground(value: Boolean) { foreground.value = value }
    fun setVisible(value: Boolean) { visible.value = value }
    fun retry() { mutable.update { it.copy(error = null) }; retry.value++ }

    fun setAllowLan(value: Boolean) {
        if (mutable.value.saving || mutable.value.settingsLoading || mutable.value.settingsError != null) return
        mutable.update { it.copy(saving = true, feedback = null, error = null) }
        viewModelScope.launch {
            try {
                runtime.updateLanSharing(value)
                mutable.update { it.copy(saving = false, feedback = if (runtime.state.value.isRunning) "局域网设置已应用" else "局域网设置已保存，启动后生效") }
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                mutable.update { it.copy(saving = false, error = "局域网设置超时，请重试") }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { mutable.update { it.copy(saving = false, error = "局域网设置失败，请重试") } }
        }
    }

    fun copyAddress() {
        val text = state.value.copyText ?: return
        try {
            clipboard.writeText(text)
            mutable.update { it.copy(feedback = "已复制") }
        } catch (_: Exception) { mutable.update { it.copy(error = "复制失败，请重试") } }
    }
}
