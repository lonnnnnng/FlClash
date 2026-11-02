package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.core.CoreMemoryStats
import com.lonnnnnng.flclash.service.MemoryRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun CoreMemoryStats.appResidentBytes(): Long = (rss - runtimeTotal.coerceAtMost(rss)).coerceAtLeast(0L)

data class MemoryUiState(
    val snapshot: CoreMemoryStats? = null,
    val isLoading: Boolean = false,
    val isReleasing: Boolean = false,
    val error: String? = null,
    val feedback: String? = null,
)

@HiltViewModel
class MemoryViewModel @Inject constructor(private val runtime: MemoryRuntime) : ViewModel() {
    private val mutableState = MutableStateFlow(MemoryUiState())
    val state = mutableState.asStateFlow()
    private val foreground = MutableStateFlow(false)
    private val visible = MutableStateFlow(false)
    private val readLock = Mutex()
    private var releaseJob: Job? = null
    private var generation = 0L

    init {
        viewModelScope.launch {
            // 作者 long：前台且卡片可见时才采样，等价的不可见状态不应重复取消同一次回收。
            combine(foreground, visible) { front, shown -> front && shown }.distinctUntilChanged().collectLatest { active ->
                generation++
                releaseJob?.cancel()
                mutableState.update { it.copy(isLoading = false, isReleasing = false) }
                if (!active) return@collectLatest
                while (isActive) {
                    if (!mutableState.value.isReleasing) read()
                    delay(2_000L)
                }
            }
        }
    }

    fun setForeground(value: Boolean) { foreground.value = value }
    fun setVisible(value: Boolean) { visible.value = value }

    private suspend fun read() = readLock.withLock {
        mutableState.update { it.copy(isLoading = it.snapshot == null) }
        try {
            val answer = runtime.memoryStats()
            currentCoroutineContext().ensureActive()
            mutableState.update { it.copy(snapshot = answer, isLoading = false, error = null) }
        } catch (error: CancellationException) { throw error
        } catch (_: Exception) {
            // 作者 long：底层若延迟返回失败，取消后的页面也不能接收这次旧错误。
            currentCoroutineContext().ensureActive()
            mutableState.update { it.copy(isLoading = false, error = "内存读取失败，将自动重试") }
        }
    }

    fun release() {
        if (!foreground.value || !visible.value || mutableState.value.isReleasing) return
        val token = generation
        mutableState.update { it.copy(isReleasing = true, feedback = null, error = null) }
        releaseJob = viewModelScope.launch {
            try {
                readLock.withLock {
                    val answer = runtime.releaseMemory()
                    currentCoroutineContext().ensureActive()
                    if (token == generation) mutableState.update {
                        // 作者 long：RSS 会受同时发生的分配影响，不把采样差额承诺成确定的释放字节数。
                        it.copy(snapshot = answer, isReleasing = false, isLoading = false, feedback = "已请求回收，内存数据已刷新")
                    }
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (token == generation) mutableState.update { it.copy(isReleasing = false, error = "释放内存失败，请重试") }
            }
        }
    }
}
