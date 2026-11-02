package com.lonnnnnng.flclash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.flclash.service.RuntimeConfigPreview
import com.lonnnnnng.flclash.service.RuntimeConfigPreviewProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RuntimeConfigPreviewUiState(
    val profileId: Long? = null,
    val isLoading: Boolean = false,
    val preview: RuntimeConfigPreview? = null,
    val error: String? = null,
)

@HiltViewModel
class RuntimeConfigPreviewViewModel @Inject constructor(
    private val provider: RuntimeConfigPreviewProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RuntimeConfigPreviewUiState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    private var generation = 0L

    fun open(profileId: Long) {
        request?.cancel()
        val current = ++generation
        mutableState.value = RuntimeConfigPreviewUiState(profileId, isLoading = true)
        request = viewModelScope.launch {
            try {
                val preview = provider.previewConfig(profileId)
                if (current == generation) {
                    mutableState.value = RuntimeConfigPreviewUiState(profileId, preview = preview)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // 作者 long：解析器和脚本错误可能包含节点密码或 YAML 正文，预览失败只显示受控提示。
                val message = failure.message?.takeIf { it in SAFE_PREVIEW_ERRORS }
                    ?: "读取运行配置失败，请重试"
                if (current == generation) mutableState.value = RuntimeConfigPreviewUiState(profileId, error = message)
            }
        }
    }

    fun close() {
        generation++
        request?.cancel()
        request = null
        // 作者 long：关闭即丢弃正文，既不把配置写入 SavedState，也不让迟到结果重新打开页面。
        mutableState.value = RuntimeConfigPreviewUiState()
    }

    private companion object {
        val SAFE_PREVIEW_ERRORS = setOf(
            "配置不存在", "配置文件不存在", "没有可用配置", "配置超过 2 MiB，暂不支持预览",
            "绑定脚本不存在", "绑定脚本正文不存在", "脚本内容不能为空", "脚本文件超过 2 MiB 限制",
            "当前配置已变化，请重新打开预览", "配置或核心状态已变化，请重试",
        )
    }
}
