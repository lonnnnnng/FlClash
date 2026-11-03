package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.data.OnDemandCoordinator
import com.lonnnnnng.flclash.service.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class OnDemandViewModel @Inject constructor(private val store: OnDemandSettingsStore, coordinator: OnDemandCoordinator,
    manager: CoreRuntimeManager) : ViewModel() {
    val ssids = store.changes ?: MutableStateFlow(store.read()).asStateFlow()
    val network = coordinator.network
    val suspended = manager.onDemandSuspended
    val runtimeError = coordinator.error
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableSavedVersion = MutableStateFlow(0L)
    val savedVersion = mutableSavedVersion.asStateFlow()
    fun save(expected: List<String>, next: List<String>) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableError.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO + NonCancellable) {
                    // 作者 long：备份恢复与页面编辑共享存储锁，不能在比较旧目录后被另一写者插入覆盖。
                    store.compareAndSet(expected, OnDemandSettings.validate(next))
                }
                // 作者 long：保存结果属于保留的VM，Activity重建后的页面也能关闭同一次已提交表单。
                mutableSavedVersion.value++
            } catch (failure: Exception) {
                mutableError.value = failure.message?.takeIf { it.length < 120 } ?: "无法保存 SSID 列表，请重试"
            } finally { mutableBusy.value = false }
        }
    }
}
